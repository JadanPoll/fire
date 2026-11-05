package dev.jadan.fire;

import android.app.Activity;
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
 * TV-native, ungraded, problem-first Android Dojo.
 * The authoritative catalog is research/android_dojo/catalog.json, mounted
 * into Android assets by Gradle. Notes are app-private, never uploaded.
 */
public final class AndroidDojoActivity extends Activity {
    private SharedPreferences progress;
    private JSONArray topics = new JSONArray(), challenges = new JSONArray(), stackLevels = new JSONArray();
    private LinearLayout root;
    private ScrollView scroll;
    private String currentId;
    private boolean showingStack;
    private boolean challengeFromStack;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        progress = getSharedPreferences("android_dojo_progress_v1", MODE_PRIVATE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        DojoTheme.page(this, root);
        scroll.addView(root);
        setContentView(scroll);
        try (InputStream in = getAssets().open("catalog.json");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] block = new byte[4096];
            int count;
            while ((count = in.read(block)) != -1) bytes.write(block, 0, count);
            JSONObject catalog = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            topics = catalog.getJSONArray("topics");
            challenges = catalog.getJSONArray("challenges");
            stackLevels = catalog.optJSONArray("stack_levels");
            if (stackLevels == null) stackLevels = new JSONArray();
            if (challenges.length() == 0) throw new IllegalStateException("No challenges");
        } catch (Exception failure) {
            root.addView(DojoTheme.headline(this, "ANDROID DOJO / CATALOG UNAVAILABLE"));
            root.addView(DojoTheme.label(this,
                "The bundled challenge catalog could not be read. Your saved notes are untouched. " +
                failure.getClass().getSimpleName(), 16, DojoTheme.MUTED));
            return;
        }
        currentId = state == null ? null : state.getString("challenge");
        challengeFromStack = state != null && state.getBoolean("challenge_from_stack");
        if (find(currentId) != null) showChallenge(currentId);
        else if (state != null && state.getBoolean("stack_map")) showStackMap();
        else showCatalog();
    }

    private JSONObject find(String id) {
        if (id == null) return null;
        for (int i = 0; i < challenges.length(); i++) {
            JSONObject c = challenges.optJSONObject(i);
            if (c != null && id.equals(c.optString("id"))) return c;
        }
        return null;
    }

    private void header(String subtitle) {
        root.removeAllViews();
        root.addView(DojoTheme.label(this, "道場   /   FIRE LAB", 17, DojoTheme.GOLD));
        root.addView(DojoTheme.headline(this, "ANDROID DOJO"));
        root.addView(DojoTheme.label(this, subtitle, 15, DojoTheme.MUTED));
    }

    private void showCatalog() {
        currentId = null;
        showingStack = false;
        challengeFromStack = false;
        header("Unranked engineering quests  •  questions first, topics as a map, experiments at your own pace.");
        DojoTheme.action(this, root, "POSSIBILITY GARDEN  /  WHAT ELSE COULD WE DO?", () ->
            startActivity(new Intent(this, IdeaGardenActivity.class)));
        Button stackButton = DojoTheme.action(this, root, "EXPLORE THE STACK  /  SCRIPT → KERNEL", this::showStackMap);
        Button first = null;
        for (int t = 0; t < topics.length(); t++) {
            JSONObject topic = topics.optJSONObject(t);
            if (topic == null) continue;
            String topicId = topic.optString("id");
            LinearLayout card = DojoTheme.card(this);
            card.addView(DojoTheme.label(this, topic.optString("label").toUpperCase(),
                17, DojoTheme.GOLD));
            boolean included = false;
            for (int i = 0; i < challenges.length(); i++) {
                JSONObject challenge = challenges.optJSONObject(i);
                if (challenge == null) continue;
                JSONArray assigned = challenge.optJSONArray("topics");
                if (!contains(assigned, topicId)) continue;
                included = true;
                String id = challenge.optString("id");
                String state = progress.getString("state_" + id, "not started");
                Button b = DojoTheme.action(this, card,
                    challenge.optString("title") + "  [" + state + "]", () -> showChallenge(id));
                if (first == null) first = b;
            }
            if (included) DojoTheme.addCard(this, root, card);
        }
        stackButton.requestFocus();
        scroll.scrollTo(0, 0);
    }

    /** A second navigational axis, not a difficulty ladder or prerequisite gate. */
    private void showStackMap() {
        currentId = null;
        showingStack = true;
        header("Explore from a five-line script to the Android kernel boundary. Lower-level does not mean deeper understanding.");
        Button back = DojoTheme.action(this, root, "←  CHALLENGE TOPICS", this::showCatalog);
        for (int i = 0; i < stackLevels.length(); i++) {
            JSONObject level = stackLevels.optJSONObject(i);
            if (level == null) continue;
            LinearLayout card = DojoTheme.card(this);
            card.addView(DojoTheme.label(this, level.optString("label"), 19, DojoTheme.GOLD));
            card.addView(DojoTheme.label(this, "ACCESS: " + level.optString("access"), 14, DojoTheme.RED));
            card.addView(DojoTheme.label(this, level.optString("description"), 16, DojoTheme.INK));
            card.addView(DojoTheme.label(this, "BOUNDARY: " + level.optString("boundary"), 14, DojoTheme.MUTED));
            String levelId = level.optString("id");
            for (int j = 0; j < challenges.length(); j++) {
                JSONObject c = challenges.optJSONObject(j);
                if (c == null || !contains(c.optJSONArray("stack"), levelId)) continue;
                String cid = c.optString("id");
                DojoTheme.action(this, card, c.optString("title") + "  /  OPEN QUESTION", () -> showChallenge(cid));
            }
            DojoTheme.addCard(this, root, card);
        }
        back.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private boolean contains(JSONArray values, String value) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++)
            if (value.equals(values.optString(i))) return true;
        return false;
    }

    private void arraySection(LinearLayout card, String title, JSONArray items) {
        card.addView(DojoTheme.label(this, title, 17, DojoTheme.GOLD));
        if (items == null || items.length() == 0) {
            card.addView(DojoTheme.label(this, "No entries yet.", 15, DojoTheme.MUTED));
            return;
        }
        for (int i = 0; i < items.length(); i++)
            card.addView(DojoTheme.label(this, "•  " + items.optString(i), 15, DojoTheme.INK));
    }

    private JSONArray savedNotes(String id) {
        try { return new JSONArray(progress.getString("notes_" + id, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private void showChallenge(String id) {
        JSONObject c = find(id);
        if (c == null) { showCatalog(); return; }
        challengeFromStack = showingStack || (currentId != null && challengeFromStack);
        currentId = id;
        showingStack = false;
        header("探究 / QUESTION   •   Progress is never graded.");
        Button back = DojoTheme.action(this, root, "←  ALL CHALLENGES", this::showCatalog);
        LinearLayout challenge = DojoTheme.card(this);
        challenge.addView(DojoTheme.headline(this, c.optString("title")));
        challenge.addView(DojoTheme.label(this, c.optString("problem"), 16, DojoTheme.INK));
        DojoTheme.action(this, challenge, "GROW IDEAS FROM THIS CHALLENGE", () -> {
            Intent i = new Intent(this, IdeaGardenActivity.class);
            i.putExtra("challenge_id", id);
            startActivity(i);
        });
        arraySection(challenge, "POSSIBILITIES / QUESTIONS TO ASK", c.optJSONArray("possibility_seeds"));
        arraySection(challenge, "STACK LENSES / PLACES TO INVESTIGATE", c.optJSONArray("stack"));
        arraySection(challenge, "GLOSSARY / IDEAS TO EXPLORE", c.optJSONArray("glossary"));
        arraySection(challenge, "TACIT SKILLS / THINGS TO TRY", c.optJSONArray("tacit_checks"));
        arraySection(challenge, "PARTIAL PROGRESS COUNTS", c.optJSONArray("partial_progress_examples"));
        challenge.addView(DojoTheme.label(this,
            "Starting point: " + c.optString("local_start"), 15, DojoTheme.MUTED));
        if ("remote-double-action".equals(id)) {
            DojoTheme.action(this, challenge, "OPEN LIVE INPUT LAB",
                () -> startActivity(new Intent(this, InputLabActivity.class)));
        } else if ("external-sprite".equals(id)) {
            DojoTheme.action(this, challenge, "OPEN 2D PLAYROOM PROBES",
                () -> startActivity(new Intent(this, MiniGameLabActivity.class)));
        }
        DojoTheme.addCard(this, root, challenge);

        LinearLayout work = DojoTheme.card(this);
        TextView state = DojoTheme.label(this,
            "STATE  /  " + progress.getString("state_" + id, "not started"),
            16, DojoTheme.RED);
        work.addView(state);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        String[] states = {"exploring", "paused", "revisiting"};
        for (String s : states) {
            Button b = DojoTheme.button(this, s.toUpperCase(), () -> {
                progress.edit().putString("state_" + id, s).apply();
                state.setText("STATE  /  " + s);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = TvUi.dp(this, 7);
            actions.addView(b, lp);
        }
        work.addView(actions);
        work.addView(DojoTheme.label(this,
            "EXPERIMENT JOURNAL  /  SAVED ONLY ON THIS FIRE TV", 16, DojoTheme.GOLD));
        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        work.addView(history);
        displayNotes(history, id);
        EditText entry = new EditText(this);
        entry.setSingleLine(false);
        entry.setMinLines(2);
        entry.setMaxLines(5);
        entry.setHint("What did you try, observe, or wonder?");
        DojoTheme.input(this, entry);
        work.addView(entry);
        TextView feedback = DojoTheme.label(this, "No grading. Save any partial result.", 14,
            DojoTheme.MUTED);
        work.addView(feedback);
        DojoTheme.action(this, work, "SAVE EXPERIMENT NOTE", () -> {
            String text = entry.getText().toString().trim();
            if (text.isEmpty() || text.length() > 2500) {
                feedback.setText("Enter a note between 1 and 2500 characters.");
                return;
            }
            JSONArray notes = savedNotes(id);
            if (notes.length() >= 100) {
                feedback.setText("Journal has 100 entries. Existing work preserved; export before adding more.");
                return;
            }
            try {
                notes.put(new JSONObject().put("time", System.currentTimeMillis())
                    .put("text", text));
                if (!progress.edit().putString("notes_" + id, notes.toString()).commit()) {
                    feedback.setText("Storage failed. Note remains in the field.");
                    return;
                }
                entry.setText("");
                feedback.setText("Saved locally. Reopen this challenge to resume.");
                displayNotes(history, id);
            } catch (Exception failure) {
                feedback.setText("Could not save note. Text remains available.");
            }
        });
        DojoTheme.addCard(this, root, work);
        back.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private void displayNotes(LinearLayout history, String id) {
        history.removeAllViews();
        JSONArray notes = savedNotes(id);
        if (notes.length() == 0) {
            history.addView(DojoTheme.label(this, "No observations recorded yet.",
                15, DojoTheme.MUTED));
            return;
        }
        int start = Math.max(0, notes.length() - 5);
        for (int i = start; i < notes.length(); i++) {
            JSONObject note = notes.optJSONObject(i);
            if (note == null) continue;
            history.addView(DojoTheme.label(this,
                (i + 1) + ". " + note.optString("text"), 15, DojoTheme.INK));
        }
        history.addView(DojoTheme.label(this,
            notes.length() + " saved observations (most recent five displayed).",
            13, DojoTheme.MUTED));
    }

    @Override public void onBackPressed() {
        if (currentId != null) {
            if (challengeFromStack) showStackMap();
            else showCatalog();
        } else if (showingStack) showCatalog();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("challenge", currentId);
        out.putBoolean("stack_map", showingStack);
        out.putBoolean("challenge_from_stack", challengeFromStack);
        super.onSaveInstanceState(out);
    }
}
