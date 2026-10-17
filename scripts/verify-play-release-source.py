#!/usr/bin/env python3
"""Fail a Play hand-off when source accidentally reverts to the older emergency UX."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
main = root / 'app/src/main/java/com/vevak/app'
gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
controller = (main / 'emergency/EmergencyShortcutArmController.kt').read_text(encoding='utf-8')
states = (main / 'emergency/EmergencyArmState.kt').read_text(encoding='utf-8')
shortcut = (main / 'emergency/EmergencyShortcutManager.kt').read_text(encoding='utf-8')
onboarding = (main / 'ui/VeVakBetaRoot.kt').read_text(encoding='utf-8')
safety = (main / 'ui/SafetyCenterActivity.kt').read_text(encoding='utf-8')
tile = (main / 'emergency/EmergencyQuickSettingsTileService.kt').read_text(encoding='utf-8')
version = re.search(r'versionCode\s*=\s*(\d+)', gradle)
assert version and int(version.group(1)) > 17, 'Play versionCode must advance beyond uploaded 17'
assert 'EmergencyShortcutPreset.Sms' in onboarding and 'EmergencyShortcutPreset.Sms' in safety
assert 'enum class EmergencyShortcutPreset' in shortcut and 'Sms("SMS urgence"' in shortcut
assert not re.search(r'^\s*(?:Notes|Folder|Clock|Tools)\(', shortcut, re.M), 'Old icon presets in release'
assert 'presetName' not in onboarding and 'selectedShortcutPreset' not in safety, 'Icon chooser remains'
assert 'GRACE_PERIOD_MILLIS' not in controller and 'delay(' not in controller, 'Emergency delay remains'
assert 'appContext.sendBroadcast(' in controller, 'Immediate emergency broadcast missing'
assert 'EmergencyArmPhase.CANCEL_WINDOW' not in tile and 'CANCEL_WINDOW' not in states
for name, text in [('onboarding', onboarding), ('safety', safety)]:
    assert '4 secondes' not in text, f'{name} still advertises four-second delay'
print('Play hand-off source verified: unique SMS shortcut, immediate dispatch, new versionCode')
