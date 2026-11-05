package dev.jadan.fire;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * First tiny data-only versioned asset channel. A Snake style is plain JSON,
 * never executable code. Verify SHA256 before promotion, preserve one prior
 * known-good version, never require the network to start playing.
 *
 * Source repository/path can later move to its own asset project. Only explicit
 * button presses request an update; this class has no background service.
 */
final class SnakeStyleStore {
    static final String BASE =
        "https://raw.githubusercontent.com/JadanPoll/fire/main/research/playroom/packs/snake/";
    private static final String ID = "fire.snake.theme";
    private final SharedPreferences prefs;

    static final class Style {
        final String version;
        final int board, cellA, cellB, food, head, body;
        Style(String ver, int b, int a, int alt, int f, int h, int tail) {
            version=ver; board=b; cellA=a; cellB=alt; food=f; head=h; body=tail;
        }
    }

    static final class Candidate {
        final Style style;
        final String json, sha;
        Candidate(Style s, String j, String h) { style=s; json=j; sha=h; }
    }

    SnakeStyleStore(Context context) {
        prefs=context.getSharedPreferences("fire_snake_style_v1",Context.MODE_PRIVATE);
    }

    static Style fallback() {
        return new Style("built-in", 0xFF162C3E,0xFF223F4F,0xFF274657,
            0xFFFFB464,0xFF7AE7C8,0xFF58B3D2);
    }

    static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(64);
        for (byte b : digest) value.append(String.format(Locale.US,"%02x",b & 255));
        return value.toString();
    }

    static int color(String value) {
        if (value == null || !value.matches("#[0-9A-Fa-f]{6}"))
            throw new IllegalArgumentException("Asset color must be #RRGGBB");
        return Color.parseColor(value);
    }

    static Style parse(String json) throws Exception {
        JSONObject d=new JSONObject(json);
        if (d.getInt("schema_version")!=1 || !ID.equals(d.getString("pack_id")))
            throw new IllegalArgumentException("Unsupported Snake style pack");
        String version=d.getString("version");
        if (version.length()<1 || version.length()>48 ||
            !version.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Invalid style version");
        JSONObject c=d.getJSONObject("colors");
        return new Style(version, color(c.getString("board")),
            color(c.getString("cell_a")), color(c.getString("cell_b")),
            color(c.getString("food")), color(c.getString("head")),
            color(c.getString("body")));
    }

    private Candidate cached(String slot) {
        try {
            String raw=prefs.getString(slot+"_json","");
            String digest=prefs.getString(slot+"_sha","");
            if (raw.isEmpty() || !digest.matches("[0-9a-f]{64}")) return null;
            if (!sha256(raw.getBytes(StandardCharsets.UTF_8)).equals(digest)) return null;
            return new Candidate(parse(raw),raw,digest);
        } catch (Exception e) { return null; }
    }

    Style current() {
        Candidate c=cached("active");
        return c==null?fallback():c.style;
    }

    boolean canRollback() { return cached("previous")!=null; }

    /** Apply only a previously verified candidate; cache update is atomic. */
    boolean promote(Candidate next) {
        if (next==null) return false;
        Candidate current=cached("active");
        if (current!=null && current.sha.equals(next.sha)) return true;
        SharedPreferences.Editor edit=prefs.edit();
        if (current!=null) {
            edit.putString("previous_json",current.json);
            edit.putString("previous_sha",current.sha);
        }
        edit.putString("active_json",next.json);
        edit.putString("active_sha",next.sha);
        return edit.commit();
    }

    boolean rollback() {
        Candidate old=cached("previous");
        if (old==null) return false;
        Candidate active=cached("active");
        SharedPreferences.Editor edit=prefs.edit()
            .putString("active_json",old.json).putString("active_sha",old.sha);
        if (active!=null)
            edit.putString("previous_json",active.json)
                .putString("previous_sha",active.sha);
        else
            edit.remove("previous_json").remove("previous_sha");
        return edit.commit();
    }

    private static byte[] fetch(String url) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept","application/json");
        try {
            if (conn.getResponseCode()!=200)
                throw new IllegalStateException("HTTP "+conn.getResponseCode());
            try (InputStream input=conn.getInputStream();
                 ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] b=new byte[1024];
                int n;
                while ((n=input.read(b))!=-1) {
                    if (bytes.size()+n>8192)
                        throw new IllegalArgumentException("Style file too large");
                    bytes.write(b,0,n);
                }
                return bytes.toByteArray();
            }
        } finally { conn.disconnect(); }
    }

    /** Fetch and verify off the UI thread. This does not modify the active cache. */
    Candidate downloadLatest() throws Exception {
        String manifestRaw=new String(fetch(BASE+"manifest.json"),StandardCharsets.UTF_8);
        JSONObject manifest=new JSONObject(manifestRaw);
        if (manifest.getInt("schema_version")!=1 ||
            !ID.equals(manifest.getString("pack_id")))
            throw new IllegalArgumentException("Unrecognized style manifest");
        String file=manifest.getString("file");
        if (!file.matches("style-[A-Za-z0-9_-]{1,32}\\.json"))
            throw new IllegalArgumentException("Unsafe style filename");
        String expected=manifest.getString("sha256");
        if (!expected.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid content hash");
        byte[] bytes=fetch(BASE+file);
        String actual=sha256(bytes);
        if (!expected.equals(actual))
            throw new IllegalArgumentException("Content hash mismatch");
        String raw=new String(bytes,StandardCharsets.UTF_8);
        Style parsed=parse(raw);
        if (!parsed.version.equals(manifest.getString("version")))
            throw new IllegalArgumentException("Version disagreement");
        return new Candidate(parsed,raw,actual);
    }
}
