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

package org.openrefine.model.recon;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.openrefine.expr.ExpressionUtils;
import org.openrefine.model.Cell;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.Row;
import org.openrefine.model.recon.Recon.Judgment;
import org.openrefine.model.Row;
import org.openrefine.util.HttpClient;
import org.openrefine.util.ParsingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class StandardReconConfig extends ReconConfig {

    private static final long serialVersionUID = 3641905651013923439L;

    final static Logger logger = LoggerFactory.getLogger("refine-standard-recon");

    private static final String DEFAULT_SCHEMA_SPACE = "http://localhost/schema";
    private static final String DEFAULT_IDENTIFIER_SPACE = "http://localhost/identifier";

    static public class ColumnDetail implements Serializable {

        private static final long serialVersionUID = -6532890579963639291L;

        @JsonProperty("column")
        final public String columnName;
        @JsonProperty("propertyName")
        final public String propertyName;
        @JsonProperty("propertyID")
        final public String propertyID;

        /**
         * Unfortunately the format of ColumnDetail is inconsistent in the UI and the backend so we need to support two
         * deserialization formats. See the tests for that.
         */
        @JsonCreator
        public ColumnDetail(
                @JsonProperty("column") String columnName,
                @JsonProperty("propertyName") String propertyName,
                @JsonProperty("propertyID") String propertyID,
                @JsonProperty("property") ReconType property) {
            this.columnName = columnName;
            this.propertyName = property == null ? propertyName : property.name;
            this.propertyID = property == null ? propertyID : property.id;
        }

        @Override
        public String toString() {
            try {
                return ParsingUtilities.mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return super.toString();
            }
        }
    }

    static public StandardReconConfig reconstruct(String json) throws IOException {
        return ParsingUtilities.mapper.readValue(json, StandardReconConfig.class);
    }

    static protected class StandardReconJob implements ReconJob {

        private final String cellValue;
        private final String jsonQuery;

        public StandardReconJob(
                String cellValue,
                String jsonQuery) {
            this.cellValue = cellValue;
            this.jsonQuery = jsonQuery;
        }

        @Override
        public String getCellValue() {
            return cellValue;
        }

        public String getJsonQuery() {
            return jsonQuery;
        }

        @Override
        public String toString() {
            return jsonQuery;
        }

        @Override
        public int hashCode() {
            return jsonQuery.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof StandardReconJob)) {
                return false;
            }
            StandardReconJob otherJob = (StandardReconJob) other;
            return cellValue.equals(otherJob.cellValue) && jsonQuery.equals(otherJob.jsonQuery);
        }
    }

    @JsonProperty("service")
    final public String service;
    @JsonProperty("identifierSpace")
    final public String identifierSpace;
    @JsonProperty("schemaSpace")
    final public String schemaSpace;

    @JsonIgnore
    final public String typeID;
    @JsonIgnore
    final public String typeName;
    @JsonProperty("autoMatch")
    final public boolean autoMatch;
    @JsonProperty("columnDetails")
    final public List<ColumnDetail> columnDetails;
    @JsonProperty("limit")
    final private int limit;

    // initialized lazily
    private HttpClient httpClient = null;

    @JsonCreator
    public StandardReconConfig(
            @JsonProperty("service") String service,
            @JsonProperty("identifierSpace") String identifierSpace,
            @JsonProperty("schemaSpace") String schemaSpace,
            @JsonProperty("type") ReconType type,
            @JsonProperty("autoMatch") boolean autoMatch,
            @JsonProperty("columnDetails") List<ColumnDetail> columnDetails,
            @JsonProperty("limit") int limit) {
        this(service, identifierSpace, schemaSpace,
                type != null ? type.id : null,
                type != null ? type.name : null,
                autoMatch, columnDetails, limit);
    }

    public StandardReconConfig(
            String service,
            String identifierSpace,
            String schemaSpace,

            String typeID,
            String typeName,
            boolean autoMatch,
            List<ColumnDetail> columnDetails) {
        this(service, identifierSpace, schemaSpace, typeID, typeName, autoMatch, columnDetails, 0);
    }

    /**
     * @param service
     * @param identifierSpace
     * @param schemaSpace
     * @param typeID
     * @param typeName
     * @param autoMatch
     * @param columnDetails
     * @param limit
     *            maximum number of results to return (0 = default)
     */
    public StandardReconConfig(
            String service,
            String identifierSpace,
            String schemaSpace,
            String typeID,
            String typeName,
            boolean autoMatch,
            List<ColumnDetail> columnDetails,
            int limit) {
        this.service = service;
        this.identifierSpace = identifierSpace != null ? identifierSpace : DEFAULT_IDENTIFIER_SPACE;
        this.schemaSpace = schemaSpace != null ? schemaSpace : DEFAULT_SCHEMA_SPACE;

        this.typeID = typeID;
        this.typeName = typeName;
        this.autoMatch = autoMatch;
        this.columnDetails = columnDetails;
        this.limit = limit;
    }

    @JsonProperty("type")
    @JsonInclude(Include.NON_NULL)
    public ReconType getReconType() {
        if (typeID != null) {
            return new ReconType(typeID, typeName);
        }
        return null;
    }

    @Override
    @JsonIgnore
    public int getBatchSize() {
        return 10;
    }

    @Override
    public String getBriefDescription(String columnName) {
        return "Reconcile cells in column " + columnName + " to type " + typeID;
    }

    public ReconJob createSimpleJob(String query) {
        /*
         * Same as createJob, but for simpler queries without any properties. This is much easier to generate as there
         * is no need for a Project, Row and Cell: this means the job can be created outside the usual context of
         * reconciliation (e.g. in an importer).
         */
        try {
            String queryJson = ParsingUtilities.defaultWriter.writeValueAsString(
                    Collections.singletonMap("query", query));
            return new StandardReconJob(query, queryJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected static class QueryProperty {

        @JsonProperty("pid")
        String pid;
        @JsonProperty("v")
        Object v;

        protected QueryProperty(
                String pid,
                Object v) {
            this.pid = pid;
            this.v = v;
        }

        @Override
        public String toString() {
            try {
                return ParsingUtilities.mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return super.toString();
            }
        }
    }

    protected static class ReconQuery {

        @JsonProperty("query")
        protected String query;

        @JsonProperty("type")
        @JsonInclude(Include.NON_NULL)
        protected String typeID;

        @JsonProperty("type_strict")
        @JsonInclude(Include.NON_NULL)
        public String isTypeStrict() {
            if (typeID != null) {
                return "should";
            }
            return null;
        }

        @JsonProperty("properties")
        @JsonInclude(Include.NON_EMPTY)
        protected List<QueryProperty> properties;

        // Only send limit if it's non-default (default = 0) to preserve backward
        // compatibility with services which might choke on this (pre-2013)
        @JsonIgnore
        protected int limit;

        public ReconQuery() {
            super();
            this.query = "";
            this.typeID = null;
            this.properties = null;
            this.limit = 0;
        }

        @JsonCreator
        public ReconQuery(
                String query,
                String typeID,
                List<QueryProperty> properties,
                int limit) {
            this.query = query;
            this.typeID = typeID;
            this.properties = properties;
            this.limit = limit;
        }

        @Override
        public String toString() {
            try {
                return ParsingUtilities.mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return super.toString();
            }
        }

        @JsonProperty("limit")
        @JsonInclude(Include.NON_NULL)
        public Integer getLimit() {
            return limit == 0 ? null : limit;
        }
    }

    public static class ReconResult {

        @JsonProperty("name")
        public String name;
        @JsonProperty("id")
        public String id;
        @JsonProperty("type")
        public List<ReconType> types = Collections.emptyList();
        @JsonProperty("score")
        public double score;
        @JsonProperty("match")
        public boolean match = false;

        @JsonIgnore
        public ReconCandidate toCandidate() {
            String[] bareTypes = new String[types.size()];
            for (int i = 0; i != types.size(); i++) {
                bareTypes[i] = types.get(i).id;
            }
            ReconCandidate result = new ReconCandidate(id, name, bareTypes, score);

            return result;
        }

        @Override
        public String toString() {
            try {
                return ParsingUtilities.mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return super.toString();
            }
        }
    }

    @Override
    public ReconJob createJob(ColumnModel columnModel, long rowIndex, Row row,
            String columnName, Cell cell) {

        List<QueryProperty> properties = new ArrayList<>();

        for (ColumnDetail c : columnDetails) {
            int detailCellIndex = columnModel.getColumnIndexByName(c.columnName);

            Cell cell2 = row.getCell(detailCellIndex);
            // The following is no longer possible in rows mode. Users
            // should first fill down their columns before reconciling.

            /*
             * if (cell2 == null || !ExpressionUtils.isNonBlankData(cell2.value)) { int cellIndex =
             * columnModel.getColumnByName(columnName).getCellIndex();
             * 
             * RowDependency rd = project.recordModel.getRowDependency(rowIndex); if (rd != null && rd.cellDependencies
             * != null) { int contextRowIndex = rd.cellDependencies[cellIndex].rowIndex; if (contextRowIndex >= 0 &&
             * contextRowIndex < project.rows.size()) { Row row2 = project.rows.get(contextRowIndex);
             * 
             * cell2 = row2.getCell(detailCellIndex); } } }
             */

            if (cell2 != null && ExpressionUtils.isNonBlankData(cell2.value)) {
                Object v = null;
                if (cell2.recon != null && cell2.recon.match != null) {
                    Map<String, String> recon = new HashMap<>();
                    recon.put("id", cell2.recon.match.id);
                    recon.put("name", cell2.recon.match.name);
                    v = recon;
                } else {
                    v = cell2.value;
                }
                properties.add(new QueryProperty(c.propertyID, v));
            }

        }

        ReconQuery query = new ReconQuery(cell.value.toString(), typeID, properties, limit);

        String cellValue = cell.value.toString();
        try {
            String jsonCode = ParsingUtilities.defaultWriter.writeValueAsString(query);
            return new StandardReconJob(cellValue, jsonCode);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialization of reconciliation query failed", e);
        }
    }

    protected HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new HttpClient();
        }
        return httpClient;
    }

    private String postQueries(String url, String queriesString) throws IOException {
        try {
            return getHttpClient().postNameValue(url, "queries", queriesString);

        } catch (IOException e) {
            throw new IOException("Failed to batch recon with load:\n" + queriesString, e);
        }
    }

    @Override
    public List<Recon> batchRecon(List<ReconJob> jobs, long historyEntryID) {
        List<Recon> recons = new ArrayList<Recon>(jobs.size());

        StringWriter stringWriter = new StringWriter();

        stringWriter.write("{");
        for (int i = 0; i < jobs.size(); i++) {
            StandardReconJob job = (StandardReconJob) jobs.get(i);
            if (i > 0) {
                stringWriter.write(",");
            }
            stringWriter.write("\"q" + i + "\":");
            stringWriter.write(job.getJsonQuery());
        }
        stringWriter.write("}");
        String queriesString = stringWriter.toString();

        try {
            String responseString = postQueries(service, queriesString);
            ObjectNode o = ParsingUtilities.evaluateJsonStringToObjectNode(responseString);

            if (o == null) { // utility method returns null instead of throwing
                logger.error("Failed to parse string as JSON: " + responseString);
            } else {
                for (int i = 0; i < jobs.size(); i++) {
                    StandardReconJob job = (StandardReconJob) jobs.get(i);
                    Recon recon = null;

                    String text = job.getCellValue();
                    String key = "q" + i;
                    if (o.has(key) && o.get(key) instanceof ObjectNode) {
                        ObjectNode o2 = (ObjectNode) o.get(key);
                        if (o2.has("result") && o2.get("result") instanceof ArrayNode) {
                            ArrayNode results = (ArrayNode) o2.get("result");

                            recon = createReconServiceResults(text, results, historyEntryID);
                        } else {
                            // TODO: better error reporting
                            logger.warn("Service error for text: " + text + "\n  Job code: " + job.getJsonQuery() + "\n  Response: "
                                    + o2.toString());
                        }
                    } else {
                        // TODO: better error reporting
                        logger.warn(
                                "Service error for text: " + text + "\n  Job code: " + job.getJsonQuery() + "\n Response: " + o.toString());
                    }

                    if (recon != null) {
                        recon = recon.withService(service);
                    }
                    recons.add(recon);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to batch recon with load:\n" + queriesString, e);
        }

        while (recons.size() < jobs.size()) {
            recons.add(null);
        }

        return recons;
    }

    @Override
    public Recon createNewRecon(long historyEntryID) {
        Recon recon = new Recon(historyEntryID, identifierSpace, schemaSpace)
                .withService(service);
        return recon;
    }

    protected Recon createReconServiceResults(String text, ArrayNode resultsList, long historyEntryID) {
        Recon recon = new Recon(historyEntryID, identifierSpace, schemaSpace);
        List<ReconResult> results = ParsingUtilities.mapper.convertValue(resultsList, new TypeReference<List<ReconResult>>() {
        });

        // Sort results by decreasing score
        results.sort(new Comparator<ReconResult>() {

            @Override
            public int compare(ReconResult a, ReconResult b) {
                return Double.compare(b.score, a.score);
            }
        });

        int length = results.size();
        for (int i = 0; i < length; i++) {
            ReconResult result = results.get(i);

            ReconCandidate candidate = result.toCandidate();

            if (autoMatch && i == 0 && result.match) {
                recon = recon.withMatch(candidate)
                        .withMatchRank(0)
                        .withJudgment(Judgment.Matched)
                        .withJudgmentAction("auto");
            }

            recon = recon.withCandidate(candidate);
        }

        recon = computeFeatures(recon, text);
        return recon;
    }

    /**
     * Recomputes the features associated with this reconciliation object (only if we have at least one candidate).
     * 
     * @param text
     *            the cell value to compare the reconciliation data to
     */
    public Recon computeFeatures(Recon recon, String text) {
        Object[] features = new Object[Recon.Feature_max];
        if (recon.candidates != null && !recon.candidates.isEmpty() && text != null) {
            ReconCandidate candidate = recon.candidates.get(0);

            if (candidate.name != null) {
                features[Recon.Feature_nameMatch] = text.equalsIgnoreCase(candidate.name);
                features[Recon.Feature_nameLevenshtein] = StringUtils.getLevenshteinDistance(StringUtils.lowerCase(text),
                        StringUtils.lowerCase(candidate.name));
                features[Recon.Feature_nameWordDistance] = wordDistance(text, candidate.name);
            }

            features[Recon.Feature_typeMatch] = false;
            if (this.typeID != null) {
                for (String typeID : candidate.types) {
                    if (this.typeID.equals(typeID)) {
                        features[Recon.Feature_typeMatch] = true;
                        break;
                    }
                }
            }
        }
        return recon.withFeatures(features);
    }

    static protected double wordDistance(String s1, String s2) {
        Set<String> words1 = breakWords(s1);
        Set<String> words2 = breakWords(s2);
        return words1.size() >= words2.size() ? wordDistance(words1, words2) : wordDistance(words2, words1);
    }

    static protected double wordDistance(Set<String> longWords, Set<String> shortWords) {
        if (longWords.size() == 0) {
            return 0.0;
        }

        double common = 0;
        for (String word : shortWords) {
            if (longWords.contains(word)) {
                common++;
            }
        }
        return common / longWords.size();
    }

    static final protected Set<String> s_stopWords = new HashSet<String>();
    static {
        // FIXME: This is English specific
        s_stopWords.add("the");
        s_stopWords.add("a");
        s_stopWords.add("and");
        s_stopWords.add("of");
        s_stopWords.add("on");
        s_stopWords.add("in");
        s_stopWords.add("at");
        s_stopWords.add("by");
    }

    static protected Set<String> breakWords(String s) {
        String[] words = s.toLowerCase().split("\\s+");

        Set<String> set = new HashSet<String>(words.length);
        for (String word : words) {
            if (!s_stopWords.contains(word)) {
                set.add(word);
            }
        }
        return set;
    }

    @Override
    public String getMode() {
        return "standard-service";
    }

    public boolean equals(Object other) {
        if (!(other instanceof StandardReconConfig)) {
            return false;
        }
        StandardReconConfig otherConfig = (StandardReconConfig) other;
        return (service.equals(otherConfig.service) &&
                identifierSpace.equals(otherConfig.identifierSpace) &&
                schemaSpace.equals(otherConfig.schemaSpace) &&
                ((typeID == null && otherConfig.typeID == null) || typeID.equals(otherConfig.typeID)) &&
                ((typeName == null && otherConfig.typeName == null) || typeName.equals(otherConfig.typeName)) &&
                autoMatch == otherConfig.autoMatch &&
                columnDetails.equals(otherConfig.columnDetails) &&
                limit == otherConfig.limit);
    }

    public int hashCode() {
        return Objects.hash(service, columnDetails, typeID);
    }
}