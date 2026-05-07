#!/usr/bin/env python3
"""Merge an edited CSV back into tasks.json, then bump the version.

Typical workflow
----------------
1. Export:  python3 tasks_json_to_csv.py task_data/tasks.json task_data/tasks_edit.csv
2. Husband edits tasks_edit.csv (add rows, change name/tier/prereqs/tip etc.)
3. Import:  python3 tasks_csv_to_json_merge.py task_data/tasks.json task_data/tasks_edit.csv task_data/tasks.json

Rules
-----
- Existing tasks matched by `id` (if column present and non-empty).
  If a row has no id, falls back to matching by (source, tier, name).
- Mutable fields updated from CSV: name, source, tier, prereqs, tip,
  wikiTitle, wikiUrl, displayItemId.
- Fields NEVER overwritten from CSV: id, verification, imageLink.
- Blank CSV cells are ignored (won't overwrite existing JSON value).
- Rows that don't match any existing task → appended as NEW tasks with
  auto-generated ids.
- Tasks in JSON that have no matching CSV row are left unchanged (no deletions).
- version is auto-incremented by 1.
"""
import csv
import hashlib
import json
import re
import sys
from pathlib import Path

MUTABLE_FIELDS = ["name", "source", "tier", "prereqs", "tip", "wikiTitle", "wikiUrl", "displayItemId"]


def norm(v):
    if v is None:
        return None
    v = str(v).strip()
    return v if v else None


def slug(s: str) -> str:
    s = s.strip().lower()
    s = re.sub(r"[^a-z0-9]+", "-", s)
    return re.sub(r"-+", "-", s).strip("-")


def make_id(source: str, tier: str, name: str, existing_ids: set) -> str:
    base = f"{source.lower()}_{tier.lower()}_{slug(name)[:40]}"
    short = hashlib.sha1(f"{source}|{tier}|{name}".encode()).hexdigest()[:10]
    candidate = f"{base}_{short}"
    # ensure uniqueness
    suffix = 0
    result = candidate
    while result in existing_ids:
        suffix += 1
        result = f"{candidate}_{suffix}"
    return result


def main():
    if len(sys.argv) != 4:
        print("Usage: tasks_csv_to_json_merge.py original_tasks.json edited.csv output_tasks.json")
        sys.exit(2)

    original_json = Path(sys.argv[1])
    edited_csv    = Path(sys.argv[2])
    out_json      = Path(sys.argv[3])

    data  = json.loads(original_json.read_text(encoding="utf-8"))
    tasks = data.get("tasks", [])
    if not isinstance(tasks, list):
        raise SystemExit("Invalid JSON: expected top-level { tasks: [...] }")

    # indexes
    by_id   = {t["id"]: t for t in tasks if t.get("id")}
    by_key  = {}   # (source.upper, tier.upper, name.lower) -> task
    for t in tasks:
        k = (str(t.get("source","")).upper(), str(t.get("tier","")).upper(), str(t.get("name","")).strip().lower())
        by_key[k] = t

    all_ids = set(by_id.keys())

    updated   = 0
    added     = 0
    skipped   = 0

    with edited_csv.open("r", newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            tid = norm(row.get("id"))

            # --- find matching task ---
            task = None
            if tid:
                task = by_id.get(tid)

            if task is None:
                # fallback: match by (source, tier, name)
                src  = norm(row.get("source","")) or ""
                tier = norm(row.get("tier","")) or ""
                name = norm(row.get("name","")) or ""
                k    = (src.upper(), tier.upper(), name.lower())
                task = by_key.get(k)

            if task is not None:
                # --- update existing ---
                changed = False
                for field in MUTABLE_FIELDS:
                    val = norm(row.get(field))
                    if val is None:
                        continue  # blank cell → keep existing
                    if field == "displayItemId":
                        try:
                            val = int(val)
                        except ValueError:
                            continue
                    if task.get(field) != val:
                        task[field] = val
                        changed = True
                if changed:
                    updated += 1
            else:
                # --- new task ---
                src  = norm(row.get("source")) or ""
                tier = norm(row.get("tier"))   or ""
                name = norm(row.get("name"))
                if not name:
                    skipped += 1
                    continue   # can't add a task with no name

                new_id = tid if (tid and tid not in all_ids) else make_id(src, tier, name, all_ids)
                all_ids.add(new_id)

                new_task = {"id": new_id}
                for field in MUTABLE_FIELDS:
                    val = norm(row.get(field))
                    if val is None:
                        continue
                    if field == "displayItemId":
                        try:
                            val = int(val)
                        except ValueError:
                            continue
                    new_task[field] = val

                tasks.append(new_task)
                by_id[new_id]  = new_task
                added += 1

    # bump version
    old_version = int(data.get("version", 0))
    data["version"] = old_version + 1
    data["tasks"]   = tasks

    out_json.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"version: {old_version} → {data['version']}")
    print(f"tasks updated : {updated}")
    print(f"tasks added   : {added}")
    print(f"rows skipped  : {skipped} (blank name)")
    print(f"total tasks   : {len(tasks)}")
    print(f"wrote         : {out_json}")


if __name__ == "__main__":
    main()

# Example:
#   cd src/main/resources
#   python3 utils/tasks_json_to_csv.py task_data/tasks.json task_data/tasks_edit.csv
#   # ... edit tasks_edit.csv ...
#   python3 utils/tasks_csv_to_json_merge.py task_data/tasks.json task_data/tasks_edit.csv task_data/tasks.json
