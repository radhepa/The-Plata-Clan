"""Dollhouse cutaway of a whole generated hideout, from the block dump the game test writes
(build/run/clientGameTest/hideout_dump/<name>.json): every piece cut a few blocks above its floor so
the burrow's plan shows, with the surface disguise drawn whole above it.

  python tools/hideouts/cutaway.py build/run/clientGameTest/hideout_dump/warren.json out.png [--scale 6]
"""
import argparse
import json

from render import render


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('dump')
    ap.add_argument('out')
    ap.add_argument('--scale', type=int, default=6)
    ap.add_argument('--cut', type=int, default=4, help='blocks of wall to keep above each floor')
    args = ap.parse_args()
    with open(args.dump, encoding='utf-8') as f:
        data = json.load(f)
    pieces = data['pieces']

    def owner(x, y, z):
        for i, p in enumerate(pieces):
            (x0, y0, z0), (x1, y1, z1) = p['min'], p['max']
            if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1: return i, p
        return None, None

    blocks = {}
    for x, y, z, state in data['blocks']:
        i, p = owner(x, y, z)
        if p is None: continue
        whole = i == 0 or 'stair' in p['template'] and 'tunnel' not in p['template'] or 'shaft' in p['template']
        if not whole and y > p['min'][1] + args.cut: continue
        blocks[(x, y, z)] = state
    render(blocks, args.out, s=args.scale, hide=('cave_air', 'water', 'lava'), title=data.get('structure'))
    print('wrote', args.out, len(blocks), 'blocks from', len(pieces), 'pieces')


if __name__ == '__main__':
    main()
