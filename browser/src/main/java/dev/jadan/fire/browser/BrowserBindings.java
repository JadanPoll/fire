package dev.jadan.fire.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;

/**
 * Browser-owned control profiles: mapped button -> normalized native touch.
 * Not a global keymapper and never able to interact with another app's IME.
 * A saved profile is intentionally OFF after process restart.
 */
final class BrowserBindings {
    interface Host {
        String pageHost();
        boolean keyboardVisible();
        boolean nativeEditorFocused();
        void stateChanged(boolean capture,boolean mapped);
        void performTarget(Target target);
        void status(String message);
    }
    static final int MAX_SETS=8, MAX_BINDINGS=12;
    private static final String STORE_KEY="binding_sets_v1";
    static final class Target {
        final boolean web;
        final float x,y;
        Target(boolean web,float x,float y){
            this.web=web;
            this.x=Math.max(0.005f,Math.min(.995f,x));
            this.y=Math.max(0.005f,Math.min(.995f,y));
        }
    }
    private static final class Set {
        final String id;
        String label,host;
        final LinkedHashMap<Integer,Target> targets=new LinkedHashMap<>();
        Set(String id,String label,String host){
            this.id=id;this.label=label;this.host=host;
        }
    }
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Host host;
    private final ArrayList<Set> sets=new ArrayList<>();
    private final HashSet<Integer> pressed=new HashSet<>();
    private final HashMap<Integer,Long> lastTap=new HashMap<>();
    private String selectedId;
    private boolean capture,active,dialogOpen;

    BrowserBindings(Activity activity,SharedPreferences prefs,Host host){
        this.activity=activity;this.prefs=prefs;this.host=host;
        load();
    }
    boolean captureMode(){return capture;}
    boolean activeMode(){return active;}
    String selectedLabel(){
        Set selected=selected();
        return selected==null?"none":selected.label;
    }
    private Set selected(){
        for(Set set:sets)if(set.id.equals(selectedId))return set;
        return null;
    }
    private String currentHost(){
        String value=host.pageHost();
        return value==null?"":value.toLowerCase(Locale.ROOT);
    }
    private boolean matches(Set set){
        return set!=null && set.host.equals(currentHost());
    }
    static boolean allowedKey(int key){
        // These keys cannot be stolen from escape, navigation and dialog paths:
        // OK/ENTER/BUTTON_A, BACK/BUTTON_B, MENU, Play/Pause, Home, voice, volume.
        switch(key){
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_SPACE:
                return true;
            default:
                return key>=KeyEvent.KEYCODE_0 && key<=KeyEvent.KEYCODE_9;
        }
    }
    /** Returns true only when this browser deliberately owns the mapped key. */
    boolean onKey(KeyEvent event,long now){
        if(!active||capture||dialogOpen||host.keyboardVisible()||
           host.nativeEditorFocused()||!matches(selected()))return false;
        int key=event.getKeyCode();
        Set set=selected();
        if(set==null || !set.targets.containsKey(key))return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getRepeatCount()==0)pressed.add(key);
            return true; // hold/repeat does not fire repeated taps
        }
        if(event.getAction()==KeyEvent.ACTION_UP){
            if(pressed.remove(key)){
                Long last=lastTap.get(key);
                if(last==null||now-last>=240){
                    lastTap.put(key,now);
                    host.performTarget(set.targets.get(key));
                }
            }
            return true;
        }
        return false;
    }
    void stopInput(){
        pressed.clear();
        lastTap.clear();
    }
    void onPageChanged(String url){
        Set set=selected();
        String next="";
        try{
            android.net.Uri uri=android.net.Uri.parse(url);
            if(uri.getHost()!=null)next=uri.getHost().toLowerCase(Locale.ROOT);
        }catch(RuntimeException ignored){}
        if((active||capture) && (set==null||!set.host.equals(next))){
            active=false;capture=false;stopInput();
            host.stateChanged(false,false);
            host.status("Control set suspended: webpage changed. MENU / SETS to choose another set.");
        }
    }
    void disable(String explanation){
        active=false;capture=false;stopInput();
        host.stateChanged(false,false);
        host.status(explanation+" Saved sets are preserved.");
    }
    /** Open binding cursor by an explicit Play/Pause hold or wheel action. */
    void holdPlayPause(){
        if(host.keyboardVisible()){
            host.status("Close the Fire keyboard first. MENU / SETS always remains available.");
            return;
        }
        if(selected()==null)showWheel();
        else enterCapture();
    }
    private void enterCapture(){
        if(selected()==null){showWheel();return;}
        if(!matches(selected())){
            host.status("This set belongs to "+selected().host+
                ". Create a new set for "+currentHost()+".");
            showWheel();return;
        }
        active=false;capture=true;stopInput();
        host.stateChanged(true,false);
        host.status("BIND CURSOR · "+selected().label+
            " · aim at a page/button, OK to choose target · SETS to finish");
    }
    private void activate(Set set){
        if(!matches(set)){
            host.status("Set "+set.label+" belongs to "+set.host+
                ". Create a new set for this site.");
            return;
        }
        selectedId=set.id;
        capture=false;
        active=true;
        stopInput();
        host.stateChanged(false,true);
        host.status("CONTROL SET ON · "+set.label+" · "+
            set.targets.size()+" buttons · MENU / SETS for OFF or swap");
    }
    void captureTarget(Target target){
        if(!capture||selected()==null||host.keyboardVisible())return;
        Set set=selected();
        if(set.targets.size()>=MAX_BINDINGS){
            host.status("This set has 12 bindings. Remove one from the wheel first.");
            showWheel();return;
        }
        TextView message=new TextView(activity);
        String where=target.web?"webpage":"browser controls";
        message.setText("TARGET: "+where+" "+Math.round(target.x*100)+"%, "+
            Math.round(target.y*100)+"%\n\nPress a remote D-pad direction, Rewind / Fast Forward, or supported gamepad button. BACK cancels.\n\nAmazon's system keyboard is outside this browser; bind a webpage's own keys or controls instead.");
        message.setTextSize(18);message.setPadding(dp(22),dp(20),dp(22),dp(20));
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Bind remote button · "+set.label)
            .setView(message)
            .setNegativeButton("CANCEL",(d,w)->{})
            .create();
        dialog.setOnKeyListener((d,key,event)->{
            if(key==KeyEvent.KEYCODE_BACK)return false; // Back is always an escape
            if(event.getAction()!=KeyEvent.ACTION_DOWN||event.getRepeatCount()!=0)
                return allowedKey(key);
            if(!allowedKey(key)){
                message.setText("Reserved key: "+KeyEvent.keyCodeToString(key)+
                    ".\nUse D-pad, Rewind, Fast Forward or supported gamepad buttons. BACK cancels.");
                return true;
            }
            // Replacement is explicit, not silent: warn on an existing mapping.
            if(set.targets.containsKey(key)){
                dialog.dismiss();
                new AlertDialog.Builder(activity)
                    .setTitle("Replace "+KeyEvent.keyCodeToString(key)+"?")
                    .setMessage("This changes only the mapping in "+set.label+".")
                    .setPositiveButton("REPLACE",(which,w)->saveTarget(set,key,target))
                    .setNegativeButton("CANCEL",null).show();
                return true;
            }
            saveTarget(set,key,target);
            dialog.dismiss();
            return true;
        });
        dialog.show();
    }
    private void saveTarget(Set set,int key,Target target){
        if(!set.targets.containsKey(key)&&set.targets.size()>=MAX_BINDINGS){
            host.status("Set is full. No binding saved.");return;
        }
        set.targets.put(key,target);
        persist();
        host.status(set.label+" · "+KeyEvent.keyCodeToString(key)+" → "+
            (target.web?"webpage":"browser control")+
            " at "+Math.round(target.x*100)+"%, "+Math.round(target.y*100)+
            "% · aim and OK for another, SETS to activate");
    }
    private static String cleanLabel(String label){
        if(label==null)return "";
        return label.replaceAll("[\\p{Cntrl}]+"," ")
            .replaceAll("\\s+"," ").trim();
    }
    private int dp(int v){return (int)(v*activity.getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String text,int sp){
        TextView v=new TextView(activity);
        v.setText(text);v.setTextColor(0xFF193850);v.setTextSize(sp);
        v.setPadding(dp(4),dp(8),dp(4),dp(9));
        return v;
    }
    private Button item(LinearLayout parent,String text,Runnable run){
        Button b=new Button(activity);
        b.setText(text);b.setAllCaps(false);b.setTextSize(17);
        b.setTextColor(0xFF17324A);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xFFF7FCFF);bg.setCornerRadius(dp(15));
        bg.setStroke(dp(2),0xFF99C8E6);
        b.setBackground(bg);b.setMinHeight(dp(64));
        b.setOnFocusChangeListener((v,focused)->{
            GradientDrawable focusBg=new GradientDrawable();
            focusBg.setColor(focused?0xFFDBF2FF:0xFFF7FCFF);
            focusBg.setCornerRadius(dp(15));
            focusBg.setStroke(dp(focused?4:2),focused?0xFF157CAF:0xFF99C8E6);
            b.setBackground(focusBg);
        });
        b.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(6);
        parent.addView(b,params);
        return b;
    }
    /**
     * A 3×3 D-pad-native wheel. Centre always restores normal control;
     * up to eight named slots surround it. Advanced editing remains below.
     */
    void showWheel(){
        if(activity.isFinishing()||dialogOpen)return;
        dialogOpen=true;
        ScrollView scroll=new ScrollView(activity);
        LinearLayout column=new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16),dp(10),dp(16),dp(12));
        scroll.addView(column);
        column.addView(label("D-pad selects a named set. Centre turns custom controls OFF. Back closes without changes.",16));
        final AlertDialog[] holder=new AlertDialog[1];
        final Button[] preferred=new Button[1];
        Runnable close=()->{if(holder[0]!=null)holder[0].dismiss();};
        Set selected=selected();
        for(int row=0;row<3;row++){
            LinearLayout strip=new LinearLayout(activity);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams stripLP=new LinearLayout.LayoutParams(-1,-2);
            stripLP.bottomMargin=dp(5);
            column.addView(strip,stripLP);
            for(int col=0;col<3;col++){
                int cell=row*3+col;
                int slot=cell<4?cell:cell-1;
                Set set=cell==4?null:(slot<sets.size()?sets.get(slot):null);
                Button b=new Button(activity);
                b.setAllCaps(false);
                b.setTextSize(15);
                b.setMinHeight(dp(68));
                b.setText(cell==4?"NORMAL\nOFF":
                    set==null?"+":
                    set.label+"\n"+set.targets.size()+" keys");
                b.setEnabled(cell==4 || set!=null);
                b.setTextColor(0xFF193850);
                b.setBackgroundColor(cell==4?0xFFDBF7E9:0xFFF1F8FF);
                b.setOnFocusChangeListener((v,focused)->{
                    GradientDrawable bg=new GradientDrawable();
                    bg.setColor(focused?0xFFD8EDFF:(cell==4?0xFFDBF7E9:0xFFF1F8FF));
                    bg.setCornerRadius(dp(13));
                    bg.setStroke(dp(focused?4:1),focused?0xFF1285C4:0xFFBED7E9);
                    b.setBackground(bg);
                });
                LinearLayout.LayoutParams tileLP=new LinearLayout.LayoutParams(0,dp(76),1);
                if(col>0)tileLP.leftMargin=dp(6);
                strip.addView(b,tileLP);
                if(cell==4){
                    b.setOnClickListener(v->{close.run();disable("Normal browser controls restored.");});
                    if(preferred[0]==null)preferred[0]=b;
                }else if(set!=null){
                    b.setOnClickListener(v->{close.run();activate(set);});
                    if(set.id.equals(selectedId))preferred[0]=b;
                }
            }
        }
        item(column,"+ NEW LABELLED SET FOR THIS PAGE",()->{close.run();createSet();});
        if(selected!=null){
            item(column,"BIND TARGETS  /  "+selected.label,()->{
                close.run();enterCapture();
            });
            item(column,"REMOVE A BINDING",()->{close.run();removeBinding();});
            item(column,"RENAME SELECTED SET",()->{close.run();renameSet();});
            item(column,"DELETE SELECTED SET…",()->{close.run();deleteSet();});
        }
        item(column,"EMERGENCY RESTORE  /  KEEP SAVED SETS",()->{
            close.run();disable("Emergency restore.");
        });
        holder[0]=new AlertDialog.Builder(activity)
            .setTitle("CONTROL WHEEL  /  FIRE BROWSER")
            .setView(scroll)
            .setNegativeButton("CLOSE",null)
            .create();
        holder[0].setOnDismissListener(d->dialogOpen=false);
        holder[0].show();
        if(preferred[0]!=null)preferred[0].requestFocus();
    }
    private void createSet(){
        if(sets.size()>=MAX_SETS){
            host.status("Eight control sets already saved. Delete one to make room.");return;
        }
        String domain=currentHost();
        if(domain.isEmpty()){
            host.status("Open a webpage before creating a control set.");return;
        }
        EditText edit=new EditText(activity);
        edit.setSingleLine(true);
        edit.setText("Controls "+(sets.size()+1));
        edit.setSelectAllOnFocus(true);
        LinearLayout wrapper=new LinearLayout(activity);
        wrapper.setPadding(dp(18),dp(8),dp(18),dp(4));
        wrapper.addView(edit,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(activity).setTitle("New control set · "+domain)
            .setView(wrapper)
            .setPositiveButton("CREATE",(d,w)->{
                String name=cleanLabel(edit.getText().toString());
                if(name.isEmpty()||name.length()>32){
                    host.status("Set name must contain 1–32 characters. Nothing saved.");return;
                }
                for(Set old:sets)if(old.host.equals(domain)&&old.label.equalsIgnoreCase(name)){
                    host.status("A set with this name already exists for "+domain);return;
                }
                Set set=new Set(UUID.randomUUID().toString(),name,domain);
                sets.add(set);selectedId=set.id;persist();enterCapture();
            })
            .setNegativeButton("CANCEL",null).show();
    }
    private void removeBinding(){
        Set set=selected();if(set==null)return;
        if(set.targets.isEmpty()){
            host.status("No bindings to remove in "+set.label);return;
        }
        ArrayList<Integer> codes=new ArrayList<>(set.targets.keySet());
        String[] items=new String[codes.size()];
        for(int i=0;i<codes.size();i++)
            items[i]=KeyEvent.keyCodeToString(codes.get(i))+" → "+
                (set.targets.get(codes.get(i)).web?"webpage":"browser controls");
        new AlertDialog.Builder(activity).setTitle("Remove binding · "+set.label)
            .setItems(items,(d,which)->{
                set.targets.remove(codes.get(which));persist();
                host.status("Binding removed. Saved set retained.");
            })
            .setNegativeButton("CANCEL",null).show();
    }
    private void renameSet(){
        Set set=selected();if(set==null)return;
        EditText edit=new EditText(activity);edit.setSingleLine(true);
        edit.setText(set.label);
        new AlertDialog.Builder(activity).setTitle("Rename control set")
            .setView(edit)
            .setPositiveButton("RENAME",(d,w)->{
                String name=cleanLabel(edit.getText().toString());
                if(name.length()<1||name.length()>32){
                    host.status("Name must contain 1–32 characters. Not changed.");return;
                }
                set.label=name;persist();
                host.status("Renamed set to "+name);
            }).setNegativeButton("CANCEL",null).show();
    }
    private void deleteSet(){
        Set set=selected();if(set==null)return;
        new AlertDialog.Builder(activity).setTitle("Delete "+set.label+"?")
            .setMessage("Remove this control set and its button mappings. The browser, games and other sets are untouched.")
            .setPositiveButton("DELETE SET",(d,w)->{
                sets.remove(set);selectedId=null;disable("Selected set deleted.");
                persist();
            }).setNegativeButton("CANCEL",null).show();
    }
    private void persist(){
        try{
            JSONArray data=new JSONArray();
            for(Set set:sets){
                JSONArray items=new JSONArray();
                for(java.util.Map.Entry<Integer,Target> entry:set.targets.entrySet()){
                    Target t=entry.getValue();
                    items.put(new JSONObject().put("key",entry.getKey())
                        .put("web",t.web).put("x",t.x).put("y",t.y));
                }
                data.put(new JSONObject().put("id",set.id).put("label",set.label)
                    .put("host",set.host).put("bindings",items));
            }
            prefs.edit().putString(STORE_KEY,
                new JSONObject().put("version",1).put("sets",data).toString()).apply();
        }catch(Exception ex){
            host.status("Could not save bindings: "+ex.getClass().getSimpleName());
        }
    }
    private void load(){
        try{
            JSONObject root=new JSONObject(prefs.getString(STORE_KEY,"{}"));
            if(root.optInt("version")!=1)return;
            JSONArray data=root.optJSONArray("sets");if(data==null)return;
            for(int i=0;i<Math.min(MAX_SETS,data.length());i++){
                JSONObject o=data.optJSONObject(i);if(o==null)continue;
                String id=o.optString("id"),name=cleanLabel(o.optString("label"));
                String domain=o.optString("host").toLowerCase(Locale.ROOT);
                if(id.isEmpty()||id.length()>64||name.isEmpty()||name.length()>32||
                   !domain.matches("[a-z0-9.-]{1,253}"))continue;
                Set set=new Set(id,name,domain);
                JSONArray targets=o.optJSONArray("bindings");
                if(targets!=null)for(int j=0;j<Math.min(MAX_BINDINGS,targets.length());j++){
                    JSONObject t=targets.optJSONObject(j);if(t==null)continue;
                    int key=t.optInt("key",-1);
                    double px=t.optDouble("x",-1),py=t.optDouble("y",-1);
                    if(allowedKey(key)&&Double.isFinite(px)&&Double.isFinite(py)&&
                        px>=0&&px<=1&&py>=0&&py<=1)
                        set.targets.put(key,new Target(t.optBoolean("web",true),
                            (float)px,(float)py));
                }
                sets.add(set);
            }
        }catch(Exception ignored){
            // Corrupt settings must never prevent browser startup or trap input.
            sets.clear();
        }
        // Active mappings intentionally never restore across app restarts.
        active=false;capture=false;
    }
}
