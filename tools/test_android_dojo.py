"""Dojo learning ledger checks; no Android runtime or internet."""
import tempfile
from pathlib import Path
import unittest

from android_dojo import read_catalog, get_challenge, read_progress, save_progress, record


class DojoTest(unittest.TestCase):
    def test_catalog_topics_and_related_challenges(self):
        catalog = read_catalog()
        item = get_challenge(catalog, "remote-double-action")
        self.assertIn("debouncing", item["glossary"])
        self.assertIn("tv-companion-control", item["related"])

    def test_partial_progress_survives_restart_without_grade(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "progress.json"
            original = read_progress(path)
            record(original, "remote-double-action", note="Observed an extra DOWN",
                   state="paused", clock="2026-11-04T00:00:00+00:00")
            save_progress(path, original)
            later = read_progress(path)
            self.assertEqual(later["challenges"]["remote-double-action"]["state"], "paused")
            self.assertEqual(later["challenges"]["remote-double-action"]["notes"][0]["text"],
                             "Observed an extra DOWN")
            record(later, "remote-double-action", state="revisiting")
            save_progress(path, later)
            self.assertEqual(len(read_progress(path)["challenges"]["remote-double-action"]["notes"]), 1)

    def test_incompatible_progress_never_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "progress.json"
            path.write_text('{"schema_version":999,"challenges":{}}')
            with self.assertRaises(ValueError):
                read_progress(path)
            self.assertIn("999", path.read_text())


if __name__ == "__main__":
    unittest.main()
