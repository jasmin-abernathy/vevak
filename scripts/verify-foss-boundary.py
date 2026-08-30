#!/usr/bin/env python3
"""Fail CI if proprietary dependencies or unguarded network behaviour leak into FOSS/core."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
settings_model = (ROOT / "app/src/main/java/com/vevak/app/model/VeVakSettings.kt").read_text(encoding="utf-8")
resolver = (ROOT / "app/src/main/java/com/vevak/app/location/VeVakPositionResolver.kt").read_text(encoding="utf-8")
online_provider = (ROOT / "app/src/main/java/com/vevak/app/location/OnlineApproximateLocationProvider.kt").read_text(encoding="utf-8")

# VeVak 0.3.3 permits Internet transport solely for an explicit, disabled-by-default coarse
# location fallback. Keep the permission and its privacy gate coupled so future refactors cannot
# silently turn the FOSS build into a networked tracker.
if "android.permission.INTERNET" not in manifest:
    errors.append("Opt-in network fallback requires android.permission.INTERNET")
if "val allowNetworkApproximation: Boolean = false" not in settings_model:
    errors.append("Network approximation must remain disabled by default")
if "if (settings.allowNetworkApproximation)" not in resolver:
    errors.append("Online approximation must remain behind the explicit settings gate")
if "https://api.beacondb.net/v1/geolocate" not in online_provider:
    errors.append("Unexpected or missing FOSS network geolocation endpoint")
if 'put("considerIp", true)' not in online_provider or 'put("lacf", false)' not in online_provider:
    errors.append("FOSS online fallback must stay IP-only and must not submit cell/Wi-Fi identifiers")

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
