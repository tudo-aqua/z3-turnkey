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

/**
 * Metadata for a downloadable Z3 distribution.
 * @param nameInTasks the CamelCase name to use in derived Gradle tasks.
 * @param nameInDownload the name used in the downloaded file's name.
 * @param operatingSystem the operating system's canonical name.
 * @param cpuArchitecture the CPU architecture's canonical name.
 * @param libraryExtension the file name extension used by the Z3 libraries.
 * @param needsInstallNameTool true if `install_name_tool` must be used to repair the linkage.
 */
data class Z3Distribution(
    val nameInTasks: String,
    val nameInDownload: String,
    val operatingSystem: String,
    val cpuArchitecture: String,
    val libraryExtension: String,
    val needsInstallNameTool: Boolean = false,
)
