package org.openrefine.model.changes;

import java.util.Objects;

/**
 * A pair of a history entry id and a string identifier for the change data in it.
 */
public class ChangeDataId {

    private final long historyEntryId;
    private final String changeDataId;

    public ChangeDataId(long historyEntryId, String changeDataId) {
        this.historyEntryId = historyEntryId;
        this.changeDataId = changeDataId;
    }

    public long getHistoryEntryId() {
        return historyEntryId;
    }

    public String getChangeDataId() {
        return changeDataId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChangeDataId that = (ChangeDataId) o;
        return historyEntryId == that.historyEntryId && Objects.equals(changeDataId, that.changeDataId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(historyEntryId, changeDataId);
    }

    @Override
    public String toString() {
        return "ChangeDataId{" +
                "historyEntryId=" + historyEntryId +
                ", changeDataId='" + changeDataId + '\'' +
                '}';
    }
}
