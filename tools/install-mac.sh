#!/usr/bin/env bash
# ==============================================================================
# RupeeCRM macOS 1-Line Quick Installer
# ==============================================================================
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/main/tools/install-mac.sh | bash
#
# This installer:
# 1. Downloads the latest native RupeeCRM.app bundle directly via curl.
# 2. Installs RupeeCRM into /Applications.
# 3. Clears Gatekeeper quarantine attributes to ensure instant, warning-free launches.
# 4. Launches RupeeCRM automatically in the background.
# ==============================================================================

set -euo pipefail

BOLD='\033[1m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}${BOLD}======================================================${NC}"
echo -e "${BLUE}${BOLD}        RupeeCRM — macOS Native Quick Installer       ${NC}"
echo -e "${BLUE}${BOLD}======================================================${NC}"
echo ""

# 1. OS Check
OS="$(uname -s)"
if [ "$OS" != "Darwin" ]; then
    echo -e "${RED}Error: This installer is intended for macOS only.${NC}"
    exit 1
fi

# 2. Architecture Check
ARCH="$(uname -m)"
echo -e "${GREEN}✓${NC} Detected architecture: ${BOLD}${ARCH}${NC}"

REPO="RanjeetYelave/Simple-Billing"
API_URL="https://api.github.com/repos/${REPO}/releases/latest"

echo -e "${BLUE}ℹ${NC} Checking latest release from GitHub..."
RELEASE_JSON="$(curl -fsSL -H "User-Agent: RupeeCRM-Installer" "${API_URL}" || true)"

if [ -z "$RELEASE_JSON" ]; then
    echo -e "${RED}Error: Could not retrieve release information from GitHub.${NC}"
    exit 1
fi

TAG_NAME="$(echo "$RELEASE_JSON" | grep '"tag_name":' | head -n 1 | sed -E 's/.*"tag_name": *"([^"]+)".*/\1/')"
DOWNLOAD_URL="$(echo "$RELEASE_JSON" | grep '"browser_download_url":' | grep 'RupeeCRM-macOS-arm64.tar.gz' | head -n 1 | sed -E 's/.*"browser_download_url": *"([^"]+)".*/\1/')"

if [ -z "$DOWNLOAD_URL" ]; then
    # Fallback to direct release asset URL
    DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${TAG_NAME}/RupeeCRM-macOS-arm64.tar.gz"
fi

echo -e "${GREEN}✓${NC} Latest release: ${BOLD}${TAG_NAME}${NC}"
echo -e "${BLUE}ℹ${NC} Downloading ${BOLD}RupeeCRM-macOS-arm64.tar.gz${NC}..."

TMP_DIR="$(mktemp -d -t rupeecrm_install_XXXXXX)"
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

curl -fSL --progress-bar "$DOWNLOAD_URL" -o "${TMP_DIR}/RupeeCRM-macOS-arm64.tar.gz"

echo -e "${BLUE}ℹ${NC} Extracting application bundle..."
tar -xzf "${TMP_DIR}/RupeeCRM-macOS-arm64.tar.gz" -C "$TMP_DIR"

if [ ! -d "${TMP_DIR}/RupeeCRM.app" ]; then
    echo -e "${RED}Error: RupeeCRM.app not found in download archive.${NC}"
    exit 1
fi

TARGET_APP="/Applications/RupeeCRM.app"

# Stop any running instances
if pgrep -f "RupeeCRM" > /dev/null 2>&1; then
    echo -e "${YELLOW}ℹ Stopping existing RupeeCRM service...${NC}"
    pkill -f "RupeeCRM" || true
    sleep 1
fi

echo -e "${BLUE}ℹ${NC} Installing to ${BOLD}${TARGET_APP}${NC}..."
rm -rf "$TARGET_APP"
cp -R "${TMP_DIR}/RupeeCRM.app" /Applications/

echo -e "${BLUE}ℹ${NC} Clearing Gatekeeper quarantine attributes..."
xattr -cr "$TARGET_APP" 2>/dev/null || true

echo -e "${GREEN}✓${NC} Installation complete!"
echo ""
echo -e "${BLUE}ℹ${NC} Starting RupeeCRM..."
open "$TARGET_APP"

echo ""
echo -e "${GREEN}${BOLD}======================================================${NC}"
echo -e "${GREEN}${BOLD}   RupeeCRM is now active and running in background!  ${NC}"
echo -e "${GREEN}${BOLD}   Open in Browser: http://localhost:8080/            ${NC}"
echo -e "${GREEN}${BOLD}======================================================${NC}"
echo ""
echo -e "You can launch RupeeCRM anytime from ${BOLD}Spotlight${NC} (Cmd + Space $\to$ RupeeCRM) or ${BOLD}/Applications${NC}."
