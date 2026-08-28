#!/usr/bin/env python3
"""Prevent accidental logging/printing from VeVak Android production sources."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOTS = [ROOT / "app/src/main", ROOT / "app/src/foss", ROOT / "app/src/play"]

patterns = {
    "android.util.Log call": re.compile(r"\bLog\.(?:v|d|i|w|e|wtf)\s*\("),
    "println call": re.compile(r"\b(?:System\.out\.)?println\s*\("),
    "printStackTrace call": re.compile(r"\.printStackTrace\s*\("),
}

errors: list[str] = []
for source_root in SOURCE_ROOTS:
    if not source_root.exists():
        continue
    for path in source_root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for label, pattern in patterns.items():
            if pattern.search(text):
                errors.append(f"{label}: {path.relative_to(ROOT)}")

if errors:
    print("Sensitive-log verification failed. Production sources must not emit raw runtime data:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Sensitive-log verification: OK")
