package dev.jadan.fire;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * On-TV research lookup, not an installer.
 * Reads the exact portability matrix consumed by the host agent CLI.
 * A missing record is an open question, never a claim of impossibility.
 */
public final class PortabilityLabActivity extends Activity {
    private JSONArray entries = new JSONArray();
    private LinearLayout root;
    private ScrollView scroll;
    private EditText query;
    private TextView feedback;
    private SharedPreferences notes;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        notes = getSharedPreferences("portability_research_v1", MODE_PRIVATE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        TvUi.page(this, root);
        scroll.addView(root);
        setContentView(scroll);
        try (InputStream in = getAssets().open("targets.json");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            entries = new JSONObject(out.toString(StandardCharsets.UTF_8.name()))
                .getJSONArray("entries");
        } catch (Exception ex) {
            root.addView(TvUi.header(this, "PORTABILITY / RESEARCH UNAVAILABLE"));
            root.addView(TvUi.text(this, "The local matrix could not be read. Existing games remain untouched.",
                16, TvUi.SUB));
            return;
        }
        showIndex();
    }

    private void start(String name) {
        JSONObject found = find(name);
        if (found == null) showUnassessed(name.trim());
        else showEntry(found);
    }

    private JSONObject find(String name) {
        String term = name.trim();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) continue;
            if (item.optString("id").equalsIgnoreCase(term)) return item;
            JSONArray aliases = item.optJSONArray("names");
            if (aliases != null) for (int j = 0; j < aliases.length(); j++)
                if (aliases.optString(j).equalsIgnoreCase(term)) return item;
        }
        return null;
    }

    private void heading(String message) {
        root.removeAllViews();
        root.addView(TvUi.header(this, "PORTABILITY  /  THE RESEARCH FRONTIER"));
        root.addView(TvUi.text(this, message, 16, TvUi.SUB));
    }

    private void showIndex() {
        heading("Ask for software, compare routes, and identify what remains an experiment. A listing does not install an app or prove TV usability.");
        LinearLayout panel = TvUi.panel(this);
        panel.addView(TvUi.section(this, "GET ME THIS APP / GAME"));
        query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("VS Code, emulator, Windows program ...");
        query.setText(notes.getString("draft", ""));
        TvUi.field(this, query);
        panel.addView(query);
        feedback = TvUi.text(this, "Unknown software becomes a research task, not an automatic rejection.", 15, TvUi.SUB);
        panel.addView(feedback);
        TvUi.action(this, panel, "LOOK UP THE ROUTES", () -> {
            String name = query.getText().toString().trim();
            if (name.isEmpty() || name.length() > 100) {
                feedback.setText("Enter a program name between 1 and 100 characters.");
                return;
            }
            notes.edit().putString("draft", name).apply();
            start(name);
        });
        TvUi.addPanel(this, root, panel);

        root.addView(TvUi.section(this, "KNOWN / UNDER INVESTIGATION"));
        Button first = null;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id");
            Button b = TvUi.action(this, root,
                item.optJSONArray("names").optString(0) + "   /   " + id.toUpperCase(),
                () -> start(id));
            if (first == null) first = b;
        }
        JSONArray pending = savedTargets();
        if (pending.length() > 0) {
            root.addView(TvUi.section(this, "YOUR UNASSESSED REQUESTS"));
            for (int i = pending.length() - 1; i >= 0; i--) {
                String name = pending.optString(i);
                TvUi.action(this, root, "INVESTIGATE  " + name, () -> start(name));
            }
        }
        if (first != null) first.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private JSONArray savedTargets() {
        try { return new JSONArray(notes.getString("unknown", "[]")); }
        catch (Exception ex) { return new JSONArray(); }
    }

    private void showUnassessed(String name) {
        heading(name + ": UNASSESSED. Absence from this index is not evidence of incompatibility.");
        Button back = TvUi.action(this, root, "←  INDEX", this::showIndex);
        LinearLayout panel = TvUi.panel(this);
        panel.addView(TvUi.section(this, "OPEN RESEARCH QUESTION"));
        panel.addView(TvUi.text(this, "Determine the software's requirements and intended user experience. Compare native Android, browser, emulation, remote execution, and reconstruction. Then assess remote and optional iPad controls separately.",
            17, TvUi.INK));
        TvUi.action(this, panel, "SAVE TO LOCAL RESEARCH QUEUE", () -> {
            JSONArray current = savedTargets();
            for (int i = 0; i < current.length(); i++)
                if (name.equalsIgnoreCase(current.optString(i))) {
                    Toast.makeText(this,"Already in research queue",Toast.LENGTH_SHORT).show();
                    return;
                }
            if (current.length() >= 32) {
                Toast.makeText(this,"Queue full; existing requests retained",Toast.LENGTH_SHORT).show();
                return;
            }
            current.put(name);
            notes.edit().putString("unknown", current.toString()).apply();
            Toast.makeText(this,"Saved research request",Toast.LENGTH_SHORT).show();
        });
        TvUi.action(this, panel, "COPY AGENT RESEARCH QUESTION",
            () -> copy(name, "No known route in the local index; investigate without guessing feasibility.", "unassessed"));
        TvUi.addPanel(this, root, panel);
        back.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private void showEntry(JSONObject entry) {
        heading(entry.optString("intent"));
        Button back = TvUi.action(this, root, "←  INDEX", this::showIndex);
        root.addView(TvUi.section(this, entry.optString("id").toUpperCase()));
        JSONArray routes = entry.optJSONArray("routes");
        if (routes != null) for (int i = 0; i < routes.length(); i++) {
            JSONObject r = routes.optJSONObject(i);
            if (r == null) continue;
            LinearLayout panel = TvUi.panel(this);
            panel.addView(TvUi.section(this, r.optString("id") + "  /  " + r.optString("state")));
            panel.addView(TvUi.text(this,
                "Execution: " + r.optString("execution"), 16, TvUi.INK));
            JSONObject controls = r.optJSONObject("interaction");
            if (controls != null) {
                panel.addView(TvUi.text(this,
                    "TV remote: " + controls.optString("remote", "unknown") +
                    "   |   iPad: " + controls.optString("ipad_companion", "unknown") +
                    "   |   keyboard/mouse: " + controls.optString("keyboard_mouse", "unknown"),
                    16, TvUi.INK));
            }
            JSONArray adaptation = r.optJSONArray("adaptation");
            if (adaptation != null) for (int j = 0; j < adaptation.length(); j++)
                panel.addView(TvUi.text(this, "Adaptation: " + adaptation.optString(j),
                    15, TvUi.SUB));
            JSONArray blockers = r.optJSONArray("blockers");
            if (blockers != null) for (int j = 0; j < blockers.length(); j++)
                panel.addView(TvUi.text(this, "Open issue: " + blockers.optString(j),
                    15, TvUi.SUB));
            JSONArray evidence = r.optJSONArray("evidence");
            panel.addView(TvUi.text(this, "References: " +
                (evidence == null ? 0 : evidence.length()) +
                " (source links are not physical Fire TV verification)", 14, TvUi.SUB));
            panel.addView(TvUi.text(this, "Next experiment: " + r.optString("next_check"),
                16, TvUi.INK));
            String next = r.optString("next_check");
            String state = r.optString("state");
            TvUi.action(this, panel, "COPY ROUTE INVESTIGATION FOR AGENT",
                () -> copy(entry.optString("id"), next, state));
            TvUi.addPanel(this, root, panel);
        }
        back.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private void copy(String name, String next, String state) {
        ClipboardManager cb = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb == null) return;
        String prompt = "Fire Console / Portability Intelligence\n" +
            "Request: Get " + name + " usable on my TV.\n" +
            "Current indexed state: " + state + ". This is not a device test.\n" +
            "Current research step: " + next + "\n" +
            "Compare execution feasibility, remote-only usability, optional iPad control, " +
            "reusable adaptations, legal constraints and real device evidence. " +
            "Investigate existing implementations before proposing new architecture. " +
            "Explain which limitations are proven and which remain hypotheses. " +
            "Do not install anything automatically.";
        cb.setPrimaryClip(ClipData.newPlainText("Fire portability research", prompt));
        Toast.makeText(this,"Copied for an agent; nothing installed",Toast.LENGTH_SHORT).show();
    }

    @Override public void onBackPressed() {
        if (query == null || query.getParent() == null) showIndex();
        else super.onBackPressed();
    }
}
