import struct
import zlib
import os

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TARGET_DIR = os.path.join(REPO_ROOT, "billsoft", "src", "main", "webapp", "assets", "branding")
os.makedirs(TARGET_DIR, exist_ok=True)

with open('/tmp/sheet.bmp', 'rb') as f:
    bmp_data = f.read()

bfOffBits = struct.unpack('<I', bmp_data[10:14])[0]
src_w, raw_h = struct.unpack('<ii', bmp_data[18:26])
src_h = abs(raw_h)
is_bottom_up = (raw_h > 0)
stride = (src_w * 3 + 3) & ~3

def get_pixel(x, y):
    actual_y = (src_h - 1 - y) if is_bottom_up else y
    idx = bfOffBits + actual_y * stride + x * 3
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
    print(f"Wrote {filename} ({w}x{h})")

# Theme palettes: (c_dark, c_light) for light mode and (c_base, c_bright) for dark mode
THEMES_LIGHT = {
    'forest':   ((22, 132, 91),  (57, 185, 135)),
    'navy':     ((29, 78, 216),  (96, 165, 250)),
    'teal':     ((15, 118, 110), (45, 212, 191)),
    'plum':     ((107, 33, 168), (192, 132, 252)),
    'burgundy': ((136, 19, 55),  (251, 113, 133)),
    'amber':    ((180, 83, 9),   (245, 158, 11))
}

THEMES_DARK = {
    'forest':   ((57, 185, 135),  (74, 222, 128)),
    'navy':     ((96, 165, 250),  (147, 197, 253)),
    'teal':     ((45, 212, 191),  (94, 234, 212)),
    'plum':     ((192, 132, 252), (233, 213, 255)),
    'burgundy': ((251, 113, 133), (253, 164, 175)),
    'amber':    ((251, 191, 36),  (252, 211, 77))
}

# --- 1. LIGHT LOGO GENERATION ---
# Light master crop: x=48..492, y=30..133 (w=445, h=104)
# Content bounding box analysis:
# Background is cream: (248, 246, 242)
bg_r, bg_g, bg_b = 248, 246, 242
w_l = 492 - 48 + 1
h_l = 133 - 30 + 1

for theme_name, (c_dark, c_light) in THEMES_LIGHT.items():
    rgba = bytearray()
    for y in range(30, 134):
        for x in range(48, 493):
            r, g, b = get_pixel(x, y)
            dist = max(abs(r - bg_r), abs(g - bg_g), abs(b - bg_b))
            
            if dist < 14:
                # Transparent background
                rgba.extend([0, 0, 0, 0])
                continue

            # Compute smooth alpha for anti-aliased edge
            if dist < 32:
                a = int((dist - 14) / 18.0 * 255)
            else:
                a = 255

            rel_x = x - 48
            # Is this part of the theme accent (bars/arrow or CRM wordmark)?
            # Rupee symbol is x < 95 (dark)
            # Bars and arrow are 95 <= rel_x <= 165
            # Vertical divider is 165 < rel_x < 185 (dark neutral)
            # Rupee text is 185 <= rel_x < 325 (dark neutral)
            # CRM text is rel_x >= 325 and y < 85 (colored accent)
            # Tagline is y >= 85 and rel_x >= 185 (slate gray)
            
            is_accent = False
            if 95 <= rel_x <= 168 and (g > r + 8 or g > b + 5):
                is_accent = True
            elif rel_x >= 325 and y < 85 and (g > r + 5 or g > b + 5):
                is_accent = True

            if is_accent:
                # Interpolate between c_dark and c_light based on green luminosity
                # green channels range roughly 70 to 220
                t = max(0.0, min(1.0, (g - 70) / 140.0))
                pr = int(c_dark[0] * (1 - t) + c_light[0] * t)
                pg = int(c_dark[1] * (1 - t) + c_light[1] * t)
                pb = int(c_dark[2] * (1 - t) + c_light[2] * t)
                rgba.extend([pr, pg, pb, a])
            else:
                # Dark neutral or tagline: keep clean anti-aliased color
                # If it's anti-aliasing against cream background, normalize it towards dark neutral or slate
                if rel_x < 325 and y < 85:
                    # Dark neutral (₹, Rupee, divider)
                    # Clean dark neutral: #0F172A
                    rgba.extend([15, 23, 42, a])
                else:
                    # Tagline: #64748B
                    rgba.extend([100, 116, 139, a])

    fn_base = f"{TARGET_DIR}/rupeecrm-logo-{theme_name}-light.png"
    write_png(w_l, h_l, rgba, fn_base)
    # Also write with transparent suffix for backward-compatibility
    fn_trans = f"{TARGET_DIR}/rupeecrm-logo-{theme_name}-light-transparent.png"
    write_png(w_l, h_l, rgba, fn_trans)

# --- 2. DARK LOGO GENERATION ---
# Dark master crop: x=550..992, y=30..133 (w=443, h=104)
# Background is dark green: (7, 37, 20)
dbg_r, dbg_g, dbg_b = 7, 37, 20
w_d = 992 - 550 + 1
h_d = 133 - 30 + 1

for theme_name, (c_dark, c_light) in THEMES_DARK.items():
    rgba = bytearray()
    for y in range(30, 134):
        for x in range(550, 993):
            r, g, b = get_pixel(x, y)
            dist = max(abs(r - dbg_r), abs(g - dbg_g), abs(b - dbg_b))
            
            if dist < 18:
                # Transparent background
                rgba.extend([0, 0, 0, 0])
                continue

            # Compute smooth alpha for anti-aliased edge
            if dist < 38:
                a = int((dist - 18) / 20.0 * 255)
            else:
                a = 255

            rel_x = x - 550
            # Rupee symbol is rel_x < 95 (white)
            # Bars and arrow are 95 <= rel_x <= 168 (accent)
            # Divider is 168 < rel_x < 185 (white/subtle)
            # Rupee text is 185 <= rel_x < 325 (white)
            # CRM text is rel_x >= 325 and y < 85 (accent)
            # Tagline is y >= 85 and rel_x >= 185 (light slate)

            is_accent = False
            if 95 <= rel_x <= 168 and (g > r + 15 or (g > 60 and r < 160)):
                is_accent = True
            elif rel_x >= 325 and y < 85 and (g > r + 15 or (g > 60 and r < 160)):
                is_accent = True

            if is_accent:
                t = max(0.0, min(1.0, (g - 80) / 140.0))
                pr = int(c_dark[0] * (1 - t) + c_light[0] * t)
                pg = int(c_dark[1] * (1 - t) + c_light[1] * t)
                pb = int(c_dark[2] * (1 - t) + c_light[2] * t)
                rgba.extend([pr, pg, pb, a])
            else:
                if rel_x < 325 and y < 85:
                    # Crisp white for Rupee and divider
                    rgba.extend([255, 255, 255, a])
                else:
                    # Tagline: light slate #94A3B8
                    rgba.extend([148, 163, 184, a])

    fn_base = f"{TARGET_DIR}/rupeecrm-logo-{theme_name}-dark.png"
    write_png(w_d, h_d, rgba, fn_base)
    fn_trans = f"{TARGET_DIR}/rupeecrm-logo-{theme_name}-dark-transparent.png"
    write_png(w_d, h_d, rgba, fn_trans)

# Also generate aliases for theme keys (forest-green -> forest, royal-navy -> navy, etc.)
ALIASES = {
    'forest-green': 'forest',
    'royal-navy': 'navy',
    'petrol-teal': 'teal',
    'royal-plum': 'plum',
    'burnished-amber': 'amber'
}

for long_name, short_name in ALIASES.items():
    for mode in ['light', 'dark']:
        for suff in ['', '-transparent']:
            src = f"{TARGET_DIR}/rupeecrm-logo-{short_name}-{mode}{suff}.png"
            dst = f"{TARGET_DIR}/rupeecrm-logo-{long_name}-{mode}{suff}.png"
            with open(src, 'rb') as f_in, open(dst, 'wb') as f_out:
                f_out.write(f_in.read())
            print(f"Copied alias: {dst}")

print("All horizontal logos generated successfully!")
