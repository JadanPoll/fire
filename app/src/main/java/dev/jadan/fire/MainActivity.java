package dev.jadan.fire;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Tools, not a second home screen.
 *
 * The historic Dev Bridge updater duplicated CatalogActivity's already
 * verified SHA-256/installer/retry machinery; its global-ish key log
 * duplicated InputLabActivity's much stronger input diagnostics.
 * Both duplicated implementations were deleted, not just hidden.
 *
 * Existing tools remain available as explicit, independently navigable
 * activities. Android Back always returns to Console.
 */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        TvUi.page(this,root);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(TvUi.header(this,"TOOLS  /  MAINTENANCE"));
        root.addView(TvUi.text(this,
            "The main Console stays small. Installations, local diagnostics, and early experiments live here.",
            16,TvUi.SUB));
        root.addView(TvUi.text(this,
            "Device: "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+
                " · API "+Build.VERSION.SDK_INT+" · Console "+BuildConfig.VERSION_NAME,
            14,TvUi.SUB));

        TvUi.action(this,root,"INSTALL / UPDATE FIRE APPS",
            ()->startActivity(new Intent(this,CatalogActivity.class))).requestFocus();
        TvUi.action(this,root,"OTHER TOOLS  /  DIAGNOSTICS",this::advanced);
        root.addView(TvUi.text(this,
            "Use updates when needed. The terminal, input labs and repair tools remain available under Other Tools.",
            14,TvUi.SUB));
    }

    private void advanced(){
        final String[] entries={
            "LOCAL TERMINAL",
            "VOICE & KEYBOARD RECOVERY",
            "INPUT LAB  /  KEY EVENTS & POINTER",
            "OLD WEBVIEW BROWSER LAB",
            "COMPUTE  /  HOST & BROWSER DIAGNOSTICS",
            "INSTALLED TV APPLICATIONS"
        };
        new AlertDialog.Builder(this)
            .setTitle("ADVANCED  /  DEVELOPMENT LABS")
            .setItems(entries,(dialog,choice)->{
                Class<?> target;
                switch(choice){
                    case 0: target=TerminalActivity.class;break;
                    case 1: target=VoiceProbeActivity.class;break;
                    case 2: target=InputLabActivity.class;break;
                    case 3: target=FireBrowserActivity.class;break;
                    case 4: target=CodexDeskActivity.class;break;
                    default: target=AppLibraryActivity.class;
                }
                startActivity(new Intent(this,target));
            })
            .setNegativeButton("BACK",null)
            .show();
    }
}
