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
package org.openrefine.clustering.knn;

import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.Serializable;

import org.openrefine.RefineTest;
import org.openrefine.browsing.Engine;
import org.openrefine.browsing.EngineConfig;
import org.openrefine.clustering.ClustererConfigFactory;
import org.openrefine.clustering.knn.kNNClusterer.kNNClustererConfig;
import org.openrefine.model.GridState;
import org.openrefine.util.ParsingUtilities;
import org.openrefine.util.TestUtils;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import edu.mit.simile.vicino.distances.PPMDistance;

public class kNNClustererTests extends RefineTest {
    
    public static String configJson = "{"
            + "\"type\":\"knn\","
            + "\"function\":\"PPM\","
            + "\"column\":\"values\","
            + "\"params\":{\"radius\":1,\"blocking-ngram-size\":2}"
            + "}";
    public static String clustererJson = "["
            + "   [{\"v\":\"ab\",\"c\":1},{\"v\":\"abc\",\"c\":1}]"
            + "]";
    
    @BeforeTest
    public void registerClusterer() {
    	ClustererConfigFactory.register("knn", kNNClustererConfig.class);
    	DistanceFactory.put("ppm", new VicinoDistance(new PPMDistance()));
    }
    
    @Test
    public void serializekNNClustererConfig() throws JsonParseException, JsonMappingException, IOException {
        kNNClustererConfig config = ParsingUtilities.mapper.readValue(configJson, kNNClustererConfig.class);
        TestUtils.isSerializedTo(config, configJson, ParsingUtilities.defaultWriter);
    }
    
    @Test
    public void serializekNNClusterer() throws JsonParseException, JsonMappingException, IOException {
        GridState grid = createGrid(new String[] {"values"},
        		new Serializable[][] {
        	{ "ab" },
        	{ "abc" },
        	{ "c" },
        	{ "ĉ" }});
        
        kNNClustererConfig config = ParsingUtilities.mapper.readValue(configJson, kNNClustererConfig.class);
        kNNClusterer clusterer = config.apply(grid);
        clusterer.computeClusters(new Engine(grid, EngineConfig.ALL_ROWS));
        
        TestUtils.isSerializedTo(clusterer, clustererJson, ParsingUtilities.defaultWriter);
    }
    
    @Test
    public void testNoLonelyclusters() throws JsonParseException, JsonMappingException, IOException {
    	GridState grid = createGrid(new String[] {"values"},
    			new Serializable[][] {
    		{ "foo" },
    		{ "bar" }});
    	kNNClustererConfig config = ParsingUtilities.mapper.readValue(configJson, kNNClustererConfig.class);
    	kNNClusterer clusterer = config.apply(grid);
        clusterer.computeClusters(new Engine(grid, EngineConfig.ALL_ROWS));
    	
    	assertTrue(clusterer.getJsonRepresentation().isEmpty());
    }
}
