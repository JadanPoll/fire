# Fire Console — source-backed architecture and voice recovery

## Intent and boundary

The newly labelled `Fire Console` is **a native TV-oriented home inside the
existing Dev Bridge app** (`dev.jadan.fire`). It is not a Fire OS replacement,
not the Android HOME intent handler, and does not intercept the system HOME,
Alexa or volume buttons. Its icon channels act as stable entrypoints to
existing independent apps:

| Channel | Actual destination | Preserved existing work |
| --- | --- | --- |
| Inbox | Fire Browser / deployed Fire Inbox | Browser cookies and notes |
| Browser | `dev.jadan.fire.browser` | Browser history, sessions, input mapping |
| Playroom | `GameShelfActivity` / GBA room | Pinned HTTPS games, native game integrations |
| Compute | `CodexDeskActivity` in streamlined mode | Trusted Codex executor and Cloud fallback |
| Apps | `AppLibraryActivity` | Normal independent Android TV launcher registry, including Canvas |
| Tools | `MainActivity` | App Catalog, terminal, Voice, Input Lab and legacy browser lab |

**No installed app packages were merged or deleted.** The redundant Dev Bridge
updater and second key logger were removed from source in the Console v2
consolidation; Catalog and Input Lab retain the underlying user workflows.
Package and signing identities are
unchanged. Updating `firetv-dev-bridge.apk` in place preserves the Dev
Bridge's existing app data. The separate Fire Browser still needs its own
updated `firetv-browser.apk` for Browser → MORE → Pin webpage to Playroom.

## Prior art and authority

- [Android TV launcher and focus model](https://developer.android.com/training/tv/get-started/create):
  native D-pad focus, Leanback intent filter and app icons/banners.
- [Android TV icon/banner recommendations](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines):
  own lightweight vector channel art plus Android's actual installed app icons.
- [TV Launcher](https://github.com/wiiguy/lightweight_androidtv_launcher):
  compact app icon grid, user-controlled tile selection; unlike that project,
  Fire Console does not replace a device launcher or install an accessibility
  service.
- [Existing Fire Browser](https://github.com/JadanPoll/fire/blob/main/browser/src/main/java/dev/jadan/fire/browser/BrowserActivity.java):
  reuse the real Chromium WebView, session, URL history and crash recovery;
  avoid inventing a second browser inside Console.
- [Existing App Catalog](https://github.com/JadanPoll/fire/blob/main/app/src/main/java/dev/jadan/fire/CatalogActivity.java):
  explicit SHA-256-verified release installer, no additional update engine.

## Voice outage diagnosis — no invented microphone control

Current Fire Browser, Codex and Voice test fields rely on the **Amazon/Fire
OS keyboard** for dictation. They do not directly access the Alexa Voice
Remote's microphone and do not own a SpeechRecognizer instance. Android
`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` is only a separate *optional*
activity probe and may not be installed.

Authoritative distinction:
- [Amazon Developer Community — keyboard dictation varies by device/OS](https://community.amazondeveloper.com/t/which-fire-tv-devices-support-keyboard-dictation-mic-key-in-third-party-text-fields/29808):
  keyboard microphone transcription is not a guaranteed third-party API.
  The Oct 28, 2026 case describes dictation failing across apps after an OS
  update and persisting across app restarts; another locale made it work on
  that specific device. It is a **hypothesis**, not proof for this Fire TV.
- [Android InputMethodManager.restartInput](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager#restartInput(android.view.View)):
  resets the *client view's* input connection, not the OS's voice recognition
  service. Fire Console only offers an explicit, safe reconnect.
- [Amazon Fire TV voice remote](https://www.amazon.com/gp/help/customer/display.html?nodeId=201246260):
  Alexa remote mic is controlled by the device service, not arbitrary app
  microphone permissions.
- [Android RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent):
  system-installed activity may return a transcript, but the presence of
  an Intent handler is not proof of Fire remote mic access.

The Voice channel displays OS build, locale, selected input method, native
input devices and recognizer service availability. It offers:
1. A real editable text field to test ordinary keyboard transcription.
2. **Reconnect this text field**, which requests `hideSoftInputFromWindow`,
   `restartInput`, and `showSoftInput`, without clearing any app data.
3. A separately chosen Android speech activity probe (where installed).
4. A shortcut to system settings, without changing the device automatically.

Classification of outcomes:
- **Keyboard can't open at all**: test the client IME reconnect and Android
  keyboard service, then check across other apps.
- **Typing works but mic closes keyboard/opens Alexa or yields no transcript**:
  suspect system/locale/OS dictation regression; this app cannot force the
  service to recover. Test another text field/app and inspect OS build/locale.
- **Speech activity works but keyboard mic fails**: different system paths;
  do not claim a Fire Browser problem.
- **Both fail**: verify Fire OS update, reboot *the Fire TV device*, not
  merely this app, and use system troubleshooting. Do not clear Browser data
  or cookies as the first step.
- **If all apps are affected after an update**, consider the reported locale
  workaround only as a reversible manual experiment. Record the original
  language first. No automatic configuration changes.

## Playroom and data safety

The browser sends the current page URL to `GameShelfActivity` via a targeted
`ACTION_SEND` with `EXTRA_SUBJECT` (page title). An explicit PIN confirmation
is required. The bookmark policy accepts only real HTTPS hosts, rejects
credentials in the authority, strips queries and fragments that may contain
tokens, and limits the number of pinned games to 16. The game itself runs in
Fire Browser with its existing audio engine. Unpin only deletes a shortcut,
never a website, browser storage or game.

## Wii-style legacy channel refresh and terminal IME recovery (November 2026)

The original Dev Bridge/Tools, Catalog, Codex, Voice, Playroom, Apps and
Input Lab now use the same **light TV channel** visual vocabulary as Home:
pale background, white rounded surfaces, blue D-pad focus outline,
two-column action rows for wide screens, legible descriptions, and the
same Back/OK behavior. A shared `TvUi` class centralizes presentation.
This change deliberately **does not rewrite** network updates, Playroom
preferences, the installed-app launcher registry, device capability checks,
Codex job behavior, or Input Lab's normalization logic.

The local terminal had a concrete TV usability defect: on launch it
immediately focused the EditText and opened Fire OS's on-screen keyboard,
while RUN/STOP/COPY/CLOSE were rendered *below* the output. Fire's keyboard
could then cover the actionable controls. The terminal now:

- opens with **TYPE** focused, not the text editor; Android soft input
  is initially hidden and does not auto-reopen on D-pad focus
- anchors COMMAND DECK, entry, TYPE/RUN/HIDE/STOP/COPY at the top of the
  window; scrollable output occupies the lower region
- uses `SOFT_INPUT_ADJUST_RESIZE` where Fire OS honors it, but relies on
  the top-anchored command deck as a fallback when the keyboard overlays
  rather than resizes
- invokes the keyboard only through TYPE; Run and IME Done both hide it,
  clear text focus, and return control to a visible button
- retains command draft when another process is busy; restores the console
  via Android Back; keeps command limits and local app-UID trust boundary

The keyboard is owned by Amazon's OS, so a full-screen Fire IME can still
temporarily occlude the Activity until **Back** or **Done** exits it.
The app cannot control those OS-level surfaces or globally remap Back.
The observed defect addressed here is *automatic keyboard entry plus
bottom-positioned controls*, not a claim that third-party code can
guarantee behavior of every Fire OS keyboard.

### Physical Fire TV acceptance sequence

1. Open Home → Tools → LOCAL TERMINAL.
   The terminal must show **TYPE** focus and **no keyboard**.
2. Move focus across TYPE/RUN/HIDE/STOP/COPY using D-pad; confirm
   focus is visible and output remains in the lower panel.
3. Select TYPE. Verify the keyboard opens, enter `pwd`, press its Done.
   Confirm keyboard closes, output contains the workspace path,
   and D-pad returns to a visible control.
4. Repeat but press Back instead of Done; select RUN. No automatic
   keyboard reappearance should occur. Use HIDE if necessary.
5. Run a noisy command, then COPY, navigate Back, and reopen terminal.
   No browser/game session should have been reset.
6. Exercise Tools → Check/Install, Voice → keyboard test, Apps →
   Catalog, Playroom → Play, Codex → executor pairing to ensure
   actions and data paths still work.

Automated validation: `python3 tools/check_tv_ui_contract.py`,
`python3 tools/check_console_manifest.py`, Android unit tests and
`:app:assembleRelease`. These are static/build checks; device acceptance
is an additional requirement.

## Physical TV acceptance checks

Perform from a trusted authorized ADB link:

```sh
python tools/firetv_test.py --serial TV_IP:5555 console
python tools/firetv_test.py --serial TV_IP:5555 voice
python tools/firetv_test.py --serial TV_IP:5555 inbox
```

Then navigate through all six Home channels using only D-pad/OK/Back, confirm
installed-app icons and real Playroom game playback/audio, pin/unpin a test
HTTPS game URL, test Fire OS keyboard dictation in Voice, try a safe reconnect,
and return to Browser without losing cookies or input state. The screenshots
are smoke evidence, **not** proof of dictation or game playback.

## Stopping rule

Keep the familiar apps as independent packages until evidence shows a
specific quality or maintenance problem from their separation. The Home
shell is the factorization point. No additional privilege, launcher takeover,
new JavaScript engine, audio daemon or silent installer has been added.
