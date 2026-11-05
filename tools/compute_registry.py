#!/usr/bin/env python3
"""Small public-metadata capability registry: physical, virtual, or GitHub.

An untrusted GET announcement is a *claim*, not a trusted device identity or
authority to execute a workflow. Server runs locally unless --bind is changed.
No command invocation, tokens, secret storage, or remote execution endpoint.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import sys
import time
from urllib.parse import parse_qs, urlsplit

DEFAULT = Path(__file__).resolve().parents[1] / "research/compute/devices.json"
IDENTIFIER = re.compile(r"^[a-z][a-z0-9._-]{0,63}$")
KINDS = {"physical", "virtual_agent", "virtual_platform", "service"}
TTL_SECONDS = 300


def load_catalog(path=DEFAULT):
    doc = json.loads(Path(path).read_text(encoding="utf-8"))
    if doc.get("schema_version") != 1 or not isinstance(doc.get("devices"), list):
        raise ValueError("Invalid capability catalog")
    seen = set()
    for d in doc["devices"]:
        identifier = d["id"]
        if not IDENTIFIER.fullmatch(identifier) or identifier in seen:
            raise ValueError("Duplicate or invalid device ID: " + identifier)
        seen.add(identifier)
        if d["kind"] not in KINDS or not d["name"].strip():
            raise ValueError("Invalid device profile")
        offers = set()
        for offer in d["offers"]:
            o = offer["id"]
            if not IDENTIFIER.fullmatch(o) or o in offers:
                raise ValueError("Duplicate/invalid capability: " + o)
            offers.add(o)
    return doc


class Registry:
    def __init__(self, catalog, now=time.monotonic):
        self.catalog = catalog
        self.now = now
        self.live = {}

    def announce(self, identifier, name, kind, offers):
        if not IDENTIFIER.fullmatch(identifier or "") or len(name) > 80 or not name.strip():
            raise ValueError("Invalid id or name")
        if kind not in KINDS:
            raise ValueError("Invalid device kind")
        if len(offers) > 32 or not offers or any(
                not IDENTIFIER.fullmatch(o) for o in offers):
            raise ValueError("1-32 named capability IDs required")
        if any(d["id"] == identifier for d in self.catalog["devices"]):
            raise ValueError("Built-in identity is reserved")
        # An unauthenticated advertiser can replace its own transient claim.
        self.live[identifier] = {
            "seen": self.now(),
            "device": {
                "id": identifier, "name": name, "kind": kind,
                "availability": "advertised_unverified",
                "description": "Transient, unauthenticated metadata announcement.",
                "offers": [
                    {"id": o, "label": o, "class": "advertised", "status": "unverified"}
                    for o in dict.fromkeys(offers)
                ]
            }
        }
        return self.live[identifier]["device"]

    def all_devices(self):
        current = self.now()
        self.live = {k: v for k, v in self.live.items()
                     if current - v["seen"] <= TTL_SECONDS}
        return [*self.catalog["devices"], *[v["device"] for v in self.live.values()]]


def make_handler(registry):
    class Handler(BaseHTTPRequestHandler):
        def respond(self, status, payload):
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            uri = urlsplit(self.path)
            if uri.path == "/v1/devices":
                self.respond(200, {"schema_version": 1,
                                   "devices": registry.all_devices()})
                return
            if uri.path == "/v1/announce":
                try:
                    query = parse_qs(uri.query, keep_blank_values=True,
                                     max_num_fields=8, strict_parsing=False)
                    if any(len(v) != 1 for v in query.values()):
                        raise ValueError("Each field must appear once")
                    identifier = query.get("id", [""])[0]
                    name = query.get("name", [""])[0]
                    kind = query.get("kind", [""])[0]
                    offers = query.get("offers", [""])[0].split(",")
                    if len(uri.query) > 1024:
                        raise ValueError("Announcement too long")
                    claim = registry.announce(identifier, name, kind, offers)
                    self.respond(200, {"accepted": True, "verified": False,
                                       "expires_in_seconds": TTL_SECONDS,
                                       "device": claim})
                except (ValueError, TypeError) as exc:
                    self.respond(400, {"accepted": False, "error": str(exc)})
                return
            self.respond(404, {"error": "Unknown metadata endpoint"})

        def log_message(self, fmt, *args):
            pass  # Do not spill announcement query strings into logs.
    return Handler


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--catalog", type=Path, default=DEFAULT)
    sub = p.add_subparsers(dest="command", required=True)
    sub.add_parser("validate")
    sub.add_parser("list")
    serve = sub.add_parser("serve")
    serve.add_argument("--bind", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8768)
    args = p.parse_args(argv)
    try:
        catalog = load_catalog(args.catalog)
    except (OSError, ValueError, KeyError, TypeError) as exc:
        p.exit(1, "Registry catalog error: " + str(exc) + "\n")
    if args.command == "validate":
        print("OK: " + str(len(catalog["devices"])) + " indexed devices")
    elif args.command == "list":
        for device in catalog["devices"]:
            print(device["id"] + ": " + device["name"] + " / " +
                  ", ".join(o["id"] for o in device["offers"]))
    else:
        with ThreadingHTTPServer((args.bind, args.port),
                                 make_handler(Registry(catalog))) as http:
            print("Discovery-only GET service: http://" +
                  str(args.bind) + ":" + str(http.server_port) + "/v1/devices")
            http.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
