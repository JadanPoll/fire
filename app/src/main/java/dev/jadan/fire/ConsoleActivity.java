package dev.jadan.fire;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Fire Console: lightweight, D-pad-native channel home over the EXISTING apps.
 *
 * Source models: Android TV Leanback launcher/focus contract and lightweight
 * TV launcher app grids. This is NOT a replacement for Fire OS Home, not an
 * accessibility-based key remapper, and not an installer.
 *
 * All existing app packages, account sessions and update flows remain intact.
 */
public final class ConsoleActivity extends Activity {
    static final String BROWSER="dev.jadan.fire.browser";
    static final String INBOX="https://fire-inbox-jadan.brinyduck2.chatgpt.site/";
    private TextView status;
    private final int navy=0xFF0F2441, muted=0xFFACBED4;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFFEDF5FA);
        content.setPadding(dp(25),dp(17),dp(25),dp(13));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(0xFFEDF5FA);
        scroll.addView(content);
        setContentView(scroll);

        LinearLayout banner=new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(banner,new LinearLayout.LayoutParams(-1,dp(65)));
        TextView brand=new TextView(this);
        brand.setText("FIRE  /  CONSOLE");
        brand.setTextColor(navy);
        brand.setTextSize(29);brand.setTypeface(null,1);
        banner.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView hint=new TextView(this);
        hint.setText("CHANNELS");
        hint.setTextColor(0xFF58738B);hint.setTextSize(14);
        hint.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        banner.addView(hint);

        TextView intro=new TextView(this);
        intro.setText("Pick a channel with the D-pad. OK opens it; Back returns here.");
        intro.setTextColor(0xFF4A657E);intro.setTextSize(16);
        intro.setPadding(0,0,0,dp(13));
        content.addView(intro);

        // Only permanent destinations deserve Home space. Early diagnostic
        // experiments remain in Tools; independent APKs belong to Apps.
        final Channel[] channels={
            new Channel("Inbox","Messages and notes",0xFF23A8BF,0,()->openBrowser(INBOX)),
            new Channel("Browser","Browse and work",0xFF268ED0,1,()->openBrowser(null)),
            new Channel("Playroom","Games and experiments",0xFFE5A84C,2,
                ()->startActivity(new Intent(this,PlayroomHubActivity.class))),
            new Channel("Compute","Offshore work and Codex",0xFF8573D0,3,
                ()->startActivity(new Intent(this,CodexDeskActivity.class))),
            new Channel("Apps","Independent applications",0xFF5D9CB6,6,
                ()->startActivity(new Intent(this,AppLibraryActivity.class))),
            new Channel("Tools","Updates and diagnostics",0xFF71849A,7,
                ()->startActivity(new Intent(this,MainActivity.class)))
        };

        View first=null;
        for(int row=0;row<2;row++){
            LinearLayout strip=new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLP=new LinearLayout.LayoutParams(-1,0,1);
            rowLP.bottomMargin=dp(13);
            content.addView(strip,rowLP);
            for(int col=0;col<3;col++){
                Channel channel=channels[row*3+col];
                FrameLayout tile=tile(channel);
                LinearLayout.LayoutParams cardLP=new LinearLayout.LayoutParams(0,-1,1);
                if(col>0)cardLP.leftMargin=dp(14);
                strip.addView(tile,cardLP);
                if(first==null)first=tile;
            }
        }

        LinearLayout footer=new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(footer,new LinearLayout.LayoutParams(-1,dp(46)));
        status=new TextView(this);
        status.setTextColor(0xFF48637C);status.setTextSize(15);
        status.setText("Ready · Local console · No background network requests");
        status.setSingleLine(true);
        footer.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        TextView help=new TextView(this);
        help.setText("◀ ▶ ▲ ▼  NAVIGATE     OK  OPEN");
        help.setTextColor(0xFF7792A5);help.setTextSize(13);
        footer.addView(help);
        if(first!=null)first.requestFocus();
    }

    private static final class Channel{
        final String name,subtitle;
        final int color,icon;
        final Runnable launch;
        Channel(String name,String subtitle,int color,int icon,Runnable launch){
            this.name=name;this.subtitle=subtitle;this.color=color;this.icon=icon;this.launch=launch;
        }
    }

    private GradientDrawable shape(int fill,int outline,int stroke){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(fill);bg.setCornerRadius(dp(17));
        bg.setStroke(dp(stroke),outline);
        return bg;
    }

    private FrameLayout tile(Channel item){
        FrameLayout card=new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(shape(0xFFFFFFFF,0xFFD9E5ED,1));
        card.setElevation(dp(2));
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(10),dp(12),dp(10),dp(9));
        card.addView(body,new FrameLayout.LayoutParams(-1,-1));
        ChannelPicture picture=new ChannelPicture(this,item.icon,item.color);
        LinearLayout.LayoutParams pic=new LinearLayout.LayoutParams(dp(70),0,1);
        body.addView(picture,pic);
        TextView name=new TextView(this);
        name.setText(item.name);
        name.setTextSize(21);name.setTextColor(navy);name.setTypeface(null,1);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        body.addView(name,new LinearLayout.LayoutParams(-1,dp(32)));
        TextView subtitle=new TextView(this);
        subtitle.setText(item.subtitle);
        subtitle.setTextColor(0xFF6D8599);
        subtitle.setTextSize(12);subtitle.setGravity(Gravity.CENTER);
        subtitle.setSingleLine(true);
        body.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(25)));

        card.setOnFocusChangeListener((v,focused)->{
            card.setBackground(focused
                ? shape(0xFFFCFEFF,item.color,3)
                : shape(0xFFFFFFFF,0xFFD9E5ED,1));
            card.setElevation(dp(focused?9:2));
            card.setScaleX(focused?1.025f:1f);
            card.setScaleY(focused?1.025f:1f);
            if(focused && status!=null)
                status.setText(item.name+" · "+item.subtitle+" · OK to open");
        });
        card.setOnClickListener(v->{
            if(status!=null)status.setText("Opening "+item.name+"…");
            try{item.launch.run();}
            catch(RuntimeException ex){
                if(status!=null)status.setText(item.name+": "+ex.getClass().getSimpleName()+
                    " · Open Tools → App Catalog if missing");
            }
        });
        card.setContentDescription(item.name+": "+item.subtitle);
        return card;
    }

    void openBrowser(String targetUrl){
        ConsoleLaunch.openBrowser(this,targetUrl,status);
    }

    /** Lightweight vector channel pictograms; no external font or image assets. */
    static final class ChannelPicture extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect=new RectF();
        private final int symbol,color;
        ChannelPicture(Context c,int symbol,int color){super(c);this.symbol=symbol;this.color=color;}
        private void line(Canvas c,float a,float b,float x,float y,float stroke){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(a,b,x,y,p);p.setStyle(Paint.Style.FILL);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int save=canvas.save();
            canvas.translate((getWidth()-Math.min(getWidth(),getHeight()))/2f,
                (getHeight()-Math.min(getWidth(),getHeight()))/2f);
            canvas.scale(Math.min(getWidth(),getHeight())/100f,
                Math.min(getWidth(),getHeight())/100f);
            p.setStyle(Paint.Style.FILL);p.setColor(color);
            canvas.drawRoundRect(7,7,93,93,22,22,p);
            p.setColor(0xFFFFFFFF);p.setStrokeWidth(5);
            switch(symbol){
                case 0: // mailbox envelope
                    p.setStyle(Paint.Style.STROKE);
                    canvas.drawRoundRect(22,32,78,70,6,6,p);
                    line(canvas,23,35,50,53,4);line(canvas,77,35,50,53,4);
                    break;
                case 1: // globe
                    p.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(50,50,27,p);
                    canvas.drawOval(39,23,61,77,p);
                    line(canvas,24,50,76,50,4);
                    break;
                case 2: // games controller
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(22,38,78,67);
                    canvas.drawRoundRect(rect,12,12,p);
                    line(canvas,34,50,46,50,5);line(canvas,40,44,40,56,5);
                    p.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(62,47,3,p);canvas.drawCircle(69,55,3,p);
                    break;
                case 3: // Compute: cloud with remote server/terminal
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(5);
                    Path cloud=new Path();
                    cloud.moveTo(28,65);
                    cloud.cubicTo(16,65,16,47,30,44);
                    cloud.cubicTo(31,28,49,25,59,37);
                    cloud.cubicTo(73,32,83,43,80,55);
                    cloud.cubicTo(87,65,79,73,68,73);
                    cloud.lineTo(30,73);
                    canvas.drawPath(cloud,p);
                    canvas.drawRoundRect(34,50,66,67,3,3,p);
                    line(canvas,40,58,46,58,3);line(canvas,40,63,46,63,3);
                    line(canvas,53,63,60,63,3);
                    break;
                case 6: // four applications
                    for(int i=0;i<2;i++)for(int j=0;j<2;j++)
                        canvas.drawRoundRect(27+25*i,27+25*j,47+25*i,47+25*j,5,5,p);
                    break;
                case 7: // settings cog
                    p.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(50,50,21,p);
                    canvas.drawCircle(50,50,8,p);
                    for(int k=0;k<8;k++){
                        double a=k*Math.PI/4;
                        line(canvas,(float)(50+21*Math.cos(a)),(float)(50+21*Math.sin(a)),
                            (float)(50+30*Math.cos(a)),(float)(50+30*Math.sin(a)),5);
                    }
                    break;
            }
            p.setStyle(Paint.Style.FILL);
            canvas.restoreToCount(save);
        }
    }
}
