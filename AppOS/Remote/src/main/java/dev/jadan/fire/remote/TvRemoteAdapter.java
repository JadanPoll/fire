package dev.jadan.fire.remote;

import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Optional adapter for an app's OWN Activity.
 *
 * Usage in dispatchKeyEvent:
 *   if (remote.dispatch(event)) return true;
 *   return super.dispatchKeyEvent(event);
 * Call reset() onPause or before opening a modal.
 *
 * Native applications that already handle their controller should skip this
 * adapter entirely. Back/Home and media keys remain platform/pass-through.
 */
public final class TvRemoteAdapter {
    private final RemotePolicy policy=new RemotePolicy();
    private final RemotePolicy.Sink sink;
    public TvRemoteAdapter(RemotePolicy.Sink listener){
        if(listener==null)throw new IllegalArgumentException("listener");
        sink=listener;
    }
    public boolean dispatch(KeyEvent event){
        if(event==null)return false;
        return policy.consume(event.getKeyCode(),event.getAction(),
                event.getRepeatCount(),SystemClock.uptimeMillis(),sink);
    }
    public void reset(){policy.reset(sink);}
}
