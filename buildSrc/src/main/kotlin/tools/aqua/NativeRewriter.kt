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

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import java.io.FilterReader
import java.io.Reader
import java.io.StringReader

/** A rewriter that can be used to fix Z3's `Native.java` to hook into out `Z3Loader`. */
class NativeRewriter(input: Reader) : FilterReader(rewriteNative(input))

private fun rewriteNative(source: Reader): Reader {
  val parse = JavaParser().parse(source)

  val compilationUnit = parse.result.orElseThrow(::NoSuchElementException)
  val nativeClass = compilationUnit.types.single { it.name.id == "Native" }
  val staticInitializer =
      nativeClass.members
          .filterIsInstance(InitializerDeclaration::class.java)
          .first(InitializerDeclaration::isStatic)
  staticInitializer.body = BlockStmt(NodeList(ExpressionStmt(MethodCallExpr("Z3Loader.loadZ3"))))

  compilationUnit.toString()

  return object : StringReader(compilationUnit.toString()) {
    override fun close() {
      super.close()
      source.close()
    }
  }
}
