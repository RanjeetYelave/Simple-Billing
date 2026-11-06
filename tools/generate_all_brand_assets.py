#!/usr/bin/env python3
import os
import struct
import subprocess

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ELECTRON_ASSETS = os.path.join(REPO_ROOT, "desktop", "electron", "assets")
TOOLS_ASSETS = os.path.join(REPO_ROOT, "tools", "assets")
WEBAPP_ICONS = os.path.join(REPO_ROOT, "billsoft", "src", "main", "webapp", "icons")
WEBAPP_ROOT = os.path.join(REPO_ROOT, "billsoft", "src", "main", "webapp")

os.makedirs(ELECTRON_ASSETS, exist_ok=True)
os.makedirs(TOOLS_ASSETS, exist_ok=True)
os.makedirs(WEBAPP_ICONS, exist_ok=True)

# 1. Master Modern SVG Icon (Squircle + White Rupee + 3 Ascending Bars + Integrated Upward Arrow)
# Matches approved reference image Option C — Refined & Premium
svg_app_master = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" fill="none">
  <defs>
    <linearGradient id="appBgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0F281B"/>
      <stop offset="50%" stop-color="#0A1C14"/>
      <stop offset="100%" stop-color="#07150E"/>
    </linearGradient>
    <linearGradient id="growthGrad" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#16845B"/>
      <stop offset="100%" stop-color="#39B987"/>
    </linearGradient>
    <filter id="softGlow" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="6" stdDeviation="10" flood-color="#000000" flood-opacity="0.4"/>
    </filter>
  </defs>

  <!-- Squircle Rounded Base with subtle border -->
  <rect width="512" height="512" rx="116" fill="url(#appBgGrad)"/>
  <rect width="506" height="506" x="3" y="3" rx="113" fill="none" stroke="rgba(255,255,255,0.15)" stroke-width="2.5"/>

  <!-- Approved Symbol (Option C): ₹ + 3 Ascending Rounded Bars + Integrated Upward Arrow -->
  <g filter="url(#softGlow)" transform="translate(74, 115)">
    <!-- Slanted Indian Rupee (₹) Symbol in Crisp White -->
    <g stroke="#FFFFFF" stroke-linecap="round" stroke-linejoin="round">
      <path d="M 18 40 H 142" stroke-width="25"/>
      <path d="M 18 82 H 128" stroke-width="25"/>
      <path d="M 38 40 V 144" stroke-width="25"/>
      <path d="M 38 82 C 102 82 118 96 118 116 C 118 144 88 144 38 144" stroke-width="25"/>
      <path d="M 58 142 L 112 235" stroke-width="27"/>
    </g>

    <!-- Three Ascending Rounded Bars (Option C) -->
    <rect x="152" y="165" width="34" height="70" rx="17" fill="url(#growthGrad)"/>
    <rect x="200" y="120" width="34" height="115" rx="17" fill="url(#growthGrad)"/>
    <rect x="248" y="75" width="34" height="160" rx="17" fill="url(#growthGrad)"/>

    <!-- Integrated Upward-Trending Arrow (Option C) -->
    <path d="M 144 205 L 275 78" stroke="url(#growthGrad)" stroke-width="26" stroke-linecap="round"/>
    <polygon points="320,32 250,48 278,76 304,104" fill="url(#growthGrad)" stroke="url(#growthGrad)" stroke-width="4" stroke-linejoin="round"/>
  </g>
</svg>
"""

# Maskable icon (with safe-zone inset)
svg_maskable = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" fill="none">
  <defs>
    <linearGradient id="maskBg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0F281B"/>
      <stop offset="50%" stop-color="#0A1C14"/>
      <stop offset="100%" stop-color="#07150E"/>
    </linearGradient>
    <linearGradient id="growthGradMask" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#16845B"/>
      <stop offset="100%" stop-color="#39B987"/>
    </linearGradient>
  </defs>

  <rect width="512" height="512" fill="url(#maskBg)"/>

  <g transform="translate(94, 130) scale(0.78)">
    <!-- Slanted Indian Rupee (₹) Symbol in Crisp White -->
    <g stroke="#FFFFFF" stroke-linecap="round" stroke-linejoin="round">
      <path d="M 18 40 H 142" stroke-width="25"/>
      <path d="M 18 82 H 128" stroke-width="25"/>
      <path d="M 38 40 V 144" stroke-width="25"/>
      <path d="M 38 82 C 102 82 118 96 118 116 C 118 144 88 144 38 144" stroke-width="25"/>
      <path d="M 58 142 L 112 235" stroke-width="27"/>
    </g>

    <!-- Three Ascending Rounded Bars (Option C) -->
    <rect x="152" y="165" width="34" height="70" rx="17" fill="url(#growthGradMask)"/>
    <rect x="200" y="120" width="34" height="115" rx="17" fill="url(#growthGradMask)"/>
    <rect x="248" y="75" width="34" height="160" rx="17" fill="url(#growthGradMask)"/>

    <!-- Integrated Upward-Trending Arrow (Option C) -->
    <path d="M 144 205 L 275 78" stroke="url(#growthGradMask)" stroke-width="26" stroke-linecap="round"/>
    <polygon points="320,32 250,48 278,76 304,104" fill="url(#growthGradMask)" stroke="url(#growthGradMask)" stroke-width="4" stroke-linejoin="round"/>
  </g>
</svg>
"""

tmp_master_svg = "/tmp/rupeecrm_app_master.svg"
tmp_maskable_svg = "/tmp/rupeecrm_app_maskable.svg"

with open(tmp_master_svg, "w") as f:
    f.write(svg_app_master)

with open(tmp_maskable_svg, "w") as f:
    f.write(svg_maskable)

with open(os.path.join(WEBAPP_ICONS, "icon.svg"), "w") as f:
    f.write(svg_app_master)

with open(os.path.join(WEBAPP_ICONS, "icon-maskable.svg"), "w") as f:
    f.write(svg_maskable)

print("[1/4] Saved vector SVGs to webapp/icons/")

# Render PNG thumbnails using qlmanage
tmp_dir = "/tmp/rcrm_renders"
os.makedirs(tmp_dir, exist_ok=True)

subprocess.run(["qlmanage", "-t", "-s", "512", "-o", tmp_dir, tmp_master_svg], check=True)
master_png_512 = os.path.join(tmp_dir, "rupeecrm_app_master.svg.png")

subprocess.run(["qlmanage", "-t", "-s", "512", "-o", tmp_dir, tmp_maskable_svg], check=True)
maskable_png_512 = os.path.join(tmp_dir, "rupeecrm_app_maskable.svg.png")

# Save master 512x512 PNGs
subprocess.run(["cp", master_png_512, os.path.join(ELECTRON_ASSETS, "icon.png")], check=True)
subprocess.run(["cp", master_png_512, os.path.join(WEBAPP_ICONS, "icon-512.png")], check=True)
subprocess.run(["cp", maskable_png_512, os.path.join(WEBAPP_ICONS, "icon-maskable-512.png")], check=True)

# Generate 192x192 PNGs
pwa_192 = os.path.join(WEBAPP_ICONS, "icon-192.png")
subprocess.run(["sips", "-z", "192", "192", master_png_512, "--out", pwa_192], check=True)

pwa_mask_192 = os.path.join(WEBAPP_ICONS, "icon-maskable-192.png")
subprocess.run(["sips", "-z", "192", "192", maskable_png_512, "--out", pwa_mask_192], check=True)

print("[2/4] Generated master PNGs (512x512, 192x192)")

# ICO packaging helper
def build_ico(png_items):
    count = len(png_items)
    header = struct.pack('<HHH', 0, 1, count)
    entries = []
    offset = 6 + 16 * count
    data_blobs = []
    for width, height, data in png_items:
        w_byte = 0 if width >= 256 else width
        h_byte = 0 if height >= 256 else height
        size_bytes = len(data)
        entry = struct.pack('<BBBBHHII', w_byte, h_byte, 0, 0, 1, 32, size_bytes, offset)
        entries.append(entry)
        data_blobs.append(data)
        offset += size_bytes
    return header + b''.join(entries) + b''.join(data_blobs)

# Generate multi-resolution icons for Windows ICO (256, 128, 64, 48, 32, 16)
win_sizes = [256, 128, 64, 48, 32, 16]
win_png_items = []
for s in win_sizes:
    out_path = os.path.join(tmp_dir, f"win_{s}.png")
    subprocess.run(["sips", "-z", str(s), str(s), master_png_512, "--out", out_path], check=True)
    with open(out_path, "rb") as f:
        win_png_items.append((s, s, f.read()))

full_ico_data = build_ico(win_png_items)

# Write desktop/electron/assets/icon.ico and tools/assets/RupeeCRM.ico
electron_ico = os.path.join(ELECTRON_ASSETS, "icon.ico")
with open(electron_ico, "wb") as f:
    f.write(full_ico_data)

tools_ico = os.path.join(TOOLS_ASSETS, "RupeeCRM.ico")
with open(tools_ico, "wb") as f:
    f.write(full_ico_data)

print("[3/4] Generated multi-resolution Windows ICOs (256..16)")

# Generate favicon.ico for webapp (64, 48, 32, 16)
fav_sizes = [64, 48, 32, 16]
fav_png_items = []
for s in fav_sizes:
    out_path = os.path.join(tmp_dir, f"win_{s}.png")
    with open(out_path, "rb") as f:
        fav_png_items.append((s, s, f.read()))

fav_ico_data = build_ico(fav_png_items)
webapp_fav = os.path.join(WEBAPP_ROOT, "favicon.ico")
with open(webapp_fav, "wb") as f:
    f.write(fav_ico_data)

print("[4/4] Generated favicon.ico in webapp root")
print("All brand assets generated successfully!")
