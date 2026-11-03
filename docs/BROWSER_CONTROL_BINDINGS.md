# Fire Browser — native control binding profiles

## A user-first control surface

The goal is to adapt remote input to the current game, not force every game
or website to know about Amazon's remote. The user points at a visible target,
presses OK, presses the remote button they want to bind, and gets an explicit
status confirmation. Controls are grouped into named, per-host profiles that
can be swapped through a 3×3 D-pad-native wheel.

This implementation reuses BrowserActivity's existing native pointer and
WebView touch pipeline. A mapping is one of:

- native SOURCE_TOUCHSCREEN **tap at a normalized webpage coordinate**;
- native tap on a Fire Browser-owned control;
- **virtual key event** from the browser-owned GAME KEYS palette (arrows,
  Space, Enter, A, B, W, S, D, Escape).

The GAME KEYS overlay is inside Fire Browser. Clicking one sends an Android
KeyEvent to the current WebView, and binding one stores its key identity;
bindings still work when the overlay is hidden. HTML games may or may not
handle these events, so actual game input must be checked on device. There is
no device-wide key injection and no JavaScript injection into arbitrary pages.

Amazon's own system IME belongs to a separate window/process. Fire Browser
**cannot map a pointer click to an Amazon on-screen keyboard key** using its
ordinary WebView/Activity permissions. The app-owned game-key palette is the
supported alternative. The system keyboard retains the D-pad/OK while visible.

## Operator path

1. Open a game in Fire Browser. Press **SETS** in the browser toolbar.
2. Choose **NEW LABELLED SET** and give it a short name (e.g. `Runner A`).
3. Binding cursor becomes **green**. If binding game keys, toggle **KEYS** in
   the toolbar first. Aim at a webpage action or a GAME KEYS tile with D-pad.
4. Press **OK** to select the target, then press the remote key that should
   activate it. A replacement mapping requires explicit confirmation.
5. Repeat and press MENU to open the 3×3 CONTROL WHEEL. Select the labelled
   set. The centre tile is **NORMAL / OFF**.
6. When a mapping is active, **MENU** directly opens the wheel for a quick
   swap. BACK disables mapping and restores normal D-pad navigation.
7. A Play/Pause **hold** can enter binding capture when the Android key stream
   provides repeated down or a corresponding up event showing >=750 ms.
   Some Fire OS media routes provide only a transport callback or DOWN with
   no UP; in that case, **SETS** and **MENU** are the reliable alternatives.

The wheel's centre always turns off custom mappings, and profiles are OFF
again on browser process restart. Their saved data survives normal updates.

## Safety invariants

- **Emergency chord**: two distinct D-pad DOWN taps then hold OK for **5 s**
  disables bindings and the browser's remote media shortcut setting. Never
  deletes saved profiles. An armed hold displays a status message. The final
  OK release is swallowed so it cannot click through after the reset.
- **Immediate escapes**: BACK disables active controls; MENU opens the wheel
  while a mapping is active; SETS is also a visible toolbar button.
- **Unmappable**: OK/Enter, Back, Menu, Home, Play/Pause, power, volume and
  microphone/system keys cannot be assigned as physical input sources.
- **Keyboard boundary**: visible Fire OS keyboard owns D-pad/OK; pending
  key presses are cleared when keyboard becomes visible, page focus disappears,
  or Activity is paused.
- **No cross-site auto-run**: active/capture input is suspended on page host
  change; profiles are host-scoped and remain OFF on app launch.
- **No surprise repetitions**: mapped keys trigger once per armed DOWN/UP,
  with 240 ms suppression of duplicate releases and no OS repeat actions.
- **Bounded storage**: 8 profiles × 12 bindings, short labels and validated
  data; malformed stored JSON is ignored rather than blocking app startup.
- **Non-disruptive updates**: existing WebView data, bookmarks, login cookies,
  Playroom pins and APK package identity are unchanged.

**Limitations**: Normalized target coordinates can become stale when a page
changes layout, scales or scrolls. Users should prefer a game-key target
where the page supports keyboard input. Physical remote key events are not
guaranteed across Amazon models; system-reserved keys remain inaccessible.
The five-second emergency gesture depends on the Activity receiving those
key events, so BACK, MENU, and restarting the Activity are independent
recovery paths.

## Validation

CI: `:browser:testReleaseUnitTest` and `:browser:assembleRelease`.
`BindingSafetyTest` covers realistic duplicated DOWN/UP, repeated events,
canceled gestures, and exact reset timing. `BrowserBindingsTest` guards
reserved keys and virtual key target validation.

On Fire TV:

1. Browse a regular website. Open SETS, create a labelled set, bind DPAD_UP
   to GAME KEYS / Space. Activate set; check the page receives one key.
2. While active press MENU; confirm CONTROL WHEEL appears and centre
   selects NORMAL/OFF. Press BACK after reactivation: D-pad must again
   navigate normally.
3. Test two distinct DOWN taps + a short OK: no reset. Then two DOWN taps +
   OK held five seconds: mapping turns OFF, saved set remains.
4. Test binding cursor over a webpage and over GAME KEYS overlay. A capture
   must never click the underlying page or video.
5. Open Fire keyboard in a text input; verify mapping does not steal keys.
   Dismiss keyboard and return to the browser; do not assume the app can
   click the IME's separate window.
6. Restart browser, visit unrelated host, and reopen the same game: mapping
   is OFF after restart and cannot act on unrelated domains.
7. Verify video playback Play/Pause behavior and long-press delivery on
   the actual remote. The SETS path must remain usable even without media
   key-up events.

This is a **functional baseline**, not a complete gamepad emulator: analog
axes, held-key streams, DOM selectors, keyboard composition and system-wide
remapping are out of scope until tested and justified.

## Platform basis

- [Android KeyEvent](https://developer.android.com/reference/android/view/KeyEvent)
- [Android MediaSession](https://developer.android.com/reference/android/media/session/MediaSession)
- [Android TV controllers](https://developer.android.com/training/tv/get-started/controllers)
- [Amazon Fire TV controller input](https://developer.amazon.com/docs/fire-tv/game-controller-input.html)
- [TV Bro remote adapter](https://github.com/truefedex/tv-bro)
- Existing `docs/INPUT_SYSTEM_AUDIT.md` and `RemoteInputPolicy`

The goal is to preserve stable expectations under the real remote's unusual
key-up, repeat, focus and keyboard behavior, not to impose mathematically
strict workflows that trap the operator.
