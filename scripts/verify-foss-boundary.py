#!/usr/bin/env python3
"""Fail CI if proprietary/network dependencies leak into the canonical FOSS core."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for permission in (
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
):
    if permission in manifest:
        errors.append(f"Forbidden core manifest permission: {permission}")

# Proprietary Google APIs are allowed only in the Play source set/dependency scope.
for source_root in (ROOT / "app/src/main", ROOT / "app/src/foss"):
    if not source_root.exists():
        continue
    for path in source_root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        if "com.google.android.gms" in text:
            errors.append(f"Google Play Services import leaked into FOSS/core source: {path.relative_to(ROOT)}")

build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if '"playImplementation"("com.google.android.gms:' not in build:
    errors.append("Google Play dependency must remain explicitly scoped to playImplementation.")

for forbidden_scope in (
    'implementation("com.google.android.gms:',
    '"fossImplementation"("com.google.android.gms:',
):
    if forbidden_scope in build:
        errors.append(f"Proprietary dependency escaped Play scope: {forbidden_scope}")

if errors:
    print("FOSS boundary verification failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("FOSS boundary verification: OK")
