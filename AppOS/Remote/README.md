# Fire Remote Adapter — optional native integration

This is the **one shared normalization point** for code we own. It is not a
global remapper, AccessibilityService, injection API, proxy Activity, or
permission gateway. It cannot receive Fire remote events on behalf of another
Android app. That boundary is enforced by Android's window/input routing.

## Use it only when needed

**Independent native/pass-through:** if an agent builds an Android TV APK
with its own control handling (including a game engine), make a normal
`MAIN` + `LEANBACK_LAUNCHER` Activity and install it. Fire Console already
discovers and launches it. It **needs no** `appos.json`, Remote Adapter,
custom rendering API, IPC, or Fire-specific dependency.

**Remote-normalized:** if the engine wants semantic directional and
selection controls, add the optional `:remoteinput` Android library and
bind a tiny callback to its existing input handlers:

```java
private final TvRemoteAdapter remote = new TvRemoteAdapter((action, phase) -> {
    if (action == RemotePolicy.Action.PRIMARY &&
        phase == RemotePolicy.Phase.ACTIVATE) game.pressA();
    // Handle UP/DOWN/LEFT/RIGHT presses and releases in the same callback.
});

@Override public boolean dispatchKeyEvent(KeyEvent event) {
    if (remote.dispatch(event)) return true;
    return super.dispatchKeyEvent(event); // native pass-through
}

@Override protected void onPause() {
    remote.reset();  // release held directions when losing foreground
    super.onPause();
}
```

See the real `Applications/SignalStudio/SignalActivity.java` for a working
integration. The library also has standalone JVM state-machine tests.

## Minimal stable semantics

- UP/DOWN/LEFT/RIGHT: DOWN once; bounded REPEAT when Android reports
  repeats; UP once. Duplicate physical DOWN/UP events do not create new
  actions.
- PRIMARY: a short OK/Enter gesture, emitted as ACTIVATE on release.
- SECONDARY: an **observed** long OK gesture; DOWN/UP if repeated key
  events confirm hold, otherwise ACTIVATE on a long release. A missing
  release does **not** generate a phantom long press.
- Back, Home, Menu, volume, voice and media: **not captured**. The app
  can handle its own Back/Menu with normal Activity APIs; Home belongs
  to Fire OS. Optional MediaSession for real media controls should be
  configured by the actual media owner.
- `reset()` emits UP for outstanding controls and returns the adapter to
  a neutral state.
- The adapter does not synthesize Android key events or control keyboard
  focus. Forward semantics directly to an engine's own input API.

Raw key signals are often already correct. Prefer pass-through first; add
the adapter only where a real remote timing or input-mapping problem is
observed. This is a **convenience layer**, not a mandatory SDK.

## Boundary and non-goals

An external app's own Activity must opt into the library. Console cannot
globally rewrite its controls without OS privilege or an appropriately
authorized system input method/service. There is intentionally no
Cross-App InputService or Binder broker for cheap KeyEvents.

The higher-value future interface is a tiny optional
`capabilities/controls` descriptor for discovery, not a new compulsory
manifest schema. A third-party APK stays first-class even without one.

Authoritative references:
- https://developer.android.com/training/tv/get-started/controllers
- https://developer.android.com/games/sdk/game-controller/controller-input
- https://developer.amazon.com/docs/fire-tv/mediasession-api-integration.html
