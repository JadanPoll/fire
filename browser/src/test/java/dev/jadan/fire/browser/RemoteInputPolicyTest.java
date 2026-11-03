package dev.jadan.fire.browser;

import org.junit.Test;
import static org.junit.Assert.*;

/** Realistic event sequences from TV Bro/Fire OS remote reports. */
public class RemoteInputPolicyTest {
    @Test public void oneTapFromKeyDownAndUp() {
        RemoteInputPolicy p=new RemoteInputPolicy();
        p.okDown(0);
        assertTrue(p.okUp(1000));
        assertFalse("UP without DOWN must not click",p.okUp(1001));
    }

    @Test public void oneTapEvenWhenDownIsDuplicatedAndKeysAreAliased() {
        RemoteInputPolicy p=new RemoteInputPolicy();
        p.okDown(0); // DPAD_CENTER
        p.okDown(0); // Duplicate ENTER down from same OK gesture
        p.okDown(2); // Fire OS repeats
        assertTrue(p.okUp(1000));
        assertFalse(p.okUp(1002)); // second key-up must be inert
        p.okDown(0);
        assertFalse("bounce within 450ms",p.okUp(1170));
        p.okDown(0);
        assertTrue("new deliberate tap after guard",p.okUp(1510));
    }

    @Test public void keyboardTransitionCancelsPendingClick() {
        RemoteInputPolicy p=new RemoteInputPolicy();
        p.okDown(0);
        p.cancelOk();
        assertFalse(p.okUp(1000));
        assertFalse(RemoteInputPolicy.pointerOwnsDpad(true,true));
        assertTrue(RemoteInputPolicy.pointerOwnsDpad(true,false));
        assertFalse(RemoteInputPolicy.pointerOwnsDpad(false,false));
    }

    @Test public void mediaKeyAndMediaSessionCannotToggleTwice() {
        RemoteInputPolicy p=new RemoteInputPolicy();
        assertTrue(p.mediaDown(0,1000));  // MediaSession PLAY_PAUSE
        assertFalse(p.mediaDown(0,1005)); // Activity duplicated dispatch
        assertFalse(p.mediaDown(1,1010)); // OS repeat
        assertFalse(p.mediaDown(0,1300)); // duplicate PLAY/PAUSE
        assertTrue(p.mediaDown(0,1500));  // deliberate next press
    }

    @Test public void focusIsNotPointerMode() {
        assertTrue("Pointer selection does not disappear when focus changes",
            RemoteInputPolicy.pointerOwnsDpad(true,false));
        assertFalse("Visible keyboard owns D-pad while it is up",
            RemoteInputPolicy.pointerOwnsDpad(true,true));
    }
}
