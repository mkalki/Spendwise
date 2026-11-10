# Spendwise cinematic landing assets

The original silent 10-second, 1280x720 H.264 loop, poster, and product concepts live in frontend/public/media. No RTFKT footage or models are included. The camera and object transforms use periodic curves for a seamless loop.

Frontend usage requires no rendering libraries. To regenerate from the repository root with Python 3.12 on Windows:

```powershell
python -m pip install -r frontend/scripts/scene-requirements.txt
python -m pip install --upgrade PyOpenGL
python frontend/scripts/render_shopping_film.py
```

The separate PyOpenGL upgrade is needed for current Windows/NumPy compatibility because pyrender pins an older version. Regeneration uses an offscreen OpenGL context and requires an OpenGL-capable driver.

The page honors reduced-motion preferences, pauses while sign-in is open or the tab is hidden, and exposes playback and cinema-view controls. Product cards are illustrative concepts; live product discovery uses the existing sign-in flow.

After regenerating the original film, run `python frontend/scripts/optimize_background.py` to produce the soft-focus copy used by the dashboard. This version bakes the subtle blur into the video, avoiding a live full-screen GPU filter while scrolling. Clear mode uses the original video. The cursor uses native SVG cursor assets and has no animation loop.
