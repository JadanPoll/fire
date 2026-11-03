package dev.jadan.fire.browser;

import org.junit.Test;
import static org.junit.Assert.*;

public class BindingSafetyTest {
    @Test public void doubleDownThenFiveSecondsOkDisables(){
        BindingSafety s=new BindingSafety();
        s.downPressed(0);s.downReleased(1000);
        s.downPressed(0);s.downReleased(1340);
        assertTrue(s.okPressed(0,1460));
        assertFalse(s.resetDue(6459));
        assertTrue(s.resetDue(6460));
        assertFalse("Only one emergency action",s.resetDue(6500));
        assertTrue("Release of emergency OK consumed",s.okReleased(6500));
    }
    @Test public void briefOkAfterDownSequenceStillClicksNormally(){
        BindingSafety s=new BindingSafety();
        s.downPressed(0);s.downReleased(1000);
        s.downPressed(0);s.downReleased(1300);
        s.okPressed(0,1400);
        assertFalse(s.okReleased(1650));
        assertFalse(s.resetDue(9000));
    }
    @Test public void repeatsAndStrayUpsCannotForgeTwoTaps(){
        BindingSafety s=new BindingSafety();
        s.downPressed(0);s.downPressed(3);s.downReleased(1000);
        s.downReleased(1010);
        assertFalse(s.okPressed(0,1110));
        assertFalse(s.resetDue(6200));
    }
    @Test public void slowOrInterruptedSequenceDoesNotArm(){
        BindingSafety s=new BindingSafety();
        s.downPressed(0);s.downReleased(500);
        s.downPressed(0);s.downReleased(3000);
        assertFalse(s.okPressed(0,3050));
        s.cancel();
        s.downPressed(0);s.downReleased(4000);
        s.otherDirection();
        s.downPressed(0);s.downReleased(4200);
        assertFalse(s.okPressed(0,4300));
    }
    @Test public void unknownKeyUpCannotCauseEmergency(){
        BindingSafety s=new BindingSafety();
        s.downPressed(0);s.downReleased(1000);
        s.downPressed(0);s.downReleased(1300);
        assertFalse(s.okReleased(7000));
        assertFalse(s.resetDue(8000));
    }
}
