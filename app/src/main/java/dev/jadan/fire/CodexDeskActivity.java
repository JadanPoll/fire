package dev.jadan.fire;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.ArrayList;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import java.util.List;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Arrays;

/**
 * On-device Codex UX. Real on-TV entry and Codex Cloud opening are available now.
 * Native Codex app-server execution is a separate, capability-gated milestone:
 * no credentials are embedded, and a normal app can't execute arbitrary packages
 * from another app's private directory.
 */
public final class CodexDeskActivity extends Activity {
    private EditText prompt;
    private TextView details, feedback, speechStatus;
    private String lastInput="";
    private static final String CODEX_URL = "https://chatgpt.com/codex";
    private static final String CHAT_URL = "https://chatgpt.com/";
    private static final String SILK_PACKAGE = "com.amazon.cloud9";
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroller = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(28,20,28,20);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF102132);
        scroller.addView(root);
        setContentView(scroller);
        root.addView(label("CODEX  /  ON FIRE",27,0xFF64CAFF));
        root.addView(label("Same workspace goal: run Codex here, or continue on a trusted remote executor. Voice and typing share one intention.",16,0xFFDEEBF3));

        prompt = new EditText(this);
        prompt.setSingleLine(false);
        prompt.setMinLines(3);
        prompt.setMaxLines(5);
        prompt.setTextColor(0xFF172334);
        prompt.setHintTextColor(0xFF607080);
        prompt.setBackgroundColor(0xFFE3EEF5);
        prompt.setPadding(16,16,16,16);
        prompt.setHint("Describe your next experiment. You can try the Fire TV keyboard microphone.");
        prompt.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        root.addView(prompt,new LinearLayout.LayoutParams(-1,-2));
        speechStatus = label("Tap SPEAK VIA FIRE KEYBOARD, then hold the remote's mic button if the keyboard supports dictation.",15,0xFF96C2DB);
        root.addView(speechStatus);
        prompt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                lastInput=s.toString();
                speechStatus.setText("Text received ("+lastInput.length()+" characters). Review before sending.");
            }
            @Override public void afterTextChanged(Editable e){}
        });
        button(root,"SPEAK / TYPE USING FIRE KEYBOARD",()-> {
            prompt.requestFocus();
            InputMethodManager input=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(input!=null)input.showSoftInput(prompt,InputMethodManager.SHOW_IMPLICIT);
            speechStatus.setText("Keyboard requested. Hold the remote mic button and speak if dictation appears; otherwise type.");
        });
        button(root,"COPY PROMPT + OPEN CODEX IN SILK",() -> launchSite(CODEX_URL,"silk",true));
        button(root,"OPEN CODEX — LIST AVAILABLE BROWSERS",this::chooseBrowser);
        button(root,"TRY FIRE'S OWN WEBVIEW BROWSER",this::openFireBrowser);
        button(root,"OPEN CHATGPT HOME IN SILK",() -> launchSite(CHAT_URL,"silk",false));
        button(root,"OPEN SILK ADDRESS BAR (NO LINK)",this::launchSilkHome);
        button(root,"COPY PROMPT ONLY",this::copy);
        button(root,"REFRESH BROWSER DIAGNOSTICS",this::browserDiagnostics);
        root.addView(label("LOCAL CODEX HARDWARE PRECHECK",21,0xFF64CAFF));
        details=label("",15,0xFFFFFFFF);
        root.addView(details);
        button(root,"REFRESH ON-FIRE CAPABILITIES",this::scan);
        feedback=label("Browser handoff is optional; the speech-dictated prompt stays in this app. No Codex credentials are stored.",15,0xFF9FC8DD);
        root.addView(feedback);
        root.addView(label("If ChatGPT fails to load, choose a different browser, try ChatGPT Home, or open Silk directly. An old browser can fail even when our link opens correctly. Native Codex remains a separate execution path.",14,0xFFACC3D3));
        if (b!=null) prompt.setText(b.getString("fire_draft",""));
        scan();
        browserDiagnostics();
    }
    private TextView label(String t,int sp,int color){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(color);
        v.setPadding(0,12,0,12);return v;
    }
    private void button(LinearLayout root,String t,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(t);b.setMinHeight(68);
        b.setTextColor(0xFFFFFFFF);b.setBackgroundColor(0xFF2D4B65);
        b.setOnFocusChangeListener((v,f)->b.setBackgroundColor(f?0xFF176FA3:0xFF2D4B65));
        b.setOnClickListener(v->action.run());
        root.addView(b);
    }
    private void scan(){
        StringBuilder result=new StringBuilder();
        result.append("Fire OS/Android API: ").append(Build.VERSION.SDK_INT).append("\n");
        result.append("Device: ").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL).append("\n");
        result.append("SUPPORTED_ABIS: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append("\n");
        result.append("SUPPORTED_64_BIT_ABIS: ").append(Arrays.toString(Build.SUPPORTED_64_BIT_ABIS)).append("\n");
        result.append("OS arch: ").append(System.getProperty("os.arch")).append("\n");
        ActivityManager manager=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        if(manager!=null){
            ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(mem);
            result.append("Memory available / total MiB: ").append(mem.availMem/(1024*1024))
                    .append(" / ").append(mem.totalMem/(1024*1024)).append("\n");
            result.append("Android low-memory flag: ").append(mem.lowMemory).append("\n");
        }
        File files=getFilesDir();
        result.append("App private storage free MiB: ").append(files.getUsableSpace()/(1024*1024)).append("\n");
        Intent termux=getPackageManager().getLaunchIntentForPackage("com.termux");
        result.append("Termux launcher visible: ").append(termux!=null).append("\n");
        if(Build.SUPPORTED_64_BIT_ABIS.length==0)result.append("Probe outcome: no 64-bit Android ABI advertised; 64-bit Codex forks cannot run here.\n");
        else if(Build.VERSION.SDK_INT<29)result.append("Probe outcome: 64-bit ABI available, but current Termux Codex ports may require Android API 29+. Find a verified API-28 build.\n");
        else result.append("Probe outcome: Android ABI plausibly suitable for an Android-native Codex port; execution untested.\n");
        details.setText(result.toString());
    }
    private void copy(){
        if(copyInternal()){
            feedback.setText("Prompt copied to clipboard. Paste it into Codex in an available browser or paired workstation.");
        } else {
            feedback.setText("Nothing to copy. Dictate or type an intention first.");
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("fire_draft",prompt.getText().toString());
        super.onSaveInstanceState(out);
    }

    private static Intent webIntent(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        return i;
    }

    /** Report what Android intends to launch, not whether the destination site can load. */
    private void browserDiagnostics(){
        PackageManager pm = getPackageManager();
        Intent test = webIntent(CODEX_URL);
        StringBuilder info = new StringBuilder();
        try {
            ResolveInfo chosen = pm.resolveActivity(test,PackageManager.MATCH_DEFAULT_ONLY);
            info.append("Android default link handler: ");
            info.append(chosen == null || chosen.activityInfo == null ? "NONE"
                    : chosen.activityInfo.packageName).append("\n");
            List<ResolveInfo> handlers=pm.queryIntentActivities(test,PackageManager.MATCH_DEFAULT_ONLY);
            info.append("Available handlers: ").append(handlers.size()).append("\n");
            for (int j=0; j<Math.min(6,handlers.size()); j++) {
                ResolveInfo h=handlers.get(j);
                if(h.activityInfo!=null)
                    info.append(" - ").append(h.loadLabel(pm)).append(" (")
                            .append(h.activityInfo.packageName).append(")\n");
            }
            pm.getPackageInfo(SILK_PACKAGE,0);
            info.append("Amazon Silk package: present\n");
        } catch (PackageManager.NameNotFoundException ex){
            info.append("Amazon Silk package: not found\n");
        } catch (RuntimeException ex) {
            info.append("Unable to inspect browser handlers: ").append(ex.getMessage()).append("\n");
        }
        info.append("The handler is not proof of page compatibility.\n");
        feedback.setText(info.toString());
    }

    private void launchSite(String url,String route,boolean withCopy) {
        if(withCopy && !copyInternal()) {
            feedback.setText("No prompt to copy. Dictate or type a request first; browser controls are still available.");
            return;
        }
        try {
            Intent i=webIntent(url);
            if("silk".equals(route)) {
                i.setPackage(SILK_PACKAGE);
                // Explicitly target Silk rather than the Fire OS default web link handler.
                // If Silk is absent, offer the system browser chooser instead.
                if(i.resolveActivity(getPackageManager())==null) {
                    i=Intent.createChooser(webIntent(url),"Choose installed browser");
                    feedback.setText("Silk not available. Showing available browser options.");
                }
            } else {
                // Browser selection is handled explicitly, not through Android's implicit chooser.
                chooseBrowser();
                return;
            }
            startActivity(i);
            feedback.setText((withCopy?"Prompt copied. ":"") +
                    "Browser launch requested for "+url+". This does not confirm ChatGPT loaded. " +
                    "Return to fire if it fails; your dictated draft remains here.");
        } catch (ActivityNotFoundException ex) {
            feedback.setText("No app can open ChatGPT links. Install or launch Silk using the Fire TV Apps menu. " +
                    "Your prompt remains in fire.");
        } catch (SecurityException ex) {
            feedback.setText("Fire OS denied opening this browser: "+ex.getMessage());
        } catch (RuntimeException ex) {
            feedback.setText("Browser launch failed: "+ex.getClass().getSimpleName()+": "+ex.getMessage());
        }
    }

    private void openFireBrowser() {
        Intent intent=new Intent(this,FireBrowserActivity.class);
        intent.putExtra("draft",prompt.getText().toString());
        startActivity(intent);
    }

    /** Show an actual list even if Android exposes only one browser handler. */
    private void chooseBrowser() {
        PackageManager pm=getPackageManager();
        List<ResolveInfo> found=pm.queryIntentActivities(webIntent(CODEX_URL),PackageManager.MATCH_DEFAULT_ONLY);
        ArrayList<ResolveInfo> handlers=new ArrayList<>();
        ArrayList<String> titles=new ArrayList<>();
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(ResolveInfo candidate:found) {
            if(candidate.activityInfo==null)continue;
            String pkg=candidate.activityInfo.packageName;
            if(ids.add(pkg)){
                handlers.add(candidate);
                titles.add(candidate.loadLabel(pm)+" ("+pkg+")");
            }
        }
        titles.add("Fire Browser Lab (built-in WebView)");
        final int builtIn=handlers.size();
        new AlertDialog.Builder(this)
            .setTitle(handlers.size()==1?"Only one external browser detected":"Select a browser")
            .setMessage(handlers.size()==1?"Android only reports one handler. Previous 'Choose Browser' opened it directly because no alternative was available.":null)
            .setItems(titles.toArray(new String[0]),(dialog,which)->{
                if(which==builtIn){openFireBrowser();return;}
                ResolveInfo target=handlers.get(which);
                Intent intent=webIntent(CODEX_URL);
                intent.setClassName(target.activityInfo.packageName,target.activityInfo.name);
                try{startActivity(intent);
                    feedback.setText("Launched "+target.loadLabel(pm)+". The website's textbox may still be incompatible.");
                }catch(Exception ex){feedback.setText("Browser launch failed: "+ex.getMessage());}
            })
            .setNegativeButton("Cancel",null).show();
    }

    private void launchSilkHome(){
        try {
            Intent i=getPackageManager().getLaunchIntentForPackage(SILK_PACKAGE);
            if(i==null) {
                feedback.setText("Silk launcher not found. Install or open the Internet app from Fire TV Apps.");
                return;
            }
            startActivity(i);
            feedback.setText("Silk launched directly. Enter chatgpt.com/codex in its address bar. " +
                    "Your dictated prompt is still saved in this screen.");
        } catch(Exception ex) {
            feedback.setText("Could not launch Silk: "+ex.getMessage());
        }
    }

    private boolean copyInternal(){
        String s=prompt.getText().toString().trim();
        if(s.length()==0) return false;
        ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(c==null)return false;
        c.setPrimaryClip(ClipData.newPlainText("Fire Codex intention",s));
        return true;
    }
}
