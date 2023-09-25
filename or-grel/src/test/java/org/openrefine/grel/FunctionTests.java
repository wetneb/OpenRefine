/*

Copyright 2013, Thomas F. Morris
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
 
package org.openrefine.grel;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.openrefine.browsing.Engine;
import org.openrefine.expr.EvalError;
import org.openrefine.expr.MetaParser;
import org.openrefine.model.ModelException;
import org.openrefine.model.Project;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


public class FunctionTests extends FunctionTestBase {

    Project project;
    Engine engine;

    @BeforeMethod
    public void SetUp() throws IOException, ModelException {
        MetaParser.registerLanguageParser("grel", "General Refine Expression Language", Parser.grelParser, "value");
    }


    @AfterMethod
    public void TearDown() {
        bindings = null;
    }

    @Test
    void testZeroArgs() {
        Set<String> valid0args = new HashSet<>(Arrays.asList("now")); // valid 0-arg returns datetype
        // Not sure which, if any, of these are intended, but fixing them may break existing scripts
        Set<String> returnsNull = new HashSet<>(Arrays.asList("chomp", "contains", "escape", "unescape", "exp",
                "fingerprint", "get", "jsonize", "parseJson", "partition", "pow", "rpartition",
                "slice", "substring", // synonyms for Slice
                "unicode", "unicodeType"
                ));
        Set<String> returnsFalse = new HashSet<>(Arrays.asList("hasField"));

        for (Entry<String, Function> entry : ControlFunctionRegistry.getFunctionMapping()) {
            Function func = entry.getValue();
            Object result = func.call(bindings, new Object[0]);
            if (returnsNull.contains(ControlFunctionRegistry.getFunctionName(func))) {
                assertNull(result, ControlFunctionRegistry.getFunctionName(func) + " didn't return null on 0 args");
            } else if (returnsFalse.contains(ControlFunctionRegistry.getFunctionName(func))) {
                assertEquals(result, Boolean.FALSE, ControlFunctionRegistry.getFunctionName(func) + " didn't return false on 0 args");
            } else if (!valid0args.contains(ControlFunctionRegistry.getFunctionName(func))) {
                assertTrue(result instanceof EvalError, ControlFunctionRegistry.getFunctionName(func) + " didn't error on 0 args");
            }
        }
    }

    @Test
    void testTooManyArgs() {
        // Not sure which, if any, of these are intended, but fixing them may break existing scripts
        Set<String> returnsNull = new HashSet<>(Arrays.asList("chomp", "contains", "coalesce", "escape", "unescape",
                "exp", "fingerprint", "get", "now", "parseJson", "partition", "pow", "rpartition",
                "slice", "substring", // synonyms for Slice
                "unicode", "unicodeType"
                ));
        Set<String> returnsFalse = new HashSet<>(Arrays.asList("hasField"));
        Set<String> exempt = new HashSet<>(Arrays.asList(
                "jsonize" // returns literal string "null"
                ));
        for (Entry<String, Function> entry : ControlFunctionRegistry.getFunctionMapping()) {
            Function func = entry.getValue();
            // No functions take 8 arguments, so they should all error
            Object result = func.call(bindings, new Object[] {null, null, null, null, null, null, null, null});
            if (returnsNull.contains(ControlFunctionRegistry.getFunctionName(func))) {
                assertNull(result, ControlFunctionRegistry.getFunctionName(func) + " didn't return null on 8 args");
            } else if (returnsFalse.contains(ControlFunctionRegistry.getFunctionName(func))) {
                assertEquals(result, Boolean.FALSE, ControlFunctionRegistry.getFunctionName(func) + " didn't return false on 8 args");
            } else if (!exempt.contains(ControlFunctionRegistry.getFunctionName(func))) {
                assertTrue(result instanceof EvalError, ControlFunctionRegistry.getFunctionName(func) + " didn't error on 8 args");
            }
        }
    }
}
