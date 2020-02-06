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
package org.openrefine.expr.functions.strings;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import org.openrefine.expr.EvalError;
import org.openrefine.expr.functions.FunctionTestBase;
import org.openrefine.util.ParsingUtilities;
import org.openrefine.util.TestUtils;

public class SmartSplitTests extends FunctionTestBase {

    @Test
    public void testSmartSplitInvalidParams() {
        assertTrue(invoke("smartSplit") instanceof EvalError);
        assertTrue(invoke("smartSplit", "teststring1", 1, "teststring2", 2) instanceof EvalError);
    }

    @Test
    public void testSmartSplitGuessComma() {
        String testString = "teststring1,teststring2,teststring3,teststring4";
        String[] expected = { "teststring1", "teststring2", "teststring3", "teststring4" };
        String[] actual = (String[]) invoke("smartSplit", testString);
        assertEquals(expected, actual);
    }

    @Test
    public void testSmartSplitGuessTab() {
        String testString = "teststring1	teststring2	teststring3	teststring4";
        String[] expected = { "teststring1", "teststring2", "teststring3", "teststring4" };
        String[] actual = (String[]) invoke("smartSplit", testString);
        assertEquals(expected, actual);
    }

    @Test
    public void testSmartSplitCharSepGiven() {
        String testString = "teststring1#teststring2#teststring3#teststring4";
        String[] expected = { "teststring1", "teststring2", "teststring3", "teststring4" };
        String[] actual = (String[]) invoke("smartSplit", testString, '#');
        assertEquals(expected, actual);
    }
    
    @Test
    public void testSmartSplitCharSepSpace() {
        String testString = "teststring1 teststring2 teststring3 teststring4";
        String[] expected = { "teststring1", "teststring2", "teststring3", "teststring4" };
        String[] actual = (String[]) invoke("smartSplit", testString, ' ');
        assertEquals(expected, actual);
    }
    
    @Test
    public void testSmartSplitStringSepGiven() {
        String testString = "teststring1#@$teststring2#@$teststring3#@$teststring4";
        Object actual = invoke("smartSplit", testString, "#@$");
        assertTrue(actual instanceof EvalError);
    }

    @Test
    public void serializeSmartSplit() {
        String json = "{\"description\":\"Returns the array of strings obtained by splitting s with separator sep. Handles quotes properly. Guesses tab or comma separator if \\\"sep\\\" is not given.\",\"params\":\"string s, optional string sep\",\"returns\":\"array\"}";
        TestUtils.isSerializedTo(new SmartSplit(), json, ParsingUtilities.defaultWriter);
    }

}