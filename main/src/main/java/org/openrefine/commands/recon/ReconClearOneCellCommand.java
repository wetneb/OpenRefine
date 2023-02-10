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

package org.openrefine.commands.recon;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openrefine.commands.Command;
import org.openrefine.expr.ExpressionUtils;
import org.openrefine.history.HistoryEntry;
import org.openrefine.model.Cell;
import org.openrefine.model.ColumnMetadata;
import org.openrefine.model.Grid;
import org.openrefine.model.Project;
import org.openrefine.model.changes.Change;
import org.openrefine.model.changes.ReconCellChange;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReconClearOneCellCommand extends Command {

    protected static class CellResponse {

        @JsonProperty("code")
        protected String code = "ok";
        @JsonProperty("historyEntry")
        protected HistoryEntry entry;
        @JsonProperty("cell")
        Cell cell;

        protected CellResponse(HistoryEntry historyEntry, Cell newCell) {
            entry = historyEntry;
            cell = newCell;
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!hasValidCSRFToken(request)) {
            respondCSRFError(response);
            return;
        }

        try {
            Project project = getProject(request);

            int rowIndex = Integer.parseInt(request.getParameter("row"));
            int cellIndex = Integer.parseInt(request.getParameter("cell"));

            Grid state = project.getCurrentGrid();
            Cell cell = state.getRow(rowIndex).getCell(cellIndex);
            if (cell == null || !ExpressionUtils.isNonBlankData(cell.value)) {
                throw new Exception("Cell is blank or error");
            }

            ColumnMetadata column = state.getColumnModel().getColumnByIndex(cellIndex);
            if (column == null) {
                throw new Exception("No such column");
            }

            Cell newCell = new Cell(cell.value, null);

            String description = "Clear recon data for single cell on row " + (rowIndex + 1) +
                    ", column " + column.getName() +
                    ", containing \"" + cell.value + "\"";

            Change change = new ReconCellChange(rowIndex, column.getName(), null);

            HistoryEntry historyEntry = project.getHistory().addEntry(description, null, change);
            respondJSON(response, new CellResponse(historyEntry, newCell));
        } catch (Exception e) {
            respondException(response, e);
        }
    }
}
