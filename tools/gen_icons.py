# -*- coding: utf-8 -*-
"""Generate launcher icons for Свобода 2.0 from docs/logo_orig (JPEG)."""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "logo_orig")
RES = os.path.join(ROOT, "app", "src", "main", "res")

img = Image.open(SRC).convert("RGBA")
print("source:", img.size, img.mode)

# 1) Legacy launcher icons (square, scaled).
legacy = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
for folder, size in legacy.items():
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    resized = img.resize((size, size), Image.LANCZOS)
    resized.save(os.path.join(d, "ic_launcher.png"))
    resized.save(os.path.join(d, "ic_launcher_round.png"))
    print("wrote", folder, size)

# 2) Adaptive icon foreground: 108dp canvas, logo inside safe zone (~66%).
fore_size = 432
foreground = Image.new("RGBA", (fore_size, fore_size), (0, 0, 0, 0))
logo_size = int(fore_size * 0.62)
logo = img.resize((logo_size, logo_size), Image.LANCZOS)
offset = (fore_size - logo_size) // 2
foreground.paste(logo, (offset, offset), logo)
d = os.path.join(RES, "drawable-nodpi")
os.makedirs(d, exist_ok=True)
foreground.save(os.path.join(d, "ic_launcher_foreground.png"))
print("wrote adaptive foreground", fore_size)

# 3) Background color.
d = os.path.join(RES, "values")
os.makedirs(d, exist_ok=True)
with open(os.path.join(d, "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            '    <color name="ic_launcher_background">#080C11</color>\n</resources>\n')

# 4) Adaptive icon XML (anydpi-v26).
d = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(d, exist_ok=True)
xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
       '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
       '    <background android:drawable="@color/ic_launcher_background" />\n'
       '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
       '</adaptive-icon>\n')
with open(os.path.join(d, "ic_launcher.xml"), "w", encoding="utf-8") as f:
    f.write(xml)
with open(os.path.join(d, "ic_launcher_round.xml"), "w", encoding="utf-8") as f:
    f.write(xml)
print("wrote adaptive xml")
