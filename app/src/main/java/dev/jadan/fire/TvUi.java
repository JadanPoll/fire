package dev.jadan.fire;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared Fire Console TV language. Visual-only: no global key capture, app
 * privileges, background polling, or replacement for existing feature logic.
 *
 * Matches ConsoleActivity's light channel aesthetic. Every action provides
 * visible D-pad focus independent of hover/pointer behavior.
 */
final class TvUi {
    static final int BG=0xFFEDF5FA;
    static final int INK=0xFF0F2441;
    static final int SUB=0xFF55748B;
    static final int ACCENT=0xFF248AC2;
    static final int CARD=0xFFFFFFFF;
    private TvUi(){}

    static int dp(Activity a,int n){
        return (int)(n*a.getResources().getDisplayMetrics().density+0.5f);
    }
    static GradientDrawable rounded(Activity a,int fill,int stroke,int strokeWidth,int radius){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(a,radius));
        if(strokeWidth>0)bg.setStroke(dp(a,strokeWidth),stroke);
        return bg;
    }
    static void page(Activity a,LinearLayout root){
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,28),dp(a,16),dp(a,28),dp(a,22));
        root.setBackgroundColor(BG);
    }
    static TextView text(Activity a,String value,int size,int color){
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0,dp(a,6),0,dp(a,6));
        return t;
    }
    static TextView header(Activity a,String title){
        TextView t=text(a,title,28,INK);
        t.setTypeface(null,1);
        return t;
    }
    static TextView section(Activity a,String title){
        TextView t=text(a,title,20,INK);
        t.setTypeface(null,1);
        t.setPadding(0,dp(a,18),0,dp(a,9));
        return t;
    }
    static LinearLayout panel(Activity a){
        LinearLayout panel=new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));
        panel.setBackground(rounded(a,CARD,0xFFD5E4EE,1,15));
        panel.setElevation(dp(a,1));
        return panel;
    }
    static void addPanel(Activity a,LinearLayout parent,LinearLayout panel){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(a,10);lp.bottomMargin=dp(a,8);
        parent.addView(panel,lp);
    }
    static Button button(Activity a,String title,Runnable action){
        Button b=new Button(a);
        b.setText(title);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(INK);
        b.setMinHeight(dp(a,60));
        b.setBackground(rounded(a,CARD,0xFFD5E3ED,1,14));
        b.setFocusable(true);
        b.setOnFocusChangeListener((v,focused)->{
            b.setBackground(rounded(a,focused?0xFFF8FCFF:CARD,
                focused?ACCENT:0xFFD5E3ED,focused?3:1,14));
            b.setElevation(dp(a,focused?7:1));
            b.setScaleX(focused?1.01f:1f);
            b.setScaleY(focused?1.01f:1f);
        });
        b.setOnClickListener(v->action.run());
        return b;
    }
    static Button action(Activity a,LinearLayout parent,String title,Runnable fn){
        Button b=button(a,title,fn);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(a,9);
        parent.addView(b,lp);
        return b;
    }
    static void field(Activity a,EditText edit){
        edit.setTextColor(INK);
        edit.setHintTextColor(0xFF6E869A);
        edit.setTextSize(17);
        edit.setPadding(dp(a,14),dp(a,13),dp(a,14),dp(a,13));
        edit.setBackground(rounded(a,CARD,0xFFC8DBEA,2,12));
        edit.setOnFocusChangeListener((v,focused)->{
            edit.setBackground(rounded(a,CARD,focused?ACCENT:0xFFC8DBEA,focused?3:2,12));
        });
    }
}
