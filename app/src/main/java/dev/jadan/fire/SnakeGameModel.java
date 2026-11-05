package dev.jadan.fire;

/**
 * Standalone, deterministic Snake rules. No Android or renderer dependency.
 * Coordinates and food selection are stable so physical input/timing bugs are
 * distinguishable from nondeterministic game logic.
 */
final class SnakeGameModel {
    static final int COLS = 16;
    static final int ROWS = 10;
    static final int CAPACITY = COLS * ROWS;
    private final int[] body = new int[CAPACITY]; // head first
    int length = 3;
    int directionX = 1, directionY = 0;
    private int pendingX = 1, pendingY = 0;
    private boolean turnQueued;
    int foodX = 10, foodY = 5;
    int score;
    boolean paused, over, won;
    private int seed = 0x145A6C;

    SnakeGameModel() {
        body[0] = cell(5, 5);
        body[1] = cell(4, 5);
        body[2] = cell(3, 5);
    }

    static int cell(int x, int y) { return y * COLS + x; }
    int bodyCell(int index) { return body[index]; }
    int headX() { return body[0] % COLS; }
    int headY() { return body[0] / COLS; }

    /** A single intentional change of direction can be queued each tick. */
    boolean turn(int dx, int dy) {
        if (paused || over || turnQueued || Math.abs(dx) + Math.abs(dy) != 1)
            return false;
        if (dx == -directionX && dy == -directionY) return false;
        if (dx == directionX && dy == directionY) return false;
        pendingX = dx;
        pendingY = dy;
        turnQueued = true;
        return true;
    }

    void tick() {
        if (paused || over) return;
        if (turnQueued) {
            directionX = pendingX;
            directionY = pendingY;
        }
        turnQueued = false;

        int x = headX() + directionX;
        int y = headY() + directionY;
        if (x < 0 || y < 0 || x >= COLS || y >= ROWS) {
            over = true;
            return;
        }

        int next = cell(x, y);
        boolean eating = x == foodX && y == foodY;
        // Entering the last tail cell is legal if that cell will vacate.
        int occupied = eating ? length : length - 1;
        for (int i = 0; i < occupied; i++) {
            if (body[i] == next) {
                over = true;
                return;
            }
        }

        if (eating) length++;
        for (int i = length - 1; i > 0; i--) body[i] = body[i - 1];
        body[0] = next;
        if (eating) {
            score++;
            if (length == CAPACITY) {
                won = true;
                over = true;
            } else {
                chooseFood();
            }
        }
    }

    private boolean occupies(int position) {
        for (int i = 0; i < length; i++)
            if (body[i] == position) return true;
        return false;
    }

    private void chooseFood() {
        seed = seed * 1664525 + 1013904223;
        int start = (seed & 0x7fffffff) % CAPACITY;
        for (int i = 0; i < CAPACITY; i++) {
            int candidate = (start + i) % CAPACITY;
            if (!occupies(candidate)) {
                foodX = candidate % COLS;
                foodY = candidate / COLS;
                return;
            }
        }
        won = true;
        over = true;
    }
}
