package dev.jadan.fire;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fire-owned terminal, not a remote command endpoint. The stable, top-anchored
 * command deck is kept above the scrollable output: the Fire IME can occlude
 * bottom content, but cannot force the user into an invisible input field.
 */
public final class TerminalActivity extends Activity {
    private static final int OUTPUT_LIMIT=24000;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile Process active;
    private File cwd;
    private EditText entry;
    private TextView output, status, keyboardHint;
    private ScrollView outputScroll;
    private Button type, run;
    private boolean busy;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN |
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        cwd=new File(getFilesDir(),"workspace");
        if(!cwd.isDirectory()&&!cwd.mkdirs())cwd=getFilesDir();

        // Single screen with a fixed command deck at the TOP, not under the IME.
        LinearLayout root=new LinearLayout(this);
        TvUi.page(this,root);
        setContentView(root);
        root.addView(TvUi.header(this,"FIRE  /  TERMINAL"));
        root.addView(TvUi.text(this,"Local app shell · No root or remote access · Back returns to Console",15,TvUi.SUB));
        status=TvUi.text(this,"Ready · "+cwd.getAbsolutePath(),15,TvUi.INK);
        root.addView(status);

        LinearLayout deck=TvUi.panel(this);
        deck.addView(TvUi.section(this,"COMMAND DECK"));
        keyboardHint=TvUi.text(this,
            "Navigate with D-pad. Select TYPE to summon the Fire keyboard; Back closes the keyboard. RUN always remains above output.",
            14,TvUi.SUB);
        deck.addView(keyboardHint);
        entry=new EditText(this);
        entry.setSingleLine(true);
        entry.setHint("Command to run in app workspace");
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE);
        TvUi.field(this,entry);
        // KEY INVARIANT: focusing this field does NOT automatically reopen the IME.
        // The keyboard is entered only by explicitly selecting TYPE.
        entry.setShowSoftInputOnFocus(false);
        deck.addView(entry,new LinearLayout.LayoutParams(-1,-2));
        entry.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_DONE ||
                (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&
                 event.getAction()==KeyEvent.ACTION_UP)){
                execute();
                return true;
            }
            return false;
        });

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0,TvUi.dp(this,12),0,0);
        deck.addView(actions);
        type=button(actions,"TYPE",this::openKeyboard);
        run=button(actions,"RUN",this::execute);
        button(actions,"HIDE",this::hideKeyboard);
        button(actions,"STOP",this::stop);
        button(actions,"COPY",this::copy);
        button(actions,"EXIT",()->{hideKeyboard();finish();});
        TvUi.addPanel(this,root,deck);

        LinearLayout outputDeck=TvUi.panel(this);
        outputDeck.addView(TvUi.section(this,"OUTPUT  /  COPYABLE LOG"));
        outputScroll=new ScrollView(this);
        outputScroll.setFillViewport(true);
        outputScroll.setFocusable(false);
        output=TvUi.text(this,"$ pwd\n"+cwd.getAbsolutePath()+"\n",16,TvUi.INK);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        // A non-focusable log prevents Fire's D-pad from getting trapped in text
        // selection. COPY uses the system clipboard; output is not discarded.
        output.setFocusable(false);
        outputScroll.addView(output);
        outputDeck.addView(outputScroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(outputDeck,new LinearLayout.LayoutParams(-1,0,1));

        // On entry, focus a visible action, never the EditText. The keyboard
        // cannot obscure the RUN/HIDE controls while the operator is typing.
        type.requestFocus();
    }

    private Button button(LinearLayout parent,String text,Runnable action){
        Button b=TvUi.button(this,text,action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.rightMargin=TvUi.dp(this,6);
        parent.addView(b,lp);
        return b;
    }

    private void openKeyboard(){
        if(isFinishing())return;
        entry.requestFocus();
        keyboardHint.setText("Keyboard requested. Use Done or Back to return to D-pad controls; RUN is above the log.");
        InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(input!=null)entry.post(()->input.showSoftInput(entry,InputMethodManager.SHOW_IMPLICIT));
    }

    private void hideKeyboard(){
        InputMethodManager input=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(input!=null)input.hideSoftInputFromWindow(entry.getWindowToken(),0);
        entry.clearFocus();
        type.requestFocus();
        keyboardHint.setText("D-pad controls ready. TYPE reopens the keyboard explicitly.");
    }

    private void stop(){
        Process p=active;
        if(p!=null){
            p.destroy();
            status.setText("Stop requested · waiting for process exit");
        } else status.setText("No command running. Ready.");
        hideKeyboard();
    }

    private void copy(){
        ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(manager!=null){
            manager.setPrimaryClip(ClipData.newPlainText("Fire terminal output",output.getText()));
            status.setText("Terminal output copied.");
        }
        hideKeyboard();
    }

    private void execute(){
        if(busy){status.setText("Command still running. STOP or wait; draft retained.");return;}
        final String command=entry.getText().toString().trim();
        hideKeyboard();
        if(command.isEmpty()){
            status.setText("Nothing to run. Select TYPE to enter a command.");
            return;
        }
        if(command.length()>4096){
            status.setText("Command exceeds 4096 characters; draft retained.");return;
        }
        entry.setText("");
        append("$ "+command+"\n");
        if("cd".equals(command)||command.startsWith("cd ")){
            String path=command.length()==2?getFilesDir().getAbsolutePath():command.substring(3).trim();
            if("~".equals(path))path=getFilesDir().getAbsolutePath();
            try{
                File next=new File(path);
                if(!next.isAbsolute())next=new File(cwd,path);
                next=next.getCanonicalFile();
                if(!next.isDirectory())throw new Exception("Directory not found");
                cwd=next;status.setText("Directory: "+cwd.getAbsolutePath());
            }catch(Exception ex){append("cd: "+ex.getMessage()+"\n");}
            return;
        }
        busy=true;run.setEnabled(false);
        status.setText("Running as app UID · 15-second limit");
        final File directory=cwd;
        worker.execute(()->{
            String result;
            try{
                Process p=new ProcessBuilder("/system/bin/sh","-c",command)
                    .directory(directory).redirectErrorStream(true).start();
                active=p;
                ByteArrayOutputStream sink=new ByteArrayOutputStream();
                Thread reader=new Thread(()->{
                    try(InputStream input=p.getInputStream()){
                        byte[] chunk=new byte[2048];int n;
                        while((n=input.read(chunk))!=-1){
                            synchronized(sink){
                                if(sink.size()<OUTPUT_LIMIT)
                                    sink.write(chunk,0,Math.min(n,OUTPUT_LIMIT-sink.size()));
                            }
                        }
                    }catch(Exception ignored){}
                },"fire-terminal-drain");
                reader.setDaemon(true);reader.start();
                boolean finished=p.waitFor(15,TimeUnit.SECONDS);
                if(!finished)p.destroyForcibly();
                reader.join(1000);
                synchronized(sink){
                    result=new String(sink.toByteArray(),StandardCharsets.UTF_8);
                    if(sink.size()>=OUTPUT_LIMIT)result+="\n[output truncated]";
                }
                result+=finished?"\n[exit "+p.exitValue()+"]\n":"\n[15s timeout; process stopped]\n";
            }catch(Exception ex){
                result=ex.getClass().getSimpleName()+": "+ex.getMessage()+"\n";
            }finally{active=null;}
            final String shown=result;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                append(shown);
                busy=false;run.setEnabled(true);
                status.setText("Ready · "+cwd.getAbsolutePath());
            });
        });
    }
    private void append(String text){
        String prior=output.getText().toString();
        if(prior.length()>32000)prior=prior.substring(prior.length()-16000);
        output.setText(prior+text);
        if(outputScroll!=null)
            outputScroll.post(()->outputScroll.fullScroll(View.FOCUS_DOWN));
    }
    @Override protected void onDestroy(){
        Process p=active;
        if(p!=null)p.destroyForcibly();
        worker.shutdownNow();
        super.onDestroy();
    }
}
