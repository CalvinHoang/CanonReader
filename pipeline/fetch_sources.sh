#!/usr/bin/env bash
# Clone the Standard Ebooks source repos the pipeline reads (about 60 small repos).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p se
while read -r repo; do
  [ -z "$repo" ] && continue
  if [ -d "se/$repo" ]; then
    git -C "se/$repo" pull -q --ff-only
  else
    git clone -q --depth 1 "https://github.com/standardebooks/$repo.git" "se/$repo"
  fi
  echo "ok  $repo"
done < sources.txt
