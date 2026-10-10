"""Offline previewer: renders mobs from their generated geometry + textures with headless Edge.

  python tools/preview/preview.py sporeling                 one mob, 3/4 view -> tools/preview/out/sporeling.png
  python tools/preview/preview.py sporeling --views 4       front, 3/4, side, back in one image
  python tools/preview/preview.py --all                     contact sheet of every mob

The cube/UV code is a straight port of net.minecraft.client.model.geom.ModelPart.Cube, and parts are
transformed like ModelPart.translateAndRotate (translate, then rotationZYX), then flipped like
LivingEntityRenderer (scale -1,-1,1, up 1.501 blocks). What you see here is what the game draws,
minus lighting.
"""
import argparse
import base64
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from mobkit import MOD_ID  # noqa: E402
ROOT = os.path.dirname(os.path.dirname(HERE))
GEO = os.path.join(ROOT, "src", "client", "resources", "assets", MOD_ID, "geometry")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", MOD_ID, "textures", "entity")
OUT = os.path.join(HERE, "out")
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
THREE = "https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"


def data_uri(path):
    with open(path, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def mob_payload(mob_id, texture=None):
    with open(os.path.join(GEO, mob_id + ".json"), encoding="utf-8") as f:
        geo = json.load(f)
    tex = texture or mob_id
    glow = os.path.join(TEX, tex + "_glow.png")
    return {
        "id": mob_id,
        "geo": geo,
        "tex": data_uri(os.path.join(TEX, tex + ".png")),
        "glow": data_uri(glow) if os.path.exists(glow) else None,
    }


PAGE = r"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;height:100%;overflow:hidden;background:#2b2f3a}
canvas{display:block}
.lab{position:absolute;color:#e8e8e8;font:15px monospace;text-align:center;text-shadow:1px 1px 0 #000}
</style></head><body>
<script src="__THREE__"></script>
<script>
const MOBS = __MOBS__;
const VIEWS = __VIEWS__;
const POSES = __POSES__;
const W = innerWidth, H = innerHeight;
const renderer = new THREE.WebGLRenderer({antialias:true});
renderer.setPixelRatio(1); renderer.setSize(W, H); renderer.setScissorTest(true);
renderer.setClearColor(0x2b2f3a);
document.body.appendChild(renderer.domElement);

function loadTex(uri){ const t = new THREE.TextureLoader().load(uri, () => draw());
  t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; return t; }

// Port of ModelPart.Cube: returns [positions, uvs] for the six faces.
function cubeGeometry(c, tw, th){
  let [minX,minY,minZ] = c.from; const [w,h,d] = c.size; const g = c.inflate||0;
  let maxX=minX+w, maxY=minY+h, maxZ=minZ+d;
  const [xo,yo] = c.uv;
  minX-=g; minY-=g; minZ-=g; maxX+=g; maxY+=g; maxZ+=g;
  if (c.mirror){ const t=maxX; maxX=minX; minX=t; }
  const t0=[minX,minY,minZ], t1=[maxX,minY,minZ], t2=[maxX,maxY,minZ], t3=[minX,maxY,minZ];
  const l0=[minX,minY,maxZ], l1=[maxX,minY,maxZ], l2=[maxX,maxY,maxZ], l3=[minX,maxY,maxZ];
  const u0=xo, u1=xo+d, u2=xo+d+w, u22=xo+d+w+w, u3=xo+d+w+d, u4=xo+d+w+d+w;
  const v0=yo, v1=yo+d, v2=yo+d+h;
  const polys = [
    [[l1,l0,t0,t1], u1,v0,u2,v1],
    [[t2,t3,l3,l2], u2,v1,u22,v0],
    [[t0,l0,l3,t3], u0,v1,u1,v2],
    [[t1,t0,t3,t2], u1,v1,u2,v2],
    [[l1,t1,t2,l2], u2,v1,u3,v2],
    [[l0,l1,l2,l3], u3,v1,u4,v2],
  ];
  const pos=[], uv=[];
  for (const [vs,a,b,cc,dd] of polys){
    const uvs = [[cc,b],[a,b],[a,dd],[cc,dd]];
    for (const i of [0,1,2,0,2,3]){ pos.push(...vs[i]); uv.push(uvs[i][0]/tw, 1-uvs[i][1]/th); }
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos,3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv,2));
  return geo;
}

function buildMob(m){
  const [tw,th] = m.geo.texture;
  const base = new THREE.MeshBasicMaterial({map:loadTex(m.tex), transparent:true, alphaTest:0.1, side:THREE.DoubleSide});
  const glow = m.glow ? new THREE.MeshBasicMaterial({map:loadTex(m.glow), transparent:true, blending:THREE.AdditiveBlending, depthWrite:false, side:THREE.DoubleSide}) : null;
  const root = new THREE.Group();
  const groups = {"": root};
  const pose = Object.assign({}, m.geo.preview || {}, POSES[m.id] || {});
  for (const p of m.geo.parts){
    const g = new THREE.Group();
    g.position.set(...p.pivot);
    const r = pose[p.name] || p.rotation;
    g.rotation.set(r[0], r[1], r[2], 'ZYX');
    for (const c of p.cubes){
      const geo = cubeGeometry(c, tw, th);
      g.add(new THREE.Mesh(geo, base));
      if (glow) g.add(new THREE.Mesh(geo, glow));
    }
    groups[p.parent].add(g); groups[p.name] = g;
  }
  // LivingEntityRenderer: scale(-1,-1,1), then translate(0,-1.501,0)  (in pixels: 24.016)
  const sc = m.geo.scale || 1;
  const flip = new THREE.Group(); flip.scale.set(-sc,-sc,sc);
  root.position.y = -24.016; flip.add(root);
  const box = new THREE.Box3().setFromObject(flip);
  return {obj:flip, box};
}

// layout: one cell per (mob, view)
const cells = [];
for (const m of MOBS) for (const v of VIEWS) cells.push({m, v});
const cols = Math.ceil(Math.sqrt(cells.length * W / H * 0.9)) || 1;
const rows = Math.ceil(cells.length / cols);
const cw = W / cols, ch = H / rows;
const scenes = cells.map((c, i) => {
  const scene = new THREE.Scene();
  const {obj, box} = buildMob(c.m);
  scene.add(obj);
  const size = box.getSize(new THREE.Vector3()), mid = box.getCenter(new THREE.Vector3());
  const span = Math.max(size.y, size.x * 0.9, size.z * 0.9) * 0.62 + 2;
  const cam = new THREE.OrthographicCamera(-span*cw/ch, span*cw/ch, span, -span, 0.1, 500);
  const a = c.v * Math.PI / 180;
  // mob faces -z: angle 0 looks at its face
  cam.position.set(mid.x + Math.sin(a)*90, mid.y + 22, mid.z - Math.cos(a)*90);
  cam.lookAt(mid);
  const col = i % cols, row = Math.floor(i / cols);
  const lab = document.createElement('div'); lab.className='lab';
  lab.style.left = (col*cw)+'px'; lab.style.width = cw+'px'; lab.style.top = (row*ch + 6)+'px';
  lab.textContent = c.m.id + (VIEWS.length > 1 ? ' ' + c.v + '\u00b0' : '');
  document.body.appendChild(lab);
  return {scene, cam, col, row};
});
function draw(){
  for (const s of scenes){
    const x = s.col*cw, y = H - (s.row+1)*ch;
    renderer.setViewport(x, y, cw, ch); renderer.setScissor(x, y, cw, ch);
    renderer.render(s.scene, s.cam);
  }
}
draw();
</script></body></html>"""


def render(mob_ids, views, out_png, size=(1200, 800), poses=None, texture=None):
    os.makedirs(OUT, exist_ok=True)
    mobs = [mob_payload(m, texture) for m in mob_ids]
    page = (PAGE.replace("__THREE__", THREE).replace("__MOBS__", json.dumps(mobs))
            .replace("__VIEWS__", json.dumps(views)).replace("__POSES__", json.dumps(poses or {})))
    tag = str(os.getpid())   # several previews may run at once (one page + profile each)
    html = os.path.join(OUT, f"_page_{tag}.html")
    with open(html, "w", encoding="utf-8") as f:
        f.write(page)
    profile = os.path.join(OUT, f"_edge_profile_{tag}")
    if os.path.exists(out_png):
        os.remove(out_png)
    subprocess.run([EDGE, "--headless=new", "--enable-unsafe-swiftshader", "--use-angle=swiftshader",
                    "--hide-scrollbars", f"--window-size={size[0]},{size[1]}", "--virtual-time-budget=6000",
                    f"--user-data-dir={profile}", f"--screenshot={out_png}", "file:///" + html.replace("\\", "/")],
                   check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
    # msedge.exe hands off to a running browser process and returns early: wait for the file
    import time
    for _ in range(240):
        if os.path.exists(out_png) and os.path.getsize(out_png) > 0:
            time.sleep(0.5)
            break
        time.sleep(0.5)
    else:
        sys.exit("preview failed: no screenshot written")
    import shutil
    try:
        os.remove(html)
    except OSError:
        pass
    shutil.rmtree(profile, ignore_errors=True)
    return out_png


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mobs", nargs="*")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--views", type=int, default=1, help="1 = 3/4 view, 4 = front/3-4/side/back")
    ap.add_argument("--texture", help="texture variant id to use instead of the mob's own")
    ap.add_argument("--out")
    ap.add_argument("--size", default="1200x800")
    a = ap.parse_args()
    mobs = a.mobs
    if a.all:
        mobs = sorted(f[:-5] for f in os.listdir(GEO) if f.endswith(".json"))
    views = [35] if a.views == 1 else [0, 35, 90, 180]
    w, h = (int(v) for v in a.size.split("x"))
    out = os.path.abspath(a.out) if a.out else os.path.join(OUT, ("contact" if a.all else "_".join(mobs)) + ".png")
    print(render(mobs, views, out, (w, h), texture=a.texture))


if __name__ == "__main__":
    main()
