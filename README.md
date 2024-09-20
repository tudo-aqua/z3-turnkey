<!--
   SPDX-License-Identifier: CC-BY-4.0

   Copyright 2019-2024 The TurnKey Authors

   This work is licensed under the Creative Commons Attribution 4.0
   International License.

   You should have received a copy of the license along with this
   work. If not, see <https://creativecommons.org/licenses/by/4.0/>.
-->

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/tudo-aqua/z3-turnkey/ci.yml?logo=githubactions&logoColor=white)](https://github.com/tudo-aqua/z3-turnkey/actions)
[![JavaDoc](https://javadoc.io/badge2/tools.aqua/z3-turnkey/javadoc.svg)](https://javadoc.io/doc/tools.aqua/z3-turnkey)
[![Maven Central](https://img.shields.io/maven-central/v/tools.aqua/z3-turnkey?logo=apache-maven)](https://search.maven.org/artifact/tools.aqua/z3-turnkey)

# The Z3-TurnKey Distribution

[The Z3 Theorem Prover](https://github.com/Z3Prover/z3/) is a widely used SMT solver that is written
in C and C++. The authors provide a Java API, however, it expects the user to install native
libraries for their platform. This precludes easy usage of the solver as, e.g., a Maven dependency.

This project packages the Z3 Java API and native libraries for all supported platforms as a
[TurnKey bundle](https://github.com/tudo-aqua/turnkey-support). At runtime, the correct library for
the platform is extracted and automatically loaded. The libraries themselves are modified to support
this paradigm.

At the moment, platform support is as follows:

| Operating System | x86 | AMD64 | AARCH64 |
| ---------------- | :-: | :---: | :-----: |
| Linux            |     |   ✓   |         |
| macOS            |     |   ✓   |    ✓    |
| Windows          |  ✓  |   ✓   |         |

## Usage

The library can be included as a regular Maven dependency from the Maven Central repository. See the
page for your version of choice [there](https://search.maven.org/artifact/tools.aqua/z3-turnkey) for
configuration snippets for most build tools.

## Building

Building the library requires multiple native tools to be installed that can not be installed using
Gradle:

- For library rewriting, see the tools required by the
  [turnkey-gradle-plugin](https://github.com/tudo-aqua/turnkey-gradle-plugin).
- Additionally, Python 3 is required for a code generation step and localized by the
  [gradle-use-python-plugin](https://github.com/xvik/gradle-use-python-plugin).

## Java and JPMS Support

The library requires Java 8. It can be used as a Java module on Java 9+ via the multi-release JAR
mechanism as the `tools.aqua.turnkey.z3` module.

## License

Z3 is licensed under the [MIT License](https://opensource.org/licenses/MIT). Two dependencies are
introduced: the TurnKey support library is licensed under the
[ISC License](https://opensource.org/licenses/ISC) and the JSpecify annotation library used by it is
licensed under the [Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Tests and other non-runtime code are licensed under the
[Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). Standalone documentation
is licensed under the
[Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/).
