
package org.openrefine.model.changes;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.openrefine.model.Runner;
import org.openrefine.process.Process;
import org.openrefine.process.ProcessManager;
import org.openrefine.process.ProgressReporter;

/**
 * A {@link ChangeDataStore} which stores change data on disk.
 * This is the default one used in OpenRefine.
 * <br>
 * It makes use of two directories:
 * <ul>
 *     <li>The base directory, where changes are stored;</li>
 *     <li>The incomplete directory, which is used as a temporary location when resuming the fetching
 *     of some change data after an interruption.</li>
 * </ul>
 * When {@link #retrieveOrCompute(ChangeDataId, ChangeDataSerializer, Function, String)} finds an incomplete change
 * data is found in the base directory, it is moved to the incomplete directory. A new version of the change data,
 * completed using the completion process, is then saved again in the base directory.
 */
public class FileChangeDataStore implements ChangeDataStore {

    private final Runner _runner;
    private final File _baseDirectory;
    private final File _incompleteDirectory;
    private final ProcessManager processManager = new ProcessManager();
    private final Set<ChangeDataId> _toRefresh;

    public FileChangeDataStore(Runner runner, File baseDirectory, File incompleteDirectory) {
        _runner = runner;
        _baseDirectory = baseDirectory;
        _incompleteDirectory = incompleteDirectory;
        _toRefresh = new HashSet<>();
    }

    /**
     * Associates to a pair of ids the location where we should store them.
     */
    private File idsToFile(ChangeDataId changeDataId, boolean incomplete) {
        return new File(historyEntryIdToDir(changeDataId.getHistoryEntryId(), incomplete), changeDataId.getSubDirectory());
    }

    private Set<ChangeDataId> changeDataIdsInProgress() {
        return processManager.getProcesses().stream()
                .map(Process::getChangeDataId)
                .collect(Collectors.toSet());
    }

    /**
     * Directory where all change data belonging to a given history entry id should be stored.
     */
    private File historyEntryIdToDir(long historyEntryId, boolean incomplete) {
        return new File(incomplete ? _incompleteDirectory : _baseDirectory, Long.toString(historyEntryId));
    }

    @Override
    public ProcessManager getProcessManager() {
        return processManager;
    }

    @Override
    public <T> void store(ChangeData<T> data, ChangeDataId changeDataId,
            ChangeDataSerializer<T> serializer, Optional<ProgressReporter> progressReporter) throws IOException {
        File file = idsToFile(changeDataId, false);
        file.mkdirs();
        try {
            if (progressReporter.isPresent()) {
                data.saveToFile(file, serializer, progressReporter.get());
            } else {
                data.saveToFile(file, serializer);
            }
        } catch (InterruptedException e) {
            FileUtils.deleteDirectory(file);
            throw new IOException(e);
        }
    }

    @Override
    public <T> ChangeData<T> retrieve(ChangeDataId changeDataId,
            ChangeDataSerializer<T> serializer) throws IOException {
        File file = idsToFile(changeDataId, false);
        ChangeData<T> changeData = _runner.loadChangeData(file, serializer);
        if (changeData.isComplete() && _toRefresh.contains(changeDataId)) {
            _toRefresh.remove(changeDataId);
        }
        return changeData;
    }

    @Override
    public <T> ChangeData<T> retrieveOrCompute(
            ChangeDataId changeDataId,
            ChangeDataSerializer<T> serializer,
            Function<Optional<ChangeData<T>>, ChangeData<T>> completionProcess, String description) throws IOException {
        File changeDataDir = idsToFile(changeDataId, false);
        File incompleteDir = null;

        Optional<ChangeData<T>> storedChangeData;
        boolean storedChangedDataIsComplete;
        try {
            storedChangeData = Optional.of(_runner.loadChangeData(changeDataDir, serializer));
            storedChangedDataIsComplete = storedChangeData.get().isComplete();
            if (!storedChangedDataIsComplete) {
                incompleteDir = idsToFile(changeDataId, true);
                storedChangeData = Optional.empty();
                if (incompleteDir.exists()) {
                    FileUtils.deleteDirectory(changeDataDir);
                }
                FileUtils.moveDirectory(changeDataDir, incompleteDir);
                storedChangeData = Optional.of(_runner.loadChangeData(incompleteDir, serializer));
            }
        } catch (IOException e) {
            storedChangeData = Optional.empty();
            storedChangedDataIsComplete = false;
        }

        if (!storedChangedDataIsComplete && !changeDataIdsInProgress().contains(changeDataId)) {
            // queue a new process to compute the change data
            processManager.queueProcess(new ChangeDataStoringProcess<T>(description,
                    storedChangeData,
                    changeDataId,
                    this,
                    serializer,
                    completionProcess,
                    incompleteDir));
            _toRefresh.add(changeDataId);
        }
        return storedChangeData.orElse(_runner.create(Collections.emptyList()));
    }

    @Override
    public boolean needsRefreshing(long historyEntryId) {
        return _toRefresh.stream().map(ChangeDataId::getHistoryEntryId).anyMatch(id -> id == historyEntryId);
    }

    @Override
    public void discardAll(long historyEntryId) {
        File file = historyEntryIdToDir(historyEntryId, false);
        if (file.exists()) {
            try {
                FileUtils.deleteDirectory(file);
            } catch (IOException e) {
                ;
            }
        }
    }

    protected static class ChangeDataStoringProcess<T> extends Process implements Runnable {

        final Optional<ChangeData<T>> storedChangeData;
        final ChangeDataId changeDataId;
        final ChangeDataStore changeDataStore;
        final ChangeDataSerializer<T> serializer;
        final Function<Optional<ChangeData<T>>, ChangeData<T>> completionProcess;
        final File temporaryDirToDelete;

        public ChangeDataStoringProcess(
                String description,
                Optional<ChangeData<T>> storedChangeData,
                ChangeDataId changeDataId,
                ChangeDataStore changeDataStore,
                ChangeDataSerializer<T> serializer, Function<Optional<ChangeData<T>>, ChangeData<T>> completionProcess,
                File temporaryDirToDelete) {
            super(description);
            this.storedChangeData = storedChangeData;
            this.changeDataId = changeDataId;
            this.changeDataStore = changeDataStore;
            this.serializer = serializer;
            this.completionProcess = completionProcess;
            this.temporaryDirToDelete = temporaryDirToDelete;
        }

        @Override
        protected Runnable getRunnable() {
            return this;
        }

        @Override
        public ChangeDataId getChangeDataId() {
            return changeDataId;
        }

        @Override
        public void run() {
            ChangeData<T> newChangeData = completionProcess.apply(storedChangeData);
            try {
                changeDataStore.store(newChangeData, changeDataId, serializer, Optional.of(_reporter));
                if (temporaryDirToDelete != null && temporaryDirToDelete.exists()) {
                    FileUtils.deleteDirectory(temporaryDirToDelete);
                }
                _manager.onDoneProcess(this);
            } catch (Exception e) {
                _manager.onFailedProcess(this, e);
            }
        }
    }

}
