#!/usr/bin/env python3
"""Validate VeVak's hand-authored neutral shortcut vectors.

Papirus was used only for an earlier prototype. Runtime shortcut icons are now original,
flat VectorDrawable resources and must not be overwritten by the archived converter.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NAMES = ("notes", "list", "clock", "folder", "tools", "memo")
MARKER = "Original VeVak neutral utility icon"

errors = []
for name in NAMES:
    path = ROOT / f"app/src/main/res/drawable/ic_shortcut_{name}.xml"
    if not path.exists():
        errors.append(f"Missing shortcut icon: {path.relative_to(ROOT)}")
        continue
    text = path.read_text(encoding="utf-8")
    if MARKER not in text:
        errors.append(f"Shortcut icon is not the approved original flat vector: {path.relative_to(ROOT)}")
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        errors.append(f"Invalid XML in {path.relative_to(ROOT)}: {exc}")
        continue
    android = "{http://schemas.android.com/apk/res/android}"
    if root.attrib.get(android + "viewportWidth") != "108" or root.attrib.get(android + "viewportHeight") != "108":
        errors.append(f"Unexpected viewport in {path.relative_to(ROOT)}; keep the 108×108 launcher-safe canvas.")

if errors:
    raise SystemExit("\n".join(errors))

print("Shortcut vector validation: OK")
