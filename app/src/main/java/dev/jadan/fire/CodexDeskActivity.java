package dev.jadan.fire;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
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
    private TextView feedback, speechStatus;
    private String lastInput="";
    private EditText executorUrl, pairingToken;
    private TextView remoteStatus, remoteOutput;
    private final ExecutorService remoteWorker = Executors.newSingleThreadExecutor();
    private final Handler remoteClock = new Handler(Looper.getMainLooper());
    private boolean remoteBusy;
    private boolean inboxBusy;
    private String inboxSender, inboxRequestId, inboxResultUrl;
    private TextView inboxStatus, inboxResult;
    private Button inboxRefreshButton, inboxGitHubButton;
    private String jobId;
    private final Runnable remoteTick = () -> {
        if (jobId != null && !remoteBusy) remoteAction("poll");
    };
    private static final String CODEX_URL = "https://chatgpt.com/codex";
    private static final String SILK_PACKAGE = "com.amazon.cloud9";
    // Process-local only: return from Browser/Playroom without retyping the
    // trusted host's token. Never write it to SharedPreferences or Bundle.
    // Process death clears this session; FORGET PAIRING clears it earlier.
    private static final class LivePairing {
        private static String token="";
    }
    private LinearLayout pairingPanel;
    private LinearLayout jobPanel;
    private Button jobDetailsButton;
    private Button firstJobAction;
    private Button moreButton;
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

        root.addView(TvUi.header(this,"COMPUTE"));
        root.addView(TvUi.text(this,
            "Describe what you want done. Send it to your agents, or work directly in Codex.",
            16,TvUi.SUB));

        prompt=new EditText(this);
        prompt.setSingleLine(false);
        prompt.setMinLines(3);
        prompt.setMaxLines(6);
        prompt.setHint("What should we work on?");
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

        root.addView(TvUi.text(this,
            "Agent tasks are public messages. Do not send secrets. A task is not executed until an agent with its own permissions accepts it.",
            13,TvUi.SUB));
        LinearLayout actions=TvUi.actionRow(this,root);
        Button sendButton=TvUi.actionInRow(this,actions,"SEND TO AGENTS",this::sendInboxTask);
        Button cloudButton=TvUi.actionInRow(this,actions,"OPEN CODEX",this::openCodexCloud);
        inboxStatus=label("No agent request submitted. Nothing runs automatically.",16,TvUi.SUB);
        inboxStatus.setTextIsSelectable(true);
        root.addView(inboxStatus);
        inboxResult=label("",16,TvUi.SUB);
        inboxResult.setTextIsSelectable(true);
        root.addView(inboxResult);
        inboxRefreshButton=TvUi.action(this,root,"CHECK AGENT RESPONSE",this::checkInboxTask);
        inboxGitHubButton=TvUi.action(this,root,"VIEW RESULT ON GITHUB",this::openInboxGitHub);
        inboxRefreshButton.setVisibility(View.GONE);
        inboxGitHubButton.setVisibility(View.GONE);

        remoteStatus=label("No paired Codex job active.",16,TvUi.SUB);
        remoteStatus.setTextIsSelectable(true);
        remoteOutput=label("",15,TvUi.SUB);
        remoteOutput.setTextIsSelectable(true);
        // Paired-host diagnostics are important but not every-visit navigation.
        // Keep the existing executor's status/results in the job details panel.

        jobDetailsButton=TvUi.action(this,root,"CURRENT JOB  /  DETAILS",this::showJobDetails);
        jobPanel=TvUi.panel(this);
        jobPanel.addView(TvUi.section(this,"TRUSTED CODEX JOB"));
        jobPanel.addView(remoteStatus);
        jobPanel.addView(remoteOutput);
        LinearLayout jobActions=TvUi.actionRow(this,jobPanel);
        firstJobAction=TvUi.actionInRow(this,jobActions,"REFRESH / RECOVER",
            ()->remoteAction(jobId==null?"recover":"poll"));
        TvUi.actionInRow(this,jobActions,"STOP JOB",()->remoteAction("cancel"));
        TvUi.action(this,jobPanel,"COPY RESULT",this::copyResult);
        root.addView(jobPanel);
        jobPanel.setVisibility(View.GONE);

        moreButton=TvUi.action(this,root,"MORE  /  COMPUTE OPTIONS",this::more);
        buildExecutorUi(root);
        pairingPanel.setVisibility(View.GONE);
        feedback=label("",14,TvUi.SUB);
        root.addView(feedback);

        if(b!=null){
            prompt.setText(b.getString("fire_draft",""));
            jobId=b.getString("compute_job");
        } else {
            prompt.setText(computePrefs.getString("draft",""));
            jobId=computePrefs.getString("job_id",null);
        }
        inboxSender=ComputeInboxClient.senderId(computePrefs.getString("inbox_sender",""));
        computePrefs.edit().putString("inbox_sender",inboxSender).apply();
        inboxRequestId=computePrefs.getString("inbox_request_id",null);
        if(inboxRequestId!=null) {
            inboxStatus.setText("Last public task "+inboxRequestId+" · Check response to see agent progress.");
            inboxRefreshButton.setVisibility(View.VISIBLE);
        }
        executorUrl.setText(computePrefs.getString("host_origin",""));
        pairingToken.setText(LivePairing.token);
        // The pairing credential remains process-memory only: the lab APK
        // signing key is public and must not persist a bearer token.
        if(jobId!=null)remoteStatus.setText("Previous job "+jobId+
            " · "+(pairingToken.length()>0?"Select REFRESH / RECOVER.":"Connect host to refresh."));
        else if(pairingToken.length()>0)remoteStatus.setText(
            "Previous pairing restored in this running Console process.");
        jobDetailsButton.setVisibility(jobId==null?View.GONE:View.VISIBLE);
        sendButton.requestFocus();
        // A returning user can check the last request without reopening setup.
        // Keep the everyday action spatially stable across fresh and returning sessions.
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
        if(show)executorUrl.requestFocus();
    }
    private void showJobDetails(){
        jobPanel.setVisibility(View.VISIBLE);
        firstJobAction.requestFocus();
    }

    private void more(){
        String[] choices={"CHECK GITHUB PROJECT WORK","OPEN LIVE COMPUTE BOARD",
            "RUN TRUSTED CODEX HOST","DEVICES & CAPABILITIES",
            "CONNECT / CHANGE HOST","JOB CONTROLS & RECOVERY","ADVANCED DIAGNOSTICS"};
        new AlertDialog.Builder(this).setTitle("COMPUTE  /  MORE")
            .setItems(choices,(dialog,index)->{
                switch(index){
                    case 0:openComputeBoard();break;
                    case 1:openComputeBoard();break;
                    case 2:remoteAction("submit");break;
                    case 3:startActivity(new Intent(this,ComputeDevicesActivity.class));break;
                    case 4:togglePairing();break;
                    case 5:showJobDetails();break;
                    default:advanced();break;
                }
            }).setNegativeButton("BACK",null).show();
    }

    private void openComputeBoard() {
        launchSite("https://fire-inbox.vercel.app/compute.html");
    }

    private void sendInboxTask() {
        if(inboxBusy)return;
        final String goal=prompt.getText().toString().trim();
        final JSONObject task;
        try {
            task=ComputeInboxClient.makeRequest(inboxSender,
                ComputeInboxClient.DEFAULT_NAME,goal,null);
        }catch(Exception e) {
            inboxStatus.setText("Describe a non-sensitive task first. "+e.getMessage());
            return;
        }
        inboxBusy=true;
        final String id=task.optString("id");
        // Persist before network I/O: if the write succeeds but the reply is
        // lost, the human can reconcile by ID rather than submitting twice.
        inboxRequestId=id;
        computePrefs.edit().putString("inbox_request_id",id)
            .putString("draft",goal).apply();
        inboxStatus.setText("Saving public request "+id+"…");
        inboxRefreshButton.setVisibility(View.VISIBLE);
        inboxGitHubButton.setVisibility(View.GONE);
        inboxResult.setText("");
        remoteWorker.execute(()->{
            JSONObject receipt=null;
            Exception failure=null;
            try{receipt=ComputeInboxClient.submit(task);}
            catch(Exception e){failure=e;}
            final JSONObject saved=receipt;
            final Exception error=failure;
            runOnUiThread(()->{
                inboxBusy=false;
                if(isFinishing()||isDestroyed())return;
                if(error!=null){
                    inboxStatus.setText("Delivery uncertain for "+id+
                        ". Draft kept. CHECK AGENT RESPONSE reconciles before any retry. "+
                        error.getClass().getSimpleName()+": "+error.getMessage());
                    return;
                }
                inboxStatus.setText("Saved request "+saved.optString("id")+
                    ". Awaiting a connected agent; no code executed by sending.");
                if(goal.equals(prompt.getText().toString().trim()))prompt.setText("");
                checkInboxTask();
            });
        });
    }

    private void checkInboxTask() {
        if(inboxBusy || inboxRequestId==null)return;
        inboxBusy=true;
        final String current=inboxRequestId;
        inboxStatus.setText("Checking durable Inbox history for "+current+"…");
        remoteWorker.execute(()->{
            JSONObject history=null;
            Exception failure=null;
            try{history=ComputeInboxClient.fetchRecent();}
            catch(Exception e){failure=e;}
            final JSONObject response=history;
            final Exception error=failure;
            runOnUiThread(()->{
                inboxBusy=false;
                if(isFinishing()||isDestroyed()||!current.equals(inboxRequestId))return;
                if(error!=null){
                    inboxStatus.setText("Could not fetch agent responses: "+
                        error.getClass().getSimpleName()+". Request ID "+current+" retained.");
                    return;
                }
                JSONObject reply=ComputeInboxClient.latestReply(response,inboxSender,current);
                if(reply!=null) {
                    String kind=reply.optString("kind","message");
                    inboxStatus.setText("Agent "+kind+" received · Request "+current);
                    inboxResult.setText(reply.optString("text",""));
                    org.json.JSONArray refs=reply.optJSONArray("refs");
                    inboxResultUrl=null;
                    if(refs!=null)for(int i=0;i<refs.length();i++){
                        String ref=refs.optString(i);
                        if(ref.startsWith("https://github.com/")&&ref.length()<180){
                            inboxResultUrl=ref;break;
                        }
                    }
                    inboxGitHubButton.setVisibility(inboxResultUrl==null?View.GONE:View.VISIBLE);
                } else {
                    boolean saved=ComputeInboxClient.savedRequest(response,inboxSender,current);
                    inboxStatus.setText(saved
                        ? "Task saved. Awaiting a connected agent. Select CHECK AGENT RESPONSE later."
                        : "Request not in the latest Inbox page. Delivery still unverified; no automatic retry.");
                }
            });
        });
    }

    private void openInboxGitHub() {
        if(inboxResultUrl!=null)ConsoleLaunch.openBrowser(this,inboxResultUrl,inboxStatus);
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
        LivePairing.token="";
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
        launchSite(CODEX_URL);
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
        String[] options={"OPEN FIRE BROWSER","COPY DRAFT","LOCAL TERMINAL",
            "VOICE & KEYBOARD TEST","CLEAR SAVED DRAFT"};
        new AlertDialog.Builder(this).setTitle("COMPUTE  /  ADVANCED")
            .setItems(options,(dialog,which)->{
                switch(which){
                    case 0:openFireBrowser();break;
                    case 1:copy();break;
                    case 2:startActivity(new Intent(this,TerminalActivity.class));break;
                    case 3:startActivity(new Intent(this,VoiceProbeActivity.class));break;
                    case 4:
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
            remoteStatus.setText("No paired host. Select MORE / DEVICES & SETTINGS to connect. "+
                "OPEN CODEX remains available.");
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
        if("submit".equals(action)||"probe".equals(action))
            showJobDetails();  // never hide the actual outcome after the user acts
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
                    LivePairing.token=pairingToken.getText().toString();
                    return;
                }
                if("submit".equals(action)||"recover".equals(action)){
                    jobId=result.optString("id",null);
                    rememberHost();
                    LivePairing.token=pairingToken.getText().toString();
                    computePrefs.edit().putString("job_id",jobId).apply();
                    if(jobDetailsButton!=null)jobDetailsButton.setVisibility(View.VISIBLE);
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
    @Override public void onBackPressed() {
        // A hidden settings panel should not trap the user or force an
        // unexpected return all the way to the Console.
        if (pairingPanel!=null && pairingPanel.getVisibility()==View.VISIBLE) {
            pairingPanel.setVisibility(View.GONE);
            moreButton.requestFocus();
            return;
        }
        if (jobPanel!=null && jobPanel.getVisibility()==View.VISIBLE) {
            jobPanel.setVisibility(View.GONE);
            moreButton.requestFocus();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onPause(){
        remoteClock.removeCallbacks(remoteTick);
        if(computePrefs!=null && prompt!=null)
            computePrefs.edit().putString("draft",prompt.getText().toString()).apply();
        if(pairingToken!=null)LivePairing.token=pairingToken.getText().toString();
        if(prompt!=null)prompt.setShowSoftInputOnFocus(false);
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

    private void launchSite(String url) {
        try {
            Intent i=webIntent(url);
            i.setPackage(SILK_PACKAGE);
            if(i.resolveActivity(getPackageManager())==null)
                i=Intent.createChooser(webIntent(url),"Choose installed browser");
            startActivity(i);
            feedback.setText("Codex opened in browser. Your original draft remains in Compute.");
        } catch(ActivityNotFoundException error) {
            feedback.setText("No browser available. Your draft is retained.");
        } catch(RuntimeException error) {
            feedback.setText("Browser handoff failed: "+error.getClass().getSimpleName()+
                ". Your draft is retained.");
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

    private boolean copyInternal(){
        String s=prompt.getText().toString().trim();
        if(s.length()==0) return false;
        ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(c==null)return false;
        c.setPrimaryClip(ClipData.newPlainText("Fire Codex intention",s));
        return true;
    }
}
