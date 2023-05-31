/*
 * Copyright 2019-2023 The Z3-TurnKey Authors
 * SPDX-License-Identifier: ISC
 *
 * Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby
 * granted, provided that the above copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN
 * AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

package tools.aqua

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

/** A toolchain to run tests on. */
data class TestToolchain(
    /** The gradle-style name for the test, in CamelCase. */
    val name: String,
    /** The JVM version to run on. */
    val version: JavaLanguageVersion,
    /** The JVM provider to use. */
    val vendor: JvmVendorSpec,
) {
  /** Simplified constructzor that accepts a JVM version as a number. */
  constructor(
      name: String,
      version: Int,
      vendor: JvmVendorSpec
  ) : this(name, JavaLanguageVersion.of(version), vendor)
}
