package org.openrefine.model;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openrefine.browsing.facets.RecordAggregator;
import org.openrefine.browsing.facets.RowAggregator;
import org.openrefine.model.changes.ChangeData;
import org.openrefine.model.changes.RecordChangeDataJoiner;
import org.openrefine.model.changes.RecordChangeDataProducer;
import org.openrefine.model.changes.RowChangeDataFlatJoiner;
import org.openrefine.model.changes.RowChangeDataJoiner;
import org.openrefine.model.changes.RowChangeDataProducer;
import org.openrefine.overlay.OverlayModel;
import org.openrefine.overlay.OverlayModelResolver;
import org.openrefine.sorting.SortingConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * Immutable object which represents the state of the project grid
 * at a given point in a workflow.
 */
public interface GridState {
    
    final static public String METADATA_PATH = "metadata.json";
    final static public String GRID_PATH = "grid";
    
    /**
     * @return the runner which created this grid state
     */
    public DatamodelRunner getDatamodelRunner();
    
    /**
     * @return the column metadata at this stage of the workflow
     */
    @JsonProperty("columnModel")
    public ColumnModel getColumnModel();
    
    /**
     * @param newColumnModel the column model to apply to the grid
     * @return a copy of this grid state with a modified column model.
     */
    public GridState withColumnModel(ColumnModel newColumnModel);
 
    /**
     * Returns a row by index. Repeatedly calling this method to obtain multiple rows
     * might be inefficient compared to fetching them by batch, depending on the implementation.
     * 
     * @param id the row index
     * @return the row at the given index
     * @throws IndexOutOfBoundsException if row id could not be found
     */
    public Row getRow(long id);
    
    /**
     * Returns a list of rows, starting from a given index and defined by a maximum
     * size.
     * 
     * @param start the first row id to fetch (inclusive)
     * @param limit the maximum number of rows to fetch
     * @return the list of rows with their ids (if any)
     */
    public List<IndexedRow> getRows(long start, int limit);
    
    /**
     * Returns a list of rows corresponding to the row indices supplied.
     * By default this calls {@link getRow(long)} on all values, but implementations
     * can override this to more efficient strategies if available.
     * 
     * @param rowIndices the indices of the rows to lookup
     * @return the list contains null values for the row indices which could not be
     * found.
     */
    public default List<IndexedRow> getRows(List<Long> rowIndices) {
        List<IndexedRow> result = new ArrayList<>(rowIndices.size());
        for (long rowId : rowIndices) {
            try {
                result.add(new IndexedRow(rowId, getRow(rowId)));
            } catch(IndexOutOfBoundsException e) {
                result.add(null);
            }
        }
        return result;
    }
    
    /**
     * Among the subset of filtered rows, return a list of rows,
     * starting from a given index and defined by a maximum size.
     * 
     * @param filter the subset of rows to paginate through.
     *               This object and its dependencies are required
     *               to be serializable.
     * @param sortingConfig the order in which to return the rows
     * @param start the first row id to fetch (inclusive)
     * @param limit the maximum number of rows to fetch
     * @return the list of rows with their ids (if any)
     */
    public List<IndexedRow> getRows(RowFilter filter, SortingConfig sortingConfig, long start, int limit);
    
    /**
     * Iterate over rows matched by a filter, in the order determined by
     * a sorting configuration. This might not require loading all rows
     * in memory at once, but might be less efficient than {@link collectRows()}
     * if all rows are to be stored in memory downstream.
     */
    public Iterable<IndexedRow> iterateRows(RowFilter filter, SortingConfig sortingConfig);
    
    /**
     * Count the number of rows which match a given filter.
     * 
     * @param filter the row filter
     * @return the number of rows for which this filter returns true
     */
    public long countMatchingRows(RowFilter filter);
    
    /**
     * Return the number of rows matching the given row filter, 
     * but by processing at most a fixed number of row.
     * 
     * @param filter counts the number of records on which it returns true
     * @param limit maximum number of records to process
     * @return
     */
    public ApproxCount countMatchingRowsApprox(RowFilter filter, long limit);
    
    /**
     * Returns all rows in a list. This is inefficient
     * for large datasets as it forces the entire grid to be loaded in 
     * memory.
     */
    public List<IndexedRow> collectRows();

    /**
     * Returns a record obtained by its id. Repeatedly calling this method to obtain
     * multiple records might be inefficient depending on the implementation.
     * 
     * @param id the row id of the first row in the record
     * @return the corresponding record
     * @throws IllegalArgumentException if record id could not be found
     */
    public Record getRecord(long id);
    
    /**
     * Returns a list of records, starting from a given index and defined by a maximum
     * size.
     * 
     * @param start the first record id to fetch (inclusive)
     * @param limit the maximum number of records to fetch
     * @return the list of records (if any)
     */
    public List<Record> getRecords(long start, int limit);
 
    /**
     * Among the filtered subset of records, returns a list of records,
     * starting from a given index and defined by a maximum size.
     * 
     * @param filter the filter which defines the subset of records to paginate through
     *               This object and its dependencies are required
     *               to be serializable.
     * @param sortingConfig the order in which the rows should be returned
     * @param start the first record id to fetch (inclusive)
     * @param limit the maximum number of records to fetch
     * @return the list of records (if any)
     */
    public List<Record> getRecords(RecordFilter filter, SortingConfig sortingConfig, long start, int limit);
    
    /**
     * Iterate over records matched by a filter, ordered according to the sorting configuration.
     * This might not require loading all records in memory at once, but might be less efficient
     * than {@link collectRecords()} if all records are to be stored in memory downstream.
     */
    public Iterable<Record> iterateRecords(RecordFilter filter, SortingConfig sortingConfig);

    /**
     * Return the number of records which are filtered by this filter.
     * 
     * @param filter the filter to evaluate
     * @return the number of records for which this filter evaluates to true
     */
    public long countMatchingRecords(RecordFilter filter);
    
    /**
     * Return the number of records matching the given record filter, 
     * but by processing at most a fixed number of records.
     * 
     * @param filter counts the number of records on which it returns true
     * @param limit maximum number of records to process
     * @return
     */
    public ApproxCount countMatchingRecordsApprox(RecordFilter filter, long limit);
    
    /**
     * Returns all records in a list. This is inefficient for large datasets
     * as it forces all records to be loaded in memory.
     */
    public List<Record> collectRecords();
    
    /**
     * @return the number of rows in the table
     */
    @JsonProperty("rowCount")
    public long rowCount();
    
    /**
     * @return the number of records in the table
     */
    @JsonProperty("recordCount")
    public long recordCount();
    
    /**
     * @return the overlay models in this state
     */
    @JsonProperty("overlayModels")
    public Map<String, OverlayModel> getOverlayModels();

    /**
     * Saves the grid state to a specified directory,
     * following OpenRefine's format for grid storage.
     * 
     * @param file the directory where to save the grid state
     * @throws IOException
     */
    public void saveToFile(File file) throws IOException;
    
    // Aggregations

    /**
     * Computes the result of a row aggregator on the grid.
     */
    public <T extends Serializable> T aggregateRows(RowAggregator<T> aggregator, T initialState);
    
    /**
     * Computes the result of a row aggregator on the grid.
     */
    public <T extends Serializable> T aggregateRecords(RecordAggregator<T> aggregator, T initialState);
    
    /**
     * Computes the result of a row aggregator on the grid,
     * reading at most a fixed number of rows.
     * The rows read should be deterministic for a given implementation.
     */
    public <T extends Serializable> T aggregateRowsApprox(RowAggregator<T> aggregator, T initialState, long maxRows);
    
    /**
     * Computes the result of a row aggregator on the grid,
     * reading at most a fixed number of records.
     * The records read should be deterministic for a given implementation.
     */
    public <T extends Serializable> T aggregateRecordsApprox(RecordAggregator<T> aggregator, T initialState, long maxRecords);

    // Transformations
    
    /**
     * Returns a new grid state where the overlay models have changed.
     * @param overlayModel the new overlay models to apply to the grid state
     * @return the changed grid state
     */
    public GridState withOverlayModels(Map<String, OverlayModel> overlayModel);
    
    /**
     * Returns a new grid state, where the rows have been mapped by the mapper.
     * 
     * @param mapper the function used to transform rows
     *               This object and its dependencies are required
     *               to be serializable.
     * @param newColumnModel the column model of the resulting grid state
     * @return the resulting grid state
     */
    public GridState mapRows(RowMapper mapper, ColumnModel newColumnModel);
    
    /**
     * Returns a new grid state, where the rows have been mapped by the flat mapper.
     * 
     * @param mapper the function used to transform rows
     *               This object and its dependencies are required
     *               to be serializable.
     * @param newColumnModel the column model of the resulting grid state
     * @return the resulting grid state
     */
    public GridState flatMapRows(RowFlatMapper mapper, ColumnModel newColumnModel);
    
    /**
     * Returns a new grid state where the rows have been mapped by the
     * stateful mapper. This can be significantly less efficient than a
     * stateless mapper, so only use this if you really need to rely on state.
     * 
     * @param <S> the type of state kept by the mapper
     * @param mapper the mapper to apply to the grid
     * @param newColumnModel the column model to apply to the new grid
     * @return
     */
    public <S extends Serializable> GridState mapRows(RowScanMapper<S> mapper, ColumnModel newColumnModel);
    
    /**
     * Returns a new grid state, where the records have been mapped by the mapper
     * 
     * @param filter the subset of records to which the mapper should be applied.
     *               This object and its dependencies are required
     *               to be serializable.
     * @param mapper the function used to transform records
     *               This object and its dependencies are required
     *               to be serializable.
     * @param newColumnModel the column model of the resulting grid state
     * @return the resulting grid state
     */
    public GridState mapRecords(RecordMapper mapper, ColumnModel newColumnModel);
    
    /**
     * Returns a new grid state where rows have been reordered according
     * to the configuration supplied.
     * 
     * @param sortingConfig the criteria to sort rows
     * @return the resulting grid state
     */
    public GridState reorderRows(SortingConfig sortingConfig);
    
    /**
     * Returns a new grid state where records have been reordered according
     * to the configuration supplied.
     * 
     * @param sortingConfig the criteria to sort records
     * @return the resulting grid state
     */
    public GridState reorderRecords(SortingConfig sortingConfig);
    
    /**
     * Removes all rows selected by a filter
     * 
     * @param filter which returns true when we should delete the row
     * @return the grid where the matching rows have been removed
     */
    public GridState removeRows(RowFilter filter);
    
    /**
     * Removes all records selected by a filter
     * 
     * @param filter which returns true when we should delete the record
     * @return the grid where the matching record have been removed
     */
    public GridState removeRecords(RecordFilter filter);
    
    /**
     * Only keep the first rows.
     * 
     * By default, this uses {@link GridState.removeRows}
     * to remove the last rows, but implementations can override
     * this for efficiency.
     * 
     * @param rowLimit the number of rows to keep
     * @return the limited grid
     */
    public default GridState limitRows(long rowLimit) {
        return removeRows(RowFilter.limitFilter(rowLimit));
    }
    
    /**
     * Drop the first rows.
     * 
     * By default, this uses {@link GridState.removeRows}
     * to remove the first rows, but implementations can override
     * this for efficiency.
     * 
     * @param rowsToDrop the number of rows to drop
     * @return the grid consisting of the last rows
     */
    public default GridState dropRows(long rowsToDrop) {
        return removeRows(RowFilter.dropFilter(rowsToDrop));
    }
    
    // Interaction with change data
    
    /**
     * Extract change data by applying a function to each filtered row.
     * The calls to the change data producer are batched if requested by the producer.
     * 
     * @param <T> the type of change data that is serialized to disk for each row
     * @param filter a filter to select which rows to map
     * @param rowMapper produces the change data for each row
     * @return
     * @throws IllegalStateException if the row mapper returns a batch of results with a
     * different size than the batch of rows it was called on
     */
    public <T extends Serializable> ChangeData<T> mapRows(RowFilter filter, RowChangeDataProducer<T> rowMapper);
    
    /**
     * Extract change data by applying a function to each filtered record.
     * The calls to the change data producer are batched if requested by the producer.
     * 
     * @param <T> the type of change data that is serialized to disk for each row
     * @param filter a filter to select which rows to map
     * @param recordMapper produces the change data for each record
     * @return
     * @throws IllegalStateException if the record mapper returns a batch of results with a
     * different size than the batch of records it was called on
     */
    public <T extends Serializable> ChangeData<T> mapRecords(RecordFilter filter, RecordChangeDataProducer<T> recordMapper);
    
    /**
     * Joins pre-computed change data with the current grid data, row by row.
     * 
     * @param <T> the type of change data that was serialized to disk for each row
     * @param changeData the serialized change data
     * @param rowJoiner produces the new row by joining the old row with change data
     * @param newColumnModel the column model to apply to the new grid
     * @return
     */
    public <T extends Serializable> GridState join(ChangeData<T> changeData, RowChangeDataJoiner<T> rowJoiner, ColumnModel newColumnModel);
    
    /**
     * Joins pre-computed change data with the current grid data,
     * with a joiner function that can return multiple rows for a given original row.
     * 
     * @param <T> the type of change data that was serialized to disk for each row
     * @param changeData the serialized change data
     * @param rowJoiner produces the new row by joining the old row with change data
     * @param newColumnModel the column model to apply to the new grid
     * @return
     */
    public <T extends Serializable> GridState join(ChangeData<T> changeData, RowChangeDataFlatJoiner<T> rowJoiner, ColumnModel newColumnModel);
    
    /**
     * Joins pre-computed change data with the current grid data, record by record.
     * 
     * @param <T> the type of change data that was serialized to disk for each record
     * @param changeData the serialized change data
     * @param rowJoiner produces the new list of rows by joining the old record with change data
     * @param newColumnModel the column model to apply to the new grid
     * @return
     */
    public <T extends Serializable> GridState join(ChangeData<T> changeData, RecordChangeDataJoiner<T> recordJoiner, ColumnModel newColumnModel);
    
    // Union of grid states
    
    /**
     * Creates a new grid state containing all rows in this grid, followed
     * by all rows in the other grid supplied.
     * The overlay models of this grid have priority over the others.
     * 
     * The two grid states are required to have the same number of columns.
     * 
     * @param other the grid to concatenate to this one
     * @return a new grid, union of the two
     */
    public GridState concatenate(GridState other);
    
    // Memory management
    
    /**
     * Is this grid cached in memory? If not, its contents are stored on disk.
     */
    public boolean isCached();
    
    /**
     * Free up any memory used to cache this grid in memory.
     */
    public void uncache();
    
    /**
     * Attempt to cache this grid in memory. If the grid is too big,
     * this can fail.
     * @return whether the grid was actually cached in memory. 
     */
    public boolean cache();
    
    /**
     * Utility class to represent the outcome of a partial count:
     * the number of records/rows processed, and how many of these
     * fulfilled the condition.
     */
    public static class ApproxCount implements Serializable {

        private static final long serialVersionUID = -6472934740385946264L;
        private final long _processed;
        private final long _matched;
        
        public ApproxCount(long processed, long matched) {
            _processed = processed;
            _matched = matched;
        }
        
        public long getProcessed() {
            return _processed;
        }
        
        public long getMatched() {
            return _matched;
        }
    }
    
    /**
     * Utility class to help with deserialization of the metadata
     * without other attributes (such as number of rows)
     */
    public static class Metadata {
        @JsonProperty("columnModel")
        protected ColumnModel columnModel;
        
        @JsonProperty("overlayModels")
        @JsonTypeInfo(
                use=JsonTypeInfo.Id.NAME,
                include=JsonTypeInfo.As.PROPERTY,
                property="overlayModelType",
                visible=true) // for UnknownOverlayModel, which needs to read its own id
        @JsonTypeIdResolver(OverlayModelResolver.class)
        Map<String, OverlayModel> overlayModels;
        
        @JsonProperty("rowCount")
        long rowCount = -1;
        
        @JsonProperty("recordCount")
        long recordCount = -1;
    }

}
