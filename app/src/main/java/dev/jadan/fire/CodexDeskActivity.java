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
import org.json.JSONObject;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private EditText executorUrl, pairingToken;
    private TextView remoteStatus, remoteOutput;
    private final ExecutorService remoteWorker = Executors.newSingleThreadExecutor();
    private final Handler remoteClock = new Handler(Looper.getMainLooper());
    private boolean remoteBusy;
    private String jobId;
    private final Runnable remoteTick = () -> {
        if (jobId != null && !remoteBusy) remoteAction("poll");
    };
    private static final String CODEX_URL = "https://chatgpt.com/codex";
    private static final String CHAT_URL = "https://chatgpt.com/";
    private static final String SILK_PACKAGE = "com.amazon.cloud9";
    private LinearLayout pairingPanel;
    private Button connectButton;
    private android.content.SharedPreferences computePrefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        computePrefs=getSharedPreferences("fire_compute_v1",MODE_PRIVATE);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ScrollView scroller=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        TvUi.page(this,root);
        scroller.addView(root);
        setContentView(scroller);

        root.addView(TvUi.header(this,"COMPUTE  /  OFFSHORE WORK"));
        root.addView(TvUi.text(this,
            "One intention. Run in a paired Codex workspace, or open Codex Cloud without pairing.",
            16,TvUi.SUB));

        prompt=new EditText(this);
        prompt.setSingleLine(false);
        prompt.setMinLines(3);
        prompt.setMaxLines(6);
        prompt.setHint("What should Compute build or investigate?");
        prompt.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_ACTION_DONE);
        TvUi.field(this,prompt);
        prompt.setShowSoftInputOnFocus(false);
        root.addView(prompt,new LinearLayout.LayoutParams(-1,-2));
        speechStatus=label("Your draft stays here when you switch apps.",14,TvUi.SUB);
        root.addView(speechStatus);
        prompt.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence t,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence t,int start,int before,int count){
                lastInput=t.toString();
                speechStatus.setText("Draft: "+lastInput.length()+" characters. Nothing sent automatically.");
            }
            @Override public void afterTextChanged(Editable t){}
        });
        TvUi.action(this,root,"TYPE / DICTATE",()->{
            // Unlike the initial terminal regression, an explicit keyboard
            // request must first allow input to be shown for this field.
            prompt.setShowSoftInputOnFocus(true);
            prompt.requestFocus();
            InputMethodManager manager=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            prompt.post(()->{
                if(manager!=null){
                    manager.restartInput(prompt);
                    manager.showSoftInput(prompt,0);
                }
            });
            speechStatus.setText("Fire keyboard requested. Dictation depends on the system IME.");
        });

        LinearLayout actions=TvUi.actionRow(this,root);
        TvUi.actionInRow(this,actions,"RUN ON PAIRED HOST",()->remoteAction("submit"));
        TvUi.actionInRow(this,actions,"OPEN CODEX CLOUD",this::openCodexCloud);

        remoteStatus=label("No remote job active. Connect a trusted host or use Codex Cloud.",16,TvUi.SUB);
        remoteStatus.setTextIsSelectable(true);
        root.addView(remoteStatus);
        remoteOutput=label("",15,TvUi.SUB);
        remoteOutput.setTextIsSelectable(true);
        root.addView(remoteOutput);

        LinearLayout jobActions=TvUi.actionRow(this,root);
        TvUi.actionInRow(this,jobActions,"REFRESH / RECOVER",()->remoteAction(jobId==null?"recover":"poll"));
        TvUi.actionInRow(this,jobActions,"STOP JOB",()->remoteAction("cancel"));
        TvUi.action(this,root,"COPY RESULT",this::copyResult);

        connectButton=TvUi.action(this,root,"CONNECT / CHANGE COMPUTE HOST",this::togglePairing);
        buildExecutorUi(root);
        pairingPanel.setVisibility(View.GONE);

        TvUi.action(this,root,"ADVANCED / DIAGNOSTICS",this::advanced);
        feedback=label("A host requires a private HTTPS endpoint and pairing token. Neither is sent to Codex Cloud.",
            14,TvUi.SUB);
        root.addView(feedback);

        if(b!=null){
            prompt.setText(b.getString("fire_draft",""));
            jobId=b.getString("compute_job");
        } else {
            prompt.setText(computePrefs.getString("draft",""));
            jobId=computePrefs.getString("job_id",null);
        }
        executorUrl.setText(computePrefs.getString("host_origin",""));
        // The pairing credential remains session-only: this lab APK's
        // signing key is public and must not persist a bearer token.
        if(jobId!=null)remoteStatus.setText("Previous job "+jobId+" · Connect host to refresh, or use Recover.");
        connectButton.requestFocus();
    }

    private void buildExecutorUi(LinearLayout root){
        pairingPanel=TvUi.panel(this);
        pairingPanel.addView(TvUi.section(this,"TRUSTED REMOTE WORKSPACE"));
        pairingPanel.addView(TvUi.text(this,
            "The host runs a separately authenticated Codex CLI behind your private HTTPS proxy. "+
            "The address is remembered; bearer credentials stay in memory only.",
            14,TvUi.SUB));
        executorUrl=new EditText(this);
        executorUrl.setSingleLine(true);
        executorUrl.setHint("https://your-private-executor.example");
        executorUrl.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        TvUi.field(this,executorUrl);
        pairingPanel.addView(executorUrl,new LinearLayout.LayoutParams(-1,-2));
        pairingToken=new EditText(this);
        pairingToken.setSingleLine(true);
        pairingToken.setHint("Executor pairing token, not OpenAI login");
        pairingToken.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TvUi.field(this,pairingToken);
        pairingPanel.addView(pairingToken,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout pairActions=TvUi.actionRow(this,pairingPanel);
        TvUi.actionInRow(this,pairActions,"TEST CONNECTION",()->remoteAction("probe"));
        TvUi.actionInRow(this,pairActions,"PASTE TOKEN",this::pasteToken);
        TvUi.action(this,pairingPanel,"FORGET PAIRING",this::forgetPairing);
        root.addView(pairingPanel);
    }

    private void togglePairing(){
        boolean show=pairingPanel.getVisibility()!=View.VISIBLE;
        pairingPanel.setVisibility(show?View.VISIBLE:View.GONE);
        connectButton.setText(show?"HIDE CONNECTION SETTINGS":"CONNECT / CHANGE COMPUTE HOST");
        if(show)executorUrl.requestFocus();
        else connectButton.requestFocus();
    }
    private void pasteToken(){
        ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cb==null||!cb.hasPrimaryClip()){
            remoteStatus.setText("No clipboard text available. Type the pairing token instead.");return;
        }
        ClipData clip=cb.getPrimaryClip();
        CharSequence item=clip!=null&&clip.getItemCount()>0?
            clip.getItemAt(0).coerceToText(this):null;
        String secret=item==null?"":item.toString().trim();
        try{
            CodexExecutorClient.requireToken(secret);
            pairingToken.setText(secret);
            remoteStatus.setText("Token entered privately. Select TEST CONNECTION to verify.");
        }catch(IllegalArgumentException error){
            remoteStatus.setText("Clipboard is not a valid pairing token. Nothing changed.");
        }
    }
    private void forgetPairing(){
        pairingToken.setText("");
        executorUrl.setText("");
        jobId=null;
        computePrefs.edit().remove("host_origin").remove("job_id").apply();
        remoteClock.removeCallbacks(remoteTick);
        remoteStatus.setText("Pairing cleared on this device. No remote job was cancelled.");
    }
    private void openCodexCloud(){
        if(prompt.getText().length()>0 && !copyInternal()){
            feedback.setText("Clipboard unavailable. Opening Codex without copying your draft.");
        }
        launchSite(CODEX_URL,"silk",false);
    }
    private void copyResult(){
        String output=remoteOutput.getText().toString();
        if(output.isEmpty()){
            remoteStatus.setText("No result yet. Select REFRESH / RECOVER to request the host status.");
            return;
        }
        ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cb==null){remoteStatus.setText("Clipboard unavailable.");return;}
        cb.setPrimaryClip(ClipData.newPlainText("Compute result",output));
        remoteStatus.setText("Result copied to clipboard. Original remains visible.");
    }
    private void advanced(){
        String[] options={"OPEN FIRE BROWSER","CHOOSE BROWSER","CHATGPT HOME / SILK",
            "SILK ADDRESS BAR","COPY DRAFT","LOCAL TERMINAL","VOICE TEST",
            "DEVICE HARDWARE PRECHECK","BROWSER ROUTING DIAGNOSTICS","CLEAR SAVED DRAFT"};
        new AlertDialog.Builder(this).setTitle("COMPUTE  /  ADVANCED")
            .setItems(options,(dialog,which)->{
                switch(which){
                    case 0:openFireBrowser();break;
                    case 1:chooseBrowser();break;
                    case 2:launchSite(CHAT_URL,"silk",false);break;
                    case 3:launchSilkHome();break;
                    case 4:copy();break;
                    case 5:startActivity(new Intent(this,TerminalActivity.class));break;
                    case 6:startActivity(new Intent(this,VoiceProbeActivity.class));break;
                    case 7:
                        scan();
                        new AlertDialog.Builder(this).setTitle("LOCAL CAPABILITIES")
                            .setMessage(details.getText()).setPositiveButton("CLOSE",null).show();
                        break;
                    case 8:browserDiagnostics();break;
                    case 9:
                        prompt.setText("");
                        computePrefs.edit().remove("draft").apply();
                        feedback.setText("Local draft cleared. Remote jobs unaffected.");
                        break;
                }
            }).setNegativeButton("CLOSE",null).show();
    }
    private void remoteAction(String action){
        if(remoteBusy || remoteStatus==null)return;
        if(("submit".equals(action)||"probe".equals(action)||"recover".equals(action)
                ||"poll".equals(action)||"cancel".equals(action)) &&
           (executorUrl.getText().toString().trim().isEmpty() ||
            pairingToken.getText().toString().trim().isEmpty())){
            remoteStatus.setText("Host not paired. Select CONNECT / CHANGE COMPUTE HOST. "+
                "OPEN CODEX CLOUD remains available.");
            if(pairingPanel.getVisibility()!=View.VISIBLE)togglePairing();
            return;
        }
        if(jobId==null && ("poll".equals(action)||"cancel".equals(action))){
            remoteStatus.setText("No executor job yet. Submit a prompt first.");return;
        }
        final String origin=executorUrl.getText().toString().trim();
        final String secret=pairingToken.getText().toString();
        final String draft=prompt.getText().toString();
        final String currentId=jobId;
        remoteClock.removeCallbacks(remoteTick);
        remoteBusy=true;
        remoteStatus.setText("Compute host: "+action+" requested…");
        remoteWorker.execute(()->{
            JSONObject reply=null; Exception failure=null;
            try{
                if("probe".equals(action))
                    reply=CodexExecutorClient.probe(origin,secret);
                else if("submit".equals(action))
                    reply=CodexExecutorClient.submit(origin,secret,draft);
                else if("recover".equals(action))
                    reply=CodexExecutorClient.recover(origin,secret);
                else if("cancel".equals(action))
                    reply=CodexExecutorClient.cancel(origin,secret,currentId);
                else
                    reply=CodexExecutorClient.poll(origin,secret,currentId);
            }catch(Exception ex){failure=ex;}
            final JSONObject result=reply;
            final Exception error=failure;
            runOnUiThread(()->{
                remoteBusy=false;
                if(isFinishing()||isDestroyed())return;
                if(error!=null){
                    remoteStatus.setText("Host "+action+" outcome unknown: "+error.getMessage()+
                        ". Use REFRESH / RECOVER before retrying a submission. Draft retained.");
                    if(jobId!=null && pairingToken!=null && pairingToken.length()>0)scheduleRemotePoll();
                    return;
                }
                if("probe".equals(action)){
                    remoteStatus.setText("Host connected · Protocol "+result.optInt("protocol",-1)+
                        " · "+result.optString("executor","unknown"));
                    rememberHost();
                    return;
                }
                if("submit".equals(action)||"recover".equals(action)){
                    jobId=result.optString("id",null);
                    rememberHost();
                    computePrefs.edit().putString("job_id",jobId).apply();
                }
                String state=result.optString("state","unknown");
                remoteStatus.setText("Executor "+state+" · Job "+result.optString("id","?")+
                    " · "+result.optString("message",""));
                String text=result.optString("output","");
                if(!text.isEmpty())remoteOutput.setText(text);
                if("running".equals(state)||"queued".equals(state))scheduleRemotePoll();
            });
        });
    }
    private void rememberHost(){
        try{
            String origin=CodexExecutorClient.serverOrigin(executorUrl.getText().toString());
            computePrefs.edit().putString("host_origin",origin).apply();
        }catch(Exception ignored){}
    }
    private void scheduleRemotePoll(){
        remoteClock.removeCallbacks(remoteTick);
        if(jobId!=null&&!isFinishing()&&pairingToken!=null&&
            pairingToken.length()>0)remoteClock.postDelayed(remoteTick,3500);
    }
    @Override protected void onPause(){
        remoteClock.removeCallbacks(remoteTick);
        if(computePrefs!=null && prompt!=null)
            computePrefs.edit().putString("draft",prompt.getText().toString()).apply();
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        if(jobId!=null)scheduleRemotePoll();
    }
    @Override protected void onDestroy(){
        remoteClock.removeCallbacks(remoteTick);
        remoteWorker.shutdownNow();
        super.onDestroy();
    }

    private TextView label(String t,int sp,int color){
        return TvUi.text(this,t,sp,sp>=17?TvUi.INK:TvUi.SUB);
    }
    private void button(LinearLayout root,String title,Runnable action){
        TvUi.action(this,root,title,action);
    }
    private void scan(){
        if(details==null)details=label("",15,TvUi.SUB);
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
        out.putString("compute_job",jobId);
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
        String draft=prompt.getText().toString();
        Intent installed=getPackageManager().getLaunchIntentForPackage("dev.jadan.fire.browser");
        if(installed!=null){
            installed.putExtra("draft",draft);
            startActivity(installed);
            return;
        }
        // The lab remains available until the standalone APK has been installed.
        Intent fallback=new Intent(this,FireBrowserActivity.class);
        fallback.putExtra("draft",draft);
        startActivity(fallback);
        feedback.setText("Standalone Fire Browser not yet installed. Open APP CATALOG to install it; this is the older built-in lab.");
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
