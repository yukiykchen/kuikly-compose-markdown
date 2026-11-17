#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ "$#" -ne 1 ]; then
  echo "Usage: scripts/publish-remote.sh <public|internal>" >&2
  exit 2
fi

TARGET="$1"
EXPECTED_VERSION="0.4.4"
RESOLUTION_BASE="https://mirrors.tencent.com/nexus/repository/maven-tencent"
case "$TARGET" in
  public)
    USER_VAR="KUIKLY_OPEN_MAVEN_USERNAME"
    PASSWORD_VAR="KUIKLY_OPEN_MAVEN_PASSWORD"
    REPO_URL="https://mirrors.tencent.com/repository/maven/kuikly-open/"
    ;;
  internal)
    USER_VAR="KUIKLY_INTERNAL_MAVEN_USERNAME"
    PASSWORD_VAR="KUIKLY_INTERNAL_MAVEN_PASSWORD"
    REPO_URL="https://mirrors.tencent.com/repository/maven/kuikly/"
    ;;
  *)
    echo "Unknown target: $TARGET (expected public or internal)" >&2
    exit 2
    ;;
esac

USERNAME="$(printenv "$USER_VAR" || true)"
PASSWORD="$(printenv "$PASSWORD_VAR" || true)"
if [[ -z "$USERNAME" || -z "$PASSWORD" ]]; then
  echo "Missing $USER_VAR / $PASSWORD_VAR for $TARGET release" >&2
  exit 3
fi

"$ROOT/scripts/production-gates.sh"

existing_status="$(curl -sS --max-time 20 -o /dev/null -w '%{http_code}' \
  "$RESOLUTION_BASE/com/tencent/kuiklybase/markdown/$EXPECTED_VERSION/markdown-$EXPECTED_VERSION.pom" || true)"
if [ "$existing_status" = "200" ]; then
  echo "Version $EXPECTED_VERSION already exists in $TARGET resolution repository; refusing to overwrite." >&2
  exit 4
fi

# Remote publishing is destructive: require an explicit operator confirmation
# in addition to the target argument and credentials.
if [ "${ALLOW_REMOTE_PUBLISH:-0}" != "1" ]; then
  echo "DRY_RUN: remote publish not submitted. Re-run with ALLOW_REMOTE_PUBLISH=1 after confirming target=$TARGET." >&2
  exit 0
fi

# Pass the selected endpoint and credentials as Gradle properties. The build
# only creates a remote Maven repository when all three are present.
./gradlew :markdown:publish \
  -PrepoUrl="$REPO_URL" \
  -PmavenUserName="$USERNAME" \
  -PmavenPassword="$PASSWORD" \
  --console=plain --no-daemon

echo "REMOTE_PUBLISH_SUBMITTED target=$TARGET repo=$REPO_URL"
