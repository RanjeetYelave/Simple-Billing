#!/usr/bin/env bash
# ==============================================================================
# RupeeCRM — macOS Native Clean Uninstaller
# ==============================================================================
# Usage:
#   bash tools/uninstall-mac.sh [--purge-data]
# ==============================================================================

set -euo pipefail

BOLD='\033[1m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}${BOLD}======================================================${NC}"
echo -e "${BLUE}${BOLD}        RupeeCRM — macOS Native Uninstaller          ${NC}"
echo -e "${BLUE}${BOLD}======================================================${NC}"
echo ""

PURGE_DATA=0
for arg in "$@"; do
    if [ "$arg" == "--purge-data" ] || [ "$arg" == "-p" ]; then
        PURGE_DATA=1
    fi
done

# 1. Stop background processes
echo -e "${BLUE}ℹ${NC} Stopping running RupeeCRM service..."
pkill -f "RupeeCRM" 2>/dev/null || true
pkill -f "LauncherMain" 2>/dev/null || true
sleep 1

# 2. Remove LaunchAgent
PLIST_FILE="${HOME}/Library/LaunchAgents/com.rupeecrm.billing.plist"
if [ -f "$PLIST_FILE" ]; then
    echo -e "${BLUE}ℹ${NC} Removing LaunchAgent auto-start service..."
    launchctl unload "$PLIST_FILE" 2>/dev/null || true
    rm -f "$PLIST_FILE"
fi

# 3. Remove Login Item
echo -e "${BLUE}ℹ${NC} Removing macOS Login Item registration..."
osascript -e 'tell application "System Events" to delete (every login item whose name is "RupeeCRM")' 2>/dev/null || true

# 4. Remove /Applications/RupeeCRM.app
TARGET_APP="/Applications/RupeeCRM.app"
if [ -d "$TARGET_APP" ]; then
    echo -e "${BLUE}ℹ${NC} Removing ${BOLD}${TARGET_APP}${NC}..."
    if [ -w "/Applications" ]; then
        rm -rf "$TARGET_APP"
    else
        sudo rm -rf "$TARGET_APP"
    fi
fi

# 5. Handle Persistent Data
DATA_DIR="${HOME}/Library/Application Support/RupeeCRM"
if [ $PURGE_DATA -eq 1 ]; then
    echo -e "${YELLOW}ℹ Purging user database and configuration at ${DATA_DIR}...${NC}"
    rm -rf "$DATA_DIR"
else
    echo -e "${GREEN}ℹ${NC} User database preserved at: ${BOLD}${DATA_DIR}${NC}"
    echo -e "  (To completely remove all data, pass --purge-data flag or delete that folder)"
fi

echo ""
echo -e "${GREEN}${BOLD}✓ RupeeCRM has been successfully uninstalled from your Mac.${NC}"
