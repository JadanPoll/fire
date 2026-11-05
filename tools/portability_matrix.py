#!/usr/bin/env python3
"""Offline, evidence-qualified route lookup for Fire TV portability agents.

This is NOT an installer or an assertion that a candidate works on a Fire TV.
Unknown software produces an explicit research stub, never "impossible".
"""
import argparse
import json
from pathlib import Path
import sys

DEFAULT = Path(__file__).resolve().parents[1] / "research/portability/targets.json"
STATES = {"unexamined", "researching", "candidate", "upstream_supported",
          "repo_implemented", "device_verified", "blocked"}
INTERACTIONS = {"unknown", "unexamined", "poor", "candidate", "plausible", "tested"}


def read_matrix(path=DEFAULT):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        raise ValueError("Unsupported portability schema_version")
    names = set()
    for entry in data["entries"]:
        if not entry.get("id") or not entry.get("routes"):
            raise ValueError("Entry requires id and routes")
        within_entry = set()
        for name in [entry["id"], *entry["names"]]:
            key = name.strip().casefold()
            if not key or key in names:
                raise ValueError("Duplicate or empty portability alias: " + name)
            if key in within_entry:
                continue  # canonical ID may also be a same-record alias
            within_entry.add(key)
        names.update(within_entry)
        route_ids = set()
        for route in entry["routes"]:
            if route["id"] in route_ids or route["state"] not in STATES:
                raise ValueError("Duplicate route or invalid state in " + entry["id"])
            route_ids.add(route["id"])
            if not set(route["interaction"].values()).issubset(INTERACTIONS):
                raise ValueError("Invalid interaction assessment in " + route["id"])
            if route["state"] in {"upstream_supported", "repo_implemented",
                                  "device_verified"} and not route["evidence"]:
                raise ValueError("Supported/implemented/verified route needs evidence")
            if route["state"] == "device_verified" and not any(
                x.get("kind") == "device_trace" for x in route["evidence"]
            ):
                raise ValueError("Device verification requires an actual device_trace")
    return data


def lookup(data, name):
    key = name.strip().casefold()
    for entry in data["entries"]:
        if key in {s.casefold() for s in [entry["id"], *entry["names"]]}:
            return entry
    return None


def unassessed(name):
    return {
        "query": name, "state": "unassessed",
        "meaning": "No record, NOT evidence of incompatibility.",
        "research_stub": {
            "id": "", "names": [name], "intent": "Clarify intended experience.",
            "routes": [], "next_step": "Inspect software requirements and user controls; compare existing execution paths."
        }
    }


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--database", type=Path, default=DEFAULT)
    sub = p.add_subparsers(dest="command", required=True)
    sub.add_parser("validate")
    sub.add_parser("list")
    cmd = sub.add_parser("lookup")
    cmd.add_argument("name", help="Exact id or alias (no fabricated fuzzy match)")
    cmd.add_argument("--json", action="store_true")
    args = p.parse_args(argv)
    try:
        data = read_matrix(args.database)
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as ex:
        p.exit(1, "Invalid portability database: " + str(ex) + "\n")
    if args.command == "validate":
        print("OK: " + str(len(data["entries"])) + " entries, evidence and aliases checked")
    elif args.command == "list":
        for e in data["entries"]:
            print(e["id"] + " — " + e["intent"])
    else:
        e = lookup(data, args.name)
        if not e:
            print(json.dumps(unassessed(args.name), indent=2))
            return 2
        if args.json:
            print(json.dumps(e, indent=2))
        else:
            print(e["id"] + ": " + e["intent"])
            for r in e["routes"]:
                controls = ", ".join(k + "=" + v for k, v in r["interaction"].items())
                print("  - " + r["id"] + " [" + r["state"] + "] " + r["execution"])
                print("    controls: " + controls)
                print("    next: " + r["next_check"])
                if r["blockers"]:
                    print("    blockers: " + "; ".join(r["blockers"]))
                print("    evidence: " + str(len(r["evidence"])) + " reference(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
