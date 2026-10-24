#!/usr/bin/env python3
"""Check upstream Fastlane metadata without pretending to run fdroid lint/build.

This project deliberately keeps screenshots optional during preparation:
only genuine captures from the installable FOSS build may be submitted.
"""
from __future__ import annotations

from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "fastlane/metadata/android"
LANGUAGES = ("en-US", "fr-FR")
errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
code_match = re.search(r'\bversionCode\s*=\s*(\d+)', gradle)
name_match = re.search(r'\bversionName\s*=\s*"([^"]+)"', gradle)
require(code_match is not None and name_match is not None, "Missing numeric versionCode / versionName")
version_code = code_match.group(1) if code_match else None


def png_size(path: Path) -> tuple[int, int] | None:
    try:
        data = path.read_bytes()
        if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
            return None
        return struct.unpack(">II", data[16:24])
    except OSError:
        return None


icons: list[bytes] = []
for locale in LANGUAGES:
    directory = METADATA / locale
    for name, max_length in [
        ("title.txt", 50),
        ("short_description.txt", 80),
        ("full_description.txt", 4000),
    ]:
        file = directory / name
        if not file.is_file():
            errors.append(f"{locale}: missing {name}")
            continue
        value = file.read_text(encoding="utf-8").strip()
        require(bool(value), f"{locale}: empty {name}")
        require(len(value) <= max_length, f"{locale}: {name} exceeds {max_length} characters")
        if name == "short_description.txt":
            require(not value.endswith("."), f"{locale}: short description should not end with period")
    if version_code is not None:
        changelog = directory / "changelogs" / f"{version_code}.txt"
        if not changelog.is_file():
            errors.append(f"{locale}: missing changelog for versionCode {version_code}")
        else:
            require(len(changelog.read_text(encoding="utf-8").strip()) <= 500,
                    f"{locale}: changelog exceeds 500 characters")
    icon = directory / "images/icon.png"
    require(png_size(icon) == (512, 512), f"{locale}: expected 512x512 PNG at {icon}")
    if icon.is_file():
        icons.append(icon.read_bytes())
    screenshot_dir = directory / "images/phoneScreenshots"
    shots = list(screenshot_dir.glob("*.png")) + list(screenshot_dir.glob("*.jpg")) + list(screenshot_dir.glob("*.jpeg"))
    if not shots:
        print(f"NOTE {locale}: no real-device screenshots supplied yet; not inventing any.")

if len(icons) == len(LANGUAGES):
    require(len(set(icons)) == 1, "FR/EN launcher icons are not identical")

for error in errors:
    print(f"ERROR: {error}", file=sys.stderr)
if errors:
    sys.exit(1)
print("Fastlane metadata: names, descriptions, changelog and launcher icons OK.")
