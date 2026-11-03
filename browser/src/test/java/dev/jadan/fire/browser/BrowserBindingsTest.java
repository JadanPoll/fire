package dev.jadan.fire.browser;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserBindingsTest {
    @Test public void normalEscapeAndSystemKeysCannotBeRemapped(){
        assertFalse(BrowserBindings.allowedKey(KeyEvent.KEYCODE_BACK));
        assertFalse(BrowserBindings.allowedKey(KeyEvent.KEYCODE_MENU));
        assertFalse(BrowserBindings.allowedKey(KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(BrowserBindings.allowedKey(KeyEvent.KEYCODE_ENTER));
        assertFalse(BrowserBindings.allowedKey(KeyEvent.KEYCODE_BUTTON_A));
        assertFalse(BrowserBindings.allowedKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        assertFalse(BrowserBindings.allowedKey(KeyEvent.KEYCODE_HOME));
        assertFalse(BrowserBindings.allowedKey(KeyEvent.KEYCODE_VOLUME_UP));
        assertTrue(BrowserBindings.allowedKey(KeyEvent.KEYCODE_DPAD_LEFT));
        assertTrue(BrowserBindings.allowedKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD));
    }

    @Test public void nativeGameTargetsUseExplicitAllowlist(){
        assertTrue(BrowserBindings.allowedVirtualKey(KeyEvent.KEYCODE_W));
        assertTrue(BrowserBindings.allowedVirtualKey(KeyEvent.KEYCODE_SPACE));
        assertTrue(BrowserBindings.allowedVirtualKey(KeyEvent.KEYCODE_DPAD_UP));
        assertFalse(BrowserBindings.allowedVirtualKey(KeyEvent.KEYCODE_HOME));
        assertFalse(BrowserBindings.allowedVirtualKey(KeyEvent.KEYCODE_POWER));
        assertFalse(BrowserBindings.allowedVirtualKey(KeyEvent.KEYCODE_VOLUME_MUTE));
        BrowserBindings.Target target=new BrowserBindings.Target(KeyEvent.KEYCODE_SPACE);
        assertTrue(target.isVirtualKey());
        assertEquals(KeyEvent.KEYCODE_SPACE,target.virtualKey);
    }

    @Test public void touchTargetsAreNormalizedAndNeverEscalateToKeyboard(){
        BrowserBindings.Target target=new BrowserBindings.Target(true,2f,-1f);
        assertFalse(target.isVirtualKey());
        assertTrue(target.web);
        assertTrue(target.x<=.995f);
        assertTrue(target.y>=.005f);
    }
}
