"""Render every hideout piece to tools/hideouts/out/ (cutaways for the underground ones).
  python tools/hideouts/preview.py [name-filter]"""
import os
import sys

from pieces import all_pieces
from render import render_template

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out')

if __name__ == '__main__':
    want = sys.argv[1] if len(sys.argv) > 1 else ''
    for t in all_pieces():
        if want not in t.name: continue
        t.validate()
        flat = t.name.replace('/', '_')
        surface = 'surface' in t.name
        if surface:
            render_template(t, os.path.join(OUT, flat + '.png'), s=12, title=t.name)
        else:
            render_template(t, os.path.join(OUT, flat + '.png'), s=12, title=t.name, cut_y=t.size[1] - 2, open_near=1)
        print('rendered', t.name, t.size, t.notes)
