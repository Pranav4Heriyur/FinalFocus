#!/usr/bin/env python3
"""
Import the FOCUS Workout Asset Pack (C0-C5) into the Android project.

    python3 tools/import_workout_assets.py <path-to-unzipped-pack> [--project <repo root>]

Outputs (idempotent, safe to re-run):
  app/src/main/res/drawable/wk_ui_<name>.xml        static UI icons (tintable)
  app/src/main/res/drawable/wk_ex_<exercise_id>.xml static exercise pictograms (id from exercise-catalog.json)
  app/src/main/res/drawable/wk_fx_<name>.xml        static frame of the one-shot/loop effect SVGs
  app/src/main/java/com/pranav/focus/workout/WorkoutAssets.kt   generated registry (stable IDs -> R.drawable)

Animated SVGs are NOT shipped as SVG: SMIL is unsupported by Android's VectorDrawable and by Compose.
Every exercise animation in the pack is the same two motions (halo pulse + 1.2-unit bob); those are
re-implemented natively in ExerciseMotion.kt and parameters are verified here against the source SVGs.
"""
import json, re, sys, argparse
import xml.etree.ElementTree as ET
from pathlib import Path

NS = "{http://www.w3.org/2000/svg}"
ANDROID = 'xmlns:android="http://schemas.android.com/apk/res/android"'

def rid(s): return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")

def f(v):
    v = float(v)
    return str(int(v)) if v == int(v) else ("%.4f" % v).rstrip("0").rstrip(".")

def circle_d(cx, cy, r):
    cx, cy, r = float(cx), float(cy), float(r)
    return f"M{f(cx-r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2*r)},0a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0"

def rect_d(x, y, w, h, rx):
    x, y, w, h, rx = map(float, (x, y, w, h, rx))
    if rx <= 0:
        return f"M{f(x)},{f(y)}h{f(w)}v{f(h)}h{f(-w)}z"
    rx = min(rx, w / 2, h / 2)
    return (f"M{f(x+rx)},{f(y)}h{f(w-2*rx)}a{f(rx)},{f(rx)} 0 0 1 {f(rx)},{f(rx)}v{f(h-2*rx)}"
            f"a{f(rx)},{f(rx)} 0 0 1 {f(-rx)},{f(rx)}h{f(-(w-2*rx))}a{f(rx)},{f(rx)} 0 0 1 {f(-rx)},{f(-rx)}"
            f"v{f(-(h-2*rx))}a{f(rx)},{f(rx)} 0 0 1 {f(rx)},{f(-rx)}z")

def color(c, default=None):
    if c is None: return default
    if c == "none": return None
    if c == "currentColor": return "#FFFFFFFF"
    c = c.lstrip("#")
    if len(c) == 3: c = "".join(ch * 2 for ch in c)
    return "#FF" + c.upper()

INHERIT = ["fill", "stroke", "stroke-width", "stroke-opacity", "fill-opacity",
           "stroke-linecap", "stroke-linejoin", "opacity"]

def convert(svg_path: Path) -> str:
    root = ET.parse(svg_path).getroot()
    vb = [float(x) for x in root.get("viewBox").split()]
    paths = []

    def walk(el, inh):
        st = dict(inh)
        for k in INHERIT:
            if el.get(k) is not None: st[k] = el.get(k)
        tag = el.tag.replace(NS, "")
        d = None
        if tag == "path": d = el.get("d")
        elif tag == "circle": d = circle_d(el.get("cx", 0), el.get("cy", 0), el.get("r"))
        elif tag == "rect": d = rect_d(el.get("x", 0), el.get("y", 0), el.get("width"), el.get("height"), el.get("rx", 0))
        if d:
            a = []
            fill = color(st.get("fill", "#000000"))
            stroke = color(st.get("stroke"))
            if fill:
                a.append(f'android:fillColor="{fill}"')
                if "fill-opacity" in st: a.append(f'android:fillAlpha="{float(st["fill-opacity"])}"')
            if stroke:
                a.append(f'android:strokeColor="{stroke}"')
                a.append(f'android:strokeWidth="{st.get("stroke-width", "1")}"')
                if "stroke-opacity" in st: a.append(f'android:strokeAlpha="{float(st["stroke-opacity"])}"')
                a.append(f'android:strokeLineCap="{st.get("stroke-linecap", "butt")}"')
                a.append(f'android:strokeLineJoin="{st.get("stroke-linejoin", "miter")}"')
            paths.append("    <path\n        " + "\n        ".join(a) + f'\n        android:pathData="{d}" />')
        for ch in el:
            if ch.tag.replace(NS, "") in ("g", "path", "circle", "rect"): walk(ch, st)

    walk(root, {"fill": root.get("fill", "#000000")})
    w, h = f(vb[2]), f(vb[3])
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<vector {ANDROID}\n    android:width="{w}dp"\n    android:height="{h}dp"\n'
            f'    android:viewportWidth="{w}"\n    android:viewportHeight="{h}">\n' + "\n".join(paths) + "\n</vector>\n")

def verify_motion(pack: Path, catalog):
    """Assert every exercise animation matches the two motions implemented natively."""
    bad = []
    for e in catalog:
        t = (pack / "animations/exercises" / f"{e['id']}-animated.svg").read_text()
        ok = ('values="0 0;0 -1.2;0 0" dur="1.6s"' in t) and ('values=".08;.28;.08" dur="2.4s"' in t)
        if not ok: bad.append(e["id"])
    if bad: sys.exit(f"Animation params differ from ExerciseMotion for: {bad}")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pack"); ap.add_argument("--project", default=str(Path(__file__).resolve().parents[1]))
    a = ap.parse_args()
    pack, proj = Path(a.pack), Path(a.project)
    drawable = proj / "app/src/main/res/drawable"
    kt = proj / "app/src/main/java/com/pranav/focus/workout"
    drawable.mkdir(parents=True, exist_ok=True); kt.mkdir(parents=True, exist_ok=True)

    catalog = json.loads((pack / "docs/exercise-catalog.json").read_text())
    verify_motion(pack, catalog)

    ui, ex, fx = {}, [], {}
    for p in sorted((pack / "icons/ui").glob("*.svg")):
        n = "wk_ui_" + rid(p.stem); (drawable / f"{n}.xml").write_text(convert(p)); ui[p.stem] = n
    for e in catalog:
        n = "wk_ex_" + rid(e["id"]); (drawable / f"{n}.xml").write_text(convert(pack / e["path"])); ex.append((e, n))
    for p in sorted((pack / "animations/svg").glob("*.svg")):
        n = "wk_fx_" + rid(p.stem); (drawable / f"{n}.xml").write_text(convert(p)); fx[p.stem] = n

    L = ["// GENERATED by tools/import_workout_assets.py - do not edit by hand.",
         "package com.pranav.focus.workout", "", "import androidx.annotation.DrawableRes", "import com.pranav.focus.R", "",
         "enum class RoutineType { GYM, MORNING }", "",
         "data class ExerciseAsset(val id: String, val name: String, val routine: RoutineType, @DrawableRes val icon: Int)", "",
         "/** Single source of truth: stable exercise/UI icon IDs -> drawables. Never reference wk_* drawables elsewhere. */",
         "object WorkoutAssets {", "    val exercises: List<ExerciseAsset> = listOf("]
    for e, n in ex:
        L.append(f'        ExerciseAsset("{e["id"]}", "{e["name"].replace(chr(34), chr(92)+chr(34))}", RoutineType.{e["routineType"].upper()}, R.drawable.{n}),')
    L += ["    )", "    private val byId = exercises.associateBy { it.id }",
          "    fun exercise(id: String): ExerciseAsset? = byId[id]", "",
          "    /** Stable UI icon keys (file stem in the pack). */", "    @DrawableRes fun ui(key: String): Int = UI[key] ?: error(\"Unknown workout UI icon: $key\")",
          "    private val UI: Map<String, Int> = mapOf("]
    for k, n in ui.items(): L.append(f'        "{k}" to R.drawable.{n},')
    L += ["    )", "    /** Static frames of the effect animations (animated natively in WorkoutFx). */", "    val FX: Map<String, Int> = mapOf("]
    for k, n in fx.items(): L.append(f'        "{k}" to R.drawable.{n},')
    L += ["    )", "}", ""]
    (kt / "WorkoutAssets.kt").write_text("\n".join(L))
    print(f"ui={len(ui)} exercises={len(ex)} fx={len(fx)} -> {drawable}")

if __name__ == "__main__":
    main()
