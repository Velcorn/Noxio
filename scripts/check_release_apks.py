#!/usr/bin/env python3
"""Check that release APKs preserve universal support and identical ARM64 code/resources."""
import sys
import zipfile

ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
LIBRARIES = {"libtagJNI.so", "libffmpegJNI.so"}


def native_entries(abis):
    return {f"lib/{abi}/{name}" for abi in abis for name in LIBRARIES}


def application_files(names):
    return {
        name for name in names
        if name.endswith(".dex") or name == "resources.arsc"
        or name.startswith(("res/", "assets/"))
    }


def check(universal_path, arm64_path):
    with zipfile.ZipFile(universal_path) as universal, zipfile.ZipFile(arm64_path) as arm64:
        universal_names, arm64_names = set(universal.namelist()), set(arm64.namelist())
        for label, names, abis in [
            ("Universal", universal_names, ABIS),
            ("ARM64", arm64_names, {"arm64-v8a"}),
        ]:
            libraries = {name for name in names if name.startswith("lib/") and not name.endswith("/")}
            if libraries != native_entries(abis):
                raise ValueError(
                    f"{label} APK has missing or unexpected native libraries: {sorted(libraries)}"
                )
            if "classes.dex" not in names or "resources.arsc" not in names:
                raise ValueError(f"{label} APK is missing application code or resources")
        # ABI splits should change native libraries only, not application features or resources.
        if application_files(universal_names) != application_files(arm64_names):
            raise ValueError("APK application file lists differ")
        for name in application_files(universal_names) | native_entries({"arm64-v8a"}):
            if universal.read(name) != arm64.read(name):
                raise ValueError(f"APK payload differs: {name}")


if __name__ == "__main__":
    check(*sys.argv[1:])
    print("Release APKs checked: universal supports all four ABIs; ARM64 payload matches.")
