#!/usr/bin/env python3
import csv
import json
import sys
from pathlib import Path

UPDATE_FIELDS = ["prereqs", "wikiTitle", "wikiUrl", "description"]

def norm(s):
    if s is None:
        return None
    s = str(s).strip()
    return s if s else None

def main():
    if len(sys.argv) != 4:
        print("Usage: tasks_csv_to_json_merge.py original_tasks.json edited.csv output_tasks.json")
        sys.exit(2)

    original_json = Path(sys.argv[1])
    edited_csv = Path(sys.argv[2])
    out_json = Path(sys.argv[3])

    data = json.loads(original_json.read_text(encoding="utf-8"))
    tasks = data.get("tasks", [])
    if not isinstance(tasks, list):
        raise SystemExit("Invalid JSON: expected top-level { tasks: [...] }")

    by_id = {t.get("id"): t for t in tasks if t.get("id")}
    if not by_id:
        raise SystemExit("No tasks with id found in original JSON.")

    updated = 0
    missing = 0

    with edited_csv.open("r", newline="", encoding="utf-8") as f:
        r = csv.DictReader(f)
        if not r.fieldnames or "id" not in r.fieldnames:
            raise SystemExit("Edited CSV must have an 'id' column.")

        for row in r:
            tid = norm(row.get("id"))
            if not tid:
                continue

            t = by_id.get(tid)
            if not t:
                missing += 1
                continue

            changed_any = False
            for field in UPDATE_FIELDS:
                val = norm(row.get(field))
                if val is None:
                    continue  # don't overwrite with blanks
                if t.get(field) != val:
                    t[field] = val
                    changed_any = True

            if changed_any:
                updated += 1

    out_json.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Updated {updated} tasks. CSV ids missing from JSON: {missing}. Wrote {out_json}")

if __name__ == "__main__":
    main()


# python3 utils/tasks_csv_to_json_merge.py task_data/tasks.json task_data/tasks_review.csv task_data/tasks.json
