# Architecture and stopping rules

## Goal

A stable development appliance on a Fire TV: the operator requests **build**, **install**, **launch**, or **observe**, sees what is happening, and does not need to understand incidental network/installer variations.

## Dependency and trust boundaries

- GitHub is source-of-truth for code, CI build metadata, and releases.
- App is an unprivileged Android application. It does not assume shell/ADB, accessibility service, device-owner privileges, or silent installation.
- Network interaction begins on an explicit action, via HTTPS. No remote command processing is enabled.
- Updates are on-demand, can be declined, and require Android's package installation confirmation.
- Release checksum guards against accidental corruption, **not compromise of the publishing account**. Signing is PUBLIC LAB ONLY, unsuitable for privileged features.
- Device diagnostics stay on the TV until deliberately shared.

## Invariants

1. User action always has persistent visible status (idle, working, success, error) and a recovery path.
2. No error locks the operator into a dead end: check, retry, and continue.
3. Never silently act on stale update results or install arbitrary URLs.
4. Filter repeat key events for observations only when explicitly testing normalization; retain raw event information for diagnosis.
5. Separate local app-level input behavior from Android global behavior; never claim global debounce capability without a proven privileged path.

## Minimal experiment matrix

| Behavior | Status |
| --- | --- |
| Android API 28 native APK build | CI pending first verified build |
| Launcher tile, D-pad focus | pending physical TV test |
| Local key timing, OK repeat | pending TV test |
| GitHub release lookup and APK download | pending TV test |
| Fire OS package installer confirmation | pending TV test |
| Network ADB from remote desktop | **not assumed** |
| Global key remapping | outside current application privilege |

## Stop/expand criteria

Stage 1 stops when first APK installs and releases update with reliable error displays. No service or backend yet.
Stage 2 stops when a separately built test app appears in a catalog and can be launched/updated with explicit consent.
Stage 3 adds an authenticated telemetry/diagnostics channel only if staging experiments show it is needed. Prefer simple pull/short-lived channels over permanent services.
