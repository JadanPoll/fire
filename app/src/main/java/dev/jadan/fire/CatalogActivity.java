package dev.jadan.fire;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GitHub-backed app catalog. Explicit taps only; no background polling or remote execution. */
public final class CatalogActivity extends Activity {
    private static final String CATALOG =
            "https://raw.githubusercontent.com/JadanPoll/fire/main/catalog.json";
    private static final String RELEASE_API =
            "https://api.github.com/repos/JadanPoll/fire/releases/tags/";
    private static final String RELEASE_ASSET =
            "https://github.com/JadanPoll/fire/releases/download/";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<App> apps = new ArrayList<>();
    private LinearLayout list;
    private TextView status;
    private boolean busy;
    private File verifiedApk;

    private static final class App {
        final String name, description, packageId, tag, asset;
        App(JSONObject o) throws Exception {
            name=o.getString("name");description=o.getString("description");
            packageId=o.getString("package");tag=o.getString("tag");asset=o.getString("asset");
            if(name.length()>64 || description.length()>150 ||
                    !packageId.matches("dev\\.jadan\\.fire(\\.[a-z0-9.]+)?") ||
                    !tag.matches("[A-Za-z0-9._-]{1,64}") ||
                    !asset.matches("firetv-[a-z0-9-]{1,48}\\.apk"))
                throw new Exception("Catalog contains disallowed app values");
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroller=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30,22,30,22);
        root.setBackgroundColor(0xFF102132);
        scroller.addView(root);setContentView(scroller);
        root.addView(label("FIRE  /  APP CATALOG",26,0xFF64C8FF));
        root.addView(label("Install, update or launch approved GitHub-built apps.",16,0xFFFFFFFF));
        status=label("Catalog not yet loaded.",17,0xFFCADBE8);
        root.addView(status);
        addButton(root,"REFRESH CATALOG",this::loadCatalog).requestFocus();
        addButton(root,"RETRY VERIFIED INSTALL (NO DOWNLOAD)",this::retryInstall);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        root.addView(label("No unattended installs. Source: JadanPoll/fire. BACK to return.",14,0xFF95B2C9));
        loadCatalog();
    }

    private TextView label(String text, int sp, int color) {
        TextView t=new TextView(this); t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        t.setPadding(0,12,0,12);return t;
    }
    private Button addButton(LinearLayout parent,String text,Runnable callback) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);
        b.setMinHeight(72);b.setTextColor(0xFFFFFFFF);b.setBackgroundColor(0xFF2D4B65);
        b.setOnFocusChangeListener((v,focus)->b.setBackgroundColor(focus?0xFF176FA3:0xFF2D4B65));
        b.setOnClickListener(v->callback.run());parent.addView(b);return b;
    }
    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(25000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","FireTVDevBridge-Catalog");
        return c;
    }
    private static byte[] readLimited(InputStream in,long limit) throws Exception {
        try(InputStream s=in;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;long size=0;
            while((n=s.read(b))!=-1) {
                size+=n;if(size>limit)throw new Exception("Download exceeds size limit");
                out.write(b,0,n);
            }
            return out.toByteArray();
        }
    }
    private static String loadText(String url) throws Exception {
        HttpURLConnection c=open(url);
        try {
            if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());
            return new String(readLimited(c.getInputStream(),256*1024),StandardCharsets.UTF_8);
        } finally {c.disconnect();}
    }
    private void fail(Exception e) {
        runOnUiThread(()->{busy=false;status.setText(e.getClass().getSimpleName()+": "+e.getMessage());});
    }
    private void loadCatalog() {
        if(busy)return;
        busy=true;status.setText("Loading catalog…");
        worker.execute(()->{
            try {
                JSONObject json=new JSONObject(loadText(CATALOG));
                if(json.getInt("schema")!=1)throw new Exception("Unsupported catalog schema");
                JSONArray items=json.getJSONArray("apps");
                if(items.length()>12)throw new Exception("Too many catalog entries");
                ArrayList<App> found=new ArrayList<>();
                for(int i=0;i<items.length();i++) found.add(new App(items.getJSONObject(i)));
                runOnUiThread(()->{
                    busy=false;apps.clear();apps.addAll(found);
                    list.removeAllViews();
                    for(App app:apps) {
                        list.addView(label(app.name+" — "+app.description,18,0xFFFFFFFF));
                        addButton(list,"INSTALL / UPDATE: "+app.name,()->download(app));
                        addButton(list,"OPEN IF INSTALLED: "+app.name,()->launch(app));
                    }
                    status.setText("Catalog loaded: "+apps.size()+" apps. Select an action.");
                });
            } catch(Exception e){fail(e);}
        });
    }
    private void launch(App app) {
        Intent intent=getPackageManager().getLaunchIntentForPackage(app.packageId);
        if(intent==null){status.setText(app.name+" not installed or launcher unavailable.");return;}
        try{startActivity(intent);status.setText("Opened "+app.name);}
        catch(Exception e){status.setText("Launch failed: "+e.getMessage());}
    }
    private void download(App app) {
        if(busy)return;
        busy=true;status.setText("Checking release for "+app.name+"…");
        worker.execute(()->{
            File partial=null;
            try {
                JSONObject release=new JSONObject(loadText(RELEASE_API+app.tag));
                String body=release.optString("body","");
                Matcher checksum=Pattern.compile("(?m)^SHA256 "+Pattern.quote(app.asset)+": ([0-9a-f]{64})\\s*$").matcher(body);
                if(!checksum.find())throw new Exception("Missing asset checksum");
                String expected=checksum.group(1);
                String url=null;
                JSONArray assets=release.getJSONArray("assets");
                for(int i=0;i<assets.length();i++){
                    JSONObject a=assets.getJSONObject(i);
                    if(app.asset.equals(a.optString("name"))) url=a.getString("browser_download_url");
                }
                if(url==null||!url.startsWith(RELEASE_ASSET+app.tag+"/"+app.asset))
                    throw new Exception("Unexpected APK URL");
                File dir=new File(getFilesDir(),"apks");
                if(!dir.exists()&&!dir.mkdirs())throw new Exception("Cannot create downloads directory");
                partial=new File(dir,app.asset+".part");
                File apk=new File(dir,app.asset);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                HttpURLConnection c=open(url);
                long total=0;
                try{
                    if(c.getResponseCode()!=200)throw new Exception("APK HTTP "+c.getResponseCode());
                    try(InputStream input=c.getInputStream();FileOutputStream output=new FileOutputStream(partial)){
                        byte[] buffer=new byte[16*1024];int n;
                        while((n=input.read(buffer))!=-1){
                            total+=n;if(total>64L*1024*1024)throw new Exception("APK too large");
                            output.write(buffer,0,n);digest.update(buffer,0,n);
                        }
                    }
                } finally{c.disconnect();}
                StringBuilder hash=new StringBuilder();
                for(byte b:digest.digest())hash.append(String.format(Locale.US,"%02x",b&0xff));
                if(!expected.equals(hash.toString()))throw new Exception("Checksum mismatch");
                if(apk.exists()&&!apk.delete())throw new Exception("Cannot replace old APK");
                if(!partial.renameTo(apk))throw new Exception("Cannot finalize APK");
                PackageInfo info=getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0);
                if(info==null||!app.packageId.equals(info.packageName))
                    throw new Exception("APK package does not match catalog");
                runOnUiThread(()->{
                    busy=false;
                    verifiedApk = apk;
                    status.setText("Verified "+app.name+". Android will request installation confirmation.");
                    try {
                        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apk);
                        Intent install=new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        install.setData(uri);
                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(install);
                    }catch(Exception e){status.setText("Installer failed: "+e.getMessage());}
                });
            }catch(Exception e){
                if(partial!=null)partial.delete();fail(e);
            }
        });
    }
    private void retryInstall() {
        if(verifiedApk==null || !verifiedApk.isFile()) {
            status.setText("No verified APK cached in this session. Select Install / Update first.");
            return;
        }
        try {
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",verifiedApk);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            status.setText("Installer reopened. If Fire OS blocks this source, permit Fire TV Dev Bridge once.");
        } catch(Exception e) {status.setText("Installer failed: "+e.getMessage());}
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
