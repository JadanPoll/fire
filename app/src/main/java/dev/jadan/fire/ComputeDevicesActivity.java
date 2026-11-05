package dev.jadan.fire;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Device-shaped capability browser, with physical and virtual devices.
 * The authoritative static seed is research/compute/devices.json; this
 * view is read-only. No presence, execution or access claims follow from a
 * listed provider. Separate future transport can consume /v1/devices.
 */
public final class ComputeDevicesActivity extends Activity {
    private JSONArray devices = new JSONArray();
    private LinearLayout root;
    private ScrollView scroll;
    private String selected;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        TvUi.page(this, root);
        scroll.addView(root);
        setContentView(scroll);
        try (InputStream in = getAssets().open("devices.json");
             ByteArrayOutputStream data = new ByteArrayOutputStream()) {
            byte[] part = new byte[4096];
            int n;
            while ((n = in.read(part)) != -1) data.write(part, 0, n);
            devices = new JSONObject(data.toString(StandardCharsets.UTF_8.name()))
                .getJSONArray("devices");
        } catch (Exception ex) {
            root.addView(TvUi.header(this, "COMPUTE / DEVICE INDEX UNAVAILABLE"));
            root.addView(TvUi.text(this,
                "Bundled capability list could not be opened. Existing Codex jobs are unchanged.",
                17, TvUi.SUB));
            return;
        }
        selected = state == null ? null : state.getString("device");
        if (selected != null && find(selected) != null) detail(selected);
        else catalog();
    }

    private JSONObject find(String id) {
        for (int i = 0; i < devices.length(); i++) {
            JSONObject item = devices.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }

    private TextView text(String value, int sp, int color) {
        return TvUi.text(this, value, sp, color);
    }

    private void header(String hint) {
        root.removeAllViews();
        root.addView(TvUi.header(this, "COMPUTE  /  DEVICE FABRIC"));
        root.addView(text(hint, 15, TvUi.SUB));
    }

    private void catalog() {
        selected = null;
        header("Physical devices and virtual capability providers. Listed does not mean connected, trusted or remotely executable.");
        LinearLayout row = null;
        View first = null;
        for (int i = 0; i < devices.length(); i++) {
            JSONObject d = devices.optJSONObject(i);
            if (d == null) continue;
            if ((i % 2) == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = TvUi.dp(this, 12);
                root.addView(row, lp);
            }
            String id = d.optString("id");
            LinearLayout card = TvUi.panel(this);
            card.setPadding(TvUi.dp(this, 14), TvUi.dp(this, 16),
                TvUi.dp(this, 14), TvUi.dp(this, 18));
            card.setFocusable(true);
            card.setClickable(true);
            card.setOnClickListener(v -> detail(id));
            card.setOnFocusChangeListener((v, on) -> {
                card.setBackground(TvUi.rounded(this, on ? 0xFFF9FDFF : TvUi.CARD,
                    on ? TvUi.ACCENT : 0xFFD5E4EE, on ? 3 : 1, 15));
                card.setElevation(TvUi.dp(this, on ? 7 : 1));
            });
            card.addView(new DevicePicture(this, d.optString("kind")),
                new LinearLayout.LayoutParams(-1, TvUi.dp(this, 76)));
            TextView title = text(d.optString("name"), 21, TvUi.INK);
            title.setTypeface(null, 1);
            title.setGravity(Gravity.CENTER);
            card.addView(title);
            TextView subtitle = text(d.optString("kind").replace('_', ' ') +
                "   •   " + d.optJSONArray("offers").length() + " offers", 14, TvUi.SUB);
            subtitle.setGravity(Gravity.CENTER);
            card.addView(subtitle);
            String availability = d.optString("availability", "unassessed");
            TextView state = text(availability.replace('_', ' '), 13, TvUi.SUB);
            state.setGravity(Gravity.CENTER);
            card.addView(state);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
            if ((i % 2) == 1) cp.leftMargin = TvUi.dp(this, 14);
            row.addView(card, cp);
            if (first == null) first = card;
        }
        if (first != null) first.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private void detail(String id) {
        JSONObject d = find(id);
        if (d == null) { catalog(); return; }
        selected = id;
        header("VERSIONED RESOURCE OR EXECUTION PROVIDER / " + id);
        android.widget.Button back = TvUi.action(this, root, "←  ALL DEVICES", this::catalog);
        LinearLayout panel = TvUi.panel(this);
        panel.addView(TvUi.section(this, d.optString("name")));
        panel.addView(text(d.optString("description"), 16, TvUi.INK));
        panel.addView(text("TYPE: " + d.optString("kind") +
            "    PRESENCE: " + d.optString("availability"), 15, TvUi.SUB));
        if ("fire-console".equals(id)) {
            panel.addView(TvUi.section(this, "LOCAL HARDWARE (REPORTED BY ANDROID)"));
            panel.addView(text("Model: " + Build.MANUFACTURER + " / " + Build.MODEL,
                15, TvUi.INK));
            panel.addView(text("Android API " + Build.VERSION.SDK_INT +
                "   •   ABIs: " + String.join(", ", Build.SUPPORTED_ABIS),
                15, TvUi.INK));
        }
        panel.addView(TvUi.section(this, "ENUMERATED CAPABILITIES"));
        JSONArray offers = d.optJSONArray("offers");
        if (offers != null) for (int i = 0; i < offers.length(); i++) {
            JSONObject offer = offers.optJSONObject(i);
            if (offer == null) continue;
            panel.addView(text("●  " + offer.optString("label"), 17, TvUi.INK));
            panel.addView(text("   " + offer.optString("id") +
                "   / " + offer.optString("class") +
                "   / " + offer.optString("status"), 14, TvUi.SUB));
        }
        panel.addView(text("Read-only metadata. Capability existence is not authorization or proof that a provider is currently online.", 14, TvUi.SUB));
        TvUi.addPanel(this, root, panel);
        back.requestFocus();
        scroll.scrollTo(0, 0);
    }

    @Override public void onBackPressed() {
        if (selected != null) catalog();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("device", selected);
        super.onSaveInstanceState(out);
    }

    static final class DevicePicture extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String kind;
        DevicePicture(Activity a, String k) {
            super(a);
            kind = k;
            setFocusable(false);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFF2688B6);
            if ("physical".equals(kind)) {
                c.drawRoundRect(cx - 48, cy - 24, cx + 48, cy + 18, 9, 9, paint);
                c.drawLine(cx, cy + 18, cx, cy + 31, paint);
                c.drawLine(cx - 24, cy + 31, cx + 24, cy + 31, paint);
            } else if ("virtual_platform".equals(kind)) {
                c.drawRoundRect(cx - 53, cy - 30, cx + 53, cy - 8, 6, 6, paint);
                c.drawRoundRect(cx - 53, cy + 1, cx + 53, cy + 23, 6, 6, paint);
                c.drawCircle(cx - 39, cy - 19, 3, paint);
                c.drawCircle(cx - 39, cy + 12, 3, paint);
            } else {
                c.drawCircle(cx, cy, 27, paint);
                c.drawCircle(cx, cy, 9, paint);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2.0;
                    c.drawLine(cx + (float)Math.cos(a) * 28,
                        cy + (float)Math.sin(a) * 28,
                        cx + (float)Math.cos(a) * 39,
                        cy + (float)Math.sin(a) * 39, paint);
                }
            }
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
