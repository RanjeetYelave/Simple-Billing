import os
import subprocess
import struct

SRC_ICON = 'billsoft/src/main/webapp/assets/branding/rupeecrm-app-icon.png'
TARGET_DIR = 'billsoft/src/main/webapp/assets/branding'

# Create technical sizes using sips high-quality resampling
sizes = [16, 32, 48, 64, 128, 192, 256, 512]
temp_pngs = {}

for s in sizes:
    out_p = f"{TARGET_DIR}/temp_icon_{s}.png"
    subprocess.run(['sips', '-z', str(s), str(s), SRC_ICON, '--out', out_p], check=True, stdout=subprocess.DEVNULL)
    temp_pngs[s] = out_p

# Copy 512 to desktop/electron/assets/icon.png and webapp
subprocess.run(['cp', temp_pngs[512], 'desktop/electron/assets/icon.png'], check=True)
subprocess.run(['cp', temp_pngs[512], 'billsoft/src/main/webapp/icons/icon-512.png'], check=True)
subprocess.run(['cp', temp_pngs[512], 'billsoft/src/main/webapp/icons/icon-maskable-512.png'], check=True)
subprocess.run(['cp', temp_pngs[192], 'billsoft/src/main/webapp/icons/icon-192.png'], check=True)
subprocess.run(['cp', temp_pngs[192], 'billsoft/src/main/webapp/icons/icon-maskable-192.png'], check=True)

# Build multi-resolution ICO files
def create_ico(size_list, out_ico_path):
    ico_entries = []
    offset = 6 + 16 * len(size_list)
    png_datas = []

    for s in size_list:
        p = temp_pngs[s]
        with open(p, 'rb') as f:
            data = f.read()
        png_datas.append(data)
        b_w = 0 if s == 256 else s
        b_h = 0 if s == 256 else s
        entry = struct.pack('<BBBBHHII', b_w, b_h, 0, 0, 1, 32, len(data), offset)
        ico_entries.append(entry)
        offset += len(data)

    header = struct.pack('<HHH', 0, 1, len(size_list))
    with open(out_ico_path, 'wb') as f:
        f.write(header)
        for entry in ico_entries:
            f.write(entry)
        for data in png_datas:
            f.write(data)
    print(f"Generated ICO: {out_ico_path} (resolutions: {size_list})")

# 1. desktop/electron/assets/icon.ico (Windows application icon)
create_ico([16, 32, 48, 64, 128, 256], 'desktop/electron/assets/icon.ico')

# 2. tools/assets/RupeeCRM.ico (Installer and launcher icon)
create_ico([16, 32, 48, 64, 128, 256], 'tools/assets/RupeeCRM.ico')

# 3. billsoft/src/main/webapp/assets/branding/rupeecrm-app-icon.ico
create_ico([16, 32, 48, 64, 128, 256], f"{TARGET_DIR}/rupeecrm-app-icon.ico")

# 4. billsoft/src/main/webapp/favicon.ico
create_ico([16, 32, 48], 'billsoft/src/main/webapp/favicon.ico')

# Clean up temp pngs
for p in temp_pngs.values():
    if os.path.exists(p):
        os.remove(p)

print("\n--- ALL EXTRACTED TECHNICAL APPLICATION ICONS GENERATED SUCCESSFULLY ---")
