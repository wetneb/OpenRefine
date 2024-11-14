
package com.google.refine.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.Validate;

/**
 * Represents the changes made by an operation to the set of columns in a project.
 */
public class ColumnsDiff {

    private final Set<String> addedColumns;
    private final Set<String> deletedColumns;

    private final static ColumnsDiff empty = new ColumnsDiff(Set.of(), Set.of());

    /**
     * An empty diff, for when columns don't change at all.
     */
    public static ColumnsDiff empty() {
        return empty;
    }

    /**
     * A fresh builder object.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructor. Consider using {@link Builder} instead.
     * 
     * @param addedColumns
     *            the set of column names that appear after this operation
     * @param deletedColumns
     *            the set of column names that disappear after this operation
     */
    public ColumnsDiff(Set<String> addedColumns, Set<String> deletedColumns) {
        this.addedColumns = addedColumns;
        this.deletedColumns = deletedColumns;
    }

    /**
     * The columns names that were absent before and present after the operation. This includes the new names of any
     * renamed columns.
     */
    public Set<String> getAddedColumns() {
        return addedColumns;
    }

    /**
     * The columns names that were present before and absent after the operation. This includes the old names of any
     * renamed columns.
     */
    public Set<String> getDeletedColumns() {
        return deletedColumns;
    }

    public static class Builder {

        private final Set<String> added = new HashSet<>();
        private final Set<String> deleted = new HashSet<>();
        private boolean built = false;

        public Builder addColumn(String columnName) {
            added.add(columnName);
            return this;
        }

        public Builder deleteColumn(String columnName) {
            deleted.add(columnName);
            return this;
        }

        public ColumnsDiff build() {
            Validate.isTrue(!built, "The ColumnsDiff was already built");
            built = true;
            return new ColumnsDiff(added, deleted);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(addedColumns, deletedColumns);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ColumnsDiff other = (ColumnsDiff) obj;
        return Objects.equals(addedColumns, other.addedColumns) && Objects.equals(deletedColumns, other.deletedColumns);
    }

    @Override
    public String toString() {
        return "ColumnsDiff [addedColumns=" + addedColumns + ", deletedColumns=" + deletedColumns + "]";
    }

}
