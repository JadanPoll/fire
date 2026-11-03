#!/usr/bin/env python3
"""Cheap, source-backed checks for the new Console factorization.

Not a physical TV test. Avoid asserting implementation trivia beyond
important user-visible routing/security invariants.
"""
from pathlib import Path
import json
repo=Path(__file__).resolve().parents[1]
source=repo/"app/src/main/java/dev/jadan/fire"
home=(source/"ConsoleActivity.java").read_text()
tools=(source/"MainActivity.java").read_text()
compute=(source/"CodexDeskActivity.java").read_text()
signal=(repo/"Applications/SignalStudio/src/main/java/dev/jadan/fire/signalstudio/SignalActivity.java").read_text()
remote=(repo/"AppOS/Remote/src/main/java/dev/jadan/fire/remote/RemotePolicy.java").read_text()
settings=(repo/"settings.gradle").read_text()
names=["Inbox","Browser","Playroom","Compute","Apps","Tools"]
assert home.count("new Channel(")==6
assert all('new Channel("'+name+'"' in home for name in names)
assert 'case 3: // Compute: cloud with remote server/terminal' in home
assert 'new Channel("Canvas"' not in home
assert 'new Channel("Voice"' not in home
assert "CatalogActivity.class" in tools
assert "InputLabActivity.class" in tools
assert "VoiceProbeActivity.class" in tools
assert "TerminalActivity.class" in tools
# The redundant updater and key log were deleted—not just hidden.
assert "checkRelease()" not in tools
assert "downloadRelease()" not in tools
assert "keyEvents" not in tools
assert "HttpURLConnection" not in tools
# Compute reuses existing HTTPS executor and cloud path, not a second backend.
assert "CodexExecutorClient.submit" in compute
assert "CodexExecutorClient.recover" in compute
assert "CodexExecutorClient.cancel" in compute
assert "COMPUTE  /  OFFSHORE WORK" in compute
assert "COPY DRAFT + CODEX" in compute
assert "ADVANCED / DIAGNOSTICS" in compute
assert "computePrefs.edit().putString(\"draft\"" in compute
assert "LivePairing.token" in compute
assert 'putString("token"' not in compute
assert "setShowSoftInputOnFocus(true);" in compute
assert "setShowSoftInputOnFocus(false);" in compute
# Independent native app can opt into a single adapter, no Console dependency.
assert "TvRemoteAdapter" in signal
assert "import dev.jadan.fire.remote" in signal
assert "com.android.library" in (repo/"AppOS/Remote/build.gradle").read_text()
assert "project(':remoteinput')" in (repo/"Applications/SignalStudio/build.gradle").read_text()
assert "include ':remoteinput'" in settings
assert "KEYCODE_HOME" not in remote and "KEYCODE_BACK" not in remote
assert "void reset" in remote
print("Console consolidation PASS: six channels, one updater, Compute host/cloud, optional in-process remote normalization")
