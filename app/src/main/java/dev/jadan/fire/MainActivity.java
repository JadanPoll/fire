package dev.jadan.fire;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final String RELEASE_API =
            "https://api.github.com/repos/JadanPoll/fire/releases/tags/firetv-dev-bridge-latest";
    private static final String ASSET_PREFIX =
            "https://github.com/JadanPoll/fire/releases/download/firetv-dev-bridge-latest/";
    private static final long MAX_APK_BYTES = 64L * 1024L * 1024L;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> keyEvents = new ArrayDeque<>();
    private TextView status, detail, keyLog;
    private Button install;
    private Release release;
    private boolean busy;

    private static final class Release {
        final String url;
        final String sha256;
        final int versionCode;
        Release(String url, String sha256, int versionCode) {
            this.url = url; this.sha256 = sha256; this.versionCode = versionCode;
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(20), dp(26), dp(20));
        root.setBackgroundColor(0xFF102133);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(root);
        setContentView(scroller);

        TextView heading = label("FIRE TV DEV BRIDGE", 28, 0xFF55C4FF);
        root.addView(heading);
        root.addView(label("GitHub → Fire TV. Updates require your approval.", 17, 0xFFCADBE8));
        root.addView(label("Device: " + Build.MODEL + " | Android " + Build.VERSION.RELEASE
                + " | API " + Build.VERSION.SDK_INT
                + " | App " + BuildConfig.VERSION_NAME, 15, 0xFF9FB0C0));

        status = label("Ready. Check for the published build.", 19, 0xFFFFFFFF);
        root.addView(status);
        detail = label("No network requests until you select Check.", 14, 0xFF9FB0C0);
        root.addView(detail);

        Button check = action("CHECK LATEST GITHUB BUILD", root, this::checkRelease);
        install = action("DOWNLOAD & INSTALL (SYSTEM CONFIRMATION)", root, this::downloadRelease);
        install.setEnabled(false);
        action("VOICE PREFLIGHT — CHECK MICROPHONE", root, () ->
                startActivity(new Intent(this, VoiceProbeActivity.class)));
        action("APP CATALOG — INSTALL & LAUNCH", root, () ->
                startActivity(new Intent(this, CatalogActivity.class)));
        action("INPUT LAB — VIRTUAL POINTER", root, () ->
                startActivity(new Intent(this, InputLabActivity.class)));
        action("SHARE LOCAL DIAGNOSTICS", root, this::shareDiagnostics);
        root.addView(label("REMOTE INPUT EVENTS (this app only)", 17, 0xFF55C4FF));
        keyLog = label("Press remote buttons to observe key-down events.", 15, 0xFFFFFFFF);
        root.addView(keyLog);
        root.addView(label("No background polling, no privileged system access, and no automatic telemetry upload.",
                13, 0xFF9FB0C0));
        check.requestFocus();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setPadding(0, dp(9), 0, dp(9));
        return view;
    }

    private Button action(String title, LinearLayout parent, Runnable runnable) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(64));
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundColor(0xFF2D4B65);
        button.setOnFocusChangeListener((v, focused) ->
                button.setBackgroundColor(focused ? 0xFF176FA3 : 0xFF2D4B65));
        button.setOnClickListener(v -> runnable.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(10);
        parent.addView(button, p);
        return button;
    }

    private void setBusy(boolean value) {
        busy = value;
        install.setEnabled(!busy && release != null);
    }

    private void checkRelease() {
        if (busy) return;
        setBusy(true);
        status.setText("Checking GitHub…");
        worker.execute(() -> {
            try {
                Release result = loadRelease();
                runOnUiThread(() -> {
                    release = result;
                    setBusy(false);
                    int local = BuildConfig.VERSION_CODE;
                    status.setText(result.versionCode > local ? "Update available" :
                            result.versionCode == local ? "Already on current build" :
                                    "Installed build is newer than release");
                    detail.setText("Local build " + local + "; published build " + result.versionCode
                            + ". SHA-256 provided and checked before installation.");
                });
            } catch (Exception ex) {
                showError("Release lookup failed", ex);
            }
        });
    }

    private static HttpURLConnection open(String uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(uri).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "FireTVDevBridge/0.1");
        return connection;
    }

    private static Release loadRelease() throws Exception {
        HttpURLConnection c = open(RELEASE_API);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (c.getResponseCode() != 200) throw new Exception("GitHub HTTP " + c.getResponseCode());
            byte[] response = readLimited(c.getInputStream(), 256 * 1024);
            JSONObject data = new JSONObject(new String(response, StandardCharsets.UTF_8));
            String body = data.optString("body", "");
            Matcher checksum = Pattern.compile("(?m)^SHA256: ([0-9a-f]{64})\\s*$").matcher(body);
            Matcher version = Pattern.compile("(?m)^Version code: ([0-9]+)\\s*$").matcher(body);
            if (!checksum.find() || !version.find()) throw new Exception("Release missing checksum or version");
            int versionCode = Integer.parseInt(version.group(1));
            JSONArray assets = data.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (!"firetv-dev-bridge.apk".equals(asset.optString("name"))) continue;
                String url = asset.getString("browser_download_url");
                if (!url.startsWith(ASSET_PREFIX)) throw new Exception("Unexpected release asset URL");
                return new Release(url, checksum.group(1), versionCode);
            }
            throw new Exception("No APK in release");
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readLimited(InputStream in, long limit) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) != -1) {
                if (out.size() + n > limit) throw new Exception("Response too large");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private void downloadRelease() {
        if (busy || release == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status.setText("Permit the bridge to install unknown apps, then select Install again.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
            return;
        }
        final Release expected = release;
        setBusy(true);
        status.setText("Downloading verified APK…");
        worker.execute(() -> {
            File pending = null;
            try {
                File directory = new File(getFilesDir(), "apks");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new Exception("Cannot create update directory");
                pending = new File(directory, "download.part");
                File apk = new File(directory, "bridge.apk");
                HttpURLConnection c = open(expected.url);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long received = 0;
                try {
                    if (c.getResponseCode() != 200) throw new Exception("Download HTTP " + c.getResponseCode());
                    try (InputStream input = c.getInputStream(); FileOutputStream output = new FileOutputStream(pending)) {
                        byte[] buffer = new byte[16 * 1024];
                        int n;
                        while ((n = input.read(buffer)) != -1) {
                            received += n;
                            if (received > MAX_APK_BYTES) throw new Exception("APK exceeds size limit");
                            output.write(buffer, 0, n);
                            digest.update(buffer, 0, n);
                        }
                    }
                } finally {
                    c.disconnect();
                }
                StringBuilder computed = new StringBuilder();
                for (byte b : digest.digest()) computed.append(String.format(Locale.US, "%02x", b & 0xFF));
                if (!expected.sha256.equals(computed.toString())) throw new Exception("SHA-256 mismatch: refused installation");
                if (apk.exists() && !apk.delete()) throw new Exception("Cannot remove old APK");
                if (!pending.renameTo(apk)) throw new Exception("Cannot finalize download");
                runOnUiThread(() -> {
                    setBusy(false);
                    status.setText("Verified. Android installer will request confirmation.");
                    try {
                        Uri file = FileProvider.getUriForFile(this,
                                getPackageName() + ".fileprovider", apk);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(file, "application/vnd.android.package-archive");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } catch (Exception ex) {
                        showError("Could not launch installer", ex);
                    }
                });
            } catch (Exception ex) {
                if (pending != null) pending.delete();
                showError("Download failed", ex);
            }
        });
    }

    private void showError(String operation, Exception ex) {
        runOnUiThread(() -> {
            setBusy(false);
            status.setText(operation);
            detail.setText(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        });
    }

    private void shareDiagnostics() {
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("fire Dev Bridge\n");
        diagnostic.append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        diagnostic.append("Android ").append(Build.VERSION.RELEASE).append(" API ")
                .append(Build.VERSION.SDK_INT).append("\n");
        diagnostic.append("App ").append(BuildConfig.VERSION_NAME).append("\n");
        diagnostic.append("Status: ").append(status.getText()).append("\n");
        diagnostic.append("Input event sample (key codes only):\n");
        for (String event : keyEvents) diagnostic.append(event).append("\n");
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, diagnostic.toString());
        try {
            startActivity(Intent.createChooser(intent, "Share diagnostics"));
        } catch (ActivityNotFoundException ex) {
            status.setText("No share-capable app available. Diagnostics remain on screen.");
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyLog != null) {
            String line = SystemClock.uptimeMillis() + "ms "
                    + KeyEvent.keyCodeToString(event.getKeyCode())
                    + (event.getRepeatCount() == 0 ? "" : " repeat=" + event.getRepeatCount());
            keyEvents.addFirst(line);
            while (keyEvents.size() > 12) keyEvents.removeLast();
            StringBuilder sample = new StringBuilder();
            for (String item : keyEvents) sample.append(item).append("\n");
            keyLog.setText(sample.toString());
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
