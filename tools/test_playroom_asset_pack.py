"""Small asset-pack contract checks; no Android SDK or GitHub calls."""
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "research/playroom/packs/snake"


class SnakePackTest(unittest.TestCase):
    def test_verified_source_pack_is_not_engine_code(self):
        manifest = json.loads((PACK / "manifest.json").read_text())
        self.assertEqual(manifest["schema_version"], 1)
        self.assertEqual(manifest["pack_id"], "fire.snake.theme")
        self.assertRegex(manifest["file"], r"^style-[A-Za-z0-9_-]{1,32}\.json$")
        raw = (PACK / manifest["file"]).read_bytes()
        self.assertLessEqual(len(raw), 8192)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), manifest["sha256"])
        style = json.loads(raw)
        self.assertEqual(style["version"], manifest["version"])
        self.assertEqual(style["pack_id"], manifest["pack_id"])
        self.assertEqual(style["schema_version"], 1)
        self.assertEqual(set(style["colors"]),
                         {"board", "cell_a", "cell_b", "food", "head", "body"})
        for value in style["colors"].values():
            self.assertTrue(re.fullmatch(r"#[0-9A-Fa-f]{6}", value))

    def test_manifests_and_style_pack_remain_data_only(self):
        for file in PACK.glob("*.json"):
            self.assertLess(file.stat().st_size, 8192)
            self.assertNotIn("javascript:", file.read_text())


if __name__ == "__main__":
    unittest.main()
