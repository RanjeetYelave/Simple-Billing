import struct
import zlib
import subprocess
import os
import shutil

SOURCE_BMP = 'tools/branding_source/rupeecrm_branding_sheet_approved.bmp'
TARGET_DIR = 'billsoft/src/main/webapp/assets/branding'
os.makedirs(TARGET_DIR, exist_ok=True)

with open(SOURCE_BMP, 'rb') as f:
    bmp_data = f.read()

bfOffBits = struct.unpack('<I', bmp_data[10:14])[0]
src_w, src_h = struct.unpack('<ii', bmp_data[18:26])
src_h = abs(src_h)
stride = (src_w * 3 + 3) & ~3

def get_pixel(x, y):
    if x < 0 or x >= src_w or y < 0 or y >= src_h:
        return (0, 0, 0)
    idx = bfOffBits + y * stride + x * 3
    return (bmp_data[idx+2], bmp_data[idx+1], bmp_data[idx])

def write_png(w, h, rgba_bytes, filename):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw.extend(rgba_bytes[y * w * 4 : (y + 1) * w * 4])
    compressed = zlib.compress(raw, level=9)
    def chunk(tag, data):
        length = len(data)
        crc = zlib.crc32(tag + data) & 0xffffffff
        return struct.pack('>I', length) + tag + data + struct.pack('>I', crc)
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    png_data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', compressed) + chunk(b'IEND', b'')
    with open(filename, 'wb') as f:
        f.write(png_data)

def crop_rgba(x1, y1, x2, y2, transparent_bg=None, threshold=20):
    w = x2 - x1 + 1
    h = y2 - y1 + 1
    rgba = bytearray()
    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            r, g, b = get_pixel(x, y)
            if transparent_bg is not None:
                bg_r, bg_g, bg_b = transparent_bg
                dist = max(abs(r - bg_r), abs(g - bg_g), abs(b - bg_b))
                if dist < threshold:
                    a = 0
                elif dist < threshold * 2:
                    a = int((dist - threshold) / threshold * 255)
                else:
                    a = 255
            else:
                a = 255
            rgba.extend([r, g, b, a])
    return w, h, rgba

def save_crop(x1, y1, x2, y2, filename, transparent_bg=None, threshold=20):
    w, h, rgba = crop_rgba(x1, y1, x2, y2, transparent_bg, threshold)
    write_png(w, h, rgba, filename)
    print(f"Extracted {filename} ({w}x{h})")

print("--- 1. Extracting Full Modern Logos (Top Row) ---")
# Light horizontal logo content: [48, 30] to [492, 133]
save_crop(48, 30, 492, 133, f"{TARGET_DIR}/rupeecrm-logo-forest-green-light.png")
save_crop(48, 30, 492, 133, f"{TARGET_DIR}/rupeecrm-logo-forest-green-light-transparent.png", transparent_bg=(248, 247, 243), threshold=15)
save_crop(9, 14, 509, 152, f"{TARGET_DIR}/rupeecrm-logo-forest-green-light-card.png")

# Dark horizontal logo content: [550, 30] to [992, 133]
save_crop(550, 30, 992, 133, f"{TARGET_DIR}/rupeecrm-logo-forest-green-dark.png")
save_crop(550, 30, 992, 133, f"{TARGET_DIR}/rupeecrm-logo-forest-green-dark-transparent.png", transparent_bg=(7, 38, 22), threshold=22)
save_crop(521, 14, 1014, 152, f"{TARGET_DIR}/rupeecrm-logo-forest-green-dark-card.png")

print("\n--- 2. Extracting Middle Row Theme Logos ---")
card_spans = [
    (6, 169, 'forest-green'),
    (175, 340, 'royal-navy'),
    (347, 509, 'petrol-teal'),
    (514, 678, 'royal-plum'),
    (686, 847, 'burgundy'),
    (854, 1021, 'burnished-amber')
]

for x1, x2, name in card_spans:
    # Logo crop (Symbol + Wordmark): y from 178 to 266
    save_crop(x1, 178, x2, 266, f"{TARGET_DIR}/rupeecrm-logo-{name}.png")
    # Transparent logo:
    save_crop(x1, 178, x2, 266, f"{TARGET_DIR}/rupeecrm-logo-{name}-transparent.png", transparent_bg=(251, 248, 243), threshold=16)
    # Full card crop (with theme pill badge): y from 173 to 307
    save_crop(x1, 173, x2, 307, f"{TARGET_DIR}/rupeecrm-logo-{name}-card.png")

print("\n--- 3. Extracting Windows 98 Retro Logo (Bottom-Right) ---")
# Retro logo content: [585, 360] to [995, 490]
save_crop(585, 360, 995, 490, f"{TARGET_DIR}/rupeecrm-retro-logo.png")
save_crop(585, 360, 995, 490, f"{TARGET_DIR}/rupeecrm-retro-logo-transparent.png", transparent_bg=(251, 251, 251), threshold=12)

print("\n--- 4. Extracting Application Icon (Bottom-Left Forest Green Squircle) ---")
# 101x101 squircle crop: [153, 379] to [253, 479]
# To make corners transparent without touching interior:
w = 253 - 153 + 1
h = 479 - 379 + 1
raw_pixels = []
for y in range(379, 480):
    row = []
    for x in range(153, 254):
        r, g, b = get_pixel(x, y)
        row.append([r, g, b, 255])
    raw_pixels.append(row)

# Flood fill from 4 corners
from collections import deque
visited = set()
queue = deque([(0, 0), (w-1, 0), (0, h-1), (w-1, h-1)])
for pt in queue:
    visited.add(pt)

while queue:
    x, y = queue.popleft()
    r, g, b, a = raw_pixels[y][x]
    # Outside background is light gray/white (r>180, g>180, b>180)
    if r > 180 and g > 180 and b > 180:
        raw_pixels[y][x][3] = 0 # set alpha to 0
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in visited:
                visited.add((nx, ny))
                queue.append((nx, ny))

icon_rgba = bytearray()
for y in range(h):
    for x in range(w):
        icon_rgba.extend(raw_pixels[y][x])

write_png(w, h, icon_rgba, f"{TARGET_DIR}/rupeecrm-app-icon-101.png")
write_png(w, h, icon_rgba, f"{TARGET_DIR}/rupeecrm-app-icon.png")
print(f"Extracted {TARGET_DIR}/rupeecrm-app-icon.png (101x101 with transparent corners)")

