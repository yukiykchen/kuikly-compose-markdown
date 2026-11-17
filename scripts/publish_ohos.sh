#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

: "${KUIKLY_OPEN_MAVEN_USERNAME:?missing public Maven username}"
: "${KUIKLY_OPEN_MAVEN_PASSWORD:?missing public Maven password}"
REPO_URL="${KUIKLY_OPEN_MAVEN_REPO_URL:-https://mirrors.tencent.com/repository/maven/kuikly-open/}"

# The KBA OHOS release uses build.ohos.gradle.kts and version 0.4.4-ohos.
export COMPAT_BUILD_TYPE=ohos
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew -c settings.ohos.gradle.kts :markdown:publish \
  -PrepoUrl="$REPO_URL" \
  -PmavenUserName="$KUIKLY_OPEN_MAVEN_USERNAME" \
  -PmavenPassword="$KUIKLY_OPEN_MAVEN_PASSWORD" \
  --console=plain --no-daemon
