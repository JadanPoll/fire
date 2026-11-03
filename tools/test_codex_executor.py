#!/usr/bin/env python3
"""Fast localhost protocol tests; no Codex login, Fire device, or network needed."""
import contextlib
import http.server
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parent))
from codex_executor import Executor, make_handler, token_valid


class ExecutorContractTest(unittest.TestCase):
    def test_pairing_token_validation(self):
        self.assertFalse(token_valid(""))
        self.assertFalse(token_valid("short"))
        self.assertFalse(token_valid("a" * 32 + "\r\nInjected: x"))
        self.assertTrue(token_valid("a" * 32))

    def test_localhost_api_and_codex_stub(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            executable = root / "codex-stub"
            executable.write_text(
                "#!/usr/bin/env python3\n"
                "import json,time\n"
                "print(json.dumps({'type':'item.completed',"
                "'item':{'type':'agent_message','text':'test complete'}}),flush=True)\n"
                "time.sleep(.1)\n",
                encoding="utf8",
            )
            executable.chmod(0o700)
            executor = Executor(root, str(executable))
            token = "A" * 40
            httpd = http.server.ThreadingHTTPServer(
                ("127.0.0.1", 0), make_handler(executor, token)
            )
            worker = threading.Thread(target=httpd.serve_forever, daemon=True)
            worker.start()
            base = f"http://127.0.0.1:{httpd.server_port}"
            def call(path, method="GET", body=None, auth=True):
                data = None if body is None else json.dumps(body).encode("utf8")
                headers = {"Content-Type": "application/json"}
                if auth:
                    headers["Authorization"] = "Bearer " + token
                req = Request(base + path, method=method, headers=headers, data=data)
                with urlopen(req, timeout=2) as response:
                    return response.status, json.load(response)
            try:
                with self.assertRaises(HTTPError) as denied:
                    call("/v1/jobs/latest", auth=False)
                self.assertEqual(401, denied.exception.code)
                with self.assertRaises(HTTPError) as absent:
                    call("/v1/jobs/latest")
                self.assertEqual(404, absent.exception.code)
                status, job = call("/v1/jobs", method="POST", body={"prompt": "Say hello"})
                self.assertEqual(202, status)
                self.assertIn(job["state"], ("queued", "running", "completed"))
                job_id = job["id"]
                for _ in range(80):
                    _, current = call("/v1/jobs/" + job_id)
                    if current["state"] == "completed":
                        break
                    time.sleep(.05)
                self.assertEqual("completed", current["state"], current)
                self.assertIn("test complete", current["output"])
                _, latest = call("/v1/jobs/latest")
                self.assertEqual(job_id, latest["id"])
                _, final = call("/v1/jobs/" + job_id, method="DELETE")
                self.assertEqual("completed", final["state"])
                with self.assertRaises(HTTPError) as malformed:
                    call("/v1/jobs", method="POST", body={"prompt": "x", "shell": "evil"})
                self.assertEqual(400, malformed.exception.code)
            finally:
                httpd.shutdown()
                httpd.server_close()
                worker.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
