
package com.google.refine.operations;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.testng.annotations.Test;

import com.google.refine.model.AbstractOperation;
import com.google.refine.model.ColumnsDiff;
import com.google.refine.util.ParsingUtilities;
import com.google.refine.util.TestUtils;

public class RecipeTests {

    String json = "[{\"op\":\"unknown\",\"description\":\"some operation\"}]";

    // Sample test operations
    class ColumnRemovalOperation extends AbstractOperation {

        final String columnName;

        public ColumnRemovalOperation(String columnName) {
            this.columnName = columnName;
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.of(Set.of(columnName));
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.of(ColumnsDiff.builder().deleteColumn(columnName).build());
        }
    }

    class ColumnRenameOperation extends AbstractOperation {

        final String oldName;
        final String newName;

        public ColumnRenameOperation(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.of(Set.of(oldName));
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.of(ColumnsDiff.builder().deleteColumn(oldName).addColumn(newName, oldName).build());
        }
    }

    class ColumnSplitOperation extends AbstractOperation {

        final String columnName;

        public ColumnSplitOperation(String columnName) {
            this.columnName = columnName;
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.of(Set.of(columnName));
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.empty();
        }
    }

    class ColumnTransformOperation extends AbstractOperation {

        final String columnName;

        public ColumnTransformOperation(String columnName) {
            this.columnName = columnName;
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.of(Set.of(columnName));
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.of(ColumnsDiff.modifySingleColumn(columnName));
        }
    }

    class OpaqueOperation extends AbstractOperation {

        OpaqueOperation() {
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.empty();
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.empty();
        }
    }

    @Test
    public void testDeserialize() throws Exception {
        Recipe recipe = ParsingUtilities.mapper.readValue(json, Recipe.class);

        assertEquals(recipe.getOperations().size(), 1);
        assertEquals(recipe.getOperations().get(0).getOperationId(), "unknown");

        TestUtils.isSerializedTo(recipe, json);
    }

    @Test
    public void testValidate() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(List.of(
                new UnknownOperation("some-operation", "Some description"))).validate());

        assertThrows(IllegalArgumentException.class, () -> new Recipe(Collections.singletonList(null)).validate());

        new Recipe(List.of(
                new ColumnRemovalOperation("foo"))).validate();
    }

    @Test
    public void testComputeRequiredColumns() throws Exception {
        assertEquals(
                new Recipe(List.of()).computeRequiredColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRemovalOperation("foo"))).computeRequiredColumns(),
                Set.of("foo"));

        assertEquals(
                new Recipe(List.of(
                        new ColumnRemovalOperation("foo"),
                        new ColumnRemovalOperation("bar"))).computeRequiredColumns(),
                Set.of("foo", "bar"));

        assertEquals(
                new Recipe(List.of(
                        new ColumnRenameOperation("foo", "foo2"),
                        new ColumnRemovalOperation("bar"))).computeRequiredColumns(),
                Set.of("foo", "bar"));

        assertEquals(
                new Recipe(List.of(
                        new ColumnRenameOperation("foo", "foo2"),
                        new ColumnSplitOperation("foo2"),
                        // The dependency of the following operation is not taken into account,
                        // because the previous operation does not expose a columns diff,
                        // so we can't predict if "bar" is going to be produced by it or not.
                        new ColumnRemovalOperation("bar"))).computeRequiredColumns(),
                Set.of("foo"));

        assertEquals(
                new Recipe(List.of(
                        new ColumnTransformOperation("foo"),
                        new ColumnRemovalOperation("foo"))).getRequiredColumns(),
                Set.of("foo"));

        // unanalyzable operation
        assertEquals(
                new Recipe(List.of(
                        new OpaqueOperation())).computeRequiredColumns(),
                Set.of());
    }

    @Test
    public void testRequiredColumnsFromInconsistentOperations() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(List.of(
                new ColumnRemovalOperation("foo"),
                new ColumnRenameOperation("foo", "bar"))).computeRequiredColumns());
    }

    @Test
    public void testConflictingColumnCreation() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(List.of(
                new ColumnTransformOperation("bar"),
                new ColumnRenameOperation("foo", "bar"))).getRequiredColumns());
    }

}
