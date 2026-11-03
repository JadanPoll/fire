# Fire Browser input-system audit — 2026-11-03

## Invariants, not isolated features

Fire Browser must maintain the operator's intended input mode even when Fire
TV delivers inconsistent remote events. "Pointer selected" and "software
keyboard visible" are independent: when the keyboard is displayed, it
temporarily owns D-pad and OK. Once dismissed, pointer motion resumes without
a surprise transition into focus/tab mode. The screen shows POINTER ON so mode
is never invisible.

### Grounding in concrete implementations and APIs

| Existing work / authoritative specification | Applied invariant / why necessary |
| --- | --- |
| [TV Bro `DPADNavigationEventsAdapter`](https://github.com/truefedex/tv-bro/blob/master/app/common/src/main/java/com/phlox/tvwebbrowser/utils/DPADNavigationEventsAdapter.kt), particularly its release-on-IME-visible and held-key bookkeeping | A remote OK gesture must generate at most one click, and software keyboard owns its own directional keys. `RemoteInputPolicy` owns OK/media debounce and lost-UP cancellation. |
| [Android keyboard visibility with `WindowInsetsCompat`](https://developer.android.com/develop/ui/views/layout/sw-keyboard) | Query `WindowInsetsCompat.Type.ime()` on Android/Fire OS with `adjustResize`. Do not equate a focused WebView with a visible keyboard. **Legacy Fire OS visibility is approximate**; device confirmation is essential. |
| [Android `MotionEvent`](https://developer.android.com/reference/android/view/MotionEvent) and [`WebView.onGenericMotionEvent`](https://developer.android.com/reference/android/webkit/WebView#onGenericMotionEvent(android.view.MotionEvent)) | Native SOURCE_MOUSE HOVER_ENTER/MOVE/EXIT reaches the engine's hover hit testing for video player overlays; EXIT must precede a touchscreen tap. No injection of site-specific click/hover JavaScript. |
| [Comet TV `CursorController`](https://github.com/SamWylde/comet-tv/blob/main/app/src/main/java/com/tdarby/comet/input/CursorController.kt) | Small initial nudge, bounded frame-timed acceleration, native SOURCE_TOUCHSCREEN DOWN/UP tap, and mouse-wheel edge scrolling. |
| [Android MediaSession callbacks](https://developer.android.com/reference/android/media/session/MediaSession.Callback) and [media-button dispatch](https://developer.android.com/media/legacy/media-buttons) | Foreground-only session and Activity fallback, with the same debounce clock. The browser only receives keys that Fire OS actually routes to it; no global key interception is promised. |
| [Android WebView renderer termination guide](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination) | Remove, destroy and replace the invalid WebView synchronously; preserve recovered URL in RECENT and avoid crash loops. |
| [Android CookieManager.flush](https://developer.android.com/reference/android/webkit/CookieManager#flush()) | Flush eligible cookies off the UI thread; do not misrepresent session-only cookies or auth-policy restrictions as a browser persistence bug. |
| [Existing Fire Dev Bridge CatalogActivity](https://github.com/JadanPoll/fire/blob/main/app/src/main/java/dev/jadan/fire/CatalogActivity.java) and [Comet TV UpdateChecker](https://github.com/SamWylde/comet-tv/blob/main/app/src/main/java/com/tdarby/comet/update/UpdateChecker.kt) | Reuse check + SHA-256 verification + cached APK + explicit system installer; no unattended self-installs. |

## Regressions fixed at the design level

1. **Per-frame relayout:** previous code called `Button.setText` and
   `cursor.setVisibility` on every pointer frame, including when unchanged.
   This was unnecessary expensive UI work. The update only changes each view
   when its actual state changes.
2. **Mode invariant:** URL navigation and text-field focus cannot change the
   user's pointer selection. A visible IME temporarily owns D-pad/OK instead.
3. **OK invariance:** DPAD_CENTER, ENTER, NUMPAD_ENTER and BUTTON_A are one
   logical input; unmatched extra UP and repeat events cannot click; a
   450ms guard absorbs common duplicated Fire TV OK gestures.
4. **Hover and click ordering:** mouse hover is emitted when the cursor moves,
   HOVER_EXIT precedes tap, and exactly one SOURCE_TOUCHSCREEN DOWN/UP
   delivers the click.
5. **Media key invariance:** Play/Pause can arrive as Activity events,
   MediaSession media events, or transport callbacks. A single 450ms clock
   prevents double toggles.
6. **IME submit:** only submit the ChatGPT composer on the release half of a
   keyboard Enter event, never both DOWN and UP.

### Exact event aliases and timing caveat

The platform key-code aliases are documented by [Android TV's controller
guide](https://developer.android.com/training/tv/get-started/controllers) and
[Amazon's controller-input reference](https://developer.amazon.com/docs/fire-tv/game-controller-input.html).
Amazon specifically warns that controller A may produce both BUTTON_A and
DPAD_CENTER, and that unconsumed primary events can cause double inputs. Fire
Browser handles the selection family as a single logical press and returns
`true` when it consumes a key.

The **450ms debounce threshold** is an *initial engineering parameter*, not
a value guaranteed by Android, Amazon, Comet, or TV Bro. To avoid guessing
about a particular Fire remote, MENU → MORE → Browser diagnostics now includes
the last 16 direction/OK/media key transitions and IME visibility transitions,
with key codes and relative timestamps only (no URLs, typed text or cookies).
Use those observations to refine the guard, not to add blind delays.

## Continuous integration vs physical TV acceptance

The pure-Java `RemoteInputPolicyTest` exercises duplicate OK events, key
aliases, keyboard ownership, and shared media debouncing. The workflow builds
and runs these JUnit tests for this pull request, in addition to compiling
the Android APK.

The **physical TV sequence** still matters: navigate to Fire Inbox, post a
test note through the real Fire OS keyboard, leave it open and use CURSOR →
hover over a video → OK click video controls → OK click a text field →
navigate the software keyboard → dismiss keyboard → resume pointer movement
→ navigate Back/Forward → Play/Pause twice → leave and reopen browser to
check login. Capture `tools/firetv_test.py errors` if the renderer exits.

Each step must be visible and independently recoverable. Do not mark an
intermittent crash or cookie-loss report resolved solely because the build
passed. The Fire OS remote/media routing and WebView provider can vary by
device and version.

## Explicitly not added

No global hotkey service, accessibility-service remapping, silent package
installation, arbitrary webpage JavaScript event injection, mandatory
account reauthentication, account-cookie export, or background update daemon.
These would create new failure surfaces without evidence of necessity.
