"""Build every Plata rank's model and texture:  python tools/plata/build.py"""
import common  # noqa: F401  (path setup)
import scuttler
import shardcaster
import warden

RANKS = (scuttler, shardcaster, warden)


def main():
    for rank in RANKS:
        m = rank.build()
        parts = len(m.parts)
        cubes = sum(len(p.cubes) for p in m.parts)
        print(f"{m.id}: {parts} parts, {cubes} cubes, {m.tex_w}x{m.tex_h}")


if __name__ == "__main__":
    main()
