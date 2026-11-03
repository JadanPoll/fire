package dev.jadan.fire;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fire Playroom GBA entrance. Integrates an existing, proven emulator rather
 * than inventing a second game engine. No Nintendo ROM/BIOS/game assets included.
 * All emulation and saves belong to the separate Lemuroid Android package.
 *
 * Official upstream release pinned by URL + SHA-256 + package identity.
 * There is NO arbitrary APK installer and NO silent installation.
 */
public final class RetroRoomActivity extends Activity {
    private static final String EMULATOR_PACKAGE="com.swordfish.lemuroid";
    private static final String EMULATOR_VERSION="1.17.0";
    private static final String EMULATOR_URL=
        "https://github.com/Swordfish90/Lemuroid/releases/download/1.17.0/lemuroid-app-free-dynamic-release.apk";
    private static final String EMULATOR_SHA256=
        "d44cfa0cdc3e43fd91df97f625938366ccef9793b06a80b9e6fad97a6cd88e51";
    private static final String DOWNLOAD_NAME="lemuroid-official-1.17.0.apk";
    private static final int MAX_APK_BYTES=24*1024*1024;
    private static final long MAX_ROM_BYTES=32L*1024*1024;
    private static final int PICK_GAME=2041, STORAGE_PERMISSION=2042;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private Future<?> task;
    private TextView status, details;
    private Uri pendingRom;
    private boolean busy;
    private File cachedVerifiedApk;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        TvUi.page(this,root);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(TvUi.header(this,"PLAYROOM  /  GBA GAME ROOM"));
        root.addView(TvUi.text(this,
            "Play full Game Boy Advance software in an existing emulator. No games are bundled; bring your own legal ROM files.",
            16,TvUi.SUB));
        status=TvUi.text(this,"Ready. This setup never modifies Fire Browser or your other games.",
            16,TvUi.INK);
        root.addView(status);

        LinearLayout engine=TvUi.panel(this);
        engine.addView(TvUi.section(this,"1  /  REAL EMULATOR"));
        engine.addView(TvUi.text(this,
            "Lemuroid 1.17.0 · open-source Android TV frontend · mGBA core. The official APK is pinned to a published SHA-256.",
            15,TvUi.SUB));
        TvUi.action(this,engine,"INSTALL VERIFIED LEMUROID (EXPLICIT ANDROID CONFIRMATION)",
            this::installEmulator);
        TvUi.action(this,engine,"RETRY INSTALL OF VERIFIED DOWNLOAD",
            this::retryInstall);
        TvUi.addPanel(this,root,engine);

        LinearLayout games=TvUi.panel(this);
        games.addView(TvUi.section(this,"2  /  YOUR GAME FILE"));
        games.addView(TvUi.text(this,
            "Select your own .gba file. A copy is placed in Downloads/FirePlayroom/GBA for the emulator's TV folder picker. Never uploaded.",
            15,TvUi.SUB));
        TvUi.action(this,games,"IMPORT .GBA FROM DEVICE",this::chooseRom);
        TvUi.action(this,games,"SHOW ROM FOLDER / TRANSFER INSTRUCTIONS",this::showTransferHelp);
        TvUi.addPanel(this,root,games);

        LinearLayout play=TvUi.panel(this);
        play.addView(TvUi.section(this,"3  /  START PLAYING"));
        TvUi.action(this,play,"OPEN LEMUROID TV",this::launchEmulator);
        TvUi.action(this,play,"CANCEL CURRENT TRANSFER",this::cancelTransfer);
        details=TvUi.text(this,
            "First launch: choose Downloads/FirePlayroom/GBA as game directory, then allow the GBA core to download from upstream if requested.",
            15,TvUi.SUB);
        play.addView(details);
        TvUi.addPanel(this,root,play);

        root.addView(TvUi.text(this,
            "CONTROL LIMIT: the standard Fire TV remote has too few buttons for all GBA actions. Lemuroid's current Fire-specific remote fix is not in its released build. A connected gamepad is the dependable baseline until our own frontend is verified. BACK always returns to Console.",
            14,TvUi.SUB));
        TvUi.action(this,root,"BACK TO PLAYROOM",this::finish).requestFocus();
    }

    @Override protected void onResume(){
        super.onResume();
        // Do not block GUI for package checks or trigger a download on resume.
        if(!busy&&status!=null)status.setText(isEmulatorInstalled()?
            "Lemuroid is installed. Import a game or open the TV emulator.":
            "Lemuroid is not installed. Select Install Verified Emulator.");
    }

    private boolean isEmulatorInstalled(){
        try{
            getPackageManager().getPackageInfo(EMULATOR_PACKAGE,0);
            return true;
        }catch(PackageManager.NameNotFoundException ignored){return false;}
    }

    private static String hex(byte[] input){
        StringBuilder out=new StringBuilder(input.length*2);
        for(byte b:input)out.append(String.format(Locale.US,"%02x",b&0xff));
        return out.toString();
    }

    private void setStatus(String text){
        runOnUiThread(()->{
            if(!isFinishing()&&!isDestroyed())status.setText(text);
        });
    }
    private boolean begin(){
        if(busy){status.setText("A transfer is already running. Cancel or press Back; other channels still work.");return false;}
        busy=true;cancelled.set(false);return true;
    }
    private void end(){
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())busy=false;});
    }
    private void cancelTransfer(){
        if(!busy){status.setText("No active transfer.");return;}
        cancelled.set(true);
        Future<?> running=task;
        if(running!=null)running.cancel(true);
        status.setText("Cancel requested. Partial files will be removed; press Back to leave.");
        // Do not clear busy until the worker has actually exited.
    }

    private void installEmulator(){
        if(!begin())return;
        status.setText("Downloading official Lemuroid 1.17.0. Nothing is installed until Android asks you.");
        task=worker.submit(()->{
            File part=null;
            try{
                File folder=new File(getFilesDir(),"apks");
                if(!folder.exists()&&!folder.mkdirs())throw new IOException("APK cache directory unavailable");
                File output=new File(folder,DOWNLOAD_NAME);
                part=new File(folder,DOWNLOAD_NAME+".part");
                if(output.isFile()&&verifyApk(output)){
                    cachedVerifiedApk=output;
                    setStatus("Official emulator APK already verified. Opening Android installer.");
                    promptInstall(output);return;
                }
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                HttpURLConnection c=(HttpURLConnection)new URL(EMULATOR_URL).openConnection();
                try{
                    c.setConnectTimeout(12000);c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent","FireConsole-GBA/1");
                    if(c.getResponseCode()!=200)throw new IOException("Emulator HTTP "+c.getResponseCode());
                    long count=0;
                    try(InputStream input=c.getInputStream();
                        FileOutputStream out=new FileOutputStream(part)){
                        byte[] data=new byte[16384];int n;
                        while((n=input.read(data))!=-1){
                            if(cancelled.get()||Thread.currentThread().isInterrupted())
                                throw new IOException("Transfer cancelled");
                            count+=n;
                            if(count>MAX_APK_BYTES)throw new IOException("APK exceeds 24 MiB limit");
                            digest.update(data,0,n);out.write(data,0,n);
                        }
                        out.getFD().sync();
                    }
                }finally{c.disconnect();}
                if(cancelled.get())throw new IOException("Transfer cancelled");
                if(!EMULATOR_SHA256.equals(hex(digest.digest())))
                    throw new IOException("APK checksum mismatch; not installed");
                if(!verifyPackage(part))
                    throw new IOException("APK identity does not match official emulator");
                if(output.exists()&&!output.delete())throw new IOException("Cannot replace cache file");
                if(!part.renameTo(output))throw new IOException("Cannot finalize cached APK");
                cachedVerifiedApk=output;
                setStatus("Verified Lemuroid SHA-256 and package. Android will ask to install.");
                promptInstall(output);
            }catch(Exception e){
                setStatus("Emulator not installed: "+e.getMessage()+". Try again or download from official GitHub.");
            }finally{
                if(part!=null&&part.exists())part.delete();
                end();
            }
        });
    }
    private boolean verifyPackage(File file){
        PackageInfo info=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);
        return info!=null&&EMULATOR_PACKAGE.equals(info.packageName)&&
            EMULATOR_VERSION.equals(info.versionName);
    }
    private boolean verifyApk(File file)throws Exception{
        if(file.length()<=0||file.length()>MAX_APK_BYTES)return false;
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new java.io.FileInputStream(file)){
            byte[] data=new byte[16384];int n;
            while((n=in.read(data))!=-1){
                if(cancelled.get())throw new IOException("Cancelled");
                hash.update(data,0,n);
            }
        }
        return EMULATOR_SHA256.equals(hex(hash.digest()))&&verifyPackage(file);
    }
    private void promptInstall(File apk){
        runOnUiThread(()->{
            if(isFinishing()||isDestroyed()||cancelled.get())return;
            try{
                Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apk);
                Intent installer=new Intent(Intent.ACTION_INSTALL_PACKAGE);
                installer.setData(uri);
                installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(installer);
                status.setText("Android installer opened. Confirm installation there; Back always cancels.");
            }catch(RuntimeException e){
                status.setText("Android installer unavailable: "+e.getClass().getSimpleName());
            }
        });
    }
    private void retryInstall(){
        File file=cachedVerifiedApk;
        if(file==null||!file.exists()){
            status.setText("No verified APK in this session. Select Install Verified Emulator.");return;
        }
        if(!begin())return;
        status.setText("Re-verifying cached emulator before reopening installer.");
        task=worker.submit(()->{
            try{
                if(verifyApk(file)&&!cancelled.get())promptInstall(file);
                else setStatus("Cached APK no longer matches. Select Install Verified Emulator again.");
            }catch(Exception e){setStatus("Could not verify cached APK: "+e.getMessage());}
            finally{end();}
        });
    }

    private void launchEmulator(){
        Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage(EMULATOR_PACKAGE);
        if(launch==null)launch=getPackageManager().getLaunchIntentForPackage(EMULATOR_PACKAGE);
        if(launch==null){
            status.setText("Lemuroid not installed or no TV launcher. Install it first.");return;
        }
        try{
            startActivity(launch);
            status.setText("Lemuroid opened. Select your local GBA folder and the game.");
        }catch(RuntimeException error){
            status.setText("Could not launch emulator: "+error.getClass().getSimpleName());
        }
    }

    private void chooseRom(){
        if(busy){status.setText("Finish the current transfer or cancel it first.");return;}
        Intent pick=new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("*/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{
            startActivityForResult(pick,PICK_GAME);
            status.setText("Choose a .gba file you supplied. Back cancels without changing saved games.");
        }catch(RuntimeException error){
            status.setText("Fire OS has no accessible file picker. Use the ADB transfer procedure below.");
            showTransferHelp();
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_GAME||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri source=data.getData();
        if(!"content".equals(source.getScheme())){
            status.setText("Choose a file through a content provider, not an untrusted URI.");return;
        }
        String filename=displayName(source);
        if(!filename.toLowerCase(Locale.ROOT).endsWith(".gba")){
            status.setText("This importer accepts .gba ROMs only. No file copied.");return;
        }
        pendingRom=source;
        // Fire OS 7 is Android 9 (API 28); shared Downloads needs WRITE
        // access. This request affects imports only, not emulator launch.
        if(Build.VERSION.SDK_INT<=28 &&
           checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},STORAGE_PERMISSION);
            status.setText("Allow Files/Storage to copy your chosen game to shared Downloads.");
            return;
        }
        importRom(source);
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request!=STORAGE_PERMISSION)return;
        Uri source=pendingRom;pendingRom=null;
        if(source==null)return;
        if(grants.length==0||grants[0]!=PackageManager.PERMISSION_GRANTED){
            status.setText("Storage denied. Nothing copied. ADB transfer to Downloads remains available.");return;
        }
        importRom(source);
    }
    private String displayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst()){
                int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(idx>=0){
                    String value=c.getString(idx);
                    if(value!=null)return value;
                }
            }
        }catch(RuntimeException ignored){}
        return "selected.gba";
    }
    private static String safeName(String proposed){
        // Never interpolate arbitrary provider-controlled paths into a File.
        String name=proposed.replaceAll("[^A-Za-z0-9._-]","_");
        if(name.length()>64)name=name.substring(name.length()-64);
        if(!name.toLowerCase(Locale.ROOT).endsWith(".gba"))return null;
        if(name.startsWith("."))name="game_"+name.substring(1);
        return name;
    }
    private void importRom(Uri source){
        if(!begin())return;
        String name=safeName(displayName(source));
        if(name==null){busy=false;status.setText("Invalid ROM filename. Nothing imported.");return;}
        status.setText("Copying "+name+" to Downloads/FirePlayroom/GBA. This stays on the TV.");
        task=worker.submit(()->{
            File part=null;
            try{
                File download=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File dir=new File(new File(download,"FirePlayroom"),"GBA");
                if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create shared ROM folder");
                File target=new File(dir,name);
                String base=name.substring(0,name.length()-4);
                for(int i=2;target.exists()&&i<=99;i++)target=new File(dir,base+"_"+i+".gba");
                if(target.exists())throw new IOException("Too many same-named games");
                part=new File(dir,target.getName()+".part");
                long bytes=0;
                try(InputStream input=getContentResolver().openInputStream(source);
                    FileOutputStream out=new FileOutputStream(part)){
                    if(input==null)throw new IOException("Cannot open selected ROM");
                    byte[] buffer=new byte[16384];int n;
                    while((n=input.read(buffer))!=-1){
                        if(cancelled.get()||Thread.currentThread().isInterrupted())
                            throw new IOException("Import cancelled");
                        bytes+=n;
                        if(bytes>MAX_ROM_BYTES)throw new IOException("GBA ROM exceeds 32 MiB");
                        out.write(buffer,0,n);
                    }
                    out.getFD().sync();
                }
                if(bytes<192)throw new IOException("File too small for a GBA ROM");
                if(cancelled.get())throw new IOException("Import cancelled");
                if(!part.renameTo(target))throw new IOException("ROM import could not finalize");
                setStatus("IMPORTED "+target.getName()+" ("+bytes+" bytes). In Lemuroid choose Downloads/FirePlayroom/GBA.");
            }catch(Exception e){
                setStatus("ROM not imported: "+e.getMessage()+". Original game file untouched.");
            }finally{
                if(part!=null&&part.exists())part.delete();
                end();
            }
        });
    }
    private void showTransferHelp(){
        new AlertDialog.Builder(this)
            .setTitle("GBA ROM transfer · Fire OS 7")
            .setMessage("Put your own .gba file in:\n\n/sdcard/Download/FirePlayroom/GBA/\n\n"+
                "From a trusted LAN-connected PC with authorized ADB:\n"+
                "adb shell mkdir -p /sdcard/Download/FirePlayroom/GBA\n"+
                "adb push YOUR_GAME.gba /sdcard/Download/FirePlayroom/GBA/\n\n"+
                "Then open Lemuroid, choose this folder as its game library, and scan. "+
                "The first GBA launch may download the open-source mGBA core over HTTPS. "+
                "No games or firmware are included. A real gamepad may be required; Fire remote support is not confirmed.")
            .setPositiveButton("OK",null).show();
    }
    @Override protected void onDestroy(){
        cancelled.set(true);
        Future<?> t=task;if(t!=null)t.cancel(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
