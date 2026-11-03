package dev.jadan.fire.gbaplayer

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class RemotePadPolicyTest {
    private val down=KeyEvent.ACTION_DOWN
    private val up=KeyEvent.ACTION_UP

    @Test fun duplicateOkReleasesProduceOneA(){
        val p=RemotePadPolicy()
        assertTrue(p.ok(down,0,1000).isEmpty())
        assertTrue(p.ok(down,0,1002).isEmpty()) // aliased Fire CENTER/ENTER
        assertEquals(KeyEvent.KEYCODE_BUTTON_A,p.ok(up,0,1150).single().key)
        assertTrue(p.ok(up,0,1160).isEmpty())
    }
    @Test fun longOkMapsBWithoutAccidentalA(){
        val p=RemotePadPolicy()
        p.ok(down,0,1000)
        val pressed=p.ok(down,3,1450)
        assertEquals(RemotePadPolicy.Command(KeyEvent.KEYCODE_BUTTON_B,true),pressed.single())
        assertTrue(p.ok(down,4,1500).isEmpty())
        assertEquals(RemotePadPolicy.Command(KeyEvent.KEYCODE_BUTTON_B,false),
                     p.ok(up,0,1700).single())
    }
    @Test fun longReleaseWithoutRepeatStillB(){
        val p=RemotePadPolicy()
        p.ok(down,0,1000)
        val pulse=p.ok(up,0,1450).single()
        assertEquals(KeyEvent.KEYCODE_BUTTON_B,pulse.key)
        assertTrue(pulse.pulse)
    }
    @Test fun duplicateDirectionsDoNotStutter(){
        val p=RemotePadPolicy()
        assertEquals(1,p.direction(KeyEvent.KEYCODE_DPAD_UP,down).size)
        assertTrue(p.direction(KeyEvent.KEYCODE_DPAD_UP,down).isEmpty())
        assertEquals(1,p.direction(KeyEvent.KEYCODE_DPAD_UP,up).size)
        assertTrue(p.direction(KeyEvent.KEYCODE_DPAD_UP,up).isEmpty())
    }
    @Test fun openingMenuReleasesHeldBAndDirections(){
        val p=RemotePadPolicy()
        p.ok(down,0,1000)
        p.ok(down,2,1470)
        p.direction(KeyEvent.KEYCODE_DPAD_RIGHT,down)
        val reset=p.cancel()
        assertEquals(2,reset.size)
        assertTrue(reset.any{it.key==KeyEvent.KEYCODE_BUTTON_B && !it.down})
        assertTrue(reset.any{it.key==KeyEvent.KEYCODE_DPAD_RIGHT && !it.down})
        assertTrue(p.ok(up,0,1500).isEmpty())
    }
}
