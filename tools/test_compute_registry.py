"""Capability discovery regression checks, no external network or credentials."""
import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from urllib.request import urlopen
from urllib.parse import urlencode

from compute_registry import load_catalog, Registry, make_handler


class RegistryTest(unittest.TestCase):
    def test_seed_represents_github_as_storage_and_compilation(self):
        catalog = load_catalog()
        github = next(d for d in catalog["devices"] if d["id"] == "github")
        ids = {o["id"] for o in github["offers"]}
        self.assertIn("git.versioned-storage", ids)
        self.assertIn("actions.static-build", ids)

    def test_ttl_and_no_implicit_trust(self):
        now = [1000]
        r = Registry(load_catalog(), now=lambda: now[0])
        item = r.announce("pi-test", "Raspberry Pi", "physical",
                          ["python.execute", "sensor.read"])
        self.assertEqual(item["availability"], "advertised_unverified")
        self.assertEqual(item["offers"][0]["status"], "unverified")
        self.assertTrue(any(d["id"] == "pi-test" for d in r.all_devices()))
        now[0] += 301
        self.assertFalse(any(d["id"] == "pi-test" for d in r.all_devices()))

    def test_reserved_id_cannot_override_github(self):
        r = Registry(load_catalog())
        with self.assertRaises(ValueError):
            r.announce("github", "fake", "virtual_platform", ["run.anything"])

    def test_get_announce_and_query(self):
        registry = Registry(load_catalog())
        with ThreadingHTTPServer(("127.0.0.1", 0), make_handler(registry)) as srv:
            thread = threading.Thread(target=srv.serve_forever, daemon=True)
            thread.start()
            try:
                base = "http://127.0.0.1:" + str(srv.server_port)
                query = urlencode({"id": "test-agent", "name": "My Python worker",
                                   "kind": "virtual_agent",
                                   "offers": "python.execute,data.analyze"})
                with urlopen(base + "/v1/announce?" + query, timeout=2) as res:
                    receipt = json.load(res)
                self.assertTrue(receipt["accepted"])
                self.assertFalse(receipt["verified"])
                with urlopen(base + "/v1/devices", timeout=2) as res:
                    listed = json.load(res)
                self.assertTrue(any(d["id"] == "test-agent" for d in listed["devices"]))
            finally:
                srv.shutdown()
                thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
