package dev.jadan.fire;

import org.junit.Test;
import static org.junit.Assert.*;

public class SnakeGameModelTest {
    @Test public void initialFoodGrowsAndNewFoodIsNotOnBody() {
        SnakeGameModel s = new SnakeGameModel();
        for (int i = 0; i < 5; i++) s.tick();
        assertEquals(10, s.headX());
        assertEquals(5, s.headY());
        assertEquals(1, s.score);
        assertEquals(4, s.length);
        int food = SnakeGameModel.cell(s.foodX, s.foodY);
        for (int i = 0; i < s.length; i++) assertNotEquals(food, s.bodyCell(i));
    }

    @Test public void rejectsReverseAndMoreThanOneTurnPerTick() {
        SnakeGameModel s = new SnakeGameModel();
        assertFalse(s.turn(-1, 0));
        assertTrue(s.turn(0, -1));
        assertFalse(s.turn(0, 1));
        assertFalse(s.turn(1, 0));
        s.tick();
        assertEquals(5, s.headX());
        assertEquals(4, s.headY());
        assertFalse(s.turn(0, 1));
        assertTrue(s.turn(-1, 0));
        s.tick();
        assertEquals(4, s.headX());
        assertEquals(4, s.headY());
    }

    @Test public void wallEndsGameAndFurtherTicksDoNothing() {
        SnakeGameModel s = new SnakeGameModel();
        for (int i = 0; i < 11; i++) s.tick();
        assertTrue(s.over);
        assertEquals(15, s.headX());
        s.tick();
        assertEquals(15, s.headX());
    }

    @Test public void pausePreservesBodyAndDirection() {
        SnakeGameModel s = new SnakeGameModel();
        s.paused = true;
        assertFalse(s.turn(0, -1));
        s.tick();
        assertEquals(5, s.headX());
        s.paused = false;
        s.tick();
        assertEquals(6, s.headX());
    }

    @Test public void sameInitialStateGivesSameFoodSequence() {
        SnakeGameModel a = new SnakeGameModel();
        SnakeGameModel b = new SnakeGameModel();
        for (int i = 0; i < 5; i++) { a.tick(); b.tick(); }
        assertEquals(a.foodX, b.foodX);
        assertEquals(a.foodY, b.foodY);
    }
}
