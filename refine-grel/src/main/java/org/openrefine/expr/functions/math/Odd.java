/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.openrefine.expr.functions.math;

import org.openrefine.expr.EvalError;
import org.openrefine.grel.ControlFunctionRegistry;
import org.openrefine.grel.EvalErrorMessage;
import org.openrefine.grel.FunctionDescription;
import org.openrefine.grel.PureFunction;

public class Odd extends PureFunction {

    private static final long serialVersionUID = -403957751541507355L;

    @Override
    public Object call(Object[] args) {
        if (args.length == 1 && args[0] instanceof Number) {
            return Odd.roundUpToOdd(((Number) args[0]).doubleValue());
        }
        return new EvalError(EvalErrorMessage.expects_one_number(ControlFunctionRegistry.getFunctionName(this)));
    }

    public static double roundUpToOdd(double d) {
        double temp = Math.ceil(d);
        return ((temp % 2) == 0) ? temp + 1 : temp;
    }

    @Override
    public String getDescription() {
        return FunctionDescription.math_odd();
    }

    @Override
    public String getParams() {
        return "number n1";
    }

    @Override
    public String getReturns() {
        return "number";
    }
}
