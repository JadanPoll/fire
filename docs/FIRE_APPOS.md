# Fire AppOS v1 — faithful Manifold Console adaptation

## Investigated source architecture

Source: [Manifold_Console](https://github.com/JadanPoll/Manifold_Console),
especially `AppOS/README.md`, `Contracts/README.md`,
`Contracts/app.example.json`, `Runtime/catalog.py`,
`Runtime/launcher.py`, `Services/SWITCHBOARD.md`,
`Services/RESOURCE_MODEL.md`, `Latent/README.md`,
`Docs/AX.md`, `Docs/REM_AX.md`, and Playroom Studio.

| Manifold responsibility | Fire v1 implementation | Deferred deliberately |
|---|---|---|
| AppOS Shell | Existing Wii-style Fire Console + Apps Leanback list | New window manager / replacing Fire OS Home |
| Runtime | Android's installed-app launcher, process lifecycle, explicit installer | Separate arbitrary-code launcher |
| Contracts | `Applications/*/appos.json` + deterministic validation | Stable cross-OS plugin ABI |
| Lib | Direct stateless `SignalMath` per app | Mandatory service for simple algorithms |
| Playroom/Axioms | Offline Signal Studio native visual experiment | Automatic capability graph/Connect editor |
| Latent | Android's ordinary process lifecycle; no bespoke controller | Unverified CPU/GPU resource budgets |
| Switchboard | Not invoked; single app needs no shared acquisition | Camera/stream broker without actual consumers |
| AX | `AGENTS.md`, small `--json` discovery index, deterministic recipe | Autonomous shell with broad rights |

Manifold explicitly says the Python launcher and supervisor are **Dev mode,
not sandboxes**. Contract declarations do not grant rights. The same
distinction is retained here. A signed APK and manifest do not prove app
trustworthiness, and the repository's public lab signing key is **not an
authenticity root**.

## Minimal contract

`Applications/SignalStudio/appos.json` is the first executable example:

- `schema_version` is 1, `id` is stable, `name` and `version` identify it
- `entry.type` is `android-tv`, `entry.package` identifies the installed
  Android application and `entry.category` is `LEANBACK_LAUNCHER`
- `capabilities` describe requested features; validation uses an explicit
  allowlist, **not** a permission authority
- `ui.category` is a Manifold capability chapter, **not** a dependency
- `build.project` and `build.asset` identify the reviewed Gradle module
  and published APK; ordinary OS launchers own execution
- app storage belongs to Android's actual per-application storage model

For the first reference app, the v1 vocabulary is deliberately tiny:
`signal.synthetic`, `input.dpad`, `display.canvas`. Extend it only when
a concrete new capability is implemented and has an independently verifiable
provider. An unrecognized capability fails validation. The v1 schema is
**repository-local**, not a universal format imposed on every game engine.

### Agent-oriented happy path

1. Read `Applications/AGENTS.md` and the specific manifest.
2. Make changes in the **app's own directory**, respecting its declared
   package identity and native platform capabilities.
3. Run `python3 tools/check_appos_contract.py --json` for a short,
   machine-readable discovery index (no imports, subprocesses or app launch).
4. Run app-local deterministic tests; the existing build workflow creates
   the independent APK and records its SHA-256 and source commit.
5. Review the code/manifest/permissions, then merge to `main`.
6. The existing catalog installs the verified APK through Android's system
   confirmation; the **Apps** channel discovers it automatically using
   `LEANBACK_LAUNCHER`.
7. Run the physical-TV acceptance flow and record observed limitations. If
   an operation fails, stop at the narrow failing boundary without resetting
   browser state or blocking other apps.

New app inclusion currently requires an explicit reviewed Gradle and catalog
registration—**not changes to Fire Console's source code**. We deliberately
avoid unreviewed automatic build discovery, because an app's Gradle script
is executable code even when its JSON manifest is passive data. A future
release registry may automate publication once independent signing and
review policies are implemented.

## Why this is the stopping point

The one-app demonstration succeeds when an agent can change Signal Studio
without touching the Console, the CI builds an independent APK with test
evidence, the catalog verifies its checksum and package identity, and the
physical TV launches it without destabilizing other apps.

It does **not** demonstrate production signing, OS-enforced resource
budgets, remote-process trust, cross-OS native loading, real audio devices,
graph composition or autonomous agent permissions.

The next independently justified workload is remote compute: test a narrowly
scoped queued operation using the existing opt-in Codex executor before
adding a new broker. Keep expensive/exclusive resource routing in
Switchboard, policies in Latent, pure math in Lib, and user-facing capabilities
in Axioms. No IPC or agent daemon is needed for a 360-sample synthetic signal.

## Explicit platform evidence

Android's `CATEGORY_LEANBACK_LAUNCHER` is the documented TV entry point:
https://developer.android.com/training/tv/get-started/create

The Android lifecycle and package manager—not the AppOS manifest—control
foreground/background transitions and access to installed app components.
Manifest parsing alone provides no confinement.
