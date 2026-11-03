package dev.jadan.fire;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.TextView;

/** The single, explicit browser handoff used by Console and Playroom. */
final class ConsoleLaunch {
    private static final String BROWSER="dev.jadan.fire.browser";
    private ConsoleLaunch(){}

    static boolean openBrowser(Activity parent,String target,TextView status){
        if(target!=null){
            Uri u;
            try{u=Uri.parse(target);}
            catch(RuntimeException e){return fail(status,"Invalid address");}
            if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null ||
               u.getUserInfo()!=null)
                return fail(status,"Only HTTPS webpages can be opened");
        }
        try{
            Intent browser=parent.getPackageManager().getLaunchIntentForPackage(BROWSER);
            if(browser!=null){
                if(target!=null){
                    browser.setAction(Intent.ACTION_VIEW);
                    browser.setData(Uri.parse(target));
                }
                parent.startActivity(browser);
                return true;
            }
            if(target!=null){
                Intent external=new Intent(Intent.ACTION_VIEW,Uri.parse(target));
                if(external.resolveActivity(parent.getPackageManager())!=null){
                    parent.startActivity(external);
                    if(status!=null)status.setText("Opened using another installed browser. Fire Browser can be installed from Tools.");
                    return true;
                }
            }
            return fail(status,"Fire Browser missing. Open Tools → App Catalog.");
        }catch(RuntimeException e){
            return fail(status,"Unable to launch browser ("+e.getClass().getSimpleName()+").");
        }
    }
    private static boolean fail(TextView view,String message){
        if(view!=null)view.setText(message);return false;
    }
}
