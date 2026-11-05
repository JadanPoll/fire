"""Problem-first human capability tests, not educational performance scores."""
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]


class DojoPossibilityContract(unittest.TestCase):
    def test_every_challenge_invites_new_questions(self):
        catalog=json.loads((ROOT/"research/android_dojo/catalog.json").read_text())
        purpose=catalog["holistic_purpose"].lower()
        self.assertIn("human",purpose)
        self.assertIn("intuition",purpose)
        for c in catalog["challenges"]:
            with self.subTest(id=c["id"]):
                self.assertGreaterEqual(len(c["possibility_seeds"]),2)
                self.assertEqual(len(set(c["possibility_seeds"])),len(c["possibility_seeds"]))
                self.assertTrue(all("?" in q for q in c["possibility_seeds"]))
                self.assertIn("glossary",c)
                self.assertIn("tacit_checks",c)


if __name__=="__main__":
    unittest.main()
