package dev.jadan.fire.gbaplayer

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Fire-owned, self-contained libretro frontend. The mGBA engine is existing
 * upstream code, packaged inside this Android application. Remote input
 * translation therefore occurs IN-PROCESS; no global injection, ADB or
 * AccessibilityService is involved.
 *
 * LibretroDroid is GPLv3. See README and third-party attribution.
 */
class GbaGameActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GAME="game_filename"
        private const val MAX_ROM=32L*1024*1024
        private const val MAX_STATE=16*1024*1024
    }
    private val policy=RemotePadPolicy()
    private val handler=Handler(Looper.getMainLooper())
    private val saver=Executors.newSingleThreadExecutor{r->
        Thread(r,"FireGBA-Save").apply{isDaemon=true}
    }
    private var ready=false
    private var menuOpen=false
    private lateinit var retro:GLRetroView
    private lateinit var status:TextView
    private lateinit var saveDir:File
    private var session:MediaSession?=null
    private var lastMediaAt=-100000L
    private var lastFrameKeyAt=0L
    private val heldGamepad=mutableSetOf<Int>()

    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=
            (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
             android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
             android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val name=intent.getStringExtra(EXTRA_GAME)
        if(name==null || !Regex("[A-Za-z0-9._-]{1,64}\\.gba",RegexOption.IGNORE_CASE).matches(name)){
            showFailure("Invalid game request. No software executed.");return
        }
        val dir=File(filesDir,"roms")
        val game=File(dir,name)
        if(!game.isFile || game.length() !in 192L..MAX_ROM ||
            game.canonicalFile.parentFile!=dir.canonicalFile){
            showFailure("Selected game file is missing or outside the private ROM library.");return
        }
        // Preserve save paths across renames or separate titles by ROM content,
        // not by its display label. A bounded 32-MiB hash is cheap at launch.
        val digest=MessageDigest.getInstance("SHA-256")
        game.inputStream().use{stream->
            val block=ByteArray(16384)
            while(true){
                val n=stream.read(block);if(n<0)break
                digest.update(block,0,n)
            }
        }
        val fingerprint=digest.digest().take(12).joinToString(""){"%02x".format(it)}
        saveDir=File(File(filesDir,"saves"),fingerprint)
        saveDir.mkdirs()
        val previous=File(saveDir,"save.ram")
        val savedRam=try{
            previous.takeIf{it.isFile && it.length()<=1024*1024}?.readBytes()
        }catch(ex:Exception){null}

        try{
            val data=GLRetroViewData(this).apply{
                coreFilePath="libmgba_libretro_android.so"
                gameFilePath=game.absolutePath
                systemDirectory=filesDir.absolutePath
                savesDirectory=saveDir.absolutePath
                shader=ShaderConfig.Sharp
                preferLowLatencyAudio=true
                rumbleEventsEnabled=false
                saveRAMState=savedRam
            }
            retro=GLRetroView(this,data)
            val root=FrameLayout(this)
            root.setBackgroundColor(Color.BLACK)
            root.addView(retro,FrameLayout.LayoutParams(-1,-1))
            status=TextView(this).apply{
                text="FIRE GBA · D-pad move · OK=A / long OK=B · BACK=control wheel"
                textSize=15f
                setTextColor(Color.WHITE)
                setBackgroundColor(0xB0122C41.toInt())
                setPadding(24,10,24,10)
            }
            root.addView(status,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
            setContentView(root)
            lifecycle.addObserver(retro)
            ready=true
            retro.isFocusableInTouchMode=true
            retro.requestFocus()
            handler.postDelayed({if(ready && !menuOpen)status.alpha=.55f},3500)
        }catch(error:Throwable){
            showFailure("Native GBA runtime unavailable: "+error.javaClass.simpleName)
        }
    }

    private fun showFailure(reason:String){
        ready=false
        val label=TextView(this).apply{
            text="FIRE GBA\n\n$reason\n\nBack returns to the library."
            textSize=22f
            setTextColor(0xFF173452.toInt())
            setBackgroundColor(0xFFEDF5FA.toInt())
            setPadding(36,36,36,36)
        }
        setContentView(label)
        label.isFocusable=true
        label.requestFocus()
    }

    private fun send(code:Int,action:Int){
        if(!ready)return
        try{retro.sendKeyEvent(action,code,0)}
        catch(error:RuntimeException){
            status.text="Input path unavailable. BACK opens recovery controls."
        }
    }
    private fun pulse(code:Int){
        if(!ready)return
        send(code,KeyEvent.ACTION_DOWN)
        // Must span rendered frames: DOWN+UP queued in the same UI tick may
        // both be drained before the libretro core observes its input state.
        handler.postDelayed({send(code,KeyEvent.ACTION_UP)},110L)
    }
    private fun apply(events:List<RemotePadPolicy.Command>){
        for(event in events){
            if(event.pulse)pulse(event.key)
            else send(event.key,if(event.down)KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP)
        }
    }

    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if(!ready)return super.dispatchKeyEvent(event)
        val code=event.keyCode
        if(code==KeyEvent.KEYCODE_BACK || code==KeyEvent.KEYCODE_MENU){
            if(event.action==KeyEvent.ACTION_UP)showWheel()
            return true
        }
        if(code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
           code==KeyEvent.KEYCODE_MEDIA_PLAY ||
           code==KeyEvent.KEYCODE_MEDIA_PAUSE ||
           code==KeyEvent.KEYCODE_MEDIA_REWIND ||
           code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){
            return handleMedia(event)
        }
        if(code in setOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT)){
            apply(policy.direction(code,event.action))
            return true
        }
        val gamepad=(event.source and InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD
        val okAlias=(code==KeyEvent.KEYCODE_DPAD_CENTER ||
            code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_NUMPAD_ENTER ||
            (code==KeyEvent.KEYCODE_BUTTON_A && !gamepad))
        if(okAlias){
            apply(policy.ok(event.action,event.repeatCount,SystemClock.uptimeMillis()))
            return true
        }
        val gameCodes=setOf(
            KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y
        )
        if(code in gameCodes && gamepad){
            if(event.action==KeyEvent.ACTION_DOWN && heldGamepad.add(code))
                send(code,KeyEvent.ACTION_DOWN)
            if(event.action==KeyEvent.ACTION_UP && heldGamepad.remove(code))
                send(code,KeyEvent.ACTION_UP)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onGenericMotionEvent(event:MotionEvent):Boolean{
        if(ready && (event.source and InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK){
            retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD,
                event.getAxisValue(MotionEvent.AXIS_HAT_X),
                event.getAxisValue(MotionEvent.AXIS_HAT_Y),0)
            retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                event.getAxisValue(MotionEvent.AXIS_X),
                event.getAxisValue(MotionEvent.AXIS_Y),0)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    private fun handleMedia(event:KeyEvent):Boolean{
        if(event.action!=KeyEvent.ACTION_DOWN || event.repeatCount!=0)return true
        val now=SystemClock.uptimeMillis()
        if(now-lastMediaAt<300)return true
        lastMediaAt=now
        when(event.keyCode){
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.KEYCODE_MEDIA_PAUSE ->
                pulse(KeyEvent.KEYCODE_BUTTON_START)
            KeyEvent.KEYCODE_MEDIA_REWIND -> pulse(KeyEvent.KEYCODE_BUTTON_B)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> pulse(KeyEvent.KEYCODE_BUTTON_SELECT)
        }
        return true
    }
    private fun openMediaSession(){
        if(session==null){
            session=MediaSession(this,"FireGBA-Controls").apply{
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setCallback(object:MediaSession.Callback(){
                    override fun onMediaButtonEvent(intent:Intent):Boolean{
                        @Suppress("DEPRECATION")
                        val event=intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                            ?: return false
                        return handleMedia(event)
                    }
                    override fun onPlay(){mediaTransportStart()}
                    override fun onPause(){mediaTransportStart()}
                },handler)
                setPlaybackState(PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD)
                    .setState(PlaybackState.STATE_PAUSED,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN,1f).build())
            }
        }
        session?.isActive=true
    }
    private fun mediaTransportStart(){
        val now=SystemClock.uptimeMillis()
        if(now-lastMediaAt<300)return
        lastMediaAt=now
        pulse(KeyEvent.KEYCODE_BUTTON_START)
    }

    private fun showWheel(){
        if(!ready||menuOpen)return
        // The existing user escape is ALWAYS available. Stop held GBA keys
        // before opening a modal; no mapping persists beyond game focus.
        apply(policy.cancel())
        for(code in heldGamepad.toList())send(code,KeyEvent.ACTION_UP)
        heldGamepad.clear()
        menuOpen=true
        status.alpha=1f
        AlertDialog.Builder(this)
            .setTitle("FIRE GBA  /  CONTROLS")
            .setItems(arrayOf(
                "RESUME GAME",
                "PRESS B  /  CANCEL",
                "PRESS START  /  GAME MENU",
                "PRESS SELECT",
                "PRESS L SHOULDER",
                "PRESS R SHOULDER",
                "SAVE GAME SRAM",
                "SAVE SNAPSHOT",
                "LOAD SNAPSHOT…",
                "SAVE CARTRIDGE RAM & EXIT",
                "EXIT NOW (SKIP SAVE)"
            )){_,selection->
                when(selection){
                    1->pulse(KeyEvent.KEYCODE_BUTTON_B)
                    2->pulse(KeyEvent.KEYCODE_BUTTON_START)
                    3->pulse(KeyEvent.KEYCODE_BUTTON_SELECT)
                    4->pulse(KeyEvent.KEYCODE_BUTTON_L1)
                    5->pulse(KeyEvent.KEYCODE_BUTTON_R1)
                    6->save(false,false)
                    7->save(true,false)
                    8->confirmLoadSnapshot()
                    9->save(false,true)
                    10->finish()
                }
            }
            .setNegativeButton("CLOSE",null)
            .setOnDismissListener{
                menuOpen=false
                if(ready){
                    retro.requestFocus()
                    status.text="Fire GBA · OK=A / long OK=B · BACK=control wheel"
                }
            }.show()
    }
    private fun safeWrite(target:File,bytes:ByteArray){
        if(bytes.isEmpty()||bytes.size>MAX_STATE)throw IllegalArgumentException("Invalid save size")
        val pending=File(target.parentFile,target.name+".part")
        try{
            FileOutputStream(pending).use{
                it.write(bytes);it.fd.sync()
            }
            if(!pending.renameTo(target))throw IllegalStateException("Atomic save failed")
        }finally{if(pending.exists())pending.delete()}
    }
    private fun save(snapshot:Boolean,exitAfter:Boolean){
        if(!ready)return
        status.text="Saving GBA "+(if(snapshot)"state" else "cartridge RAM")+"… Back/Home remain available."
        // GLRetroView serializes on its emulation thread and waits for a
        // latch. Never issue that potentially blocking call on the UI thread.
        saver.submit{
            try{
                val bytes=if(snapshot)retro.serializeState() else retro.serializeSRAM()
                safeWrite(File(saveDir,if(snapshot)"snapshot.state" else "save.ram"),bytes)
                runOnUiThread{
                    if(isFinishing||isDestroyed)return@runOnUiThread
                    status.text="GBA "+(if(snapshot)"snapshot" else "cartridge save")+" stored locally."
                    if(exitAfter)finish()
                }
            }catch(error:Exception){
                runOnUiThread{
                    if(!isFinishing&&!isDestroyed)status.text=
                        "Save not confirmed: ${error.javaClass.simpleName}. BACK always exits."
                }
            }
        }
    }
    private fun confirmLoadSnapshot(){
        AlertDialog.Builder(this).setTitle("Restore previous GBA snapshot?")
            .setMessage("This replaces the running game's state. Save current progress first if needed.")
            .setPositiveButton("RESTORE"){_,_->
                val saved=File(saveDir,"snapshot.state")
                if(!saved.isFile||saved.length() !in 1..MAX_STATE.toLong()){
                    status.text="No valid snapshot exists.";return@setPositiveButton
                }
                saver.submit{
                    try{
                        val result=retro.unserializeState(saved.readBytes())
                        runOnUiThread{
                            if(!isFinishing&&!isDestroyed)status.text=
                                if(result)"Restored previous game snapshot." else "Core rejected snapshot; no confirmation."
                        }
                    }catch(error:Exception){
                        runOnUiThread{
                            if(!isFinishing&&!isDestroyed)status.text=
                                "Snapshot restore failed: ${error.javaClass.simpleName}"
                        }
                    }
                }
            }.setNegativeButton("CANCEL",null).show()
    }
    override fun onBackPressed(){
        if(ready)showWheel() else super.onBackPressed()
    }
    override fun onResume(){
        super.onResume()
        if(ready)openMediaSession()
    }
    override fun onPause(){
        // Cancel held inputs immediately. Save without blocking the Activity
        // lifecycle: a dead GL thread must never hold user navigation hostage.
        if(ready){
            apply(policy.cancel())
            for(code in heldGamepad.toList())send(code,KeyEvent.ACTION_UP)
            heldGamepad.clear()
            session?.isActive=false
            save(false,false)
        }
        super.onPause()
    }
    override fun onDestroy(){
        session?.release()
        session=null
        saver.shutdown() // daemon thread; do not block UI waiting on GL
        ready=false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
