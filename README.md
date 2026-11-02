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
