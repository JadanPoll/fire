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
assert 'TvUi.header(this,"COMPUTE")' in compute
# The day-to-day task path is directly visible; pairing remains a contextual action.
assert 'SEND TO AGENTS' in compute and 'this::sendInboxTask' in compute
assert 'CHECK AGENT RESPONSE' in compute and 'this::checkInboxTask' in compute
assert 'VIEW RESULT ON GITHUB' in compute
assert 'ComputeInboxClient.submit(task)' in compute
assert 'ComputeInboxClient.latestReply(response,inboxSender,current)' in compute
assert 'inbox_request_id' in compute and 'inbox_sender' in compute
assert 'https://fire-inbox.vercel.app/compute.html' in compute
# REM week-two view: the game is reachable from the first Playroom screen.
hub=(source/"PlayroomHubActivity.java").read_text()
assert "PlayroomHubActivity.class" in home
assert 'new Destination("SNAKE"' in hub and "SnakeActivity.class" in hub
assert "ScrollView" not in hub, "Playable primary game shelf must not need scrolling"
assert hub.index('new Destination("SNAKE"') < hub.index('new Destination("EXPLORE"')
assert "pinned_games_v1" in hub and "GameShelfActivity.class" in hub
# Recoverable but less-frequent features stay reachable, not top-priority menus.
assert "private void showJobDetails()" in compute and "CodexExecutorClient.cancel" in compute
app_library=(source/"AppLibraryActivity.java").read_text()
assert "firstApp.requestFocus()" in app_library
assert app_library.index('int limit=Math.min(entries.size(),64);') < app_library.index('Button manager=TvUi.action(this,root,')
assert 'OTHER TOOLS  /  DIAGNOSTICS' in tools
assert "OPEN CODEX" in compute and "this::openCodexCloud" in compute
assert "MORE  /  COMPUTE OPTIONS" in compute and "private void advanced()" in compute
assert "computePrefs.edit().putString(\"draft\"" in compute
assert "LivePairing.token" in compute
assert 'putString("token"' not in compute
assert "setShowSoftInputOnFocus(true);" in compute
assert "setShowSoftInputOnFocus(false);" in compute
# Inbox form is now a compatibility fallback, not the only transport.
browser=(repo/"browser/src/main/java/dev/jadan/fire/browser/BrowserActivity.java").read_text()
assert "fetch(url,options)" in browser and "var options=usePost?" in browser
assert "method:'POST'" in browser and "method:'GET'" in browser
assert 'private void submitInboxForm(WebView target,String value)' in browser
assert 'String(m.id)===id||String(o.id)===id' in browser
assert 'Draft retained.' in browser
assert 'final boolean useJsonPost=value.length()>1200;' in browser
assert 'fire-inbox.vercel.app' in browser and 'https://fire-inbox.vercel.app/' in home
# Repeated Fire TV Inbox messages must have a recognizable stable sender.
# Native SharedPreferences preserve the address independently of WebView cookies
# and page-local storage; the Site displays and permits renaming its public label.
assert 'INBOX_SENDER_PREF = "fire_inbox_sender_v1"' in browser
assert 'prefs.getString(INBOX_SENDER_PREF' in browser
assert 'prefs.edit().putString(INBOX_SENDER_PREF,sender).apply()' in browser
assert '"device:fire-tv-"+java.util.UUID.randomUUID()' in browser
assert 'appendQueryParameter("from",inboxSenderId())' in browser
assert 'inboxUrlWithSender(url)' in browser
assert 'from_label:senderLabel' in browser
assert "'&from='+encodeURIComponent(sender)+'&from_label='+encodeURIComponent(senderLabel)" in browser
# Independent native app can opt into a single adapter, no Console dependency.
assert "TvRemoteAdapter" in signal
assert "import dev.jadan.fire.remote" in signal
assert "com.android.library" in (repo/"AppOS/Remote/build.gradle").read_text()
assert "project(':remoteinput')" in (repo/"Applications/SignalStudio/build.gradle").read_text()
assert "include ':remoteinput'" in settings
assert "KEYCODE_HOME" not in remote and "KEYCODE_BACK" not in remote
assert "void reset" in remote
print("Console consolidation PASS: six channels, one updater, Compute host/cloud, optional in-process remote normalization")
