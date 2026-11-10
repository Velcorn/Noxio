import tempfile
import unittest
import zipfile
from pathlib import Path

from check_release_apks import ABIS, check, native_entries


class ReleaseApkTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.universal = Path(self.temp.name) / "universal.apk"
        self.arm64 = Path(self.temp.name) / "arm64.apk"
        self.common = {"classes.dex": b"code", "resources.arsc": b"resources", "res/icon.xml": b"icon"}
        self.universal_files = {**self.common, **dict.fromkeys(native_entries(ABIS), b"native")}
        self.arm64_files = {**self.common, **dict.fromkeys(native_entries({"arm64-v8a"}), b"native")}

    def verify(self):
        for path, files in [(self.universal, self.universal_files), (self.arm64, self.arm64_files)]:
            with zipfile.ZipFile(path, "w") as archive:
                for name, contents in files.items():
                    archive.writestr(name, contents)
        check(self.universal, self.arm64)

    def test_matching_apks_pass(self):
        self.verify()

    def test_missing_universal_architecture_is_rejected(self):
        for name in native_entries({"armeabi-v7a"}):
            del self.universal_files[name]
        with self.assertRaisesRegex(ValueError, "Universal APK"):
            self.verify()

    def test_extra_architecture_in_arm64_is_rejected(self):
        self.arm64_files["lib/x86/libtagJNI.so"] = b"native"
        with self.assertRaisesRegex(ValueError, "ARM64 APK"):
            self.verify()

    def test_missing_decoder_is_rejected(self):
        del self.arm64_files["lib/arm64-v8a/libffmpegJNI.so"]
        with self.assertRaisesRegex(ValueError, "ARM64 APK"):
            self.verify()

    def test_missing_code_is_rejected(self):
        del self.arm64_files["classes.dex"]
        with self.assertRaisesRegex(ValueError, "missing application"):
            self.verify()

    def test_missing_resource_is_rejected(self):
        del self.arm64_files["res/icon.xml"]
        with self.assertRaisesRegex(ValueError, "file lists differ"):
            self.verify()

    def test_changed_code_resources_and_native_payloads_are_rejected(self):
        for name in ["classes.dex", "resources.arsc", "res/icon.xml", "lib/arm64-v8a/libtagJNI.so"]:
            with self.subTest(name=name):
                original = self.arm64_files[name]
                self.arm64_files[name] = b"changed"
                with self.assertRaisesRegex(ValueError, "payload differs"):
                    self.verify()
                self.arm64_files[name] = original


if __name__ == "__main__":
    unittest.main()
