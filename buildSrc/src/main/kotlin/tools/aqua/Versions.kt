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

import java.util.Locale.ROOT

/** Keywords that indicate a stable version, independent of other version elements. */
private val stableKeyword = listOf("RELEASE", "FINAL", "GA")

/** A regular expression recognizing "usual" stable versions. */
private val regex = "^[0-9,.v-]+(-r)?$".toRegex()

/** Checks if this string appears to represent a stable version. */
val String.isStable: Boolean
  get() = stableKeyword.any { uppercase(ROOT).contains(it) } || regex.matches(this)

/** Checks if this string does not appear to represent a stable version. */
val String.isUnstable: Boolean
  get() = isStable.not()
