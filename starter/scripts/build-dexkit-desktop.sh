#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 ../dexadapt/scripts/build-dexkit.py
