
package org.openrefine.history;

import java.util.Arrays;

import org.openrefine.history.dag.DagSlice;
import org.openrefine.history.dag.OpaqueSlice;
import org.openrefine.model.ColumnMetadata;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.GridState;
import org.openrefine.model.changes.Change;
import org.openrefine.model.changes.ChangeContext;

public class ChangeStub implements Change {

    @Override
    public GridState apply(GridState projectState, ChangeContext context) {
        return projectState;
    }

    @Override
    public boolean isImmediate() {
        return false;
    }

    @Override
    public DagSlice getDagSlice() {
        return new OpaqueSlice(new ColumnModel(Arrays.asList(new ColumnMetadata("foo"))));
    }

}