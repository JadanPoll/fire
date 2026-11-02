package dev.jadan.fire;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * A capability probe, not a voice assistant. It makes NO assumptions about Alexa
 * Voice Remote microphone access, which is controlled by the Fire TV system.
 * No network calls, RECORD_AUDIO permission, or background microphone usage.
 */
public final class VoiceProbeActivity extends Activity {
    private static final int SPEECH_REQUEST = 101;
    private TextView report;
    private String lastReport = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF102132);
        root.setPadding(dp(28),dp(20),dp(28),dp(20));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        root.addView(text("VOICE PREFLIGHT",26,0xFF64CAFF));
        root.addView(text("A cheap test of what this particular Fire TV actually exposes.",17,0xFFE6F0F6));
        report = text("",16,0xFFE6F0F6);
        root.addView(report);
        Button scan = button(root,"SCAN AUDIO INPUTS AND SPEECH SERVICES",this::scan);
        button(root,"TRY ANDROID SPEECH RECOGNITION",this::trySpeech);
        button(root,"COPY DIAGNOSTIC REPORT",this::copy);
        root.addView(text("Alexa remote voice button is system-owned and cannot be intercepted as an ordinary app key. " +
                "A reported recognition service or microphone does not prove that Alexa remote audio is available.",
                15,0xFFABC4D8));
        scan.requestFocus();
        scan();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }
    private TextView text(String value,int sp,int color) {
        TextView t = new TextView(this);
        t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setPadding(0,dp(9),0,dp(9));
        return t;
    }
    private Button button(LinearLayout root,String label,Runnable fn) {
        Button b = new Button(this);
        b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(64));
        b.setTextColor(0xFFFFFFFF);b.setBackgroundColor(0xFF2D4B65);
        b.setOnFocusChangeListener((v,focused)->b.setBackgroundColor(focused?0xFF176FA3:0xFF2D4B65));
        b.setOnClickListener(v->fn.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin = dp(8);
        root.addView(b,params);
        return b;
    }

    private void show(String details) {
        lastReport=details;
        report.setText(details);
    }

    private void scan() {
        StringBuilder sb = new StringBuilder();
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" / API ")
                .append(Build.VERSION.SDK_INT).append("\n");
        PackageManager pm = getPackageManager();
        sb.append("Android declares microphone feature: ")
                .append(pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)).append("\n");
        boolean recognizer = SpeechRecognizer.isRecognitionAvailable(this);
        sb.append("Android recognition service available: ").append(recognizer).append("\n");

        AudioManager audio = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] inputs = audio==null ? new AudioDeviceInfo[0]
                : audio.getDevices(AudioManager.GET_DEVICES_INPUTS);
        sb.append("Reported audio input devices: ").append(inputs.length).append("\n");
        for (int i=0;i<inputs.length;i++) {
            AudioDeviceInfo device=inputs[i];
            sb.append(" - ID ").append(device.getId())
                    .append(", type ").append(device.getType())
                    .append(", name ").append(device.getProductName()).append("\n");
        }
        if(inputs.length==0) sb.append(" - No Android-recordable input device reported.\n");
        sb.append("\nNext: select TRY to see whether Android can return a transcript.\n");
        sb.append("Nothing is recorded or uploaded by this app.\n");
        show(sb.toString());
    }

    private void trySpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Say a short test sentence");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        try {
            startActivityForResult(intent,SPEECH_REQUEST);
            show(lastReport+"\nSpeech recognition activity requested. Await result or cancel.\n");
        } catch(ActivityNotFoundException e) {
            show(lastReport+"\nRESULT: No speech recognition activity handles this request.\n" +
                    "Try Alexa skill or an external microphone route instead.\n");
        } catch(SecurityException e) {
            show(lastReport+"\nRESULT: Speech recognition blocked by system permission: " +
                    e.getMessage()+"\n");
        } catch(Exception e) {
            show(lastReport+"\nRESULT: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");
        }
    }

    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if(code!=SPEECH_REQUEST)return;
        if(result!=RESULT_OK || data==null) {
            show(lastReport+"\nRESULT: Speech activity canceled or returned no result ("+result+").\n");
            return;
        }
        ArrayList<String> transcripts=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if(transcripts==null || transcripts.isEmpty()) {
            show(lastReport+"\nRESULT: Activity returned but had no transcript.\n");
        } else {
            StringBuilder sb = new StringBuilder(lastReport).append("\nRecognized transcript:\n");
            for (int i=0;i<Math.min(3,transcripts.size());i++) {
                sb.append(" - ").append(transcripts.get(i)).append("\n");
            }
            show(sb.toString());
        }
    }

    private void copy() {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("fire voice preflight",lastReport));
            report.setText(lastReport+"\nCopied diagnostic report to clipboard.\n");
        }
    }
}
