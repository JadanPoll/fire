package dev.jadan.fire.browser;

/**
 * App-local normalization of Fire TV remote press/release gestures.
 *
 * Proven primitives: Android KeyEvent ACTION_DOWN/ACTION_UP and repeatCount;
 * TV Bro's DPADNavigationEventsAdapter maintains held-key state and releases
 * ownership when the software keyboard is visible:
 * https://github.com/truefedex/tv-bro/blob/master/app/common/src/main/java/com/phlox/tvwebbrowser/utils/DPADNavigationEventsAdapter.kt
 *
 * This pure-Java class deliberately knows nothing about WebView, MediaSession,
 * or UI lifecycle; those are handled by the Activity at the proper boundary.
 */
final class RemoteInputPolicy {
    static final long OK_DEBOUNCE_MS = 450L;
    static final long MEDIA_DEBOUNCE_MS = 450L;

    private boolean okArmed;
    private long lastOkClick = -OK_DEBOUNCE_MS;
    private long lastMediaToggle = -MEDIA_DEBOUNCE_MS;

    /**
     * Key identities differ among Fire/Android TV remotes: DPAD_CENTER,
     * ENTER, NUMPAD_ENTER and BUTTON_A can all represent OK. Treat these
     * aliases as one armed press, not separate clicks.
     */
    void okDown(int repeatCount) {
        if (repeatCount == 0) okArmed = true;
    }

    /** Exactly one click on a matching armed release, suppressing bounce. */
    boolean okUp(long elapsedRealtimeMs) {
        if (!okArmed) return false;
        okArmed = false;
        if (elapsedRealtimeMs - lastOkClick < OK_DEBOUNCE_MS) return false;
        lastOkClick = elapsedRealtimeMs;
        return true;
    }

    /** Prevent a delayed key-up from clicking after focus/keyboard changes. */
    void cancelOk() {
        okArmed = false;
    }

    /**
     * All media event entrypoints (Activity dispatch, MediaSession key, or
     * transport callback) share one debounce clock. Repeats never toggle.
     */
    boolean mediaDown(int repeatCount, long elapsedRealtimeMs) {
        if (repeatCount != 0) return false;
        if (elapsedRealtimeMs - lastMediaToggle < MEDIA_DEBOUNCE_MS) return false;
        lastMediaToggle = elapsedRealtimeMs;
        return true;
    }

    /** Use Android's authoritative IME visibility rather than WebView focus. */
    static boolean pointerOwnsDpad(boolean pointerSelected, boolean imeVisible) {
        return pointerSelected && !imeVisible;
    }
}
