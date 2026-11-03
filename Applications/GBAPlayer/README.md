# Fire GBA — an actual native GBA execution surface

A stand-alone `dev.jadan.fire.gbaplayer` Android TV APK, grounded in
[Manifold AppOS](https://github.com/JadanPoll/Manifold_Console) principles:
capability declaration, direct native computation, bounded application
storage, no generic agent daemon and independently launchable lifecycle.

## Native engine: reuse verified implementations

- [LibretroDroid 0.13.2](https://github.com/Swordfish90/LibretroDroid/tree/0.13.2)
  (GPL-3.0; already the backend used by Lemuroid).
- [mGBA libretro core from LemuroidCores 1.17.0](https://github.com/Swordfish90/LemuroidCores/tree/1.17.0/lemuroid_core_mgba)
  (mGBA MPL 2.0), for both ARMv7 and ARM64.
- `tools/fetch_gba_core.py` verifies **upstream Git blob identity** and
  expected byte length before Gradle packages the native binaries.
- Fire GBA includes the core in its APK. Unlike the free-dynamic Lemuroid
  alternative it needs **no first-launch core download**.
- It has **no INTERNET permission**, includes **no ROM/BIOS**, and does not
  import code from the Fire Console application.

This project is GPL-3.0-or-later as a frontend linked with LibretroDroid.
Original code for Fire GBA is publicly available in this repository. See
the [GNU GPL text](https://www.gnu.org/licenses/gpl-3.0.txt) and
[LibretroDroid LICENSE](https://github.com/Swordfish90/LibretroDroid/blob/0.13.2/LICENSE).
mGBA uses MPL 2.0; upstream source is linked above. Do not distribute
Nintendo files as part of any build.

## Operator flow

1. Open Fire Console → Playroom → GBA Game Room → Install / update Fire GBA.
   Confirm in Android's package installer.
2. Transfer a self-provided `.gba` into
   `/sdcard/Download/FirePlayroom/GBA/` using the Console importer or
   trusted LAN ADB. No ROMs are downloaded from websites by our app.
3. Open Fire GBA → Import from Fire Playroom Downloads, grant **read-only**
   file permission, choose file. Alternatively use its system file picker.
4. A copy enters the Fire GBA app-private ROM library; select PLAY.
5. Emulator runs the **mGBA native core**. The default Fire remote scheme:
   D-pad = directions, OK short = GBA A, OK hold (≥400ms) = GBA B.
   BACK or MENU = always-available **control wheel**.
   The wheel gives B/Start/Select/L/R, save RAM, save snapshot, restore
   snapshot (confirmed), and exit.
6. Media keys, when Fire OS delivers them: Play/Pause = Start,
   Rewind = B, Fast Forward = Select. Bluetooth gamepad buttons are
   forwarded directly to the core. Home is always system-owned.

A normal quick OK press produces a 110ms in-core key pulse so it cannot
vanish between render frames. A long press is identified from actual
duration/repeat data, not an ungrounded timeout that would falsely fire on
a missing key-up. `RemotePadPolicyTest` exercises duplicates, held
directions and release/cancellation.

## Game saves and operational guarantees

- ROMs copied to this app's private `files/roms/`; filenames normalized
  and size bounded. No overwrite of previous library items.
- Saves keyed by a truncated SHA-256 of the ROM content, independent of
  visible filename. Persistent game SRAM is restored at game start.
- Control wheel has explicit **SAVE GAME SRAM** and **SAVE SNAPSHOT**.
  Restoring a snapshot requires separate confirmation.
- Save serialization runs on a daemon worker, not the Android UI thread:
  libretro's GL queue may otherwise block focus/Back if a renderer stalls.
  A save is reported **only after** bytes are written. The ordinary
  game's native save action is recommended before exiting.
- `BACK`, system `HOME`, and `EXIT TO LIBRARY` remain independent.
- No app attempts global input injection, Fire system keyboard replacement,
  privileged file access, remote command execution or automatic browser
  session sharing. A misconfigured key cannot steal controls outside this
  Activity.
- On actual renderer failure, native GL/libretro callback bugs can still
  terminate the app. Android's process boundary should leave Console alive;
  this must be verified on-device.

## Required device validation

CI proves source/Gradle compilation and core-artifact pinning, but not
runtime compatibility. On the actual Fire OS 7 unit, validate:
- game selection, core initialization, audio/video, no crashes
- short OK A, long OK B, duplicates, D-pad holds, Play/Pause routing
- BACK opens the wheel, menu item performs B/Start/Select
- cartridge SRAM save, exit/restart, loaded progress; save-state restore
- repeated launch/exit without corrupting unrelated Console/Browser data
- compatibility and performance with a FireRed-class user-owned GBA ROM

The explicit stopping point is **real gameplay plus persistent saves using
only the Fire remote**, not merely a successful Android build.
