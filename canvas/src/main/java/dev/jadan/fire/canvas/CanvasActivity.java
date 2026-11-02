package dev.jadan.fire.canvas;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;

public final class CanvasActivity extends Activity {
    private final ArrayList<float[]> trail = new ArrayList<>();
    private float x=.5f,y=.5f;
    private int moves=0,colorIndex=0;
    private final int[] palette={0xFFFFCC64,0xFF64C8FF,0xFF87E0AE,0xFFFF91B9};
    private View surface;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        surface=new View(this) {
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                c.drawColor(0xFF102132);
                float width=getWidth(),height=getHeight(),density=getResources().getDisplayMetrics().density;
                p.setTextSize(26*density);
                p.setColor(0xFF64C8FF);
                c.drawText("REMOTE CANVAS",width*.08f,height*.12f,p);
                p.setTextSize(16*density);
                p.setColor(0xFFFFFFFF);
                c.drawText("D-PAD: draw    OK: change color    BACK: return",width*.08f,height*.18f,p);
                c.drawText("Moves: "+moves+"  |  Trail points: "+trail.size(),width*.08f,height*.24f,p);
                for(float[] q:trail) {
                    p.setColor(palette[(int)q[2]]);
                    c.drawCircle(q[0]*width,q[1]*height,5*density,p);
                }
                p.setColor(palette[colorIndex]);
                c.drawCircle(x*width,y*height,13*density,p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2*density);
                c.drawCircle(x*width,y*height,23*density,p);
                p.setStyle(Paint.Style.FILL);
            }
        };
        surface.setFocusable(true);
        setContentView(surface);
        surface.requestFocus();
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if(event.getRepeatCount()>0 &&
                (key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER)) return true;
        float step=.025f;
        switch(key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: x=Math.max(.05f,x-step); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: x=Math.min(.95f,x+step); break;
            case KeyEvent.KEYCODE_DPAD_UP: y=Math.max(.30f,y-step); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: y=Math.min(.92f,y+step); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: colorIndex=(colorIndex+1)%palette.length;surface.invalidate();return true;
            default:return super.onKeyDown(key,event);
        }
        trail.add(new float[]{x,y,colorIndex});
        if(trail.size()>900)trail.remove(0);
        moves++;
        surface.invalidate();
        return true;
    }
}
