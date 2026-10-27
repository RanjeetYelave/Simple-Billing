#!/usr/bin/env bash
# ==============================================================================
# RupeeCRM / Simple-Billing — GitHub Actions Storage Cleanup Script
# Deletes old workflow runs and accumulated artifacts to free GitHub storage.
# ==============================================================================

set -eo pipefail

REPO="RanjeetYelave/Simple-Billing"
API_BASE="https://api.github.com/repos/${REPO}"

# 1. Resolve GitHub Token
TOKEN="${1:-${GITHUB_TOKEN:-${GH_TOKEN}}}"

if [ -z "$TOKEN" ]; then
  # Try fetching from macOS keychain / git credential helper
  TOKEN=$(printf "protocol=https\nhost=github.com\n" | git credential fill 2>/dev/null | grep '^password=' | cut -d= -f2- || true)
fi

if [ -z "$TOKEN" ]; then
  echo "❌ Error: No GitHub token provided."
  echo "Usage: $0 <YOUR_GITHUB_TOKEN>"
  echo "   or: export GITHUB_TOKEN=ghp_xxx && $0"
  exit 1
fi

echo "======================================================================"
echo "🧹 RupeeCRM — GitHub Actions Storage Cleanup"
echo "Repository: ${REPO}"
echo "======================================================================"

# 2. Test Token & Permissions
echo "🔍 Verifying token and checking permissions..."
TEST_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  "${API_BASE}/actions/runs?per_page=1")

HTTP_STATUS=$(echo "$TEST_RESP" | grep "HTTP_STATUS:" | cut -d: -f2)

if [ "$HTTP_STATUS" != "200" ]; then
  echo "❌ Error: Failed to query repository with provided token (HTTP $HTTP_STATUS)."
  echo "$TEST_RESP" | grep -v "HTTP_STATUS:"
  exit 1
fi

TOTAL_RUNS=$(curl -s -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/vnd.github+json" "${API_BASE}/actions/runs?per_page=1" | jq -r '.total_count // 0')
TOTAL_ARTIFACTS=$(curl -s -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/vnd.github+json" "${API_BASE}/actions/artifacts?per_page=1" | jq -r '.total_count // 0')

echo "📊 Found ${TOTAL_RUNS} workflow runs and ${TOTAL_ARTIFACTS} stored artifacts."

# 3. Test Delete Permission
echo "🔐 Testing delete permission on GitHub Actions..."
SAMPLE_RUN_ID=$(curl -s -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/vnd.github+json" "${API_BASE}/actions/runs?per_page=1" | jq -r '.workflow_runs[0].id // empty')

if [ -n "$SAMPLE_RUN_ID" ]; then
  DEL_TEST=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X DELETE \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    "${API_BASE}/actions/runs/${SAMPLE_RUN_ID}")
  
  DEL_STATUS=$(echo "$DEL_TEST" | grep "HTTP_STATUS:" | cut -d: -f2)
  
  if [ "$DEL_STATUS" = "403" ]; then
    echo ""
    echo "⚠️  PERMISSION ERROR (HTTP 403):"
    echo "The current token does not have 'Actions: Read and write' permission."
    echo ""
    echo "To grant permission:"
    echo "1. If using a Fine-Grained Personal Access Token (starts with github_pat_):"
    echo "   - Go to: https://github.com/settings/tokens"
    echo "   - Click on your token"
    echo "   - Under 'Repository permissions', set 'Actions' to 'Read and write'"
    echo "   - Click 'Save changes' at the bottom"
    echo ""
    echo "2. Or generate a Classic Personal Access Token with 'repo' scope:"
    echo "   - Go to: https://github.com/settings/tokens/new?scopes=repo,workflow"
    echo "   - Set Expiration (e.g. 7 days), check 'repo' and 'workflow', click 'Generate token'"
    echo "   - Re-run this script with the new token: $0 <NEW_TOKEN>"
    echo ""
    echo "Alternatively, you can trigger the GitHub Actions cleanup workflow directly from GitHub Actions UI!"
    exit 1
  elif [ "$DEL_STATUS" = "204" ]; then
    echo "✅ Delete permission verified (sample run #${SAMPLE_RUN_ID} deleted)."
  fi
fi

# 4. Delete Artifacts (Highest Storage Impact)
echo ""
echo "🚀 Step 1: Deleting Artifacts in parallel to immediately free disk quota..."
DELETED_ARTIFACTS=0

while true; do
  ARTIFACT_BATCH=$(curl -s -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/vnd.github+json" "${API_BASE}/actions/artifacts?per_page=100")
  IDS=$(echo "$ARTIFACT_BATCH" | jq -r '.artifacts[]?.id // empty')
  
  if [ -z "$IDS" ]; then
    echo "✓ All artifacts purged."
    break
  fi
  
  BATCH_COUNT=$(echo "$IDS" | wc -l | tr -d ' ')
  echo "Deleting batch of ${BATCH_COUNT} artifacts (parallel workers: 10)..."
  echo "$IDS" | xargs -n 1 -P 10 -I {} curl -s -o /dev/null -X DELETE \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    "${API_BASE}/actions/artifacts/{}"
  
  DELETED_ARTIFACTS=$((DELETED_ARTIFACTS + BATCH_COUNT))
  echo "✓ Total artifacts deleted so far: ${DELETED_ARTIFACTS}"
done
echo ""

# 5. Delete Workflow Runs
echo ""
echo "🚀 Step 2: Deleting Workflow Runs in parallel..."
DELETED_RUNS=0

while true; do
  RUN_BATCH=$(curl -s -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/vnd.github+json" "${API_BASE}/actions/runs?per_page=100")
  IDS=$(echo "$RUN_BATCH" | jq -r '.workflow_runs[]?.id // empty')
  
  if [ -z "$IDS" ]; then
    echo "✓ All workflow runs purged."
    break
  fi
  
  BATCH_COUNT=$(echo "$IDS" | wc -l | tr -d ' ')
  echo "Deleting batch of ${BATCH_COUNT} workflow runs (parallel workers: 10)..."
  echo "$IDS" | xargs -n 1 -P 10 -I {} curl -s -o /dev/null -X DELETE \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    "${API_BASE}/actions/runs/{}"
  
  DELETED_RUNS=$((DELETED_RUNS + BATCH_COUNT))
  echo "✓ Total workflow runs deleted so far: ${DELETED_RUNS}"
done
echo ""

echo "======================================================================"
echo "🎉 Cleanup Complete!"
echo "Total Artifacts Deleted: ${DELETED_ARTIFACTS}"
echo "Total Workflow Runs Deleted: ${DELETED_RUNS}"
echo "GitHub Actions storage quota has been reclaimed."
echo "======================================================================"
