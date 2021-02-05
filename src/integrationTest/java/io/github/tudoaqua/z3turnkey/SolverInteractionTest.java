/*
 * Copyright 2019-2021 The Z3-TurnKey Authors
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

package io.github.tudoaqua.z3turnkey;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SolverInteractionTest {

	@Test
	public void evaluatesExpression() {
		Context ctx = new Context();
		ArithExpr x = ctx.mkIntConst("x");
		ArithExpr y = ctx.mkRealConst("y");
		ArithExpr three = ctx.mkReal(3);
		ArithExpr neg2 = ctx.mkReal(-2);
		ArithExpr two_thirds = ctx.mkReal(2, 3);
		ArithExpr three_y = ctx.mkMul(three, y);
		ArithExpr diff = ctx.mkDiv(y, x);
		BoolExpr x_geq_3y = ctx.mkGe(x, three_y);
		BoolExpr x_leq_y = ctx.mkLe(x, y);
		BoolExpr neg1_lt_x = ctx.mkLt(neg2, x);
		BoolExpr assumptions = ctx.mkAnd(x_geq_3y, x_leq_y, neg1_lt_x);
		Solver solver = ctx.mkSolver();
		solver.add(assumptions);
		solver.push();
		Expr diff_leq_two_thirds = ctx.mkLe(diff, two_thirds);
		Status var1 = solver.check(diff_leq_two_thirds);
		assertEquals(Status.UNKNOWN, var1);
		solver.pop();
		solver.push();
		BoolExpr diff_is_two_thirds = ctx.mkEq(diff, two_thirds);
		solver.add(diff_is_two_thirds);
		Status var2 = solver.check();
		assertEquals(Status.UNKNOWN,var2);
		solver.pop();
	}

	@Test
	public void example2Test(){

		Context ctx = new Context();
		Solver solver = ctx.mkSolver();

		ArithExpr x = ctx.mkIntConst("x");
		ArithExpr c = ctx.mkInt(15);
		BoolExpr b = ctx.mkGt(x, c);
		assertEquals(Status.SATISFIABLE, solver.check(b));
		Model m = solver.getModel();
		Expr evaluated = m.evaluate(b, true);
		assertTrue(evaluated.isTrue(), "The model should evaluate");
	}
}
