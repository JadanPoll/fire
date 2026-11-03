package dev.jadan.fire;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Installed-app picker. Uses the Android TV's actual LEANBACK_LAUNCHER
 * activity registry instead of maintaining a second app catalog.
 * This does not replace Fire OS Home or require QUERY_ALL_PACKAGES.
 */
public final class AppLibraryActivity extends Activity {
    private final int dark=0xFF173452;
    private TextView status;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFEDF5FA);
        root.setPadding(dp(24),dp(17),dp(24),dp(18));
        scroll.addView(root);setContentView(scroll);
        TextView title=text("APPLICATIONS  /  LIBRARY",28,dark);
        root.addView(title);
        status=text("Choose an installed TV application. Back returns to Fire Console.",15,0xFF617A91);
        root.addView(status);
        Button manager=new Button(this);
        manager.setText("APP CATALOG  ·  INSTALL / UPDATE FIRE CHANNELS");
        manager.setAllCaps(false);manager.setTextColor(0xFFFFFFFF);
        manager.setBackgroundColor(0xFF286F98);
        manager.setOnClickListener(v->startActivity(new Intent(this,CatalogActivity.class)));
        root.addView(manager,new LinearLayout.LayoutParams(-1,dp(62)));

        PackageManager pm=getPackageManager();
        Intent launcher=new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps;
        try{apps=pm.queryIntentActivities(launcher,0);}
        catch(RuntimeException e){
            status.setText("Installed app list unavailable: "+e.getClass().getSimpleName());
            return;
        }
        ArrayList<ResolveInfo> entries=new ArrayList<>();
        for(ResolveInfo info:apps){
            if(info.activityInfo==null)continue;
            if(getPackageName().equals(info.activityInfo.packageName))continue;
            entries.add(info);
        }
        Collections.sort(entries,Comparator.comparing(r->String.valueOf(r.loadLabel(pm)),
            String.CASE_INSENSITIVE_ORDER));
        int limit=Math.min(entries.size(),64);
        for(int i=0;i<limit;i+=4){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLP=new LinearLayout.LayoutParams(-1,dp(153));
            rowLP.topMargin=dp(13);root.addView(row,rowLP);
            for(int j=0;j<4;j++){
                if(i+j>=limit){
                    View spacer=new View(this);
                    row.addView(spacer,new LinearLayout.LayoutParams(0,-1,1));
                    continue;
                }
                ResolveInfo app=entries.get(i+j);
                FrameLayout tile=new FrameLayout(this);
                tile.setFocusable(true);tile.setClickable(true);
                LinearLayout.LayoutParams tileLP=new LinearLayout.LayoutParams(0,-1,1);
                if(j>0)tileLP.leftMargin=dp(14);
                row.addView(tile,tileLP);
                tile.setBackground(bg(false));
                LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
                body.setGravity(Gravity.CENTER);body.setPadding(dp(8),dp(10),dp(8),dp(7));
                tile.addView(body,new FrameLayout.LayoutParams(-1,-1));
                ImageView icon=new ImageView(this);
                try{icon.setImageDrawable(app.loadIcon(pm));}
                catch(RuntimeException ignored){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                body.addView(icon,new LinearLayout.LayoutParams(dp(66),0,1));
                TextView label=text(String.valueOf(app.loadLabel(pm)),16,dark);
                label.setSingleLine(true);label.setGravity(Gravity.CENTER);
                body.addView(label,new LinearLayout.LayoutParams(-1,dp(40)));
                final String pkg=app.activityInfo.packageName;
                final String activity=app.activityInfo.name;
                tile.setOnFocusChangeListener((view,focused)->{
                    tile.setBackground(bg(focused));
                    tile.setElevation(dp(focused?8:1));
                    if(focused)status.setText(String.valueOf(app.loadLabel(pm))+" · OK to open");
                });
                tile.setOnClickListener(v->{
                    try{
                        Intent intent=new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                        intent.setClassName(pkg,activity);
                        startActivity(intent);
                    }catch(RuntimeException e){
                        status.setText("Could not open "+pkg+": "+e.getClass().getSimpleName());
                    }
                });
            }
        }
        if(limit==0)status.setText("No other Leanback applications visible. Open Catalog to install channels.");
        else if(entries.size()>limit)status.setText("Showing "+limit+" of "+entries.size()+" TV launchers.");
        manager.requestFocus();
    }
    private TextView text(String name,int size,int color){
        TextView v=new TextView(this);v.setText(name);v.setTextSize(size);
        v.setTextColor(color);v.setPadding(0,dp(8),0,dp(6));return v;
    }
    private GradientDrawable bg(boolean focused){
        GradientDrawable d=new GradientDrawable();
        d.setColor(0xFFFFFFFF);d.setCornerRadius(dp(16));
        d.setStroke(dp(focused?3:1),focused?0xFF3B9ACF:0xFFCEDFE9);
        return d;
    }
}
