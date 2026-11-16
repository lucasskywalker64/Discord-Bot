#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
image=${1:?Pass the default image reference for this bundle}
mkdir -p target/setup-bundle
cp compose.yaml setup.sh README.md target/setup-bundle/
chmod +x target/setup-bundle/setup.sh
awk -v image="$image" '/^APP_IMAGE=/ { print "APP_IMAGE=" image; next } { print }' \
    .env.example > target/setup-bundle/.env.example
tar -czf target/discord-bot-setup.tar.gz -C target/setup-bundle compose.yaml .env.example setup.sh README.md
