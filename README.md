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
- **Voice Preflight**: read-only enumeration of Android audio inputs and speech services; optional system speech-intent test and copyable results. Does not try to intercept Alexa's system microphone button.

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
