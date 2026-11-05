"""Evidence-state and unknown-software behavior tests, no network/Android SDK."""
import copy
import unittest

from portability_matrix import read_matrix, lookup, unassessed, factor_variants


class PortabilityMatrixTest(unittest.TestCase):
    def test_seed_is_valid_and_vscode_has_independent_input_assessment(self):
        entries = read_matrix()
        e = lookup(entries, "VS Code")
        self.assertEqual(e["id"], "vscode")
        self.assertEqual(e["routes"][0]["interaction"]["remote"], "poor")
        self.assertNotEqual(e["routes"][0]["state"], "device_verified")

    def test_factorized_execution_and_controls_are_independent(self):
        matrix = read_matrix()
        vscode = lookup(matrix, "VS Code")
        variants = factor_variants(vscode)
        self.assertEqual(len(variants), len(vscode["routes"]) * 3)
        browser_remote = next(v for v in variants if
                              v["execution_route"] == "vscode-dev" and
                              v["control"] == "remote")
        browser_ipad = next(v for v in variants if
                            v["execution_route"] == "vscode-dev" and
                            v["control"] == "ipad_companion")
        self.assertEqual(browser_remote["execution"], browser_ipad["execution"])
        self.assertEqual(browser_remote["control_assessment"], "poor")
        self.assertEqual(browser_ipad["control_assessment"], "candidate")
        self.assertFalse(browser_ipad["device_verified"])

    def test_unknown_is_not_incompatible(self):
        answer = unassessed("Unseen Game")
        self.assertEqual(answer["state"], "unassessed")
        self.assertIn("NOT evidence", answer["meaning"])

    def test_duplicate_alias_rejected(self):
        import tempfile
        import json
        from pathlib import Path
        data = read_matrix()
        data = copy.deepcopy(data)
        data["entries"][1]["names"].append("vscode")
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "matrix.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaises(ValueError):
                read_matrix(path)

    def test_device_claim_requires_actual_trace(self):
        import tempfile
        import json
        from pathlib import Path
        data = read_matrix()
        data["entries"][0]["routes"][0]["state"] = "device_verified"
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "matrix.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaises(ValueError):
                read_matrix(path)


if __name__ == "__main__":
    unittest.main()
