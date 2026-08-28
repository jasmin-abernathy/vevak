#!/usr/bin/env python3
"""Check machine-readable VeVak ecodesign/runtime boundaries against the project."""

from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

budgets = json.loads((ROOT / "ECODESIGN_BUDGETS.json").read_text(encoding="utf-8"))
settings = (ROOT / "app/src/main/java/com/vevak/app/model/VeVakSettings.kt").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

runtime = budgets["runtimeDefaults"]
expected_ints = {
    "minRequestIntervalSeconds": runtime["minimumAcceptedRequestIntervalSeconds"],
    "maxCachedLocationAgeSeconds": runtime["acceptedCachedLocationMaxAgeSeconds"],
    "locationTimeoutSeconds": runtime["singleLocationTimeoutSeconds"],
}
for name, expected in expected_ints.items():
    match = re.search(rf"val\s+{re.escape(name)}:\s*Int\s*=\s*(\d+)", settings)
    if not match:
        errors.append(f"Could not find runtime default {name} in VeVakSettings.kt")
    elif int(match.group(1)) != int(expected):
        errors.append(f"{name}={match.group(1)} but budget requires {expected}")

build_budget = budgets["build"]
min_sdk = re.search(r"minSdk\s*=\s*(\d+)", build)
if not min_sdk or int(min_sdk.group(1)) != int(build_budget["minimumAndroidApi"]):
    errors.append("minSdk does not match ECODESIGN_BUDGETS.json")

if build_budget.get("canonicalFlavor") == "foss" and 'create("foss")' not in build:
    errors.append("Canonical foss flavor is missing.")

if build_budget.get("releaseMinification") and "isMinifyEnabled = true" not in build:
    errors.append("Release minification budget is not enforced.")
if build_budget.get("releaseResourceShrinking") and "isShrinkResources = true" not in build:
    errors.append("Release resource shrinking budget is not enforced.")

# The core must not introduce periodic schedulers/background polling frameworks.
for path in (ROOT / "app/src/main").rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    for forbidden in ("PeriodicWorkRequest", "WorkManager.getInstance", "AlarmManager.setRepeating"):
        if forbidden in text:
            errors.append(f"Periodic/background scheduling boundary violated by {path.relative_to(ROOT)}: {forbidden}")

if errors:
    print("Ec-design boundary verification failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Ec-design boundary verification: OK")
