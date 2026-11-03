# Practical Fire TV experiment loop

## Goal

Make the human responsible for interesting observations and decisions, not
repeated installer/navigation work. This applies REM's "the experiment should
eventually run itself", caution against hostage UX, and RSM's distinction
between experiments that change a decision and tests that merely run.

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
