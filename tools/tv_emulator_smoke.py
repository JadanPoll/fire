#!/usr/bin/env python3
"""Black-box Android TV smoke test for GitHub Actions or a local ADB emulator.

Only public, already-built APKs, ADB, and the Python standard library are
needed. No Fire TV, developer credentials, account, or network service.
Failures preserve screenshots, UI hierarchy, process state and logcat.
This does NOT emulate Amazon's Fire OS or certify a physical remote.
"""
from pathlib import Path
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET

OUT = Path("tv-emulator-report")
APKS = (
    ("dev.jadan.fire", "app/build/outputs/apk/release/app-release.apk"),
    ("dev.jadan.fire.browser", "browser/build/outputs/apk/release/browser-release.apk"),
    ("dev.jadan.fire.canvas", "canvas/build/outputs/apk/release/canvas-release.apk"),
    ("dev.jadan.fire.signalstudio",
     "Applications/SignalStudio/build/outputs/apk/release/signalstudio-release.apk"),
)
RESULTS = []
COMMANDS = []
PNG = b"\x89PNG\r\n\x1a\n"


def command(args, *, timeout=35, binary=False, allow_failure=False):
    cp = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                        timeout=timeout, check=False)
    out = cp.stdout if binary else cp.stdout.decode("utf-8", "replace")
    err = cp.stderr.decode("utf-8", "replace")
    COMMANDS.append("$ " + " ".join(map(str, args)) + "\n"
                    + ("<binary output: %d bytes>" % len(out) if binary else out[:1800])
                    + "\n" + err[:1000] + "\n")
    if cp.returncode and not allow_failure:
        raise RuntimeError("%s exited %s: %s" % (args, cp.returncode, err[-900:]))
    return out


def adb(*args, **kwargs):
    return command(["adb", *args], **kwargs)


def note(label, status, details=""):
    RESULTS.append((label, status, details))
    print("[%s] %s %s" % (status, label, details), flush=True)


def foreground():
    state = adb("shell", "dumpsys", "window")
    lines = [line.strip() for line in state.splitlines()
             if "mCurrentFocus=" in line or "mFocusedApp=" in line]
    (OUT / "foreground.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return "\n".join(lines)


def check_foreground(component):
    focus = foreground()
    if component not in focus:
        raise AssertionError("Expected foreground %r; got %s" % (component, focus))
    note("Foreground " + component, "PASS")


def snapshot(name, expected=None):
    png = adb("exec-out", "screencap", "-p", timeout=35, binary=True)
    if not png.startswith(PNG) or len(png) < 4096:
        raise AssertionError("Invalid or empty screenshot %s (%s bytes)" % (name, len(png)))
    (OUT / (name + ".png")).write_bytes(png)
    # Android UIAutomator is the established source for accessible text and focus.
    xml_remote = "/sdcard/window.xml"
    result = adb("shell", "uiautomator", "dump", xml_remote,
                 timeout=45, allow_failure=True)
    hierarchy = None
    if "dumped" in result.lower():
        text = adb("shell", "cat", xml_remote, timeout=15)
        (OUT / (name + ".xml")).write_text(text, encoding="utf-8")
        try:
            hierarchy = ET.fromstring(text)
        except ET.ParseError:
            pass
    if expected:
        if hierarchy is None:
            note(name + " UI hierarchy", "UNVERIFIED", "UIAutomator did not provide parseable XML")
        else:
            values = [node.attrib.get("text", "") + " " +
                      node.attrib.get("content-desc", "") for node in hierarchy.iter()]
            if not any(expected in value for value in values):
                raise AssertionError("%s not in %s UI hierarchy" % (expected, name))
            note(name + " contains " + expected, "PASS")
    note("Screenshot " + name, "PASS", "%s bytes" % len(png))
    return hierarchy


def key(name):
    keys = {"UP": "19", "DOWN": "20", "LEFT": "21",
            "RIGHT": "22", "OK": "23", "BACK": "4"}
    adb("shell", "input", "keyevent", keys[name])
    time.sleep(0.5)


CHANNELS = ("Inbox", "Browser", "Playroom", "Compute", "Apps", "Tools")


def selected_channel():
    """Read current native focus, not a guessed startup position."""
    location = "/sdcard/window.xml"
    dump = adb("shell", "uiautomator", "dump", location, timeout=45)
    if "dumped" not in dump.lower():
        raise AssertionError("Cannot inspect Console focus with UIAutomator")
    ui = ET.fromstring(adb("shell", "cat", location, timeout=15))
    for node in ui.iter():
        if node.attrib.get("focused") != "true":
            continue
        description = node.attrib.get("content-desc", "")
        for name in CHANNELS:
            if description.startswith(name + ":"):
                return name
    raise AssertionError("Console focus was not on a recognized channel tile")


def navigate_to_channel(target):
    """Small deterministic intent-driven D-pad navigation over the 2x3 grid."""
    before = selected_channel()
    row, col = divmod(CHANNELS.index(before), 3)
    to_row, to_col = divmod(CHANNELS.index(target), 3)
    if row != to_row:
        key("DOWN" if row < to_row else "UP")
    while col < to_col:
        key("RIGHT")
        col += 1
    while col > to_col:
        key("LEFT")
        col -= 1
    after = selected_channel()
    if after != target:
        raise AssertionError("Remote navigation wanted %s, selected %s" % (target, after))
    note("D-pad navigation %s to %s" % (before, target), "PASS")
    key("OK")


def launch(component):
    result = adb("shell", "am", "start", "-W", "-n", component, timeout=45)
    if "Error:" in result or "Exception" in result:
        raise RuntimeError("Activity launch failed: " + result)
    time.sleep(1.5)


def collect_diagnostics():
    try:
        (OUT / "logcat.txt").write_text(
            adb("logcat", "-d", "-v", "time", timeout=40)[-1_000_000:],
            encoding="utf-8")
    except Exception as exc:
        note("logcat collection", "UNVERIFIED", str(exc))
    try:
        (OUT / "activity-state.txt").write_text(
            adb("shell", "dumpsys", "activity", "activities", timeout=30),
            encoding="utf-8")
    except Exception as exc:
        note("activity state", "UNVERIFIED", str(exc))


def report():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = ["# Android TV emulator smoke test", "",
            "Evidence: Android TV x86 emulator, not Amazon Fire OS.",
            "No account, signing-in, actual voice remote, playback/audio or cloud agent tested.", "",
            "| Check | Result | Detail |", "|---|---|---|"]
    for name, state, detail in RESULTS:
        rows.append("| %s | %s | %s |" % (
            name.replace("|", "/"), state, detail.replace("|", "/").replace("\n", " ")[:180]))
    rows += ["", "Screenshots and accessible UI hierarchies are alongside this report.",
             "The complete ADB command trace and logcat are in this artifact.", ""]
    (OUT / "SUMMARY.md").write_text("\n".join(rows), encoding="utf-8")
    (OUT / "commands.txt").write_text("\n".join(COMMANDS), encoding="utf-8")
    print("\n".join(rows), flush=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    try:
        assert "device" in adb("get-state"), "ADB emulator not ready"
        profile = adb("shell", "getprop", "ro.build.version.sdk").strip()
        abi = adb("shell", "getprop", "ro.product.cpu.abi").strip()
        note("Emulator ready", "PASS", "API %s / %s" % (profile, abi))
        # Stock Android TV images may invoke the Play Store's PackageWarningDialog
        # for ADB installs, leaving the installer waiting forever for a button.
        # The first run proved this (activity-state.txt). Android's own CTS
        # tests use verifier_verify_adb_installs=0 for exactly this reason.
        # Only modify this disposable CI emulator, NEVER a physical Fire TV.
        adb("shell", "settings", "put", "global", "verifier_verify_adb_installs", "0")
        verifier = adb("shell", "settings", "get", "global",
                       "verifier_verify_adb_installs").strip()
        if verifier != "0":
            raise AssertionError("ADB install verifier could not be disabled on test emulator")
        note("CI emulator ADB installer unblocked", "PASS", "verifier_verify_adb_installs=0")
        adb("shell", "svc", "power", "stayon", "usb", allow_failure=True)
        time.sleep(3)

        for index, (package, apk) in enumerate(APKS):
            if not Path(apk).is_file():
                raise FileNotFoundError(apk)
            adb("install", "-r", apk, timeout=65)
            if "package:" not in adb("shell", "pm", "path", package):
                raise AssertionError("APK not installed: " + package)
            note("Install " + package, "PASS")
            # Ensure a useful Console screenshot even if a later APK cannot
            # install: failure evidence is more important than all-or-nothing.
            if index == 0:
                launch("dev.jadan.fire/.ConsoleActivity")
                check_foreground("ConsoleActivity")
                snapshot("01-console-home", "FIRE  /  CONSOLE")

        # Clear pre-test log noise; later crash checks only inspect our run.
        adb("logcat", "-c", allow_failure=True)
        launch("dev.jadan.fire/.ConsoleActivity")
        key("RIGHT")
        home_after_right = snapshot("02-dpad-right")
        if home_after_right is not None:
            focused = [n.attrib for n in home_after_right.iter()
                       if n.attrib.get("focused") == "true"]
            (OUT / "focus-after-right.txt").write_text(repr(focused), encoding="utf-8")
            note("D-pad focus evidence", "RECORDED", repr(focused)[:140])
        key("OK")
        time.sleep(2)
        check_foreground("BrowserActivity")
        snapshot("03-browser-after-ok")

        # Separate installed app must launch without any Fire Console plugin.
        launch("dev.jadan.fire.signalstudio/.SignalActivity")
        check_foreground("SignalActivity")
        snapshot("04-independent-signal-studio")
        key("DOWN")
        key("RIGHT")
        snapshot("05-signal-studio-dpad")
        if (OUT / "04-independent-signal-studio.png").read_bytes() == (
                OUT / "05-signal-studio-dpad.png").read_bytes():
            raise AssertionError("Signal Studio display did not respond to D-pad variation")
        note("Independent app visual changed after D-pad", "PASS")

        # Internal activities are deliberately not exported. Navigate as
        # the user would, through Console D-pad/OK, never through adb am start.
        launch("dev.jadan.fire/.ConsoleActivity")
        navigate_to_channel("Apps")
        check_foreground("AppLibraryActivity")
        library = snapshot("06-installed-app-library", "APPLICATIONS")
        if library is not None:
            items = " ".join(node.attrib.get("text", "") for node in library.iter())
            if "Signal Studio" not in items or "Remote Canvas" not in items:
                raise AssertionError("Installed independent apps missing from Console library")
            note("Independent apps discoverable in library", "PASS",
                 "Signal Studio and Remote Canvas")

        # Back is the normal user route to the existing Console instance.
        # ADB 'am start Console' while AppLibrary is on top merely delivers
        # an intent to the task; it does NOT pop the current activity.
        key("BACK")
        check_foreground("ConsoleActivity")
        navigate_to_channel("Tools")
        check_foreground("MainActivity")
        snapshot("07-tools", "TOOLS  /  MAINTENANCE")

        # Scope crash failures to our processes; a system service crash
        # unrelated to the target app must not produce a false regression.
        log = adb("logcat", "-d", "-v", "time", timeout=35)
        blocks = log.split("FATAL EXCEPTION:")
        target_crashes = [block[:1200] for block in blocks[1:]
                          if any(("Process: " + pkg) in block[:1200]
                                 for pkg, _ in APKS)]
        if target_crashes:
            (OUT / "target-crashes.txt").write_text(
                "\n---\n".join(target_crashes), encoding="utf-8")
            raise AssertionError("FATAL EXCEPTION in tested APK process")
        note("No target-app fatal crash", "PASS")
    except Exception:
        error_lines = [line for line in traceback.format_exc().splitlines() if line.strip()]
        note("Smoke suite", "FAIL", error_lines[-1])
        (OUT / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
        return 1
    finally:
        collect_diagnostics()
        report()
    return 0


if __name__ == "__main__":
    sys.exit(main())
