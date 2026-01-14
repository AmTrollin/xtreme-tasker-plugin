#!/usr/bin/env python3
import csv
import json
import hashlib
import sys
from pathlib import Path
import re
import urllib.parse
import urllib.request
import io
import html
from html.parser import HTMLParser
from typing import Optional, Tuple, List, Dict, Set

VALID_SOURCES = {"COMBAT_ACHIEVEMENT", "COLLECTION_LOG"}
VALID_TIERS = {"EASY", "MEDIUM", "HARD", "ELITE", "MASTER", "GRANDMASTER"}

WIKI_API = "https://oldschool.runescape.wiki/api.php"
WIKI_PAGE_BASE = "https://oldschool.runescape.wiki/w/"

# -------------------- in-memory caches (BIG perf win) --------------------
_HTML_CACHE: Dict[str, Optional[str]] = {}
_LINKS_CACHE: Dict[tuple, List[str]] = {}
_QUEST_REQ_CACHE: Dict[str, Optional[str]] = {}
_SEARCH_CACHE: Dict[str, Optional[str]] = {}
_EXTRACT_CACHE: Dict[str, Optional[str]] = {}


# -------------------- utils --------------------

def slug(s: str) -> str:
    s = s.strip().lower()
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"[^a-z0-9]+", "-", s)
    s = re.sub(r"-+", "-", s).strip("-")
    return s


def normalize_tier(tier: str) -> str:
    t = tier.strip().upper()
    return "MASTER" if t == "GRANDMASTER" else t


def stable_row_id(source: str, tier: str, name: str, occurrence: int) -> str:
    base = f"{source}|{tier}|{name}|{occurrence}".strip().lower()
    short = hashlib.sha1(base.encode("utf-8")).hexdigest()[:10]
    return f"{source.lower()}_{tier.lower()}_{slug(name)[:40]}_{occurrence:03d}_{short}"



def http_get_json(url: str, timeout_s: int = 25) -> dict:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "XtremeTaskerTaskBuilder/1.0 (local script)",
            "Accept": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        data = resp.read().decode("utf-8")
        return json.loads(data)


class _HTMLText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self._skip = False

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style"):
            self._skip = True
        if tag in ("p", "br", "li"):
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in ("script", "style"):
            self._skip = False
        if tag in ("p", "ul", "ol"):
            self.parts.append("\n")

    def handle_data(self, data):
        if not self._skip:
            self.parts.append(data)


def html_to_text(s: str) -> str:
    parser = _HTMLText()
    parser.feed(s)
    text = "".join(parser.parts)
    text = html.unescape(text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"[ \t]{2,}", " ", text)
    return text.strip()


def wiki_title_to_url(title: str) -> str:
    return WIKI_PAGE_BASE + urllib.parse.quote(title.replace(" ", "_"))


def read_csv_text_handling_table1(path: Path) -> str:
    raw = path.read_text(encoding="utf-8", errors="replace").splitlines()
    while raw and not raw[0].strip():
        raw.pop(0)
    if raw and raw[0].strip() == "Table 1":
        raw = raw[1:]
    return "\n".join(raw) + "\n"


# -------------------- wiki helpers (cached) --------------------

def wiki_search_best_title(query: str, srnamespace: int = 0) -> Optional[str]:
    key = f"{srnamespace}::{query}".strip().lower()
    if key in _SEARCH_CACHE:
        return _SEARCH_CACHE[key]

    params = {
        "action": "query",
        "format": "json",
        "list": "search",
        "srsearch": query,
        "srlimit": "1",
        "srprop": "",
        "srnamespace": str(srnamespace),
    }
    url = WIKI_API + "?" + urllib.parse.urlencode(params)
    data = http_get_json(url)
    results = data.get("query", {}).get("search", [])
    title = results[0].get("title") if results else None
    _SEARCH_CACHE[key] = title
    return title


def wiki_fetch_extract(title: str, chars: int = 520) -> Optional[str]:
    key = f"{title}::{chars}"
    if key in _EXTRACT_CACHE:
        return _EXTRACT_CACHE[key]

    params = {
        "action": "query",
        "format": "json",
        "prop": "extracts",
        "titles": title,
        "exintro": "1",
        "explaintext": "1",
        "exchars": str(chars),
        "redirects": "1",
    }
    url = WIKI_API + "?" + urllib.parse.urlencode(params)
    data = http_get_json(url)
    pages = data.get("query", {}).get("pages", {})
    extract = None
    for _, page in pages.items():
        extract = page.get("extract")
        if extract:
            extract = extract.strip()
            break

    _EXTRACT_CACHE[key] = extract
    return extract


def wiki_parse_html(title: str) -> Optional[str]:
    if title in _HTML_CACHE:
        return _HTML_CACHE[title]

    params = {
        "action": "parse",
        "format": "json",
        "page": title,
        "prop": "text",
        "redirects": "1",
    }
    url = WIKI_API + "?" + urllib.parse.urlencode(params)
    data = http_get_json(url)
    blob = (data.get("parse", {}).get("text", {}) or {}).get("*")
    _HTML_CACHE[title] = blob
    return blob


def wiki_parse_links(title: str, section: str = "0", limit: int = 60) -> List[str]:
    key = (title, section, limit)
    if key in _LINKS_CACHE:
        return _LINKS_CACHE[key]

    params = {
        "action": "parse",
        "format": "json",
        "page": title,
        "prop": "links",
        "section": section,
        "redirects": "1",
    }
    url = WIKI_API + "?" + urllib.parse.urlencode(params)
    data = http_get_json(url)
    links = data.get("parse", {}).get("links", []) or []

    out = []
    for l in links:
        ns = l.get("ns")
        t = l.get("*")
        if ns == 0 and t:
            out.append(t)
        if len(out) >= limit:
            break

    _LINKS_CACHE[key] = out
    return out


# -------------------- Requirements table row (quest Details) --------------------

_REQ_ROW_RE = re.compile(
    r'(<tr[^>]*>\s*.*?<th[^>]*>\s*Requirements\s*</th>\s*.*?</tr>)',
    re.DOTALL | re.IGNORECASE
)
_TD_RE = re.compile(r'<td[^>]*>(.*?)</td>', re.DOTALL | re.IGNORECASE)


def extract_requirements_row_any_table(html_blob: str) -> Optional[str]:
    if not html_blob:
        return None

    row_m = _REQ_ROW_RE.search(html_blob)
    if not row_m:
        return None

    row_html = row_m.group(1)
    td_m = _TD_RE.search(row_html)
    if not td_m:
        return None

    td_html = td_m.group(1)
    req_text = html_to_text(td_html)
    req_text = re.sub(r"\s+", " ", req_text).strip()
    return req_text if req_text else None


def quest_requirements_text(quest_title: str) -> Optional[str]:
    if quest_title in _QUEST_REQ_CACHE:
        return _QUEST_REQ_CACHE[quest_title]

    html_blob = wiki_parse_html(quest_title)
    if not html_blob:
        _QUEST_REQ_CACHE[quest_title] = None
        return None

    req = extract_requirements_row_any_table(html_blob)
    _QUEST_REQ_CACHE[quest_title] = req
    return req


# -------------------- CL item link logic --------------------

def parse_collection_log_item_from_task_name(task_name: str) -> str:
    s = task_name.strip()

    m = re.match(r"^Get\s+(.+?)\s+from\s+.+$", s, re.IGNORECASE)
    if m:
        item = m.group(1).strip()
    else:
        m2 = re.match(r"^Get\s+(.+)$", s, re.IGNORECASE)
        item = m2.group(1).strip() if m2 else s

    item = re.split(r"\s*\+\s*", item)[0].strip()
    item = re.sub(r"^(a|an|the)\s+", "", item, flags=re.IGNORECASE)
    item = re.sub(r"^\d+\s+", "", item)

    return item.strip()


def wiki_item_title_for_collection_log(task_name: str) -> Optional[str]:
    item = parse_collection_log_item_from_task_name(task_name)
    if not item:
        return None
    return wiki_search_best_title(item, srnamespace=0)


# -------------------- prereqs (pass 2 only) --------------------

_TO_PLAY_RE = re.compile(r"\bTo play\b", re.IGNORECASE)

def find_first_minigame_like_link(item_title: str, link_limit: int = 50) -> Optional[str]:
    links = wiki_parse_links(item_title, section="0", limit=link_limit)
    for t in links:
        h = wiki_parse_html(t)
        if not h:
            continue
        if _TO_PLAY_RE.search(h):
            return t
    return None


def extract_to_play_sentence(minigame_html: str) -> Optional[str]:
    if not minigame_html:
        return None

    m = re.search(r"(To play[^<]{0,600})", minigame_html, re.IGNORECASE)
    if not m:
        txt = html_to_text(minigame_html)
        m2 = re.search(r"(To play[^.]{0,600}\.)", txt, re.IGNORECASE)
        if not m2:
            return None
        s = m2.group(1).strip()
        return re.sub(r"\s+", " ", s)

    s = html_to_text(m.group(1))
    s = re.sub(r"\s+", " ", s).strip()
    return s


def parse_first_quest_title_from_minigame(minigame_title: str) -> Optional[str]:
    # cheap heuristic: first linked page that has a Requirements row
    links = wiki_parse_links(minigame_title, section="0", limit=80)
    for t in links:
        req = quest_requirements_text(t)
        if req:
            return t
    return None


def build_prereqs_for_collection_log(item_title: str) -> str:
    minigame_title = find_first_minigame_like_link(item_title)
    if not minigame_title:
        return "None"

    minigame_html = wiki_parse_html(minigame_title)
    to_play_line = extract_to_play_sentence(minigame_html) if minigame_html else None

    quest_title = parse_first_quest_title_from_minigame(minigame_title)
    quest_req = quest_requirements_text(quest_title) if quest_title else None

    parts: List[str] = []
    if quest_title and quest_req:
        parts.append(f"{quest_title}: {quest_req}")

    if to_play_line:
        if len(to_play_line) > 240:
            to_play_line = to_play_line[:240].rstrip() + "…"
        parts.append(f"{minigame_title}: {to_play_line}")

    return "\n".join(parts) if parts else "None"


def build_prereqs_for_task(task: dict) -> str:
    title = task.get("wikiTitle")
    source = task.get("source")
    if not title or not source:
        return "None"

    if source == "COLLECTION_LOG":
        return build_prereqs_for_collection_log(title)

    # CA prereqs: you said you still want prereqs eventually, but for now leave.
    return "None"


# -------------------- Pass 1: CSV -> JSON (fast) --------------------

def pass1_build_from_csv(csv_path: Path, out_json: Path, enrich_wiki: bool) -> None:
    tasks: List[dict] = []
    errors: List[str] = []

    csv_text = read_csv_text_handling_table1(csv_path)
    f = io.StringIO(csv_text)
    reader = csv.DictReader(f)

    if not reader.fieldnames:
        print("ERROR: CSV appears to have no header row.")
        sys.exit(1)

    header_map = {}
    for h in reader.fieldnames:
        if h is None:
            continue
        header_map[h.strip().lower()] = h

    def get(row, key: str):
        h = header_map.get(key)
        return (row.get(h) if h else None)

    occurrences: Dict[str, int] = {}

    for i, row in enumerate(reader, start=2):
        source = ((get(row, "source") or "")).strip().upper()
        tier_raw = ((get(row, "tier") or "")).strip().upper()
        name = ((get(row, "name") or "")).strip()

        # ✅ pull these from CSV (husband file already has them)
        prereqs = ((get(row, "prereqs") or "")).strip()
        wiki_title_csv = ((get(row, "wikititle") or "")).strip()
        wiki_url_csv = ((get(row, "wikiurl") or "")).strip()
        desc_csv = ((get(row, "description") or "")).strip()

        icon_item_id_raw = ((get(row, "iconitemid") or "")).strip()
        icon_key = ((get(row, "iconkey") or "")).strip() or None

        # skip blank rows
        if not source and not tier_raw and not name and not icon_item_id_raw and not icon_key:
            continue

        if source not in VALID_SOURCES:
            errors.append(f"Line {i}: invalid source '{source}'")
            continue

        if tier_raw not in VALID_TIERS:
            errors.append(f"Line {i}: invalid tier '{tier_raw}'")
            continue

        if not name:
            errors.append(f"Line {i}: missing name")
            continue

        tier = normalize_tier(tier_raw)

        # ✅ unique-per-occurrence id (allows intentional duplicates)
        key = f"{source}|{tier}|{name}".strip().lower()
        occurrences[key] = occurrences.get(key, 0) + 1
        occ = occurrences[key]
        tid = stable_row_id(source, tier, name, occ)

        task = {
            "id": tid,
            "name": name,
            "source": source,
            "tier": tier,
        }

        if icon_item_id_raw:
            try:
                task["iconItemId"] = int(icon_item_id_raw)
            except ValueError:
                errors.append(f"Line {i}: iconItemId must be an integer, got '{icon_item_id_raw}'")
                continue

        if icon_key:
            task["iconKey"] = icon_key

        # ✅ ALWAYS keep prereqs from CSV
        task["prereqs"] = prereqs if prereqs else "None"

        # ✅ Always copy wiki fields from CSV if present
        if wiki_title_csv:
            task["wikiTitle"] = wiki_title_csv
        if wiki_url_csv:
            task["wikiUrl"] = wiki_url_csv
        if desc_csv:
            task["description"] = desc_csv

        # ✅ Optionally fill missing wiki fields via wiki calls
        if enrich_wiki:
            # Only do lookups if fields are missing
            if not task.get("wikiTitle"):
                primary_title: Optional[str] = None
                if source == "COMBAT_ACHIEVEMENT":
                    primary_title = wiki_search_best_title(name, srnamespace=0)
                else:
                    primary_title = wiki_item_title_for_collection_log(name)

                if primary_title:
                    task["wikiTitle"] = primary_title

            if task.get("wikiTitle") and not task.get("wikiUrl"):
                task["wikiUrl"] = wiki_title_to_url(task["wikiTitle"])

            # Only CA gets description (your original behavior)
            if source == "COMBAT_ACHIEVEMENT" and task.get("wikiTitle") and not task.get("description"):
                desc = wiki_fetch_extract(task["wikiTitle"], chars=520)
                if desc:
                    task["description"] = desc

        tasks.append(task)

        if i % 50 == 0:
            print(f"Pass1 processed up to CSV line {i}...")

    if errors:
        print("Errors:")
        for e in errors:
            print(" -", e)
        sys.exit(1)

    out = {"version": 1, "tasks": tasks}
    out_json.write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Pass1 wrote {len(tasks)} tasks to {out_json}")



# -------------------- Pass 2: Fill prereqs in existing JSON --------------------

def pass2_fill_prereqs(in_json: Path, out_json: Path, limit: Optional[int]) -> None:
    data = json.loads(in_json.read_text(encoding="utf-8"))
    tasks = data.get("tasks") or []
    if not isinstance(tasks, list):
        raise ValueError("Invalid tasks.json format: expected {tasks: [...]}")

    total = len(tasks)
    processed = 0

    for idx, task in enumerate(tasks):
        if limit is not None and processed >= limit:
            break

        # Only compute if missing or "None"
        if str(task.get("prereqs", "")).strip() and task.get("prereqs") != "None":
            continue

        # Need wikiTitle to do anything
        if not task.get("wikiTitle"):
            task["prereqs"] = "None"
            processed += 1
            continue

        try:
            task["prereqs"] = build_prereqs_for_task(task)
        except Exception:
            task["prereqs"] = "None"

        processed += 1
        if (idx + 1) % 20 == 0:
            print(f"Pass2 progress: {idx+1}/{total} tasks scanned, {processed} prereqs attempted...")

    data["tasks"] = tasks
    out_json.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Pass2 wrote updated prereqs to {out_json} (attempted {processed} tasks)")


# -------------------- CLI --------------------

def main():
    """
    Two modes:

      PASS 1 (fast, from CSV):
        python3 csv_to_tasks_json.py pass1 input.csv output.json --enrich-wiki

      PASS 2 (fill prereqs into existing json):
        python3 csv_to_tasks_json.py pass2 input.json output.json [--limit N]

    """
    if len(sys.argv) < 2:
        print(main.__doc__)
        sys.exit(2)

    mode = sys.argv[1].strip().lower()

    if mode == "pass1":
        if len(sys.argv) < 4:
            print(main.__doc__)
            sys.exit(2)
        in_csv = Path(sys.argv[2])
        out_json = Path(sys.argv[3])
        enrich = ("--enrich-wiki" in sys.argv[4:])
        pass1_build_from_csv(in_csv, out_json, enrich_wiki=enrich)
        return

    if mode == "pass2":
        if len(sys.argv) < 4:
            print(main.__doc__)
            sys.exit(2)
        in_json = Path(sys.argv[2])
        out_json = Path(sys.argv[3])

        limit = None
        if "--limit" in sys.argv:
            i = sys.argv.index("--limit")
            if i + 1 >= len(sys.argv):
                print("ERROR: --limit requires a number")
                sys.exit(2)
            limit = int(sys.argv[i + 1])

        pass2_fill_prereqs(in_json, out_json, limit=limit)
        return

    print(main.__doc__)
    sys.exit(2)


if __name__ == "__main__":
    main()
