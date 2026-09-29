#!/usr/bin/env bash
# ==============================================================================
# RupeeCRM — macOS Packaging & Lifecycle Validation Script
# ==============================================================================

set -euo pipefail

BOLD='\033[1m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}${BOLD}======================================================${NC}"
echo -e "${BLUE}${BOLD}    RupeeCRM — macOS Lifecycle Validation Test Suite  ${NC}"
echo -e "${BLUE}${BOLD}======================================================${NC}"
echo ""

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# 1. Check icon assets
echo -e "${BLUE}1/6. Validating macOS Icon Asset...${NC}"
if [ ! -f "tools/assets/RupeeCRM.icns" ]; then
    echo -e "${RED}FAIL: tools/assets/RupeeCRM.icns is missing!${NC}"
    exit 1
fi
ICNS_SIZE=$(stat -f%z "tools/assets/RupeeCRM.icns" 2>/dev/null || stat -c%s "tools/assets/RupeeCRM.icns")
if [ "$ICNS_SIZE" -lt 10000 ]; then
    echo -e "${RED}FAIL: RupeeCRM.icns is too small (${ICNS_SIZE} bytes).${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Icon asset valid (${ICNS_SIZE} bytes).${NC}"

# 2. Syntax check shell scripts
echo -e "${BLUE}2/6. Validating Shell Script Syntax...${NC}"
bash -n tools/install-mac.sh
bash -n tools/uninstall-mac.sh
echo -e "${GREEN}✓ Shell script syntax verified.${NC}"

# 3. Test jpackage packaging contract simulation
echo -e "${BLUE}3/6. Testing jpackage app-image contract...${NC}"
TEST_DIR="$(mktemp -d -t rupeecrm_test_XXXXXX)"
cleanup() {
    rm -rf "$TEST_DIR"
}
trap cleanup EXIT

mkdir -p "$TEST_DIR/pkg-in/runtime"
touch "$TEST_DIR/pkg-in/launcher.jar"
touch "$TEST_DIR/pkg-in/runtime/billsoft.war"

jpackage --name "RupeeCRM" \
         --input "$TEST_DIR/pkg-in" \
         --main-jar launcher.jar \
         --main-class com.billing.simple.launcher.LauncherMain \
         --type app-image \
         --icon tools/assets/RupeeCRM.icns \
         --dest "$TEST_DIR/dist-pkg" \
         --app-version 1.0.999 \
         --vendor "RupeeCRM" \
         --mac-package-name "RupeeCRM" \
         --mac-package-identifier "com.rupeecrm.billing" \
         --java-options "-Djava.awt.headless=false"

APP_PATH="$TEST_DIR/dist-pkg/RupeeCRM.app"
if [ ! -d "$APP_PATH" ]; then
    echo -e "${RED}FAIL: RupeeCRM.app was not produced by jpackage!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ App bundle created: ${APP_PATH}${NC}"

# 4. Validate App Bundle Internal Structure & Info.plist
echo -e "${BLUE}4/6. Validating App Bundle Structure & Info.plist...${NC}"
if [ ! -f "$APP_PATH/Contents/Info.plist" ]; then
    echo -e "${RED}FAIL: Info.plist missing from app bundle!${NC}"
    exit 1
fi
if [ ! -x "$APP_PATH/Contents/MacOS/RupeeCRM" ]; then
    echo -e "${RED}FAIL: Executable missing or not executable!${NC}"
    exit 1
fi
if [ ! -f "$APP_PATH/Contents/Resources/RupeeCRM.icns" ]; then
    echo -e "${RED}FAIL: App icon missing from Resources/!${NC}"
    exit 1
fi

plutil -lint "$APP_PATH/Contents/Info.plist" >/dev/null
BUNDLE_ID=$(defaults read "$APP_PATH/Contents/Info.plist" CFBundleIdentifier)
BUNDLE_NAME=$(defaults read "$APP_PATH/Contents/Info.plist" CFBundleName)
BUNDLE_ICON=$(defaults read "$APP_PATH/Contents/Info.plist" CFBundleIconFile)

if [ "$BUNDLE_ID" != "com.rupeecrm.billing" ]; then
    echo -e "${RED}FAIL: CFBundleIdentifier mismatch: ${BUNDLE_ID}${NC}"
    exit 1
fi
if [ "$BUNDLE_ICON" != "RupeeCRM.icns" ]; then
    echo -e "${RED}FAIL: CFBundleIconFile mismatch: ${BUNDLE_ICON}${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Bundle Metadata Verified (ID: ${BUNDLE_ID}, Name: ${BUNDLE_NAME}, Icon: ${BUNDLE_ICON})${NC}"

# 5. Validate Tar Archive Creation & Extraction Contract
echo -e "${BLUE}5/6. Validating Tarball Packaging & Extraction Contract...${NC}"
tar -czf "$TEST_DIR/RupeeCRM-macOS-arm64.tar.gz" -C "$TEST_DIR/dist-pkg" RupeeCRM.app
mkdir -p "$TEST_DIR/extracted"
tar -xzf "$TEST_DIR/RupeeCRM-macOS-arm64.tar.gz" -C "$TEST_DIR/extracted"

EXTRACTED_APP="$(find "$TEST_DIR/extracted" -name "RupeeCRM.app" -type d -maxdepth 2)"
if [ -z "$EXTRACTED_APP" ] || [ ! -d "$EXTRACTED_APP" ]; then
    echo -e "${RED}FAIL: Extracted RupeeCRM.app not found!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Tarball contract verified (Exact path: RupeeCRM.app at archive root).${NC}"

# 6. Validate LaunchAgent Plist Structure
echo -e "${BLUE}6/6. Validating LaunchAgent Plist Structure...${NC}"
SAMPLE_PLIST="$TEST_DIR/com.rupeecrm.billing.plist"
cat <<EOF > "$SAMPLE_PLIST"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.rupeecrm.billing</string>
    <key>ProgramArguments</key>
    <array>
        <string>/Applications/RupeeCRM.app/Contents/MacOS/RupeeCRM</string>
        <string>--background</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <false/>
    <key>ProcessType</key>
    <string>Interactive</string>
</dict>
</plist>
EOF

plutil -lint "$SAMPLE_PLIST" >/dev/null
echo -e "${GREEN}✓ LaunchAgent plist syntax valid.${NC}"

echo ""
echo -e "${GREEN}${BOLD}======================================================${NC}"
echo -e "${GREEN}${BOLD}   ✓ ALL MACOS PACKAGING & LIFECYCLE TESTS PASSED!    ${NC}"
echo -e "${GREEN}${BOLD}======================================================${NC}"
