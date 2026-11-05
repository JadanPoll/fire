"""Cheap self-tests for change-scope classification; no SDK or emulator."""
import unittest
from validation_plan import classify


class ValidationRoutingTest(unittest.TestCase):
    def test_python_harness_is_static(self):
        for p in ("tools/tv_emulator_smoke.py", "catalog.json",
                  "Applications/SignalStudio/README.md",
                  "AppOS/Remote/README.md", "docs/notes.md",
                  "Applications/SignalStudio/appos.json"):
            with self.subTest(p=p):
                self.assertFalse(classify([p])["android"])

    def test_browser_logic_is_not_all_modules(self):
        plan = classify(["browser/src/main/java/dev/jadan/fire/browser/BrowserActivity.java"])
        self.assertEqual(plan["tasks"], [":browser:testReleaseUnitTest"])

    def test_native_resources_must_package(self):
        plan = classify(["app/src/main/res/drawable/tv_icon.xml"])
        self.assertEqual(plan["tasks"],
                         [":app:testReleaseUnitTest", ":app:assembleRelease"])

    def test_canvas_can_build_itself(self):
        self.assertEqual(classify(["canvas/src/main/AndroidManifest.xml"])["tasks"],
                         [":canvas:assembleRelease"])

    def test_shared_input_affects_signal_studio(self):
        plan = classify(["AppOS/Remote/src/main/java/dev/jadan/fire/remote/RemotePolicy.java"])
        self.assertEqual(plan["tasks"],
                         [":remoteinput:testReleaseUnitTest", ":signalstudio:testReleaseUnitTest"])

    def test_new_native_family_is_not_silently_skipped(self):
        plan = classify(["Applications/GBAPlayer/src/main/java/Player.java"])
        self.assertTrue(plan["android"])
        self.assertIn(":app:testReleaseUnitTest", plan["tasks"])

    def test_root_configuration_always_compiles(self):
        plan = classify(["settings.gradle"])
        self.assertEqual(set(plan["modules"]),
                         {"app", "browser", "canvas", "signalstudio", "remoteinput"})
        self.assertIn(":app:assembleRelease", plan["tasks"])

    def test_only_docs_in_native_tree_cost_no_gradle(self):
        self.assertFalse(classify(["app/README.md", "browser/DEVELOPMENT.md"])["android"])


if __name__ == "__main__":
    unittest.main()
