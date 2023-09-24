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

package org.openrefine.operations.row;

import org.openrefine.browsing.EngineConfig;
import org.openrefine.model.GridState;
import org.openrefine.model.Row;
import org.openrefine.model.RowMapper;
import org.openrefine.model.changes.ChangeContext;
import org.openrefine.operations.ImmediateRowMapOperation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RowStarOperation extends ImmediateRowMapOperation {
    final protected boolean _starred;

    @JsonCreator
    public RowStarOperation(
            @JsonProperty("engineConfig")
            EngineConfig engineConfig,
            @JsonProperty("starred")
            boolean starred) {
        super(engineConfig);
        _starred = starred;
    }
    
    @JsonProperty("starred")
    public boolean getStarred() {
        return _starred;
    }

    @Override
    public String getDescription() {
        return (_starred ? "Star rows" : "Unstar rows");
    }


	@Override
	public RowMapper getPositiveRowMapper(GridState grid, ChangeContext context) {
		return rowMapper(_starred);
	}

    protected static RowMapper rowMapper(boolean starred) {
    	return new RowMapper() {

			private static final long serialVersionUID = 7358285487574613684L;

			@Override
			public Row call(long rowId, Row row) {
				return row.withStarred(starred);
			}
    		
    	};
    }
}
