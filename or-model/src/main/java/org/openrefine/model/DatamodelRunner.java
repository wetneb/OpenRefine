package org.openrefine.model;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.fs.FileSystem;
import org.openrefine.model.changes.ChangeData;
import org.openrefine.model.changes.ChangeDataSerializer;
import org.openrefine.model.changes.IndexedData;
import org.openrefine.overlay.OverlayModel;

/**
 * Encapsulates the context required to implement,
 * read and execute operations on {@link GridState} objects.
 * 
 * @author Antonin Delpeuch
 *
 */
public interface DatamodelRunner {
    
    /**
     * Loads a {@link GridState} serialized at a given
     * location.
     * 
     * @param path the directory where the GridState is stored
     * @return the grid
     * @throws IOException when loading the grid failed
     */
    public GridState loadGridState(File path) throws IOException;
    
    /**
     * Loads a {@link ChangeData} serialized at a given
     * location.
     * 
     * @param path the directory where the ChangeData is stored
     * @throws IOException when loading the grid failed
     */
    public <T extends Serializable> ChangeData<T> loadChangeData(File path, ChangeDataSerializer<T> serializer) throws IOException;
    
    /**
     * Returns a file system used by the implementation to read
     * files to import.
     * @throws IOException 
     */
    public FileSystem getFileSystem() throws IOException;
    
    /**
     * Creates a {@link GridState} from an in-memory list of rows,
     * which will be numbered from 0 to length-1.
     */
    public GridState create(ColumnModel columnModel, List<Row> rows, Map<String,OverlayModel> overlayModels);
    
    /**
     * Loads a text file as a {@link GridState} with a single column
     * named "Column" and whose contents are the lines in the file,
     * parsed as strings.
     */
    public GridState loadTextFile(String path) throws IOException;
    
    /**
     * Loads a text file as a {@link GridState} with a single column
     * named "Column" and whose contents are the lines in the file,
     * parsed as strings.
     * 
     * @param path the path to the text file to load
     * @param limit the maximum number of lines to read
     */
    public GridState loadTextFile(String path, long limit) throws IOException;
    
    /**
     * Creates a {@link ChangeData} from an in-memory list of
     * indexed data. The list is required to be sorted.
     */
    public <T extends Serializable> ChangeData<T> create(List<IndexedData<T>> changeData);

}
