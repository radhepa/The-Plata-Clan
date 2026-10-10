"""Clip previews and pose checks for the Plata clips, posed exactly the way ResidentPoser does it.

    python tools/animations/clip_preview.py                    contact sheets of every clip -> tools/preview/out/clips_*.png
    python tools/animations/clip_preview.py --clips molt,unfurl --frames 8
    python tools/animations/clip_preview.py --report           numbers only: ground clearance, feet, limb overlaps

Posing (port of VF's ResidentPoser + AnimationTrack): every bone's keys are sampled with monotone cubic
curves; arm/leg/head/body offsets are added to the parts; the waist turns body, head and arms about the hips
(y 12); the root turns the whole model about the feet (0, 24, 0) and then moves by root_pos. Model space has
y pointing down, the model faces -z, the ground is y = 24.

Uses src/client/resources/assets/plataclan/geometry/plata_scuttler.json + its texture when they exist,
otherwise a plain placeholder humanoid built into build/anim-preview/.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
ROOT = os.path.dirname(TOOLS)
sys.path.insert(0, HERE)

BASE = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
        "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}
UPPER = ("body", "head", "right_arm", "left_arm")          # what the waist carries
# The vanilla player boxes (origin, size) used for the numeric checks, whatever the costume adds.
BOXES = {"head": ((-4, -8, -4), (8, 8, 8)), "body": ((-4, 0, -2), (8, 12, 4)),
         "right_arm": ((-3, -2, -2), (4, 12, 4)), "left_arm": ((-1, -2, -2), (4, 12, 4)),
         "right_leg": ((-2, 0, -2), (4, 12, 4)), "left_leg": ((-2, 0, -2), (4, 12, 4))}
HANDS = {"right_arm": (-1, 10, 0), "left_arm": (1, 10, 0)}


class Track:
    """Monotone cubic sampler, a line-by-line port of AnimationTrack.java."""

    def __init__(self, keys):
        self.t = [float(k[0]) for k in keys]
        self.v = [[float(x) for x in k[1:]] + [0.0] * (4 - len(k)) for k in keys]
        n = len(keys)
        self.m = [[0.0, 0.0, 0.0] for _ in range(n)]
        for c in range(3):
            for k in range(1, n - 1):
                before, after = self._sec(k - 1, c), self._sec(k, c)
                self.m[k][c] = 0.0 if before * after <= 0 else (before + after) / 2
            for k in range(n - 1):
                d = self._sec(k, c)
                if d == 0:
                    self.m[k][c] = self.m[k + 1][c] = 0.0
                    continue
                a, b = self.m[k][c] / d, self.m[k + 1][c] / d
                s = a * a + b * b
                if s > 9:
                    sc = 3 / math.sqrt(s)
                    self.m[k][c], self.m[k + 1][c] = sc * a * d, sc * b * d

    def _sec(self, k, c):
        return (self.v[k + 1][c] - self.v[k][c]) / (self.t[k + 1] - self.t[k])

    def __call__(self, t):
        n = len(self.t)
        if n == 1 or t <= self.t[0]:
            return list(self.v[0])
        if t >= self.t[-1]:
            return list(self.v[-1])
        k = 0
        while k < n - 2 and t >= self.t[k + 1]:
            k += 1
        h = self.t[k + 1] - self.t[k]
        s = (t - self.t[k]) / h
        s2, s3 = s * s, s * s * s
        h00, h10, h01, h11 = 2 * s3 - 3 * s2 + 1, s3 - 2 * s2 + s, -2 * s3 + 3 * s2, s3 - s2
        return [h00 * self.v[k][c] + h10 * h * self.m[k][c] + h01 * self.v[k + 1][c] + h11 * h * self.m[k + 1][c]
                for c in range(3)]


def rzyx(x, y, z):
    """Degrees -> Rz @ Ry @ Rx, like JOML rotationZYX / ModelPart."""
    x, y, z = (math.radians(a) for a in (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


class Pose:
    """Samples one compiled clip (the JSON dict) and poses the model the way ResidentPoser does."""

    def __init__(self, clip):
        self.clip = clip
        self.tracks = {k: Track(v) for k, v in clip["tracks"].items()}

    def channel(self, key, t):
        tr = self.tracks.get(key)
        return tr(t) if tr else [0.0, 0.0, 0.0]

    def at(self, t):
        """{'root': 4x4, part: 4x4 (in root space)}, plus 'lid'."""
        out = {}
        for part, base in BASE.items():
            rot, pos = self.channel(part + ".rot", t), self.channel(part + ".pos", t)
            out[part] = [np.array(base, float) + pos, rzyx(*rot)]
        waist = self.channel("waist.rot", t)
        if any(waist):
            turn, hip = rzyx(*waist), np.array([0, 12.0, 0])
            for part in UPPER:
                p, r = out[part]
                out[part] = [turn @ (p - hip) + hip, turn @ r]
        root = self.channel("root.rot", t)
        feet = np.array([0, 24.0, 0])
        turn = rzyx(*root)
        rp = turn @ (np.zeros(3) - feet) + feet + np.array(self.channel("root.pos", t))
        mats = {"root": _mat(rp, turn)}
        for part, (p, r) in out.items():
            mats[part] = _mat(p, r)
        mats["lid"] = self.channel("eyes.lid", t)[0]
        return mats


def _mat(p, r):
    m = np.eye(4)
    m[:3, :3], m[:3, 3] = r, p
    return m


# -- checks ----------------------------------------------------------------------------------------
def corners(name):
    (x, y, z), (w, h, d) = BOXES[name]
    return np.array([[x + i * w, y + j * h, z + k * d, 1] for i in (0, 1) for j in (0, 1) for k in (0, 1)], float)


def world_box(mats, name):
    """(centre, axes 3x3 columns, half sizes) of a part's vanilla box in model space."""
    m = mats["root"] @ mats[name]
    (x, y, z), (w, h, d) = BOXES[name]
    centre = (m @ np.array([x + w / 2, y + h / 2, z + d / 2, 1]))[:3]
    return centre, m[:3, :3], np.array([w / 2, h / 2, d / 2])


def overlap(a, b):
    """Penetration depth of two oriented boxes (separating-axis test); <= 0 when apart."""
    (ca, ra, ha), (cb, rb, hb) = a, b
    axes = [ra[:, i] for i in range(3)] + [rb[:, i] for i in range(3)]
    axes += [np.cross(ra[:, i], rb[:, j]) for i in range(3) for j in range(3)]
    depth = 1e9
    for ax in axes:
        n = np.linalg.norm(ax)
        if n < 1e-6:
            continue
        ax = ax / n
        pa = sum(abs(ax @ ra[:, i]) * ha[i] for i in range(3))
        pb = sum(abs(ax @ rb[:, i]) * hb[i] for i in range(3))
        depth = min(depth, pa + pb - abs(ax @ (cb - ca)))
        if depth <= 0:
            return depth
    return depth


# Pairs that must not pass through each other, with the overlap a joint or a brush allows (px). Crossed arms
# pass through each other a little, as vanilla's own folded arms do; a raised arm brushes the jaw; a deep fold
# at the hip sinks the thigh into the belly (vanilla sitting: 2.1, a 120 degree fold: 3.0).
PAIRS = [("right_arm", "body", 2.0), ("left_arm", "body", 2.0), ("right_arm", "head", 1.5), ("left_arm", "head", 1.5),
         ("right_arm", "left_arm", 3.6), ("right_arm", "right_leg", 1.2), ("left_arm", "left_leg", 1.2),
         ("right_arm", "left_leg", 1.2), ("left_arm", "right_leg", 1.2), ("right_leg", "left_leg", 1.0),
         ("head", "right_leg", 1.0), ("head", "left_leg", 1.0), ("body", "right_leg", 3.4), ("body", "left_leg", 3.4)]
SHORT = {"head": "head", "body": "body", "right_arm": "ra", "left_arm": "la", "right_leg": "rl", "left_leg": "ll"}
SOLE = np.array([0, 12, 0, 1.0])


def check(mats):
    """Height above the ground of the lowest head/body/arm corner, of each sole's centre and of the lowest leg
    corner (a tilted box always dips a corner), plus the overlaps beyond their allowance."""
    low = {}
    for name in BOXES:
        pts = (mats["root"] @ mats[name] @ corners(name).T).T
        low[name] = 24 - pts[:, 1].max()
    soles = [24 - (mats["root"] @ mats[leg] @ SOLE)[1] for leg in ("right_leg", "left_leg")]
    boxes = {n: world_box(mats, n) for n in BOXES}
    hits = []
    for a, b, allow in PAIRS:
        d = overlap(boxes[a], boxes[b])
        if d > allow:
            hits.append(f"{SHORT[a]}x{SHORT[b]} {d:.1f}")
    upper = min(low[n] for n in ("head", "body", "right_arm", "left_arm"))
    if upper < -1.0 or min(soles) < -1.0 or min(low["right_leg"], low["left_leg"]) < -2.4:
        hits.insert(0, "GROUND")
    return upper, soles, min(low["right_leg"], low["left_leg"]), hits


def hand(mats, arm):
    return (mats["root"] @ mats[arm] @ np.array([*HANDS[arm], 1]))[:3]


def report(clips, step=0.05, flags_only=False, underground=None):
    """Pose checks every step seconds. underground: {clip id: (from, to)} where being in the ground is the point."""
    bad = 0
    for c in clips:
        pose = Pose(c)
        n = int(round(c["length"] / step))
        if not flags_only:
            print(f"{c['id']} ({c['length']}s)   upper = lowest head/body/arm point, soles R/L, leg = lowest leg corner")
        for i in range(n + 1):
            t = min(c["length"], i * step)
            mats = pose.at(t)
            upper, soles, leg, hits = check(mats)
            window = (underground or {}).get(c["id"])
            if window and window[0] <= t <= window[1] and hits and hits[0] == "GROUND":
                hits = hits[1:]
            body_inv = np.linalg.inv(mats["root"] @ mats["body"])
            rh, lh = (body_inv @ np.array([*hand(mats, a), 1]) for a in ("right_arm", "left_arm"))
            bad += bool(hits)
            if flags_only:
                if hits:
                    print(f"  {c['id']:11} {t:5.3f} upper {upper:5.1f} soles {soles[0]:5.1f}/{soles[1]:5.1f}  "
                          + ", ".join(hits))
                continue
            print(f"  {t:5.2f} upper {upper:5.1f} soles {soles[0]:5.1f}/{soles[1]:5.1f} leg {leg:5.1f}"
                  f"  hands@torso R({rh[0]:5.1f},{rh[1]:5.1f},{rh[2]:5.1f}) L({lh[0]:5.1f},{lh[1]:5.1f},{lh[2]:5.1f})"
                  + ("  " + ", ".join(hits) if hits else ""))
    return bad


# -- model -----------------------------------------------------------------------------------------
GEO = os.path.join(ROOT, "src", "client", "resources", "assets", "plataclan", "geometry", "plata_scuttler.json")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "plataclan", "textures", "entity", "plata_scuttler.png")
SCRATCH = os.path.join(ROOT, "build", "anim-preview")
OUT = os.path.join(TOOLS, "preview", "out")


def placeholder():
    """A plain humanoid (vanilla player boxes) with a face, told-apart arms and reverse-grip knives."""
    sys.path.insert(0, TOOLS)
    from mobkit import Model, faces
    m = Model("plata_placeholder", 64, 64)
    fill = lambda c: (lambda cv: cv.fill(c))  # noqa: E731

    def mask(cv):
        cv.fill("#A9B0B8")
        cv.rect(1, 3, 2, 1, "#24282D")
        cv.rect(5, 3, 2, 1, "#24282D")
        cv.rect(0, 7, 8, 1, "#5E646C")

    def vest(cv):
        cv.fill("#464C54")
        for y in (1, 4, 7):
            cv.rect(1, y, 6, 2, "#C9CFD5")

    head = m.part("head")
    head.cube((0, 0), (-4, -8, -4), (8, 8, 8), faces(fill("#34393F"), front=mask))
    head.part("hat")
    m.part("body").cube((16, 16), (-4, 0, -2), (8, 12, 4), faces(fill("#34393F"), front=vest, back=fill("#24282D")))
    ra = m.part("right_arm", (-5, 2, 0))
    ra.cube((40, 16), (-3, -2, -2), (4, 12, 4), faces(fill("#4F6A8A"), front=fill("#6283AA"), bottom=fill("#2A2622")))
    la = m.part("left_arm", (5, 2, 0))
    la.cube((32, 48), (-1, -2, -2), (4, 12, 4), faces(fill("#4B7A6E"), front=fill("#5E9688"), bottom=fill("#2A2622")))
    m.part("right_leg", (-1.9, 12, 0)).cube((0, 16), (-2, 0, -2), (4, 12, 4), faces(fill("#2E3238"), front=fill("#3E444C")))
    m.part("left_leg", (1.9, 12, 0)).cube((16, 48), (-2, 0, -2), (4, 12, 4), faces(fill("#2A2E33"), front=fill("#383E45")))
    blade = faces(fill("#E6EAEE"), top=fill("#C9CFD5"), bottom=fill("#858C95"))
    ra.part("fang_r", (-1, 9.5, 0)).cube((0, 32), (-0.5, -0.5, 0), (1, 1, 7), blade)
    la.part("fang_l", (1, 9.5, 0)).cube((16, 32), (-0.5, -0.5, 0), (1, 1, 7), blade)
    m.paint()
    os.makedirs(SCRATCH, exist_ok=True)
    geo, tex = os.path.join(SCRATCH, "plata_placeholder.json"), os.path.join(SCRATCH, "plata_placeholder.png")
    with open(geo, "w", encoding="utf-8") as f:
        json.dump(m.geometry(), f, indent=1)
    m.image.save(tex)
    return geo, tex


def model_files(force_placeholder=False):
    if not force_placeholder and os.path.exists(GEO) and os.path.exists(TEX):
        return GEO, TEX
    return placeholder()


# -- rendering -------------------------------------------------------------------------------------
PAGE = r"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;height:100%;overflow:hidden;background:#262a33}
.lab{position:absolute;color:#e8e8e8;font:13px monospace;text-shadow:1px 1px 0 #000;padding:3px 5px}
.lab b{color:#ffd27a}
</style></head><body>
<script src="__THREE__"></script>
<script>
const GEO = __GEO__, TEXURI = "__TEX__", CELLS = __CELLS__, COLS = __COLS__, SPAN = __SPAN__;
const W = innerWidth, H = innerHeight, rows = Math.ceil(CELLS.length / COLS), cw = W / COLS, ch = H / rows;
const renderer = new THREE.WebGLRenderer({antialias:true});
renderer.setPixelRatio(1); renderer.setSize(W, H); renderer.setScissorTest(true);
document.body.appendChild(renderer.domElement);
const tex = new THREE.TextureLoader().load(TEXURI, () => draw());
tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter;
const mat = new THREE.MeshBasicMaterial({map:tex, transparent:true, alphaTest:0.1, side:THREE.DoubleSide});
function cubeGeometry(c, tw, th){
  let [minX,minY,minZ] = c.from; const [w,h,d] = c.size; const g = c.inflate||0;
  let maxX=minX+w, maxY=minY+h, maxZ=minZ+d; const [xo,yo] = c.uv;
  minX-=g; minY-=g; minZ-=g; maxX+=g; maxY+=g; maxZ+=g;
  if (c.mirror){ const t=maxX; maxX=minX; minX=t; }
  const t0=[minX,minY,minZ], t1=[maxX,minY,minZ], t2=[maxX,maxY,minZ], t3=[minX,maxY,minZ];
  const l0=[minX,minY,maxZ], l1=[maxX,minY,maxZ], l2=[maxX,maxY,maxZ], l3=[minX,maxY,maxZ];
  const u0=xo, u1=xo+d, u2=xo+d+w, u22=xo+d+w+w, u3=xo+d+w+d, u4=xo+d+w+d+w, v0=yo, v1=yo+d, v2=yo+d+h;
  const polys = [[[l1,l0,t0,t1],u1,v0,u2,v1],[[t2,t3,l3,l2],u2,v1,u22,v0],[[t0,l0,l3,t3],u0,v1,u1,v2],
    [[t1,t0,t3,t2],u1,v1,u2,v2],[[l1,t1,t2,l2],u2,v1,u3,v2],[[l0,l1,l2,l3],u3,v1,u4,v2]];
  const pos=[], uv=[];
  for (const [vs,a,b,cc,dd] of polys){ const uvs=[[cc,b],[a,b],[a,dd],[cc,dd]];
    for (const i of [0,1,2,0,2,3]){ pos.push(...vs[i]); uv.push(uvs[i][0]/tw, 1-uvs[i][1]/th); } }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos,3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv,2));
  return geo;
}
function setMat(g, m){ g.matrixAutoUpdate = false; g.matrix.set(...m); }
function build(cell){
  const [tw,th] = GEO.texture, root = new THREE.Group(), groups = {"": root};
  setMat(root, cell.m.root);
  for (const p of GEO.parts){
    const g = new THREE.Group();
    if (p.parent === "" && cell.m[p.name]) setMat(g, cell.m[p.name]);
    else { g.position.set(...p.pivot); g.rotation.set(p.rotation[0], p.rotation[1], p.rotation[2], 'ZYX'); }
    for (const c of p.cubes) g.add(new THREE.Mesh(cubeGeometry(c, tw, th), mat));
    groups[p.parent].add(g); groups[p.name] = g;
  }
  const flip = new THREE.Group(); flip.scale.set(-1,-1,1);
  const lift = new THREE.Group(); lift.position.y = -24.016; lift.add(root); flip.add(lift);
  return flip;
}
const ground = new THREE.GridHelper(64, 16, 0x7a8294, 0x454b58);
const scenes = CELLS.map((cell, i) => {
  const scene = new THREE.Scene(); scene.background = new THREE.Color(cell.bg || 0x2f343f);
  scene.add(build(cell)); scene.add(ground.clone());
  const cam = new THREE.OrthographicCamera(-SPAN*cw/ch, SPAN*cw/ch, SPAN, -SPAN, 0.1, 500);
  const a = cell.view * Math.PI / 180, mid = new THREE.Vector3(0, 17, -2);
  cam.position.set(Math.sin(a)*90, mid.y + 14, -Math.cos(a)*90); cam.lookAt(mid);
  const col = i % COLS, row = Math.floor(i / COLS);
  const lab = document.createElement('div'); lab.className = 'lab';
  lab.style.left = (col*cw)+'px'; lab.style.top = (row*ch)+'px'; lab.innerHTML = cell.label;
  document.body.appendChild(lab);
  return {scene, cam, col, row};
});
function draw(){ for (const s of scenes){ const x = s.col*cw, y = H - (s.row+1)*ch;
  renderer.setViewport(x, y, cw, ch); renderer.setScissor(x, y, cw, ch); renderer.render(s.scene, s.cam); } }
draw();
</script></body></html>"""


SHEETS = [("molt", ["molt", "unfurl", "defeat"]),
          ("stance", ["stance", "ready", "taunt", "stagger", "call"]),
          ("flurry", ["flurry_1", "flurry_2", "flurry_3", "flurry_4", "lunge"]),
          ("dodge", ["backflip", "sideroll", "burrow_dive", "emerge"]),
          ("heavy", ["throw_fan", "kick", "lash", "slam"])]


def frame_times(c, beats, frames):
    if c["id"] in beats and frames == 6:
        return list(beats[c["id"]])
    return [round(c["length"] * i / (frames - 1), 3) for i in range(frames)]


def render(clips, out_png, beats, hits, frames=6, view=35, size=None):
    sys.path.insert(0, os.path.join(TOOLS, "preview"))
    import preview  # the mob previewer: Edge + three.js locations and the PNG data URI helper
    import subprocess
    import time
    import shutil
    geo_path, tex_path = model_files()
    with open(geo_path, encoding="utf-8") as f:
        geo = json.load(f)
    cells = []
    for row, c in enumerate(clips):
        pose = Pose(c)
        for i, t in enumerate(frame_times(c, beats, frames)):
            mats = pose.at(t)
            m = {k: [round(float(x), 5) for x in v.flatten()] for k, v in mats.items() if k != "lid"}
            hit = abs(t - hits.get(c["id"], -1)) < 1e-6
            label = (f"<b>{c['id']}</b> " if i == 0 else "") + f"{t:.2f}s" + (" HIT" if hit else "")
            cells.append({"m": m, "label": label, "view": view,
                          "bg": 0x4a3436 if hit else (0x2f343f if row % 2 == 0 else 0x343a46)})
    w, h = size or (frames * 300, len(clips) * 300)
    page = (PAGE.replace("__THREE__", preview.THREE).replace("__GEO__", json.dumps(geo))
            .replace("__TEX__", preview.data_uri(tex_path)).replace("__CELLS__", json.dumps(cells))
            .replace("__COLS__", str(frames)).replace("__SPAN__", "25"))
    os.makedirs(SCRATCH, exist_ok=True)
    tag = str(os.getpid())
    html, profile = os.path.join(SCRATCH, f"_page_{tag}.html"), os.path.join(SCRATCH, f"_edge_{tag}")
    with open(html, "w", encoding="utf-8") as f:
        f.write(page)
    if os.path.exists(out_png):
        os.remove(out_png)
    subprocess.run([preview.EDGE, "--headless=new", "--enable-unsafe-swiftshader", "--use-angle=swiftshader",
                    "--hide-scrollbars", f"--window-size={w},{h}", "--virtual-time-budget=6000",
                    f"--user-data-dir={profile}", f"--screenshot={out_png}", "file:///" + html.replace("\\", "/")],
                   check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
    for _ in range(240):
        if os.path.exists(out_png) and os.path.getsize(out_png) > 0:
            time.sleep(0.5)
            break
        time.sleep(0.5)
    else:
        sys.exit("preview failed: no screenshot written")
    os.remove(html)
    shutil.rmtree(profile, ignore_errors=True)
    return out_png


def main():
    import build_clips
    ap = argparse.ArgumentParser()
    ap.add_argument("--clips", help="comma-separated clip ids (default: every sheet)")
    ap.add_argument("--frames", type=int, default=6)
    ap.add_argument("--view", type=int, default=35, help="camera angle: 0 front, 35 three-quarter, 90 right side")
    ap.add_argument("--report", action="store_true", help="print pose checks instead of rendering")
    ap.add_argument("--flags", action="store_true", help="with --report: only the flagged frames")
    ap.add_argument("--step", type=float, default=0.05)
    ap.add_argument("--out", help="output PNG (with --clips)")
    a = ap.parse_args()
    packs = build_clips.collect()
    by_id = {c["id"]: c for pack in packs.values() for c in pack["clips"]}
    beats, hits = build_clips.beats()
    under = getattr(build_clips.load("plata_moves")[1], "UNDERGROUND", {})
    if a.clips:
        groups = [("custom", a.clips.split(","))]
    else:
        groups = SHEETS
    if a.report:
        bad = report([by_id[i] for _, ids in groups for i in ids], a.step, a.flags, under)
        print(f"{bad} flagged frames")
        return
    suffix = "" if a.view == 35 else f"_{a.view}"
    for name, ids in groups:
        out = os.path.abspath(a.out) if a.out and a.clips else os.path.join(OUT, f"clips_{name}{suffix}.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        print(render([by_id[i] for i in ids], out, beats, hits, a.frames, a.view))


if __name__ == "__main__":
    main()
