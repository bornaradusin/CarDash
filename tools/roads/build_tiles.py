#!/usr/bin/env python3
"""
Builds CarDash's road data from an OpenStreetMap extract: speed limits, traffic lights and speed
cameras, cut into small squares the app downloads as the car reaches them (and keeps).

    build_tiles.py filtered.osm.pbf out/ [garda/]

Each square is TILE_LAT by TILE_LON degrees (about 5.5 by 5.3 km in Ireland), saved as
out/tiles/<row>_<col>.json, where row = floor(lat / TILE_LAT) and col = floor(lon / TILE_LON):

    {"v": 1,
     "r": [[kmh, lat, lon, dlat, dlon, dlat, dlon, ...], ...],   roads with a limit: the first point,
                                                                  then each next one as a step
     "l": [lat, lon, lat, lon, ...],                             traffic lights
     "c": [lat, lon, kmh, ...],                                  speed cameras (kmh 0 if not known)
     "z": [[name, lat, lon, dlat, dlon, ...], ...]}               GoSafe speed van zones: stretches of road

With a garda/ folder, the Garda's own files are added: the GoSafe zones (every .kmz, the stretches of
road the speed vans work on) and the static cameras (static.kml, from their map of them).

All coordinates are whole numbers of 1/100,000 of a degree (about a metre). A road is in every
square it passes within MARGIN of, so the car finds it near an edge. out/region.json says what the
data covers: a square inside it that has no file has nothing in it.
"""
import json
import math
import os
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone

import osmium

TILE_LAT = 0.05
TILE_LON = 0.08
# A road or light this close to a square's edge goes in the next square too (degrees, about 60 m)
MARGIN = 0.0006
SCALE = 100_000

DRIVEN = {
    "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
    "living_street", "service", "motorway_link", "trunk_link", "primary_link", "secondary_link",
    "tertiary_link", "road",
}


def kmh(tag):
    """'50' → 50, '30 mph' → 48; 'IE:rural', 'none', 'signals' and the like → None."""
    if not tag:
        return None
    t = tag.split(";")[0].strip().lower()
    m = re.match(r"^(\d{1,3})(\s*mph)?$", t)
    if not m:
        return None
    v = int(m.group(1))
    return int(v * 1.609) if m.group(2) else v


def squares(lat, lon, margin=MARGIN):
    """The squares a point is in, counting the margin round it."""
    rows = range(math.floor((lat - margin) / TILE_LAT), math.floor((lat + margin) / TILE_LAT) + 1)
    cols = range(math.floor((lon - margin) / TILE_LON), math.floor((lon + margin) / TILE_LON) + 1)
    return [(r, c) for r in rows for c in cols]


class Collect(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.tiles = {}
        self.bounds = [90.0, 180.0, -90.0, -180.0]
        self.counts = {"roads": 0, "lights": 0, "cameras": 0, "zones": 0, "garda_cameras": 0}

    def tile(self, key):
        t = self.tiles.get(key)
        if t is None:
            t = self.tiles[key] = {"r": [], "l": [], "c": [], "z": []}
        return t

    def seen(self, lat, lon):
        b = self.bounds
        b[0], b[1], b[2], b[3] = min(b[0], lat), min(b[1], lon), max(b[2], lat), max(b[3], lon)

    def node(self, n):
        hw = n.tags.get("highway")
        if hw not in ("traffic_signals", "speed_camera") or not n.location.valid():
            return
        lat, lon = n.location.lat, n.location.lon
        self.seen(lat, lon)
        a, b = round(lat * SCALE), round(lon * SCALE)
        for key in squares(lat, lon):
            t = self.tile(key)
            if hw == "traffic_signals":
                t["l"] += [a, b]
            else:
                t["c"] += [a, b, kmh(n.tags.get("maxspeed")) or 0]
        self.counts["lights" if hw == "traffic_signals" else "cameras"] += 1

    def line_squares(self, pts):
        """Every square a line passes within the margin of."""
        keys = set()
        for lat, lon in pts:
            keys.update(squares(lat, lon))
        for (a_lat, a_lon), (b_lat, b_lon) in zip(pts, pts[1:]):
            steps = int(max(abs(b_lat - a_lat) / TILE_LAT, abs(b_lon - a_lon) / TILE_LON) * 4)
            for i in range(1, steps):
                f = i / steps
                keys.update(squares(a_lat + (b_lat - a_lat) * f, a_lon + (b_lon - a_lon) * f))
        return keys

    def zone(self, name, pts):
        if len(pts) < 2:
            return
        enc = [name]
        prev = None
        for lat, lon in pts:
            p = (round(lat * SCALE), round(lon * SCALE))
            enc += list(p) if prev is None else [p[0] - prev[0], p[1] - prev[1]]
            prev = p
        for key in self.line_squares(pts):
            self.tile(key)["z"].append(enc)
        self.counts["zones"] += 1

    def camera(self, lat, lon, limit):
        a, b = round(lat * SCALE), round(lon * SCALE)
        for key in squares(lat, lon):
            self.tile(key)["c"] += [a, b, limit or 0]
        self.counts["garda_cameras"] += 1

    def way(self, w):
        if w.tags.get("highway") not in DRIVEN:
            return
        limit = kmh(w.tags.get("maxspeed"))
        if limit is None:
            return
        pts = []
        for nd in w.nodes:
            if nd.location.valid():
                pts.append((nd.location.lat, nd.location.lon))
        if len(pts) < 2:
            return
        for lat, lon in pts:
            self.seen(lat, lon)
        # A long straight piece can cross a square without a point in it: filled in along each piece
        keys = self.line_squares(pts)
        enc = [limit]
        prev = None
        for lat, lon in pts:
            p = (round(lat * SCALE), round(lon * SCALE))
            enc += list(p) if prev is None else [p[0] - prev[0], p[1] - prev[1]]
            prev = p
        for key in keys:
            self.tile(key)["r"].append(enc)
        self.counts["roads"] += 1


def kml_placemarks(text):
    """(name, [(lat, lon), ...] lines, [(lat, lon)] points) for each Placemark in a KML document."""
    root = ET.fromstring(text)
    for pm in root.iter():
        if not pm.tag.endswith("Placemark"):
            continue
        name = ""
        lines, points = [], []
        for el in pm.iter():
            tag = el.tag.split("}")[-1]
            if tag == "name" and not name:
                name = (el.text or "").strip()
            elif tag == "LineString" or tag == "Point":
                coords = next((c for c in el.iter() if c.tag.endswith("coordinates")), None)
                if coords is None or not coords.text:
                    continue
                pts = []
                for chunk in coords.text.split():
                    parts = chunk.split(",")
                    if len(parts) >= 2:
                        try:
                            pts.append((float(parts[1]), float(parts[0])))
                        except ValueError:
                            pass
                if tag == "LineString":
                    lines.append(pts)
                else:
                    points += pts
        yield name, lines, points


def add_garda(c, folder):
    """The Garda's GoSafe zones (.kmz) and static cameras (static.kml)."""
    for f in sorted(os.listdir(folder)):
        path = os.path.join(folder, f)
        try:
            if f.endswith(".kmz"):
                with zipfile.ZipFile(path) as z:
                    doc = next(n for n in z.namelist() if n.endswith(".kml"))
                    text = z.read(doc)
            elif f.endswith(".kml"):
                text = open(path, "rb").read()
            else:
                continue
            for name, lines, points in kml_placemarks(text):
                if f.startswith("static"):
                    for lat, lon in points:
                        c.camera(lat, lon, None)
                else:
                    for pts in lines:
                        c.zone(name[:80], pts)
                    # A zone given as a point: a short stretch either side isn't known, so the point alone
                    for lat, lon in points:
                        c.zone(name[:80], [(lat, lon), (lat + 0.00001, lon + 0.00001)])
            print(f"{f}: read", file=sys.stderr)
        except Exception as e:  # one bad file shouldn't stop the rest
            print(f"{f}: skipped ({e})", file=sys.stderr)


def main():
    src, out = sys.argv[1], sys.argv[2]
    c = Collect()
    c.apply_file(src, locations=True)
    if len(sys.argv) > 3 and os.path.isdir(sys.argv[3]):
        add_garda(c, sys.argv[3])
    os.makedirs(os.path.join(out, "tiles"), exist_ok=True)
    for (row, col), t in c.tiles.items():
        with open(os.path.join(out, "tiles", f"{row}_{col}.json"), "w") as f:
            json.dump({"v": 1, "r": t["r"], "l": t["l"], "c": t["c"], "z": t["z"]}, f, separators=(",", ":"))
    s, w, n, e = c.bounds
    region = {
        "v": 1,
        "built": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
        "name": os.environ.get("REGION_NAME", "Ireland and Northern Ireland"),
        "tile": [TILE_LAT, TILE_LON],
        # Whole squares: any square from row s..n and column w..e is covered
        "rows": [math.floor(s / TILE_LAT), math.floor(n / TILE_LAT)],
        "cols": [math.floor(w / TILE_LON), math.floor(e / TILE_LON)],
        "counts": c.counts,
        "squares": len(c.tiles),
    }
    with open(os.path.join(out, "region.json"), "w") as f:
        json.dump(region, f, indent=1)
    print(json.dumps(region))


if __name__ == "__main__":
    main()
