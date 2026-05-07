#!/usr/bin/env python3
"""Export tasks.json to a CSV for editing.

Usage:
    python3 tasks_json_to_csv.py task_data/tasks.json task_data/tasks_edit.csv

The husband edits tasks_edit.csv, then run tasks_csv_to_json_merge.py to merge back.

Editable columns (round-trip through CSV):
  id            - leave as-is; leave blank for brand-new rows
  source        - COLLECTION_LOG or COMBAT_ACHIEVEMENT
  tier          - EASY / MEDIUM / HARD / ELITE / MASTER / GRANDMASTER
  name          - display name
  prereqs       - quest/skill requirements (plain text)
  tip           - short hint shown in the plugin
  wikiTitle     - wiki page title used for the link
  wikiUrl       - full wiki URL
  displayItemId - item ID for the icon sprite

NOT in CSV (preserved unchanged in JSON):
  verification, imageLink
"""
import csv
import json
import sys
from pathlib import Path

FIELDS = [
    "id",
    "source",
    "tier",
    "name",
    "prereqs",
    "tip",
    "wikiTitle",
    "wikiUrl",
    "displayItemId",
]


def main():
    if len(sys.argv) != 3:
        print("Usage: tasks_json_to_csv.py input_tasks.json output.csv")
        sys.exit(2)

    in_path = Path(sys.argv[1])
    out_path = Path(sys.argv[2])

    data = json.loads(in_path.read_text(encoding="utf-8"))
    tasks = data.get("tasks", [])
    if not isinstance(tasks, list):
        raise SystemExit("Invalid JSON: expected top-level { tasks: [...] }")

    with out_path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for t in tasks:
            row = {}
            for k in FIELDS:
                v = t.get(k, "")
                row[k] = "" if v is None else v
            w.writerow(row)

    print(f"Wrote {len(tasks)} rows to {out_path}")
    print(f"version in JSON: {data.get('version', '?')}")


if __name__ == "__main__":
    main()
