#!/usr/bin/env python3
"""Critical UI invariants that do not require a physical Fire TV to inspect.

This is a source contract, not a substitute for a real keyboard/TV test.
"""
from pathlib import Path

repo=Path(__file__).resolve().parents[1]
src=repo/"app/src/main/java/dev/jadan/fire"
terminal=(src/"TerminalActivity.java").read_text(encoding="utf-8")
common=(src/"TvUi.java").read_text(encoding="utf-8")

assert "SOFT_INPUT_STATE_ALWAYS_HIDDEN" in terminal
assert "SOFT_INPUT_ADJUST_RESIZE" in terminal
assert "setShowSoftInputOnFocus(false)" in terminal
# Explicit TYPE must re-enable the input connection before requesting the IME.
assert "entry.setShowSoftInputOnFocus(true);" in terminal
open_method=terminal.split("private void openKeyboard()")[1].split("private void hideKeyboard()")[0]
assert open_method.index("setShowSoftInputOnFocus(true)") < open_method.index("requestFocus()") < open_method.index("showSoftInput(entry,0)")
hide_method=terminal.split("private void hideKeyboard()")[1].split("private void stop()")[0]
assert "entry.setShowSoftInputOnFocus(false)" in hide_method
assert "private void hideKeyboard()" in terminal
assert 'button(actions,"HIDE",this::hideKeyboard)' in terminal
assert "type.requestFocus();" in terminal
assert "entry.setOnEditorActionListener" in terminal
assert "outputDeck" in terminal and terminal.index("deck.addView(actions)") < terminal.index("outputDeck=")
assert "outputScroll" in terminal
assert 'new ProcessBuilder("/system/bin/sh","-c",command)' in terminal
assert "requestFocus();" not in terminal.split("private void openKeyboard()")[0].split("type.requestFocus();")[1], "No hidden field focus on entry"

assert "setOnFocusChangeListener" in common
assert "static LinearLayout actionRow" in common
assert "static Button actionInRow" in common
for filename in ("MainActivity.java","CatalogActivity.java","CodexDeskActivity.java",
                 "VoiceProbeActivity.java","GameShelfActivity.java","AppLibraryActivity.java",
                 "InputLabActivity.java"):
    text=(src/filename).read_text(encoding="utf-8")
    assert "TvUi." in text, f"{filename} lost shared TV theme"

print("Fire TV UI contract PASS: local terminal starts without keyboard, actions above output, all classic channels share focus theme")
