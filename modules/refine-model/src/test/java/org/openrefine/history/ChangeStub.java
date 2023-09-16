
package org.openrefine.history;

import java.util.Arrays;

import org.openrefine.model.ColumnMetadata;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.Grid;
import org.openrefine.model.changes.Change;
import org.openrefine.model.changes.ChangeContext;

public class ChangeStub implements Change {

    @Override
    public ChangeResult apply(Grid projectState, ChangeContext context) {
        return new ChangeResult(
                projectState,
                GridPreservation.NO_ROW_PRESERVATION);
    }

    @Override
    public boolean isImmediate() {
        return false;
    }

}
