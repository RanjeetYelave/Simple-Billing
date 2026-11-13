#!/usr/bin/env python3
"""
RupeeCRM - Master Application Branding & Technical Icon Generator

Renders the approved vector SVG artwork (billsoft/src/main/webapp/icons/icon.svg)
into production-ready application icons for macOS (.icns), Windows (.ico), and Web/PWA (.png):
- Complete Apple ICNS with all 10 Retina sizes up to 1024x1024 (ic10/ic12)
- Multi-resolution Windows ICO with 7 sizes (16, 24, 32, 48, 64, 128, 256)
- High-resolution 1024x1024 master PNG for Electron branding and macOS Dock runtime
"""

import os
import shutil
import struct
import subprocess
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SVG_SOURCE = os.path.join(REPO_ROOT, 'billsoft', 'src', 'main', 'webapp', 'icons', 'icon.svg')

TEMP_DIR = '/tmp/rupeecrm_branding_build'
os.makedirs(TEMP_DIR, exist_ok=True)

print(f"[RupeeCRM Icon Builder] Master SVG Source: {SVG_SOURCE}")
if not os.path.exists(SVG_SOURCE):
    print(f"ERROR: Master SVG source not found at {SVG_SOURCE}")
    sys.exit(1)

# 1. Generate 1024x1024 Master SVG & PNG
svg_1024_path = os.path.join(TEMP_DIR, 'icon_1024.svg')
png_1024_path = os.path.join(TEMP_DIR, 'icon_1024.png')

with open(SVG_SOURCE, 'r', encoding='utf-8') as f:
    svg_content = f.read()

# Ensure 1024x1024 viewBox / dimensions
svg_1024 = svg_content.replace('width="512" height="512"', 'width="1024" height="1024"')
with open(svg_1024_path, 'w', encoding='utf-8') as f:
    f.write(svg_1024)

# Rasterize via sips (macOS native CoreGraphics vector rendering)
subprocess.run(['sips', '-s', 'format', 'png', svg_1024_path, '--out', png_1024_path], check=True, stdout=subprocess.DEVNULL)
print(f"  -> Generated 1024x1024 Master PNG: {png_1024_path}")

# 2. Build Apple ICNS with complete iconset (all 10 sizes)
iconset_dir = os.path.join(TEMP_DIR, 'RupeeCRM.iconset')
if os.path.exists(iconset_dir):
    shutil.rmtree(iconset_dir)
os.makedirs(iconset_dir, exist_ok=True)

mac_specs = [
    ('icon_16x16.png', 16),
    ('icon_16x16@2x.png', 32),
    ('icon_32x32.png', 32),
    ('icon_32x32@2x.png', 64),
    ('icon_128x128.png', 128),
    ('icon_128x128@2x.png', 256),
    ('icon_256x256.png', 256),
    ('icon_256x256@2x.png', 512),
    ('icon_512x512.png', 512),
    ('icon_512x512@2x.png', 1024),
]

for name, sz in mac_specs:
    target_file = os.path.join(iconset_dir, name)
    subprocess.run(['sips', '-z', str(sz), str(sz), png_1024_path, '--out', target_file], check=True, stdout=subprocess.DEVNULL)

master_icns_path = os.path.join(TEMP_DIR, 'RupeeCRM.icns')
subprocess.run(['iconutil', '-c', 'icns', iconset_dir, '-o', master_icns_path], check=True)
print(f"  -> Generated Apple ICNS: {master_icns_path} ({os.path.getsize(master_icns_path)} bytes)")

# 3. Build Windows Multi-Resolution ICO
win_sizes = [16, 24, 32, 48, 64, 128, 256]
temp_win_pngs = {}
for s in win_sizes:
    p = os.path.join(TEMP_DIR, f'win_{s}.png')
    subprocess.run(['sips', '-z', str(s), str(s), png_1024_path, '--out', p], check=True, stdout=subprocess.DEVNULL)
    temp_win_pngs[s] = p

ico_entries = []
offset = 6 + 16 * len(win_sizes)
png_datas = []

for s in win_sizes:
    p = temp_win_pngs[s]
    with open(p, 'rb') as f:
        data = f.read()
    png_datas.append(data)
    b_w = 0 if s == 256 else s
    b_h = 0 if s == 256 else s
    entry = struct.pack('<BBBBHHII', b_w, b_h, 0, 0, 1, 32, len(data), offset)
    ico_entries.append(entry)
    offset += len(data)

header = struct.pack('<HHH', 0, 1, len(win_sizes))
master_ico_path = os.path.join(TEMP_DIR, 'RupeeCRM.ico')
with open(master_ico_path, 'wb') as f:
    f.write(header)
    for entry in ico_entries:
        f.write(entry)
    for data in png_datas:
        f.write(data)

print(f"  -> Generated Windows ICO: {master_ico_path} ({os.path.getsize(master_ico_path)} bytes, {len(win_sizes)} resolutions)")

# 4. Generate 512x512 and 192x192 PNGs
png_512_path = os.path.join(TEMP_DIR, 'icon_512.png')
png_192_path = os.path.join(TEMP_DIR, 'icon_192.png')
subprocess.run(['sips', '-z', '512', '512', png_1024_path, '--out', png_512_path], check=True, stdout=subprocess.DEVNULL)
subprocess.run(['sips', '-z', '192', '192', png_1024_path, '--out', png_192_path], check=True, stdout=subprocess.DEVNULL)

# 5. Deploy to target directories
destinations = [
    # Electron desktop assets
    (master_icns_path, os.path.join(REPO_ROOT, 'desktop', 'electron', 'assets', 'icon.icns')),
    (master_ico_path, os.path.join(REPO_ROOT, 'desktop', 'electron', 'assets', 'icon.ico')),
    (png_1024_path, os.path.join(REPO_ROOT, 'desktop', 'electron', 'assets', 'icon.png')),
    # Tools assets
    (master_icns_path, os.path.join(REPO_ROOT, 'tools', 'assets', 'RupeeCRM.icns')),
    (master_ico_path, os.path.join(REPO_ROOT, 'tools', 'assets', 'RupeeCRM.ico')),
    # Webapp branding & favicon
    (master_ico_path, os.path.join(REPO_ROOT, 'billsoft', 'src', 'main', 'webapp', 'assets', 'branding', 'rupeecrm-app-icon.ico')),
    (master_ico_path, os.path.join(REPO_ROOT, 'billsoft', 'src', 'main', 'webapp', 'favicon.ico')),
    (png_512_path, os.path.join(REPO_ROOT, 'billsoft', 'src', 'main', 'webapp', 'icons', 'icon-512.png')),
    (png_512_path, os.path.join(REPO_ROOT, 'billsoft', 'src', 'main', 'webapp', 'icons', 'icon-maskable-512.png')),
    (png_192_path, os.path.join(REPO_ROOT, 'billsoft', 'src', 'main', 'webapp', 'icons', 'icon-192.png')),
    (png_192_path, os.path.join(REPO_ROOT, 'billsoft', 'src', 'main', 'webapp', 'icons', 'icon-maskable-192.png')),
]

for src, dst in destinations:
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)
    print(f"  ✓ Deployed -> {os.path.relpath(dst, REPO_ROOT)} ({os.path.getsize(dst)} bytes)")

print("\n[RupeeCRM Icon Builder] All branding assets built and synchronized successfully.")
