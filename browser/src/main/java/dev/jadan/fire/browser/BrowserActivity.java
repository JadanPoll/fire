package dev.jadan.fire.browser;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.view.Gravity;
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
import android.view.Choreographer;
import android.view.Gravity;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
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
    private static final String SEARCH = "https://www.google.com/search?q=";
    private static final String PREFS = "browser_state";
    private SharedPreferences prefs;
    private int textZoom = 85;
    private int pageZoom = 85;
    private long lastSend;
    private int heldKey = -1;
    private long heldSince, lastMove;
    private long lastFrameNs;
    private boolean frameScheduled;
    private long lastWebImeSubmitAt;
    private String lastWebImeAction="none";
    private int rendererRecoveries;
    private int lastProgress=-1;
    private long lastProgressAt, pageStartedAt, lastLoadMillis;
    private boolean reducedMotion;
    private boolean devtoolsEnabled;
    private boolean remoteMediaShortcuts;
    private String lastRecordedUrl="";
    // Comet/TV Bro principle: a tiny precise initial nudge, followed by
    // frame-timed acceleration rather than an OS key-repeat-sized jump.
    private final Choreographer.FrameCallback pointerFrame = frameNs -> {
        frameScheduled=false;
        if(!pointerMode || heldKey<0 || cursor==null)return;
        if(lastFrameNs>0){
            float dt=Math.max(0f,Math.min(.05f,(frameNs-lastFrameNs)/1000000000f));
            float age=Math.max(0f,(SystemClock.uptimeMillis()-heldSince)/1000f);
            float speed=Math.min(560f,75f+600f*age);
            moveCursor(heldKey,dpFloat(speed)*dt);
        }
        lastFrameNs=frameNs;
        schedulePointerFrame();
    };
    private static final String PROBE = "https://fire-input-test.invalid/";
    private WebView web;
    private FrameLayout screen;
    private LinearLayout root;
    private LinearLayout toolsPanel;
    private boolean webEditorFocused;
    private String lastRawEnter="none";
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
        textZoom=Math.max(55,Math.min(200,prefs.getInt("text_zoom",85)));
        pageZoom=Math.max(50,Math.min(160,prefs.getInt("page_zoom",85)));
        reducedMotion=prefs.getBoolean("reduced_motion",false);
        remoteMediaShortcuts=prefs.getBoolean("remote_media_shortcuts",false);
        screen=new FrameLayout(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1C29);
        root.setPadding(dp(5),dp(3),dp(5),dp(3));
        screen.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(screen);

        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(line,new LinearLayout.LayoutParams(-1,-2));
        address=new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(13);
        address.setHint("Search Google or enter a web address");
        address.setTextColor(0xFF112233);
        address.setBackgroundColor(0xFFD7EAF6);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_GO){goAddress();return true;}
            return false;
        });
        line.addView(address,new LinearLayout.LayoutParams(0,dp(42),1));
        button(line,"GO",this::goAddress);
        button(line,"MENU",this::toggleTools);
        button(line,"CURSOR",this::togglePointer);
        // Float over the page instead of resizing WebView every time the menu opens.
        toolsPanel=new LinearLayout(this);
        toolsPanel.setOrientation(LinearLayout.HORIZONTAL);
        toolsPanel.setBackgroundColor(0xFF163047);
        toolsPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams menuLayout=new FrameLayout.LayoutParams(-1,dp(44),Gravity.TOP);
        menuLayout.topMargin=dp(44);
        screen.addView(toolsPanel,menuLayout);
        toolsPanel.setElevation(dp(8));
        button(toolsPanel,"BACK",this::browseBack);
        button(toolsPanel,"FWD",this::browseForward);
        button(toolsPanel,"RELOAD",()->{if(web!=null)web.reload();});
        button(toolsPanel,"RECENT",this::showRecent);
        button(toolsPanel,"CHATGPT",()->loadUrl(HOME));
        button(toolsPanel,"MORE",this::showMore);

        LinearLayout composing=new LinearLayout(this);
        composing.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(composing,new LinearLayout.LayoutParams(-1,-2));
        draft=new EditText(this);
        draft.setTextSize(13);
        draft.setTextColor(0xFF132436);
        draft.setBackgroundColor(0xFFDFEAF3);
        draft.setSingleLine(true);
        draft.setHint("Speak or type a message");
        draft.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_SEND);
        draft.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEND ||
               action==EditorInfo.IME_ACTION_NEXT ||
               action==EditorInfo.IME_ACTION_DONE ||
               action==EditorInfo.IME_ACTION_GO ||
               (event!=null && (event.getKeyCode()==KeyEvent.KEYCODE_ENTER ||
                                event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER) &&
                event.getAction()==KeyEvent.ACTION_UP)){
                sendDraft();return true;
            }
            return false;
        });
        composing.addView(draft,new LinearLayout.LayoutParams(0,dp(44),1));
        button(composing,"VOICE",()->showKeyboard(draft));
        button(composing,"SEND",this::sendDraft);

        viewport=new FrameLayout(this);
        root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        createWebView();
        cursor=new View(this){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                float cx=getWidth()*.5f,cy=getHeight()*.5f;
                p.setColor(0xFFFFC951);
                p.setStrokeWidth(dp(2));
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(cx,cy,dp(8),p);
                c.drawLine(cx-dp(13),cy,cx+dp(13),cy,p);
                c.drawLine(cx,cy-dp(13),cx,cy+dp(13),p);
                p.setStyle(Paint.Style.FILL);
            }
        };
        cursor.setFocusable(false);
        cursor.setClickable(false);
        cursor.setVisibility(View.GONE);
        // A small composited layer moves without invalidating an entire TV-sized
        // transparent view on every frame over a heavy Chromium page.
        screen.addView(cursor,new FrameLayout.LayoutParams(dp(32),dp(32)));
        screen.post(this::updateCursorVisual);

        status=new TextView(this);
        status.setTextSize(11);status.setTextColor(0xFFDBE7EE);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
        status.setText("Fire WebView probe. POINTER toggles D-pad cursor; OK taps web elements.");
        if(b!=null) draft.setText(b.getString("draft",""));
        else {
            String fromDesk=getIntent().getStringExtra("draft");
            if(fromDesk!=null)draft.setText(fromDesk);
        }
        String fromLink=getIntent().getDataString();
        String restore=b==null?"":b.getString("url","");
        if(getIntent().getBooleanExtra("test_lab",false))loadProbe();
        else if(fromLink!=null && fromLink.startsWith("https://"))loadUrl(fromLink);
        else if(restore.startsWith("https://"))loadUrl(restore);
        else {String last=prefs.getString("last_url",HOME);loadUrl(isMeaningfulUrl(last)?last:HOME);}
        address.requestFocus();
    }



    /**
     * Amazon's keyboard may call performEditorAction(NEXT), commitText("\n")
     * or sendKeyEvent, none of which is guaranteed to pass Activity.dispatchKeyEvent.
     * Preserve Chromium's InputConnection for everything other than an explicit
     * submit action while the ChatGPT composer (not an arbitrary form) has focus.
     */
    private final class BrowserWebView extends WebView {
        BrowserWebView(){super(BrowserActivity.this);}
        @Override public InputConnection onCreateInputConnection(EditorInfo editor){
            InputConnection base=super.onCreateInputConnection(editor);
            if(base==null)return null;
            if(isChatGPT()){
                editor.imeOptions=(editor.imeOptions&~EditorInfo.IME_MASK_ACTION) |
                    EditorInfo.IME_ACTION_SEND;
                post(BrowserActivity.this::refreshComposerFocus);
            }
            return new InputConnectionWrapper(base,false){
                @Override public boolean performEditorAction(int action){
                    if((action==EditorInfo.IME_ACTION_NEXT || action==EditorInfo.IME_ACTION_SEND
                       || action==EditorInfo.IME_ACTION_GO || action==EditorInfo.IME_ACTION_DONE)
                        && handleWebImeSubmit("editor action "+action))return true;
                    return super.performEditorAction(action);
                }
                @Override public boolean commitText(CharSequence value,int cursorPosition){
                    if(value!=null && "\n".contentEquals(value)
                       && handleWebImeSubmit("IME committed newline"))return true;
                    return super.commitText(value,cursorPosition);
                }
                @Override public boolean sendKeyEvent(KeyEvent event){
                    int code=event.getKeyCode();
                    boolean enter=code==KeyEvent.KEYCODE_ENTER ||
                        code==KeyEvent.KEYCODE_NUMPAD_ENTER;
                    if(enter && handleWebImeKey(event))return true;
                    return super.sendKeyEvent(event);
                }
            };
        }
    }
    private static final String COMPOSER_CHECK =
        "(function(){var e=document.activeElement;"+
        "var box=document.querySelector('#prompt-textarea,[contenteditable=\"true\"].ProseMirror');"+
        "return !!(e&&box&&(e===box||box.contains(e)));})()";
    private void refreshComposerFocus(){
        if(web==null || !isChatGPT()){webEditorFocused=false;return;}
        WebView active=web;
        active.evaluateJavascript(COMPOSER_CHECK,v->{
            if(web==active)webEditorFocused="true".equals(v);
        });
    }
    private boolean handleWebImeKey(KeyEvent event){
        // Hardware Shift+Enter must remain available for multiline prompts.
        // A soft keyboard reporting Shift+Enter can still mean "Next".
        boolean soft=(event.getFlags()&KeyEvent.FLAG_SOFT_KEYBOARD)!=0 ||
            event.getDeviceId()==KeyCharacterMap.VIRTUAL_KEYBOARD;
        if(event.isShiftPressed() && !soft)return false;
        return handleWebImeSubmit("IME Enter"+(event.isShiftPressed()?" (soft Shift)":""));
    }
    private boolean handleWebImeSubmit(String source){
        if(web==null || !isChatGPT() || !webEditorFocused)return false;
        long now=SystemClock.uptimeMillis();
        lastWebImeAction=source;
        if(now-lastWebImeSubmitAt<750)return true;
        lastWebImeSubmitAt=now;
        web.post(()->sendFocusedComposer(source));
        return true;
    }
    private void sendFocusedComposer(String source){
        if(web==null || !isChatGPT())return;
        WebView active=web;
        // A conservative composer-only check prevents clicking a send button
        // while the user was editing a separate search, login or other field.
        String js="(function(){"+
            "var e=document.activeElement;"+
            "var box=document.querySelector('#prompt-textarea,[contenteditable=\"true\"].ProseMirror');"+
            "if(!e||!box||!(e===box||box.contains(e)))return 'NOT_COMPOSER';"+
            "var text=(box.value||box.textContent||'').trim();if(!text)return 'EMPTY';"+
            "var selectors=['button[data-testid=\"send-button\"]',"+
            "'button[data-testid=\"fruitjuice-send-button\"]',"+
            "'button[aria-label=\"Send prompt\"]','button[aria-label=\"Send message\"]'];"+
            "for(var s of selectors){var button=document.querySelector(s);"+
            "if(button&&!button.disabled&&button.getAttribute('aria-disabled')!=='true'){"+
            "button.click();return 'CLICK_ATTEMPTED';}}return 'SEND_UNAVAILABLE';})()";
        active.evaluateJavascript(js,result->{
            if(web!=active)return;
            if("\"CLICK_ATTEMPTED\"".equals(result)){
                webEditorFocused=false;
                InputMethodManager im=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(im!=null)im.hideSoftInputFromWindow(active.getWindowToken(),0);
                status.setText("IME "+source+": Send clicked; verify the message appeared.");
            } else status.setText("IME "+source+": "+result+"; text preserved.");
        });
    }
    private void createWebView(){
        web=new BrowserWebView();
        web.setBackgroundColor(0xFFFFFFFF);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setTextZoom(textZoom);
        settings.setDefaultFontSize(16);
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
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){
                if(web!=view)return;
                webEditorFocused=false;
                pageStartedAt=SystemClock.uptimeMillis();
                lastProgress=-1;
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(web != view) return;
                lastLoadMillis=pageStartedAt>0?SystemClock.uptimeMillis()-pageStartedAt:0;
                address.setText(url.equals(PROBE)?"local:input-lab":url);
                if(isMeaningfulUrl(url)) {
                    lastPage=url;
                    rememberPage(url,view.getTitle());
                }
                applyPageZoom();
                applyReducedMotion();
                status.setText("Ready: "+displayHost(url)+" · load "+lastLoadMillis+"ms · text "+
                    textZoom+"%, page "+pageZoom+"%.");
            }
            @Override public void doUpdateVisitedHistory(WebView view,String url,boolean isReload){
                if(web!=view || !isMeaningfulUrl(url))return;
                lastPage=url;
                if(!isReload)rememberPage(url,view.getTitle());
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
                if(view!=web || progress>=100)return;
                long now=SystemClock.uptimeMillis();
                if((progress<lastProgress+10 || now-lastProgressAt<250)&&progress>5)return;
                lastProgress=progress;lastProgressAt=now;
                status.setText("Loading "+displayHost(view.getUrl())+"… "+progress+"%");
            }
        });
        viewport.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void button(LinearLayout bar,String name,Runnable run) {
        Button b=new Button(this);
        b.setText(name);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(0xFFFFFFFF);b.setBackgroundColor(0xFF255477);
        b.setMinHeight(dp(40));b.setMinWidth(0);
        b.setPadding(dp(7),0,dp(7),0);
        b.setOnFocusChangeListener((v,focus)->b.setBackgroundColor(focus?0xFF1786BD:0xFF255477));
        b.setOnClickListener(v->run.run());
        bar.addView(b,new LinearLayout.LayoutParams(-2,dp(40)));
    }
    private void showMore(){
        // Keep uncommon operations reachable without putting 20 buttons on screen.
        final String[] actions={"Paste draft without sending","Text smaller","Text larger",
            "Page smaller","Page larger","Reset zoom to 100%","Input test page",
            "Browser diagnostics","Inspect with Chrome","Reduced motion",
            "Remote media shortcuts"};
        new AlertDialog.Builder(this).setTitle("Browser options")
            .setItems(actions,(dlg,index)->{
                switch(index){
                    case 0:pasteDraft();break;
                    case 1:changeTextZoom(-10);break;
                    case 2:changeTextZoom(10);break;
                    case 3:zoomPage(false);break;
                    case 4:zoomPage(true);break;
                    case 5:resetZoom();break;
                    case 6:loadProbe();break;
                    case 7:showDiagnostics();break;
                    case 8:toggleInspection();break;
                    case 9:toggleReducedMotion();break;
                    case 10:toggleRemoteShortcuts();break;
                }
            }).setNegativeButton("Close",null).show();
    }

    private void toggleTools(){
        if(toolsPanel==null)return;
        boolean show=toolsPanel.getVisibility()!=View.VISIBLE;
        toolsPanel.setVisibility(show?View.VISIBLE:View.GONE);
        status.setText(show?"Tools open. Pointer can click toolbar controls.":"MENU reopens tools.");
    }
    private void browseBack(){
        if(web!=null&&web.canGoBack())web.goBack();
        else status.setText("No earlier page.");
    }
    private void browseForward(){
        if(web!=null&&web.canGoForward())web.goForward();
        else status.setText("No later page.");
    }
    private boolean nonMediaSite(){
        if(web==null)return false;
        try{
            Uri url=Uri.parse(web.getUrl());
            String host=url.getHost();
            return "chatgpt.com".equals(host)||"www.google.com".equals(host)||"google.com".equals(host);
        }catch(Exception e){return false;}
    }
    private void showKeyboard(View view){
        stopMotion();pointerMode=false;updateCursorVisual();
        view.requestFocus();
        InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(input!=null)input.showSoftInput(view,InputMethodManager.SHOW_IMPLICIT);
        status.setText("Requested Fire TV keyboard for native text field. Dictation should work here.");
    }
    private void loadUrl(String url){
        webEditorFocused=false;
        stopMotion();pointerMode=false;updateCursorVisual();
        if(!url.startsWith("https://")){
            status.setText("Only HTTPS websites are supported in this lab.");
            return;
        }
        address.setText(url);
        web.loadUrl(url);
    }
    private void goAddress(){
        String u=address.getText().toString().trim();
        if(u.isEmpty())return;
        if(u.equalsIgnoreCase("local:input-lab")){loadProbe();return;}
        if(u.startsWith("https://")){loadUrl(u);return;}
        if(u.contains("://")){status.setText("Only HTTPS websites are supported.");return;}
        // Interpret free-form text as a search rather than pretending it is a hostname.
        if(u.indexOf(' ')>=0 || u.indexOf('.')<0 || u.startsWith("?")){
            loadUrl(SEARCH+Uri.encode(u));return;
        }
        loadUrl("https://"+u);
    }
    private void loadProbe(){
        webEditorFocused=false;
        stopMotion();pointerMode=false;updateCursorVisual();
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
        updateCursorVisual();
        if(pointerMode){
            if(web!=null)web.requestFocus();
            status.setText("Pointer works across webpage and toolbar. MENU toggles tools.");
        }else status.setText("Pointer OFF. D-pad navigates normal controls.");
    }
    @Override public boolean dispatchKeyEvent(KeyEvent ev){
        int key=ev.getKeyCode();
        if(key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER)
            lastRawEnter=KeyEvent.keyCodeToString(key)+", shift="+ev.isShiftPressed()+
                ", "+(ev.getAction()==KeyEvent.ACTION_UP?"UP":"DOWN");
        // System-reserved Home, Alexa and volume keys are never remapped.
        if(key==KeyEvent.KEYCODE_MENU){
            if(ev.getAction()==KeyEvent.ACTION_UP)toggleTools();
            return true;
        }
        // Do not hijack playback keys on arbitrary video/media websites.
        if(remoteMediaShortcuts && nonMediaSite() && (key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key==KeyEvent.KEYCODE_MEDIA_REWIND)){
            if(ev.getAction()==KeyEvent.ACTION_UP){
                if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)togglePointer();
                else if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)browseForward();
                else browseBack();
            }
            return true;
        }
        // Raw Enter from a hardware keyboard may produce a newline in a rich
        // composer. Shift+Enter still passes through. A Fire OS IME may commit
        // text without key events; native SEND remains the reliable fallback.
        if(!pointerMode && webEditorFocused && web!=null && web.hasFocus() && isChatGPT() &&
            (key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER)
            && !ev.isShiftPressed()){
            if(ev.getAction()==KeyEvent.ACTION_UP){
                clickSendButton();
                webEditorFocused=false;
            }
            return true;
        }
        if(pointerMode){
            int k=ev.getKeyCode();
            if(k==KeyEvent.KEYCODE_BACK && ev.getAction()==KeyEvent.ACTION_UP){
                stopMotion();pointerMode=false;okArmed=false;updateCursorVisual();
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
                    moveCursor(k,dpFloat(2.5f));
                    schedulePointerFrame();
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
        if(screen==null||web==null)return;
        long time=SystemClock.uptimeMillis();lastOk=time;
        float px=x*screen.getWidth(),py=y*screen.getHeight();
        int[] screenLocation=new int[2],webLocation=new int[2];
        screen.getLocationOnScreen(screenLocation);
        web.getLocationOnScreen(webLocation);
        float webX=px+screenLocation[0]-webLocation[0];
        float webY=py+screenLocation[1]-webLocation[1];
        boolean inWeb=webX>=0&&webY>=0&&webX<web.getWidth()&&webY<web.getHeight()
            &&web.getVisibility()==View.VISIBLE;
        View receiver=inWeb?web:screen;
        float rx=inWeb?webX:px,ry=inWeb?webY:py;
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,rx,ry,0);
        MotionEvent up=MotionEvent.obtain(time,time+60,MotionEvent.ACTION_UP,rx,ry,0);
        try{
            receiver.dispatchTouchEvent(down);
            receiver.dispatchTouchEvent(up);
        }finally{down.recycle();up.recycle();}
        if(!inWeb){
            // Let Android handle the toolbar button's actual click/focus chain.
            View focused=screen.findFocus();
            if(focused instanceof EditText){
                stopMotion();pointerMode=false;updateCursorVisual();
                InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(input!=null)input.showSoftInput(focused,InputMethodManager.SHOW_IMPLICIT);
            }
            return;
        }
        web.requestFocus();
        final WebView clicked=web;
        clicked.postDelayed(()->{
            if(web!=clicked)return;
            clicked.evaluateJavascript(COMPOSER_CHECK,result->{
                if(web!=clicked)return;
                webEditorFocused="true".equals(result);
                if(webEditorFocused){
                    stopMotion();pointerMode=false;updateCursorVisual();
                    InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(input!=null)input.showSoftInput(clicked,InputMethodManager.SHOW_IMPLICIT);
                    status.setText("Web editor focused; native SPEAK and SEND also work.");
                }else status.setText("Page clicked; pointer remains active.");
            });
        },180);
    }
    private static String insertTextScript(String value){
        // Keep the old working execCommand path for rich editors. The input path
        // uses its native setter to notify controlled frameworks of the change.
        return "(function(){"+
            "var el=document.activeElement;"+
            "if(!el||!(el.isContentEditable||el.tagName==='INPUT'||el.tagName==='TEXTAREA'))"+
            "el=document.querySelector('#prompt-textarea,[contenteditable=\"true\"],textarea,input:not([type=\"hidden\"])');"+
            "if(!el)return 'NO_EDITABLE';el.focus();"+
            "var value="+JSONObject.quote(value)+";"+
            "if(el.isContentEditable){var ok=document.execCommand('insertText',false,value);"+
            "return ok?'INSERTED_RICH':'RICH_EDITOR_REJECTED';}"+
            "var setter=Object.getOwnPropertyDescriptor(el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,'value');"+
            "if(setter&&setter.set){setter.set.call(el,(el.value||'')+value);}else{el.value+=value;}"+
            "el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:value}));"+
            "return 'INSERTED_INPUT';})()";
    }
    private void pasteDraft(){
        final String value=draft.getText().toString();
        if(value.trim().isEmpty()){
            status.setText("Dictate or type a draft into the native field first.");return;
        }
        if(web==null){status.setText("Browser renderer recovering. Try again.");return;}
        web.evaluateJavascript(insertTextScript(value),
            result->status.setText("Paste: "+result+" — check the webpage. Nothing was sent."));
    }
    private boolean isChatGPT(){
        if(web==null || web.getUrl()==null)return false;
        try {
            android.net.Uri url=android.net.Uri.parse(web.getUrl());
            String host=url.getHost();
            return "https".equalsIgnoreCase(url.getScheme()) &&
                ("chatgpt.com".equalsIgnoreCase(host) ||
                 (host!=null&&host.endsWith(".chatgpt.com")));
        }catch(Exception ex){return false;}
    }
    private void sendDraft(){
        if(web==null){status.setText("Browser recovering; no message was sent.");return;}
        if(!isChatGPT()){
            status.setText("SEND is ChatGPT-specific. PASTE works on other sites; nothing submitted.");
            return;
        }
        long now=SystemClock.uptimeMillis();
        if(lastSend>0 && now-lastSend<1200){
            status.setText("Send already requested. Check the conversation before sending again.");
            return;
        }
        lastSend=now;
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null)imm.hideSoftInputFromWindow(draft.getWindowToken(),0);
        String text=draft.getText().toString();
        if(text.trim().isEmpty()){
            clickSendButton();
            return;
        }
        status.setText("Inserting draft into ChatGPT…");
        web.evaluateJavascript(insertTextScript(text),result->{
            if(result==null || !result.startsWith("\"INSERTED_")){
                status.setText("Text insertion not confirmed ("+result+"). Nothing sent; draft retained.");
                return;
            }
            // Give the site's controlled editor time to reconcile after input.
            web.postDelayed(this::clickSendButton,350);
        });
    }
    private void clickSendButton(){
        if(!isChatGPT()){
            status.setText("ChatGPT is no longer active. Nothing sent; draft retained.");return;
        }
        final String script="(function(){"+
            "var selectors=['button[data-testid=\"send-button\"]','button[data-testid=\"fruitjuice-send-button\"]',"+
            "'button[aria-label=\"Send prompt\"]','button[aria-label=\"Send message\"]',"+
            "'button[aria-label=\"Send\"]','form button[type=\"submit\"]'];"+
            "for(var s of selectors){var btn=document.querySelector(s);"+
            "if(btn&&!btn.disabled&&btn.getAttribute('aria-disabled')!=='true'){btn.click();return 'CLICKED_SEND';}}"+
            "return 'SEND_BUTTON_UNAVAILABLE';})()";
        web.evaluateJavascript(script,result->{
            if("\"CLICKED_SEND\"".equals(result)){
                status.setText("Clicked ChatGPT Send. Check the message appears; draft retained for recovery.");
            }else{
                status.setText("No enabled Send button ("+result+"). Draft retained; select ChatGPT's arrow if needed.");
            }
        });
    }

    private float dpFloat(float n){return n*getResources().getDisplayMetrics().density;}
    private void schedulePointerFrame(){
        if(frameScheduled || !pointerMode || heldKey<0)return;
        frameScheduled=true;
        Choreographer.getInstance().postFrameCallback(pointerFrame);
    }
    private void stopMotion(){
        heldKey=-1;
        lastFrameNs=0L;
        if(frameScheduled){
            Choreographer.getInstance().removeFrameCallback(pointerFrame);
            frameScheduled=false;
        }
    }
    private void updateCursorVisual(){
        if(cursor==null || screen==null)return;
        cursor.setVisibility(pointerMode?View.VISIBLE:View.GONE);
        if(screen.getWidth()==0 || screen.getHeight()==0)return;
        cursor.setTranslationX(x*screen.getWidth()-cursor.getWidth()*.5f);
        cursor.setTranslationY(y*screen.getHeight()-cursor.getHeight()*.5f);
    }
    private void moveCursor(int key,float delta){
        if(!pointerMode || viewport==null)return;
        float w=Math.max(1,screen.getWidth()),h=Math.max(1,screen.getHeight());
        if(key==KeyEvent.KEYCODE_DPAD_LEFT)x=Math.max(.02f,x-delta/w);
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT)x=Math.min(.98f,x+delta/w);
        if(key==KeyEvent.KEYCODE_DPAD_UP)y=Math.max(.02f,y-delta/h);
        if(key==KeyEvent.KEYCODE_DPAD_DOWN)y=Math.min(.98f,y+delta/h);
        updateCursorVisual();
    }
    private void applyPageZoom(){
        if(web==null)return;
        // The WebView zoomOut() control may refuse to go below its internal floor.
        // CSS zoom is a separate, reversible site-scale setting; unlike transforms,
        // it participates in layout. Some complex pages may still override styling.
        String script="(function(){if(!document.documentElement)return 'NO_ROOT';"+
            "document.documentElement.style.zoom='"+(pageZoom/100.0)+"';"+
            "return document.documentElement.style.zoom;})()";
        web.evaluateJavascript(script,null);
    }
    private void zoomPage(boolean larger){
        pageZoom=Math.max(50,Math.min(160,pageZoom+(larger?10:-10)));
        prefs.edit().putInt("page_zoom",pageZoom).apply();
        applyPageZoom();
        status.setText("Page scale "+pageZoom+"% (saved). Below 100% is supported. FIT restores 100%.");
    }
    private void resetZoom(){
        textZoom=100;pageZoom=100;
        prefs.edit().putInt("text_zoom",100).putInt("page_zoom",100).apply();
        if(web!=null)web.getSettings().setTextZoom(100);
        applyPageZoom();
        status.setText("Restored text and page to 100%. Use A− and PAGE− for smaller layout.");
    }
    private void changeTextZoom(int delta){
        textZoom=Math.max(55,Math.min(200,textZoom+delta));
        prefs.edit().putInt("text_zoom",textZoom).apply();
        if(web!=null)web.getSettings().setTextZoom(textZoom);
        status.setText("Text size "+textZoom+"% (saved); fixed CSS text may require PAGE−.");
    }

    private static String displayHost(String raw){
        try {Uri u=Uri.parse(raw);String host=u.getHost();return host==null?"Local page":host;}
        catch(Exception ex){return "Page";}
    }

    /**
     * A transient OAuth callback is not a destination. Never retain it as
     * a recently visited site OR as the page to restore at startup.
     */
    private static boolean isMeaningfulUrl(String raw){
        try {
            if(raw==null || raw.equals(PROBE) || raw.length()>2048)return false;
            Uri u=Uri.parse(raw);
            if(!"https".equalsIgnoreCase(u.getScheme()))return false;
            String host=u.getHost();
            if(host==null || host.isEmpty())return false;
            host=host.toLowerCase(java.util.Locale.US);
            if(host.equals("accounts.google.com")||host.endsWith(".accounts.google.com")
               ||host.equals("login.microsoftonline.com")||host.equals("appleid.apple.com")
               ||host.startsWith("auth.")||host.startsWith("login.")
               ||host.contains(".auth0.com"))return false;
            String path=u.getPath();
            if(path!=null){
                for(String segment:path.toLowerCase(java.util.Locale.US).split("/")){
                    if(segment.equals("oauth")||segment.equals("authorize")||segment.equals("callback")
                       ||segment.equals("signin")||segment.equals("login")
                       ||segment.equals("sso")||segment.equals("sign-in")
                       ||segment.equals("auth")||segment.equals("consent")
                       ||segment.equals("verify")||segment.equals("verification"))return false;
                }
            }
            for(String key:u.getQueryParameterNames()){
                String name=key.toLowerCase(java.util.Locale.US);
                if(name.equals("code")||name.equals("state")||name.equals("token")
                    ||name.equals("access_token")||name.equals("refresh_token")
                    ||name.equals("id_token")||name.equals("session_state")
                    ||name.equals("oauth_token")||name.equals("redirect_uri")
                    ||name.equals("code_challenge")||name.equals("client_secret")
                    ||name.equals("error_description"))return false;
            }
            return true;
        }catch(Exception ex){return false;}
    }
    private static String cleanHistoryUrl(String url){
        if(!isMeaningfulUrl(url))return null;
        try {
            Uri u=Uri.parse(url);
            Uri.Builder b=u.buildUpon().fragment(null).clearQuery();
            // Remove URL tracking while preserving links that require other parameters.
            for(String key:u.getQueryParameterNames()){
                String k=key.toLowerCase(java.util.Locale.US);
                if(k.startsWith("utm_")||k.equals("fbclid")||k.equals("gclid")
                    ||k.equals("msclkid")||k.equals("ref_src"))continue;
                for(String value:u.getQueryParameters(key))b.appendQueryParameter(key,value);
            }
            String clean=b.build().toString();
            return clean.length()<=1800?clean:null;
        }catch(Exception ex){return null;}
    }
    private static String historyLabel(String url,String title){
        String s=title==null?"":title.trim().replace('\n',' ');
        // A generic "Google" title is not enough to distinguish searches.
        try {
            Uri u=Uri.parse(url);
            String h=u.getHost();
            if(h!=null&&h.contains("google.")&&"/search".equals(u.getPath())){
                String query=u.getQueryParameter("q");
                if(query!=null&&!query.isEmpty())return "Google · "+
                    (query.length()>45?query.substring(0,42)+"…":query);
            }
        }catch(Exception ignored){} 
        // A generic site title isn't enough to distinguish two conversations.
        if(s.length()>0 && s.length()<=120 && !s.startsWith("http")
                && !("ChatGPT".equalsIgnoreCase(s) && url.contains("/c/"))){
            return s.length()>65?s.substring(0,62)+"…":s;
        }
        try {
            Uri uri=Uri.parse(url);
            String host=uri.getHost();
            String path=uri.getPath();
            if(host==null)return "Webpage";
            if(host.contains("google.") && "/search".equals(path)){
                String query=uri.getQueryParameter("q");
                if(query!=null)return "Google · "+(query.length()>45?query.substring(0,42)+"…":query);
            }
            if("chatgpt.com".equals(host) && path!=null && path.startsWith("/c/")){
                return "ChatGPT · conversation "+path.substring(3,Math.min(path.length(),11));
            }
            String label=host+(path==null||path.equals("/")?"":path);
            return label.length()>68?label.substring(0,65)+"…":label;
        }catch(Exception ex){return "Webpage";}
    }
    private static String entryUrl(JSONArray history,int index){
        Object entry=history.opt(index);
        if(entry instanceof String)return (String)entry;
        if(entry instanceof JSONObject)return ((JSONObject)entry).optString("url","");
        return "";
    }
    private void rememberPage(String url,String title){
        final String clean=cleanHistoryUrl(url);
        if(clean==null || clean.equals(lastRecordedUrl))return;
        lastRecordedUrl=clean;
        prefs.edit().putString("last_url",clean).apply();
        try {
            JSONArray old=new JSONArray(prefs.getString("recents","[]"));
            JSONArray updated=new JSONArray();
            JSONObject first=new JSONObject();
            first.put("url",clean);
            first.put("title",historyLabel(clean,title));
            updated.put(first);
            for(int i=0;i<old.length() && updated.length()<16;i++){
                String previous=cleanHistoryUrl(entryUrl(old,i));
                if(previous==null || previous.equals(clean))continue;
                JSONObject item=new JSONObject();
                item.put("url",previous);
                JSONObject orig=old.optJSONObject(i);
                String previousTitle=orig==null?"":orig.optString("title","");
                item.put("title",historyLabel(previous,previousTitle));
                updated.put(item);
            }
            prefs.edit().putString("recents",updated.toString()).apply();
        }catch(Exception ex){
            // A bad history entry must never prevent a page from loading.
            status.setText("History not updated; browser remains usable.");
        }
    }
    private void showRecent(){
        try {
            JSONArray raw=new JSONArray(prefs.getString("recents","[]"));
            java.util.ArrayList<String> urls=new java.util.ArrayList<>();
            java.util.ArrayList<String> labels=new java.util.ArrayList<>();
            for(int i=0;i<raw.length() && urls.size()<16;i++){
                String url=cleanHistoryUrl(entryUrl(raw,i));
                if(url==null||urls.contains(url))continue;
                urls.add(url);
                JSONObject obj=raw.optJSONObject(i);
                labels.add(historyLabel(url,obj==null?"":obj.optString("title","")));
            }
            if(urls.isEmpty()){
                status.setText("No useful recent destinations. OAuth callbacks are excluded.");return;
            }
            new AlertDialog.Builder(this).setTitle("Recent destinations")
                .setItems(labels.toArray(new String[0]),(dialog,index)->loadUrl(urls.get(index)))
                .setNeutralButton("Clear",(dialog,which)->{
                    prefs.edit().remove("last_url").remove("recents").apply();
                    status.setText("Recent destinations cleared.");
                }).setNegativeButton("Cancel",null).show();
        }catch(Exception ex){status.setText("Recent pages unavailable: "+ex.getMessage());}
    }
    private void showDiagnostics(){
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        if(am!=null)am.getMemoryInfo(mi);
        PackageInfo provider=WebView.getCurrentWebViewPackage();
        StringBuilder sb=new StringBuilder();
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
          .append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("WebView: ").append(provider==null?"unknown":provider.packageName+" "+provider.versionName).append('\n');
        sb.append("RAM available: ").append(mi.availMem/(1024*1024)).append(" MiB");
        sb.append(" / system low-memory: ").append(mi.lowMemory).append('\n');
        Runtime runtime=Runtime.getRuntime();
        sb.append("App Java heap used: ").append((runtime.totalMemory()-runtime.freeMemory())/(1024*1024))
          .append(" MiB; total: ").append(runtime.totalMemory()/(1024*1024)).append(" MiB\n");
        sb.append("Renderer recoveries: ").append(rendererRecoveries).append('\n');
        sb.append("Last document load callback: ").append(lastLoadMillis).append("ms\n");
        sb.append("Current page progress: ").append(lastProgress).append("%\n");
        sb.append("Page host: ").append(displayHost(web==null?null:web.getUrl())).append('\n');
        sb.append("Reduce motion: ").append(reducedMotion).append('\n');
        sb.append("Last raw Enter: ").append(lastRawEnter).append('\n');
        sb.append("Last web IME submit: ").append(lastWebImeAction).append('\n');
        sb.append("Media-button shortcuts: ").append(remoteMediaShortcuts).append('\n');
        sb.append("Note: long JavaScript tasks can freeze without a renderer crash. ")
          .append("These numbers do not measure JavaScript heap or establish root cause.");
        String report=sb.toString();
        new AlertDialog.Builder(this).setTitle("Browser diagnostics")
            .setMessage(report)
            .setPositiveButton("COPY",(dlg,which)->{
                ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Fire Browser diagnostics",report));
                status.setText("Diagnostics copied; safe to share (does not include URLs or cookies).");
            }).setNegativeButton("Close",null).show();
    }
    private void toggleRemoteShortcuts(){
        remoteMediaShortcuts=!remoteMediaShortcuts;
        prefs.edit().putBoolean("remote_media_shortcuts",remoteMediaShortcuts).apply();
        status.setText(remoteMediaShortcuts?
            "Remote shortcuts ON: Play/Pause=pointer, Rewind/FF=Back/Forward on ChatGPT/Google. MENU=tools.":
            "Media buttons restored to normal media handling. MENU still opens tools.");
    }
    private void toggleInspection(){
        if(devtoolsEnabled){
            devtoolsEnabled=false;
            WebView.setWebContentsDebuggingEnabled(false);
            status.setText("WebView inspection disabled.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Enable WebView debugging?")
            .setMessage("This allows a computer with authorized ADB access to inspect webpage content, including signed-in sessions. Enable only on a trusted development device and disable when finished. On the PC, visit chrome://inspect/#devices.")
            .setPositiveButton("ENABLE",(dialog,which)->{
                devtoolsEnabled=true;
                WebView.setWebContentsDebuggingEnabled(true);
                status.setText("WebView inspection enabled for authorized ADB. Toggle INSPECT to turn off.");
            })
            .setNegativeButton("Cancel",null).show();
    }
    private void toggleReducedMotion(){
        reducedMotion=!reducedMotion;
        prefs.edit().putBoolean("reduced_motion",reducedMotion).apply();
        applyReducedMotion();
        status.setText("Reduced page motion "+(reducedMotion?"enabled":"disabled")+
            ". Optional experiment; may not accelerate JavaScript execution.");
    }
    private void applyReducedMotion(){
        if(web==null)return;
        final String script="(function(){"+
            "var e=document.getElementById('firetv-reduced-motion');"+
            "if(!"+reducedMotion+"){if(e)e.remove();return 'off';}"+
            "if(!e){e=document.createElement('style');e.id='firetv-reduced-motion';"+
            "e.textContent='*,*::before,*::after{animation-duration:0.01ms !important;"+
            "transition-duration:0s !important;scroll-behavior:auto !important;}';"+
            "(document.head||document.documentElement).appendChild(e);}return 'on';})()";
        web.evaluateJavascript(script,null);
    }
    @Override public void onBackPressed(){
        if(pointerMode){stopMotion();pointerMode=false;updateCursorVisual();return;}
        if(web!=null&&web.canGoBack()){web.goBack();return;}
        super.onBackPressed();
    }
    @Override protected void onPause(){
        stopMotion();
        if(devtoolsEnabled)WebView.setWebContentsDebuggingEnabled(false);
        if(web!=null)web.onPause();
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        if(devtoolsEnabled)WebView.setWebContentsDebuggingEnabled(true);
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
        if(intent.getBooleanExtra("test_lab",false))loadProbe();
        else if(url!=null && url.startsWith("https://"))loadUrl(url);
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("draft",draft.getText().toString());
        out.putString("url",lastPage);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy(){
        if(devtoolsEnabled)WebView.setWebContentsDebuggingEnabled(false);
        stopMotion();
        if(web!=null){
            viewport.removeView(web);
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
