# Independent application authoring — Fire AppOS v1

Read `docs/FIRE_APPOS.md` and `Applications/SignalStudio/appos.json`
before changing any app packaging or lifecycle behavior.

Prefer normal platform-native projects. **Do not import Fire Console's
Activity classes** or require an in-process plugin interface. Every app
has a stable Android package identity and its own source directory.

Your app provides:
- `appos.json` with a v1, validated identity, launcher and capabilities
- Android TV `MAIN` + `LEANBACK_LAUNCHER` Activity, independently installable
- narrow deterministic tests and a D-pad / Back physical acceptance sequence
- build output and exact release-asset name, with SHA-256 and source revision

Apps may use Java, Kotlin, C/C++ through the NDK, or other Android-compatible
engines, provided the resulting Android TV package meets this contract.

Capability declarations **do not grant rights**. Use Android permissions and
the user's explicit consent; do not add a shell command, ADB endpoint,
arbitrary remote execution, global key remapping or automatic downloads to
the manifest. Treat external agent output as untrusted until reviewed.

`tools/check_appos_contract.py --json` gives a small, inspectable AppOS
discovery index without importing or executing application code. Do not
send the entire repository to an agent when its scope is one capability.

Prefer direct Lib-style calls for cheap work. Consider Switchboard-like
shared ownership only for demonstrably expensive or exclusive resources.
Latent-like resource enforcement needs actual OS facilities and independent
evidence, not a declarative flag.

No app may hold Home/Back, the on-screen keyboard, browser state or
installer hostage. Preserve user exit paths and saved data. If you need
changes outside your app directory, state precisely why.
