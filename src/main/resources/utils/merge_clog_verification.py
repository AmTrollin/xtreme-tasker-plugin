#!/usr/bin/env python3
"""
Merges verification data from clog_source_tasks.json into tasks.json.
Matches tasks by normalized name + tier, with ordered-list matching for
tasks that share the same name (progressive count variants).
Adds: verification, displayItemId, tip fields to matched CLOG tasks.
"""

import json
import re
from collections import defaultdict
from pathlib import Path

TASKS_JSON = Path(__file__).parent.parent / "task_data" / "tasks.json"
SOURCE_JSON = Path(__file__).parent.parent / "task_data" / "clog_source_tasks.json"

# Source tier -> tasks.json tier
TIER_MAP = {
    "easy":   "EASY",
    "medium": "MEDIUM",
    "hard":   "HARD",
    "elite":  "ELITE",
    "master": "MASTER",
}

# (normalized tasks.json name, tasks.json tier or None) -> normalized source name
# None tier means apply to any tier. Entries are checked most-specific first.
NAME_MAP = {
    # MTA wand - different wording per tier
    ("upgradethemtawandonce",                          "EASY"):   "getthenexttierofmtawand",
    ("upgradethemtawandonce",                          "MEDIUM"): "upgradetoamasterwand",
    # Boat bottle parenthetical differs
    ("getaboatbottleempty",                            None):     "getaboatbottle",
    # Sea Treasure wording
    ("getaseatreasure",                                None):     "getthenextseatreasure",
    # Alchemist outfit typo in tasks.json
    ("get1alchemistoutiftpiece",                       None):     "get1alchemistoutfitpiece",
    # Graceful recolor naming
    ("1gracefulrecolor",                               None):     "getawyrmbrimbavengracefulrecolor",
    # Rosewood schematic triple-o typo
    ("gettherosewooodcargoholdschematic",              None):     "gettherosewoodcargoholdschematic",
    # Unsired: tasks.json HARD omits "an"
    ("getauniquefromunsired",                          "HARD"):   "getauniquefromanunsired",
    # Vial of blood: tasks.json adds mode qualifier
    ("getavialofbloodentryornormalmode",               None):     "getavialofblood",
    # Gwyneth/Gwenith spelling
    ("getthenextrewardfromthegwynethglide",            None):     "getthenextrewardfromthegwenithglide",
    # Cerberus crystal/stone -> generic cerberus unique
    ("get1uniquecrystalstonefromcerberus",             None):     "get1uniquefromcerberus",
    # Fortis: extra "the"
    ("get1uniquefromthefortiscolosseum",               None):     "get1uniquefromfortiscolosseum",
    # Dragon archer headpiece: tasks.json has verbose qualifier
    ("upgradetothedragonarcherheadpieceexpertifalreadydragon", None): "upgradetothedragonarcherheadpiece",
    # Giant egg sac -> Sarachnis unique
    ("getagianteggsacfull",                            None):     "get1uniquefromsarachnis",
    # Sarachnis cudgel -> Sarachnis unique
    ("getasarachniscudgel",                            None):     "get1uniquefromsarachnis",
    # level 99 cape -> "A new level 99"
    ("1level99cape",                                   None):     "anewlevel99",
    # Minigame log slot variants (tasks.json name has "excluding LMS" qualifier)
    ("1minigamelogslotexcludinglms",                   None):     "1minigamelogslot",
    ("2minigamelogslotexcludinglms",                   None):     "1minigamelogslot",
    ("3minigamelogslotexcludinglms",                   None):     "1minigamelogslot",
    ("4minigamelogslotexcludinglms",                   None):     "1minigamelogslot",
    # LMS slots
    ("get2lmslogslots",                                None):     "get2lmsslots",
    # CoX/ToB/ToA log slots (name is same but "log" added in tasks.json)
    ("get1coxlogslot",                                 None):     "1coxlogslot",
    ("get1toblogslot",                                 None):     "1toblogslot",
    ("get1toalogslot",                                 None):     "1toalogslot",
    # Boss pet/jar
    ("get1bosspetorjar",                               None):     "1bosspetorjar",
    # Skilling pet
    ("get1skillingpet",                                None):     "1skillingpet",
    # Wildy unique
    ("get1wildyunique",                                None):     "1wildyunique",
    # Slayer log slot
    ("get1slayerlogslot",                              None):     "get1slayerlogslot",
    # Miscellaneous log slot
    ("get1miscellaneouslogslot",                       None):     "1miscellaneouslogslot",
    # Sigil from Corp
    ("1sigifromthecorporealbeast",                     None):     "1sigilfromcorp",
    ("1sigilfromthecorporealbeast",                    None):     "1sigilfromcorp",
    # DT2 bosses name mismatch
    ("1uniquefromdt2bosses",                           None):     "get1uniquefromthedt2bosses",
    # Fortis Colosseum
    ("1fortiscolosseumunique",                         None):     "1fortiscolosseumunique",
}

# Tasks with no equivalent in the source (new/sailing content) - skip silently
NO_SOURCE = {
    "getateaflask",
    "getarunesatchel",
    "getagreensatchel",
    "getaredsatchel",
    "getablacksatchel",
    "getagoldsatchel",
    "getaplainsatchel",
    "gettheguildedsmileflag",
}


def normalize(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def build_source_index(source_data: dict) -> dict:
    """
    Build a dict keyed by (normalized_name, tier) -> [list of source tasks].
    Lists preserve insertion order so same-name progressive tasks match in sequence.
    Also builds a cross-tier fallback: normalized_name -> [all source tasks].
    """
    index: dict = defaultdict(list)
    cross_tier: dict = defaultdict(list)
    for tier_key, tasks in source_data.items():
        tier = TIER_MAP.get(tier_key.lower())
        if tier is None:
            continue
        for task in tasks:
            key = (normalize(task["name"]), tier)
            index[key].append(task)
            cross_tier[normalize(task["name"])].append(task)
    return dict(index), dict(cross_tier)


def resolve_source_name(norm_tasks_name: str, tier: str) -> str:
    """Return the source normalized name to look up, applying NAME_MAP if needed."""
    # Most-specific: (name, tier)
    mapped = NAME_MAP.get((norm_tasks_name, tier))
    if mapped is not None:
        return mapped
    # Less-specific: (name, None)
    mapped = NAME_MAP.get((norm_tasks_name, None))
    if mapped is not None:
        return mapped
    return norm_tasks_name


def apply_source(task: dict, src: dict):
    verif = src.get("verification")
    if verif:
        task["verification"] = verif
    if "displayItemId" in src:
        task["displayItemId"] = src["displayItemId"]
    if src.get("tip"):
        task["tip"] = src["tip"]
    if src.get("wikiLink") and not task.get("wikiUrl"):
        task["wikiUrl"] = src["wikiLink"]
    if src.get("imageLink"):
        task["imageLink"] = src["imageLink"]


def main():
    with open(TASKS_JSON) as f:
        data = json.load(f)

    tasks = data["tasks"] if isinstance(data, dict) else data

    with open(SOURCE_JSON) as f:
        source_data = json.load(f)

    index, cross_tier = build_source_index(source_data)

    matched = 0
    cross_matched = 0
    unmatched = []

    for task in tasks:
        if task.get("source") != "COLLECTION_LOG":
            continue

        norm_name = normalize(task["name"])
        tier = task.get("tier", "")

        if norm_name in NO_SOURCE:
            continue

        src_norm = resolve_source_name(norm_name, tier)
        key = (src_norm, tier)

        # Try same-tier first (ordered list - pop front for sequential matching)
        candidates = index.get(key)
        if candidates:
            src = candidates.pop(0)
            if not candidates:
                del index[key]
            apply_source(task, src)
            matched += 1
            continue

        # Cross-tier fallback
        fallback = cross_tier.get(src_norm)
        if fallback:
            src = fallback.pop(0)
            if not fallback:
                del cross_tier[src_norm]
            apply_source(task, src)
            cross_matched += 1
            continue

        unmatched.append(f"{task['name']} [{tier}]")

    # Write back
    if isinstance(data, dict):
        data["tasks"] = tasks

    with open(TASKS_JSON, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

    print(f"Matched (same tier):  {matched}")
    print(f"Matched (cross-tier): {cross_matched}")
    print(f"Unmatched ({len(unmatched)}) — no source data:")
    for name in unmatched:
        print(f"  {name}")


if __name__ == "__main__":
    main()
