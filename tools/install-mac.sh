#!/usr/bin/env bash
# ==============================================================================
# RupeeCRM — macOS Native Quick Installer & Updater
# ==============================================================================
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/OTA_update/tools/install-mac.sh | bash
#
# This installer:
# 1. Detects native macOS architecture (Apple Silicon arm64 vs Intel x86_64).
# 2. Resolves and downloads the latest official RupeeCRM release package.
# 3. Validates archive integrity and extracts RupeeCRM.app bundle.
# 4. Installs RupeeCRM cleanly into /Applications.
# 5. Clears Gatekeeper quarantine attributes for smooth native execution.
# 6. Configures auto-start on macOS user login via standard LaunchAgent & Login Item.
# 7. Automatically launches RupeeCRM and verifies service health.
# ==============================================================================

set -euo pipefail

BOLD='\033[1m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}${BOLD}======================================================${NC}"
echo -e "${BLUE}${BOLD}        RupeeCRM — macOS Native Quick Installer       ${NC}"
echo -e "${BLUE}${BOLD}======================================================${NC}"
echo ""

# 1. Operating System Validation
OS="$(uname -s)"
if [ "$OS" != "Darwin" ]; then
    echo -e "${RED}Error: This installer is intended for macOS only.${NC}"
    exit 1
fi

# 2. Architecture Detection
RAW_ARCH="$(uname -m)"
case "$RAW_ARCH" in
    arm64|aarch64)
        PKG_ARCH="arm64"
        ARCH_DESC="Apple Silicon (ARM64)"
        ;;
    x86_64|amd64|i386)
        PKG_ARCH="x64"
        ARCH_DESC="Intel (x86_64)"
        ;;
    *)
        echo -e "${YELLOW}Warning: Unknown architecture '${RAW_ARCH}'. Defaulting to arm64.${NC}"
        PKG_ARCH="arm64"
        ARCH_DESC="Generic (${RAW_ARCH})"
        ;;
esac

echo -e "${GREEN}✓${NC} Detected architecture: ${BOLD}${ARCH_DESC}${NC}"

# 3. Release Discovery & Asset URL Resolution
REPO="RanjeetYelave/Simple-Billing"
API_URL="https://api.github.com/repos/${REPO}/releases/latest"

echo -e "${BLUE}ℹ${NC} Checking latest release from GitHub..."

TAG_NAME=""
DOWNLOAD_URL=""

# Strategy A: GitHub REST API
RELEASE_JSON="$(curl -fsSL -H "User-Agent: RupeeCRM-Installer/1.0" -H "Accept: application/vnd.github.v3+json" "${API_URL}" 2>/dev/null || true)"

if [ -n "$RELEASE_JSON" ] && echo "$RELEASE_JSON" | grep -q '"tag_name":'; then
    TAG_NAME="$(echo "$RELEASE_JSON" | grep '"tag_name":' | head -n 1 | sed -E 's/.*"tag_name": *"([^"]+)".*/\1/')"
    
    # Try exact architecture match
    DOWNLOAD_URL="$(echo "$RELEASE_JSON" | grep '"browser_download_url":' | grep "RupeeCRM-macOS-${PKG_ARCH}.tar.gz" | head -n 1 | sed -E 's/.*"browser_download_url": *"([^"]+)".*/\1/' || true)"
    
    # Fallback to arm64 if x64 is not published on older release
    if [ -z "$DOWNLOAD_URL" ]; then
        DOWNLOAD_URL="$(echo "$RELEASE_JSON" | grep '"browser_download_url":' | grep 'RupeeCRM-macOS-arm64.tar.gz' | head -n 1 | sed -E 's/.*"browser_download_url": *"([^"]+)".*/\1/' || true)"
    fi
fi

# Strategy B: Direct Redirect Query (Rate-limit immune)
if [ -z "$TAG_NAME" ] || [ -z "$DOWNLOAD_URL" ]; then
    LATEST_REDIRECT="$(curl -sIL -o /dev/null -w "%{url_effective}" "https://github.com/${REPO}/releases/latest" 2>/dev/null || true)"
    if echo "$LATEST_REDIRECT" | grep -q '/releases/tag/'; then
        TAG_NAME="$(echo "$LATEST_REDIRECT" | sed -E 's|.*/releases/tag/||' | tr -d '[:space:]')"
    fi
    if [ -z "$TAG_NAME" ]; then
        TAG_NAME="latest"
    fi
    DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${TAG_NAME}/RupeeCRM-macOS-${PKG_ARCH}.tar.gz"
fi

echo -e "${GREEN}✓${NC} Latest release: ${BOLD}${TAG_NAME}${NC}"
echo -e "${BLUE}ℹ${NC} Target package: ${BOLD}RupeeCRM-macOS-${PKG_ARCH}.tar.gz${NC}"

# 4. Secure Temporary Workspace
TMP_DIR="$(mktemp -d -t rupeecrm_install_XXXXXX)"
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT INT TERM

ARCHIVE_FILE="${TMP_DIR}/RupeeCRM-macOS-${PKG_ARCH}.tar.gz"
EXTRACT_DIR="${TMP_DIR}/extracted"
mkdir -p "$EXTRACT_DIR"

# 5. Download Release Asset
echo -e "${BLUE}ℹ${NC} Downloading release package..."

DOWNLOAD_SUCCESS=0
if curl -fSL --progress-bar "$DOWNLOAD_URL" -o "$ARCHIVE_FILE" 2>/dev/null; then
    DOWNLOAD_SUCCESS=1
elif [ "$PKG_ARCH" != "arm64" ]; then
    # Fallback to ARM64 asset if x64 asset was not found on this specific release
    FALLBACK_URL="https://github.com/${REPO}/releases/download/${TAG_NAME}/RupeeCRM-macOS-arm64.tar.gz"
    echo -e "${YELLOW}ℹ ${PKG_ARCH} build not found for ${TAG_NAME}, trying universal/arm64 fallback...${NC}"
    if curl -fSL --progress-bar "$FALLBACK_URL" -o "$ARCHIVE_FILE" 2>/dev/null; then
        DOWNLOAD_SUCCESS=1
    fi
fi

if [ $DOWNLOAD_SUCCESS -ne 1 ] || [ ! -s "$ARCHIVE_FILE" ]; then
    echo -e "${RED}Error: Failed to download release package from GitHub.${NC}"
    echo -e "URL attempted: ${DOWNLOAD_URL}"
    exit 1
fi

# 6. Verify Archive Structure & Extract
echo -e "${BLUE}ℹ${NC} Validating and extracting application bundle..."

if ! tar -tzf "$ARCHIVE_FILE" >/dev/null 2>&1; then
    echo -e "${RED}Error: Downloaded file is not a valid gzip tar archive.${NC}"
    exit 1
fi

tar -xzf "$ARCHIVE_FILE" -C "$EXTRACT_DIR"

FOUND_APP="$(find "$EXTRACT_DIR" -name "RupeeCRM.app" -type d -maxdepth 3 | head -n 1)"
if [ -z "$FOUND_APP" ]; then
    FOUND_APP="$(find "$EXTRACT_DIR" -name "*.app" -type d -maxdepth 3 | head -n 1)"
fi

if [ -z "$FOUND_APP" ] || [ ! -d "$FOUND_APP" ]; then
    echo -e "${RED}Error: RupeeCRM.app bundle was not found inside the download archive.${NC}"
    echo -e "Archive contents:"
    ls -la "$EXTRACT_DIR" || true
    exit 1
fi

# Verify essential macOS bundle metadata
if [ ! -f "${FOUND_APP}/Contents/Info.plist" ]; then
    echo -e "${RED}Error: Malformed application bundle (missing Contents/Info.plist).${NC}"
    exit 1
fi

# 7. Safe Instance Shutdown for Clean Upgrade
if pgrep -f "RupeeCRM" > /dev/null 2>&1 || pgrep -f "LauncherMain" > /dev/null 2>&1; then
    echo -e "${YELLOW}ℹ Stopping running RupeeCRM instance for clean upgrade...${NC}"
    pkill -f "RupeeCRM" 2>/dev/null || true
    pkill -f "LauncherMain" 2>/dev/null || true
    sleep 1
fi

# 8. Install to /Applications
TARGET_APP="/Applications/RupeeCRM.app"
echo -e "${BLUE}ℹ${NC} Installing application to ${BOLD}${TARGET_APP}${NC}..."

if [ -w "/Applications" ]; then
    rm -rf "$TARGET_APP"
    cp -R "$FOUND_APP" "$TARGET_APP"
else
    echo -e "${YELLOW}ℹ Elevated permissions required to write to /Applications...${NC}"
    sudo rm -rf "$TARGET_APP"
    sudo cp -R "$FOUND_APP" "$TARGET_APP"
fi

# 9. Gatekeeper & Permissions Normalization
echo -e "${BLUE}ℹ${NC} Normalizing macOS security & bundle attributes..."
xattr -dr com.apple.quarantine "$TARGET_APP" 2>/dev/null || true
xattr -cr "$TARGET_APP" 2>/dev/null || true
chmod -R u+w "$TARGET_APP" 2>/dev/null || true
chmod +x "$TARGET_APP"/Contents/MacOS/* 2>/dev/null || true

# 10. Persistent Auto-Start Configuration (LaunchAgent & Login Item)
echo -e "${BLUE}ℹ${NC} Configuring automatic background startup on login..."

LAUNCH_AGENT_DIR="${HOME}/Library/LaunchAgents"
mkdir -p "$LAUNCH_AGENT_DIR"
PLIST_FILE="${LAUNCH_AGENT_DIR}/com.rupeecrm.billing.plist"

cat <<EOF > "$PLIST_FILE"
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
    <key>StandardOutPath</key>
    <string>${HOME}/Library/Application Support/RupeeCRM/logs/launchagent-stdout.log</string>
    <key>StandardErrorPath</key>
    <string>${HOME}/Library/Application Support/RupeeCRM/logs/launchagent-stderr.log</string>
</dict>
</plist>
EOF

# Ensure log directory exists
mkdir -p "${HOME}/Library/Application Support/RupeeCRM/logs"

# Refresh LaunchAgent registration
launchctl unload "$PLIST_FILE" 2>/dev/null || true
launchctl load -w "$PLIST_FILE" 2>/dev/null || true

# Also sync AppleScript Login Item for native macOS Settings integration
osascript -e 'tell application "System Events" to delete (every login item whose name is "RupeeCRM")' 2>/dev/null || true
osascript -e 'tell application "System Events" to make login item at end with properties {path:"/Applications/RupeeCRM.app", hidden:true, name:"RupeeCRM"}' 2>/dev/null || true

# 11. Local Hostname Mapping (/etc/hosts)
if ! grep -q "management.rupeecrm.local" /etc/hosts 2>/dev/null; then
    echo -e "${BLUE}ℹ${NC} Configuring canonical hostname 'management.rupeecrm.local'..."
    if [ -w /etc/hosts ]; then
        echo "127.0.0.1 management.rupeecrm.local" >> /etc/hosts
    else
        sudo sh -c 'echo "127.0.0.1 management.rupeecrm.local" >> /etc/hosts' 2>/dev/null || true
    fi
fi

# 12. Application Launch & Health Check
echo -e "${BLUE}ℹ${NC} Launching RupeeCRM..."
open -a "$TARGET_APP"

echo -e "${BLUE}ℹ${NC} Waiting for service initialization..."
HEALTH_URL="http://127.0.0.1:28080/api/health"
MAX_WAIT=20
HEALTHY=0

for i in $(seq 1 $MAX_WAIT); do
    if curl -s -f "$HEALTH_URL" >/dev/null 2>&1 || curl -s -f "http://127.0.0.1:28080/index.html" >/dev/null 2>&1; then
        HEALTHY=1
        break
    fi
    sleep 1
done

echo ""
echo -e "${GREEN}${BOLD}======================================================${NC}"
if [ $HEALTHY -eq 1 ]; then
    echo -e "${GREEN}${BOLD}   ✓ RupeeCRM Installed & Running Successfully!       ${NC}"
else
    echo -e "${GREEN}${BOLD}   ✓ RupeeCRM Installation Completed!                 ${NC}"
    echo -e "${YELLOW}   (Application is starting up in the background)     ${NC}"
fi
echo -e "${GREEN}${BOLD}======================================================${NC}"
echo ""
echo -e "• Web Dashboard:     ${CYAN}${BOLD}http://management.rupeecrm.local:28080/${NC}"
echo -e "• Direct IP Access:  ${CYAN}${BOLD}http://127.0.0.1:28080/${NC}"
echo -e "• Installed Path:    ${BOLD}/Applications/RupeeCRM.app${NC}"
echo -e "• Data Directory:    ${BOLD}~/Library/Application Support/RupeeCRM${NC}"
echo -e "• Login Auto-Start:  ${GREEN}Enabled${NC} (LaunchAgent + Login Item)"
echo ""
echo -e "You can access RupeeCRM anytime via ${BOLD}Spotlight${NC} (Cmd+Space → 'RupeeCRM'), ${BOLD}Launchpad${NC}, or ${BOLD}Finder → Applications${NC}."
