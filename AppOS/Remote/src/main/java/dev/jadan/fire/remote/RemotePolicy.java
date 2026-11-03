package dev.jadan.fire.remote;

import android.view.KeyEvent;
import java.util.EnumMap;

/**
 * Reusable, in-process remote normalization. No global key interception.
 * Non-mapped buttons (Back, Home, volume, media) pass to the platform.
 *
 * The owning application chooses what semantic events mean. This library
 * never synthesizes an event into another Android process.
 */
public final class RemotePolicy {
    public enum Action { UP, DOWN, LEFT, RIGHT, PRIMARY, SECONDARY }
    public enum Phase { DOWN, REPEAT, UP, ACTIVATE }
    public interface Sink { void onAction(Action action, Phase phase); }
    private static final int LONG_MS=400, REPEAT_MS=110, DUPLICATE_MS=190;
    private final EnumMap<Action,Long> pressed=new EnumMap<>(Action.class);
    private final EnumMap<Action,Long> lastRepeat=new EnumMap<>(Action.class);
    private boolean okDown,secondaryDown;
    private long okAt,lastOkAt=Long.MIN_VALUE/2,lastPrimaryAt=Long.MIN_VALUE/2;

    /** @return true if this foreground event was handled by the adapter. */
    public boolean consume(int code,int eventAction,int repeats,long now,Sink sink){
        if(sink==null)throw new IllegalArgumentException("Sink required");
        Action direction=direction(code);
        if(direction!=null){
            if(eventAction==KeyEvent.ACTION_DOWN){
                if(!pressed.containsKey(direction)){
                    pressed.put(direction,now);
                    lastRepeat.put(direction,now);
                    sink.onAction(direction,Phase.DOWN);
                }else if(repeats>0 && now-lastRepeat.get(direction)>=REPEAT_MS){
                    lastRepeat.put(direction,now);
                    sink.onAction(direction,Phase.REPEAT);
                }
                return true;
            }
            if(eventAction==KeyEvent.ACTION_UP){
                if(pressed.remove(direction)!=null){
                    lastRepeat.remove(direction);
                    sink.onAction(direction,Phase.UP);
                }
                return true;
            }
            return false;
        }
        if(!isSelect(code))return false;
        if(eventAction==KeyEvent.ACTION_DOWN){
            if(!okDown){
                okDown=true;
                okAt=now;
                secondaryDown=false;
            }else if(repeats>0&&!secondaryDown&&now-okAt>=LONG_MS){
                secondaryDown=true;
                sink.onAction(Action.SECONDARY,Phase.DOWN);
            }
            return true;
        }
        if(eventAction==KeyEvent.ACTION_UP){
            if(!okDown)return true; // Fire OS sometimes duplicates UP.
            okDown=false;
            if(secondaryDown){
                secondaryDown=false;
                sink.onAction(Action.SECONDARY,Phase.UP);
            }else if(now-okAt>=LONG_MS){
                if(now-lastOkAt>=DUPLICATE_MS){
                    lastOkAt=now;
                    sink.onAction(Action.SECONDARY,Phase.ACTIVATE);
                }
            }else if(now-lastPrimaryAt>=DUPLICATE_MS){
                lastPrimaryAt=now;
                sink.onAction(Action.PRIMARY,Phase.ACTIVATE);
            }
            return true;
        }
        return false;
    }
    private static boolean isSelect(int key){
        return key==KeyEvent.KEYCODE_DPAD_CENTER ||
               key==KeyEvent.KEYCODE_ENTER ||
               key==KeyEvent.KEYCODE_NUMPAD_ENTER;
    }
    private static Action direction(int key){
        switch(key){
            case KeyEvent.KEYCODE_DPAD_UP:return Action.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:return Action.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:return Action.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:return Action.RIGHT;
            default:return null;
        }
    }
    /** Release every held key when focus is lost or a menu opens. */
    public void reset(Sink sink){
        for(Action direction:pressed.keySet())
            sink.onAction(direction,Phase.UP);
        pressed.clear();
        lastRepeat.clear();
        if(secondaryDown)sink.onAction(Action.SECONDARY,Phase.UP);
        secondaryDown=false;
        okDown=false;
    }
}
