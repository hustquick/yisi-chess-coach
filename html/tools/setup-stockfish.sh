#!/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENGINE_DIR="$ROOT_DIR/.local/stockfish"
SOURCE_DIR="$(mktemp -d /tmp/stockfish18-build.XXXXXX)"
trap 'rm -rf "$SOURCE_DIR"' EXIT
git clone --depth 1 --branch sf_18 https://github.com/official-stockfish/Stockfish.git "$SOURCE_DIR"
make -C "$SOURCE_DIR/src" net
if [ "$(uname -m)" = "arm64" ]; then ENGINE_ARCH="apple-silicon"; else ENGINE_ARCH="x86-64"; fi
make -C "$SOURCE_DIR/src" -j4 build ARCH="$ENGINE_ARCH"
mkdir -p "$ENGINE_DIR"
cp "$SOURCE_DIR/src/stockfish" "$ENGINE_DIR/stockfish"
cp "$SOURCE_DIR/Copying.txt" "$ENGINE_DIR/COPYING.txt"
printf 'Stockfish 18 installed in %s\n' "$ENGINE_DIR"
