#!/usr/bin/env bash
# Clone the sources the pipeline reads:
#   se/  Standard Ebooks source repos (sources.txt, about 50 small repos)
#   pg/  Project Gutenberg books, from GITenberg's mirror on GitHub (pg_sources.txt)
set -euo pipefail
cd "$(dirname "$0")"

fetch() {  # fetch <dir> <github url>
  if [ -d "$1" ]; then
    git -C "$1" pull -q --ff-only
  else
    git clone -q --depth 1 "$2" "$1"
  fi
  echo "ok  $1"
}

mkdir -p se pg
while read -r repo; do
  [ -z "$repo" ] && continue
  fetch "se/$repo" "https://github.com/standardebooks/$repo.git"
done < sources.txt
while read -r repo; do
  [ -z "$repo" ] && continue
  fetch "pg/$repo" "https://github.com/GITenberg/$repo.git"
done < pg_sources.txt
