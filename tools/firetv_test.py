#!/usr/bin/env python3
"""Tiny Fire TV remote test helper using stock Python, official adb and scrcpy.

Typical:
  python tools/firetv_test.py --connect 192.168.1.42:5555 doctor
  python tools/firetv_test.py --serial 192.168.1.42:5555 smoke --latest
  python tools/firetv_test.py --serial 192.168.1.42:5555 live

The first ADB connection needs one authorization on the TV itself. Keep ADB
on a trusted LAN: never expose TCP/5555 directly to the internet.
"""
import argparse
import hashlib
import json
import urllib.request
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
RELEASE = "https://api.github.com/repos/JadanPoll/fire/releases/tags/firetv-dev-bridge-latest"
ASSET = "firetv-browser.apk"
MAX_SIZE = 64 * 1024 * 1024

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

def latest_browser():
    """Get the published browser using the same GitHub release checksum as Fire."""
    req = urllib.request.Request(RELEASE, headers={
        "User-Agent": "FireTV-Lab-Test/1", "Accept": "application/vnd.github+json"
    })
    try:
        with urllib.request.urlopen(req, timeout=20) as conn:
            meta = conn.read(256 * 1024 + 1)
        if len(meta) > 256 * 1024:
            raise RuntimeError("Release metadata too large")
        release = json.loads(meta)
        expected = None
        for line in release.get("body", "").splitlines():
            if line.startswith("SHA256 " + ASSET + ": "):
                expected = line.split(": ", 1)[1].strip()
        if expected is None or len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected):
            raise RuntimeError("Browser SHA-256 absent/invalid in release")
        url = None
        for asset in release.get("assets", []):
            if asset.get("name") == ASSET:
                url = asset.get("browser_download_url")
        prefix = "https://github.com/JadanPoll/fire/releases/download/firetv-dev-bridge-latest/"
        if not isinstance(url, str) or not url.startswith(prefix):
            raise RuntimeError("Unexpected browser release URL")
        OUTPUT.mkdir(parents=True, exist_ok=True)
        path = OUTPUT / ("firetv-browser-" + expected[:12] + ".apk")
        if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected:
            print("Using cached verified browser:", path)
            return path
        tmp = OUTPUT / (ASSET + ".part")
        digest = hashlib.sha256()
        size = 0
        with urllib.request.urlopen(urllib.request.Request(url,
               headers={"User-Agent": "FireTV-Lab-Test/1"}), timeout=40) as response:
            with tmp.open("wb") as out:
                while True:
                    part = response.read(16384)
                    if not part: break
                    size += len(part)
                    if size > MAX_SIZE: raise RuntimeError("APK exceeds size limit")
                    digest.update(part); out.write(part)
        if digest.hexdigest() != expected:
            tmp.unlink(missing_ok=True)
            raise RuntimeError("Browser APK SHA-256 mismatch; refused to install")
        tmp.replace(path)
        print("Downloaded verified browser:", path, "bytes:", size)
        return path
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        raise RuntimeError("Release download failed: " + str(exc)) from exc


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
    source = smoke.add_mutually_exclusive_group()
    source.add_argument("--apk", type=Path)
    source.add_argument("--latest", action="store_true", help="Download current verified GitHub Release APK")
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
        apk = latest_browser() if args.action == "smoke" and args.latest else args.apk
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
