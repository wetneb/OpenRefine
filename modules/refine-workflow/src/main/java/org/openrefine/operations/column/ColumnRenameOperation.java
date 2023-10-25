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

package org.openrefine.operations.column;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.openrefine.browsing.EngineConfig;
import org.openrefine.model.ColumnInsertion;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.RowInRecordMapper;
import org.openrefine.model.changes.ChangeContext;
import org.openrefine.operations.OperationDescription;
import org.openrefine.operations.RowMapOperation;
import org.openrefine.operations.exceptions.OperationException;
import org.openrefine.overlay.OverlayModel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ColumnRenameOperation extends RowMapOperation {

    final protected String _oldColumnName;
    final protected String _newColumnName;

    @JsonCreator
    public ColumnRenameOperation(
            @JsonProperty("oldColumnName") String oldColumnName,
            @JsonProperty("newColumnName") String newColumnName) {
        super(EngineConfig.ALL_ROWS);
        _oldColumnName = oldColumnName;
        _newColumnName = newColumnName;
    }

    @JsonProperty("oldColumnName")
    public String getOldColumnName() {
        return _oldColumnName;
    }

    @JsonProperty("newColumnName")
    public String getNewColumnName() {
        return _newColumnName;
    }

    @Override
    public String getDescription() {
        return OperationDescription.column_rename_brief(_oldColumnName, _newColumnName);
    }

    @Override
    public List<String> getColumnDependencies() {
        return Collections.emptyList();
    }

    @Override
    public List<ColumnInsertion> getColumnInsertions() {
        return Collections.singletonList(new ColumnInsertion(_newColumnName, _oldColumnName, true, _oldColumnName));
    }

    @Override
    protected RowInRecordMapper getPositiveRowMapper(ColumnModel columnModel, Map<String, OverlayModel> overlayModels,
            ChangeContext context) throws OperationException {
        return RowInRecordMapper.IDENTITY;
    }

    // engine config is never useful, so we remove it from the JSON serialization
    @Override
    @JsonIgnore
    public EngineConfig getEngineConfig() {
        return super.getEngineConfig();
    }
}
