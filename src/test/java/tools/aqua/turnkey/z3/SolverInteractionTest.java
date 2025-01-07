/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2025 The TurnKey Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.aqua.turnkey.z3;

import static com.microsoft.z3.Status.SATISFIABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BOOLEAN;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.BoolSort;
import com.microsoft.z3.CharSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.RealExpr;
import com.microsoft.z3.RealSort;
import com.microsoft.z3.SeqExpr;
import com.microsoft.z3.Solver;
import org.junit.jupiter.api.Test;

/** Test more complicated solver interactions that require the entire ecosystem to be loaded. */
class SolverInteractionTest {

  /** Check the satisfiability of a simple comparison. */
  @Test
  void testSimpleSolving() {
    try (Context ctx = new Context()) {
      final Solver solver = ctx.mkSolver();

      final IntExpr x = ctx.mkIntConst("x");
      final IntExpr c = ctx.mkInt(15);
      final BoolExpr b = ctx.mkGt(x, c);
      assertThat(solver.check(b)).isEqualTo(SATISFIABLE);

      final Model m = solver.getModel();
      final Expr<BoolSort> evaluated = m.evaluate(b, true);
      assertThat(evaluated).extracting(Expr::isTrue, BOOLEAN).isTrue();
    }
  }

  /** Check the satisfiability of two floating-point expressions. */
  @Test
  void testArithmeticSolving() {
    try (Context ctx = new Context()) {
      final IntExpr x = ctx.mkIntConst("x");
      final RealExpr xReal = ctx.mkInt2Real(x);

      final RealExpr y = ctx.mkRealConst("y");

      final RealExpr three = ctx.mkReal(3);
      final RealExpr minusTwo = ctx.mkReal(-2);
      final RealExpr twoThirds = ctx.mkReal(2, 3);

      final ArithExpr<RealSort> threeY = ctx.mkMul(three, y);
      final ArithExpr<RealSort> yOverX = ctx.mkDiv(y, xReal);

      final BoolExpr xGreaterEqualThreeY = ctx.mkGe(xReal, threeY);
      final BoolExpr xLessEqualY = ctx.mkLe(xReal, y);
      final BoolExpr minusTwoLessX = ctx.mkLt(minusTwo, xReal);

      final BoolExpr assumptions = ctx.mkAnd(xGreaterEqualThreeY, xLessEqualY, minusTwoLessX);

      final Solver solver = ctx.mkSolver();
      solver.add(assumptions);

      solver.push();
      final BoolExpr differenceLessEqualTwoThirds = ctx.mkLe(yOverX, twoThirds);
      assertThat(solver.check(differenceLessEqualTwoThirds)).isEqualTo(SATISFIABLE);
      solver.pop();

      solver.push();
      final BoolExpr differenceIsTwoThirds = ctx.mkEq(yOverX, twoThirds);
      assertThat(solver.check(differenceIsTwoThirds)).isEqualTo(SATISFIABLE);
      solver.pop();
    }
  }

  /** Check that expression concatenation operates correctly. */
  @Test
  void testConcat() {
    try (Context ctx = new Context()) {
      final Solver solver = ctx.mkSolver();

      final SeqExpr<CharSort> x = ctx.mkString("x");
      final SeqExpr<CharSort> y = ctx.mkString("y");
      final SeqExpr<CharSort> xPlusY = ctx.mkConcat(x, y);
      final SeqExpr<CharSort> xy = ctx.mkString("xy");

      final BoolExpr b = ctx.mkEq(xPlusY, xy);
      assertThat(solver.check(b)).isEqualTo(SATISFIABLE);
    }
  }
}
