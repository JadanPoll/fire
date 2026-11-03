# Fire GBA native player — source-backed execution architecture

Fire GBA is the first more ambitious independently installed Fire AppOS
application. Instead of reimplementing the GBA CPU, memory map, PPU, APU and
cartridge saves, it composes two proven upstream implementations:
`LibretroDroid` for native Android rendering/audio/input/lifecycle and
`mGBA` for actual GBA emulation.

The separate Lemuroid fallback remains under Playroom. Its upstream Fire
Stick-specific patch #1171 is still open at the time of this work; the Fire
GBA player addresses input inside its **own app** instead of trying to
inject keys into another Android process.

**Safety distinction:** the Fire AppOS manifest is metadata, not an
enforcement mechanism. Android's process, package, permissions, and
lifecycle boundaries are the concrete runtime controls. Fire GBA requests
only READ_EXTERNAL_STORAGE on Android 9 for explicit imports. It has no
INTERNET, accessibility, ADB or root permissions, and distributes no
copyrighted commercial game binaries.

Integration:
```
Manifold AppOS contract -> Android Leanback launcher
Fire Playroom local ROM importer -> /Download/FirePlayroom/GBA
Fire GBA private ROM library -> libretro mGBA core -> GLRetroView and audio
Fire remote events -> RemotePadPolicy -> GLRetroView.sendKeyEvent()
Game SRAM / snapshot -> app-private per-ROM saves
```

The native binaries are pulled at CI time from the **known upstream
1.17.0 Git tree** with exact content IDs. A build fails on changed
artifacts or unsupported ABIs. The app is independently published as
`firetv-gba-player.apk` and is accessible through the existing catalog;
no changes to Console's launcher are needed to install it.

The first actual TV gameplay test remains unverified until installation
on the physical device. Source correctness and emulator behavior are
separate forms of evidence.

References:
- https://github.com/Swordfish90/LibretroDroid/tree/0.13.2
- https://github.com/Swordfish90/LibretroDroid/blob/0.13.2/libretrodroid/src/main/cpp/input.cpp
- https://github.com/Swordfish90/LemuroidCores/tree/1.17.0/lemuroid_core_mgba
- https://github.com/mgba-emu/mgba
- https://docs.libretro.com/library/mgba/
- https://developer.amazon.com/docs/fire-tv/controller-behavior-guidelines.html
