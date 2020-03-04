#!/bin/bash

# Copyright 2019-2020 Simon Dierl <simon.dierl@cs.tu-dortmund.de>
# SPDX-License-Identifier: ISC
#
# Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby
# granted, provided that the above copyright notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
# INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN
# AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
# PERFORMANCE OF THIS SOFTWARE.

set -euxo pipefail
shopt -s extglob

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
  -o jabba
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

for class in $(jar tf "${DOWNLOAD_PATH}"/z3-turnkey-*([0-9.])-integration-tests.jar |
  grep -F '.class' | sed 's#/#.#g;s#.class##g'); do
  java -jar junit.jar \
    --class-path "${DOWNLOAD_PATH}"/z3-turnkey-*([0-9.]).jar \
    --class-path "${DOWNLOAD_PATH}"/z3-turnkey-*([0-9.])-integration-tests.jar \
    --reports-dir "test-$class" \
    --select-class "$class"
done
