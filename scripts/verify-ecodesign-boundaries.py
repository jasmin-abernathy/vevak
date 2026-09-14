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

# Periodic refresh may use optional background-location access, but only behind the explicit setting
# and a runtime permission check. It must never become a prerequisite for SMS or onboarding.
refresh_scheduler_path = ROOT / "app/src/main/java/com/vevak/app/background/PositionRefreshScheduler.kt"
refresh_receiver_path = ROOT / "app/src/main/java/com/vevak/app/background/PositionRefreshReceiver.kt"
if "android.permission.ACCESS_BACKGROUND_LOCATION" not in manifest:
    errors.append("Optional periodic refresh requires ACCESS_BACKGROUND_LOCATION to work off-screen.")
for path in (refresh_scheduler_path, refresh_receiver_path):
    text = path.read_text(encoding="utf-8")
    if "BackgroundLocationAccess" not in text:
        errors.append(f"Background refresh is missing its permission gate: {path.relative_to(ROOT)}")

# Since 0.3.14 the optional freshness tick deliberately yields during deep idle. It must not consume
# the app-wide allow-while-idle budget also used by the voluntary emergency fallback.
refresh_scheduler_text = refresh_scheduler_path.read_text(encoding="utf-8")
if "setAndAllowWhileIdle(" in refresh_scheduler_text:
    errors.append(
        "Optional position refresh must not use setAndAllowWhileIdle; reserve that idle budget "
        "for the voluntary emergency fallback."
    )

# Exact-alarm privileges and battery-optimization exemptions are deliberately out of VeVak's
# architecture. Do not introduce a scary/high-privilege installation surface to force timing.
for forbidden_permission in (
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.USE_EXACT_ALARM",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
):
    if forbidden_permission in manifest:
        errors.append(f"Forbidden scheduling/battery permission declared: {forbidden_permission}")

# User-approved exception (2026-09-14): optional emergency feedback only.
# The default and the core incoming-SMS path remain notification-free.
feedback_permission_paths = {
    ROOT / "app/src/main/java/com/vevak/app/emergency/EmergencyFeedback.kt",
    ROOT / "app/src/main/java/com/vevak/app/ui/EmergencyFeedbackSettings.kt",
}
main_kotlin_paths = list((ROOT / "app/src/main").rglob("*.kt"))
for path in main_kotlin_paths:
    text = path.read_text(encoding="utf-8")
    if "POST_NOTIFICATIONS" in text and path not in feedback_permission_paths:
        errors.append(f"Notification permission outside optional emergency feedback: {path.relative_to(ROOT)}")

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
# second location resolver: emergency shares the canonical resolver used by normal requests.
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
    if "setAndAllowWhileIdle(" not in arm_text:
        errors.append(
            "Emergency process-death fallback must retain its inexact setAndAllowWhileIdle alarm."
        )
    for forbidden in ("VeVakPositionResolver", "VeVakLocationRepository", "OnlineApproximateLocationProvider"):
        if forbidden in arm_text:
            errors.append(f"Emergency arm controller location boundary violated: {forbidden}")

# Jasmin explicitly approved keeping a delayed voluntary emergency pending and locally cancellable
# until the private receiver claims it. Freeze the key runtime/UI contract so a later cleanup cannot
# silently revert to grace-window-only cancellation or an idle-looking tile while work is pending.
arm_state_path = ROOT / "app/src/main/java/com/vevak/app/emergency/EmergencyArmState.kt"
tile_path = ROOT / "app/src/main/java/com/vevak/app/emergency/EmergencyQuickSettingsTileService.kt"
pending_contract_path = ROOT / "docs/emergency-pending-contract.md"
if not arm_state_path.exists():
    errors.append("Approved emergency pending-state policy is missing.")
else:
    arm_state_text = arm_state_path.read_text(encoding="utf-8")
    for required in ("PENDING_SYSTEM", "isCancellable", "No expiry"):
        if required not in arm_state_text:
            errors.append(f"Emergency pending-state contract missing from policy: {required}")
if arm_path.exists():
    arm_text = arm_path.read_text(encoding="utf-8")
    if "EmergencyArmPhase.PENDING_SYSTEM" not in arm_text or "consumeIfArmed" not in arm_text:
        errors.append("Emergency receiver claim must remain gated on the PENDING_SYSTEM arm phase.")
if not tile_path.exists():
    errors.append("Quick Settings emergency tile is missing from the approved pending-state flow.")
else:
    tile_text = tile_path.read_text(encoding="utf-8")
    for required in ("Urgence en attente", "Touchez pour annuler", "cancellationShown"):
        if required not in tile_text:
            errors.append(f"Emergency tile no longer preserves cancellable pending UX: {required}")
if not pending_contract_path.exists():
    errors.append("Approved pending-emergency product contract documentation is missing.")

# Keep allow-while-idle exceptional: only the voluntary emergency fallback may use it. The optional
# position-memory refresh explicitly accepts Doze deferral instead of competing for this app-wide quota.
for path in main_kotlin_paths:
    text = path.read_text(encoding="utf-8")
    if path != arm_path and "setAndAllowWhileIdle(" in text:
        errors.append(
            "allow-while-idle boundary violated outside EmergencyShortcutArmController: "
            f"{path.relative_to(ROOT)}"
        )

# Best-effort periodic memory is allowed, but VeVak still rejects repeating/exact polling frameworks
# and WorkManager loops. The implementation must schedule one future tick at a time instead.
for path in main_kotlin_paths:
    text = path.read_text(encoding="utf-8")
    for forbidden in (
        "PeriodicWorkRequest",
        "WorkManager.getInstance",
        "AlarmManager.setRepeating",
        "AlarmManager.setExact",
        "AlarmManager.setExactAndAllowWhileIdle",
        "startForegroundService",
    ):
        if forbidden in text:
            errors.append(f"Periodic/background scheduling boundary violated by {path.relative_to(ROOT)}: {forbidden}")

if errors:
    print("Ec-design boundary verification failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Ec-design boundary verification: OK")
