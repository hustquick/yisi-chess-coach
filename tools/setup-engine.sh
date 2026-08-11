#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SHARED_SRC="$ROOT_DIR/shared/Stockfish/src"
IOS_SRC="$ROOT_DIR/iOS/ThirdParty/Stockfish/src"
IOS_RESOURCES="$ROOT_DIR/iOS/App/Resources"
LARGE_NETWORK="nn-c288c895ea92.nnue"

# Stockfish's own build target downloads and verifies the official sf_18 NNUE
# files. They stay local because the large network exceeds GitHub's 100 MB
# regular-file limit.
make -C "$SHARED_SRC" net
mkdir -p "$IOS_SRC" "$IOS_RESOURCES"
cp "$SHARED_SRC"/nn-*.nnue "$IOS_SRC"/
cp "$SHARED_SRC/$LARGE_NETWORK" "$IOS_RESOURCES/stockfish.nnue"

printf 'Stockfish 18 NNUE is ready for iOS and Android.\n'
