#!/usr/bin/env python3
"""Compile the Plata Clan dialogue sources (tools/lines/*.txt) into plata_lines.json.

Source format:
    # a comment
    [pool.name]                 starts a pool (a pool may continue in another file)
    One entry per line.
    {warden}A rank-tagged line  (fight.* and defeat pools only)

Usage (Python 3.10+, stdlib only):
    python tools/lines/build_lines.py           validate, then write the JSON
    python tools/lines/build_lines.py --check   validate and fail if the JSON is stale; writes nothing
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
OUT = ROOT / "src" / "main" / "resources" / "data" / "plataclan" / "plata_lines.json"

# Every pool the mod reads, with its minimum size. Anything else is a typo.
POOLS = {
    "disguise.greet.sand": 12,
    "disguise.greet.stone": 12,
    "disguise.greet.green": 12,
    "disguise.greet.cold": 12,
    "disguise.greet.any": 20,
    "disguise.reply.polite": 8,
    "disguise.reply.curious": 8,
    "disguise.reply.suspicious": 8,
    "reveal.taunt.polite": 10,
    "reveal.taunt.curious": 10,
    "reveal.taunt.suspicious": 10,
    "reveal.taunt.rude": 10,
    "reveal.taunt.timeout": 8,
    "reveal.first": 6,
    "fight.burrow": 10,
    "fight.hurt": 10,
    "fight.molt": 8,
    "fight.call": 8,
    "fight.dodge": 8,
    "fight.win": 8,
    "fight.flee": 8,
    "defeat": 12,
    "kin.whisper": 20,
    "kin.reply": 4,
    "hideout.wake": 12,
    "hideout.elder": 8,
    "hideout.reseal": 8,
}

RANKS = ("scuttler", "shardcaster", "warden")

# Placeholders and their longest likely expansion, used when measuring length limits.
PLACEHOLDERS = {"player": 16, "name": 16, "rank": 11}

# (pool prefix, max characters). Reply labels are buttons; taunts and fight lines are speech lines.
LIMITS = (
    ("disguise.reply.", 32),
    ("kin.reply", 32),
    ("reveal.taunt.", 90),
    ("reveal.first", 60),
    ("fight.", 90),
    ("defeat", 90),
    ("hideout.", 90),
)

# The clan is inspired by, never copied from, another franchise's ninja clan. Keep its names out.
BANNED = re.compile(
    r"\b(yiga|kohga|sheikah|hylian?|hyrule|bananas?|link|footsoldiers?|blademasters?)\b", re.IGNORECASE
)

HEADER = re.compile(r"^\[([^\]]*)\]$")
POOL_NAME = re.compile(r"^[a-z]+(\.[a-z]+)*$")
TOKEN = re.compile(r"\{([^{}]*)\}")
LEAD_TAG = re.compile(r"^\{([^{}]*)\}\s*")


def may_rank(pool: str) -> bool:
    return pool.startswith("fight.") or pool.startswith("hideout.") or pool == "defeat"


def limit_for(pool: str) -> int | None:
    for prefix, limit in LIMITS:
        if pool.startswith(prefix):
            return limit
    return None


def display_len(text: str) -> int:
    return len(TOKEN.sub(lambda m: "x" * PLACEHOLDERS.get(m.group(1), len(m.group(0))), text))


def parse(files: list[Path], errors: list[str]) -> dict[str, list[dict]]:
    pools: dict[str, list[dict]] = {}
    for path in files:
        pool = None
        for n, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            where = f"{path.name}:{n}"
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            header = HEADER.match(line)
            if header:
                pool = header.group(1).strip()
                if not POOL_NAME.match(pool):
                    errors.append(f"{where}: bad pool name [{pool}]")
                elif pool not in POOLS:
                    errors.append(f"{where}: unknown pool [{pool}]")
                pools.setdefault(pool, [])
                continue
            if pool is None:
                errors.append(f"{where}: entry before any [pool] header")
                continue

            rank = None
            tag = LEAD_TAG.match(line)
            if tag and tag.group(1) not in PLACEHOLDERS:
                name = tag.group(1)
                if name not in RANKS:
                    errors.append(f"{where}: unknown rank tag {{{name}}}")
                elif not may_rank(pool):
                    errors.append(f"{where}: rank tag {{{name}}} not allowed in [{pool}]")
                else:
                    rank = name
                line = line[tag.end():]

            for token in TOKEN.findall(line):
                if token not in PLACEHOLDERS:
                    errors.append(f"{where}: unknown placeholder {{{token}}}")
            if "{" in TOKEN.sub("", line) or "}" in TOKEN.sub("", line):
                errors.append(f"{where}: unbalanced brace")
            if not line:
                errors.append(f"{where}: empty entry")
                continue
            pools[pool].append({"text": line, "rank": rank, "where": where})
    return pools


def validate(pools: dict[str, list[dict]], errors: list[str]) -> None:
    for pool, minimum in POOLS.items():
        entries = pools.get(pool)
        if entries is None:
            errors.append(f"missing pool [{pool}]")
            continue
        if len(entries) < minimum:
            errors.append(f"[{pool}] has {len(entries)} lines, needs at least {minimum}")
        if may_rank(pool):
            for rank in RANKS:
                usable = sum(1 for e in entries if e["rank"] in (None, rank))
                if usable < minimum:
                    errors.append(f"[{pool}] gives {rank} only {usable} lines, needs at least {minimum}")

    seen: dict[str, str] = {}
    for pool, entries in pools.items():
        limit = limit_for(pool)
        for e in entries:
            text, where = e["text"], e["where"]
            if limit is not None and display_len(text) > limit:
                errors.append(f"{where}: [{pool}] line is {display_len(text)} chars, limit {limit}: {text}")
            key = " ".join(text.casefold().split())
            if key in seen:
                errors.append(f"{where}: duplicate of {seen[key]}: {text}")
            else:
                seen[key] = where
            if BANNED.search(text):
                errors.append(f"{where}: banned word '{BANNED.search(text).group(0)}': {text}")
            if "  " in text:
                errors.append(f"{where}: double space")


def render(pools: dict[str, list[dict]]) -> str:
    data = {
        "format": 1,
        "pools": {
            pool: [{"text": e["text"], "rank": e["rank"]} for e in pools[pool]]
            for pool in sorted(pools)
            if pool in POOLS
        },
    }
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="validate and fail if the JSON is stale; write nothing")
    args = ap.parse_args()

    files = sorted(HERE.glob("*.txt"), key=lambda p: p.name)
    errors: list[str] = []
    if not files:
        errors.append(f"no .txt sources in {HERE}")
    pools = parse(files, errors)
    validate(pools, errors)
    out = render(pools)

    if args.check and not errors:
        current = OUT.read_text(encoding="utf-8") if OUT.exists() else None
        if current != out:
            errors.append(f"{OUT.relative_to(ROOT)} is stale; run build_lines.py")

    if errors:
        for e in errors:
            print(f"error: {e}", file=sys.stderr)
        print(f"{len(errors)} error(s)", file=sys.stderr)
        return 1

    total = sum(len(pools[p]) for p in POOLS)
    if args.check:
        print(f"ok: {len(POOLS)} pools, {total} lines, {OUT.relative_to(ROOT)} is up to date")
        return 0

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(out, encoding="utf-8", newline="\n")
    for pool in POOLS:
        print(f"  {pool:28} {len(pools[pool]):3}")
    print(f"wrote {OUT.relative_to(ROOT)}: {len(POOLS)} pools, {total} lines")
    return 0


if __name__ == "__main__":
    sys.exit(main())
