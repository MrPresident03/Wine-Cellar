#!/usr/bin/env bash
# Force-push a results folder to a dedicated branch (one commit, no history).
# usage: publish.sh <branch> <dir>
set -euo pipefail
branch="$1"; dir="$2"
mkdir -p "$dir"
cd "$dir"
rm -rf .git
git init -q -b "$branch"
git add -A
git -c user.name="ci" -c user.email="ci@users.noreply.github.com" commit -q --allow-empty -m "$branch results for ${GITHUB_SHA:-local}"
git push -q -f "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}" "$branch"
echo "Published results to $branch"
