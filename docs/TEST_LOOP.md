# Practical Fire TV experiment loop

## Goal

Make the human responsible for interesting observations and decisions, not
repeated installer/navigation work. This applies REM's "the experiment should
eventually run itself", caution against hostage UX, and RSM's distinction
between experiments that change a decision and tests that merely run.

## REM testing-budget tiers (November 2026)

**Rule:** ask what uncertainty would actually change the next decision.
Start with the cheapest discriminating evidence; escalate only when the
remaining uncertainty matters. A green source check is not a runtime proof,
and repeating the emulator without a new question wastes the feedback loop.

| Change or question | Default evidence | Expensive escalation only when needed |
| --- | --- | --- |
| Documentation, test scripts, app curation | Fast static Python contracts | None for native execution |
| One native module / remote-input adapter | Fast checks plus only affected Gradle unit-test/compile tasks | Emulator if behavior or focus changed |
| New signed release on `main` | Complete APK build and tests, GitHub release | Physical Fire TV when Amazon-specific |
| "Does the published Console launch and respond to OK?" | On-demand emulator, `source=release`, `scope=quick` | Full suite if app interactions matter |
| "Do independent apps compose correctly?" | On-demand emulator, `scope=full` | Real Fire OS remote/IME as appropriate |
| Unmerged code needs screenshot/runtime evidence | Emulator `source=branch` compiles that ref | Physical device only for unexplained differences |

**iPad / ordinary GitHub loop:** Open
[Fast change-scoped validation](../.github/workflows/fast-validation.yml)
to read the PR's short report. To request real emulator evidence, open
[Android TV emulator smoke](https://github.com/JadanPoll/fire/actions/workflows/android-tv-emulator.yml),
select **Run workflow**, and choose a source and scope. `release` downloads
the four existing APKs with their release-notes SHA-256 checksums, skipping
the whole Java/SDK/Gradle build. `branch` builds the selected Git ref.
`quick` checks Console → Browser, focus/OK, and crash log;
`full` additionally checks independent apps, Apps, Tools and navigation.
Screenshots, UI XML, ADB commands and logcat are published as one artifact;
the readable summary is on the run page. No new accounts, MCP services,
repeated device setup or private GitHub credentials are needed.

**Bounded measurements, not synthetic targets:**
the earlier full-build emulator path was 197 seconds; the verified release
download + full emulator run was 158 seconds; quick release + emulator
was 162 seconds in one sample; pure tooling fast validation was 11 seconds.
The 4-second quick/full difference is run-to-run noise at this scale, so
do not pretend the narrower scope eliminates emulator cold-boot cost.
A publicly documented AVD snapshot cache may reduce repeated emulator
boot time, but its cold-cache creation, transfer and eviction costs need
measurement before we add permanent machinery.

**No hostage gates:** A failed optional emulator or an unrelated stale
experiment cannot block making a new PR or publishing a correctly built
release. Diagnose failures with the shortest relevant source/log/screenshot
witness. For changes to Fire OS-specific keyboard, remote timing, audio
or WebView behavior, label emulator evidence as provisional and use the
physical TV only when it would resolve an actual question.

## Which test method answers which question?

1. GitHub CI: Build APKs, catch compile errors, verify test-helper syntax.
   Cannot prove how the Fire TV keyboard or ChatGPT DOM behaves.
2. Local-network physical Fire TV: ADB install, launch, key events, screenshot,
   process-status; scrcpy offers live view and remote control. One physical
   authorization must be accepted first. This is our default realistic loop.
3. Amazon Appstore Quality Central: Live Device Interaction virtual Fire TV
   devices were announced July 2, 2026. It provides cloud APK upload, virtual
   remote, screenshots, logs and ADB. Availability must be checked in the
   Developer Console; physical cloud device access is partner-restricted.
4. Actual Fire TV speech/IME test: necessary when the outcome depends on
   Amazon's remote microphone/keyboard, UI focus, or hardware-specific WebView.
   Do this once for a discriminating fork, not on every code change.

## One-time setup

Install official Android platform-tools (adb) on a trusted host sharing the TV
LAN. Enable ADB debugging on Fire TV. Identify its IP at
Settings / My Fire TV / About / Network. The first adb connect triggers an
authorization dialog on the TV; approve the trusted host there.

## Fast path, from repository root

    python tools/firetv_test.py --connect FIRE_TV_IP:5555 doctor
    python tools/firetv_test.py --serial FIRE_TV_IP:5555 smoke --apk firetv-browser.apk
    python tools/firetv_test.py --serial FIRE_TV_IP:5555 live
    python tools/firetv_test.py --serial FIRE_TV_IP:5555 shot
    python tools/firetv_test.py --serial FIRE_TV_IP:5555 key ok

smoke uses the existing local HTML editor/input lab, screenshots into
.fire-tests/ and checks whether the browser process survives. There is no
automatic inference and no silent retry. A single screenshot cannot confirm
that dictation or sending works; remotely observe the flow with scrcpy.
Live ADB access is powerful. Never expose TCP/5555 to the internet or commit
ADB credentials to the repository. If working from another network, connect
through a trusted host and secure tunnel rather than opening ADB publicly.

## Known user-flow distinctions

- Native dictation works on this physical TV, while ChatGPT's web editor
  does not reliably invoke the Fire TV IME. Keep the working native field.
- The system keyboard can display Previous instead of Enter. Request
  IME_ACTION_SEND, but provide an explicit native SEND action regardless.
- JavaScript-invoked ChatGPT Send is a site-specific optional shim; preserve
  text and show an actionable failure when no enabled send button is found.
- WebView's zoomOut() may have its own floor, but Android textZoom doesn't
  mandate 100% minimum. Expose 55%-200% text and 50%-160% CSS page scale with
  a predictable 100% reset. Complex sites may handle CSS zoom imperfectly.

## Reuse versus unnecessary framework construction

Existing building blocks: adb, scrcpy, WebView, Android's keyboard, Github
releases and one Python script. Comet, Arrowz and TV Bro illustrate TV-browser
techniques; do not replicate their entire media/download/tab systems.
Maestro/Appium/UIAutomator can be added once a real repeated sequence justifies
a richer driver. For structural UI testing without the hardware, use Android TV
emulators or Amazon's virtual-device service, but do not confuse their results
with Fire OS remote voice behavior.

Stop engineering test infrastructure once installation, opening, screenshot,
observation and result-sharing take little manual effort. Reopen when a real
failure remains hidden. Source material:
https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html
https://github.com/Genymobile/scrcpy
https://community.amazondeveloper.com/t/live-device-interaction-with-virtual-devices-is-now-available/28664
https://github.com/HereIamGosu/android-tv-adb-control-center

The smoke command also accepts --latest instead of --apk to fetch and SHA-256 verify the current rolling browser release automatically. This makes the common edit/build/release/device-test loop one host command, once ADB is authorized.

## Diagnosing a freeze rather than guessing at optimizations

The browser's **TOOLS → STATS** shows Android/WebView version, system memory,
recent load time, and renderer recovery count. **TOOLS → INSPECT** is an opt-in
toggle for Chrome DevTools inspection over an already-authorized ADB connection;
it is off by default and is disabled while the activity is backgrounded.
Because debugging reveals signed-in page content, use only on a trusted device
and trusted computer. In Chrome on your computer visit
`chrome://inspect/#devices`. JavaScript Performance and Memory profiles
can distinguish a long JS task, repeated layout, and an excessive JS heap.

From your trusted computer run:

    python tools/firetv_test.py --serial TV_IP:5555 perf

It saves bounded `dumpsys webviewupdate`, memory, frame stats and CPU data in
`.fire-tests/`, not GitHub or a server. Run once when responsive and once
when ChatGPT becomes slow. Review it before sharing because system process
names may be present. WebView renderer memory may live outside the browser app
process, so these are partial measurements. See Android's WebView memory
guide: https://developer.android.com/topic/performance/memory/guide/webview-memory

Use the observations to distinguish:
- native controls remain responsive, webpage does not: possible WebView
  renderer / JavaScript task; DevTools can narrow it down.
- entire app UI fails to respond: Android UI thread, system load or focus;
  capture system state instead of randomly editing website scripts.
- app closes / renderer is recreated: renderer death; examine renderer
  recovery count and memory. A killed renderer and an unresponsive renderer
  are different cases.
- only ChatGPT is problematic: compare the same workload in TV Bro (Gecko)
  or an alternative engine. Our installed WebView may be the constraint.

Optional **MOTION** disables animations/transitions on pages without
disabling content, but it is not a proven optimization for ChatGPT's JS work.
Do not remove images, GPU acceleration or authenticated site features just
to make a synthetic performance benchmark look better.

## Remote keys and keyboard boundaries

MENU opens or closes TOOLS. The pointer now covers both webpage and native
browser controls. Fire Home, Alexa microphone and volume remain system-owned.
TOOLS -> REMOTE opts into Play/Pause=pointer and Rewind/FF=browser history
on ChatGPT/Google; off by default to preserve normal media behavior. Media
keys may not reach the app on every Fire OS hardware/software combination.

A raw unmodified Enter received by the app while ChatGPT's web editor is
focused now attempts Send. Shift+Enter is not remapped. But the Fire TV
software keyboard can commit a newline directly without a KeyEvent;
Java key mapping cannot guarantee it becomes ChatGPT Send. The native
SPEAK/SEND controls remain available. STATS records only last raw Enter
key metadata, not typed text, to distinguish these cases.

## Compact UI and Fire keyboard Next instrumentation

Default UI: address field, GO, MENU, CURSOR, native dictation field, VOICE and SEND. MENU is a single floating navigation row, with further actions in MORE. Normal pointer mode covers browser controls and webpage without moving the webpage's layout when MENU opens.

Pointer motion uses a fine initial nudge and Choreographer-paced acceleration, not OS key-repeat jumps. A small translated cursor layer avoids redrawing an entire 1080p overlay each frame. This is a code-backed improvement hypothesis, not proof of physical TV smoothness.

The Fire TV IME may use EditorInfo editor actions, InputConnection.commitText(newline), or soft KEYCODE_ENTER rather than Activity.dispatchKeyEvent. The WebView now wraps its original InputConnection while preserving normal input operations; the targeted action tries submission only if the ChatGPT composer has been identified. The native VOICE field handles Next/Done/Send/Go. In MENU → MORE → Browser diagnostics check Last web IME submit and Last raw Enter after trying Next. If both show none, Fire OS may have routed the key through another path; do not add another blind interception hook without tracing it first.

## Fire Inbox bug reports: pointer scrolling, renderer failures and cookies

When pointer mode is ON, pushing the D-pad toward the **WebView's** top or
bottom edge sends a throttled mouse-wheel event at the pointer's x coordinate.
It can scroll nested website panels; it does not call the top-level
`WebView.scrollBy()` or inject a DOM scroll implementation. A held key-up
watchdog stops runaway movement if the Fire remote never releases a key.

A disappearing page is not necessarily a crashing app. Browser diagnostics
(MENU → MORE → Browser diagnostics) records the most recent pointer action,
renderer exit, and recovery count without recording the URL or typed text.
The renderer cleanup now happens synchronously as Android's WebView API
recommends. A renderer crash recovers to the input lab to avoid automatically
restarting a known-crashing page. The previous URL remains available in RECENT.

From an authorized device on the same local network:

    python tools/firetv_test.py --serial TV_IP:5555 errors

This saves a bounded logcat subset (FireBrowser and Android WebView/crash
tags) under .fire-tests/. It is not uploaded automatically. Review crash
stacks before sharing: exceptional logs can still include private text.

For a non-posting smoke test of the deployed Fire Inbox:

    python tools/firetv_test.py --serial TV_IP:5555 inbox

This opens the Site and captures a screenshot. It does not test actual
saving; the existing Fire Inbox test message is stronger evidence of
the posting flow.

Cookie handling: Android CookieManager.flush() runs on a background worker
after HTTPS page loads and when the Activity pauses/destroys. Cookies are not
cleared on normal navigation or renderer recovery. Optional MENU → MORE →
Allow third-party cookies can help some sign-in providers; it is OFF by default
because it also broadens tracking. The diagnostics display only current-page
cookie presence, never cookie names/values. Persisting a session also requires
the **server** to set a persistent cookie (not a session-only cookie) and the
user to retain the app's data. Android apps/browsers do not share cookies
automatically; uninstalling/clearing Fire Browser's data removes its cookies.

This work is code-verified and CI-built, not a claim that the intermittent
device crash or logout was reproduced or eliminated.

## Research-backed remote behavior (Fire Inbox queue, 2026-11-03)

**Source / justification map** (source implementations first; all still require
physical Fire TV validation):

| Fire Browser mechanism | Existing concrete implementation |
| --- | --- |
| Precision pointer, key hold acceleration, mouse-wheel at page edge | [Comet CursorController](https://github.com/SamWylde/comet-tv/blob/main/app/src/main/java/com/tdarby/comet/input/CursorController.kt) and [TV Bro D-pad adapter](https://github.com/truefedex/tv-bro/blob/master/app/common/src/main/java/com/phlox/tvwebbrowser/utils/DPADNavigationEventsAdapter.kt) |
| Foreground, opt-in MediaSession for Play/Pause and separate MEDIA_PLAY/MEDIA_PAUSE key codes | [Amazon MediaSession integration](https://developer.amazon.com/docs/fire-tv/mediasession-api-integration.html) and [Android MediaSession.Callback](https://developer.android.com/reference/android/media/session/MediaSession.Callback) |
| Check → verify release checksum → cached APK → user-confirmed installer | Existing [Dev Bridge CatalogActivity](https://github.com/JadanPoll/fire/blob/main/app/src/main/java/dev/jadan/fire/CatalogActivity.java) and [Comet UpdateChecker](https://github.com/SamWylde/comet-tv/blob/main/app/src/main/java/com/tdarby/comet/update/UpdateChecker.kt) |
| Keep pointer state when navigating pages | Explicit user requirement; addresses the actual `loadUrl()` reset. Comet has a deliberately different Back/toolbar policy, so this is not claimed as copied behavior. |

### Minimal invariants and test flow

1. Enable CURSOR. Navigate via link taps, Go, Back, and the Inbox menu.
   The pointer remains active through URL navigations, including Back when
   WebView history exists. Deliberately entering a native voice field or an
   editable web composer still hands control to the keyboard/focus mode.
2. In MENU → MORE select **Remote media shortcuts** (off by default).
   Play/Pause, Play, or Pause should toggle pointer on every page; Rewind and
   Fast Forward navigate history. Media keys are intercepted **while enabled**,
   including on websites with video. The optional MediaSession is active only
   while this Activity is resumed and is released on destruction. No
   background receiver, notification, or playback capability is added.
   **CURSOR remains the fallback** if Fire OS withholds a media key.
   Check MENU → MORE → Browser diagnostics for `Last media delivery` and
   `Media session active` when a shortcut doesn't work.
3. To update on the TV, select **MENU → MORE → Check browser update**.
   The app checks the existing pinned rolling GitHub release, verifies
   `SHA256 firetv-browser.apk`, and downloads only after confirmation.
   Reopening a verified version after a failed installer uses the cached APK.
   Android must still confirm an installation; no silent self-install.
   Do **not** uninstall the browser to update: in-place update preserves
   app data, subject to Android signing rules and system behavior.
4. Re-test previous observations: 25-dp pointer jumps, corner/edge scroll,
   sign-in persistence, and intermittent WebView renderer termination.
   **A successful APK build is not a successful physical-device test.**
   Capture `python tools/firetv_test.py --serial TV_IP:5555 errors`
   when the renderer crashes (review logs locally before sharing).

**Safety:** Both the Dev Bridge and Fire Browser currently use a public
development signing key. Updates preserve Android's normal signature checks,
but the development signing key is not safe for production or sensitive
account use. GitHub SHA-256 checks ensure downloaded file integrity relative
to release metadata, not release publisher authenticity after account
compromise. Do not use the self-updater to claim secure production updates.

**Stopping rules:** Keep a feature only if it directly eliminates a
reproduced UX failure, preserves a demonstrated existing workflow, or provides
the minimum observation needed to diagnose one. No generic media framework,
permission automation, background update scheduler, or site-specific
pointer exception without evidence.

## Native hover and text-entry pointer contract — 2026-11-03

Reference behavior:
- [Android WebView / View generic hover events](https://developer.android.com/reference/android/webkit/WebView#onGenericMotionEvent(android.view.MotionEvent)): `ACTION_HOVER_ENTER`, `ACTION_HOVER_MOVE`, `ACTION_HOVER_EXIT` with mouse source. The browser now dispatches those at the real pointer coordinates. Movement-only hover is throttled to <= 30 Hz. Unlike CSS/JS site injection, this lets the WebView itself decide whether and how to expose controls; some sites can still ignore hover.
- [Comet CursorController](https://github.com/SamWylde/comet-tv/blob/main/app/src/main/java/com/tdarby/comet/input/CursorController.kt): explicit `SOURCE_TOUCHSCREEN` on synthesized taps (the previous Fire Browser code omitted this), using one DOWN/UP pair for click.
- [TV Bro D-pad/IME adapter](https://github.com/truefedex/tv-bro/blob/master/app/common/src/main/java/com/phlox/tvwebbrowser/utils/DPADNavigationEventsAdapter.kt): keyboard ownership is a distinct input state from pointer movement; avoid switching arbitrary site controls because a deferred focus callback ran.
- [Amazon Fire TV MediaSession](https://developer.amazon.com/docs/fire-tv/mediasession-api-integration.html): media keys may arrive through MediaSession rather than Activity keys. Fire Browser registers for transport controls while foreground, defaults the remote shortcut ON unless explicitly disabled in preferences, and keeps CURSOR available as fallback.

Physical TV acceptance cases (not simulated by CI):
1. On a real video page, move the pointer over a playing video and check if that player's standard hover controls appear; repeat near playback seek bar, over a fullscreen button, and on a non-video section. Site-specific click/touch-only video players may still differ.
2. On a page with multiple textboxes and links, OK-click one textbox, type into it, dismiss the keyboard, and navigate again. Exactly one tap per OK event; no focus jump from a second forced `requestFocus()`; selected pointer mode remains visible. Repeat after navigating to a second URL.
3. Press the Fire TV Play/Pause remote button twice, allowing >350ms between presses. The `POINTER ON` toolbar label should toggle correspondingly. If it does not, open MENU → MORE → Browser diagnostics and inspect `Media session active` and `Last media delivery`. ADB can generate a comparable key: `adb shell input keyevent 85`; actual remote delivery can differ.
4. Keep media shortcuts OFF from MORE when using that remote key for ordinary website video playback.
5. During all tests, confirm Fire Inbox native VOICE → SEND still posts, and no stored note or session is modified by pointer movement.

A successful GitHub Actions build verifies Java/resource compatibility only. Do not mark these Fire Inbox bug reports done without actual Fire TV evidence.
