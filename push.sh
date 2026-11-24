#!/bin/sh
# Phone-friendly push: creates/commits the project and pushes to an empty GitHub repo.
# Usage: GH_TOKEN=... ./push.sh https://github.com/USER/BrahmaMobile.git
set -e

REPO="$1"
if [ -z "$REPO" ]; then
    echo "usage: ./push.sh <github-repo-url>"
    exit 1
fi

cd "$(dirname "$0")"

git init -q
git config user.name "Brahma Builder"
git config user.email "brahma-builder@users.noreply.github.com"

# Keep local-only files out of the commit even if .gitignore is bypassed.
rm -f local.properties

git add -A
git commit -q -m "Brahma Mobile: initial Android project" || true

if [ -n "$GH_TOKEN" ]; then
    REPO=$(printf '%s' "$REPO" | sed "s#https://#https://${GH_TOKEN}@#")
fi

git remote remove origin 2>/dev/null || true
git remote add origin "$REPO"
git branch -M main
git push -u origin main

echo "Pushed. Now open GitHub -> Actions -> Build Brahma Mobile APK."
