package dev.jadan.fire;

/**
 * Deterministic tiny game rules, independent of Android Canvas and input.
 * These probes are deliberately not a general engine or asset system.
 */
final class ProbeGameModel {
    enum Mode { COLLECT, DODGE }
    static final int COLS = 9, ROWS = 6;
    final Mode mode;
    int playerX = 4, playerY = 5;
    int targetX = 2, targetY = 2;
    int hazardX = 4, hazardY = 0;
    int score = 0, lives = 3, waves = 0;
    boolean over = false;

    ProbeGameModel(Mode mode) { this.mode = mode; }

    void move(int dx, int dy) {
        if (over) return;
        playerX = clamp(playerX + dx, 0, COLS - 1);
        if (mode == Mode.COLLECT) {
            playerY = clamp(playerY + dy, 0, ROWS - 1);
            if (playerX == targetX && playerY == targetY) {
                score++;
                targetX = (score * 3 + 2) % COLS;
                targetY = (score * 2 + 1) % ROWS;
                if (targetX == playerX && targetY == playerY)
                    targetX = (targetX + 1) % COLS;
            }
        }
    }

    void tick() {
        if (mode != Mode.DODGE || over) return;
        hazardY++;
        if (hazardY == playerY && hazardX == playerX) {
            lives--;
            if (lives <= 0) over = true;
        }
        if (hazardY >= ROWS) {
            score++;
            waves++;
            hazardX = (waves * 5 + 4) % COLS;
            hazardY = 0;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
