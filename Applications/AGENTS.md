# Agent app integration — minimum viable contract

**Do not start by studying Fire Console internals.** Build the most natural
Android TV application for the task. An independent agent can work in another
repository and deliver an ordinary installable APK.

## Native pass-through (default)

- Declare a `MAIN` + `LEANBACK_LAUNCHER` Activity and normal app icon.
- Use your preferred engine: Java/Kotlin, Android GameActivity/NDK C/C++,
  Unity, Godot, libretro, or Android-compatible native software.
- Handle input/rendering/audio/storage **directly** through Android.
- Ship a signed APK through the usual Android installation process.
- Fire Console's Apps page discovers the APK from the OS registry—no Fire
  Console source modification and **no mandatory AppOS manifest or SDK**.

## Remote adapter (only if needed)

The optional `AppOS/Remote` module normalizes Fire remote KeyEvents inside
your own app. Use it if you need stable D-pad/OK/long-OK controls instead of
duplicating Fire-specific input work. See
[`AppOS/Remote/README.md`](../AppOS/Remote/README.md) and
`Applications/SignalStudio/SignalActivity.java` for the exact wiring.
If your engine already handles Fire input, don't add the adapter.

## Optional repository-local publishing

Apps maintained in this repo can provide `appos.json` plus a reviewed
Gradle module and CI release asset. `tools/check_appos_contract.py --json`
validates those *curated* apps. This schema is not a universal ABI and
does not authorize any capability or permission.

When adding an app, keep it in its own source boundary, test the cheapest
realistic flow, preserve Back/Home exits, and avoid requesting privileges
unrelated to its purpose. Explain any change outside the app's directory.

**Stopping point:** a new agent-created APK can be installed, found in
Apps and launched on TV; normalized input works if explicitly opted in.
Do not add a daemon, broker, custom launcher or generic cross-app service
before a demonstrable workload needs one.
