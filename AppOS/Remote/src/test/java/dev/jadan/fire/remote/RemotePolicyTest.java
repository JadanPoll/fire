package dev.jadan.fire.remote;

import org.junit.Test;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class RemotePolicyTest {
    private static class Trace implements RemotePolicy.Sink {
        final List<String> events=new ArrayList<>();
        @Override public void onAction(RemotePolicy.Action action,RemotePolicy.Phase phase){
            events.add(action+":"+phase);
        }
    }
    @Test public void fireDuplicateOkTriggersOnePrimaryOnRelease(){
        RemotePolicy p=new RemotePolicy();Trace t=new Trace();
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0,1000,t);
        p.consume(KeyEvent.KEYCODE_ENTER,KeyEvent.ACTION_DOWN,0,1004,t);
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0,1120,t);
        p.consume(KeyEvent.KEYCODE_ENTER,KeyEvent.ACTION_UP,0,1122,t);
        assertEquals(List.of("PRIMARY:ACTIVATE"),t.events);
    }
    @Test public void physicalLongPressIsSecondaryNoPhantomPrimary(){
        RemotePolicy p=new RemotePolicy();Trace t=new Trace();
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0,1000,t);
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,3,1450,t);
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0,1700,t);
        assertEquals(List.of("SECONDARY:DOWN","SECONDARY:UP"),t.events);
    }
    @Test public void timerlessLongUpStillSecondary(){
        RemotePolicy p=new RemotePolicy();Trace t=new Trace();
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0,0,t);
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0,450,t);
        assertEquals(List.of("SECONDARY:ACTIVATE"),t.events);
    }
    @Test public void dpadDedupAndStableRepeat(){
        RemotePolicy p=new RemotePolicy();Trace t=new Trace();
        p.consume(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_DOWN,0,1000,t);
        p.consume(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_DOWN,0,1003,t);
        p.consume(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_DOWN,1,1060,t);
        p.consume(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_DOWN,2,1120,t);
        p.consume(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_UP,0,1210,t);
        p.consume(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_UP,0,1212,t);
        assertEquals(List.of("RIGHT:DOWN","RIGHT:REPEAT","RIGHT:UP"),t.events);
    }
    @Test public void menuBackAndHomeAlwaysPassThrough(){
        RemotePolicy p=new RemotePolicy();Trace t=new Trace();
        for(int key:new int[]{KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE})
            assertFalse(p.consume(key,KeyEvent.ACTION_DOWN,0,1000,t));
        assertTrue(t.events.isEmpty());
    }
    @Test public void focusLossReleasesHeldInputs(){
        RemotePolicy p=new RemotePolicy();Trace t=new Trace();
        p.consume(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.ACTION_DOWN,0,1000,t);
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0,1000,t);
        p.consume(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,4,1450,t);
        p.reset(t);
        assertEquals(List.of("UP:DOWN","SECONDARY:DOWN","UP:UP","SECONDARY:UP"),t.events);
    }
}
