#!/usr/bin/env python3
"""Coverage of the Noxio source files listed in noxio-code.txt, taken from the all-code JaCoCo report.

Usage: noxio_coverage.py REPORT.xml noxio-code.txt [--badge OUTPUT.json LABEL]

Prints the uncovered files and the total. With --badge it also writes a shields.io endpoint file,
like coverage_badge.py does for the all-code number. Files are matched by their path below
org/oxycblt/auxio/, so classes with other names than their file are counted too.
"""
import json
import sys
import xml.etree.ElementTree as ET

from coverage_badge import colour

PREFIX = "org/oxycblt/auxio/"


def listed(path: str) -> set:
    with open(path, encoding="utf-8") as f:
        return {l.strip() for l in f if l.strip() and not l.startswith("#")}


def main() -> None:
    report, files = sys.argv[1], listed(sys.argv[2])
    rows = []
    for package in ET.parse(report).getroot().findall("package"):
        name = package.get("name")
        if not name.startswith(PREFIX.rstrip("/")):
            continue
        folder = name[len(PREFIX):] + "/" if len(name) > len(PREFIX) else ""
        for source in package.findall("sourcefile"):
            key = folder + source.get("name")
            line = next((c for c in source.findall("counter") if c.get("type") == "LINE"), None)
            if key in files and line is not None:
                rows.append((key, int(line.get("missed")), int(line.get("covered"))))
    missing = files - {r[0] for r in rows}
    if missing:
        print("Listed but not in the report (no code, or the name is wrong):", ", ".join(sorted(missing)))
    for key, missed, covered in sorted(rows, key=lambda r: -r[1]):
        if missed:
            print(f"{missed:5d} missed {covered:5d} covered  {key}")
    missed = sum(r[1] for r in rows)
    covered = sum(r[2] for r in rows)
    total = missed + covered
    percent = 100.0 * covered / total if total else 0.0
    print(f"Noxio code: {covered}/{total} lines covered ({percent:.1f}%)")
    if "--badge" in sys.argv:
        out, label = sys.argv[sys.argv.index("--badge") + 1 : sys.argv.index("--badge") + 3]
        with open(out, "w", encoding="utf-8") as f:
            json.dump({"schemaVersion": 1, "label": label, "message": f"{percent:.0f}%", "color": colour(percent)}, f)


if __name__ == "__main__":
    sys.path.insert(0, __file__.rsplit("/", 1)[0])
    main()
