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

package org.openrefine.commands.expr;

import java.io.IOException;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openrefine.commands.Command;
import org.openrefine.expr.EvalError;
import org.openrefine.expr.Evaluable;
import org.openrefine.expr.ExpressionUtils;
import org.openrefine.expr.HasFields;
import org.openrefine.expr.MetaParser;
import org.openrefine.expr.ParsingException;
import org.openrefine.expr.WrappedCell;
import org.openrefine.expr.WrappedRow;
import org.openrefine.model.Cell;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.GridState;
import org.openrefine.model.IndexedRow;
import org.openrefine.model.Project;
import org.openrefine.model.Record;
import org.openrefine.model.Row;
import org.openrefine.util.ParsingUtilities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PreviewExpressionCommand extends Command {
    
    protected static interface ExpressionValue  { }
    protected static class ErrorMessage implements ExpressionValue {
        @JsonProperty("message")
        protected String message;
        public ErrorMessage(String m) {
            message = m;
        }
    }
    protected static class SuccessfulEvaluation implements ExpressionValue {
        @JsonIgnore
        protected String value;
        
        protected SuccessfulEvaluation(String value) {
            this.value = value;
        }
        
        @JsonValue
        protected String getValue() {
        	return value;
        }
    }
    
    protected static class PreviewResult  {
        @JsonProperty("code")
        protected String code;
        @JsonProperty("message")
        @JsonInclude(Include.NON_NULL)
        protected String message;
        @JsonProperty("type")
        @JsonInclude(Include.NON_NULL)
        protected String type;
        @JsonProperty("results")
        @JsonInclude(Include.NON_NULL)
        List<ExpressionValue> results; 
        
        public PreviewResult(String code, String message, String type) {
            this.code = code;
            this.message = message;
            this.type = type;
            this.results = null;
        }

        public PreviewResult(List<ExpressionValue> evaluated) {
            this.code = "ok";
            this.message = null;
            this.type = null;
            this.results = evaluated;
        }
    }
    /**
     * The command uses POST but does not actually modify any state so it does
     * not require CSRF.
     */
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        try {
            Project project = getProject(request);
            
            int cellIndex = Integer.parseInt(request.getParameter("cellIndex"));
            String columnName = cellIndex < 0 ? "" : project.getColumnModel().getColumns().get(cellIndex).getName();
            
            String expression = request.getParameter("expression");
            String rowIndicesString = request.getParameter("rowIndices");
            if (rowIndicesString == null) {
                respondJSON(response, new PreviewResult("error", "No row indices specified", null));
                return;
            }
            
            boolean repeat = "true".equals(request.getParameter("repeat"));
            int repeatCount = 10;
            if (repeat) {
                String repeatCountString = request.getParameter("repeatCount");
                try {
                    repeatCount = Math.max(Math.min(Integer.parseInt(repeatCountString), 10), 0);
                } catch (Exception e) {
                }
            }
            
            List<Long> rowIndices = ParsingUtilities.mapper.readValue(rowIndicesString, new TypeReference<List<Long>>() {});
            int length = rowIndices.size();
            GridState state = project.getCurrentGridState();
            ColumnModel columnModel = state.getColumnModel();
            
            try {
                Evaluable eval = MetaParser.parse(expression);
                
                List<ExpressionValue> evaluated = new ArrayList<>();
                Properties bindings = ExpressionUtils.createBindings();
                List<IndexedRow> rows = state.getRows(rowIndices);
                for (int i = 0; i < length; i++) {
                    Object result = null;
                    
                    Long rowIndex = rowIndices.get(i);  
                            
                    try {
                    	Row row = rows.get(i).getRow();
                        Cell cell = row.getCell(cellIndex);
                        Record record = null; // TODO enable records mode for this operation after changing the API
                        // to supply engineConfig and limit rather than rowIds
                        ExpressionUtils.bind(bindings, columnModel, row, rowIndex, record, columnName, cell);
                        result = eval.evaluate(bindings);
                        
                        if (repeat) {
                            for (int r = 0; r < repeatCount && ExpressionUtils.isStorable(result); r++) {
                                Cell newCell = new Cell((Serializable) result, (cell != null) ? cell.recon : null);
                                ExpressionUtils.bind(bindings, null, row, rowIndex, record, columnName, newCell);
                                
                                Object newResult = eval.evaluate(bindings);
                                if (ExpressionUtils.isError(newResult)) {
                                    break;
                                } else if (ExpressionUtils.sameValue(result, newResult)) {
                                    break;
                                } else {
                                    result = newResult;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    
                    if (result == null) {
                        evaluated.add(null);
                    } else if (ExpressionUtils.isError(result)) {
                        evaluated.add(new ErrorMessage(((EvalError) result).message));
                    } else {
                        StringBuffer sb = new StringBuffer();
                        
                        writeValue(sb, result, false);
                        
                        evaluated.add(new SuccessfulEvaluation(sb.toString()));
                    }
                }
                respondJSON(response, new PreviewResult(evaluated));
            } catch (ParsingException e) {
                respondJSON(response, new PreviewResult("error", e.getMessage(), "parser"));
            } catch (Exception e) {
                respondJSON(response, new PreviewResult("error", e.getMessage(), "other"));
            }
        } catch (Exception e) {
            respondException(response, e);
        }
    }
    
    static protected void writeValue(StringBuffer sb, Object v, boolean quote) {
        if (ExpressionUtils.isError(v)) {
            sb.append("[error: " + ((EvalError) v).message + "]");
        } else {
            if (v == null) {
                sb.append("null");
            } else {
                if (v instanceof WrappedCell) {
                    sb.append("[object Cell]");
                } else if (v instanceof WrappedRow) {
                    sb.append("[object Row]");
                } else if (v instanceof ObjectNode) {
                   sb.append(((ObjectNode) v).toString());
                } else if (v instanceof ArrayNode) {
                    sb.append(((ArrayNode) v).toString());
                } else if (ExpressionUtils.isArray(v)) {
                    Object[] a = (Object[]) v;
                    sb.append("[ ");
                    for (int i = 0; i < a.length; i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        writeValue(sb, a[i], true);
                    }
                    sb.append(" ]");
                } else if (ExpressionUtils.isArrayOrList(v)) {
                    List<Object> list = ExpressionUtils.toObjectList(v);
                    sb.append("[ ");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        writeValue(sb, list.get(i), true);
                    }
                    sb.append(" ]");
                } else if (v instanceof HasFields) {
                    sb.append("[object " + v.getClass().getSimpleName() + "]");
                } else if (v instanceof OffsetDateTime) {
                    sb.append("[date " + 
                            ParsingUtilities.dateToString((OffsetDateTime) v) +"]");
                } else if (v instanceof String) {
                    if (quote) {
                        try {
							sb.append(ParsingUtilities.mapper.writeValueAsString(((String) v)));
						} catch (JsonProcessingException e) {
							// will not happen
						}
                    } else {
                        sb.append((String) v);
                    }
                } else if (v instanceof Double || v instanceof Float) {
                    Number n = (Number) v;
                    if (n.doubleValue() - n.longValue() == 0.0) {
                        sb.append(n.longValue());
                    } else {
                        sb.append(n.doubleValue());
                    }
                } else {
                    sb.append(v.toString());
                }
            }
        }
    }
}
