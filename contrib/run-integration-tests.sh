#!/bin/bash

set -euxo pipefail
shopt -s extglob

ls -aR # Debug output

# Install Jabba

JABBA_VERSION="$(curl -Ls https://shyiko.github.com/jabba/latest)"
case "${OS_IMAGE}" in
ubuntu-latest)
  JABBA_SUFFIX='linux-amd64'
  ;;
macOS-latest)
  JABBA_SUFFIX='darwin-amd64'
  ;;
windows-latest)
  JABBA_SUFFIX='windows-amd64.exe'
  ;;
esac
JABBA_GITHUB="https://github.com/shyiko/jabba/releases/download"
curl -L "${JABBA_GITHUB}/${JABBA_VERSION}/jabba-${JABBA_VERSION}-${JABBA_SUFFIX}" \
  -o jabba "$BINARY_URL"
chmod +x jabba

# Install JDK

./jabba install "${JABBA_JDK}"

# Install JUnit

MAVEN='https://repo1.maven.org/maven2'
JUNIT_GROUP='org/junit/platform'
JUNIT_ARTIFACT='junit-platform-console-standalone'

curl -L "${MAVEN}/${JUNIT_GROUP}/${JUNIT_ARTIFACT}/${JUNIT_VERSION}/${JUNIT_ARTIFACT}-${JUNIT_VERSION}.jar" \
  -o junit.jar

# Run Tests

for class in $(jar tf Build/z3-turnkey-*([0-9.])-integration-tests.jar |
  grep -F '.class' | sed 's#/#.#g;s#.class##g'); do
  java -jar junit.jar \
    --class-path Build/z3-turnkey-*([0-9.]).jar \
    --class-path Build/z3-turnkey-*([0-9.])-integration-tests.jar \
    --reports-dir "test-$class" \
    --select-class "$class"
done
