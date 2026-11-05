package dev.jadan.fire;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProbeGameModelTest {
    @Test public void collectIsDeterministicAndBounded() {
        ProbeGameModel m = new ProbeGameModel(ProbeGameModel.Mode.COLLECT);
        for (int i = 0; i < 20; i++) m.move(-1, 0);
        assertEquals(0, m.playerX);
        m.move(1, 0);
        m.move(1, 0);
        for (int i = 0; i < 3; i++) m.move(0, -1);
        assertEquals(1, m.score);
        assertFalse(m.targetX == m.playerX && m.targetY == m.playerY);
    }

    @Test public void dodgeCollisionsAndProgressAreDistinct() {
        ProbeGameModel m = new ProbeGameModel(ProbeGameModel.Mode.DODGE);
        for (int i = 0; i < 5; i++) m.tick();
        assertEquals(2, m.lives);
        assertEquals(0, m.score);
        m.tick();
        assertEquals(1, m.score);
        assertEquals(1, m.waves);
        assertEquals(0, m.hazardY);
        m.move(0, -1);
        assertEquals(5, m.playerY); // dodge only uses horizontal controls
    }

    @Test public void collectingDoesNotAdvanceOnClockTicks() {
        ProbeGameModel m = new ProbeGameModel(ProbeGameModel.Mode.COLLECT);
        for (int i = 0; i < 100; i++) m.tick();
        assertEquals(0, m.score);
        assertEquals(2, m.targetX);
        assertEquals(2, m.targetY);
    }
}
