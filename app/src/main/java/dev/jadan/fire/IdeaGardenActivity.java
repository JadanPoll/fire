package dev.jadan.fire;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The human is the product of Android Dojo: possibilities, questions and
 * changed mental models. Agent prompts are suggested, never sent or executed.
 * Storage is local to Fire Console, independent of lesson completion.
 */
public final class IdeaGardenActivity extends Activity {
    private static final int MAX_IDEAS = 100;
    private static final String PREF = "fire_dojo_ideas_v1";
    private static final String KEY = "entries";
    private JSONArray challenges = new JSONArray();
    private SharedPreferences store;
    private LinearLayout root, journal;
    private ScrollView scroller;
    private EditText draft;
    private TextView feedback;
    private String challengeId;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = getSharedPreferences(PREF, MODE_PRIVATE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        root = new LinearLayout(this);
        DojoTheme.page(this, root);
        scroller.addView(root);
        setContentView(scroller);
        try (InputStream in = getAssets().open("catalog.json");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            challenges = new JSONObject(out.toString(StandardCharsets.UTF_8.name()))
                .getJSONArray("challenges");
        } catch (Exception e) {
            root.addView(DojoTheme.headline(this, "IDEA GARDEN / CATALOG UNAVAILABLE"));
            root.addView(DojoTheme.label(this, "Your saved ideas are untouched.", 16, DojoTheme.MUTED));
            return;
        }
        challengeId = saved != null ? saved.getString("origin") :
            getIntent().getStringExtra("challenge_id");
        if (find(challengeId) == null && challenges.length() > 0)
            challengeId = challenges.optJSONObject(0).optString("id");
        render(saved == null ? "" : saved.getString("draft", ""));
    }

    private JSONObject find(String id) {
        if (id == null) return null;
        for (int i = 0; i < challenges.length(); i++) {
            JSONObject c = challenges.optJSONObject(i);
            if (c != null && id.equals(c.optString("id"))) return c;
        }
        return null;
    }

    private JSONArray entries() {
        try { return new JSONArray(store.getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void render(String pending) {
        root.removeAllViews();
        root.addView(DojoTheme.label(this, "発想   /   THE IDEA GARDEN", 17, DojoTheme.GOLD));
        root.addView(DojoTheme.headline(this, "WHAT CAN'T WE DO WITH THIS TV?"));
        root.addView(DojoTheme.label(this,
            "Grow your own questions and intuitions. An experiment is useful when it changes what you can imagine, predict, or ask an agent.",
            16, DojoTheme.MUTED));

        JSONObject c = find(challengeId);
        LinearLayout origin = DojoTheme.card(this);
        origin.addView(DojoTheme.label(this, "CURRENT INSPIRATION", 17, DojoTheme.GOLD));
        origin.addView(DojoTheme.label(this, c == null ? "Open curiosity" :
            c.optString("title"), 19, DojoTheme.INK));
        Button choose = DojoTheme.action(this, origin, "CHOOSE ANOTHER PROBLEM", this::chooseChallenge);
        if (c != null) {
            origin.addView(DojoTheme.label(this, "UNEXPECTED POSSIBILITIES TO CONSIDER",
                16, DojoTheme.GOLD));
            JSONArray seeds = c.optJSONArray("possibility_seeds");
            if (seeds != null) for (int i = 0; i < seeds.length(); i++) {
                String seed = seeds.optString(i);
                DojoTheme.action(this, origin, seed, () -> {
                    draft.setText(seed);
                    feedback.setText("Idea selected, not saved or sent. Edit it or compare alternatives.");
                });
            }
        }
        DojoTheme.addCard(this, root, origin);

        LinearLayout work = DojoTheme.card(this);
        work.addView(DojoTheme.label(this, "YOUR QUESTION / HYPOTHESIS / NEW POSSIBILITY",
            17, DojoTheme.GOLD));
        draft = new EditText(this);
        draft.setSingleLine(false);
        draft.setMinLines(3);
        draft.setMaxLines(5);
        draft.setHint("What would be possible if...?");
        DojoTheme.input(this, draft);
        draft.setText(pending);
        work.addView(draft);
        feedback = DojoTheme.label(this,
            "No grading or forced progression. Agent prompts only copy to clipboard.",
            14, DojoTheme.MUTED);
        work.addView(feedback);
        DojoTheme.action(this, work, "SAVE THIS IDEA LOCALLY", this::save);
        DojoTheme.action(this, work, "COPY AN INVESTIGATION QUESTION FOR AN AGENT", this::copyQuestion);
        DojoTheme.action(this, work, "REFLECT ON WHAT CHANGED MY THINKING", () -> {
            String q = draft.getText().toString().trim();
            if (q.isEmpty()) {
                feedback.setText("Choose or type a question before reflecting.");
            } else {
                draft.setText(IdeaQuestionPolicy.reflection(q));
                feedback.setText("Reflection prompt ready to edit. No score and no automatic save.");
            }
        });
        DojoTheme.addCard(this, root, work);

        LinearLayout saved = DojoTheme.card(this);
        saved.addView(DojoTheme.label(this, "SAVED IDEAS / CONTINUE ANOTHER DAY",
            17, DojoTheme.GOLD));
        journal = new LinearLayout(this);
        journal.setOrientation(LinearLayout.VERTICAL);
        saved.addView(journal);
        DojoTheme.addCard(this, root, saved);
        refreshSaved();
        choose.requestFocus();
        scroller.scrollTo(0, 0);
    }

    private void chooseChallenge() {
        String[] titles = new String[challenges.length()];
        for (int i = 0; i < titles.length; i++) {
            JSONObject c = challenges.optJSONObject(i);
            titles[i] = c == null ? "Unspecified challenge" : c.optString("title");
        }
        new AlertDialog.Builder(this).setTitle("Choose an inspiration")
            .setItems(titles, (dialog, index) -> {
                JSONObject c = challenges.optJSONObject(index);
                if (c != null) {
                    String pending = draft == null ? "" : draft.getText().toString();
                    challengeId = c.optString("id");
                    render(pending);
                }
            }).setNegativeButton("CANCEL", null).show();
    }

    private void save() {
        String q = draft.getText().toString().trim();
        if (q.isEmpty() || q.length() > 1500) {
            feedback.setText("Enter a question or idea between 1 and 1500 characters.");
            return;
        }
        JSONArray records = entries();
        if (records.length() >= MAX_IDEAS) {
            feedback.setText("Garden is full (100 ideas). Existing ideas preserved.");
            return;
        }
        try {
            records.put(new JSONObject().put("id", System.currentTimeMillis())
                .put("origin", challengeId).put("question", q));
            if (!store.edit().putString(KEY, records.toString()).commit()) {
                feedback.setText("Could not save; draft remains editable.");
                return;
            }
            feedback.setText("Saved. Revisit this question whenever you want.");
            draft.setText("");
            refreshSaved();
        } catch (Exception e) {
            feedback.setText("Could not save; draft remains editable.");
        }
    }

    private void copyQuestion() {
        String q = draft.getText().toString().trim();
        try {
            JSONObject c = find(challengeId);
            String text = IdeaQuestionPolicy.forAgent(c == null ? "Open exploration" :
                c.optString("title"), q);
            ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) { feedback.setText("Clipboard unavailable."); return; }
            clipboard.setPrimaryClip(ClipData.newPlainText("Fire Dojo investigation", text));
            feedback.setText("Copied for an agent. Review before sending; no remote action was triggered.");
        } catch (IllegalArgumentException e) {
            feedback.setText("Choose or type an idea before copying.");
        }
    }

    private void refreshSaved() {
        if (journal == null) return;
        journal.removeAllViews();
        JSONArray records = entries();
        if (records.length() == 0)
            journal.addView(DojoTheme.label(this,
                "Nothing saved yet. Surprising questions belong here.",
                15, DojoTheme.MUTED));
        for (int i = records.length() - 1; i >= Math.max(0, records.length() - 12); i--) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            String question = record.optString("question");
            String source = record.optString("origin");
            String label = ((i + 1) + ". " + question);
            DojoTheme.action(this, journal, label, () -> {
                challengeId = source;
                render(question);
            });
        }
        if (records.length() > 12)
            journal.addView(DojoTheme.label(this,
                records.length() + " saved; recent 12 displayed. Older ideas are retained.",
                14, DojoTheme.MUTED));
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("origin", challengeId);
        if (draft != null) out.putString("draft", draft.getText().toString());
        super.onSaveInstanceState(out);
    }
}
