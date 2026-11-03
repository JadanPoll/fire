# fire — Fire TV Dev Bridge

Standalone Android/Fire OS developer workstation for **Fire OS 7.7.1.7 (Android 9 / API 28)**. This project is independently versioned from Manifold Console.

## What works in the first release

- Fire TV Leanback-compatible app with D-pad focus and remote key timing/automatic repeat observations.
- Device/OS summary and human-readable network errors.
- **Input Lab**: visible virtual pointer, D-pad hold normalization independent of raw key-repeat frequency, OK release activation with cooldown, raw/accepted/suppressed event counters.
- On-demand check for latest [GitHub Release](https://github.com/JadanPoll/fire/releases/tag/firetv-dev-bridge-latest), download and SHA-256 check.
- Native installation confirmation through Fire OS (not silent).
- Local-only diagnostic capture, shareable only when the user selects Share.
- **App Catalog** pulled on demand from [catalog.json](catalog.json): install, update, or launch approved APKs, verifying each checksum and Android package identity.
- **Remote Canvas**: separately installed sample Android app proving that multiple apps can be shipped from this repository.
- **Codex on Fire**: Fire TV keyboard dictation/typing, one intention draft, clipboard handoff to Codex Cloud on the TV, native-runner CPU/ABI/RAM precheck. This is a working TV-side control surface, not yet a locally executing native Codex binary.
- **Voice Preflight**: read-only audio-device/standard recognizer probe. Keyboard dictation is independently tested in Codex on Fire.
- **Installer recovery**: Android installer instead of automatically opening Developer Options; retry last verified APK without a second download. Fire OS may still require one per-source permission grant and an explicit install confirmation.

### Install

1. On Fire TV, install **Downloader by AFTVnews** from the Amazon Appstore.
2. If necessary, enable **Install Unknown Apps → Downloader** under Developer Options (some Fire OS layouts put it under My Fire TV).
3. In Downloader, enter **https://github.com/JadanPoll/fire/releases/download/firetv-dev-bridge-latest/firetv-dev-bridge.apk**. If the app returns a web page instead, use the Release page's asset link.
4. Install and launch **Fire TV Dev Bridge**. Check for update, and explicitly approve installs when prompted.

**First release is not published until the Actions build succeeds.** See [Build](https://github.com/JadanPoll/fire/actions/workflows/android.yml).

### Development

Native Java, minSdk 28, targetSdk 28, compileSdk 35, Gradle 8.10.2, Java 17. The app is built in GitHub Actions and published as a pre-release APK under a stable tag. The CI workflow builds from repository source with the lab signing key.

The lab signing key is intentionally **public** to allow zero-configuration build reproducibility. It is **insecure for production**; anyone can sign an app pretending to be this test app. Do not log into sensitive accounts or grant highly privileged capabilities to a build signed with this identity. Before expanding into privileged or production features, migrate to privately held GitHub Actions credentials and a secure release/signing policy.

### Architecture

```
GitHub commits → GitHub Actions (build APK) → GitHub Release
                                                 ↓ HTTPS (on demand)
                                    Fire TV developer helper
                                           ↓
                              Android package installer (user approves)
```

The helper intentionally does **not** implement background polling, automatic remote execution, silent installs, privileged global input interception, or telemetry upload. Its local key telemetry is scoped to its own window. Device addresses such as `10.196.149.184` are a private-network detail, not a public connection method.

### Next priorities

1. Verify build → sideload → launch → remote event trace on physical Fire TV.
2. Split App Manager from remote-input experiments; add an app catalog and install/launch visibility.
3. Add a narrow, opt-in HTTPS diagnostics channel only after an authentication/authorization design.
4. Investigate optional separately-authorized ADB mode and global input experiments with explicit capability boundaries.

Further details: [Architecture & stopping rules](docs/ARCHITECTURE.md).

### Browser handoff troubleshooting

If Codex/ChatGPT fails to open from **ON-FIRE CODEX**, try **COPY PROMPT + OPEN CODEX IN SILK**. This explicitly targets Amazon Silk (`com.amazon.cloud9`) rather than the generic Fire OS link handler. Other controls offer a browser chooser, the ChatGPT home page, and opening Silk's address bar directly. **REFRESH BROWSER DIAGNOSTICS** reports the default Android handler and available choices. The app only knows whether browser launch was requested, not whether the webpage loaded.

A browser/rendering/login failure can occur independently of intent routing. Amazon recommends clearing Silk's cache and restarting Fire TV for freezes; OpenAI suggests a different browser or clearing affected site cookies for login problems. Embedded Android WebView isn't the default workaround because Google OAuth often rejects embedded user agents. A true two-way native Codex session eventually removes website rendering from the control path.

The dictated draft remains in the Fire UI when the browser opens; any **Copy** action is explicit. A clipboard handoff is not a submitted Codex task.

### Fire Browser Lab — independent TV browser test

The bridge now includes a **WebView Browser Lab** using Fire OS's built-in Chromium-based Amazon WebView (AWV). It is not a second independently shipped Chromium engine. It provides a local ordinary input / textarea / `contenteditable` test page, browser address field (HTTPS only), direct ChatGPT page launch, BACK / reload, D-pad virtual mouse, and a **native SPEAK → PASTE** handoff into the currently focused editable web field. PASTE is explicitly user initiated and **never submits** a message. This tests and potentially works around a missing browser keyboard in contenteditable, but does not guarantee compatibility with React/ProseMirror or any particular website.

The native draft avoids dependence on web IME for dictation, which was already confirmed to work on the Fire TV. The WebView has JavaScript and DOM storage for modern pages, but file access, automatic JS popups, and mixed HTTP content are disabled. It has no native JavaScript command bridge.

**Login warning:** ChatGPT may not support embedded WebViews, particularly Google-account sign-in. Do not enter credentials into an untrusted browser, use an unsupported OAuth workaround, or assume a good local input-probe test proves remote ChatGPT will load. For dependable Codex work, the eventual native app-server bridge remains the primary architecture.

The external browser selector now shows an actual list of eligible installed browsers, even if there is only one. Android's ordinary chooser can directly launch the only handler. Our built-in browser appears as another explicit choice.

### Standalone Fire Browser

`browser/` is now a separate Android/Fire OS application, not merely an Activity inside the Fire TV Dev Bridge. Install **Fire Browser** using the bridge's **APP CATALOG**, or install `firetv-browser.apk` directly from the rolling GitHub Release. Its independent package `dev.jadan.fire.browser`, Leanback launcher, browser icon and banner mean it appears as an ordinary Fire TV app. Amazon documents that sideloaded launched applications can appear in its Recent row, although exact ordering/placement remains under Fire OS control.

The browser also maintains its own **RECENT** (16 recent HTTPS pages, stored on device), resumes the last visited page after relaunch, and supports clearing local recent pages. Text zoom starts at 135% and is adjustable and remembered using A− and A+. The WebView recovers if the renderer is killed by the system; in a true renderer crash it shows the input lab instead of repeatedly crashing the same URL. The original browser experiment remains in the bridge for A/B comparisons, and both APKs coexist.

Source research: TV Bro (cursor and browser UI), Comet (lightweight WebView, tab state/renderer recovery), Arrowz Browser (text zoom, text-native overlay, renderer recovery), and mrowser (TV Launcher and recent/history). We intentionally haven't copied their media, ads, multi-tab or permission frameworks, which aren't necessary for the current experiment loop.

Browser text controls separate text scaling (A+/A−, remembered across launches) from page layout zoom (PAGE+/PAGE−, for fixed-size layouts). The Codex desk launches the standalone browser if installed, otherwise falls back to the original lab. This preserves the existing on-device workflow while the independent browser is being adopted.

### Browser scaling, submission and remote tests

In standalone Fire Browser, the default web text and page scale are now 85% each, with compact controls and a collapsible TOOLS panel. A−/A+ allows 55%-200% text size; PAGE−/PAGE+ allows 50%-160% page scale, FIT restores both to 100%. Your previously saved text size is retained until you change it.

Fire TV keyboard dictation is supported in the native SPEAK field, rather than relying on ChatGPT's rich web editor. A separate SEND button inserts the draft and requests the ChatGPT send action. The browser also requests IME_ACTION_SEND, though the actual Fire TV keyboard may continue to show Previous. Drafts are preserved on uncertain outcomes.

From a trusted LAN-connected computer, use Python 3 + official Android platform-tools: python tools/firetv_test.py --connect FIRE_TV_IP:5555 doctor, followed by smoke --apk path/to/firetv-browser.apk, live (scrcpy), or shot. One ADB authorization on the TV is required. See [the testing loop](docs/TEST_LOOP.md).

The lowest-friction remote iteration is: python tools/firetv_test.py --connect FIRE_TV_IP:5555 smoke --latest. It downloads the current GitHub Release browser, verifies its SHA-256 from the release notes, installs it, launches the local HTML input lab, takes a screenshot and checks whether the browser process survives. No repeated manual APK download.

### Browser navigation, history and performance (latest revision)

The address bar now treats plain words and questions as Google searches;
hostnames remain HTTPS web navigation. MENU toggles browser tools. The optional REMOTE toggle enables Play/Pause=pointer and Rewind/FF=browser history on ChatGPT/Google when Fire OS delivers them. REMOTE is off by default to preserve media playback. Home/Alexa/volume remain system-managed.

The pointer overlay covers native browser buttons as well as the webpage,
and redirects clicks to the appropriate existing Android control rather
than reinventing each button's behavior. Recent pages are labeled and
filtered to exclude OAuth/sign-in callbacks, sensitive token query strings,
and tracking-only parameters, and do not repeatedly persist the same SPA
route. Previously stored transient entries are filtered from the chooser.

TOOLS → STATS shows the WebView engine and memory diagnostics. TOOLS → INSPECT
enables authorized-ADB Chrome DevTools inspection for the foreground session
and is off by default; **never enable on an untrusted computer**. A
`python tools/firetv_test.py --serial TV_IP:5555 perf` command saves local
memory/renderer/frame/CPU diagnostics to `.fire-tests/`. Browser slowdown
on ChatGPT must be measured before choosing CSS, user-agent or engine
changes; the app cannot magically accelerate the website's JavaScript.
