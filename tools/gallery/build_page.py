"""Builds the Plata Clan showcase page (one self-contained HTML file) from the game test's
screenshots and the offline previews.

    python tools/gallery/build_page.py <out.html>

Screenshots come from build/run/clientGameTest/screenshots (gradlew runClientGameTest); missing
ones are skipped. Images are cropped and embedded as JPEG data URIs.
"""
import base64
import io
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
# Screenshots collected from game test runs (the run folder is wiped on every run, so copy them here).
SHOTS = os.path.join(ROOT, "build", "shots")
PREVIEW = os.path.join(ROOT, "tools", "preview", "out")
TEMPLATE = os.path.join(HERE, "page.html")
LINES = os.path.join(ROOT, "src", "main", "resources", "data", "plataclan", "plata_lines.json")


def find(name):
    """The image called name, as saved by the previewer or by a game test (which numbers its files 0000_name.png)."""
    for folder in (SHOTS, PREVIEW):
        if not os.path.isdir(folder):
            continue
        for f in sorted(os.listdir(folder)):
            stem = os.path.splitext(f)[0]
            if stem == name or stem.split("_", 1)[-1] == name and stem[:4].isdigit():
                return os.path.join(folder, f)
    return None


def embed(name, width=960, crop=None, quality=82):
    """A data URI for the named image, optionally cropped to (left, top, right, bottom) fractions."""
    path = find(name)
    if path is None:
        return None
    img = Image.open(path).convert("RGB")
    if crop:
        w, h = img.size
        img = img.crop((int(crop[0] * w), int(crop[1] * h), int(crop[2] * w), int(crop[3] * h)))
    if img.width > width:
        img = img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=quality, optimize=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def figure(name, caption, cls="", **kw):
    uri = embed(name, **kw)
    if uri is None:
        return ""
    return f'<figure class="{cls}"><img src="{uri}" alt="{caption}" loading="lazy"><figcaption>{caption}</figcaption></figure>'


def main(out):
    with open(TEMPLATE, encoding="utf-8") as f:
        page = f.read()
    center = (0.18, 0.05, 0.82, 0.95)
    molt = "".join([
        figure("plata-01-traveler", "1. A traveler, far from any village", crop=center, width=560),
        figure("plata-02-talk", "2. You talk. Something is off", width=560),
        figure("plata-03-molt-1", "3. They turn to you", crop=center, width=560),
        figure("plata-03-molt-2", "4. Something shivers under the skin", crop=center, width=560),
        figure("plata-03-molt-3", "5. They curl up tight", crop=center, width=560),
        figure("plata-04-unfurl", "6. Stone dust, and out they spring", crop=center, width=560),
        figure("plata-05-revealed", "7. The Plata Clan", width=560),
    ])
    ranks = [figure("plata-08-" + r, t, crop=(0.2, 0.0, 0.8, 1.0), width=440) for r, t in
             (("scuttler", "Scuttler in its stance"), ("shardcaster", "Shardcaster"), ("warden", "Molt Warden"))]
    moves = []
    for f in sorted(os.listdir(SHOTS)) if os.path.isdir(SHOTS) else []:
        f = f.split("_", 1)[-1] if f[:4].isdigit() else f
        if f.startswith("plata-move-") and f.endswith(".png"):
            name = f[:-4]
            rank, move, ticks = name[len("plata-move-"):].split("-")[0:3]
            moves.append(figure(name, f"{rank.title()} · {move.replace('_', ' ')} · {int(ticks) / 20:.2f} s", crop=(0.2, 0.0, 0.8, 1.0), width=420))
    with open(LINES, encoding="utf-8") as f:
        lines = json.load(f)["pools"]
    def quote(pool, i):
        items = lines.get(pool, [])
        return items[i % len(items)]["text"] if items else ""
    def shots(contains):
        """Every collected screenshot whose name contains the text, in order (names vary with what generated)."""
        names = []
        for f in sorted(os.listdir(SHOTS)) if os.path.isdir(SHOTS) else []:
            stem = os.path.splitext(f)[0]
            stem = stem.split("_", 1)[-1] if stem[:4].isdigit() else stem
            if contains in stem and stem not in names: names.append(stem)
        return names
    styles = {"green": "green country", "cold": "snow and taiga", "sand": "desert", "mesa": "badlands", "dry": "savanna", "stone": "windswept hills"}
    surfaces = [figure("hideout-01-warren-surface", "A Silver Warren: a ruined toll-house with an old cistern", width=620),
                figure("hideout-02-warren-lookout", "Its lookout, on the bench by the door", width=620),
                figure("hideout-20-den-surface", "A Scale Den: someone's camp, far from anywhere", width=620),
                figure("hideout-22-den-lookout", "Its lookout, by the fire", width=620),
                figure("hideout-03-warren-cistern", "The old cistern: its broken cover hides the top of a spiral stair", width=620)]
    for name in shots("-warren-") + shots("-den-"):
        parts = name.split("-")
        if len(parts) == 4 and parts[3] in styles and parts[1] >= "40":
            surfaces.append(figure(name, f"{'Warren' if parts[2] == 'warren' else 'Den'}, {styles[parts[3]]} style", width=620))
    rooms = {"hall": "The Molting Hall: the mark the length of the floor", "vault": "The vault: the elder's husk over the Clan Coffer",
             "gallery": "The Shell Gallery", "pit": "The Sparring Pit", "forge": "The Scale Forge", "nest": "The Brood",
             "shrine": "The Shrine of the First Molt", "store": "The Storeroom", "tunnel": "A ribbed tunnel"}
    room_figs = []
    for key, caption in rooms.items():
        for name in shots("-warren-" + key)[:1]:
            room_figs.append(figure(name, caption, width=560))
    for name in shots("-den-room")[:1]:
        room_figs.append(figure(name, "A Scale Den's one room", width=560))
    cutaway = embed("warren_cutaway", width=1100, quality=88)
    swaps = {
        "{{LINEUP}}": embed("plata-06-lineup", width=1280, crop=(0.2, 0.27, 0.9, 0.72)) or embed("plata_lineup", width=1400) or "",
        "{{MARK}}": embed_png(os.path.join(ROOT, "src", "main", "resources", "assets", "plataclan", "textures", "gui", "plata_mark.png")),
        "{{MOLT}}": molt,
        "{{RANKS_0}}": ranks[0], "{{RANKS_1}}": ranks[1], "{{RANKS_2}}": ranks[2],
        "{{MOVES}}": "".join(moves),
        "{{ITEMS}}": embed("items_sheet", width=1040, quality=88) or "",
        "{{LINEUP_BACK}}": figure("plata-07-lineup-back", "From behind: the three tail strands and the Warden's cape", width=900),
        "{{KIN}}": figure("plata-09-kin", "Wearing the Plata Mask: they take you for kin", width=640),
        "{{DEFEAT}}": figure("plata-10-defeat", "Beaten, they curl up and burst into dust", crop=center, width=640),
        "{{Q_GREET}}": quote("disguise.greet.any", 0),
        "{{Q_TAUNT}}": quote("reveal.taunt.suspicious", 2),
        "{{Q_DEFEAT}}": quote("defeat", 3),
        "{{Q_KIN}}": quote("kin.whisper", 4),
        "{{LINE_COUNT}}": str(sum(len(v) for v in lines.values())),
        "{{HIDEOUT_SURFACES}}": "".join(surfaces),
        "{{HIDEOUT_CUTAWAY}}": (f'<figure><img src="{cutaway}" alt="A whole generated Warren, cut away: the ruin above, the stair, the hall, tunnels, rooms and the vault">'
                               '<figcaption>A whole Warren from the game, every room cut away above its walls: the ruin and stair at the top, the burrow below</figcaption></figure>') if cutaway else "",
        "{{HIDEOUT_ROOMS}}": "".join(room_figs),
        "{{HIDEOUT_SLEEPERS}}": "".join([
            figure("hideout-30-sleeper-wakes", "Too close: the husk cracks open", width=560),
            figure("hideout-32-sleepers-up", "Its neighbours stir and follow", width=560),
            figure("hideout-33-sealed-again", "Walk away and they seal themselves back in", width=560)]),
        "{{Q_WAKE}}": quote("hideout.wake", 3),
        "{{Q_ELDER}}": quote("hideout.elder", 2),
    }
    for k, v in swaps.items():
        page = page.replace(k, v)
    with open(out, "w", encoding="utf-8") as f:
        f.write(page)
    print(f"wrote {out} ({os.path.getsize(out) // 1024} KB)")


def embed_png(path, scale=4):
    img = Image.open(path).convert("RGBA")
    img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "plata-clan.html"))
