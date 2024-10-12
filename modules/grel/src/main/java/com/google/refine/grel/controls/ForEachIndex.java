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

package com.google.refine.grel.controls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.google.refine.expr.EvalError;
import com.google.refine.expr.ExpressionUtils;
import com.google.refine.expr.util.JsonValueConverter;
import com.google.refine.grel.Control;
import com.google.refine.grel.ControlDescription;
import com.google.refine.grel.ControlEvalError;
import com.google.refine.grel.ControlFunctionRegistry;
import com.google.refine.grel.ast.GrelExpr;
import com.google.refine.grel.ast.VariableExpr;

public class ForEachIndex implements Control {

    @Override
    public String checkArguments(GrelExpr[] args) {
        if (args.length != 4) {
            return ControlEvalError.expects_four_args(ControlFunctionRegistry.getControlName(this));
        } else if (!(args[1] instanceof VariableExpr)) {
            // variable name";
            return ControlEvalError.expects_second_arg_index_var_name(ControlFunctionRegistry.getControlName(this));
        } else if (!(args[2] instanceof VariableExpr)) {
            // variable name";
            return ControlEvalError.expects_third_arg_element_var_name(ControlFunctionRegistry.getControlName(this));
        } else if (args[1].equals(args[2])) {
            return ControlEvalError.expects_second_third_args_different(ControlFunctionRegistry.getControlName(this));
        }
        return null;
    }

    @Override
    public Object call(Properties bindings, GrelExpr[] args) {
        Object o = args[0].evaluate(bindings);
        if (ExpressionUtils.isError(o)) {
            return o;
        } else if (!ExpressionUtils.isArrayOrCollection(o) && !(o instanceof ArrayNode) && !(o instanceof ObjectNode)) {
            return new EvalError(ControlEvalError.foreach_index());
        }

        String indexName = ((VariableExpr) args[1]).getName();
        String elementName = ((VariableExpr) args[2]).getName();

        Object oldIndexValue = bindings.get(indexName);
        Object oldElementValue = bindings.get(elementName);
        try {
            List<Object> results = null;

            if (o.getClass().isArray()) {
                Object[] values = (Object[]) o;

                results = new ArrayList<Object>(values.length);

                for (int i = 0; i < values.length; i++) {
                    Object v = values[i];

                    bindings.put(indexName, i);
                    if (v != null) {
                        bindings.put(elementName, v);
                    } else {
                        bindings.remove(elementName);
                    }
                    Object r = args[3].evaluate(bindings);

                    results.add(r);
                }
            } else if (o instanceof ArrayNode) {
                ArrayNode a = (ArrayNode) o;
                int l = a.size();

                results = new ArrayList<Object>(l);
                for (int i = 0; i < l; i++) {
                    Object v = JsonValueConverter.convert(a.get(i));

                    bindings.put(indexName, i);
                    if (v != null) {
                        bindings.put(elementName, v);
                    } else {
                        bindings.remove(elementName);
                    }

                    Object r = args[3].evaluate(bindings);

                    results.add(r);
                }
            } else if (o instanceof ObjectNode) {
                ObjectNode obj = (ObjectNode) o;
                results = new ArrayList<>(obj.size());
                for (Iterator<Map.Entry<String, JsonNode>> it = obj.fields(); it.hasNext();) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    if (entry != null) {
                        bindings.put(indexName, entry.getKey());
                        bindings.put(elementName, entry.getValue());
                    } else {
                        bindings.remove(indexName);
                        bindings.remove(elementName);
                    }
                    Object r = args[3].evaluate(bindings);
                    results.add(r);
                }
            } else {
                List<Object> list = ExpressionUtils.toObjectList(o);

                results = new ArrayList<Object>(list.size());

                for (int i = 0; i < list.size(); i++) {
                    Object v = list.get(i);

                    bindings.put(indexName, i);
                    bindings.put(elementName, v);

                    Object r = args[3].evaluate(bindings);

                    results.add(r);
                }
            }

            return results.toArray();
        } finally {
            /*
             * Restore the old values bound to the variables, if any.
             */
            if (oldIndexValue != null) {
                bindings.put(indexName, oldIndexValue);
            } else {
                bindings.remove(indexName);
            }
            if (oldElementValue != null) {
                bindings.put(elementName, oldElementValue);
            } else {
                bindings.remove(elementName);
            }
        }
    }

    @Override
    public String getDescription() {
        // its value to variable name v, evaluates expression e, and pushes the result onto the result array.";
        return ControlDescription.foreach_index_desc();
    }

    @Override
    public String getParams() {
        return "expression a, variable i, variable v, expression e";
    }

    @Override
    public String getReturns() {
        return "array";
    }
}
