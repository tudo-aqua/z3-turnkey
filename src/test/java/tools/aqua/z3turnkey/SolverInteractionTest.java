/*
 * Copyright 2019-2024 The Z3-TurnKey Authors
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

package tools.aqua.z3turnkey;

import static com.microsoft.z3.Status.SATISFIABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.z3.*;
import org.junit.jupiter.api.Test;

/**
 * Test more complicated solver interactions that require both the libz3java and the libz3 to be
 * loaded correctly.
 */
public class SolverInteractionTest {

  /** Check the satisfiability of two floating-point expression. */
  @Test
  @SuppressWarnings("unchecked")
  public void testArithmeticSolving() {
    try (Context ctx = new Context()) {
      IntExpr x = ctx.mkIntConst("x");
      RealExpr xReal = ctx.mkInt2Real(x);
      RealExpr y = ctx.mkRealConst("y");
      RealExpr three = ctx.mkReal(3);
      RealExpr neg2 = ctx.mkReal(-2);
      RealExpr two_thirds = ctx.mkReal(2, 3);
      ArithExpr<RealSort> three_y = ctx.mkMul(three, y);
      ArithExpr<RealSort> y_over_x = ctx.mkDiv(y, xReal);
      BoolExpr x_geq_3y = ctx.mkGe(xReal, three_y);
      BoolExpr x_leq_y = ctx.mkLe(xReal, y);
      BoolExpr neg1_lt_x = ctx.mkLt(neg2, xReal);
      BoolExpr assumptions = ctx.mkAnd(x_geq_3y, x_leq_y, neg1_lt_x);

      Solver solver = ctx.mkSolver();
      solver.add(assumptions);

      solver.push();
      BoolExpr diff_leq_two_thirds = ctx.mkLe(y_over_x, two_thirds);
      assertEquals(SATISFIABLE, solver.check(diff_leq_two_thirds));
      solver.pop();

      solver.push();
      BoolExpr diff_is_two_thirds = ctx.mkEq(y_over_x, two_thirds);
      solver.add(diff_is_two_thirds);
      assertEquals(SATISFIABLE, solver.check());
      solver.pop();
    }
  }

  /** Check the satisfiability of a simple comparison. */
  @Test
  public void testSimpleSolving() {
    try (Context ctx = new Context()) {
      Solver solver = ctx.mkSolver();

      IntExpr x = ctx.mkIntConst("x");
      IntExpr c = ctx.mkInt(15);
      BoolExpr b = ctx.mkGt(x, c);
      assertEquals(SATISFIABLE, solver.check(b));

      Model m = solver.getModel();
      Expr<BoolSort> evaluated = m.evaluate(b, true);
      assertTrue(evaluated.isTrue(), "The model should evaluate");
    }
  }

  /** Check that expression concatenation operates correctly. */
  @Test
  public void testConcat() {
    try (Context ctx = new Context()) {
      Solver solver = ctx.mkSolver();

      SeqExpr<CharSort> x = ctx.mkString("x");
      SeqExpr<CharSort> y = ctx.mkString("y");
      SeqExpr<CharSort> xPlusY = ctx.mkConcat(x, y);
      SeqExpr<CharSort> xy = ctx.mkString("xy");

      BoolExpr b = ctx.mkEq(xPlusY, xy);
      assertEquals(SATISFIABLE, solver.check(b));
    }
  }
}
