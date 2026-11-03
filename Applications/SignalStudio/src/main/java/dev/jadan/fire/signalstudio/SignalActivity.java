package dev.jadan.fire.signalstudio;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import dev.jadan.fire.remote.TvRemoteAdapter;
import dev.jadan.fire.remote.RemotePolicy;
import android.view.KeyEvent;
import android.view.View;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Independent Android TV application. Requires no Fire Console source classes.
 * Android itself owns process lifecycle, Back/Home, and per-package isolation.
 */
public final class SignalActivity extends Activity {
    private static final String[] WAVES={"sine","triangle","square"};
    private String wave="triangle", message="Ready · offline synthetic capability";
    private double hz=.7, gain=1.0, smoothing=.35;
    private int selected=0;
    private double[] trace;
    private SignalView surface;
    // Reusable platform adapter: this independent APK declares semantic
    // controls rather than debugging Fire remote behavior itself.
    private final TvRemoteAdapter remote=new TvRemoteAdapter((action,phase)->{
        if(phase!=RemotePolicy.Phase.DOWN &&
           phase!=RemotePolicy.Phase.REPEAT &&
           phase!=RemotePolicy.Phase.ACTIVATE)return;
        switch(action){
            case UP:selected=(selected+3)%4;break;
            case DOWN:selected=(selected+1)%4;break;
            case LEFT:change(false);break;
            case RIGHT:change(true);break;
            case PRIMARY:resetSelected();break;
            case SECONDARY:
                if(phase==RemotePolicy.Phase.ACTIVATE)resetSelected();
                break;
        }
        if(surface!=null)surface.invalidate();
    });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadRecipe();
        if(state!=null){
            wave=state.getString("wave",wave);
            hz=state.getDouble("hz",hz);
            gain=state.getDouble("gain",gain);
            smoothing=state.getDouble("smoothing",smoothing);
            selected=state.getInt("selected",0);
        }
        rebuild();
        surface=new SignalView();
        surface.setFocusable(true);
        surface.setFocusableInTouchMode(true);
        setContentView(surface);
        surface.requestFocus();
    }

    private void loadRecipe(){
        // A user/agent recipe is data only, never a command or expression.
        try(InputStream input=getAssets().open("example.recipe.json");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[2048];int n;
            while((n=input.read(buffer))!=-1){
                if(bytes.size()+n>8192)throw new IllegalArgumentException("recipe too large");
                bytes.write(buffer,0,n);
            }
            JSONObject json=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            String[] keys={"version","waveform","frequency","gain","smoothing"};
            if(json.length()!=5)throw new IllegalArgumentException("unknown recipe fields");
            for(String key:keys)if(!json.has(key))throw new IllegalArgumentException("missing "+key);
            if(!(json.get("version") instanceof Number)||json.getInt("version")!=1)
                throw new IllegalArgumentException("recipe version");
            if(!(json.get("waveform") instanceof String) ||
               !(json.get("frequency") instanceof Number) ||
               !(json.get("gain") instanceof Number) ||
               !(json.get("smoothing") instanceof Number))
                throw new IllegalArgumentException("recipe types");
            String type=json.getString("waveform");
            double f=json.getDouble("frequency"),g=json.getDouble("gain");
            double alpha=json.getDouble("smoothing");
            SignalMath.samples(type,f,g,alpha,2); // deterministic validation
            wave=type;hz=f;gain=g;smoothing=alpha;
        }catch(Exception ex){
            message="Invalid bundled recipe · safe default loaded ("+
                ex.getClass().getSimpleName()+")";
        }
    }
    private void rebuild(){
        try{trace=SignalMath.samples(wave,hz,gain,smoothing,360);}
        catch(IllegalArgumentException ignored){
            wave="triangle";hz=.7;gain=1;smoothing=.35;
            trace=SignalMath.samples(wave,hz,gain,smoothing,360);
        }
    }
    private void change(boolean increase){
        switch(selected){
            case 0:
                int index=0;
                for(int i=0;i<WAVES.length;i++)if(WAVES[i].equals(wave))index=i;
                wave=WAVES[(index+WAVES.length+(increase?1:-1))%WAVES.length];
                break;
            case 1:hz=Math.max(.05,Math.min(4,Math.round((hz+(increase?.1:-.1))*100)/100.0));break;
            case 2:gain=Math.max(.1,Math.min(3,Math.round((gain+(increase?.1:-.1))*100)/100.0));break;
            case 3:smoothing=Math.max(.05,Math.min(1,Math.round((smoothing+(increase?.05:-.05))*100)/100.0));break;
        }
        rebuild();
        message="Experiment updated locally · no external effects";
        surface.invalidate();
    }
    private void resetSelected(){
        switch(selected){
            case 0:wave="triangle";break;
            case 1:hz=.7;break;
            case 2:gain=1;break;
            case 3:smoothing=.35;break;
        }
        rebuild();message="Selected parameter reset";surface.invalidate();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(remote.dispatch(event))return true;
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onPause(){
        remote.reset();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("wave",wave);state.putDouble("hz",hz);
        state.putDouble("gain",gain);state.putDouble("smoothing",smoothing);
        state.putInt("selected",selected);
        super.onSaveInstanceState(state);
    }

    private final class SignalView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        private final RectF box=new RectF();
        SignalView(){super(SignalActivity.this);setBackgroundColor(0xFFEDF5FA);}
        private void word(Canvas c,String value,float x,float y,float sp,int color,boolean bold){
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:
                android.graphics.Typeface.DEFAULT);
            paint.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);
            c.drawText(value,x,y,paint);
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            word(c,"SIGNAL STUDIO  /  PLAYROOM",w*.06f,h*.10f,26,0xFF173452,true);
            word(c,"Manifold-style synthetic recipe · separate APK · no camera, network or remote worker",
                w*.06f,h*.16f,14,0xFF58728A,false);
            float left=w*.06f,right=w*.94f,top=h*.23f,bottom=h*.68f;
            paint.setColor(0xFFFFFFFF);paint.setStyle(Paint.Style.FILL);
            box.set(left,top,right,bottom);c.drawRoundRect(box,18,18,paint);
            paint.setColor(0xFFC8DCE9);paint.setStrokeWidth(2);
            c.drawLine(left+12,(top+bottom)/2,right-12,(top+bottom)/2,paint);
            double[] points=trace;
            if(points!=null&&points.length>1){
                path.reset();
                float plotX=left+24,plotW=right-left-48;
                float center=(top+bottom)/2,amplitude=(bottom-top)*.39f/3.0f;
                for(int i=0;i<points.length;i++){
                    float x=plotX+plotW*i/(points.length-1);
                    float y=center-(float)points[i]*amplitude;
                    if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
                }
                paint.setColor(0xFF248AC2);paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4);c.drawPath(path,paint);
                paint.setStyle(Paint.Style.FILL);
            }
            String[] controls={
                "WAVE  "+wave.toUpperCase(java.util.Locale.ROOT),
                String.format(java.util.Locale.US,"FREQUENCY  %.2f Hz",hz),
                String.format(java.util.Locale.US,"GAIN  %.2f",gain),
                String.format(java.util.Locale.US,"SMOOTHING  %.2f",smoothing)
            };
            for(int i=0;i<4;i++){
                int col=i%2,row=i/2;
                float x=left+col*(right-left)*.5f;
                float y=bottom+24+row*48;
                paint.setColor(selected==i?0xFFD7EEFA:0xFFFFFFFF);
                box.set(x+4,y,x+(right-left)*.49f,y+40);
                c.drawRoundRect(box,11,11,paint);
                word(c,(selected==i?"▶  ":"   ")+controls[i],x+16,y+28,16,
                    selected==i?0xFF175F89:0xFF173452,selected==i);
            }
            word(c,"D-PAD UP/DOWN: select   LEFT/RIGHT: adjust   OK: reset selected   BACK: Console",
                left,h*.93f,14,0xFF55748B,false);
            word(c,message,left,h*.985f,12,0xFF55748B,false);
        }
    }
}
