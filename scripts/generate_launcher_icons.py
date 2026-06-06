#!/usr/bin/env python3
"""Generate Portal-compatible launcher icons from design/photo-viewer.png."""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = ROOT / "design" / "photo-viewer.png"

# Portal runs at 160dpi but its launcher reads xxxhdpi directly.
# Do not ship smaller mipmap buckets: Android will pick mdpi (48px) and the
# icon looks tiny/blurry in the apps menu.
PORTAL_ICON_SIZE = 512
PORTAL_MIPMAP = "mipmap-xxxhdpi"
REMOVE_DENSITIES = (
    "mipmap-mdpi",
    "mipmap-hdpi",
    "mipmap-xhdpi",
    "mipmap-xxhdpi",
    "mipmap-anydpi-v26",
)


def load_source() -> Image.Image:
    return Image.open(SOURCE).convert("RGBA")


def resize_icon(source: Image.Image, size: int) -> Image.Image:
    return source.resize((size, size), Image.Resampling.LANCZOS)


def remove_legacy_densities() -> None:
    for folder in REMOVE_DENSITIES:
        path = RES / folder
        if path.exists():
            shutil.rmtree(path)
            print(f"Removed {folder}/")


def write_portal_icons(source: Image.Image) -> None:
    out_dir = RES / PORTAL_MIPMAP
    out_dir.mkdir(parents=True, exist_ok=True)
    icon = resize_icon(source, PORTAL_ICON_SIZE)
    icon.save(out_dir / "ic_launcher.png", optimize=True)
    icon.save(out_dir / "ic_launcher_round.png", optimize=True)
    print(f"Wrote {PORTAL_MIPMAP} ({PORTAL_ICON_SIZE}x{PORTAL_ICON_SIZE})")


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Missing source icon: {SOURCE}")

    remove_legacy_densities()
    write_portal_icons(load_source())


if __name__ == "__main__":
    main()
