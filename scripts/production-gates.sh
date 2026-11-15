#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./gradlew \
  :markdown:compileKotlinJs \
  :markdown:compileKotlinIosArm64 \
  :markdown:compileKotlinIosX64 \
  :markdown:compileKotlinIosSimulatorArm64 \
  :markdown:compileKotlinMacosX64 \
  :markdown:compileKotlinMacosArm64 \
  :markdown:compileDebugKotlinAndroid \
  :markdown:testDebugUnitTest \
  :markdown:assembleRelease \
  :markdown:lintDebug \
  :markdown:publishToMavenLocal \
  --console=plain --no-daemon

python3 - <<'PY'
from pathlib import Path
import re
import json
import xml.etree.ElementTree as ET

root = Path(".")
aar = root / "markdown/build/outputs/aar/markdown-release.aar"
assert aar.is_file(), "missing markdown-release.aar"

namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
publication = root / "markdown/build/publications/kotlinMultiplatform/pom-default.xml"
version = ET.parse(publication).getroot().findtext("m:version", namespaces=namespace)
assert version, "missing publication version"
poms = sorted(Path.home().joinpath(".m2/repository/com/tencent/kuiklybase").glob(f"markdown*/{version}/*.pom"))
assert poms, f"missing local Maven POMs for {version}"
for pom in poms:
    text = pom.read_text()
    assert ET.parse(pom).getroot().findtext("m:version", namespaces=namespace) == version, f"unexpected version in {pom}"
    assert "-internal" not in text, f"internal-only coordinate in {pom}"
    assert "sk-" not in text, f"credential-looking text in {pom}"
    assert "/Users/" not in text, f"local path in {pom}"

modules = sorted(Path.home().joinpath(".m2/repository/com/tencent/kuiklybase").glob(f"markdown*/{version}/*.module"))
assert modules, "missing Gradle module metadata"
for module in modules:
    text = module.read_text()
    assert json.loads(text)["component"]["version"] == version, f"unexpected metadata version in {module}"
    assert "-internal" not in text and "sk-" not in text and "/Users/" not in text, f"unsafe metadata in {module}"

tests = [ET.parse(path).getroot() for path in (root / "markdown/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
test_count = sum(int(test.attrib["tests"]) for test in tests)
assert test_count >= 35, f"missing hardening tests: {test_count}"
required_suites = {
    "StreamingSessionRegressionTest", "RichTextInvalidationTest", "StreamingParityRegressionTest",
    "StreamingChunkBufferTest", "MarkdownCodeChunkTest", "DiagnosticsDefaultTest",
}
actual_suites = {test.attrib["name"].split(".")[-1] for test in tests}
assert required_suites <= actual_suites, f"missing suites: {required_suites - actual_suites}"
assert all(int(test.attrib["failures"]) == 0 and int(test.attrib["errors"]) == 0 for test in tests), "unit tests failed"
assert all(int(test.attrib.get("skipped", 0)) == 0 for test in tests), "skipped production tests"

report = root / "markdown/build/reports/lint-results-debug.txt"
text = report.read_text(errors="replace")
match = re.search(r"(\d+) errors?, \d+ warnings?", text)
assert match and int(match.group(1)) == 0, f"lint errors: {match.group(0) if match else 'unknown'}"

print(f"PRODUCTION_GATES_OK version={version} aar={aar.resolve()} poms={len(poms)} modules={len(modules)} tests={test_count} lint_errors=0")
PY
