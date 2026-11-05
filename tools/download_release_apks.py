#!/usr/bin/env python3
"""Reuse verified public GitHub release APKs instead of rebuilding to test them.

No tokens, new account, Gradle, SDK or build host. URL and SHA-256 are
validated against the same immutable release metadata response.
"""
import hashlib
import json
from pathlib import Path
import re
from urllib.request import Request, urlopen

TAG = "firetv-dev-bridge-latest"
REPO = "JadanPoll/fire"
ASSETS = {
    "firetv-dev-bridge.apk": "app/build/outputs/apk/release/app-release.apk",
    "firetv-browser.apk": "browser/build/outputs/apk/release/browser-release.apk",
    "firetv-remote-canvas.apk": "canvas/build/outputs/apk/release/canvas-release.apk",
    "firetv-signal-studio.apk": (
        "Applications/SignalStudio/build/outputs/apk/release/signalstudio-release.apk"),
}
MAX_METADATA = 256 * 1024
MAX_APK = 64 * 1024 * 1024


def open_public(url):
    return urlopen(Request(url, headers={"User-Agent": "Fire-TV-CI/1",
                                        "Accept": "application/vnd.github+json"}),
                   timeout=45)


def main():
    endpoint = f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}"
    with open_public(endpoint) as conn:
        data = conn.read(MAX_METADATA + 1)
    if len(data) > MAX_METADATA:
        raise RuntimeError("Release metadata unexpectedly large")
    release = json.loads(data)
    body = release.get("body", "")
    paths = {asset["name"]: asset.get("browser_download_url")
             for asset in release.get("assets", [])}
    for asset, destination in ASSETS.items():
        match = re.search(
            r"(?m)^SHA256 " + re.escape(asset) + r": ([0-9a-f]{64})\s*$", body)
        if not match:
            raise RuntimeError("Missing release checksum for " + asset)
        expected = match.group(1)
        url = paths.get(asset)
        prefix = f"https://github.com/{REPO}/releases/download/{TAG}/"
        if url != prefix + asset:
            raise RuntimeError("Unexpected asset address " + str(url))
        target = Path(destination)
        target.parent.mkdir(parents=True, exist_ok=True)
        tmp = target.with_suffix(target.suffix + ".part")
        digest = hashlib.sha256()
        size = 0
        try:
            with open_public(url) as conn, tmp.open("wb") as output:
                while True:
                    part = conn.read(65536)
                    if not part:
                        break
                    size += len(part)
                    if size > MAX_APK:
                        raise RuntimeError("APK exceeds limit: " + asset)
                    digest.update(part)
                    output.write(part)
            if digest.hexdigest() != expected:
                raise RuntimeError("Release checksum mismatch: " + asset)
            tmp.replace(target)
        finally:
            if tmp.exists():
                tmp.unlink()
        print(f"VERIFIED {asset}: {size} bytes, sha256={expected}", flush=True)


if __name__ == "__main__":
    main()
