#!/usr/bin/env python3
import csv
import json
import sys
from pathlib import Path

FIELDS = [
    "id",
    "source",
    "tier",
    "name",
    "wikiTitle",
    "wikiUrl",
    "description",
    "prereqs",
    "iconItemId",
    "iconKey",
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
            row = {k: t.get(k, "") for k in FIELDS}

            # normalize None -> ""
            for k, v in list(row.items()):
                if v is None:
                    row[k] = ""

            # make sure iconItemId is a plain int or empty
            if row.get("iconItemId") == "":
                pass
            else:
                try:
                    row["iconItemId"] = int(row["iconItemId"])
                except Exception:
                    row["iconItemId"] = ""

            w.writerow(row)

    print(f"Wrote {len(tasks)} rows to {out_path}")

if __name__ == "__main__":
    main()
