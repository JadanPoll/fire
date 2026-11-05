package dev.jadan.fire;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Two native, asset-free 2D probes embedded in the existing Playroom.
 * The test case is D-pad -> model -> Canvas, not a promised game engine.
 */
public final class MiniGameLabActivity extends Activity {
    private LinearLayout root;
    private ProbeView game;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this);
        TvUi.page(this, root);
        setContentView(root);
        showCatalog();
    }

    private void showCatalog() {
        if (game != null) game.pause();
        game = null;
        root.removeAllViews();
        root.addView(TvUi.header(this, "PLAYROOM  /  MINIGAME PROBES"));
        root.addView(TvUi.text(this,
            "Three intentionally tiny, native 2D games. Observe input, focus, rendering, and time-based updates. No network or extra assets.",
            16, TvUi.SUB));
        LinearLayout panel = TvUi.panel(this);
        panel.addView(TvUi.section(this, "01  /  COLLECT THE DOT"));
        panel.addView(TvUi.text(this,
            "Tap D-pad in four directions to reach the yellow dot. Tests discrete movement and boundary handling.",
            15, TvUi.SUB));
        Button first = TvUi.action(this, panel, "PLAY COLLECT", () -> startGame(ProbeGameModel.Mode.COLLECT));
        panel.addView(TvUi.section(this, "02  /  DODGE THE BLOCK"));
        panel.addView(TvUi.text(this,
            "Tap Left/Right to avoid the falling orange square. Tests frame scheduling and collision state.",
            15, TvUi.SUB));
        TvUi.action(this, panel, "PLAY DODGE", () -> startGame(ProbeGameModel.Mode.DODGE));
        panel.addView(TvUi.section(this, "03  /  SNAKE"));
        panel.addView(TvUi.text(this,
            "Classic snake: eat food, avoid walls and your own body. Tests turning between timed ticks.",
            15, TvUi.SUB));
        TvUi.action(this, panel, "PLAY SNAKE", () -> startActivity(new android.content.Intent(this, SnakeActivity.class)));
        TvUi.addPanel(this, root, panel);
        first.requestFocus();
    }

    private void startGame(ProbeGameModel.Mode mode) {
        if (game != null) game.pause();
        root.removeAllViews();
        root.addView(TvUi.header(this, mode == ProbeGameModel.Mode.COLLECT
            ? "COLLECT THE DOT" : "DODGE THE BLOCK"));
        root.addView(TvUi.text(this,
            "D-pad = move  ·  OK = restart  ·  Back = catalog. Tap to move; held-key auto-repeat is deliberately ignored in this first probe.",
            15, TvUi.SUB));
        status = TvUi.text(this, "", 18, TvUi.INK);
        root.addView(status);
        game = new ProbeView(this, mode);
        root.addView(game, new LinearLayout.LayoutParams(-1, 0, 1));
        game.requestFocus();
        game.resume();
        updateStatus();
    }

    private void updateStatus() {
        if (game == null || status == null) return;
        ProbeGameModel m = game.model;
        status.setText("Score: " + m.score +
            (m.mode == ProbeGameModel.Mode.DODGE ? "   Lives: " + m.lives : "") +
            (m.over ? "   GAME OVER — OK to restart" : "   |   D-pad navigation active"));
    }

    @Override protected void onPause() {
        if (game != null) game.pause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (game != null) game.resume();
    }
    @Override public void onBackPressed() {
        if (game != null) showCatalog();
        else super.onBackPressed();
    }

    private final class ProbeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ProbeGameModel model;
        private boolean running;
        private final Runnable step = new Runnable() {
            @Override public void run() {
                if (!running || model.mode != ProbeGameModel.Mode.DODGE || model.over) return;
                model.tick();
                invalidate();
                updateStatus();
                if (!model.over) postDelayed(this, 350);
            }
        };

        ProbeView(Context context, ProbeGameModel.Mode mode) {
            super(context);
            model = new ProbeGameModel(mode);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setContentDescription("Playable grid. D-pad moves the blue player.");
        }

        void resume() {
            removeCallbacks(step);
            running = true;
            if (model.mode == ProbeGameModel.Mode.DODGE && !model.over)
                postDelayed(step, 350);
        }
        void pause() {
            running = false;
            removeCallbacks(step);
        }
        @Override protected void onDetachedFromWindow() {
            pause();
            super.onDetachedFromWindow();
        }
        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            int dx = 0, dy = 0;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: dx = -1; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: dx = 1; break;
                case KeyEvent.KEYCODE_DPAD_UP: dy = -1; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: dy = 1; break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (event.getRepeatCount() == 0) {
                        ProbeGameModel.Mode current = model.mode;
                        model = new ProbeGameModel(current);
                        resume();
                        invalidate();
                        updateStatus();
                    }
                    return true;
                default: return super.onKeyDown(keyCode, event);
            }
            if (event.getRepeatCount() == 0) {
                model.move(dx, dy);
                invalidate();
                updateStatus();
            }
            return true;
        }
        @Override public boolean onKeyUp(int code, KeyEvent event) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
                code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                return true;
            return super.onKeyUp(code, event);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(0xFFEDF5FA);
            float cell = Math.min(getWidth() * 0.085f, getHeight() * 0.145f);
            if (cell < 1f) return;
            float x0 = (getWidth() - ProbeGameModel.COLS * cell) / 2f;
            float y0 = (getHeight() - ProbeGameModel.ROWS * cell) / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF10304A);
            c.drawRoundRect(x0 - 5, y0 - 5,
                x0 + ProbeGameModel.COLS * cell + 5,
                y0 + ProbeGameModel.ROWS * cell + 5, 10, 10, paint);
            for (int y = 0; y < ProbeGameModel.ROWS; y++)
                for (int x = 0; x < ProbeGameModel.COLS; x++) {
                    paint.setColor(0xFF294861);
                    c.drawRect(x0 + x * cell + 2, y0 + y * cell + 2,
                        x0 + (x + 1) * cell - 2, y0 + (y + 1) * cell - 2, paint);
                }
            if (model.mode == ProbeGameModel.Mode.COLLECT)
                square(c, x0, y0, cell, model.targetX, model.targetY, 0xFFFFD261);
            else
                square(c, x0, y0, cell, model.hazardX, model.hazardY, 0xFFFF8C62);
            square(c, x0, y0, cell, model.playerX, model.playerY, 0xFF57C7F5);
        }
        private void square(Canvas c, float x0, float y0, float cell,
                            int gx, int gy, int color) {
            paint.setColor(color);
            float inset = cell * 0.16f;
            c.drawRoundRect(x0 + gx * cell + inset, y0 + gy * cell + inset,
                x0 + (gx + 1) * cell - inset,
                y0 + (gy + 1) * cell - inset, cell * 0.1f, cell * 0.1f, paint);
        }
    }
}
