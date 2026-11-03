package dev.jadan.fire.gbaplayer

import android.view.KeyEvent

/**
 * Pure TV-remote -> GBA key gesture policy. Render/input delivery remains
 * inside the owning Activity, not an AccessibilityService or global remapper.
 *
 * Short OK => A; long OK (>=400ms measured UP or observed repeat) => B.
 * No DOWN-only timer guesses a hold: Fire OS media/remote pipelines sometimes
 * fail to forward UP. Directions have explicit press ownership.
 */
internal class RemotePadPolicy {
    data class Command(val key: Int, val down: Boolean, val pulse: Boolean=false)
    private val directions=HashSet<Int>()
    private var okHeld=false
    private var okSince=0L
    private var bHeld=false
    private var lastOkUp=-100000L

    fun direction(key:Int,action:Int):List<Command> {
        if(key !in setOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT))return emptyList()
        if(action==KeyEvent.ACTION_DOWN && directions.add(key))
            return listOf(Command(key,true))
        if(action==KeyEvent.ACTION_UP && directions.remove(key))
            return listOf(Command(key,false))
        return emptyList()
    }
    fun ok(action:Int,repeat:Int,now:Long):List<Command>{
        if(action==KeyEvent.ACTION_DOWN){
            if(!okHeld && repeat==0){
                okHeld=true;okSince=now;bHeld=false
            }else if(okHeld && repeat>0 && !bHeld && now-okSince>=400){
                bHeld=true
                return listOf(Command(KeyEvent.KEYCODE_BUTTON_B,true))
            }
            return emptyList()
        }
        if(action==KeyEvent.ACTION_UP){
            if(!okHeld)return emptyList()
            okHeld=false
            val longPress=bHeld||now-okSince>=400
            val alreadyDown=bHeld
            bHeld=false
            if(alreadyDown)return listOf(Command(KeyEvent.KEYCODE_BUTTON_B,false))
            if(now-lastOkUp<210)return emptyList()
            lastOkUp=now
            return listOf(Command(if(longPress)KeyEvent.KEYCODE_BUTTON_B
                                   else KeyEvent.KEYCODE_BUTTON_A,true,true))
        }
        return emptyList()
    }
    fun cancel():List<Command>{
        val result=directions.map{Command(it,false)}.toMutableList()
        directions.clear()
        if(bHeld)result.add(Command(KeyEvent.KEYCODE_BUTTON_B,false))
        okHeld=false;bHeld=false
        return result
    }
}
