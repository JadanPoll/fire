package dev.jadan.fire;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * Playroom is a game shelf, not the inventory of every engineering feature.
 *
 * Focused first screen: play a game immediately, return to a pinned favorite,
 * or intentionally open a collection/research area. No vertical navigation
 * of research modules precedes the games. Existing game data is untouched.
 */
public final class PlayroomHubActivity extends Activity {
    private static final int BG=0xFFEDF5FA;
    private static final int INK=0xFF0F2441;
    private final ArrayList<Destination> destinations = new ArrayList<>();
    private String pinnedSnapshot="[]";
    private TextView footer;

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }

    private static final class Destination {
        final String title, hint;
        final int icon, color;
        final Runnable open;
        Destination(String title, String hint, int icon, int color, Runnable open) {
            this.title=title; this.hint=hint; this.icon=icon; this.color=color; this.open=open;
        }
    }

    private GradientDrawable background(int border, int weight) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(0xFFFFFFFF);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(weight),border);
        return d;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        buildDestinations();

        // Match the Console's spatial grid and D-pad behavior. Everything
        // needed for normal play is visible without scrolling.
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(25),dp(17),dp(25),dp(16));
        setContentView(root);

        TextView heading=new TextView(this);
        heading.setText("PLAYROOM");
        heading.setTextSize(29);
        heading.setTypeface(null,1);
        heading.setTextColor(INK);
        root.addView(heading,new LinearLayout.LayoutParams(-1,dp(48)));

        TextView hint=new TextView(this);
        hint.setText("Choose a game. OK plays it; Back returns to the Console.");
        hint.setTextSize(16);
        hint.setTextColor(0xFF55748B);
        hint.setSingleLine(true);
        root.addView(hint,new LinearLayout.LayoutParams(-1,dp(32)));

        int columns=destinations.size()>4 ? 3 : 2;
        int rows=(destinations.size()+columns-1)/columns;
        View first=null;
        for (int r=0;r<rows;r++) {
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);
            rp.bottomMargin=dp(12);
            root.addView(row,rp);
            for (int c=0;c<columns;c++) {
                int index=r*columns+c;
                if (index>=destinations.size()) {
                    row.addView(new View(this),new LinearLayout.LayoutParams(0,-1,1));
                    continue;
                }
                FrameLayout tile=tile(destinations.get(index));
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1);
                if(c>0)tp.leftMargin=dp(12);
                row.addView(tile,tp);
                if(first==null)first=tile;
            }
        }
        footer=new TextView(this);
        footer.setText("More experiments are in EXPLORE. They are not required to play.");
        footer.setTextColor(0xFF647F92);
        footer.setTextSize(14);
        footer.setSingleLine(true);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(28)));
        if(first!=null)first.requestFocus();
    }

    private void buildDestinations() {
        destinations.add(new Destination("SNAKE","Play now",8,0xFF39A984,
            () -> startActivity(new Intent(this,SnakeActivity.class))));
        destinations.add(new Destination("GAME BOY ADVANCE","Your games",2,0xFFDE9C36,
            () -> startActivity(new Intent(this,RetroRoomActivity.class))));

        SharedPreferences prefs=getSharedPreferences("fire_console",MODE_PRIVATE);
        pinnedSnapshot=prefs.getString("pinned_games_v1","[]");
        try {
            JSONArray games=new JSONArray(pinnedSnapshot);
            int shown=0;
            for (int i=0;i<games.length() && shown<2;i++) {
                JSONObject entry=games.optJSONObject(i);
                if(entry==null)continue;
                String url=GameUrlPolicy.sanitize(entry.optString("url"));
                if(url==null)continue;
                String name=entry.optString("title","Web game");
                if(name.trim().isEmpty())name="Web game";
                final String title=name;
                destinations.add(new Destination(title,"Pinned web game",1,0xFF4B9DC9,
                    () -> ConsoleLaunch.openBrowser(this,url,footer)));
                shown++;
            }
        } catch (Exception ignored) {
            // Corrupt shortcuts do not prevent launching native games.
        }

        destinations.add(new Destination("MY WEB GAMES","Play or pin games",1,0xFF4488C5,
            () -> startActivity(new Intent(this,GameShelfActivity.class))));
        destinations.add(new Destination("EXPLORE","Experiments & research",7,0xFF8175B5,
            this::explore));
    }

    @Override protected void onResume() {
        super.onResume();
        String current=getSharedPreferences("fire_console",MODE_PRIVATE)
            .getString("pinned_games_v1","[]");
        if (pinnedSnapshot!=null && !pinnedSnapshot.equals(current))
            recreate();  // Pinning changed. Rebuild only then; preserve ordinary game-return focus.
    }

    private FrameLayout tile(Destination item) {
        FrameLayout card=new FrameLayout(this);
        card.setBackground(background(0xFFD5E4EE,1));
        card.setFocusable(true);
        card.setClickable(true);
        card.setElevation(dp(2));
        LinearLayout contents=new LinearLayout(this);
        contents.setOrientation(LinearLayout.VERTICAL);
        contents.setGravity(Gravity.CENTER);
        contents.setPadding(dp(10),dp(8),dp(10),dp(8));
        card.addView(contents,new FrameLayout.LayoutParams(-1,-1));

        ConsoleActivity.ChannelPicture icon=
            new ConsoleActivity.ChannelPicture(this,item.icon,item.color);
        contents.addView(icon,new LinearLayout.LayoutParams(dp(76),0,1));

        TextView title=new TextView(this);
        title.setText(item.title);
        title.setSingleLine(true);
        title.setTextSize(21);
        title.setTextColor(INK);
        title.setTypeface(null,1);
        title.setGravity(Gravity.CENTER);
        contents.addView(title,new LinearLayout.LayoutParams(-1,dp(33)));

        TextView subtitle=new TextView(this);
        subtitle.setText(item.hint);
        subtitle.setSingleLine(true);
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF647E91);
        subtitle.setGravity(Gravity.CENTER);
        contents.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(28)));

        card.setContentDescription(item.title+". "+item.hint+". Press OK.");
        card.setOnFocusChangeListener((v,focused)->{
            card.setBackground(background(focused?item.color:0xFFD5E4EE,focused?3:1));
            card.setElevation(dp(focused?9:2));
            card.setScaleX(focused?1.018f:1f);
            card.setScaleY(focused?1.018f:1f);
        });
        card.setOnClickListener(v -> item.open.run());
        return card;
    }

    private void explore() {
        final String[] choices={
            "TRY COLLECT OR DODGE",
            "ANDROID DOJO / DEVELOP NEW INTUITIONS",
            "PORTABILITY RESEARCH / WHAT ELSE CAN RUN?"
        };
        new AlertDialog.Builder(this).setTitle("EXPLORE / OPTIONAL")
            .setItems(choices,(dialog,index)->{
                Class<?> target=index==0?MiniGameLabActivity.class:
                    index==1?AndroidDojoActivity.class:PortabilityLabActivity.class;
                startActivity(new Intent(this,target));
            }).setNegativeButton("BACK",null).show();
    }
}
