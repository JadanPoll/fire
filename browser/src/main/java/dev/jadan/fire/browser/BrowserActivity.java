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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.ArrayDeque;
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
import android.view.InputDevice;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final String FIRE_INBOX = "https://fire-inbox-jadan.brinyduck2.chatgpt.site/";
    private static final String SEARCH = "https://www.google.com/search?q=";
    private static final String PREFS = "browser_state";
    private SharedPreferences prefs;
    private int textZoom = 85;
    private int pageZoom = 85;
    private long lastSend;
    private String pendingInboxText;
    private int heldKey = -1;
    private long heldSince, lastMove;
    private long lastFrameNs;
    private long lastEdgeScrollMs, lastPointerInputAt;
    private String lastPointerEvent="none", lastRendererExit="none";
    private boolean frameScheduled;
    private long lastWebImeSubmitAt;
    private String lastWebImeAction="none";
    private int rendererRecoveries;
    private int lastProgress=-1;
    private long lastProgressAt, pageStartedAt, lastLoadMillis;
    private boolean reducedMotion;
    private boolean devtoolsEnabled;
    private boolean remoteMediaShortcuts;
    private MediaSession pointerMediaSession;
    private BrowserUpdater browserUpdater;
    private boolean browserForeground;
    private long lastMediaNavigationAt;
    private String lastMediaDelivery="none";
    private boolean thirdPartyCookies;
    private final ExecutorService cookieWriter=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"FireBrowser-Cookies");t.setDaemon(true);return t;
    });
    private String lastRecordedUrl="";
    private static final String PROBE = "https://fire-input-test.invalid/";
    private WebView web;
    private FrameLayout screen;
    private LinearLayout root;
    private LinearLayout toolsPanel;
    private Button pointerButton;
    private boolean webEditorFocused;
    private String lastRawEnter="none";
    private FrameLayout viewport;
    private View cursor;
    private LinearLayout gameKeysPanel;
    private final java.util.LinkedHashMap<View,Integer> gameKeys=new java.util.LinkedHashMap<>();
    private boolean gameKeysVisible;
    private TextView status;
    private EditText address, draft;
    private boolean pointerMode;
    private boolean imeVisible;

    private final RemoteInputPolicy remoteInputs=new RemoteInputPolicy();
    private BrowserBindings bindings;
    private final BindingSafety bindingSafety=new BindingSafety();
    private final Handler bindingClock=new Handler(Looper.getMainLooper());
    private boolean mediaHeld, mediaHoldTriggered, consumeEmergencyOkUp;
    private long mediaHoldSince;
    private final Runnable rescueAction=()->{
        if(bindingSafety.resetDue(SystemClock.uptimeMillis()))emergencyRestoreBindings();
    };
    private final ArrayDeque<String> remoteTrace=new ArrayDeque<>();
    private void traceInput(String description){
        if(remoteTrace.size()>=16)remoteTrace.removeFirst();
        remoteTrace.addLast((SystemClock.uptimeMillis()%1000000)+"ms "+description);
    }
    private float x=.5f, y=.48f;
    private long lastOk; // diagnostics only; gesture ownership lives in RemoteInputPolicy
    // Synthesized mouse hover (not merely a drawn yellow cursor) makes sites
    // receive native WebView hover/mousemove events.
    private WebView hoveredWeb;
    private float lastHoverX, lastHoverY;
    private long lastHoverMs, pointerClickGeneration;
    private String lastHoverEvent="none", lastWebEditor="none";
    private String lastPage = "No page yet";
    // Comet/TV Bro principle: a tiny precise initial nudge, followed by
    // frame-timed acceleration rather than an OS key-repeat-sized jump.
    private final Choreographer.FrameCallback pointerFrame = frameNs -> {
        frameScheduled=false;
        if(!RemoteInputPolicy.pointerOwnsDpad(pointerMode,imeVisible) || heldKey<0 || cursor==null)return;
        if(lastFrameNs>0){
            float dt=Math.max(0f,Math.min(.05f,(frameNs-lastFrameNs)/1000000000f));
            float age=Math.max(0f,(SystemClock.uptimeMillis()-heldSince)/1000f);
            float speed=Math.min(560f,75f+600f*age);
            moveCursor(heldKey,dpFloat(speed)*dt);
        }
        lastFrameNs=frameNs;
        maybeEdgeScroll(heldKey);
        // A lost key-up must not animate forever when Fire OS steals focus.
        long now=SystemClock.uptimeMillis();
        if(now-heldSince>8000 && now-lastPointerInputAt>1200){
            stopMotion();
            Log.w("FireBrowser","Pointer hold safety stop: key release not observed");
            return;
        }
        schedulePointerFrame();
    };


    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        textZoom=Math.max(55,Math.min(200,prefs.getInt("text_zoom",85)));
        pageZoom=Math.max(50,Math.min(160,prefs.getInt("page_zoom",85)));
        reducedMotion=prefs.getBoolean("reduced_motion",false);
        remoteMediaShortcuts=prefs.getBoolean("remote_media_shortcuts",true);
        thirdPartyCookies=prefs.getBoolean("third_party_cookies",false);
        lastRendererExit=prefs.getString("last_renderer_exit","none");
        // Initialize the cookie policy before any WebView starts loading pages.
        CookieManager.getInstance().setAcceptCookie(true);
        screen=new FrameLayout(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1C29);
        root.setPadding(dp(5),dp(3),dp(5),dp(3));
        screen.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(screen);
        // AndroidX WindowInsetsCompat is the documented keyboard visibility
        // owner (works on Fire OS 7/API 28 with adjustResize).
        // Unlike focus guesses, this prevents a pointer press from hijacking
        // D-pad input that currently belongs to the on-screen keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(screen,(v,insets)->{
            boolean visible=insets.isVisible(WindowInsetsCompat.Type.ime());
            if(imeVisible!=visible){
                imeVisible=visible;
                traceInput("software keyboard "+(visible?"VISIBLE":"HIDDEN"));
                stopMotion();
                remoteInputs.cancelOk();
                if(visible){
                    bindingSafety.cancel();
                    bindingClock.removeCallbacks(rescueAction);
                                mediaHeld=false;
                    if(bindings!=null)bindings.stopInput();
                    resetWebHover(true);
                }
                else if(pointerMode)v.post(()->emitWebHover(true));
                updateCursorVisual();
                updateGameKeys();
            }
            return insets; // Never consume insets from the WebView.
        });

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
        pointerButton=button(line,"CURSOR",this::togglePointer);
        button(line,"SETS",()->{if(bindings!=null)bindings.showWheel();});
        button(line,"KEYS",this::toggleGameKeys);
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
        button(toolsPanel,"INBOX",()->loadUrl(FIRE_INBOX));
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
        createGameKeysPalette();
        cursor=new View(this){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                float cx=getWidth()*.5f,cy=getHeight()*.5f;
                p.setColor(bindings!=null && bindings.captureMode()?
                    0xFF40F0A8:0xFFFFC951);
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
        cursor.setElevation(dp(20));
        cursor.setVisibility(View.GONE);
        // A small composited layer moves without invalidating an entire TV-sized
        // transparent view on every frame over a heavy Chromium page.
        screen.addView(cursor,new FrameLayout.LayoutParams(dp(32),dp(32)));
        screen.post(this::updateCursorVisual);

        status=new TextView(this);
        status.setTextSize(11);status.setTextColor(0xFFDBE7EE);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
        status.setText("Fire Browser ready. MENU → INBOX or MORE. CURSOR toggles pointer.");
        browserUpdater=new BrowserUpdater(this,status);
        bindings=new BrowserBindings(this,prefs,new BrowserBindings.Host(){
            @Override public String pageHost(){
                if(web==null || web.getUrl()==null)return "";
                try{
                    String hostname=Uri.parse(web.getUrl()).getHost();
                    return hostname==null?"":hostname;
                }catch(RuntimeException error){return "";}
            }
            @Override public boolean keyboardVisible(){return imeVisible;}
            @Override public boolean nativeEditorFocused(){
                return (address!=null&&address.hasFocus()) ||
                    (draft!=null&&draft.hasFocus()) || webEditorFocused;
            }
            @Override public void stateChanged(boolean capture,boolean mapped){
                stopMotion();remoteInputs.cancelOk();
                pointerMode=capture;
                if(cursor!=null)cursor.invalidate();
                updateCursorVisual();
                if(mapped&&web!=null)web.requestFocus();
            }
            @Override public void performTarget(BrowserBindings.Target target){
                deliverMappedTouch(target);
            }
            @Override public void status(String message){
                if(status!=null)status.setText(message);
            }
        });
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
                if(webEditorFocused){
                    editor.imeOptions=(editor.imeOptions &
                        ~(EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) |
                        EditorInfo.IME_ACTION_SEND;
                }
                post(BrowserActivity.this::refreshComposerFocus);
            }
            return new InputConnectionWrapper(base,false){
                @Override public boolean performEditorAction(int action){
                    lastWebImeAction="received editor action "+action+
                        " (composer="+webEditorFocused+")";
                    if((action==EditorInfo.IME_ACTION_NEXT || action==EditorInfo.IME_ACTION_SEND
                       || action==EditorInfo.IME_ACTION_GO || action==EditorInfo.IME_ACTION_DONE)
                        && handleWebImeSubmit("editor action "+action))return true;
                    return super.performEditorAction(action);
                }
                @Override public boolean commitText(CharSequence value,int cursorPosition){
                    if(value!=null && "\n".contentEquals(value))
                        lastWebImeAction="received IME newline (composer="+webEditorFocused+")";
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
        // Preserve the existing ChatGPT-specific IME submit workaround.
        // Consume BOTH halves of the key but submit only on UP, like our
        // normalized pointer OK press: never pass the DOWN to WebView and
        // also click Send on the UP.
        boolean soft=(event.getFlags()&KeyEvent.FLAG_SOFT_KEYBOARD)!=0 ||
            event.getDeviceId()==KeyCharacterMap.VIRTUAL_KEYBOARD;
        if(event.isShiftPressed() && !soft)return false;
        if(web==null || !isChatGPT() || !webEditorFocused)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN)return true;
        if(event.getAction()==KeyEvent.ACTION_UP)
            return handleWebImeSubmit("IME Enter"+(event.isShiftPressed()?" (soft Shift)":""));
        return false;
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,thirdPartyCookies);
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
                if(bindings!=null)bindings.onPageChanged(url);
                pointerClickGeneration++;
                remoteInputs.cancelOk();
                stopMotion();
                resetWebHover(false);
                pageStartedAt=SystemClock.uptimeMillis();
                lastProgress=-1;
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(web != view) return;
                if(pendingInboxText!=null && isFireInboxUrl(url)){
                    String receipt=null;
                    try{receipt=Uri.parse(url).getQueryParameter("posted");}
                    catch(Exception ignored){}
                    if("1".equals(receipt)){
                        if(pendingInboxText.equals(draft.getText().toString()))draft.setText("");
                        pendingInboxText=null;
                        status.setText("Fire Inbox confirmed your message was saved.");
                    }else{
                        pendingInboxText=null;
                        status.setText("Post not confirmed. Check the inbox before retrying.");
                    }
                }
                lastLoadMillis=pageStartedAt>0?SystemClock.uptimeMillis()-pageStartedAt:0;
                address.setText(url.equals(PROBE)?"local:input-lab":url);
                if(isMeaningfulUrl(url)) {
                    lastPage=url;
                    rememberPage(url,view.getTitle());
                }
                applyPageZoom();
                applyReducedMotion();
                if(pointerMode)emitWebHover(true);
                if(url!=null && url.startsWith("https://"))scheduleCookieFlush();
                if(!isFireInboxUrl(url) || pendingInboxText!=null ||
                    !url.contains("posted=1"))
                    status.setText("Ready: "+displayHost(url)+" · load "+lastLoadMillis+
                        "ms · text "+textZoom+"%, page "+pageZoom+"%.");
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
                lastRendererExit=(crashed?"crashed":"reclaimed")+
                    " after pointer action: "+lastPointerEvent;
                prefs.edit().putString("last_renderer_exit",lastRendererExit).apply();
                Log.e("FireBrowser","WebView renderer "+lastRendererExit);
                // The callback is already on the WebView/UI thread. Detach
                // and destroy this unusable renderer synchronously before
                // returning, rather than leaving it attached for another turn.
                stopMotion();
                resetWebHover(false);
                viewport.removeView(view);
                web=null;
                view.destroy();
                createWebView();
                rendererRecoveries++;
                if(!crashed && prior.startsWith("https://"))loadUrl(prior);
                else loadProbe();
                status.setText((crashed?"Web page renderer crashed.":"Fire OS reclaimed the renderer.")+
                    " Browser recovered ("+rendererRecoveries+"). "+
                    (crashed?"Last page preserved in RECENT; no automatic crash loop.":"Restoring last page."));
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
    private Button button(LinearLayout bar,String name,Runnable run) {
        Button b=new Button(this);
        b.setText(name);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(0xFFFFFFFF);b.setBackgroundColor(0xFF255477);
        b.setMinHeight(dp(40));b.setMinWidth(0);
        b.setPadding(dp(7),0,dp(7),0);
        b.setOnFocusChangeListener((v,focus)->b.setBackgroundColor(focus?0xFF1786BD:0xFF255477));
        b.setOnClickListener(v->run.run());
        bar.addView(b,new LinearLayout.LayoutParams(-2,dp(40)));
        return b;
    }
    private void showMore(){
        // Keep uncommon operations reachable without putting 20 buttons on screen.
        final String[] actions={"Open ChatGPT","Paste draft without sending","Text smaller","Text larger",
            "Page smaller","Page larger","Reset zoom to 100%","Input test page",
            "Browser diagnostics","Inspect with Chrome","Reduced motion",
            "Remote media shortcuts","Allow third-party cookies for sign-in",
            "Check browser update","Pin webpage to Playroom",
            "CONTROL WHEEL / named binding sets","EMERGENCY RESTORE / disable mappings"};
        new AlertDialog.Builder(this).setTitle("Browser options")
            .setItems(actions,(dlg,index)->{
                switch(index){
                    case 0:loadUrl(HOME);break;
                    case 1:pasteDraft();break;
                    case 2:changeTextZoom(-10);break;
                    case 3:changeTextZoom(10);break;
                    case 4:zoomPage(false);break;
                    case 5:zoomPage(true);break;
                    case 6:resetZoom();break;
                    case 7:loadProbe();break;
                    case 8:showDiagnostics();break;
                    case 9:toggleInspection();break;
                    case 10:toggleReducedMotion();break;
                    case 11:toggleRemoteShortcuts();break;
                    case 12:toggleThirdPartyCookies();break;
                    case 13:if(browserUpdater!=null)browserUpdater.check();break;
                    case 14:pinCurrentPage();break;
                    case 15:if(bindings!=null)bindings.showWheel();break;
                    case 16:emergencyRestoreBindings();break;
                }
            }).setNegativeButton("Close",null).show();
    }

    private void pinCurrentPage(){
        if(web==null || web.getUrl()==null || !web.getUrl().startsWith("https://")){
            status.setText("Open an HTTPS game page before pinning it.");return;
        }
        String current=web.getUrl();
        // The Console validates and strips transient URL tokens. A normal
        // app-to-app SEND intent pre-fills a draft and requires a real user
        // confirmation before anything is saved as a channel.
        try{
            Intent share=new Intent(Intent.ACTION_SEND);
            share.setClassName("dev.jadan.fire","dev.jadan.fire.GameShelfActivity");
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,current);
            String title=web.getTitle();
            if(title!=null)share.putExtra(Intent.EXTRA_SUBJECT,
                title.length()>60?title.substring(0,60):title);
            startActivity(share);
            status.setText("Opened Playroom pin draft. Confirm PIN there to save.");
        }catch(RuntimeException ex){
            status.setText("Fire Console Playroom not installed. Update Fire Console through the App Catalog.");
        }
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
    /**
     * Android routes some Fire TV media keys to the active MediaSession, not
     * Activity.dispatchKeyEvent. This session only exists when the user opts
     * into REMOTE shortcuts, and is active only while this Activity is resumed.
     * It does NOT install a background receiver or steal Home/Alexa/volume.
     */
    private void refreshMediaSession(){
        if(!remoteMediaShortcuts || !browserForeground){
            if(pointerMediaSession!=null)pointerMediaSession.setActive(false);
            return;
        }
        if(pointerMediaSession==null){
            pointerMediaSession=new MediaSession(this,"FireBrowserPointer");
            pointerMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            pointerMediaSession.setCallback(new MediaSession.Callback(){
                @Override public void onPlay(){toggleFromTransportAction("onPlay");}
                @Override public void onPause(){toggleFromTransportAction("onPause");}
                @Override public boolean onMediaButtonEvent(Intent intent){
                    KeyEvent event=intent==null?null:intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if(event==null)return false;
                    return handleMediaShortcut(event,"media session");
                }
            },new Handler(Looper.getMainLooper()));
            pointerMediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_PLAY|
                    PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_FAST_FORWARD|
                    PlaybackState.ACTION_REWIND)
                .setState(PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,1f).build());
        }
        pointerMediaSession.setActive(true);
    }
    private void toggleFromTransportAction(String source){
        if(!remoteMediaShortcuts || !browserForeground)return;
        lastMediaDelivery="media session: "+source;
        if(!remoteInputs.mediaDown(0,SystemClock.uptimeMillis()))return;
        togglePointer();
        status.setText("Remote pointer "+(pointerMode?"ON":"OFF")+" ("+source+").");
    }
    private boolean handleMediaShortcut(KeyEvent event,String source){
        // MediaSession events can bypass Activity.dispatchKeyEvent. The same
        // browser-local mapping priority applies in either route.
        if(bindings!=null && bindings.onKey(event,SystemClock.uptimeMillis()))return true;
        if(!remoteMediaShortcuts || !browserForeground)return false;
        int key=event.getKeyCode();
        boolean toggle=key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            key==KeyEvent.KEYCODE_MEDIA_PLAY || key==KeyEvent.KEYCODE_MEDIA_PAUSE;
        boolean back=key==KeyEvent.KEYCODE_MEDIA_REWIND;
        boolean forward=key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        if(!toggle && !back && !forward)return false;
        lastMediaDelivery=source+": "+KeyEvent.keyCodeToString(key)+
            " "+(event.getAction()==KeyEvent.ACTION_DOWN?"DOWN":"UP");
        long now=SystemClock.uptimeMillis();
        if(toggle){
            if(event.getAction()==KeyEvent.ACTION_UP){
                // Only a measured held press can enter binding capture. Some
                // Fire OS MediaSessions emit DOWN without UP: a timer alone
                // would wrongly treat every tap as a long press.
                if(mediaHeld && !mediaHoldTriggered &&
                   now-mediaHoldSince>=750 && bindings!=null && !imeVisible){
                    mediaHoldTriggered=true;
                    bindings.holdPlayPause();
                }
                mediaHeld=false;
                return true;
            }
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
            if(event.getRepeatCount()==0){
                mediaHeld=true;
                mediaHoldSince=now;
                mediaHoldTriggered=false;
            }else if(mediaHeld && !mediaHoldTriggered &&
                     now-mediaHoldSince>=750 && bindings!=null && !imeVisible){
                mediaHoldTriggered=true;
                bindings.holdPlayPause();
            }
            // Preserve prior tap behavior for media APIs without UP events.
            // Hold capture is unavailable there; SETS is its reliable path.
            if(remoteInputs.mediaDown(event.getRepeatCount(),now)){
                togglePointer();
                status.setText("Remote pointer "+(pointerMode?"ON":"OFF")+
                    " ("+source+"). Hold to bind when delivered; SETS always available.");
            }
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN &&
           event.getRepeatCount()==0 && now-lastMediaNavigationAt>=300){
            lastMediaNavigationAt=now;
            if(back)browseBack();else browseForward();
        }
        return true;
    }

    private void showKeyboard(View view){
        stopMotion();remoteInputs.cancelOk();
        // A keyboard owns the D-pad only while visible. Preserve the user's
        // pointer selection across this temporary focus/IME transition.
        view.requestFocus();
        InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(input!=null)input.showSoftInput(view,InputMethodManager.SHOW_IMPLICIT);
        status.setText("Requested Fire TV keyboard for native text field. Dictation should work here.");
    }
    private void loadUrl(String url){
        webEditorFocused=false;
        // A navigation does not change the operator's selected interaction mode.
        stopMotion();updateCursorVisual();
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
        stopMotion();updateCursorVisual();
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
        if(bindings!=null && bindings.activeMode())
            bindings.disable("Native pointer selected; mapped controls OFF.");
        stopMotion();remoteInputs.cancelOk();
        pointerMode=!pointerMode;
        if(pointerMode && imeVisible){
            // Explicit pointer activation is intent to exit text-entry mode.
            InputMethodManager im=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(im!=null)im.hideSoftInputFromWindow(screen.getWindowToken(),0);
        }
        updateCursorVisual();
        if(pointerMode){
            emitWebHover(true);
            status.setText("Pointer ON. D-pad moves the pointer; OK clicks once.");
        }else{
            resetWebHover(true);
            status.setText("Pointer OFF. D-pad navigates normal controls.");
        }
    }
    private void emergencyRestoreBindings(){
        bindingClock.removeCallbacks(rescueAction);
        mediaHeld=false;mediaHoldTriggered=false;
        consumeEmergencyOkUp=true;
        bindingSafety.cancel();
        if(bindings!=null)bindings.disable("Emergency: all custom controls OFF.");
        pointerMode=false;
        stopMotion();remoteInputs.cancelOk();
        resetWebHover(true);
        remoteMediaShortcuts=false;
        prefs.edit().putBoolean("remote_media_shortcuts",false).apply();
        refreshMediaSession();
        updateCursorVisual();
        status.setText("EMERGENCY RESTORE: normal D-pad / OK / Back restored. "+
            "Saved sets untouched. SETS / MENU to re-enable.");
    }
    private boolean observeEmergency(KeyEvent ev){
        if(imeVisible)return false; // System keyboard owns keys while shown.
        int key=ev.getKeyCode(),action=ev.getAction();
        long now=SystemClock.uptimeMillis();
        if((key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER ||
            key==KeyEvent.KEYCODE_NUMPAD_ENTER || key==KeyEvent.KEYCODE_BUTTON_A) &&
            consumeEmergencyOkUp){
            if(action==KeyEvent.ACTION_UP){
                consumeEmergencyOkUp=false;
                return true; // Emergency chord must never click after restore.
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(action==KeyEvent.ACTION_DOWN)bindingSafety.downPressed(ev.getRepeatCount());
            if(action==KeyEvent.ACTION_UP)bindingSafety.downReleased(now);
        }else if((key==KeyEvent.KEYCODE_DPAD_UP ||
                 key==KeyEvent.KEYCODE_DPAD_LEFT ||
                 key==KeyEvent.KEYCODE_DPAD_RIGHT) &&
                 action==KeyEvent.ACTION_DOWN && ev.getRepeatCount()==0){
            bindingSafety.otherDirection();
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER ||
           key==KeyEvent.KEYCODE_NUMPAD_ENTER || key==KeyEvent.KEYCODE_BUTTON_A){
            if(action==KeyEvent.ACTION_DOWN &&
                bindingSafety.okPressed(ev.getRepeatCount(),now)){
                bindingClock.removeCallbacks(rescueAction);
                bindingClock.postDelayed(rescueAction,BindingSafety.RESET_HOLD_MS);
            }else if(action==KeyEvent.ACTION_UP){
                bindingClock.removeCallbacks(rescueAction);
                // Completed emergency hold swallows its final OK release;
                // a short OK is passed to the normal pointer/key handler.
                return bindingSafety.okReleased(now);
            }
        }
        return false;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent ev){
        int key=ev.getKeyCode();
        if(ev.getAction()==KeyEvent.ACTION_UP ||
           (ev.getAction()==KeyEvent.ACTION_DOWN && ev.getRepeatCount()<=1)){
            if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER ||
               key==KeyEvent.KEYCODE_BUTTON_A || key==KeyEvent.KEYCODE_BUTTON_SELECT ||
               key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN ||
               key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT ||
               key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key==KeyEvent.KEYCODE_MEDIA_PLAY ||
               key==KeyEvent.KEYCODE_MEDIA_PAUSE || key==KeyEvent.KEYCODE_BACK){
                traceInput(KeyEvent.keyCodeToString(key)+
                    (ev.getAction()==KeyEvent.ACTION_DOWN?" DOWN":" UP")+
                    (ev.getRepeatCount()>0?" repeat="+ev.getRepeatCount():"")+
                    (imeVisible?" [IME]":pointerMode?" [POINTER]":" [FOCUS]"));
            }
        }
        if(key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER)
            lastRawEnter=KeyEvent.keyCodeToString(key)+", shift="+ev.isShiftPressed()+
                ", "+(ev.getAction()==KeyEvent.ACTION_UP?"UP":"DOWN");
        // Query IME ownership before the emergency chord. The system keyboard
        // owns these keys while shown, and the browser must never hijack them.
        WindowInsetsCompat visibleInsets=ViewCompat.getRootWindowInsets(screen);
        if(visibleInsets!=null)imeVisible=visibleInsets.isVisible(WindowInsetsCompat.Type.ime());
        if(observeEmergency(ev))return true;
        // Back remains an independent escape from any active profile.
        if(bindings!=null && bindings.activeMode() &&
           (key==KeyEvent.KEYCODE_BACK || key==KeyEvent.KEYCODE_BUTTON_B)){
            if(ev.getAction()==KeyEvent.ACTION_UP)
                bindings.disable("BACK: custom bindings OFF; browser controls restored.");
            return true;
        }
        // System-reserved Home, Alexa and volume keys are never remapped.
        if(key==KeyEvent.KEYCODE_MENU){
            if(ev.getAction()==KeyEvent.ACTION_UP)toggleTools();
            return true;
        }
        // A profile only captures its own mapped keys and never intercepts
        // MENU/OK/BACK or a visible Fire keyboard.
        if(bindings!=null && bindings.onKey(ev,SystemClock.uptimeMillis()))return true;
        // Activity key delivery is a fallback for keys not routed to a
        // foreground MediaSession.
        if(handleMediaShortcut(ev,"activity"))return true;
        // Raw Enter from a hardware keyboard may produce a newline in a rich
        // composer. Shift+Enter still passes through. A Fire OS IME may commit
        // text without key events; native SEND remains the reliable fallback.
        boolean softEnter=(ev.getFlags()&KeyEvent.FLAG_SOFT_KEYBOARD)!=0 ||
            ev.getDeviceId()==KeyCharacterMap.VIRTUAL_KEYBOARD;
        if(!pointerMode && webEditorFocused && web!=null && web.hasFocus() && isChatGPT() &&
            (key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER) &&
            (!ev.isShiftPressed() || softEnter)){
            if(ev.getAction()==KeyEvent.ACTION_UP){
                handleWebImeSubmit("raw Enter"+(ev.isShiftPressed()?" with soft Shift":""));
            }
            return true;
        }
        // Android's software keyboard must own D-pad/OK/Back while shown.
        // WindowInsetsCompat is more reliable than "which view is focused",
        // especially for Amazon's keyboard that takes focus asynchronously.
        WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(screen);
        if(insets!=null)imeVisible=insets.isVisible(WindowInsetsCompat.Type.ime());
        boolean pointerKeys=RemoteInputPolicy.pointerOwnsDpad(pointerMode,imeVisible);
        if(pointerMode && !pointerKeys){
            stopMotion();
            remoteInputs.cancelOk();
            return super.dispatchKeyEvent(ev);
        }
        if(pointerKeys){
            int k=ev.getKeyCode();
            if((k==KeyEvent.KEYCODE_BACK || k==KeyEvent.KEYCODE_BUTTON_B) &&
               ev.getAction()==KeyEvent.ACTION_UP){
                stopMotion();remoteInputs.cancelOk();
                if(bindings!=null && bindings.captureMode()){
                    bindings.disable("Binding cursor cancelled with BACK.");
                    return true;
                }
                if(web!=null && web.canGoBack()){
                    web.goBack();
                    status.setText("Back to previous webpage; pointer mode remains ON.");
                }else{
                    pointerMode=false;updateCursorVisual();
                    resetWebHover(true);
                    status.setText("No previous page. Pointer OFF; D-pad focus navigation restored.");
                }
                return true;
            }
            if(k==KeyEvent.KEYCODE_BACK || k==KeyEvent.KEYCODE_BUTTON_B)return true;
            if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||
               k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){
                if(ev.getAction()==KeyEvent.ACTION_UP){
                    if(heldKey==k)stopMotion();
                }else if(ev.getAction()==KeyEvent.ACTION_DOWN){
                    lastPointerInputAt=SystemClock.uptimeMillis();
                    if(heldKey!=k){
                        heldKey=k;heldSince=lastMove=lastPointerInputAt;
                        lastPointerEvent="pointer move";
                        moveCursor(k,dpFloat(2.5f));
                        schedulePointerFrame();
                    }
                }
                return true;
            }
            if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||
                k==KeyEvent.KEYCODE_NUMPAD_ENTER||k==KeyEvent.KEYCODE_BUTTON_A||
                k==KeyEvent.KEYCODE_BUTTON_SELECT){
                if(ev.getAction()==KeyEvent.ACTION_DOWN)
                    remoteInputs.okDown(ev.getRepeatCount());
                if(ev.getAction()==KeyEvent.ACTION_UP &&
                   remoteInputs.okUp(SystemClock.uptimeMillis()))clickPointer();
                return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }

    /**
     * Reuse the same native SOURCE_TOUCHSCREEN dispatch as the pointer.
     * Coordinates are relative to the target view, so browser toolbar height,
     * screen resolution, and webpage layout are not conflated.
     */
    private void deliverMappedTouch(BrowserBindings.Target target){
        if(!browserForeground || imeVisible || screen==null || web==null)return;
        if(target.isVirtualKey()){
            sendGameKey(target.virtualKey);
            return;
        }
        View receiver=target.web?web:screen;
        if(receiver.getWidth()<2||receiver.getHeight()<2 ||
           !receiver.isAttachedToWindow())return;
        float px=Math.max(1f,Math.min(receiver.getWidth()-1,target.x*receiver.getWidth()));
        float py=Math.max(1f,Math.min(receiver.getHeight()-1,target.y*receiver.getHeight()));
        long now=SystemClock.uptimeMillis();
        if(target.web){
            resetWebHover(true);
            if(!web.hasFocus())web.requestFocus();
        }
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,px,py,0);
        MotionEvent up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,px,py,0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{
            receiver.dispatchTouchEvent(down);
            receiver.dispatchTouchEvent(up);
            lastPointerEvent="bound "+(target.web?"webpage":"native control")+" tap";
        }catch(RuntimeException error){
            status.setText("Bound tap failed: "+error.getClass().getSimpleName()+
                ". MENU / SETS restores normal controls.");
        }finally{
            down.recycle();up.recycle();
        }
    }
    /**
     * A browser-owned virtual game pad. Amazon's separate system IME is not
     * part of the Activity's view tree and cannot be clicked this way.
     * Native buttons intentionally stay available to ordinary pointer taps.
     */
    private void createGameKeysPalette(){
        gameKeysPanel=new LinearLayout(this);
        gameKeysPanel.setOrientation(LinearLayout.HORIZONTAL);
        gameKeysPanel.setBackgroundColor(0xEE16364F);
        gameKeysPanel.setPadding(dp(6),dp(5),dp(6),dp(5));
        gameKeysPanel.setVisibility(View.GONE);
        final int[] keys={
            KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SPACE,KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_B,KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_D,KeyEvent.KEYCODE_ESCAPE
        };
        final String[] labels={"←","↑","↓","→","SPACE","ENTER","A","B","W","S","D","ESC"};
        for(int i=0;i<keys.length;i++){
            final int code=keys[i];
            Button button=new Button(this);
            button.setAllCaps(false);
            button.setText(labels[i]);
            button.setTextSize(12);
            button.setTextColor(0xFFFFFFFF);
            button.setMinHeight(dp(46));
            button.setBackgroundColor(0xFF225F86);
            button.setOnFocusChangeListener((v,focus)->
                button.setBackgroundColor(focus?0xFF1298C8:0xFF225F86));
            button.setOnClickListener(v->sendGameKey(code));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(50),1);
            params.rightMargin=dp(2);
            gameKeysPanel.addView(button,params);
            gameKeys.put(button,code);
        }
        FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(-1,dp(60),Gravity.BOTTOM);
        layout.bottomMargin=dp(30); // Keep native status diagnostics visible.
        screen.addView(gameKeysPanel,layout);
    }
    private void toggleGameKeys(){
        gameKeysVisible=!gameKeysVisible;
        updateGameKeys();
        if(status!=null)status.setText(gameKeysVisible?
            "GAME KEYS visible. CURSOR to tap; BIND cursor can assign remote keys. KEYS hides overlay.":
            "Game key palette hidden. Saved key bindings still work without the overlay.");
    }
    private void updateGameKeys(){
        if(gameKeysPanel==null)return;
        int desired=gameKeysVisible&&!imeVisible?View.VISIBLE:View.GONE;
        if(gameKeysPanel.getVisibility()!=desired)gameKeysPanel.setVisibility(desired);
    }
    private int gameKeyAt(float screenX,float screenY){
        if(gameKeysPanel==null||gameKeysPanel.getVisibility()!=View.VISIBLE)return -1;
        int[] screenLoc=new int[2];screen.getLocationOnScreen(screenLoc);
        for(java.util.Map.Entry<View,Integer> e:gameKeys.entrySet()){
            View view=e.getKey();
            if(!view.isShown()||view.getWidth()==0||view.getHeight()==0)continue;
            int[] loc=new int[2];view.getLocationOnScreen(loc);
            float left=loc[0]-screenLoc[0],top=loc[1]-screenLoc[1];
            if(screenX>=left&&screenX<left+view.getWidth()&&
               screenY>=top&&screenY<top+view.getHeight())return e.getValue();
        }
        return -1;
    }
    private void sendGameKey(int code){
        if(!browserForeground||imeVisible||web==null||!web.isAttachedToWindow()||
            !BrowserBindings.allowedVirtualKey(code))return;
        // A native KeyEvent path, not a privileged system input injection
        // and not site-specific JavaScript. Some games/WebViews may decline
        // these events, so test on the actual Fire OS renderer.
        web.requestFocus();
        long now=SystemClock.uptimeMillis();
        KeyEvent down=new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0,
            0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD);
        KeyEvent up=new KeyEvent(now,now+40,KeyEvent.ACTION_UP,code,0,
            0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD);
        web.dispatchKeyEvent(down);
        web.dispatchKeyEvent(up);
        lastPointerEvent="native game key "+KeyEvent.keyCodeToString(code);
    }

    private static final String WEB_EDITOR_CHECK =
        "(function(){var e=document.activeElement;if(!e)return 'NONE';"+
        "var c=document.querySelector('#prompt-textarea,[contenteditable=\"true\"].ProseMirror');"+
        "if(c&&(e===c||c.contains(e)))return 'COMPOSER';"+
        "var tag=e.tagName||'';"+
        "if(e.isContentEditable||tag==='TEXTAREA'||"+
        "(tag==='INPUT'&&!/^(hidden|button|submit|reset|checkbox|radio|file|range)$/i.test(e.type||'')))"+
        "return 'EDITOR';return 'NONE';})()";

    private void clickPointer(){
        if(screen==null||web==null)return;
        stopMotion();
        // Screen-coordinate overlays sit visually above WebView. Give them
        // priority or a "native" pointer tap will click through into the page.
        float globalPx=x*screen.getWidth(),globalPy=y*screen.getHeight();
        int gameKey=gameKeyAt(globalPx,globalPy);
        if(bindings!=null && bindings.captureMode()){
            float px=globalPx,py=globalPy;
            if(gameKey>=0){
                bindings.captureTarget(new BrowserBindings.Target(gameKey));
                return; // binding a key must not type into the webpage
            }
            int[] screenLocation=new int[2],webLocation=new int[2];
            screen.getLocationOnScreen(screenLocation);
            web.getLocationOnScreen(webLocation);
            float webX=px+screenLocation[0]-webLocation[0];
            float webY=py+screenLocation[1]-webLocation[1];
            boolean inWeb=webX>=0&&webY>=0&&
                webX<web.getWidth()&&webY<web.getHeight()&&
                web.getVisibility()==View.VISIBLE;
            bindings.captureTarget(inWeb?
                new BrowserBindings.Target(true,
                    webX/Math.max(1,web.getWidth()),webY/Math.max(1,web.getHeight())):
                new BrowserBindings.Target(false,
                    px/Math.max(1,screen.getWidth()),py/Math.max(1,screen.getHeight())));
            return; // Binding selection never clicks the underlying webpage.
        }
        remoteInputs.cancelOk();
        long time=SystemClock.uptimeMillis();
        lastOk=time;
        if(gameKey>=0){
            sendGameKey(gameKey);
            return; // never click WebView behind the GAME KEYS overlay
        }
        final long clickId=++pointerClickGeneration;
        float px=x*screen.getWidth(),py=y*screen.getHeight();
        int[] screenLocation=new int[2],webLocation=new int[2];
        screen.getLocationOnScreen(screenLocation);
        web.getLocationOnScreen(webLocation);
        float webX=px+screenLocation[0]-webLocation[0];
        float webY=py+screenLocation[1]-webLocation[1];
        boolean inWeb=webX>=0&&webY>=0&&webX<web.getWidth()&&webY<web.getHeight()
            &&web.getVisibility()==View.VISIBLE;
        lastPointerEvent=inWeb?"single WebView tap":"single browser control tap";
        View receiver=inWeb?web:screen;
        float rx=inWeb?webX:px,ry=inWeb?webY:py;
        // Touchscreen source is essential. An unspecified-source MotionEvent
        // can be interpreted differently by the Amazon Chromium WebView.
        if(inWeb){
            // A real mouse sends HOVER_EXIT before its DOWN.
            resetWebHover(true);
            if(!web.hasFocus())web.requestFocus();
        }
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,rx,ry,0);
        MotionEvent up=MotionEvent.obtain(time,time+60,MotionEvent.ACTION_UP,rx,ry,0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{
            receiver.dispatchTouchEvent(down);
            receiver.dispatchTouchEvent(up);
        }finally{down.recycle();up.recycle();}
        if(!inWeb){
            View focused=screen.findFocus();
            if(focused instanceof EditText){
                stopMotion();remoteInputs.cancelOk();
                resetWebHover(true);
                InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(input!=null)input.showSoftInput(focused,InputMethodManager.SHOW_IMPLICIT);
            }
            return;
        }
        // Do not request focus AGAIN after the tap: it can steal focus from
        // the HTML input the user just selected. Never toggle pointerMode
        // automatically from a delayed DOM callback.
        final WebView clicked=web;
        clicked.postDelayed(()->{
            if(web!=clicked || pointerClickGeneration!=clickId)return;
            clicked.evaluateJavascript(WEB_EDITOR_CHECK,result->{
                if(web!=clicked || pointerClickGeneration!=clickId)return;
                boolean composer="\"COMPOSER\"".equals(result);
                boolean editor=composer || "\"EDITOR\"".equals(result);
                lastWebEditor=composer?"ChatGPT composer":editor?"web text field":"none";
                webEditorFocused=composer;
                if(editor){
                    InputMethodManager im=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(im!=null)im.showSoftInput(clicked,InputMethodManager.SHOW_IMPLICIT);
                    status.setText("Text field selected; keyboard requested. Pointer mode retained.");
                }else status.setText("Page tapped once; pointer remains ON.");
            });
        },140);
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
    private static boolean isFireInboxUrl(String raw){
        try{
            Uri uri=Uri.parse(raw);
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                "fire-inbox-jadan.brinyduck2.chatgpt.site".equalsIgnoreCase(uri.getHost());
        }catch(Exception ex){return false;}
    }
    private boolean isFireInbox(){
        return web!=null && isFireInboxUrl(web.getUrl());
    }
    /**
     * Fire Inbox is a standard signed-in HTML form, not a ChatGPT model API.
     * Reuse the existing native dictation input; hand it to the site's form
     * using its own browser cookies, CSRF/nonce and POST route.
     * Only a response redirect containing posted=1 confirms persistence.
     */
    private void postInboxDraft(){
        if(web==null)return;
        String value=draft.getText().toString();
        if(value.trim().isEmpty()){
            status.setText("Dictate or type a note before posting.");return;
        }
        if(value.length()>12000){
            status.setText("Fire Inbox accepts at most 12,000 characters per note.");return;
        }
        long now=SystemClock.uptimeMillis();
        if(lastSend!=0 && now-lastSend<1200){
            status.setText("Post already requested. Check the inbox before retrying.");return;
        }
        lastSend=now;
        pendingInboxText=value;
        final WebView target=web;
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null)imm.hideSoftInputFromWindow(draft.getWindowToken(),0);
        status.setText("Submitting note to Fire Inbox…");
        String js="(function(){"+
            "var f=document.querySelector('form[action=\"/api/messages\"]');"+
            "var input=f&&f.querySelector('textarea[name=\"message\"]');"+
            "var nonce=f&&f.querySelector('input[name=\"nonce\"]');"+
            "if(!f||!input||!nonce||!nonce.value)return 'NOT_SIGNED_IN_OR_FORM_MISSING';"+
            "input.value="+JSONObject.quote(value)+";"+
            "input.dispatchEvent(new Event('input',{bubbles:true}));"+
            "if(typeof f.requestSubmit==='function')f.requestSubmit();"+
            "else f.submit();return 'POST_STARTED';})()";
        target.evaluateJavascript(js,result->{
            if(web!=target)return;
            if(result!=null && result.contains("NOT_SIGNED_IN_OR_FORM_MISSING")){
                pendingInboxText=null;
                status.setText("Inbox form not available. Sign in to the Site or reload, then retry.");
            }
            // A started POST is NOT proof it was stored. Wait for posted=1.
        });
    }
    private void sendDraft(){
        if(web==null){status.setText("Browser recovering; no message was sent.");return;}
        if(isFireInbox()){postInboxDraft();return;}
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
    /**
     * Wheel events at the WebView coordinate keep scrolling under Chromium's
     * normal scroll hit-testing, including internal scrollable panels. Never
     * use web.scrollBy: that only scrolls the top-level viewport.
     */
    private void maybeEdgeScroll(int key){
        if((key!=KeyEvent.KEYCODE_DPAD_UP && key!=KeyEvent.KEYCODE_DPAD_DOWN)
            || !pointerMode || web==null || screen==null)return;
        long now=SystemClock.uptimeMillis();
        if(now-lastEdgeScrollMs<110)return;
        WebView target=web;
        if(target.getWidth()<=0 || target.getHeight()<=0 || !target.isAttachedToWindow())return;
        int[] rootPosition=new int[2], pagePosition=new int[2];
        screen.getLocationOnScreen(rootPosition);
        target.getLocationOnScreen(pagePosition);
        float pageLeft=pagePosition[0]-rootPosition[0];
        float pageTop=pagePosition[1]-rootPosition[1];
        float px=x*screen.getWidth(),py=y*screen.getHeight();
        if(px<pageLeft || px>pageLeft+target.getWidth())return;
        boolean down=key==KeyEvent.KEYCODE_DPAD_DOWN;
        boolean atEdge=down ? py>=pageTop+target.getHeight()-dp(16)
            : py<=pageTop+dp(16) && py>=pageTop-dp(20);
        if(!atEdge)return;
        lastEdgeScrollMs=now;
        lastPointerEvent=down?"pointer wheel down":"pointer wheel up";
        MotionEvent.PointerProperties properties=new MotionEvent.PointerProperties();
        properties.id=0;
        properties.toolType=MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();
        coords.x=Math.max(1,Math.min(target.getWidth()-1,px-pageLeft));
        coords.y=Math.max(1,Math.min(target.getHeight()-1,
            down?target.getHeight()-dp(14):dp(14)));
        float magnitude=1f+Math.min(2.5f,Math.max(0f,(now-heldSince-600)/1800f));
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL,down?-magnitude:magnitude);
        MotionEvent wheel=MotionEvent.obtain(now,now,MotionEvent.ACTION_SCROLL,
            1,new MotionEvent.PointerProperties[]{properties},
            new MotionEvent.PointerCoords[]{coords},0,0,1f,1f,0,0,
            InputDevice.SOURCE_MOUSE,0);
        try{
            if(web==target)target.dispatchGenericMotionEvent(wheel);
        }catch(RuntimeException error){
            Log.e("FireBrowser","Pointer edge-scroll dispatch failed",error);
            stopMotion();
            status.setText("Pointer scroll failed. See ADB diagnostic logs.");
        }finally{
            wheel.recycle();
        }
    }
    private void stopMotion(){
        heldKey=-1;
        lastFrameNs=0L;
        if(frameScheduled){
            Choreographer.getInstance().removeFrameCallback(pointerFrame);
            frameScheduled=false;
        }
    }
    /**
     * Android WebView handles native SOURCE_MOUSE hover events through its
     * generic-motion pipeline. Unlike merely translating the drawn pointer,
     * this reaches a page's :hover/mousemove behavior (e.g. video toolbars).
     * Dispatch at most ~30 times/sec to avoid saturating a weak TV renderer.
     */
    private void sendWebHover(WebView target,int action,float localX,float localY){
        if(target==null || !target.isAttachedToWindow())return;
        long now=SystemClock.uptimeMillis();
        MotionEvent.PointerProperties property=new MotionEvent.PointerProperties();
        property.id=0;property.toolType=MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coordinate=new MotionEvent.PointerCoords();
        coordinate.x=localX;coordinate.y=localY;
        MotionEvent ev=MotionEvent.obtain(now,now,action,1,
            new MotionEvent.PointerProperties[]{property},
            new MotionEvent.PointerCoords[]{coordinate},
            0,0,1f,1f,0,0,InputDevice.SOURCE_MOUSE,0);
        try{
            target.dispatchGenericMotionEvent(ev);
        }catch(RuntimeException error){
            Log.w("FireBrowser","Synthetic hover not supported by WebView provider",error);
            lastHoverEvent="WebView hover dispatch failed";
        }finally{ev.recycle();}
    }
    private void resetWebHover(boolean notify){
        if(hoveredWeb!=null && notify)
            sendWebHover(hoveredWeb,MotionEvent.ACTION_HOVER_EXIT,lastHoverX,lastHoverY);
        hoveredWeb=null;
        lastHoverMs=0L;
    }
    private void emitWebHover(boolean force){
        if(web==null || screen==null ||
           !RemoteInputPolicy.pointerOwnsDpad(pointerMode,imeVisible)){
            resetWebHover(true);return;
        }
        WebView target=web;
        if(gameKeyAt(x*screen.getWidth(),y*screen.getHeight())>=0){
            resetWebHover(true);
            return; // overlay owns this region, not the webpage underneath
        }
        if(target.getWidth()<2 || target.getHeight()<2 || !target.isAttachedToWindow()){
            resetWebHover(false);return;
        }
        int[] rootPos=new int[2],viewPos=new int[2];
        screen.getLocationOnScreen(rootPos);
        target.getLocationOnScreen(viewPos);
        float localX=x*screen.getWidth()+rootPos[0]-viewPos[0];
        float localY=y*screen.getHeight()+rootPos[1]-viewPos[1];
        boolean inside=target.getVisibility()==View.VISIBLE &&
            localX>=0 && localX<target.getWidth() &&
            localY>=0 && localY<target.getHeight();
        if(!inside){
            resetWebHover(true);return;
        }
        if(hoveredWeb!=null && hoveredWeb!=target)resetWebHover(true);
        long now=SystemClock.uptimeMillis();
        if(hoveredWeb==target && !force){
            if(now-lastHoverMs<33)return;
            float dx=localX-lastHoverX,dy=localY-lastHoverY;
            if(dx*dx+dy*dy<1f)return;
        }
        if(hoveredWeb!=target)
            sendWebHover(target,MotionEvent.ACTION_HOVER_ENTER,localX,localY);
        sendWebHover(target,MotionEvent.ACTION_HOVER_MOVE,localX,localY);
        hoveredWeb=target;
        lastHoverX=localX;lastHoverY=localY;lastHoverMs=now;
        lastHoverEvent="mouse hover at WebView coordinates";
    }
    private void updateCursorVisual(){
        if(pointerButton!=null){
            String label=pointerMode?"POINTER ON":"CURSOR";
            // Pointer animation runs every frame: never re-layout the toolbar
            // 60 times/sec merely to set an unchanged button label.
            if(!label.contentEquals(pointerButton.getText()))pointerButton.setText(label);
        }
        if(cursor==null || screen==null)return;
        int visibility=pointerMode && !imeVisible?View.VISIBLE:View.GONE;
        if(cursor.getVisibility()!=visibility)cursor.setVisibility(visibility);
        // Color is only redrawn in binding-capture mode, not on every
        // ordinary animation frame in a heavyweight WebView.
        if(bindings!=null&&bindings.captureMode())cursor.invalidate();
        if(screen.getWidth()==0 || screen.getHeight()==0)return;
        cursor.setTranslationX(x*screen.getWidth()-cursor.getWidth()*.5f);
        cursor.setTranslationY(y*screen.getHeight()-cursor.getHeight()*.5f);
    }
    private void moveCursor(int key,float delta){
        if(!RemoteInputPolicy.pointerOwnsDpad(pointerMode,imeVisible) || viewport==null)return;
        float w=Math.max(1,screen.getWidth()),h=Math.max(1,screen.getHeight());
        if(key==KeyEvent.KEYCODE_DPAD_LEFT)x=Math.max(.005f,x-delta/w);
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT)x=Math.min(.995f,x+delta/w);
        if(key==KeyEvent.KEYCODE_DPAD_UP)y=Math.max(.005f,y-delta/h);
        if(key==KeyEvent.KEYCODE_DPAD_DOWN)y=Math.min(.995f,y+delta/h);
        updateCursorVisual();
        emitWebHover(false);
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
        sb.append("Last pointer action: ").append(lastPointerEvent).append('\n');
        sb.append("Last synthetic hover: ").append(lastHoverEvent).append('\n');
        sb.append("Last WebView editor tapped: ").append(lastWebEditor).append('\n');
        sb.append("Last renderer exit: ").append(lastRendererExit).append('\n');
        sb.append("Cookie storage: ").append(CookieManager.getInstance().acceptCookie()?"enabled":"disabled").append('\n');
        sb.append("Third-party cookies: ").append(thirdPartyCookies).append('\n');
        try{
            String current=web==null?null:web.getUrl();
            if(current!=null && current.startsWith("https://")){
                String cookies=CookieManager.getInstance().getCookie(current);
                sb.append("Cookie presence on current page: ")
                    .append(cookies!=null && !cookies.isEmpty()?"yes":"no").append('\n');
            }
        }catch(RuntimeException ex){
            sb.append("Cookie lookup: unavailable").append('\n');
        }
        sb.append("Last web IME submit: ").append(lastWebImeAction).append('\n');
        sb.append("Control bindings: ").append(bindings==null?"unavailable":
          bindings.activeMode()?"ACTIVE "+bindings.selectedLabel():
          bindings.captureMode()?"CAPTURE "+bindings.selectedLabel():"OFF").append('\n');
        sb.append("Game-key overlay: ").append(gameKeysVisible).append('\n');
        sb.append("Media-button shortcuts: ").append(remoteMediaShortcuts).append('\n');
        sb.append("Media session active: ").append(pointerMediaSession!=null && pointerMediaSession.isActive()).append('\n');
        sb.append("Last media delivery: ").append(lastMediaDelivery).append('\n');
        sb.append("Navigation preserves pointer mode: yes").append('\n');
        sb.append("Software keyboard visible: ").append(imeVisible).append('\n');
        sb.append("Remote OK guard ms: ").append(RemoteInputPolicy.OK_DEBOUNCE_MS).append('\n');
        sb.append("Last remote event transitions (codes only):").append('\n');
        for(String transition:remoteTrace)sb.append("  ").append(transition).append('\n');
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
        refreshMediaSession();
        status.setText(remoteMediaShortcuts?
            "REMOTE ON: Play/Pause toggles pointer on any page; Rewind/FF navigate. Media playback buttons are intercepted while Fire Browser is in front. MENU and CURSOR remain alternatives.":
            "REMOTE OFF: media buttons restored to system/video handling; CURSOR always works.");
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
    private void scheduleCookieFlush(){
        if(cookieWriter.isShutdown())return;
        try {
            cookieWriter.execute(()->{
                try{
                    // Can perform disk I/O: never block the Fire TV UI thread.
                    CookieManager.getInstance().flush();
                }catch(RuntimeException error){
                    Log.e("FireBrowser","Persistent cookie flush failed",error);
                }
            });
        }catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
    private void toggleThirdPartyCookies(){
        final boolean enabling=!thirdPartyCookies;
        if(enabling){
            new AlertDialog.Builder(this)
                .setTitle("Allow third-party cookies?")
                .setMessage("Some login providers need third-party cookies. This also allows tracking cookies on other sites loaded in Fire Browser. Enable only if sign-in fails without it.")
                .setPositiveButton("ALLOW",(dialog,which)->applyThirdPartyCookies(true))
                .setNegativeButton("Cancel",null).show();
        }else applyThirdPartyCookies(false);
    }
    private void applyThirdPartyCookies(boolean allowed){
        thirdPartyCookies=allowed;
        prefs.edit().putBoolean("third_party_cookies",allowed).apply();
        if(web!=null)CookieManager.getInstance().setAcceptThirdPartyCookies(web,allowed);
        status.setText("Third-party cookies "+(allowed?"ON":"OFF")+
            ". Try signing in again if necessary. Existing cookies were not cleared.");
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
        if(!imeVisible && bindings!=null && bindings.activeMode()){
            bindings.disable("BACK: custom bindings OFF; browser controls restored.");
            return;
        }
        // On Fire TV the IME sometimes lets Back reach the Activity. This must
        // dismiss text entry instead of unexpectedly navigating away.
        if(imeVisible){
            stopMotion();
            remoteInputs.cancelOk();
            InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(input!=null)input.hideSoftInputFromWindow(screen.getWindowToken(),0);
            status.setText("Keyboard dismissed; previous pointer selection preserved.");
            return;
        }
        if(bindings!=null && bindings.captureMode()){
            bindings.disable("Binding cursor cancelled with BACK.");
            return;
        }
        if(pointerMode){
            stopMotion();
            remoteInputs.cancelOk();
            if(web!=null&&web.canGoBack()){
                web.goBack();
                status.setText("Back to previous webpage; pointer remains ON.");
            }else{
                pointerMode=false;updateCursorVisual();
                status.setText("No previous page; pointer OFF.");
            }
            return;
        }
        if(web!=null&&web.canGoBack()){web.goBack();return;}
        super.onBackPressed();
    }
    @Override protected void onPause(){
        bindingClock.removeCallbacks(rescueAction);
        bindingSafety.cancel();
        consumeEmergencyOkUp=false;
        mediaHeld=false;
        if(bindings!=null)bindings.stopInput();
        resetWebHover(true);
        remoteInputs.cancelOk();
        browserForeground=false;
        refreshMediaSession();
        stopMotion();
        scheduleCookieFlush();
        if(devtoolsEnabled)WebView.setWebContentsDebuggingEnabled(false);
        if(web!=null)web.onPause();
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        browserForeground=true;
        if(pointerMode)screen.post(()->emitWebHover(true));
        refreshMediaSession();
        if(devtoolsEnabled)WebView.setWebContentsDebuggingEnabled(true);
        if(web!=null)web.onResume();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus){
                bindingClock.removeCallbacks(rescueAction);
            bindingSafety.cancel();
            consumeEmergencyOkUp=false;
            mediaHeld=false;
            if(bindings!=null)bindings.stopInput();
            stopMotion();
            remoteInputs.cancelOk();
            resetWebHover(true);
        }
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
        bindingClock.removeCallbacks(rescueAction);
        resetWebHover(false);
        if(browserUpdater!=null)browserUpdater.close();
        if(pointerMediaSession!=null){
            pointerMediaSession.setActive(false);
            pointerMediaSession.release();
            pointerMediaSession=null;
        }
        scheduleCookieFlush();
        cookieWriter.shutdown();
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
