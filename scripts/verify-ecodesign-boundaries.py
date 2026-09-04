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
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

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

# VeVak must not require permanent background-location permission. Optional last-position refresh is
# a best-effort one-shot scheduler and may only use sources Android legitimately exposes at each tick.
if "android.permission.ACCESS_BACKGROUND_LOCATION" in manifest:
    errors.append("Background location permission must not be declared.")

# 0.3.11 deliberately removes notification permission and all normal notification surfaces. A
# future refactor must not silently make request replies or the discreet emergency shortcut depend on
# POST_NOTIFICATIONS again.
if "android.permission.POST_NOTIFICATIONS" in manifest:
    errors.append("POST_NOTIFICATIONS must not be declared: VeVak 0.3.11 core is notification-free.")

notifier_path = ROOT / "app/src/main/java/com/vevak/app/system/RequestVisibilityNotifier.kt"
if notifier_path.exists():
    notifier_text = notifier_path.read_text(encoding="utf-8")
    for forbidden in ("NotificationManager", "Notification.Builder", ".notify(", "NotificationChannel"):
        if forbidden in notifier_text:
            errors.append(f"Notification-free boundary violated by RequestVisibilityNotifier: {forbidden}")

# Notification permission/channels must never gate the core incoming-SMS path.
sms_handler_path = ROOT / "app/src/main/java/com/vevak/app/sms/SmsRequestHandler.kt"
if sms_handler_path.exists():
    sms_handler = sms_handler_path.read_text(encoding="utf-8")
    for forbidden in (
        "RequestVisibilityNotifier",
        "showRequestReceived(",
        "notificationsAllowedForRequests(",
        "RequestAuditOutcome.BlockedVisibility",
        "Manifest.permission.POST_NOTIFICATIONS",
    ):
        if forbidden in sms_handler:
            errors.append(
                "Silent core-SMS boundary violated: "
                f"SmsRequestHandler must not depend on {forbidden}."
            )

# The explicit local emergency action must never be throttled by the anti-tracking protections that
# apply to remote automatic requests. Keep the emergency receiver independent from the global
# request-rate state so a user can trigger several alerts in a genuine emergency.
emergency_path = ROOT / "app/src/main/java/com/vevak/app/emergency/EmergencyShareReceiver.kt"
if emergency_path.exists():
    emergency_text = emergency_path.read_text(encoding="utf-8")
    for forbidden in ("RuntimeStateRepository", "RequestRatePolicy", "tryAcquire("):
        if forbidden in emergency_text:
            errors.append(
                "Emergency anti-tracking boundary violated: "
                f"EmergencyShareReceiver must not use {forbidden}."
            )

# The discreet shortcut may arm/cancel the existing local emergency action, but it must not become a
# second location resolver or bypass the carefully separated emergency last-real-only contract.
shortcut_path = ROOT / "app/src/main/java/com/vevak/app/emergency/EmergencyShortcutActivity.kt"
if shortcut_path.exists():
    shortcut_text = shortcut_path.read_text(encoding="utf-8")
    if "EmergencyShortcutArmController" not in shortcut_text:
        errors.append("Emergency shortcut must delegate timing to EmergencyShortcutArmController.")
    for forbidden in ("VeVakPositionResolver", "VeVakLocationRepository", "OnlineApproximateLocationProvider"):
        if forbidden in shortcut_text:
            errors.append(f"Emergency shortcut location boundary violated: {forbidden}")

arm_path = ROOT / "app/src/main/java/com/vevak/app/emergency/EmergencyShortcutArmController.kt"
if arm_path.exists():
    arm_text = arm_path.read_text(encoding="utf-8")
    if "EmergencyShareReceiver" not in arm_text:
        errors.append("Emergency arm controller must dispatch the canonical EmergencyShareReceiver.")
    for forbidden in ("VeVakPositionResolver", "VeVakLocationRepository", "OnlineApproximateLocationProvider"):
        if forbidden in arm_text:
            errors.append(f"Emergency arm controller location boundary violated: {forbidden}")

# Best-effort periodic memory is allowed, but VeVak still rejects repeating/exact polling frameworks
# and WorkManager loops. The implementation must schedule one future tick at a time instead.
for path in (ROOT / "app/src/main").rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    for forbidden in (
        "PeriodicWorkRequest",
        "WorkManager.getInstance",
        "AlarmManager.setRepeating",
        "AlarmManager.setExact",
        "AlarmManager.setExactAndAllowWhileIdle",
    ):
        if forbidden in text:
            errors.append(f"Periodic/background scheduling boundary violated by {path.relative_to(ROOT)}: {forbidden}")

if errors:
    print("Ec-design boundary verification failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Ec-design boundary verification: OK")
