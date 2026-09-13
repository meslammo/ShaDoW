"""MOD-35.1: free-first site/building design primitives.

Turns measured land/building inputs into a deterministic design brief and a
simple geometry model. This is not a structural-engineering approval system.
It is an assistant layer for concepts, room layouts, area calculations and
CAD/BIM adapters.
"""
from __future__ import annotations
from dataclasses import dataclass, asdict
from typing import Iterable

@dataclass(frozen=True)
class Rect:
    name: str
    x: float
    y: float
    width: float
    depth: float
    kind: str = "room"

    @property
    def area(self) -> float:
        return max(0.0, self.width) * max(0.0, self.depth)

@dataclass(frozen=True)
class Site:
    width: float
    depth: float
    unit: str = "m"

    @property
    def area(self) -> float:
        return max(0.0, self.width) * max(0.0, self.depth)

@dataclass(frozen=True)
class DesignBrief:
    site_area: float
    requested_floors: int
    rooms: tuple[str, ...]
    target_built_area: float
    notes: tuple[str, ...]
    geometry: tuple[Rect, ...]


def propose(site: Site, rooms: Iterable[str], floors: int = 1,
            coverage: float = 0.60, notes: Iterable[str] = ()) -> DesignBrief:
    if site.width <= 0 or site.depth <= 0:
        raise ValueError("site dimensions must be positive")
    if floors < 1:
        raise ValueError("floors must be >= 1")
    coverage = min(max(float(coverage), 0.05), 0.95)
    names = tuple(r.strip() for r in rooms if r and r.strip())
    built = site.area * coverage
    n = max(1, len(names))
    cols = max(1, int(n ** 0.5))
    rows = (n + cols - 1) // cols
    w = (site.width * coverage) / cols
    d = (site.depth * coverage) / rows
    geometry = tuple(Rect(name, (i % cols) * w, (i // cols) * d, w, d)
                     for i, name in enumerate(names))
    return DesignBrief(site.area, floors, names, built, tuple(notes), geometry)


def to_dict(brief: DesignBrief) -> dict:
    return {**asdict(brief), "geometry": [asdict(r) for r in brief.geometry]}


def to_dxf(brief: DesignBrief) -> str:
    """Return a minimal ASCII DXF with room rectangles for CAD import."""
    out = ["0", "SECTION", "2", "ENTITIES"]
    for r in brief.geometry:
        x1, y1, x2, y2 = r.x, r.y, r.x + r.width, r.y + r.depth
        pts = [(x1,y1),(x2,y1),(x2,y2),(x1,y2),(x1,y1)]
        for a,b in zip(pts, pts[1:]):
            out += ["0","LINE","8","SHADOW_ROOMS","10",str(a[0]),"20",str(a[1]),"11",str(b[0]),"21",str(b[1])]
    out += ["0", "ENDSEC", "0", "EOF"]
    return "\n".join(out) + "\n"
