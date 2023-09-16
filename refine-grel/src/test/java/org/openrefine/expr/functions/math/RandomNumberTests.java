/*******************************************************************************
 * Copyright (C) 2018, OpenRefine contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package org.openrefine.expr.functions.math;

import org.openrefine.expr.EvalError;
import org.openrefine.grel.FunctionTestBase;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RandomNumberTests extends FunctionTestBase {

    @Test
    public void testCall() {
        Object result1 = invoke("random");
        Assert.assertTrue(result1 instanceof Double && inRange(0, 1, result1));

        Object result2 = invoke("random", 3, 4);
        Assert.assertTrue(inRange(3, 4, result2));

        Object result3 = invoke("randomNumber", 3, 4.4);
        Assert.assertTrue(inRange(3, 4.4, result3));

        Object result4 = invoke("random", 2.3, 4);
        Assert.assertTrue(result4 instanceof Double && inRange(2.3, 4, result4));

        Object result5 = invoke("randomNumber", 3.2, 12.2);
        Assert.assertTrue(result5 instanceof Double && inRange(3.2, 12.2, result5));
    }

    public boolean inRange(double min, double max, Object result) {
        return (Double) result >= min && (Double) result <= max;
    }

    @Test
    public void testCallInvalidParams() {
        Assert.assertTrue(invoke("random", 2) instanceof EvalError);
        Assert.assertTrue(invoke("random", 3) instanceof EvalError);
        Assert.assertTrue(invoke("random", null, null) instanceof EvalError);
        Assert.assertTrue(invoke("random", 3, 4, 6, 5) instanceof EvalError);
    }

}
