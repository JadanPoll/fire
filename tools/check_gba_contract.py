#!/usr/bin/env python3
"""Source contract for the curated GBA integration, not physical gameplay proof."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
src=(ROOT/"app/src/main/java/dev/jadan/fire/RetroRoomActivity.java").read_text()
hub=(ROOT/"app/src/main/java/dev/jadan/fire/PlayroomHubActivity.java").read_text()
manifest=ET.parse(ROOT/"app/src/main/AndroidManifest.xml")
android="{http://schemas.android.com/apk/res/android}"
activities={a.get(android+"name"):a for a in manifest.findall(".//activity")}

assert ".RetroRoomActivity" in activities
assert activities[".RetroRoomActivity"].get(android+"exported")=="false"
assert "RetroRoomActivity.class" in hub, "GBA must be immediately playable from the visible game grid"
assert "com.swordfish.lemuroid" in src
assert "Swordfish90/Lemuroid/releases/download/1.17.0/" in src
assert re.search(r'EMULATOR_SHA256\s*=\s*"([a-f0-9]{64})"',src)
assert "getLeanbackLaunchIntentForPackage(EMULATOR_PACKAGE)" in src
assert "getPackageArchiveInfo" in src and "verifyApk" in src
assert "FLAG_GRANT_READ_URI_PERMISSION" in src
assert 'Intent.ACTION_INSTALL_PACKAGE' in src
assert 'Intent.ACTION_GET_CONTENT' in src
assert 'Manifest.permission.WRITE_EXTERNAL_STORAGE' in src
assert 'MAX_ROM_BYTES=32L*1024*1024' in src
assert "getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)" in src
assert "new File(new File(download,\"FirePlayroom\"),\"GBA\")" in src
assert "installEmulator()" in src and "cancelTransfer()" in src
assert 'finish).requestFocus()' in src
assert not any(bad in src for bad in ("QUERY_ALL_PACKAGES","MANAGE_EXTERNAL_STORAGE","https://roms","Pokemon FireRed.gba"))
print("GBA Game Room contract PASS: pinned emulator, explicit install, local ROM transfer, independent Back")
