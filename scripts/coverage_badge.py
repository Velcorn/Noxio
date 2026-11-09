#!/usr/bin/env python3
"""Turn a JaCoCo XML report into a shields.io endpoint file for the README badge.

Usage: coverage_badge.py REPORT.xml OUTPUT.json [LABEL]
The number is the share of covered lines in the report.
"""
import json
import sys
import xml.etree.ElementTree as ET


def colour(percent: float) -> str:
    for limit, name in ((90, "brightgreen"), (80, "green"), (70, "yellowgreen"), (60, "yellow"), (40, "orange")):
        if percent >= limit:
            return name
    return "red"


def main() -> None:
    report, output = sys.argv[1], sys.argv[2]
    label = sys.argv[3] if len(sys.argv) > 3 else "coverage"
    root = ET.parse(report).getroot()
    line = next(c for c in root.findall("counter") if c.get("type") == "LINE")
    missed, covered = int(line.get("missed")), int(line.get("covered"))
    total = missed + covered
    percent = 100.0 * covered / total if total else 0.0
    with open(output, "w", encoding="utf-8") as f:
        json.dump({"schemaVersion": 1, "label": label, "message": f"{percent:.0f}%", "color": colour(percent)}, f)
    print(f"{covered}/{total} lines covered ({percent:.1f}%)")


if __name__ == "__main__":
    main()
