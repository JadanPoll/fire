package dev.jadan.fire;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny native Snake probe. All game rules are in the deterministic SnakeGameModel. */
public final class SnakeActivity extends Activity {
    private SnakeView board;
    private TextView status;
    private SnakeGameModel game;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        TvUi.page(this, root);
        setContentView(root);
        root.addView(TvUi.header(this, "03  /  SNAKE"));
        root.addView(TvUi.text(this,
            "D-pad turns  ·  OK pauses/resumes  ·  BACK leaves. Snake moves every 230 ms.",
            16, TvUi.SUB));
        status = TvUi.text(this, "", 18, TvUi.INK);
        root.addView(status);
        board = new SnakeView(this);
        root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
        TvUi.action(this, root, "NEW GAME", () -> {
            game = new SnakeGameModel();
            board.resume();
            board.requestFocus();
            refresh();
            board.invalidate();
        });
        game = new SnakeGameModel();
        refresh();
        board.requestFocus();
    }

    private void refresh() {
        if (status == null || game == null) return;
        status.setText("SCORE " + game.score + "    LENGTH " + game.length +
            (game.over ? (game.won ? "    YOU FILLED THE BOARD" : "    GAME OVER — NEW GAME") :
                game.paused ? "    PAUSED — OK resumes" : "    RUNNING — OK pauses"));
    }

    @Override protected void onPause() {
        if (board != null) board.stop();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (board != null) board.resume();
    }

    private final class SnakeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean running;
        private final Runnable advance = new Runnable() {
            @Override public void run() {
                if (!running || game == null || game.over) return;
                game.tick();
                invalidate();
                refresh();
                if (!game.over) postDelayed(this, 230);
            }
        };

        SnakeView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setContentDescription("Snake game board. D-pad turns, OK pauses.");
        }

        void stop() { running = false; removeCallbacks(advance); }
        void resume() {
            removeCallbacks(advance);
            running = true;
            if (game != null && !game.over) postDelayed(advance, 230);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            resume();
        }

        @Override protected void onDetachedFromWindow() {
            stop();
            super.onDetachedFromWindow();
        }

        @Override public boolean onKeyDown(int key, KeyEvent event) {
            if (key == KeyEvent.KEYCODE_DPAD_CENTER ||
                key == KeyEvent.KEYCODE_ENTER ||
                key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.getRepeatCount() == 0 && !game.over) {
                    game.paused = !game.paused;
                    refresh();
                    invalidate();
                }
                return true;
            }
            int dx = 0, dy = 0;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_LEFT: dx = -1; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: dx = 1; break;
                case KeyEvent.KEYCODE_DPAD_UP: dy = -1; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: dy = 1; break;
                default: return super.onKeyDown(key, event);
            }
            if (event.getRepeatCount() == 0) game.turn(dx, dy);
            return true;
        }

        @Override public boolean onKeyUp(int key, KeyEvent event) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT ||
                key == KeyEvent.KEYCODE_DPAD_RIGHT ||
                key == KeyEvent.KEYCODE_DPAD_UP ||
                key == KeyEvent.KEYCODE_DPAD_DOWN ||
                key == KeyEvent.KEYCODE_DPAD_CENTER ||
                key == KeyEvent.KEYCODE_ENTER ||
                key == KeyEvent.KEYCODE_NUMPAD_ENTER) return true;
            return super.onKeyUp(key, event);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xFFEDF5FA);
            if (game == null) return;
            float cell = Math.min(getWidth() * .052f, getHeight() * .078f);
            if (cell <= 0) return;
            float left = (getWidth() - SnakeGameModel.COLS * cell) / 2;
            float top = (getHeight() - SnakeGameModel.ROWS * cell) / 2;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF162C3E);
            canvas.drawRoundRect(left - 7, top - 7,
                left + SnakeGameModel.COLS * cell + 7,
                top + SnakeGameModel.ROWS * cell + 7, 13, 13, paint);
            for (int y = 0; y < SnakeGameModel.ROWS; y++)
                for (int x = 0; x < SnakeGameModel.COLS; x++) {
                    paint.setColor(((x + y) & 1) == 0 ? 0xFF223F4F : 0xFF274657);
                    canvas.drawRect(left + x * cell + 1, top + y * cell + 1,
                        left + (x + 1) * cell - 1, top + (y + 1) * cell - 1, paint);
                }
            drawCell(canvas, left, top, cell, game.foodX, game.foodY, 0xFFFFB464, .23f);
            for (int i = game.length - 1; i >= 0; i--) {
                int where = game.bodyCell(i);
                drawCell(canvas, left, top, cell,
                    where % SnakeGameModel.COLS, where / SnakeGameModel.COLS,
                    i == 0 ? 0xFF7AE7C8 : 0xFF58B3D2, .12f);
            }
        }

        private void drawCell(Canvas c, float left, float top, float size,
                              int x, int y, int color, float fraction) {
            float inset = size * fraction;
            paint.setColor(color);
            c.drawRoundRect(left + x * size + inset, top + y * size + inset,
                left + (x + 1) * size - inset,
                top + (y + 1) * size - inset, size * .12f, size * .12f, paint);
        }
    }
}
