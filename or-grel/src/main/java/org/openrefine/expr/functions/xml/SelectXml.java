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

package org.openrefine.expr.functions.xml;

import org.jsoup.nodes.Element;
import org.openrefine.grel.ControlFunctionRegistry;
import org.openrefine.grel.PureFunction;

import org.openrefine.expr.EvalError;

public class SelectXml extends PureFunction {

    private static final long serialVersionUID = -3706761505628408720L;

    @Override
    public Object call(Object[] args) {
        if (args.length == 2) {
            Object o1 = args[0];
            Object o2 = args[1];
            if (o1 != null && o1 instanceof Element) {
                Element e1 = (Element)o1;
                if(o2 != null && o2 instanceof String){
                    return e1.select(o2.toString());
                }
            }else{
                return new EvalError(ControlFunctionRegistry.getFunctionName(this) + " failed as the first parameter is not an XML or HTML Element.  Please first use parseXml() or parseHtml()");
            }
        }
        return new EvalError(ControlFunctionRegistry.getFunctionName(this) + " expects two arguments");
    }


    @Override
    public String getDescription() {
        return "Returns an array of all the desired elements from an HTML or XML document, if the element exists. Elements are identified using the Jsoup selector syntax: https://jsoup.org/apidocs/org/jsoup/select/Selector.html.";
    }
    
    @Override
    public String getParams() {
        return "string s, element e";
    }
    
    @Override
    public String getReturns() {
        return "HTML Elements";
    }
}

