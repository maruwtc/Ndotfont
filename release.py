#!/usr/bin/env python3
"""Collect one release into release/<tag>/:

  NdotCJK-KSUNext-v<module>.zip   KernelSU / Magisk module, built from ksu-module/
  NdotFontForce-v<app>.apk        LSPosed module, copied from the Android Studio build
  SHA256SUMS.txt

Build the APK first (Android Studio > Build APK(s)), then run:  python3 release.py
Works on Windows too. Shell scripts are written with LF endings whatever the checkout has.
"""
import hashlib
import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MOD = ROOT / "ksu-module"

# Files that go into the module zip. Emoji.ttf is never bundled (Apple's emoji is proprietary).
MODULE_FILES = [
    "module.prop", "customize.sh", "post-fs-data.sh", "service.sh", "action.sh",
    "uninstall.sh", "README.md", "OFL.txt", "fonts/NdotCJK-Regular.ttf",
    "META-INF/com/google/android/update-binary",
    "META-INF/com/google/android/updater-script",
]
TEXT = (".sh", ".prop", ".md", ".txt", "update-binary", "updater-script")
EXEC = (".sh", "update-binary")


def module_version():
    prop = (MOD / "module.prop").read_text()
    return re.search(r"^version=(\S+)", prop, re.M).group(1)          # e.g. v1.7


def app_version():
    gradle = (ROOT / "app" / "build.gradle").read_text()
    return re.search(r'versionName\s+"([^"]+)"', gradle).group(1)    # e.g. 1.2


def build_zip(dest):
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for rel in MODULE_FILES:
            src = MOD / rel
            if not src.is_file():
                sys.exit(f"missing {src}")
            data = src.read_bytes()
            if rel.endswith(TEXT):
                data = data.replace(b"\r\n", b"\n")
            info = zipfile.ZipInfo(rel, date_time=(2026, 1, 1, 0, 0, 0))
            info.external_attr = (0o100755 if rel.endswith(EXEC) else 0o100644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)


def find_apk(ver):
    out = ROOT / "app" / "build" / "outputs" / "apk"
    apks = sorted(out.glob(f"*/NdotFontForce-v{ver}-*.apk"),
                  key=lambda p: ("release" not in p.name, -p.stat().st_mtime))
    return apks[0] if apks else None


def main():
    mver, aver = module_version(), app_version()
    outdir = ROOT / "release" / f"ksu-{mver}_app-v{aver}"
    outdir.mkdir(parents=True, exist_ok=True)

    zip_path = outdir / f"NdotCJK-KSUNext-{mver}.zip"
    build_zip(zip_path)
    files = [zip_path]
    print(f"module  {zip_path.relative_to(ROOT)}")

    apk = find_apk(aver)
    if apk:
        dest = outdir / f"NdotFontForce-v{aver}.apk"
        shutil.copy2(apk, dest)
        files.append(dest)
        print(f"app     {dest.relative_to(ROOT)}  (from {apk.name})")
    else:
        print(f"app     not found: build NdotFontForce-v{aver} in Android Studio, then rerun")

    with open(outdir / "SHA256SUMS.txt", "w", newline="\n") as f:
        for p in files:
            f.write(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n")


if __name__ == "__main__":
    main()
