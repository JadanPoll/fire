package dev.jadan.fire.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicit, user-confirmed updates. Adapted from this repository's CatalogActivity
 * and the Comet TV UpdateChecker pattern. No background polling or silent installs.
 * The pinned GitHub release SHA-256 protects against download corruption, not
 * compromise of the public lab signing key or of the GitHub publisher.
 */
final class BrowserUpdater {
    private static final String RELEASE_API =
        "https://api.github.com/repos/JadanPoll/fire/releases/tags/firetv-dev-bridge-latest";
    private static final String ASSET_URL =
        "https://github.com/JadanPoll/fire/releases/download/firetv-dev-bridge-latest/firetv-browser.apk";
    private static final int MAX_METADATA_BYTES=256*1024;
    private static final long MAX_APK_BYTES=64L*1024*1024;
    private final Activity activity;
    private final TextView status;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean busy;
    private File cachedReady;

    private static final class Release {
        final int version;
        final String sha256;
        Release(int version,String sha256){this.version=version;this.sha256=sha256;}
    }

    BrowserUpdater(Activity activity,TextView status){
        this.activity=activity;
        this.status=status;
    }

    void check(){
        if(busy){status.setText("Browser update check already running.");return;}
        busy=true;
        status.setText("Checking the signed browser release…");
        worker.execute(()->{
            try{
                final Release release=getRelease();
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    busy=false;
                    int installed=currentVersion();
                    if(release.version<=installed){
                        status.setText("Fire Browser is current (installed "+installed+
                            ", published "+release.version+").");
                        return;
                    }
                    status.setText("Browser update available: "+installed+
                        " → "+release.version+". No download started.");
                    new AlertDialog.Builder(activity)
                        .setTitle("Update Fire Browser?")
                        .setMessage("Installed build: "+installed+
                            "\nPublished build: "+release.version+
                            "\n\nVerified GitHub release only. Android will ask before installing. Browser app data and saved cookies should remain intact when the existing app is updated.")
                        .setPositiveButton("DOWNLOAD & INSTALL",(d,w)->download(release))
                        .setNegativeButton("Later",null).show();
                });
            }catch(Exception ex){failure("Update check",ex);}
        });
    }

    private static HttpURLConnection connect(String address) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(35000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent","FireBrowser-Update/1");
        connection.setRequestProperty("Accept","application/vnd.github+json");
        return connection;
    }

    private static byte[] readLimited(InputStream in,int limit) throws Exception {
        try(InputStream source=in;ByteArrayOutputStream result=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;int size=0;
            while((n=source.read(buffer))!=-1){
                size+=n;
                if(size>limit)throw new Exception("Release metadata exceeds size limit");
                result.write(buffer,0,n);
            }
            return result.toByteArray();
        }
    }

    private static Release getRelease() throws Exception {
        HttpURLConnection connection=connect(RELEASE_API);
        try{
            if(connection.getResponseCode()!=200)throw new Exception("GitHub HTTP "+connection.getResponseCode());
            JSONObject release=new JSONObject(new String(
                readLimited(connection.getInputStream(),MAX_METADATA_BYTES),
                StandardCharsets.UTF_8));
            String body=release.optString("body","");
            Matcher version=Pattern.compile("(?m)^Version code: ([0-9]{1,10})\\s*$").matcher(body);
            Matcher sha=Pattern.compile("(?m)^SHA256 firetv-browser\\.apk: ([0-9a-f]{64})\\s*$").matcher(body);
            if(!version.find()||!sha.find())throw new Exception("Release metadata missing version or browser SHA-256");
            int code=Integer.parseInt(version.group(1));
            String expected=sha.group(1);
            boolean assetFound=false;
            JSONArray assets=release.getJSONArray("assets");
            for(int i=0;i<assets.length();i++){
                JSONObject asset=assets.getJSONObject(i);
                if("firetv-browser.apk".equals(asset.optString("name")) &&
                   ASSET_URL.equals(asset.optString("browser_download_url")))assetFound=true;
            }
            if(!assetFound)throw new Exception("Pinned browser APK not present");
            return new Release(code,expected);
        }finally{connection.disconnect();}
    }

    private int currentVersion(){
        try{
            PackageInfo installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
            return Build.VERSION.SDK_INT>=28?
                (int)installed.getLongVersionCode():installed.versionCode;
        }catch(Exception ex){return -1;}
    }

    private static String hashFile(File file) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new java.io.FileInputStream(file)){
            byte[] buffer=new byte[16384];int n;
            while((n=in.read(buffer))!=-1)sha.update(buffer,0,n);
        }
        StringBuilder sb=new StringBuilder();
        for(byte b:sha.digest())sb.append(String.format(Locale.US,"%02x",b&0xff));
        return sb.toString();
    }

    private void download(Release release){
        if(busy)return;
        busy=true;
        status.setText("Checking cached verified APK…");
        worker.execute(()->{
            File partial=null;
            try{
                File directory=new File(activity.getCacheDir(),"updates");
                if(!directory.isDirectory()&&!directory.mkdirs())
                    throw new Exception("Cannot create update cache");
                File apk=new File(directory,"browser-"+release.sha256.substring(0,12)+".apk");
                if(!apk.isFile() || !release.sha256.equals(hashFile(apk))){
                    activity.runOnUiThread(()->{
                        if(!activity.isFinishing()&&!activity.isDestroyed())
                            status.setText("Downloading browser update…");
                    });
                    partial=new File(directory,"browser.part");
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");
                    HttpURLConnection connection=connect(ASSET_URL);
                    try{
                        if(connection.getResponseCode()!=200)
                            throw new Exception("APK download HTTP "+connection.getResponseCode());
                        try(InputStream input=connection.getInputStream();
                            FileOutputStream output=new FileOutputStream(partial)){
                            byte[] buffer=new byte[16384];int count;long total=0;
                            while((count=input.read(buffer))!=-1){
                                total+=count;
                                if(total>MAX_APK_BYTES)throw new Exception("APK exceeded 64 MiB safety limit");
                                output.write(buffer,0,count);
                                digest.update(buffer,0,count);
                            }
                        }
                    }finally{connection.disconnect();}
                    StringBuilder value=new StringBuilder();
                    for(byte b:digest.digest())value.append(String.format(Locale.US,"%02x",b&0xff));
                    if(!release.sha256.equals(value.toString()))
                        throw new Exception("Downloaded APK SHA-256 mismatch");
                    if(apk.exists()&&!apk.delete())throw new Exception("Cannot replace outdated cached APK");
                    if(!partial.renameTo(apk))throw new Exception("Could not finalize verified APK");
                }
                PackageInfo candidate=activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0);
                if(candidate==null || !activity.getPackageName().equals(candidate.packageName))
                    throw new Exception("Downloaded APK belongs to another package");
                long code=Build.VERSION.SDK_INT>=28?candidate.getLongVersionCode():candidate.versionCode;
                if(code!=release.version || code<=currentVersion())
                    throw new Exception("APK version does not match newer published release");
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    busy=false;
                    cachedReady=apk;
                    status.setText("Browser APK verified. Requesting Android installer…");
                    requestInstall();
                });
            }catch(Exception ex){
                if(partial!=null)partial.delete();
                failure("Browser update",ex);
            }
        });
    }

    private void requestInstall(){
        if(cachedReady==null||!cachedReady.isFile()){
            status.setText("No verified browser APK available. Check update again.");
            return;
        }
        try {
            if(Build.VERSION.SDK_INT>=26 &&
               !activity.getPackageManager().canRequestPackageInstalls()){
                status.setText("Fire OS requires permission to install from Fire Browser. The verified APK is cached.");
                new AlertDialog.Builder(activity)
                    .setTitle("Allow browser updates?")
                    .setMessage("Fire OS must authorize this app as an installation source. You can return here and check again; the downloaded APK is cached.")
                    .setPositiveButton("OPEN SETTINGS",(d,w)->{
                        Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+activity.getPackageName()));
                        try{activity.startActivity(settings);}
                        catch(Exception ex){status.setText("Open Fire OS app settings to permit installing from this source.");}
                    })
                    .setNegativeButton("Later",null).show();
                return;
            }
            Uri file=FileProvider.getUriForFile(activity,
                activity.getPackageName()+".fileprovider",cachedReady);
            Intent install=new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(file);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(install);
            status.setText("Android installer opened; confirm the update on Fire TV.");
        }catch(Exception ex){
            status.setText("Could not open installer: "+ex.getMessage()+
                ". Verified APK kept; retry via Check browser update.");
        }
    }

    private void failure(String stage,Exception error){
        android.util.Log.w("FireBrowser",stage+" failed",error);
        activity.runOnUiThread(()->{
            busy=false;
            if(!activity.isFinishing()&&!activity.isDestroyed())
                status.setText(stage+" failed: "+error.getMessage()+". Browser remains usable.");
        });
    }

    void close(){worker.shutdownNow();}
}
