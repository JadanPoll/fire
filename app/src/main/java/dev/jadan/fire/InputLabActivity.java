package dev.jadan.fire;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

/**
 * A deliberately LOCAL input-normalization experiment. This is not a global Fire OS cursor.
 *
 * Raw remote keydown/repeats are observed separately from intent-level pointer motion.
 * Tap movement is discrete; a held direction uses elapsed time rather than repeat frequency.
 * OK activates on key release and has a global-in-this-activity cooldown.
 */
public final class InputLabActivity extends Activity {
    private LabView lab;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lab = new LabView();
        setContentView(lab);
        lab.setFocusable(true);
        lab.setFocusableInTouchMode(true);
        lab.requestFocus();
    }

    @Override protected void onPause() {
        if (lab != null) lab.resetHeld();
        super.onPause();
    }

    private final class LabView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float cursorX = 0.50f, cursorY = 0.52f;
        private int rawDown, rawRepeat, accepted, suppressed, activatedTile = -1;
        private boolean left, right, up, down, okArmed;
        private long leftSince, rightSince, upSince, downSince, lastFrame, lastAccepted;
        private String event = "Press D-pad to move. OK selects the tile under the pointer.";
        private final int[] tiles = {0xFF1E4E6E,0xFF275775,0xFF1E4E6E,0xFF275775,
                0xFF275775,0xFF1E4E6E,0xFF275775,0xFF1E4E6E,
                0xFF1E4E6E,0xFF275775,0xFF1E4E6E,0xFF275775};

        LabView() {
            super(InputLabActivity.this);
            setBackgroundColor(0xFF0C1B2A);
        }

        private void text(Canvas c, String s, float x, float y, float sp, int color) {
            paint.setColor(color);
            paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
            paint.setStyle(Paint.Style.FILL);
            c.drawText(s,x,y,paint);
        }

        private float clamp(float v) { return Math.max(0.02f, Math.min(0.98f, v)); }
        private float gridLeft() { return getWidth() * 0.10f; }
        private float gridRight() { return getWidth() * 0.90f; }
        private float gridTop() { return getHeight() * 0.28f; }
        private float gridBottom() { return getHeight() * 0.78f; }

        private float posX() { return gridLeft() + cursorX * (gridRight() - gridLeft()); }
        private float posY() { return gridTop() + cursorY * (gridBottom() - gridTop()); }

        private int tile() {
            int column = Math.min(3, (int)(cursorX * 4));
            int row = Math.min(2, (int)(cursorY * 3));
            return row * 4 + column + 1;
        }

        private void movePixels(float x, float y) {
            float width = Math.max(1, gridRight()-gridLeft());
            float height = Math.max(1, gridBottom()-gridTop());
            cursorX = clamp(cursorX + x / width);
            cursorY = clamp(cursorY + y / height);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            long now = SystemClock.uptimeMillis();
            if (lastFrame == 0) lastFrame = now;
            long delta = Math.min(45, Math.max(0, now-lastFrame));
            lastFrame = now;
            float speed = getResources().getDisplayMetrics().density * 300f;
            float dx = 0,dy = 0;
            if (left && now-leftSince > 200) dx -= speed * delta / 1000f;
            if (right && now-rightSince > 200) dx += speed * delta / 1000f;
            if (up && now-upSince > 200) dy -= speed * delta / 1000f;
            if (down && now-downSince > 200) dy += speed * delta / 1000f;
            if (dx!=0 || dy!=0) movePixels(dx,dy);

            float w = getWidth(), h = getHeight();
            text(c,"INPUT LAB  /  VIRTUAL POINTER", w*.07f,h*.10f,24,0xFF64CAFF);
            text(c,"Stable pointer speed  •  raw repeat independent  •  OK cooldown 260 ms",
                    w*.07f,h*.16f,15,0xFFCFDEE9);
            text(c,String.format(java.util.Locale.US,
                    "RAW %d   REPEATS %d   ACCEPTED %d   SUPPRESSED %d",
                    rawDown,rawRepeat,accepted,suppressed),
                    w*.07f,h*.22f,16,0xFFFFFFFF);

            float l=gridLeft(),t=gridTop();
            float cellW=(gridRight()-l)/4f,cellH=(gridBottom()-t)/3f;
            int selected=tile();
            for (int row=0;row<3;row++) for(int col=0;col<4;col++) {
                int index=row*4+col;
                float x=l+col*cellW, y=t+row*cellH;
                rect.set(x+5,y+5,x+cellW-5,y+cellH-5);
                paint.setColor(activatedTile==index+1?0xFF186C54:tiles[index]);
                paint.setStyle(Paint.Style.FILL);
                c.drawRoundRect(rect,12,12,paint);
                if(selected==index+1) {
                    paint.setColor(0xFFB4E8FF);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3f);
                    c.drawRoundRect(rect,12,12,paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                text(c,String.valueOf(index+1),x+cellW*.42f,y+cellH*.55f,23,0xFFFFFFFF);
            }

            float px=posX(),py=posY();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            paint.setColor(0xFFFFCF68);
            c.drawCircle(px,py,16,paint);
            c.drawLine(px-28,py,px+28,py,paint);
            c.drawLine(px,py-28,px,py+28,paint);
            paint.setStyle(Paint.Style.FILL);

            text(c,"INTENT: "+event,w*.07f,h*.85f,16,0xFFFFFFFF);
            text(c,"D-PAD: move  •  OK/ENTER: activate  •  BACK: leave",
                    w*.07f,h*.93f,15,0xFF9FB4C6);
            if (left||right||up||down) postInvalidateOnAnimation();
        }

        private boolean direction(int key) {
            return key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT
                    || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN;
        }

        private boolean center(int key) {
            return key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER
                    || key==KeyEvent.KEYCODE_NUMPAD_ENTER || key==KeyEvent.KEYCODE_BUTTON_A;
        }

        @Override public boolean onKeyDown(int key, KeyEvent e) {
            if (direction(key) || center(key)) {
                rawDown++;
                if(e.getRepeatCount()>0) rawRepeat++;
                long now=SystemClock.uptimeMillis();
                boolean initial=e.getRepeatCount()==0;
                // Every physical initial keydown gets one discrete movement.
                // Subsequent key repeats do NOT accelerate the pointer.
                if(key==KeyEvent.KEYCODE_DPAD_LEFT) {
                    if(initial){ leftSince=now; movePixels(-25,0); } left=true;
                } else if(key==KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if(initial){ rightSince=now; movePixels(25,0); } right=true;
                } else if(key==KeyEvent.KEYCODE_DPAD_UP) {
                    if(initial){ upSince=now; movePixels(0,-25); } up=true;
                } else if(key==KeyEvent.KEYCODE_DPAD_DOWN) {
                    if(initial){ downSince=now; movePixels(0,25); } down=true;
                } else if (initial) {
                    okArmed=true;
                    event="OK held; release to activate tile "+tile();
                }
                if(direction(key)) event="Moving pointer; target tile "+tile();
                invalidate();
                return true;
            }
            return super.onKeyDown(key,e);
        }

        @Override public boolean onKeyUp(int key, KeyEvent e) {
            if (direction(key)) {
                if(key==KeyEvent.KEYCODE_DPAD_LEFT) left=false;
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT) right=false;
                if(key==KeyEvent.KEYCODE_DPAD_UP) up=false;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN) down=false;
                event="Pointer settled at tile "+tile();
                invalidate();
                return true;
            }
            if(center(key)) {
                if(okArmed) {
                    long now=SystemClock.uptimeMillis();
                    if(lastAccepted!=0 && now-lastAccepted < 260) {
                        suppressed++;
                        event="Duplicate OK ignored; tile "+tile()+" unchanged";
                    } else {
                        accepted++;
                        activatedTile=tile();
                        lastAccepted=now;
                        event="Activated tile "+activatedTile+" (visual confirmation)";
                    }
                }
                okArmed=false;
                invalidate();
                return true;
            }
            return super.onKeyUp(key,e);
        }

        void resetHeld() {
            left=right=up=down=okArmed=false;
            invalidate();
        }

        @Override public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if(!hasFocus) resetHeld();
        }
    }
}
