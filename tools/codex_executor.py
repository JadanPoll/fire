#!/usr/bin/env python3
"""Fire Console's *opt-in* trusted Codex executor.

The HTTP origin is loopback-only. A separately configured authenticated TLS
proxy/private tunnel is required for Fire TV access. This is not an ADB bridge,
a generic remote shell, or an API-key distributor.
"""
import argparse
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

PROMPT_LIMIT = 8000
REQUEST_LIMIT = 16 * 1024
RESULT_LIMIT = 9000
JOB_LIMIT_SECONDS = 300
TOKEN_RE = re.compile(r"^[A-Za-z0-9_-]{32,256}$")
UUID_RE = re.compile(r"^/v1/jobs/([0-9a-f-]{36})$")


def token_valid(token):
    return bool(TOKEN_RE.fullmatch(token or ""))


class Executor:
    def __init__(self, workspace, codex="codex"):
        self.workspace = Path(workspace).expanduser().resolve(strict=True)
        if not self.workspace.is_dir():
            raise ValueError("Workspace must be a directory")
        self.codex = shutil.which(codex) if os.path.sep not in codex else codex
        if not self.codex:
            raise ValueError("Codex executable not found. Install and sign in to Codex CLI on this host.")
        self.lock = threading.Lock()
        self.jobs = {}
        self.active = None

    def submit(self, prompt):
        if not isinstance(prompt, str) or not 0 < len(prompt.strip()) <= PROMPT_LIMIT:
            raise ValueError("Prompt must contain 1–8000 characters")
        with self.lock:
            if self.active:
                raise RuntimeError("Another Codex run is already active")
            job_id = str(uuid.uuid4())
            job = dict(id=job_id, state="queued", message="Waiting to start Codex",
                       updatedAt=int(time.time()), output="", cancel=False, process=None)
            self.jobs[job_id] = job
            self.active = job_id
        threading.Thread(target=self._run, args=(job_id, prompt), daemon=True).start()
        return self.read(job_id)

    def read(self, job_id):
        with self.lock:
            job = self.jobs.get(job_id)
            if job is None:
                return None
            return {key: job[key] for key in ("id", "state", "message", "updatedAt", "output")}

    def cancel(self, job_id):
        with self.lock:
            job = self.jobs.get(job_id)
            if job is None:
                return None
            if job["state"] in ("completed", "failed", "cancelled", "timeout"):
                return self.read_terminal(job)
            job["cancel"] = True
            job["message"] = "Cancellation requested"
            proc = job["process"]
        if proc is not None:
            proc.terminate()
        return self.read(job_id)

    @staticmethod
    def read_terminal(job):
        return {key: job[key] for key in ("id", "state", "message", "updatedAt", "output")}

    def _run(self, job_id, prompt):
        # Never give the browser pairing token to Codex or its child processes.
        env = os.environ.copy()
        env.pop("FIRE_CODEX_TOKEN", None)
        env.pop("FIRE_CODEX_BIND", None)
        # No arbitrary shell string, cwd, binary, approvals, or model is supplied
        # by the TV. The host operator chooses this repository at startup.
        argv = [self.codex, "exec", "--json", "--sandbox", "workspace-write",
                "--skip-git-repo-check", prompt]
        proc = None
        output = ""
        state = "failed"
        message = "Execution did not start"
        timer = None
        timed_out = threading.Event()
        try:
            with self.lock:
                if self.jobs[job_id]["cancel"]:
                    state, message = "cancelled", "Cancelled before execution"
                    return
            proc = subprocess.Popen(argv, cwd=self.workspace, env=env,
                                    stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, text=True,
                                    encoding="utf-8", errors="replace", bufsize=1)
            with self.lock:
                self.jobs[job_id]["process"] = proc
                self.jobs[job_id]["state"] = "running"
                self.jobs[job_id]["message"] = "Codex executing in workspace-write sandbox"
            def timeout():
                if proc.poll() is None:
                    timed_out.set()
                    proc.kill()
            timer = threading.Timer(JOB_LIMIT_SECONDS, timeout)
            timer.daemon = True
            timer.start()
            diagnostic = ""
            for line in proc.stdout:
                try:
                    event = json.loads(line)
                    kind = event.get("type", "")
                    item = event.get("item") or {}
                    if kind == "item.completed" and item.get("type") == "agent_message":
                        output = str(item.get("text", ""))[-RESULT_LIMIT:]
                    elif kind in ("turn.failed", "error"):
                        diagnostic = str(event.get("message", event.get("error", kind)))[-1000:]
                    elif kind == "turn.completed":
                        message = "Codex turn finished"
                except (ValueError, AttributeError, TypeError):
                    # Keep a small diagnostic tail; never dump entire agent trace.
                    diagnostic = (diagnostic + line)[-1000:]
                with self.lock:
                    j = self.jobs[job_id]
                    j["output"] = output[-RESULT_LIMIT:]
                    j["updatedAt"] = int(time.time())
            code = proc.wait()
            with self.lock:
                cancelled = self.jobs[job_id]["cancel"]
            if cancelled:
                state, message = "cancelled", "Stopped by the operator"
            elif timed_out.is_set():
                state, message = "timeout", "Stopped after the 5-minute execution limit"
            elif code == 0:
                state, message = "completed", "Codex exited successfully"
            else:
                state, message = "failed", "Codex exit " + str(code) + ": " + diagnostic[-400:]
            if not output and diagnostic:
                output = diagnostic[-RESULT_LIMIT:]
        except Exception as exc:
            message = type(exc).__name__ + ": " + str(exc)[:400]
            state = "failed"
        finally:
            if timer:
                timer.cancel()
            with self.lock:
                j = self.jobs[job_id]
                j.update(state=state, message=message, output=output[-RESULT_LIMIT:],
                         updatedAt=int(time.time()), process=None)
                if self.active == job_id:
                    self.active = None


def make_handler(executor, secret):
    class Handler(BaseHTTPRequestHandler):
        server_version = "FireCodex/1"
        def log_message(self, fmt, *args):
            # No prompts, tokens, or Authorization headers in logs.
            print("%s - HTTP %s" % (self.address_string(), args[1] if len(args) > 1 else "?"), flush=True)

        def reply(self, code, body):
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def authorized(self):
            provided = self.headers.get("Authorization", "")
            if (self.headers.get("Origin") or not provided.startswith("Bearer ") or
                    not hmac.compare_digest(provided[7:], secret)):
                self.reply(401, {"error": "Pairing token required or invalid"})
                return False
            return True

        def route_id(self):
            m = UUID_RE.fullmatch(urlsplit(self.path).path)
            if not m:
                return None
            try:
                return str(uuid.UUID(m.group(1)))
            except ValueError:
                return None

        def do_GET(self):
            if not self.authorized(): return
            job_id = self.route_id()
            job = executor.read(job_id) if job_id else None
            self.reply(200, job) if job else self.reply(404, {"error": "Unknown job (executor may have restarted)"})

        def do_POST(self):
            if not self.authorized(): return
            if self.path != "/v1/jobs":
                self.reply(404, {"error": "Unknown endpoint"}); return
            try:
                n = int(self.headers.get("Content-Length", "-1"))
                if n < 1 or n > REQUEST_LIMIT:
                    self.reply(413, {"error": "Request exceeds allowed size"}); return
                if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json":
                    self.reply(415, {"error": "Expected application/json"}); return
                data = json.loads(self.rfile.read(n))
                if not isinstance(data, dict) or set(data) != {"prompt"}:
                    raise ValueError("Expected exactly a prompt")
                job = executor.submit(data["prompt"])
                self.reply(202, job)
            except ValueError as exc:
                self.reply(400, {"error": str(exc)})
            except RuntimeError as exc:
                self.reply(409, {"error": str(exc)})
            except (json.JSONDecodeError, UnicodeDecodeError):
                self.reply(400, {"error": "Malformed JSON"})

        def do_DELETE(self):
            if not self.authorized(): return
            job_id = self.route_id()
            job = executor.cancel(job_id) if job_id else None
            self.reply(200, job) if job else self.reply(404, {"error": "Unknown job"})
    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", required=True, help="Existing trusted Codex workspace directory")
    parser.add_argument("--port", type=int, default=8769)
    parser.add_argument("--codex", default="codex", help="Trusted codex executable on this host")
    args = parser.parse_args()
    token = os.getenv("FIRE_CODEX_TOKEN", "")
    if not token_valid(token):
        parser.error("Set FIRE_CODEX_TOKEN to a random 32–256 character URL-safe token, e.g. secrets.token_urlsafe(32)")
    executor = Executor(args.workspace, args.codex)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(executor, token))
    server.daemon_threads = True
    print("Fire Codex executor on 127.0.0.1:%d; workspace=%s" % (args.port, executor.workspace), flush=True)
    print("Requires a separately configured trusted HTTPS tunnel/proxy for TV access. Never publish this HTTP port.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
