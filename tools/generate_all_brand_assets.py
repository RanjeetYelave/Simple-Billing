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

# 1. Master Modern SVG Icon (Squircle + White Rupee + 3 Ascending Rich Green Growth Arrows)
# Matches approved reference image Option 5 & Electron App Icon
svg_app_master = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" fill="none">
  <defs>
    <linearGradient id="appBgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#1E293B"/>
      <stop offset="50%" stop-color="#0F172A"/>
      <stop offset="100%" stop-color="#0B132B"/>
    </linearGradient>
    <linearGradient id="growthGrad" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#16845B"/>
      <stop offset="100%" stop-color="#39B987"/>
    </linearGradient>
    <filter id="softGlow" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="6" stdDeviation="12" flood-color="#000000" flood-opacity="0.45"/>
    </filter>
  </defs>

  <!-- Squircle Rounded Base with subtle border -->
  <rect width="512" height="512" rx="116" fill="url(#appBgGrad)"/>
  <rect width="506" height="506" x="3" y="3" rx="113" fill="none" stroke="rgba(255,255,255,0.15)" stroke-width="2.5"/>

  <!-- Combined Symbol: ₹ + 3 Ascending Geometric Arrows -->
  <g filter="url(#softGlow)" transform="translate(14, 12)">
    <!-- Stylized Indian Rupee ₹ Symbol -->
    <path d="M 82 150 H 210 M 82 210 H 188 M 82 150 V 336 M 82 220 C 146 220 180 246 180 284 C 180 320 144 344 82 344 M 138 284 L 234 406" 
          stroke="#FFFFFF" stroke-width="38" stroke-linecap="round" stroke-linejoin="round"/>

    <!-- 3 Ascending Growth Chevrons / Arrows -->
    <!-- Lower Arrow 1 -->
    <path d="M 235 320 L 295 260 M 295 260 H 250 M 295 260 V 305" 
          stroke="url(#growthGrad)" stroke-width="30" stroke-linecap="round" stroke-linejoin="round"/>

    <!-- Mid Arrow 2 -->
    <path d="M 295 250 L 370 175 M 370 175 H 320 M 370 175 V 225" 
          stroke="url(#growthGrad)" stroke-width="34" stroke-linecap="round" stroke-linejoin="round"/>

    <!-- Upper Arrow 3 -->
    <path d="M 365 170 L 445 90 M 445 90 H 390 M 445 90 V 145" 
          stroke="url(#growthGrad)" stroke-width="38" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>
"""

# Maskable icon (with safe-zone inset)
svg_maskable = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" fill="none">
  <defs>
    <linearGradient id="maskBg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#1E293B"/>
      <stop offset="50%" stop-color="#0F172A"/>
      <stop offset="100%" stop-color="#0B132B"/>
    </linearGradient>
    <linearGradient id="growthGradMask" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#16845B"/>
      <stop offset="100%" stop-color="#39B987"/>
    </linearGradient>
  </defs>

  <rect width="512" height="512" fill="url(#maskBg)"/>

  <g transform="translate(68, 64) scale(0.74)">
    <!-- Stylized Indian Rupee ₹ Symbol -->
    <path d="M 82 150 H 210 M 82 210 H 188 M 82 150 V 336 M 82 220 C 146 220 180 246 180 284 C 180 320 144 344 82 344 M 138 284 L 234 406" 
          stroke="#FFFFFF" stroke-width="38" stroke-linecap="round" stroke-linejoin="round"/>

    <!-- 3 Ascending Growth Chevrons / Arrows -->
    <path d="M 235 320 L 295 260 M 295 260 H 250 M 295 260 V 305" 
          stroke="url(#growthGradMask)" stroke-width="30" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M 295 250 L 370 175 M 370 175 H 320 M 370 175 V 225" 
          stroke="url(#growthGradMask)" stroke-width="34" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M 365 170 L 445 90 M 445 90 H 390 M 445 90 V 145" 
          stroke="url(#growthGradMask)" stroke-width="38" stroke-linecap="round" stroke-linejoin="round"/>
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
