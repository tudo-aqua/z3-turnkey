/*
 * Copyright 2019-2022 The Z3-TurnKey Authors
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

import java.net.URL

/** Metadata for a downloadable Z3 distribution. */
interface Z3Distribution {
  /** The CamelCase name to use in derived Gradle tasks. */
  val nameInTasks: String
  /** The URL used to download the distribution for version [version]. */
  fun downloadURL(version: String): URL
  /** The path to the library files inside the artifact for version [version]. */
  fun libraryPath(version: String): String
  /** The operating system's canonical name. */
  val operatingSystem: String
  /** The CPU architecture's canonical name. */
  val cpuArchitecture: String
  /** The file name extension used by the Z3 libraries. */
  val libraryExtension: String
  /** True if `install_name_tool` must be used to repair the linkage. */
  val needsInstallNameTool: Boolean
}

/** Self-built Z3 distribution. */
data class SelfBuiltZ3Distribution(
    override val nameInTasks: String,
    val downloadName: String,
    override val operatingSystem: String,
    override val cpuArchitecture: String,
    override val libraryExtension: String
) : Z3Distribution {
  override fun downloadURL(version: String): URL =
      URL("https://github.com/tudo-aqua/z3-builds/releases/download/$version/z3-$downloadName.zip")
  override fun libraryPath(version: String): String = "z3/build"
  override val needsInstallNameTool: Boolean = false
}

/** Z3-built Z3 distribution. */
data class OfficialZ3Distribution(
    override val nameInTasks: String,
    val downloadName: String,
    override val operatingSystem: String,
    override val cpuArchitecture: String,
    override val libraryExtension: String
) : Z3Distribution {
  override fun downloadURL(version: String): URL =
      URL(
          "https://github.com/Z3Prover/z3/releases/download/z3-$version/z3-$version-$downloadName.zip")
  override fun libraryPath(version: String): String = "z3-$version-$downloadName/bin"
  override val needsInstallNameTool: Boolean = operatingSystem == "osx"
}
