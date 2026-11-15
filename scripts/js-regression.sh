#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NODE="${MARKDOWN_NODE_BINARY:-$(command -v node)}"
YARN="${MARKDOWN_YARN_BINARY:-$(command -v yarn || true)}"
if [ -z "$YARN" ]; then
  echo "JS tests require Yarn 1.x. Set MARKDOWN_YARN_BINARY to its executable." >&2
  exit 2
fi
major="$($NODE -p 'process.versions.node.split(".")[0]')"
if [ "$major" != "20" ] && [ "$major" != "22" ]; then
  echo "Use Node 20 or 22 for the Kotlin JS test runner; set MARKDOWN_NODE_BINARY." >&2
  exit 2
fi

export PATH="$(dirname "$NODE"):$(dirname "$YARN"):$PATH"
cd "$ROOT"
./gradlew :markdown:jsNodeTest \
  -PmarkdownNodePath="$NODE" -PmarkdownUseSystemYarn=true -PmarkdownYarnCommand="$YARN" \
  --console=plain --no-daemon
