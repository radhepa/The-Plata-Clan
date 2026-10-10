"""Compiles the Plata clips into Village Friends animation packs (format 1).

    python tools/animations/build_clips.py            write both packs
    python tools/animations/build_clips.py --check    validate, and confirm the packs on disk are current
    python tools/animations/build_clips.py --list     list the clips with their lengths

Outputs:
    src/client/resources/assets/plataclan/plata_moves/plata.json           every fighter clip (plata_moves.py)
    src/client/resources/assets/plataclan/resident_animations/plata.json   only the Molt (molt.py), VF pack plataclan.plata
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import kit  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, "src", "client", "resources", "assets", "plataclan")
MAX_ROT, MAX_POS, MAX_LEN = 400, 12, 30

# Clip ids and lengths the game times damage and movement against (DESIGN.md section 9, Moves/Reveal).
MOVES = {"stance": 2.0, "unfurl": 1.1, "ready": 0.4, "flurry_1": 0.45, "flurry_2": 0.45, "flurry_3": 0.45,
         "flurry_4": 0.5, "lunge": 0.9, "backflip": 0.7, "sideroll": 0.6, "burrow_dive": 0.6, "emerge": 0.9,
         "throw_fan": 0.8, "kick": 0.6, "lash": 1.3, "slam": 1.4, "call": 1.2, "stagger": 1.0, "taunt": 1.6,
         "defeat": 1.4}
MOLT = {"molt": 1.3}
# The Silver Mother's moves (MotherMove in the game).
MOTHER = {"stance": 2.4, "curl": 4.0, "rise": 2.5, "crush": 2.4, "sweep": 1.6, "keen": 2.2, "rain": 1.6, "brood": 1.6,
          "burrow": 1.0, "erupt": 1.2, "shed": 2.6, "stagger": 1.4, "defeat": 3.0}
# Clips allowed to start (or end) away from the stance: the contract puts them somewhere else.
FREE_START = {"unfurl", "emerge"}
FREE_END = {"burrow_dive", "defeat"}

PACKS = [
    dict(path=os.path.join(ASSETS, "plata_moves", "plata.json"), module="plata_moves", required=MOVES,
         name="Plata moves", trigger="idle", require=None, free_start=FREE_START, free_end=FREE_END,
         description="The Plata Clan fighter's moves, played by id by PlataPoser on the Plata model."),
    dict(path=os.path.join(ASSETS, "resident_animations", "plata.json"), module="molt", required=MOLT,
         name="The Molt", trigger="pet", require=["play:plata_never"],
         description="A disguised Plata drops the act. Played only by the Plata Clan reveal, never by the director."),
    dict(path=os.path.join(ASSETS, "plata_moves", "mother.json"), module="mother_moves", required=MOTHER,
         name="The Silver Mother", trigger="idle", require=None,
         free_start={"curl", "rise", "erupt"}, free_end={"curl", "burrow", "defeat"},
         description="The Silver Mother's moves, played by id by MotherModel on her model."),
]


def load(module):
    """Run one clip module from scratch and return (its kit clips, the module)."""
    kit.CLIPS.clear()
    spec = importlib.util.spec_from_file_location(f"plata_clips_{module}", os.path.join(HERE, module + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    clips = list(kit.CLIPS)
    kit.CLIPS.clear()
    return clips, mod


def collect():
    """{output path: pack dict} built from the clip modules (nothing written)."""
    out = {}
    for pack in PACKS:
        clips, _ = load(pack["module"])
        out[pack["path"]] = {"format": 1, "name": pack["name"], "description": pack["description"],
                             "clips": [c.compile() for c in clips]}
    return out


def beats():
    """Preview frame times and the key beat ('HIT') per clip, from each module's BEATS/HITS."""
    times, hits = {}, {}
    for pack in PACKS:
        _, mod = load(pack["module"])
        times.update(getattr(mod, "BEATS", {}))
        hits.update(getattr(mod, "HITS", {}))
    return times, hits


BONES = {"root", "waist", "body", "head", "right_arm", "left_arm", "right_leg", "left_leg"}


def sample(clip, t):
    """{track: values} of a compiled clip at time t (same curves as the game)."""
    from clip_preview import Track
    return {k: Track(v)(t) for k, v in clip["tracks"].items()}


def differ(a, b, tol, skip=("eyes.lid", "eyes.look")):
    """Largest difference between two sampled poses (missing channels count as rest)."""
    worst, where = 0.0, ""
    for k in set(a) | set(b):
        if k in skip:
            continue
        va, vb = a.get(k, [0, 0, 0]), b.get(k, [0, 0, 0])
        d = max(abs(x - y) for x, y in zip(va, vb))
        if d > worst:
            worst, where = d, k
    return (worst, where) if worst > tol else None


def validate(pack, data):
    errors, seen = [], set()
    if data.get("format") != 1:
        errors.append("format must be 1")
    for c in data["clips"]:
        cid, where = c["id"], f"{os.path.basename(os.path.dirname(pack['path']))}:{c['id']}"
        if not re.fullmatch(r"[a-z0-9_]+", cid):
            errors.append(f"{where}: id must be [a-z0-9_]")
        if cid in seen:
            errors.append(f"{where}: duplicate id")
        seen.add(cid)
        length = c["length"]
        if not 0 < length <= MAX_LEN:
            errors.append(f"{where}: length {length} outside (0, {MAX_LEN}]")
        blend = c.get("blend", [.25, .35])
        if min(blend) < 0 or sum(blend) > length + 1e-4:
            errors.append(f"{where}: blend {blend} longer than the clip")
        if c["trigger"] != pack["trigger"] or (pack["require"] and c.get("require") != pack["require"]):
            errors.append(f"{where}: trigger/require must be {pack['trigger']} {pack['require'] or ''}")
        if c.get("mirror") != "never" or c.get("items") != "override":
            errors.append(f"{where}: needs mirror never and items override")
        for key, keys in c["tracks"].items():
            bone, _, channel = key.partition(".")
            if bone == "eyes":
                ok, limit = channel in ("lid", "look"), (1 if channel == "look" else None)
            else:
                ok = bone in BONES and channel in ("rot", "pos") and not (bone == "waist" and channel == "pos")
                limit = MAX_ROT if channel == "rot" else MAX_POS
            if not ok:
                errors.append(f"{where}: bad track {key}")
                continue
            times = [k[0] for k in keys]
            if any(b <= a for a, b in zip(times, times[1:])) or times[0] < 0 or times[-1] > length + 1e-4:
                errors.append(f"{where}: {key} key times must increase within 0..{length}")
            for k in keys:
                if key == "eyes.lid" and not 0 <= k[1] <= 1:
                    errors.append(f"{where}: lid {k[1]} outside 0..1 at {k[0]}")
                elif limit and any(abs(v) > limit + 1e-6 for v in k[1:]):
                    errors.append(f"{where}: {key} beyond +-{limit} at {k[0]}s")
    by_id = {c["id"]: c for c in data["clips"]}
    for cid, length in pack["required"].items():
        if cid not in by_id:
            errors.append(f"missing clip {cid}")
        elif abs(by_id[cid]["length"] - length) > 1e-6:
            errors.append(f"{cid}: length {by_id[cid]['length']} but the game times it as {length}")
    return errors, by_id


def stance_contract(moves, free_start, free_end, who=""):
    """The stance loops; every other clip starts and ends in it unless the contract puts it somewhere else."""
    errors = []
    stance = moves.get("stance")
    if stance:
        s0 = sample(stance, 0)
        if d := differ(s0, sample(stance, stance["length"]), 1e-3):
            errors.append(f"{who}stance does not loop: {d[1]} off by {d[0]:.3f}")
        for cid, c in moves.items():
            if cid not in free_start and (d := differ(s0, sample(c, 0), 0.5)):
                errors.append(f"{who}{cid}: starts {d[0]:.2f} away from the stance ({d[1]})")
            if cid not in free_end and (d := differ(s0, sample(c, c["length"]), 0.5)):
                errors.append(f"{who}{cid}: ends {d[0]:.2f} away from the stance ({d[1]})")
    return errors


def contract(moves, molt):
    """Blend rules between clips: stance loops, clips start/end in the stance, unfurl starts where molt ends."""
    errors = stance_contract(moves, FREE_START, FREE_END)
    if "unfurl" in moves and "molt" in molt:
        m = molt["molt"]
        if d := differ(sample(m, m["length"]), sample(moves["unfurl"], 0), 0.01):
            errors.append(f"unfurl must start in molt's final pose: {d[1]} off by {d[0]:.3f}")
    return errors


def text(data):
    """One line per track keeps the file compact and readable in diffs (VF's layout)."""
    lines = ["{", '"format": 1,', f'"name": {json.dumps(data["name"])},',
             f'"description": {json.dumps(data["description"])},', '"clips": [']
    for i, clip in enumerate(data["clips"]):
        clip = dict(clip)
        tracks = clip.pop("tracks")
        head = json.dumps(clip, separators=(", ", ": "))[:-1]
        body = ",\n".join(f"  {json.dumps(k)}: {json.dumps(v, separators=(',', ':'))}" for k, v in tracks.items())
        lines.append(f'{head}, "tracks": {{\n{body}\n}}}}' + ("," if i < len(data["clips"]) - 1 else ""))
    lines += ["]", "}"]
    out = "\n".join(lines) + "\n"
    json.loads(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="validate and confirm the packs on disk are current")
    ap.add_argument("--list", action="store_true")
    a = ap.parse_args()
    built = collect()
    errors, ids = [], {}
    for pack in PACKS:
        errs, by_id = validate(pack, built[pack["path"]])
        errors += errs
        ids[pack["module"]] = by_id
    errors += contract(ids["plata_moves"], ids["molt"])
    mother = PACKS[2]
    errors += stance_contract(ids["mother_moves"], mother["free_start"], mother["free_end"], "mother:")
    curl = ids["mother_moves"]["curl"]
    for cid, at in (("rise", 0), ("defeat", None)):
        c = ids["mother_moves"][cid]
        if d := differ(sample(curl, 0), sample(c, c["length"] if at is None else at), 0.5):
            errors.append(f"mother:{cid} must {'start' if at == 0 else 'end'} curled: {d[1]} off by {d[0]:.2f}")
    if errors:
        raise SystemExit("Clip errors:\n  " + "\n  ".join(errors))
    stale = []
    for pack in PACKS:
        path, data = pack["path"], built[pack["path"]]
        rel = os.path.relpath(path, ROOT)
        if a.list:
            for c in data["clips"]:
                print(f"{rel:58} {c['id']:12} {c['length']:4.2f}s  {c['name']}")
        out = text(data)
        if a.check:
            current = open(path, encoding="utf-8").read() if os.path.exists(path) else None
            if current != out:
                stale.append(rel)
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(out)
        print(f"{rel}: {len(data['clips'])} clips")
    if stale:
        raise SystemExit("Stale clip packs (run tools/animations/build_clips.py): " + ", ".join(stale))
    if a.check:
        print("Plata clip packs are valid and current.")


if __name__ == "__main__":
    main()
