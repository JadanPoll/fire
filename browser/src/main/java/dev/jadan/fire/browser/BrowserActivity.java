package dev.jadan.fire.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.CookieManager;
import org.json.JSONArray;
import java.util.ArrayList;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/**
 * Minimal TV browser / input experiment, NOT an alternative JavaScript engine.
 * Fire OS supplies Chromium-backed Amazon WebView. No JS bridge, scripts from our
 * native process into external sites except user-clicked, editable-only draft paste.
 * Does not bypass embedded-browser OAuth restrictions.
 */
public final class BrowserActivity extends Activity {
    private static final String HOME = "https://chatgpt.com/";
    private static final String PREFS = "browser_state";
    private SharedPreferences prefs;
    private int textZoom = 135;
    private int heldKey = -1;
    private long heldSince, lastMove;
    private int rendererRecoveries;
    private final Runnable pointerTick = new Runnable() {
        @Override public void run() {
            if(!pointerMode || heldKey < 0 || cursor == null) return;
            long now=SystemClock.uptimeMillis();
            long dt=Math.min(45,Math.max(0,now-lastMove));
            lastMove=now;
            if(now-heldSince >= 190) moveCursor(heldKey, dp(350)*dt/1000f);
            cursor.postOnAnimation(this);
        }
    };
    private static final String PROBE = "https://fire-input-test.invalid/";
    private WebView web;
    private FrameLayout viewport;
    private View cursor;
    private TextView status;
    private EditText address, draft;
    private boolean pointerMode, okArmed;
    private float x=.5f, y=.48f;
    private long lastOk;
    private String lastPage = "No page yet";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        textZoom=Math.max(100,Math.min(200,prefs.getInt("text_zoom",135)));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1C29);
        root.setPadding(dp(9),dp(5),dp(9),dp(5));
        setContentView(root);

        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(line,new LinearLayout.LayoutParams(-1,-2));
        address=new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(15);
        address.setHint("https://...");
        address.setTextColor(0xFF112233);
        address.setBackgroundColor(0xFFD7EAF6);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        line.addView(address,new LinearLayout.LayoutParams(0,dp(48),1));
        button(line,"GO",this::goAddress);

        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        button(bar,"INPUT LAB",this::loadProbe);
        button(bar,"CHATGPT",()->loadUrl(HOME));
        button(bar,"BACK",()->{if(web.canGoBack())web.goBack();else finish();});
        button(bar,"RELOAD",()->web.reload());
        button(bar,"POINTER",this::togglePointer);
        LinearLayout reading=new LinearLayout(this);
        reading.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(reading,new LinearLayout.LayoutParams(-1,-2));
        button(reading,"RECENT",this::showRecent);
        button(reading,"A −",()->changeTextZoom(-15));
        button(reading,"A +",()->changeTextZoom(15));
        button(reading,"HOME",()->loadUrl(HOME));

        LinearLayout composing=new LinearLayout(this);
        composing.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(composing,new LinearLayout.LayoutParams(-1,-2));
        draft=new EditText(this);
        draft.setTextSize(15);
        draft.setTextColor(0xFF132436);
        draft.setBackgroundColor(0xFFDFEAF3);
        draft.setSingleLine(true);
        draft.setHint("Speak/type a draft here; then paste into selected web editor");
        draft.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        composing.addView(draft,new LinearLayout.LayoutParams(0,dp(52),1));
        button(composing,"SPEAK",()->showKeyboard(draft));
        button(composing,"PASTE",this::pasteDraft);

        viewport=new FrameLayout(this);
        root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        createWebView();
        cursor=new View(this){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                if(!pointerMode)return;
                float cx=x*getWidth(), cy=y*getHeight();
                p.setColor(0xFFFFC951);p.setStrokeWidth(dp(3));p.setStyle(Paint.Style.STROKE);
                c.drawCircle(cx,cy,dp(15),p);
                c.drawLine(cx-dp(26),cy,cx+dp(26),cy,p);
                c.drawLine(cx,cy-dp(26),cx,cy+dp(26),p);
                p.setStyle(Paint.Style.FILL);
            }
        };
        cursor.setFocusable(false);
        cursor.setClickable(false);
        viewport.addView(cursor,new FrameLayout.LayoutParams(-1,-1));

        status=new TextView(this);
        status.setTextSize(14);status.setTextColor(0xFFDBE7EE);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(48)));
        status.setText("Fire WebView probe. POINTER toggles D-pad cursor; OK taps web elements.");
        if(b!=null) draft.setText(b.getString("draft",""));
        else {
            String fromDesk=getIntent().getStringExtra("draft");
            if(fromDesk!=null)draft.setText(fromDesk);
        }
        String fromLink=getIntent().getDataString();
        String restore=b==null?"":b.getString("url","");
        if(fromLink!=null && fromLink.startsWith("https://"))loadUrl(fromLink);
        else if(restore.startsWith("https://"))loadUrl(restore);
        else loadUrl(prefs.getString("last_url",HOME));
        address.requestFocus();
    }


    private void createWebView(){
        web=new WebView(this);
        web.setBackgroundColor(0xFFFFFFFF);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setTextZoom(textZoom);
        settings.setDefaultFontSize(18);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSaveFormData(false);
        CookieManager.getInstance().setAcceptCookie(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if(Build.VERSION.SDK_INT>=26)settings.setSafeBrowsingEnabled(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url) {
                if(web != view) return;
                lastPage=url;
                address.setText(url.equals(PROBE)?"local:input-lab":url);
                if(url.startsWith("https://")) rememberPage(url);
                status.setText("Ready: "+url+"  |  Text "+textZoom+"%  |  OK toggles text focus.");
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError err) {
                if(req.isForMainFrame()) status.setText("Main page error: "+err.getDescription()+" ("+err.getErrorCode()+")");
            }
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail) {
                if(view != web) return true;
                final String prior = lastPage;
                final boolean crashed=detail.didCrash();
                viewport.post(() -> {
                    if(web != view) return;
                    stopMotion();
                    viewport.removeView(view);
                    web=null;
                    view.destroy();
                    createWebView();
                    rendererRecoveries++;
                    status.setText((crashed?"Page renderer crashed.":"Renderer reclaimed by Fire OS.")+
                        " Browser restored ("+rendererRecoveries+"). "+
                        (crashed?"Select RECENT rather than automatically retrying the crashing page.":"Restoring last page…"));
                    if(!crashed && prior.startsWith("https://")) loadUrl(prior);
                    else loadProbe();
                });
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req) {
                String scheme=req.getUrl().getScheme();
                if(scheme==null||(!"https".equalsIgnoreCase(scheme)&&!"about".equalsIgnoreCase(scheme))) {
                    status.setText("Blocked non-HTTPS navigation: "+req.getUrl());
                    return true;
                }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int progress){
                if(progress<100)status.setText("Rendering page… "+progress+"%");
            }
        });
        viewport.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void button(LinearLayout bar,String name,Runnable run) {
        Button b=new Button(this);
        b.setText(name);b.setAllCaps(false);b.setTextSize(16);
        b.setTextColor(0xFFFFFFFF);b.setBackgroundColor(0xFF255477);
        b.setMinHeight(dp(48));b.setMinWidth(0);
        b.setPadding(dp(13),0,dp(13),0);
        b.setOnFocusChangeListener((v,focus)->b.setBackgroundColor(focus?0xFF1786BD:0xFF255477));
        b.setOnClickListener(v->run.run());
        bar.addView(b,new LinearLayout.LayoutParams(-2,dp(48)));
    }
    private void showKeyboard(View view){
        stopMotion();pointerMode=false;cursor.invalidate();
        view.requestFocus();
        InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(input!=null)input.showSoftInput(view,InputMethodManager.SHOW_IMPLICIT);
        status.setText("Requested Fire TV keyboard for native text field. Dictation should work here.");
    }
    private void loadUrl(String url){
        stopMotion();pointerMode=false;cursor.invalidate();
        if(!url.startsWith("https://")){
            status.setText("Only HTTPS websites are supported in this lab.");
            return;
        }
        address.setText(url);
        web.loadUrl(url);
    }
    private void goAddress(){
        String u=address.getText().toString().trim();
        if(u.equalsIgnoreCase("local:input-lab")){loadProbe();return;}
        if(!u.contains("://"))u="https://"+u;
        loadUrl(u);
    }
    private void loadProbe(){
        stopMotion();pointerMode=false;cursor.invalidate();
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<style>body{font-family:sans-serif;padding:18px;background:#e7f4fc;color:#123}"+
            "textarea,input,[contenteditable]{box-sizing:border-box;width:100%;font-size:24px;padding:14px;"+
            "margin:12px 0;background:white;border:3px solid #168cba;border-radius:8px;min-height:55px}"+
            "</style></head><body><h1>Browser keyboard probe</h1>"+
            "<p>Test each editable control with POINTER + OK. Does Fire TV show its keyboard?</p>"+
            "<label>Ordinary input</label><input placeholder='Enter words'>"+
            "<label>Textarea</label><textarea placeholder='Enter words'></textarea>"+
            "<label>Rich contenteditable (ChatGPT-like)</label>"+
            "<div contenteditable='true' role='textbox' aria-label='Rich editor' data-placeholder='Speak here'>Rich editor: </div>"+
            "<p>Then use native SPEAK at the top and PASTE to test direct insertion.</p></body></html>";
        web.loadDataWithBaseURL(PROBE,html,"text/html","UTF-8",null);
        address.setText("local:input-lab");
    }
    private void togglePointer(){
        stopMotion();pointerMode=!pointerMode;
        cursor.invalidate();
        if(pointerMode){
            web.requestFocus();
            status.setText("Pointer ON. D-pad moves, OK clicks, BACK exits pointer mode. PASTE uses native draft.");
        }else status.setText("Pointer OFF. D-pad navigates normal controls.");
    }
    @Override public boolean dispatchKeyEvent(KeyEvent ev){
        if(pointerMode){
            int k=ev.getKeyCode();
            if(k==KeyEvent.KEYCODE_BACK && ev.getAction()==KeyEvent.ACTION_UP){
                stopMotion();pointerMode=false;okArmed=false;cursor.invalidate();
                status.setText("Pointer off. D-pad focus navigation restored.");
                return true;
            }
            if(k==KeyEvent.KEYCODE_BACK)return true;
            if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||
               k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){
                if(ev.getAction()==KeyEvent.ACTION_UP){
                    if(heldKey==k)stopMotion();
                } else if(ev.getAction()==KeyEvent.ACTION_DOWN && heldKey!=k){
                    heldKey=k;heldSince=lastMove=SystemClock.uptimeMillis();
                    moveCursor(k,dp(25));
                    cursor.postOnAnimation(pointerTick);
                }
                // Ignore OS-generated key repeats; movement is based on elapsed time.
                return true;
            }
            if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){
                if(ev.getAction()==KeyEvent.ACTION_DOWN && ev.getRepeatCount()==0)okArmed=true;
                if(ev.getAction()==KeyEvent.ACTION_UP){
                    if(okArmed && (lastOk==0||SystemClock.uptimeMillis()-lastOk>250))clickPointer();
                    okArmed=false;
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }
    private void clickPointer(){
        long t=SystemClock.uptimeMillis();lastOk=t;
        float px=x*web.getWidth(),py=y*web.getHeight();
        MotionEvent start=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,px,py,0);
        MotionEvent end=MotionEvent.obtain(t,t+60,MotionEvent.ACTION_UP,px,py,0);
        try{
            web.dispatchTouchEvent(start);
            web.dispatchTouchEvent(end);
        }finally{start.recycle();end.recycle();}
        web.requestFocus();
        // Let the WebView's touch handling establish an input connection before requesting IME.
        web.postDelayed(()->{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showSoftInput(web,InputMethodManager.SHOW_IMPLICIT);
            web.evaluateJavascript("(function(){var e=document.activeElement;return e&&((e.isContentEditable)||(e.tagName==='TEXTAREA')||(e.tagName==='INPUT'))?'editable':'not editable';})()",
                    v->{
                        if("\"editable\"".equals(v)){
                            stopMotion();pointerMode=false;cursor.invalidate();
                        }
                        status.setText("Pointer click: "+v+"; native SPEAK + PASTE available.");
                    });
        },200);
    }
    private void pasteDraft(){
        final String value=draft.getText().toString();
        if(value.trim().isEmpty()){
            status.setText("Enter or dictate a draft in native field first.");return;
        }
        // Explicit user action only; insert editable text, no automatic click/send.
        String script="(function(){"+
            "var el=document.activeElement;"+
            "if(!el||!(el.isContentEditable||el.tagName==='INPUT'||el.tagName==='TEXTAREA'))"+
            "el=document.querySelector('[contenteditable=\"true\"],textarea,input:not([type=\"hidden\"])');"+
            "if(!el)return 'NO_EDITABLE';el.focus();"+
            "var value="+JSONObject.quote(value)+";"+
            "if(el.isContentEditable){var ok=document.execCommand('insertText',false,value);"+
            "return ok?'INSERTED_RICH':'RICH_EDITOR_REJECTED';}"+
            "var start=el.selectionStart==null?el.value.length:el.selectionStart;"+
            "var end=el.selectionEnd==null?start:el.selectionEnd;"+
            "el.setRangeText(value,start,end,'end');"+
            "el.dispatchEvent(new Event('input',{bubbles:true}));"+
            "return 'INSERTED_INPUT';})()";
        web.evaluateJavascript(script,response->
                status.setText("Paste result: "+response+" — verify visibly before sending. No message submitted automatically."));
    }

    private void stopMotion(){heldKey=-1;if(cursor!=null)cursor.removeCallbacks(pointerTick);}
    private void moveCursor(int key,float delta){
        if(!pointerMode || viewport==null)return;
        float w=Math.max(1,viewport.getWidth()),h=Math.max(1,viewport.getHeight());
        if(key==KeyEvent.KEYCODE_DPAD_LEFT)x=Math.max(.02f,x-delta/w);
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT)x=Math.min(.98f,x+delta/w);
        if(key==KeyEvent.KEYCODE_DPAD_UP)y=Math.max(.02f,y-delta/h);
        if(key==KeyEvent.KEYCODE_DPAD_DOWN)y=Math.min(.98f,y+delta/h);
        cursor.invalidate();
    }
    private void changeTextZoom(int delta){
        textZoom=Math.max(100,Math.min(200,textZoom+delta));
        prefs.edit().putInt("text_zoom",textZoom).apply();
        if(web!=null)web.getSettings().setTextZoom(textZoom);
        status.setText("Web text size "+textZoom+"% (saved). Some sites use fixed CSS sizes.");
    }
    private void rememberPage(String url){
        if(url.length()>1500)return;
        prefs.edit().putString("last_url",url).apply();
        try {
            JSONArray old=new JSONArray(prefs.getString("recents","[]"));
            JSONArray recent=new JSONArray();
            recent.put(url);
            for(int i=0;i<old.length() && recent.length()<16;i++){
                String prior=old.optString(i);
                if(prior.startsWith("https://")&&!prior.equals(url))recent.put(prior);
            }
            prefs.edit().putString("recents",recent.toString()).apply();
        }catch(Exception ignored){prefs.edit().putString("recents",new JSONArray().put(url).toString()).apply();}
    }
    private void showRecent(){
        try {
            JSONArray data=new JSONArray(prefs.getString("recents","[]"));
            if(data.length()==0){
                status.setText("No recent pages yet. Browse a site to save it locally.");return;
            }
            final String[] urls=new String[data.length()];
            for(int i=0;i<urls.length;i++)urls[i]=data.optString(i);
            new AlertDialog.Builder(this).setTitle("Recent pages (local to Fire Browser)")
                .setItems(urls,(dialog,index)->loadUrl(urls[index]))
                .setNeutralButton("Clear", (dialog,which)->{
                    prefs.edit().remove("last_url").remove("recents").apply();
                    status.setText("Recent pages cleared.");
                }).setNegativeButton("Cancel",null).show();
        }catch(Exception ex){status.setText("Recent pages unavailable: "+ex.getMessage());}
    }
    @Override public void onBackPressed(){
        if(pointerMode){stopMotion();pointerMode=false;cursor.invalidate();return;}
        if(web!=null&&web.canGoBack()){web.goBack();return;}
        super.onBackPressed();
    }
    @Override protected void onPause(){
        stopMotion();
        if(web!=null)web.onPause();
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        if(web!=null)web.onResume();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus)stopMotion();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        String url=intent.getDataString();
        if(url!=null && url.startsWith("https://"))loadUrl(url);
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("draft",draft.getText().toString());
        out.putString("url",lastPage);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy(){
        if(web!=null){
            viewport.removeView(web);
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
