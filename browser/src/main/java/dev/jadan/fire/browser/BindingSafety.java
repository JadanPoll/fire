package dev.jadan.fire.browser;

/**
 * Small key-event state machine independent of Android UI. The emergency
 * sequence is based on physical releases, never OS repeat counts:
 * DOWN, DOWN (two distinct taps) then hold OK for five seconds.
 *
 * The outcome is an immediate *disable*, not deletion of saved profiles.
 */
final class BindingSafety {
    static final long DOUBLE_DOWN_WINDOW_MS=1200;
    static final long RESET_HOLD_MS=5000;
    private long lastDownRelease=-100000;
    private int downCount;
    private boolean downHeld;
    private boolean okHeld, okCandidate, resetFired;
    private long okSince;

    void downPressed(int repeat){
        if(repeat==0)downHeld=true;
    }
    void downReleased(long now){
        if(!downHeld)return; // stray UP, or a duplicate
        downHeld=false;
        if(now-lastDownRelease >= 80 && now-lastDownRelease <= DOUBLE_DOWN_WINDOW_MS)
            downCount=Math.min(2,downCount+1);
        else downCount=1;
        lastDownRelease=now;
    }
    void otherDirection(){
        downHeld=false;
        downCount=0;
    }
    boolean okPressed(int repeat,long now){
        if(repeat!=0 || okHeld)return false;
        okHeld=true;
        okSince=now;
        okCandidate=downCount>=2 && now-lastDownRelease<=DOUBLE_DOWN_WINDOW_MS;
        resetFired=false;
        return okCandidate;
    }
    boolean resetDue(long now){
        if(okCandidate&&okHeld&&!resetFired&&now-okSince>=RESET_HOLD_MS){
            resetFired=true;
            downCount=0;
            return true;
        }
        return false;
    }
    /** Whether this was the key-up for a completed emergency hold. */
    boolean okReleased(long now){
        if(!okHeld)return false;
        resetDue(now);
        boolean fired=resetFired;
        okHeld=false;
        okCandidate=false;
        resetFired=false;
        downCount=0;
        return fired;
    }
    void cancel(){
        downCount=0;
        downHeld=false;
        okHeld=false;
        okCandidate=false;
        resetFired=false;
    }
}
