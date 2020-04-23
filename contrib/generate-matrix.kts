/*
 * Copyright 2020 Simon Dierl <simon.dierl@cs.tu-dortmund.de>
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

data class Platform(val name: String, val image: String)

data class JDK(val name: String, val jabbaName: String, val versions: List<Int>)

val platforms = listOf(
    Platform("Linux", "ubuntu-latest"),
    Platform("OSX", "macOS-latest"),
    Platform("Windows", "windows-latest")
)

val jdks = listOf(
    JDK("AdoptOpenJDK", "adopt", listOf(8, 11, 14)),
    JDK("OpenJDK", "openjdk", listOf(11, 14)),
    JDK("Zulu", "zulu", listOf(8, 11, 14))
)

platforms.forEach { platform ->
    jdks.forEach { jdk ->
        jdk.versions.forEach { jdkVersion ->
            println(
                """
                ${platform.name}${jdk.name}$jdkVersion:
                  VMImage: ${platform.image}
                  JDK: ${jdk.jabbaName}
                  JDKVersion: 1.$jdkVersion-0""".replaceIndent("  ".repeat(7))
            )
        }
    }
}
