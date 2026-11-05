package dev.jadan.fire;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * User-curated web games; each entry is a normal HTTPS link launched with
 * the EXISTING Fire Browser. No WebView duplicate and no game engine.
 * The share entrypoint only pre-populates a draft: saving requires a click.
 */
public final class GameShelfActivity extends Activity {
    private static final String KEY="pinned_games_v1";
    private static final int MAX=16;
    private final int navy=0xFF163049;
    private SharedPreferences prefs;
    private LinearLayout list;
    private TextView status;
    private EditText urlInput,titleInput;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("fire_console",MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);
        root.setPadding(dp(30),dp(22),dp(30),dp(24));
        root.setBackgroundColor(TvUi.BG);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);setContentView(scroll);

        TextView heading=TvUi.header(this,"PLAYROOM  /  GAME CHANNELS");
        root.addView(heading);
        root.addView(label("Web games, independent native experiments, and a real GBA emulator. Your existing pinned games are unchanged.",16,0xFF58728A));
        LinearLayout probes=TvUi.panel(this);
        probes.addView(TvUi.section(this,"2D  /  MINIGAME PROBES"));
        probes.addView(TvUi.text(this,"Two offline, native, asset-free games for learning what Fire TV rendering and D-pad controls actually do.",15,TvUi.SUB));
        TvUi.action(this,probes,"OPEN 2D MINIGAME LAB",()->startActivity(new Intent(this,MiniGameLabActivity.class)));
        TvUi.addPanel(this,root,probes);
        LinearLayout gba=TvUi.panel(this);
        gba.addView(TvUi.section(this,"GBA  /  REAL CONSOLE GAMES"));
        gba.addView(TvUi.text(this,"Import a user-supplied Game Boy Advance ROM and play it in an existing emulator. No Nintendo software is bundled.",15,TvUi.SUB));
        TvUi.action(this,gba,"OPEN GBA GAME ROOM",()->startActivity(new Intent(this,RetroRoomActivity.class)));
        TvUi.addPanel(this,root,gba);
        status=label("Ready. Select a game or pin a new HTTPS address.",14,0xFF46677E);
        root.addView(status);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list,new LinearLayout.LayoutParams(-1,-2));
        root.addView(label("PIN A GAME",18,navy));
        urlInput=new EditText(this);
        urlInput.setSingleLine(true);urlInput.setTextSize(17);
        urlInput.setHint("https://... game URL");urlInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        urlInput.setTextColor(navy);urlInput.setBackgroundColor(0xFFFFFFFF);
        TvUi.field(this,urlInput);
        root.addView(urlInput,new LinearLayout.LayoutParams(-1,dp(56)));
        titleInput=new EditText(this);
        titleInput.setSingleLine(true);titleInput.setTextSize(16);
        titleInput.setHint("Name (optional, e.g. Subway Surfers)");
        titleInput.setTextColor(navy);titleInput.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams titleLP=new LinearLayout.LayoutParams(-1,dp(50));
        TvUi.field(this,titleInput);
        titleLP.topMargin=dp(6);root.addView(titleInput,titleLP);
        button(root,"PIN THIS GAME",this::pin);
        root.addView(label("Queries and fragments are intentionally not saved, to avoid storing sign-in tokens.",13,0xFF6C8295));
        String shared=getIntent().getStringExtra(Intent.EXTRA_TEXT);
        String name=getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
        if(shared!=null){
            String clean=cleanUrl(shared);
            if(clean!=null){
                urlInput.setText(clean);
                if(name!=null)titleInput.setText(name.length()>60?name.substring(0,60):name);
                status.setText("Page received from Fire Browser. Select PIN to save it.");
            }else status.setText("Shared text was not a plain HTTPS game URL. Nothing was stored.");
        }
        refresh();
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,dp(9),0,dp(7));return t;
    }
    private void button(LinearLayout root,String name,Runnable run){
        TvUi.action(this,root,name,run);
    }

    private static String cleanUrl(String raw){
        return GameUrlPolicy.sanitize(raw);
    }
    private JSONArray entries(){
        try{return new JSONArray(prefs.getString(KEY,"[]"));}
        catch(Exception ex){return new JSONArray();}
    }
    private String suggestedTitle(String url){
        try{
            Uri uri=Uri.parse(url);String path=uri.getLastPathSegment();
            if(path!=null && !path.isEmpty())return path.replace('-', ' ').replace('_',' ');
            return uri.getHost();
        }catch(Exception ex){return "Web game";}
    }
    private void pin(){
        String url=cleanUrl(urlInput.getText().toString());
        if(url==null){
            status.setText("Use a full HTTPS game URL. Nothing saved.");return;
        }
        JSONArray old=entries();
        JSONArray next=new JSONArray();
        String title=titleInput.getText().toString().trim();
        if(title.isEmpty())title=suggestedTitle(url);
        if(title.length()>60)title=title.substring(0,60);
        try{
            next.put(new JSONObject().put("url",url).put("title",title));
            for(int i=0;i<old.length() && next.length()<MAX;i++){
                JSONObject item=old.optJSONObject(i);
                if(item==null || url.equals(item.optString("url")))continue;
                if(cleanUrl(item.optString("url"))==null)continue;
                next.put(item);
            }
        }catch(Exception ex){status.setText("Could not prepare the game shortcut.");return;}
        prefs.edit().putString(KEY,next.toString()).apply();
        urlInput.setText("");titleInput.setText("");
        status.setText("Pinned "+title+". Select its PLAY button to open.");
        refresh();
    }
    private void refresh(){
        list.removeAllViews();
        JSONArray all=entries();
        if(all.length()==0){
            list.addView(label("No pinned games yet. Your browser's game remains accessible through Browser → Recent.",16,0xFF638097));
            return;
        }
        for(int i=0;i<all.length();i++){
            JSONObject entry=all.optJSONObject(i);if(entry==null)continue;
            final String url=cleanUrl(entry.optString("url"));
            if(url==null)continue;
            String name=entry.optString("title",suggestedTitle(url));
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(4),dp(10),dp(4));
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(0xFFFFFFFF);bg.setCornerRadius(dp(12));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(80));
            lp.bottomMargin=dp(9);list.addView(row,lp);
            TextView title=label(name,20,navy);title.setSingleLine(true);
            row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            Button play=TvUi.button(this,"PLAY",()->ConsoleLaunch.openBrowser(this,url,status));
            row.addView(play);
            Button remove=TvUi.button(this,"UNPIN",()->new AlertDialog.Builder(this)
                .setTitle("Unpin "+name+"?")
                .setMessage("Remove only this launcher shortcut; the website and browser data remain untouched.")
                .setPositiveButton("UNPIN",(d,w)->remove(url))
                .setNegativeButton("Cancel",null).show());
            row.addView(remove);
        }
    }
    private void remove(String url){
        JSONArray old=entries(),next=new JSONArray();
        for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);
            if(item!=null&&!url.equals(item.optString("url")))next.put(item);
        }
        prefs.edit().putString(KEY,next.toString()).apply();
        status.setText("Shortcut unpinned. Game website and history retained.");
        refresh();
    }
}
