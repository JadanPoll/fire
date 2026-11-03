"""Manifest, nonexecuting discovery, and bounded Playroom recipe regressions."""
import json
import unittest
from pathlib import Path
import check_appos_contract as contract

ROOT=Path(__file__).resolve().parents[1]
LOCATION=ROOT/"Applications/SignalStudio"
MANIFEST=json.loads((LOCATION/"appos.json").read_text(encoding="utf-8"))
RECIPE=json.loads((LOCATION/"src/main/assets/example.recipe.json").read_text(encoding="utf-8"))

class AppOsContractTest(unittest.TestCase):
    def test_repo_discovery_and_catalog_alignment(self):
        names=[a["id"] for a in contract.check(ROOT)]
        self.assertIn("fire.playroom.signal",names)

    def test_manifest_is_metadata_not_process_command(self):
        self.assertEqual(MANIFEST["entry"]["type"],"android-tv")
        self.assertNotIn("command",MANIFEST["entry"])
        self.assertEqual(MANIFEST["build"]["project"],":signalstudio")

    def test_unknown_capability_rejected(self):
        invalid=json.loads(json.dumps(MANIFEST))
        invalid["capabilities"].append("adb.shell")
        with self.assertRaises(contract.ContractError):
            contract.validate_manifest(invalid,LOCATION,ROOT)

    def test_duplicate_capability_rejected(self):
        invalid=json.loads(json.dumps(MANIFEST))
        invalid["capabilities"].append("input.dpad")
        with self.assertRaises(contract.ContractError):
            contract.validate_manifest(invalid,LOCATION,ROOT)

    def test_identity_mismatch_rejected(self):
        invalid=json.loads(json.dumps(MANIFEST))
        invalid["entry"]["package"]="dev.jadan.fire.other"
        with self.assertRaises(contract.ContractError):
            contract.validate_manifest(invalid,LOCATION,ROOT)

    def test_validated_recipe_matches_manifold_vocabulary(self):
        self.assertEqual(contract.check_recipe(RECIPE),RECIPE)
        for key,bad in (("waveform","random"),("gain",True),
                        ("frequency",100),("smoothing",float("nan"))):
            invalid=dict(RECIPE);invalid[key]=bad
            with self.subTest(key=key),self.assertRaises(contract.ContractError):
                contract.check_recipe(invalid)

    def test_extra_recipe_command_rejected(self):
        invalid=dict(RECIPE)
        invalid["command"]="rm -rf /"
        with self.assertRaises(contract.ContractError):
            contract.check_recipe(invalid)

if __name__=="__main__":
    unittest.main()
