package dev.jadan.fire;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
        button(root,"COPY INTENTION TO CLIPBOARD",this::copy);
        button(root,"OPEN CODEX CLOUD ON FIRE TV",this::openCodex);
        root.addView(label("LOCAL CODEX HARDWARE PRECHECK",21,0xFF64CAFF));
        details=label("",15,0xFFFFFFFF);
        root.addView(details);
        button(root,"REFRESH ON-FIRE CAPABILITIES",this::scan);
        feedback=label("Native on-Fire Codex is not installed by this app. No token or unsafe shell command is requested.",15,0xFF9FC8DD);
        root.addView(feedback);
        root.addView(label("Current practical route: speak/type on Fire, copy and open Codex Cloud; native ARM/Termux app-server is a separate experimental backend.",14,0xFFACC3D3));
        scan();
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
        String s=prompt.getText().toString().trim();
        if(s.length()==0){feedback.setText("Nothing to copy. Enter an intention first.");return;}
        ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(c!=null)c.setPrimaryClip(ClipData.newPlainText("Fire Codex intention",s));
        feedback.setText("Intention copied. You can paste it into Codex on this TV or a paired workstation.");
    }
    private void openCodex(){
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://chatgpt.com/codex"));
        try{startActivity(i);feedback.setText("Opened Codex web. Your prompt remains here; use Copy to transfer it.");}
        catch(ActivityNotFoundException e){feedback.setText("No browser available. Install/open Silk, then use chatgpt.com/codex.");}
    }
}
