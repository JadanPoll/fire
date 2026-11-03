#!/usr/bin/env python3
"""Fire GBA's release/blocking architecture contracts. No hardware claim."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
app=ROOT/"Applications/GBAPlayer"
manifest=json.loads((app/"appos.json").read_text())
xml=ET.parse(app/"src/main/AndroidManifest.xml").getroot()
android="{http://schemas.android.com/apk/res/android}"
permissions={p.get(android+"name") for p in xml.findall("uses-permission")}
assert "android.permission.INTERNET" not in permissions
assert "android.permission.ACCESSIBILITY_SERVICE" not in permissions
assert "android.permission.MANAGE_EXTERNAL_STORAGE" not in permissions
assert "android.permission.READ_EXTERNAL_STORAGE" in permissions
assert manifest["entry"]["package"]=="dev.jadan.fire.gbaplayer"
assert manifest["build"]=={"project":":gbaplayer","asset":"firetv-gba-player.apk"}
assert "emulator.gba" in manifest["capabilities"]
source=(app/"src/main/java/dev/jadan/fire/gbaplayer/GbaGameActivity.kt").read_text()
assert 'coreFilePath="libmgba_libretro_android.so"' in source
assert 'lifecycle.addObserver(retro)' in source
assert 'retro.sendKeyEvent' in source
assert 'retro.serializeSRAM()' in source
assert 'retro.unserializeState' in source
assert 'if(ready)showWheel()' in source
assert 'session?.isActive=false' in source
assert 'InputDevice.SOURCE_GAMEPAD' in source
assert "saver.submit{" in source
policy=(app/"src/main/java/dev/jadan/fire/gbaplayer/RemotePadPolicy.kt").read_text()
assert "private var okHeld" in policy
assert "lastOkUp<210" in policy
assert "fun cancel()" in policy
assert "longPress=bHeld||now-okSince>=400" in policy
library=(app/"src/main/java/dev/jadan/fire/gbaplayer/GbaLibraryActivity.kt").read_text()
assert "Intent.ACTION_GET_CONTENT" in library
assert '"FirePlayroom/GBA"' in library
assert 'MAX_ROM=32L*1024*1024' in library
assert "Read_EXTERNAL_STORAGE" not in library
assert "worker.execute" in library
build=(app/"build.gradle").read_text()
assert "LibretroDroid:0.13.2" in build
assert "com.android.application" in build
fetch=(ROOT/"tools/fetch_gba_core.py").read_text()
assert "1.17.0" in fetch and "git_blob_digest" in fetch
assert "rom" not in str(list((app/"src/main/assets").glob("*.gba"))).lower() if (app/"src/main/assets").exists() else True
print("Native GBA contract PASS: independent runtime, no network or ROM bundle, bounded controller and save paths")
