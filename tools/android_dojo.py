#!/usr/bin/env python3
"""Explore ungraded Android challenges; preserve independent partial progress.

This host-side CLI is a first executable learning ledger, NOT a Fire TV UI.
User notes stay local and are not automatically sent to GitHub or agents.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys
import tempfile

CATALOG = Path(__file__).resolve().parents[1] / "research/android_dojo/catalog.json"
PROGRESS = Path.home() / ".local/share/fire-android-dojo/progress.json"
STATES = ("exploring", "paused", "revisiting", "experiment-working")


def read_catalog(path=CATALOG):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        raise ValueError("Unsupported dojo schema")
    topics = {t["id"] for t in data["topics"]}
    levels = data.get("stack_levels", [])
    level_ids = [level["id"] for level in levels]
    if len(level_ids) != len(set(level_ids)):
        raise ValueError("Duplicate stack level")
    known_levels = set(level_ids)
    ids = set()
    for c in data["challenges"]:
        if c["id"] in ids or not c.get("problem") or not c.get("glossary"):
            raise ValueError("Duplicate or incomplete dojo challenge: " + c["id"])
        if not set(c["topics"]).issubset(topics):
            raise ValueError("Unknown dojo topic: " + c["id"])
        if not set(c.get("stack", [])).issubset(known_levels):
            raise ValueError("Unknown dojo stack level: " + c["id"])
        ids.add(c["id"])
    if any(x not in ids for c in data["challenges"] for x in c["related"]):
        raise ValueError("Missing related dojo challenge")
    return data


def get_challenge(data, challenge_id):
    return next((c for c in data["challenges"] if c["id"] == challenge_id), None)


def read_progress(path):
    if not Path(path).exists():
        return {"schema_version": 1, "challenges": {}}
    d = json.loads(Path(path).read_text(encoding="utf-8"))
    if d.get("schema_version") != 1 or not isinstance(d.get("challenges"), dict):
        raise ValueError("Unsupported progress file")
    return d


def save_progress(path, data):
    """Replace atomically; a failed write must not erase prior experiments."""
    path = Path(path).expanduser()
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix=".progress-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
            f.flush()
            os.fsync(f.fileno())
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def record(data, challenge_id, note=None, state=None, clock=None):
    """No grades, mandatory steps, or 'complete' state."""
    entry = data["challenges"].setdefault(challenge_id, {"state": "exploring", "notes": []})
    if state:
        if state not in STATES:
            raise ValueError("Invalid progress state")
        entry["state"] = state
    if note:
        entry["notes"].append({"at": clock or datetime.now(timezone.utc).isoformat(),
                               "text": note})
    return entry


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--catalog", type=Path, default=CATALOG)
    p.add_argument("--progress", type=Path, default=PROGRESS)
    sub = p.add_subparsers(dest="command", required=True)
    sub.add_parser("validate")
    sub.add_parser("list")
    for action in ("show", "progress", "note", "state"):
        c = sub.add_parser(action)
        c.add_argument("challenge")
        if action == "note":
            c.add_argument("text")
        elif action == "state":
            c.add_argument("value", choices=STATES)
    args = p.parse_args(argv)
    try:
        catalog = read_catalog(args.catalog)
        if args.command == "validate":
            print("OK: " + str(len(catalog["challenges"])) + " challenges")
            return 0
        if args.command == "list":
            for c in catalog["challenges"]:
                print(c["id"] + ": " + c["title"] + " [" + ", ".join(c["topics"]) + "]")
            return 0
        c = get_challenge(catalog, args.challenge)
        if c is None:
            p.error("Unknown challenge: " + args.challenge)
        if args.command == "show":
            print(json.dumps(c, indent=2))
            return 0
        progress = read_progress(args.progress)
        if args.command in ("note", "state"):
            entry = record(progress, c["id"],
                           note=args.text if args.command == "note" else None,
                           state=args.value if args.command == "state" else None)
            save_progress(args.progress, progress)
            print(json.dumps(entry, indent=2))
        else:
            print(json.dumps(progress["challenges"].get(c["id"],
                             {"state": "not_started", "notes": []}), indent=2))
        return 0
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as ex:
        p.exit(1, "Dojo error: " + str(ex) + "\n")


if __name__ == "__main__":
    sys.exit(main())
