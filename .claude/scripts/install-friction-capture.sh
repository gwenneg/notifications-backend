#!/bin/bash
# Installs or updates the friction capture toolkit to the latest release.
# Exit codes: 0 = success, 1 = error, 2 = success and Claude restart required
set -euo pipefail

REPO="gwenneg/blog-friction-driven-feedback-loop-for-ai-context-docs"
BASE_URL="https://raw.githubusercontent.com/${REPO}"
VERSION_FILE=".claude/.friction-capture-version"
SKILL_FILE=".claude/skills/update-context-docs/SKILL.md"

TAG=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" | jq -r '.tag_name')

if [[ -z "$TAG" || "$TAG" == "null" ]]; then
  echo "Error: could not fetch latest release tag." >&2
  exit 1
fi

skill_before=$(sha256sum "$SKILL_FILE" 2>/dev/null | awk '{print $1}' || echo "")

curl -fsSL "${BASE_URL}/${TAG}/skills/update-context-docs/SKILL.md" \
  -o "$SKILL_FILE"

curl -fsSL "${BASE_URL}/${TAG}/skills/setup-friction-capture/scripts/friction-hook.sh" \
  -o .claude/scripts/friction-hook.sh
chmod +x .claude/scripts/friction-hook.sh

curl -fsSL "${BASE_URL}/${TAG}/skills/setup-friction-capture/scripts/install-friction-capture.sh" \
  -o .claude/scripts/install-friction-capture.sh
chmod +x .claude/scripts/install-friction-capture.sh

echo "$TAG" > "$VERSION_FILE"

skill_after=$(sha256sum "$SKILL_FILE" | awk '{print $1}')
if [[ "$skill_before" != "$skill_after" ]]; then
  exit 2
fi
