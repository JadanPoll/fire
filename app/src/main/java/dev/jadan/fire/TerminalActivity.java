package dev.jadan.fire;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
 * User-invoked, app-UID-only shell. No root, ADB, listener, PTY, remote
 * commands, or background daemon. A long OK on the Codex channel opens it.
 */
public final class TerminalActivity extends Activity {
    private static final int OUTPUT_LIMIT = 24000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Process active;
    private File cwd;
    private EditText entry;
    private TextView output, status;
    private Button run;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        cwd = new File(getFilesDir(), "workspace");
        if (!cwd.isDirectory() && !cwd.mkdirs()) cwd = getFilesDir();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 16);
        root.setBackgroundColor(0xFF101B27);
        setContentView(root);
        TextView title = label("FIRE  /  LOCAL TERMINAL", 25, 0xFF8AD9FF);
        root.addView(title);
        root.addView(label("Commands run only as this Android app. Not root, ADB, Termux, or remote Codex. No commands are received from the network.", 14, 0xFFACC3D3));
        status = label("Ready: " + cwd.getAbsolutePath(), 14, 0xFFB9D3E5);
        root.addView(status);
        ScrollView scroll = new ScrollView(this);
        output = label("$ pwd\n" + cwd.getAbsolutePath() + "\n", 15, 0xFFE2F0F6);
        output.setTextIsSelectable(true);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        entry = new EditText(this);
        entry.setSingleLine(true);
        entry.setTextColor(0xFF142C40);
        entry.setBackgroundColor(0xFFE8F3F8);
        entry.setHint("Enter a shell command");
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE);
        root.addView(entry, new LinearLayout.LayoutParams(-1, -2));
        entry.setOnEditorActionListener((v,action,event)->{ execute(); return true; });
        LinearLayout actions = new LinearLayout(this);
        root.addView(actions);
        run = button(actions, "RUN", this::execute);
        button(actions, "STOP", () -> {
            Process p = active;
            if (p != null) {
                p.destroy();
                status.setText("Stop requested. Waiting for process to exit.");
            }
        });
        button(actions, "COPY", () -> {
            ClipboardManager c = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if (c != null) c.setPrimaryClip(ClipData.newPlainText("Fire terminal output", output.getText()));
            status.setText("Terminal output copied.");
        });
        button(actions, "CLOSE", this::finish);
        entry.requestFocus();
    }

    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        return t;
    }
    private Button button(LinearLayout parent, String text, Runnable task) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false);
        b.setOnClickListener(v -> task.run());
        parent.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        return b;
    }
    private void execute() {
        if (busy) return;
        final String command = entry.getText().toString().trim();
        if (command.isEmpty()) return;
        if (command.length() > 4096) {
            status.setText("Command exceeds 4096 characters."); return;
        }
        entry.setText("");
        append("$ " + command + "\n");
        if ("cd".equals(command) || command.startsWith("cd ")) {
            String path = command.length() == 2 ? getFilesDir().getAbsolutePath() : command.substring(3).trim();
            if (path.equals("~")) path = getFilesDir().getAbsolutePath();
            try {
                File next = new File(path);
                if (!next.isAbsolute()) next = new File(cwd, path);
                next = next.getCanonicalFile();
                if (!next.isDirectory()) throw new Exception("Directory not found");
                cwd = next; status.setText("Directory: " + cwd.getAbsolutePath());
            } catch (Exception ex) { append("cd: " + ex.getMessage() + "\n"); }
            return;
        }
        busy = true; run.setEnabled(false);
        status.setText("Running as app UID · 15-second limit");
        final File directory = cwd;
        worker.execute(() -> {
            String result;
            try {
                Process p = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(directory).redirectErrorStream(true).start();
                active = p;
                ByteArrayOutputStream sink = new ByteArrayOutputStream();
                Thread reader = new Thread(() -> {
                    try (InputStream input = p.getInputStream()) {
                        byte[] chunk = new byte[2048]; int n;
                        while ((n = input.read(chunk)) != -1) {
                            synchronized (sink) {
                                if (sink.size() < OUTPUT_LIMIT)
                                    sink.write(chunk, 0, Math.min(n, OUTPUT_LIMIT - sink.size()));
                            }
                        }
                    } catch (Exception ignored) {}
                }, "fire-terminal-drain");
                reader.setDaemon(true); reader.start();
                boolean finished = p.waitFor(15, TimeUnit.SECONDS);
                if (!finished) p.destroyForcibly();
                reader.join(1000);
                synchronized (sink) {
                    result = new String(sink.toByteArray(), StandardCharsets.UTF_8);
                    if (sink.size() >= OUTPUT_LIMIT) result += "\n[output truncated]";
                }
                result += finished ? "\n[exit " + p.exitValue() + "]\n" : "\n[15s timeout; process stopped]\n";
            } catch (Exception ex) {
                result = ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n";
            } finally { active = null; }
            final String shown = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                append(shown);
                busy = false; run.setEnabled(true);
                status.setText("Ready: " + cwd.getAbsolutePath());
            });
        });
    }
    private void append(String s) {
        String existing = output.getText().toString();
        if (existing.length() > 32000) existing = existing.substring(existing.length()-16000);
        output.setText(existing + s);
    }
    @Override protected void onDestroy() {
        Process p = active;
        if (p != null) p.destroyForcibly();
        worker.shutdownNow();
        super.onDestroy();
    }
}
