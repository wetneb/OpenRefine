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

package org.openrefine.expr.functions.html;

import org.jsoup.nodes.Element;
import org.openrefine.expr.functions.Type;
import org.openrefine.expr.functions.xml.InnerXml;
import org.openrefine.grel.ControlFunctionRegistry;
import org.openrefine.grel.PureFunction;

import org.openrefine.expr.EvalError;

public class InnerHtml extends PureFunction {
    private static final long serialVersionUID = 7062946433356613L;

    @Override
    public Object call(Object[] args) {
        if (args.length == 1) {
            Object o1 = args[0];
            if (o1 != null && o1 instanceof Element) {
                return new InnerXml().call(args, "html");
            }else{
                return new EvalError(ControlFunctionRegistry.getFunctionName(this) + "() cannot work with this '" + new Type().call(args) + "'. The first parameter is not an HTML Element.  Please first use parseHtml(string) and select(query) prior to using this function");
            }
        }
        return new EvalError(ControlFunctionRegistry.getFunctionName(this) + "() cannot work with this '" + new Type().call(args) + "' and expects a single String as an argument");
    }


    @Override
    public String getDescription() {
        return "Returns the inner HTML of an HTML element. This will include text and children elements within the element selected. Use it in conjunction with parseHtml() and select() to provide an element.";
    }
    
    @Override
    public String getParams() {
        return "element e";
    }
    
    @Override
    public String getReturns() {
        return "string innerHtml";
    }
}

