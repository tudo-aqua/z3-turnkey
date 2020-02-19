#!/bin/bash

set -euxo pipefail
shopt -s extglob

MAVEN='https://repo1.maven.org/maven2'
JUNIT_GROUP='org/junit/platform'
JUNIT_ARTIFACT='junit-platform-console-standalone'

ls -R # Debug output

curl -sL https://github.com/shyiko/jabba/raw/master/install.sh | bash
source ~/.jabba/jabba.sh
jabba install "${JABBA_JDK}"
curl -sL "${MAVEN}/${JUNIT_GROUP}/${JUNIT_ARTIFACT}/${JUNIT_VERSION}/${JUNIT_ARTIFACT}-${JUNIT_VERSION}.jar" \
  -o junit.jar
for class in $(jar tf z3-turnkey-*([0-9.])-integration-tests.jar | grep -F '.class' | sed 's#/#.#g;s#.class##g'); do
  java -jar junit.jar \
    --class-path z3-turnkey-*([0-9.]).jar \
    --class-path z3-turnkey-*([0-9.])-integration-tests.jar \
    --reports-dir "test-$class" \
    --select-class "$class"
done
