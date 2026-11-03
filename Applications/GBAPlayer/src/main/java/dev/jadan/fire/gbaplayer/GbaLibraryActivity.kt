package dev.jadan.fire.gbaplayer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Normal Android TV library; a ROM is a user-imported local file, not a URL.
 * No network permission or always-on background process exists in this app.
 */
class GbaLibraryActivity : Activity() {
    companion object {
        private const val PICK_ROM=4201
        private const val PERMISSION_SHARED=4202
        private const val MAX_ROM=32L*1024*1024
    }
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var busy=false
    private fun dp(value:Int)=(value*resources.displayMetrics.density+0.5f).toInt()
    private val privateRoms:File get()=File(filesDir,"roms")

    private fun shape(focus:Boolean):GradientDrawable {
        return GradientDrawable().apply {
            setColor(if(focus)0xFFDDF2FF.toInt() else 0xFFFFFFFF.toInt())
            cornerRadius=dp(13).toFloat()
            setStroke(dp(if(focus)3 else 1),
                if(focus)0xFF167FB8.toInt() else 0xFFC9DAE8.toInt())
        }
    }
    private fun text(message:String,sp:Int=16):TextView {
        return TextView(this).apply {
            setText(message)
            setTextSize(sp.toFloat())
            setTextColor(0xFF173452.toInt())
            setPadding(dp(2),dp(8),dp(2),dp(8))
        }
    }
    private fun action(parent:LinearLayout,label:String,fn:()->Unit):Button{
        return Button(this).apply{
            text=label
            isAllCaps=false
            textSize=16f
            setTextColor(0xFF173452.toInt())
            minHeight=dp(62)
            background=shape(false)
            setOnFocusChangeListener{_,focused->background=shape(focused)}
            setOnClickListener{fn()}
            parent.addView(this,LinearLayout.LayoutParams(-1,dp(64)).apply{
                bottomMargin=dp(8)
            })
        }
    }
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val scroll=ScrollView(this)
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(26),dp(15),dp(26),dp(22))
            setBackgroundColor(0xFFEDF5FA.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(text("FIRE GBA  /  PLAYROOM",28).apply{setTypeface(null,1)})
        root.addView(text("Native mGBA core · Fire remote mappings · ROMs remain on this device",15))
        status=text("No game currently running. Install your own .gba file.",16)
        root.addView(status)

        action(root,"IMPORT A .GBA WITH FILE PICKER"){pickRom()}
        action(root,"IMPORT FROM FIRE PLAYROOM DOWNLOADS"){scanShared()}
        action(root,"REFRESH SAVED GAMES"){refresh()}
        action(root,"BACK TO FIRE CONSOLE"){finish()}
        root.addView(text("LOCAL GBA LIBRARY",20).apply{setTypeface(null,1)})
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(list)
        root.addView(text(
            "D-pad navigates this library. In-game: directions move; OK short=A, OK hold=B; " +
            "BACK or MENU opens the safety/control wheel. There are no game files bundled.",14))
        refresh()
        root.getChildAt(3).requestFocus()
    }

    private fun refresh(){
        if(!::list.isInitialized)return
        list.removeAllViews()
        if(!privateRoms.exists())privateRoms.mkdirs()
        val games=privateRoms.listFiles()?.filter{
            it.isFile && it.extension.equals("gba",true) && it.length() in 192..MAX_ROM
        }?.sortedBy{it.name.lowercase(Locale.ROOT)}?.take(48) ?: emptyList()
        if(games.isEmpty()){
            list.addView(text("No imported games. Transfer a .gba into Downloads/FirePlayroom/GBA or use the file picker.",16))
            return
        }
        for(game in games){
            action(list,"PLAY  /  ${game.name}"){
                val intent=Intent(this,GbaGameActivity::class.java)
                intent.putExtra(GbaGameActivity.EXTRA_GAME,game.name)
                startActivity(intent)
            }
        }
        list.addView(text("${games.size} local game(s). Game saves are stored separately from ROMs.",14))
    }

    private fun pickRom(){
        if(busy){status.text="Import already in progress. Back leaves safely.";return}
        try{
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply{
                type="*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },PICK_ROM)
        }catch(ex:RuntimeException){
            status.text="No Android file picker on this TV. Use Playroom's ROM import or trusted ADB file transfer."
        }
    }
    private fun scanShared(){
        if(busy){status.text="Import still running. Back remains available.";return}
        if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),PERMISSION_SHARED)
            status.text="Grant file read access to discover games under Downloads/FirePlayroom/GBA."
            return
        }
        val source=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "FirePlayroom/GBA")
        val files=source.listFiles()?.filter{
            it.isFile && it.extension.equals("gba",true) && it.length() in 192..MAX_ROM
        }?.sortedBy{it.name}?.take(48) ?: emptyList()
        if(files.isEmpty()){
            status.text="No .gba files found at Downloads/FirePlayroom/GBA. Use Console Playroom import first."
            return
        }
        val names=files.map{it.name}.toTypedArray()
        AlertDialog.Builder(this).setTitle("Import local GBA file")
            .setItems(names){_,position->
                val file=files[position]
                copyRom(file.name, {file.inputStream()})
            }.setNegativeButton("CANCEL",null).show()
    }
    override fun onRequestPermissionsResult(
        requestCode:Int,permissions:Array<out String>,grantResults:IntArray
    ){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode!=PERMISSION_SHARED)return
        if(grantResults.isNotEmpty()&&grantResults[0]==PackageManager.PERMISSION_GRANTED)scanShared()
        else status.text="Permission denied. Nothing altered; file picker remains available."
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=PICK_ROM || resultCode!=RESULT_OK || data?.data==null)return
        val uri=data.data!!
        if(uri.scheme!="content"){status.text="Only local content-provider files are supported.";return}
        val name=try{
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
                c->if(c.moveToFirst())c.getString(0) else null
            } ?: "chosen.gba"
        }catch(ex:RuntimeException){"chosen.gba"}
        copyRom(name,{contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Selected provider did not return file bytes")})
    }
    private fun copyRom(proposed:String,open:()->InputStream){
        if(busy){status.text="Another import is running.";return}
        val filename=proposed.replace(Regex("[^A-Za-z0-9._-]"),"_").takeLast(64)
        if(!filename.endsWith(".gba",true) || filename.startsWith(".")){
            status.text="A .gba file is required; no copy made.";return
        }
        busy=true
        status.text="Importing locally: $filename"
        worker.execute {
            var part:File?=null
            try{
                if(!privateRoms.exists()&&!privateRoms.mkdirs())error("Private ROM folder unavailable")
                var dst=File(privateRoms,filename)
                val stem=filename.substring(0,filename.length-4)
                var suffix=2
                while(dst.exists()&&suffix<=99){
                    dst=File(privateRoms,"${stem}_${suffix}.gba");suffix++
                }
                if(dst.exists())error("Too many copies of this title")
                part=File(privateRoms,dst.name+".part")
                var size=0L
                open().use{input->
                    FileOutputStream(part).use{out->
                        val buffer=ByteArray(16384)
                        while(true){
                            if(Thread.currentThread().isInterrupted)error("Import cancelled")
                            val n=input.read(buffer)
                            if(n<0)break
                            size+=n
                            if(size>MAX_ROM)error("GBA file exceeds 32 MiB")
                            out.write(buffer,0,n)
                        }
                        out.fd.sync()
                    }
                }
                if(size<192)error("ROM file is too small")
                if(!part.renameTo(dst))error("Import could not be finalized")
                runOnUiThread{
                    if(!isFinishing&&!isDestroyed){
                        status.text="Imported ${dst.name} ($size bytes). Choose PLAY."
                        refresh()
                    }
                }
            }catch(ex:Exception){
                runOnUiThread{
                    if(!isFinishing&&!isDestroyed)status.text=
                        "Import failed: ${ex.message}. Existing games unchanged."
                }
            }finally{
                part?.takeIf{it.exists()}?.delete()
                runOnUiThread{busy=false}
            }
        }
    }
    override fun onDestroy(){
        worker.shutdownNow()
        super.onDestroy()
    }
}
