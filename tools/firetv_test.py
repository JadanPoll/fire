#!/usr/bin/env python3
"""Tiny Fire TV remote test helper using stock Python, official adb and scrcpy.

Typical:
  python tools/firetv_test.py --connect 192.168.1.42:5555 doctor
  python tools/firetv_test.py --serial 192.168.1.42:5555 smoke --apk firetv-browser.apk
  python tools/firetv_test.py --serial 192.168.1.42:5555 live

The first ADB connection needs one authorization on the TV itself. Keep ADB
on a trusted LAN: never expose TCP/5555 directly to the internet.
"""
import argparse
import datetime
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

PACKAGE = "dev.jadan.fire.browser"
COMPONENT = PACKAGE + "/.BrowserActivity"
KEYS = {"up":19, "down":20, "left":21, "right":22, "ok":23,
        "back":4, "home":3, "menu":82}
OUTPUT = Path(".fire-tests")

def run(cmd, *, timeout=25, binary=False, check=True):
    try:
        r = subprocess.run(cmd, capture_output=True, timeout=timeout, text=not binary)
    except subprocess.TimeoutExpired:
        raise RuntimeError("Timeout (outcome unknown; not retried): " + " ".join(cmd[:3]))
    except OSError as exc:
        raise RuntimeError(str(exc)) from exc
    if check and r.returncode:
        err = r.stderr if not binary else r.stderr.decode("utf-8", "replace")
        raise RuntimeError("Command failed (" + str(r.returncode) + "): " + str(err)[-1200:])
    return r

def choose_device(adb, serial):
    r = run([adb, "devices"])
    connected = []
    for row in r.stdout.splitlines()[1:]:
        parts = row.split()
        if len(parts) >= 2 and parts[1] == "device": connected.append(parts[0])
        elif len(parts) >= 2 and parts[1] in ("unauthorized", "offline"):
            print("Device requires attention:", row, file=sys.stderr)
    if serial and serial not in connected:
        raise RuntimeError("Requested serial not authorized: " + serial + "\n" + r.stdout)
    if not serial and len(connected) != 1:
        raise RuntimeError("Pass --serial; expected one authorized device, found " + repr(connected))
    return serial or connected[0]

def screenshot(cmd, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    data = run(cmd + ["exec-out", "screencap", "-p"], timeout=25, binary=True).stdout
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("Screenshot response is not PNG")
    dest.write_bytes(data)
    print("Screenshot:", dest.resolve())

def main():
    p = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--adb", default="adb", help="ADB executable, default PATH")
    p.add_argument("--serial", default=os.getenv("FIRE_ADB_SERIAL"))
    p.add_argument("--connect", help="Fire TV LAN IP and port, e.g. 192.168.1.42:5555")
    subs = p.add_subparsers(dest="action", required=True)
    subs.add_parser("doctor")
    install = subs.add_parser("install")
    install.add_argument("apk", type=Path)
    smoke = subs.add_parser("smoke")
    smoke.add_argument("--apk", type=Path)
    smoke.add_argument("--output", type=Path)
    shot = subs.add_parser("shot")
    shot.add_argument("--output", type=Path)
    key = subs.add_parser("key")
    key.add_argument("name", choices=sorted(KEYS))
    subs.add_parser("live")
    args = p.parse_args()
    if not shutil.which(args.adb) and not Path(args.adb).is_file():
        p.error("adb missing. Install official Android SDK platform-tools.")
    if args.connect:
        print(run([args.adb, "connect", args.connect], timeout=25).stdout.strip())
    serial = choose_device(args.adb, args.serial)
    cmd = [args.adb, "-s", serial]
    if args.action == "doctor":
        for label, argv in (
            ("Android", ["shell", "getprop", "ro.build.version.release"]),
            ("API", ["shell", "getprop", "ro.build.version.sdk"]),
            ("ABI", ["shell", "getprop", "ro.product.cpu.abilist"]),
            ("Browser installed", ["shell", "pm", "path", PACKAGE])
        ):
            res = run(cmd + argv, check=False)
            print(label + ": " + (res.stdout.strip() or "not found"))
    elif args.action in ("smoke", "install"):
        apk = args.apk
        if apk is not None:
            if not apk.is_file(): raise RuntimeError("Missing APK: " + str(apk))
            print("Installing", apk)
            print(run(cmd + ["install", "-r", str(apk.resolve())], timeout=110).stdout.strip())
        if args.action == "smoke":
            print(run(cmd + ["shell", "am", "start", "-n", COMPONENT,
                             "--ez", "test_lab", "true"]).stdout.strip())
            time.sleep(2.5)
            screenshot(cmd, args.output or OUTPUT / "smoke.png")
            live = run(cmd + ["shell", "pidof", PACKAGE], check=False)
            if not live.stdout.strip():
                raise RuntimeError("Browser process not running; inspect screenshot.")
            print("Browser process alive:", live.stdout.strip(),
                  "| Screenshot is evidence, not proof of a successful user flow.")
    elif args.action == "shot":
        dest = args.output or OUTPUT / ("firetv-" +
                datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + ".png")
        screenshot(cmd, dest)
    elif args.action == "key":
        run(cmd + ["shell", "input", "keyevent", str(KEYS[args.name])])
        print("Sent:", args.name)
    elif args.action == "live":
        scrcpy = shutil.which("scrcpy")
        if not scrcpy:
            raise RuntimeError("scrcpy unavailable. See github.com/Genymobile/scrcpy")
        sys.exit(subprocess.call([scrcpy, "-s", serial, "--no-audio"]))

if __name__ == "__main__":
    try:
        main()
    except RuntimeError as exc:
        print("Fire TV:", exc, file=sys.stderr)
        sys.exit(1)
