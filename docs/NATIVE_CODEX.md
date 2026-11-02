# Codex on Fire OS 7: real execution, not just remote display

## What "bidirectional" means

1. Fire UI can run a session (chat, approvals, edits, builds, measurements) using a *local* on-device Codex app-server when hardware and Android compatibility allow.
2. Fire UI can also attach to a remote Codex executor. The same conceptual controls should work in either location.
3. A remote Codex session can ask Fire to run **bounded, approved experiments** and receive telemetry. Fire should not expose a generic unauthenticated shell.
4. Spoken words are another entry point to *this same* interface, not a second subsystem.

Model inference for a local Codex client is network-backed unless a separately provisioned local model is actually supported. "Codex runs on Fire" means its agent/control process and tools run on Fire, NOT necessarily the model's weights.

## Actual hardware gate

First open **ON-FIRE CODEX → REFRESH ON-FIRE CAPABILITIES**. Note `SUPPORTED_64_BIT_ABIS`, Android API, total/available RAM, and storage. CPU marketing names are not enough: a Fire TV with a 64-bit SoC may expose only 32-bit Android userspace.

Candidate community implementations to review:
- https://github.com/calibancode/codex-termux reports ARM64 Android 7+/API 24+ (compatibility needs real validation on Fire OS 7).
- https://github.com/DioNanos/codex-termux supports ARM64 Android 10+/API 29+ in recent packaged versions, **not** Fire OS 7 by default.
- https://github.com/StateMeta/codex_android describes Android native binaries but is older/experimental and lists substantial memory recommendations.

Never sideload unreviewed Codex binaries with sensitive credentials. Confirm build provenance, needed native libraries, Android API level, process isolation, CPU architecture, and auth storage. The public lab app signing key in this repo must NOT become a privileged native agent identity.

## Iteration order

A. Prove keyboard dictation works: focus the editable prompt, use the Fire remote mic, observe whether text lands. Some devices instead launch Alexa system search. This is not predictable solely from Fire OS version.

B. Get Codex Cloud on Fire (provided browser and login work). That is **on-TV operation now**, although the agent executes in the cloud.

C. Verify ARM64 / Termux / native Android process feasibility with a constrained `codex --version` / `codex app-server` test locally. Do not merge a fork or request credentials before this probe.

D. Define a versioned, authenticated, per-capability IPC protocol shared between local and remote executors. Preserve streaming notifications, approvals, task correlation and reconciliation of ambiguous outcomes from the uploaded `codex_chat_v6.py`. Never copy its UI-blocking `uncertain` state.

E. Run one real end-to-end experiment in **both directions**: Fire-originated Codex edit and remote-Codex-initiated Fire experiment. Require visual consent, explicit failures, copyable evidence, and a resumable task state.

## Installation/updates

An ordinary Fire OS 7 app cannot silently install arbitrary APKs: per-source permission + system install approval are controlled by Android. Before this revision, our app automatically opened Developer Options whenever `canRequestPackageInstalls()` returned false, causing repetitive trips. Now it opens the system installer after checksum verification and offers **RETRY VERIFIED APK** without redownload. If installation is blocked, grant the permission once to **Fire TV Dev Bridge** (not only Downloader). A Fire TV Settings bug affecting the unknown-app source list was reported on Fire OS 7 in 2026; confirm whether this device has the fix.

For a trusted developer workstation with ADB on the local network, `adb install -r <APK>` updates apps without per-source unknown-app prompts. A remote agent should not expose the ADB bridge publicly.

## Browser failure is not agent failure

The external ChatGPT/Codex website is a convenience bootstrap, not a requirement of the eventual on-device/remote bidirectional control plane. Explicit Silk launch distinguishes a wrong default intent handler from broken rendering or login. A chooser allows testing a second browser. Android WebView generally isn't a safe substitute for Google OAuth, so avoid embedding authenticated ChatGPT WebView as the primary experience.

When reporting browser behavior, distinguish unexpected app opening, empty page, scroll/cursor issues, login loops, and JavaScript compatibility errors. These imply different fixes.
