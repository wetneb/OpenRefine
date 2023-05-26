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

package org.openrefine.history;

import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.NotImplementedException;
import org.openrefine.ProjectManager;
import org.openrefine.ProjectManagerStub;
import org.openrefine.browsing.facets.Facet;
import org.openrefine.browsing.facets.FacetConfig;
import org.openrefine.browsing.facets.FacetConfigResolver;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.Runner;
import org.openrefine.operations.OperationRegistry;
import org.openrefine.overlay.OverlayModel;
import org.openrefine.util.ParsingUtilities;
import org.openrefine.util.TestUtils;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class HistoryEntryTests {

    public static final String fullJson = "{"
            + "\"id\":1533633623158,"
            + "\"description\":\"some description\","
            + "\"time\":\"2018-08-07T09:06:37Z\","
            + "\"gridPreservation\":\"preserves-rows\",\n"
            + "\"operation\":{\"op\":\"core/operation-stub\","
            + "   \"description\":\"some description\"}"
            + "}";

    public static final String unknownOperationJson = "{\n" +
            "  \"description\" : \"some mysterious operation\",\n" +
            "  \"id\" : 1533633623158,\n" +
            "\"gridPreservation\":\"preserves-rows\",\n" +
            "  \"operation\" : {\n" +
            "    \"description\" : \"some mysterious operation\",\n" +
            "    \"op\" : \"someextension/unknown-operation\",\n" +
            "    \"some_parameter\" : 234\n" +
            "  },\n" +
            "  \"time\" : \"2018-08-07T09:06:37Z\"\n" +
            "}";

    public static final String historyEntryWithCreatedFacets = "    {\n" +
            "      \"id\": 1683271793411,\n" +
            "      \"description\": \"operation stub\",\n" +
            "      \"operation\": {" +
            "           \"op\": \"core/my-operation-with-facets\"," +
            "           \"description\" : \"operation stub\"" +
            "       },\n" +
            "      \"gridPreservation\": \"preserves-rows\",\n" +
            "      \"time\": \"2023-05-05T07:27:41Z\",\n" +
            "      \"createdFacets\": [\n" +
            "        {\n" +
            "          \"type\": \"core/myfacet\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }";

    @BeforeTest
    public void register() {
        OperationRegistry.registerOperation("core", "operation-stub", OperationStub.class);
        OperationRegistry.registerOperation("core", "my-operation-with-facets", OperationStubWithFacets.class);

        ProjectManager.singleton = new ProjectManagerStub(mock(Runner.class));
    }

    @Test
    public void serializeHistoryEntry() throws Exception {
        String jsonSimple = "{"
                + "\"id\":1533633623158,"
                + "\"description\":\"some description\","
                + "\"time\":\"2018-08-07T09:06:37Z\","
                + "\"gridPreservation\":\"preserves-rows\"}";

        HistoryEntry historyEntry = HistoryEntry.load(fullJson);
        TestUtils.isSerializedTo(historyEntry, jsonSimple, ParsingUtilities.defaultWriter);
        TestUtils.isSerializedTo(historyEntry, fullJson, ParsingUtilities.saveWriter);
    }

    @Test
    public void deserializeUnknownOperation() throws IOException {
        // Unknown operations are serialized back as they were parsed
        HistoryEntry entry = HistoryEntry.load(unknownOperationJson);
        TestUtils.isSerializedTo(entry, unknownOperationJson, ParsingUtilities.saveWriter);
    }

    @Test
    public void deserializeCreatedFacets() throws IOException {
        // for https://github.com/FasterXML/jackson-databind/issues/2692
        FacetConfigResolver.registerFacetConfig("core", "myfacet", MyFacetConfig.class);
        HistoryEntry entry = HistoryEntry.load(historyEntryWithCreatedFacets);
        TestUtils.isSerializedTo(entry, historyEntryWithCreatedFacets, ParsingUtilities.saveWriter);
    }

    protected static class MyFacetConfig implements FacetConfig {

        @Override
        public Facet apply(ColumnModel columnModel, Map<String, OverlayModel> overlayModels, long projectId) {
            throw new NotImplementedException();
        }

        @Override
        public Set<String> getColumnDependencies() {
            throw new NotImplementedException();
        }

        @Override
        public FacetConfig renameColumnDependencies(Map<String, String> substitutions) {
            throw new NotImplementedException();
        }

        @Override
        public boolean isNeutral() {
            return true;
        }

        @Override
        public String getJsonType() {
            return "core/myfacet";
        }
    }
}
