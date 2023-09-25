package org.openrefine.model;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.openrefine.io.OrderedLocalFileSystem;
import org.openrefine.model.GridState.Metadata;
import org.openrefine.model.changes.ChangeData;
import org.openrefine.model.changes.ChangeDataSerializer;
import org.openrefine.model.changes.IndexedData;
import org.openrefine.model.local.PLL;
import org.openrefine.model.local.PLLContext;
import org.openrefine.model.local.PairPLL;
import org.openrefine.model.local.Tuple2;
import org.openrefine.overlay.OverlayModel;
import org.openrefine.util.ParsingUtilities;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.google.common.util.concurrent.MoreExecutors;

@JsonIgnoreType
public class LocalDatamodelRunner implements DatamodelRunner {
    
    final static protected String METADATA_PATH = "metadata.json";
    final static protected String GRID_PATH = "grid";
    
    protected final PLLContext pllContext;
    protected int numPartitions;
    
    public LocalDatamodelRunner(Integer defaultParallelism) {
        Configuration fsConf = new Configuration();
        fsConf.set("fs.file.impl", OrderedLocalFileSystem.class.getName());
        try {
            pllContext = new PLLContext(MoreExecutors.listeningDecorator(
                    Executors.newCachedThreadPool()), LocalFileSystem.get(fsConf));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        numPartitions = defaultParallelism;
    }

    public LocalDatamodelRunner() {
        this(4);
    }
    
    public PLLContext getPLLContext() {
        return pllContext;
    }

    @Override
    public GridState loadGridState(File path) throws IOException {
        File metadataFile = new File(path, METADATA_PATH);
        File gridFile = new File(path, GRID_PATH);
        
        Metadata metadata = ParsingUtilities.mapper.readValue(metadataFile, Metadata.class);
        PairPLL<Long, Row> rows = pllContext
                .textFile(gridFile.getAbsolutePath())
                .mapToPair(s -> parseIndexedRow(s));
        rows = PairPLL.assumeIndexed(rows, metadata.rowCount);
        return new LocalGridState(this, rows, metadata.columnModel, metadata.overlayModels);
    }
    
    protected static Tuple2<Long, Row> parseIndexedRow(String source) {
        IndexedRow id;
        try {
            id = ParsingUtilities.mapper.readValue(source, IndexedRow.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Tuple2<Long, Row>(id.getIndex(), id.getRow());
    }
    
    @Override
    public FileSystem getFileSystem() throws IOException {
        return pllContext.getFileSystem();
    }

    @Override
    public GridState create(ColumnModel columnModel, List<Row> rows, Map<String, OverlayModel> overlayModels) {
        // the call to zipWithIndex is efficient as the first PLL is in memory already
        PairPLL<Long, Row> pll = pllContext.parallelize(numPartitions, rows)
                .zipWithIndex();
        return new LocalGridState(this, pll, columnModel, overlayModels);
    }

    @Override
    public <T extends Serializable> ChangeData<T> loadChangeData(File path, ChangeDataSerializer<T> serializer)
            throws IOException {
        PairPLL<Long, T> pll = pllContext
                .textFile(path.getAbsolutePath())
                .map(line -> {try {
                    return IndexedData.<T>read(line, serializer);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }})
                .mapToPair(indexedData -> Tuple2.of(indexedData.getId(), indexedData.getData()));
        pll = PairPLL.assumeSorted(pll);
        return new LocalChangeData<T>(this, pll);
    }

    @Override
    public GridState loadTextFile(String path) throws IOException {
        return loadTextFile(path, -1);
    }

    @Override
    public GridState loadTextFile(String path, long limit) throws IOException {
        PLL<Row> rows = pllContext.textFile(path)
                .map(s -> new Row(Arrays.asList(new Cell(s, null))));
        if (limit >= 0) {
            // this generally leaves more rows than necessary, but is the best thing
            // we can do so far without reading the dataset to add row indices
            rows = rows.limitPartitions(limit);
        }
        PairPLL<Long, Row> pll = rows
                .zipWithIndex();
        if (limit >= 0) {
            // enforce limit properly by removing any rows from the following partitions
            // that exceed the desired row count
            // TODO: we could add a PLL API method which does this more efficiently,
            // assuming that a RangePartitioner is present, but it's a marginal optimization
            pll = pll.filter(tuple -> tuple.getKey() < limit);
        }
        return new LocalGridState(
                this,
                pll,
                new ColumnModel(Collections.singletonList(new ColumnMetadata("Column"))),
                Collections.emptyMap());
    }

    @Override
    public <T extends Serializable> ChangeData<T> create(List<IndexedData<T>> changeData) {
        PairPLL<Long, T> pll = pllContext
                .parallelize(numPartitions, changeData)
                .mapToPair(indexedData -> Tuple2.of(indexedData.getId(), indexedData.getData()))
                .filter(tuple -> tuple.getValue() != null);
        pll = PairPLL.assumeSorted(pll);
        return new LocalChangeData<T>(this, pll);
    }

}
