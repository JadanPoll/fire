import hashlib
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
import fetch_gba_core

class PinnedGbaCoreTest(unittest.TestCase):
    def test_git_blob_hash_is_of_the_content_and_length(self):
        data=b"native core fixture"
        expected=hashlib.sha1(b"blob 19\x00"+data).hexdigest()
        self.assertEqual(expected,fetch_gba_core.git_blob_digest(data))

    def test_changed_byte_fails_integrity(self):
        with TemporaryDirectory() as directory:
            file=Path(directory)/"libmgba_libretro_android.so"
            content=b"check me"
            file.write_bytes(content)
            self.assertTrue(fetch_gba_core.check_file(
                file,len(content),fetch_gba_core.git_blob_digest(content)))
            file.write_bytes(b"check mx")
            self.assertFalse(fetch_gba_core.check_file(
                file,len(content),fetch_gba_core.git_blob_digest(content)))

    def test_upstream_binaries_are_pinned_by_abi(self):
        self.assertEqual(set(fetch_gba_core.CORES),{"arm64-v8a","armeabi-v7a"})
        for size,sha in fetch_gba_core.CORES.values():
            self.assertGreater(size,100000)
            self.assertEqual(len(sha),40)
        self.assertNotIn("Pokemon",str(fetch_gba_core.DEST))

if __name__=="__main__":unittest.main()
