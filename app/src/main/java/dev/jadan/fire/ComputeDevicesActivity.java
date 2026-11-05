package dev.jadan.fire;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.EditText;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private JSONArray bundled = new JSONArray();
    private EditText endpoint;
    private TextView remoteStatus;
    private boolean checking;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private ScrollView scroll;
    private String selected;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
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
            bundled = devices;
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
        LinearLayout discovery=TvUi.panel(this);
        discovery.addView(TvUi.section(this,"FIND DEVICES / READ-ONLY HTTPS"));
        discovery.addView(text("An external registry can advertise more devices. Its claims are unverified. This TV never sends commands or credentials.",15,TvUi.SUB));
        endpoint=new EditText(this);
        endpoint.setSingleLine(true);
        endpoint.setHint("https://your-registry.example/v1/devices");
        endpoint.setText(getSharedPreferences("compute_device_view_v1",MODE_PRIVATE)
            .getString("registry_url",""));
        TvUi.field(this,endpoint);
        discovery.addView(endpoint);
        remoteStatus=text(devices.length()>bundled.length() ?
            "Remote snapshot loaded for this session. No automatic refresh." :
            "Offline list only. Enter a HTTPS registry URL to inspect its announcements.",14,TvUi.SUB);
        discovery.addView(remoteStatus);
        TvUi.action(this,discovery,"REFRESH DISCOVERY",this::fetchDevices);
        discovery.setVisibility(View.GONE);
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
        TvUi.action(this,root,"ADD OR REFRESH DEVICE REGISTRY",()->{
            discovery.setVisibility(View.VISIBLE);
            endpoint.requestFocus();
        });
        TvUi.addPanel(this,root,discovery);
        if (first != null) first.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private static byte[] download(String endpoint) throws Exception {
        URL url=new URL(endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol()) ||
            url.getUserInfo()!=null || url.getRef()!=null ||
            endpoint.length()>500 || !url.getPath().endsWith("/v1/devices"))
            throw new IllegalArgumentException("Use an HTTPS /v1/devices URL without credentials.");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept","application/json");
        try {
            if (conn.getResponseCode()!=200)
                throw new IllegalStateException("HTTP "+conn.getResponseCode());
            try (InputStream in=conn.getInputStream();
                 ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] chunk=new byte[4096];
                int n;
                while((n=in.read(chunk))!=-1) {
                    if (out.size()+n>131072)
                        throw new IllegalArgumentException("Device list too large");
                    out.write(chunk,0,n);
                }
                return out.toByteArray();
            }
        } finally { conn.disconnect(); }
    }

    private static JSONArray parseAdvertisements(byte[] bytes) throws Exception {
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(root.getInt("schema_version")!=1)
            throw new IllegalArgumentException("Unsupported discovery version");
        JSONArray claims=root.getJSONArray("devices");
        if(claims.length()>60)throw new IllegalArgumentException("Too many devices");
        JSONArray accepted=new JSONArray();
        for(int i=0;i<claims.length();i++) {
            JSONObject d=claims.optJSONObject(i);
            if(d==null)continue;
            String id=d.optString("id");
            String name=d.optString("name");
            if(!id.matches("[a-z][a-z0-9._-]{0,63}") ||
               name.isEmpty() || name.length()>80)continue;
            JSONArray offered=d.optJSONArray("offers");
            if(offered==null || offered.length()>32)continue;
            JSONArray sanitized=new JSONArray();
            for(int j=0;j<offered.length();j++) {
                JSONObject c=offered.optJSONObject(j);
                if(c==null)continue;
                String cap=c.optString("id");
                if(!cap.matches("[a-z][a-z0-9._-]{0,63}"))continue;
                sanitized.put(new JSONObject().put("id",cap)
                    .put("label",c.optString("label",cap).substring(0,
                        Math.min(80,c.optString("label",cap).length())))
                    .put("class","advertised").put("status","unverified"));
            }
            accepted.put(new JSONObject().put("id","remote:"+id)
                .put("name",name).put("kind",d.optString("kind","service"))
                .put("description","Claim from a manually supplied HTTPS registry; no verified live execution.")
                .put("availability","remote_unverified").put("offers",sanitized));
        }
        return accepted;
    }

    private void fetchDevices() {
        if(checking || endpoint==null)return;
        String url=endpoint.getText().toString().trim();
        checking=true;
        remoteStatus.setText("Requesting public metadata; no agent command will execute.");
        network.execute(()->{
            JSONArray discovered=null;
            Exception error=null;
            try {discovered=parseAdvertisements(download(url));}
            catch(Exception e){error=e;}
            final JSONArray records=discovered;
            final Exception failure=error;
            runOnUiThread(()->{
                checking=false;
                if(isFinishing()||isDestroyed())return;
                if(failure!=null) {
                    remoteStatus.setText("Discovery not available: "+
                        failure.getClass().getSimpleName()+". Existing snapshot unchanged.");
                    return;
                }
                getSharedPreferences("compute_device_view_v1",MODE_PRIVATE).edit()
                    .putString("registry_url",url).apply();
                JSONArray next=new JSONArray();
                for(int i=0;i<bundled.length();i++)next.put(bundled.opt(i));
                for(int i=0;i<records.length();i++)next.put(records.opt(i));
                devices=next;
                catalog();
            });
        });
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
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
