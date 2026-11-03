# Playroom GBA — a real FireRed-class execution path

## Goal and bounded result

**An existing mGBA core actually runs the GBA software**, not a recreated
mock RPG. Fire Console's Playroom adds a GBA Game Room that installs the
official open-source Android TV emulator [Lemuroid](https://github.com/Swordfish90/Lemuroid)
and imports a user-supplied `.gba` ROM.

The repository includes **no copyrighted Nintendo game, firmware or ROM**.
A user provides their own lawfully obtained copy. The emulator may use
its open-source HLE BIOS; original firmware is optional for mGBA.

This release is an **installation/ROM-delivery bridge**, not a new emulator
core or a claim that the Fire remote is already mapped correctly.

## Proven upstream, fixed identity

- Official Lemuroid release `1.17.0`:
  `lemuroid-app-free-dynamic-release.apk`
- Download from the fixed, official
  `https://github.com/Swordfish90/Lemuroid/releases/download/1.17.0/...`
- APK SHA-256 (GitHub Release's published digest):
  `d44cfa0cdc3e43fd91df97f625938366ccef9793b06a80b9e6fad97a6cd88e51`
- Package identity: `com.swordfish.lemuroid`, version `1.17.0`.
- Android installer must present a confirmation. No unattended installation,
  package uninstall, background download, custom privileged permissions, or
  ROM retrieval from the web is implemented.

Important: the official `free-dynamic` variant does **not** bundle emulation
cores. It downloads the mGBA native library from the upstream
`Swordfish90/LemuroidCores` tree when needed; that requires network
access once. Source:
`lemuroid-app-ext-free/.../CoreUpdaterImpl.kt`. The pinned binary supports
ARM `armeabi-v7a` and `arm64-v8a`, and mGBA is GPL-compatible with this
frontend's project distribution policies. **Do not claim fully offline
startup on first play.**

## TV path

1. Update Fire Console.
2. Open **Playroom → OPEN GBA GAME ROOM**.
3. Select **INSTALL VERIFIED LEMUROID** and approve Android's installer.
4. Transfer your own `.gba` file:
   - **IMPORT .GBA FROM DEVICE** uses the Android file picker when Fire OS
     provides one; imports to shared Downloads after explicit storage grant.
   - Or, from an authorized LAN-connected ADB host:
     `adb shell mkdir -p /sdcard/Download/FirePlayroom/GBA`
     and
     `adb push YOUR_GAME.gba /sdcard/Download/FirePlayroom/GBA/`.
5. Select **OPEN LEMUROID TV**. In Lemuroid choose the local
   `Download/FirePlayroom/GBA` folder, scan, and launch the GBA title.
6. Allow the mGBA core download if necessary, then check video, audio,
   directional input, A/B/Start/Select, and save game/resume.
7. Press Back to leave. Existing pinned web games and browser sessions remain
   untouched.

### Controls: *not* considered solved yet

Lemuroid's upstream Fire Stick-specific pull request
[#1171](https://github.com/Swordfish90/Lemuroid/pull/1171)
(2026-08-25) is **open**, not in official v1.17.0. It reports fixes for
Fire OS ROM-folder access and using the original Fire remote as a gamepad.
At API 28 Fire OS 7, the fallback storage picker and READ_EXTERNAL_STORAGE
are more plausible than on API 30; nevertheless actual operation remains
device-dependent. The standard Fire remote also lacks the dedicated A/B,
Start/Select and shoulder buttons available on a physical gamepad. A
Bluetooth/USB gamepad is the dependable initial path.

The Fire Browser's named key-wheel cannot inject key events into Lemuroid's
**separate Android process**. Do not misrepresent it as a universal
keymapper. Building a first-class remote-only GBA frontend would require
wrapping an existing libretro core **inside our own app**, where input and
emulation share one process, or shipping a reviewed Lemuroid fork with the
Fire remote patch. That is the next justified control milestone.

### Safety and limits

- APK download is bounded, streaming, TLS, SHA-256 verified and checks its
  archive's package/version. A temporary file is removed on failure.
- No APK is installed unless the user explicitly confirms Android's prompt.
- ROM transfer accepts only `.gba`, at most 32 MiB, copies a selected local
  content URI to a sanitized unique filename (no overwrite) and never uploads
  it. A failed import retains the original.
- File picker missing? The app surfaces the ADB fallback, not a blocking
  dialog/retry loop.
- Close/Back leaves Playroom; transfer work is canceled on destruction.
- Android's installed packages, private app data and existing game saves are
  never cleared by this code.
- The Console still uses an intentionally public development signing key.
  That identity is **not** a production permission boundary.

## Physical acceptance — essential before claiming success

With the TV and the user's own GBA file available, record:

- Official APK passes native Android install and launcher on Fire OS 7
- Emulator loads mGBA core for actual TV ABI
- Local game folder is visible and .gba is recognized
- Game shows real frames and audio; A/B/Start/Select respond
- Game save survives leaving/restarting
- HOME/BACK/Console remain reachable, even with emulator failure
- Games in unrelated apps retain their data and sessions

CI checks APK build and contract wiring only. This document intentionally
does **not** claim these on-device cases have passed.

Primary references:
- https://github.com/Swordfish90/Lemuroid
- https://github.com/Swordfish90/Lemuroid/releases/tag/1.17.0
- https://github.com/Swordfish90/Lemuroid/pull/1171
- https://github.com/Swordfish90/LemuroidCores/tree/1.17.0/lemuroid_core_mgba
- https://docs.libretro.com/library/mgba/
- https://developer.amazon.com/docs/fire-tv/fire-os-7.html
