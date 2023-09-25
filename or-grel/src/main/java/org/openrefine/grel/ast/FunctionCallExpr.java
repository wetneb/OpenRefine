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

package org.openrefine.grel.ast;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.openrefine.grel.Function;
import org.openrefine.grel.PureFunction;

import org.openrefine.expr.EvalError;
import org.openrefine.expr.ExpressionUtils;

/**
 * An abstract syntax tree node encapsulating a function call. The function's
 * arguments are all evaluated down to values before the function is applied.
 * If any argument is an error, the function is not applied, and the error is
 * the result of the expression.
 */
public class FunctionCallExpr implements GrelExpr {

    private static final long serialVersionUID = -7793494352606403242L;
    final protected Function    _function;
    final protected GrelExpr[] _args;
    final protected String      _sourceName;
    
    public FunctionCallExpr(GrelExpr[] args, Function f, String sourceName) {
        _args = args;
        _function = f;
        _sourceName = sourceName;
    }
                              
    @Override
    public Object evaluate(Properties bindings) {
        Object[] args = new Object[_args.length];
        for (int i = 0; i < _args.length; i++) {
            Object v = _args[i].evaluate(bindings);
            if (ExpressionUtils.isError(v)) {
                return v; // bubble up the error
            }
             args[i] = v;
        }
        try {
            return _function.call(bindings, args);
        } catch (Exception e) {
            return new EvalError(e);
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        
        for (GrelExpr ev : _args) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(ev.toString());
        }
        
        return _sourceName + "(" + sb.toString() + ")";
    }
    
    @Override
    public boolean equals(Object other) {
    	return (other instanceof GrelExpr) && toString().equals(other.toString());
    }
    
    @Override
    public final Set<String> getColumnDependencies(String baseColumn) {
        if (_function instanceof PureFunction) {
            Set<String> dependencies = new HashSet<>();
            for (GrelExpr ev : _args) {
                Set<String> deps = ev.getColumnDependencies(baseColumn);
                if (deps == null) {
                    return null;
                }
                dependencies.addAll(deps);
            }
            return dependencies;
        } else {
            // Functions which are not pure might rely on arbitrary parts of the project
            return null;
        }
    }
    
    @Override
    public FunctionCallExpr renameColumnDependencies(Map<String, String> substitutions) {
        if (_function instanceof PureFunction) {
            GrelExpr[] translatedArgs = new GrelExpr[_args.length];
            for(int i = 0; i != _args.length; i++) {
                translatedArgs[i] = _args[i].renameColumnDependencies(substitutions);
                if(translatedArgs[i] == null) {
                    return null;
                }
            }
            return new FunctionCallExpr(translatedArgs, _function, _sourceName);
        } else {
            // Functions which are not pure might rely on arbitrary parts of the project
            return null;
        }
    }

    @Override
    public boolean isLocal() {
        if (!(_function instanceof PureFunction)) {
            return false;
        }
        for (GrelExpr ev : _args) {
            if(!ev.isLocal()) {
                return false;
            }
        }
        return true;
    }
}
