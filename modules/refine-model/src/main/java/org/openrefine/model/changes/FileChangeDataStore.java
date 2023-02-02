
package org.openrefine.model.changes;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.openrefine.model.Runner;
import org.openrefine.process.LongRunningProcess;
import org.openrefine.process.ProcessManager;
import org.openrefine.process.ProgressReporter;

public class FileChangeDataStore implements ChangeDataStore {

    private Runner _runner;
    private File _baseDirectory;
    private ProcessManager processManager = new ProcessManager();

    public FileChangeDataStore(Runner runner, File baseDirectory) {
        _runner = runner;
        _baseDirectory = baseDirectory;
    }

    /**
     * Associates to a pair of ids the location where we should store them.
     */
    private File idsToFile(long historyEntryId, String dataId) {
        return new File(historyEntryIdToFile(historyEntryId), dataId);
    }

    private Set<String> changeDataIdsInProgress() {
        return processManager.getProcesses().stream()
                .map(process -> String.format("%d/%s", process.getHistoryEntryId(), process.getChangeDataId()))
                .collect(Collectors.toSet());
    }

    /**
     * Directory where all change data belonging to a given history entry id should be stored.
     */
    private File historyEntryIdToFile(long historyEntryId) {
        return new File(_baseDirectory, Long.toString(historyEntryId));
    }

    @Override
    public ProcessManager getProcessManager() {
        return processManager;
    }

    @Override
    public <T> void store(ChangeData<T> data, long historyEntryId, String dataId,
            ChangeDataSerializer<T> serializer, Optional<ProgressReporter> progressReporter) throws IOException {
        File file = idsToFile(historyEntryId, dataId);
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
    public <T> ChangeData<T> retrieve(long historyEntryId, String dataId,
            ChangeDataSerializer<T> serializer) throws IOException {
        File file = idsToFile(historyEntryId, dataId);
        return _runner.loadChangeData(file, serializer);
    }

    @Override
    public <T> ChangeData<T> retrieveOrCompute(
            long historyEntryId,
            String dataId,
            ChangeDataSerializer<T> serializer,
            Function<ChangeData<T>, ChangeData<T>> completionProcess, String description) throws IOException {
        File file = idsToFile(historyEntryId, dataId);
        String changeDataId = String.format("%d/%s", historyEntryId, dataId);

        ChangeData<T> storedChangeData;
        boolean storedChangedDataIsComplete;
        try {
            storedChangeData = _runner.loadChangeData(file, serializer);
            storedChangedDataIsComplete = storedChangeData.isComplete();
        } catch(IOException e) {
            storedChangeData = _runner.create(Collections.emptyList());
            storedChangedDataIsComplete = false;
        }

        if (!storedChangedDataIsComplete && !changeDataIdsInProgress().contains(changeDataId)) {
            // queue a new process to compute the change data
            processManager.queueProcess(new ChangeDataStoringProcess<T>(description,
                    storedChangeData,
                    historyEntryId,
                    dataId,
                    this,
                    serializer,
                    completionProcess));
        }
        return storedChangeData;
    }

    @Override
    public void discardAll(long historyEntryId) {
        File file = historyEntryIdToFile(historyEntryId);
        if (file.exists()) {
            try {
                FileUtils.deleteDirectory(file);
            } catch (IOException e) {
                ;
            }
        }
    }

    protected static class ChangeDataStoringProcess<T> extends LongRunningProcess implements Runnable {
        final ChangeData<T> storedChangeData;
        final long historyEntryId;
        final String changeDataId;
        final ChangeDataStore changeDataStore;
        final ChangeDataSerializer<T> serializer;
        final Function<ChangeData<T>, ChangeData<T>> completionProcess;

        public ChangeDataStoringProcess(
                String description,
                ChangeData<T> storedChangeData,
                long historyEntryId,
                String changeDataId,
                ChangeDataStore changeDataStore,
                ChangeDataSerializer<T> serializer, Function<ChangeData<T>, ChangeData<T>> completionProcess) {
            super(description);
            this.storedChangeData = storedChangeData;
            this.historyEntryId = historyEntryId;
            this.changeDataId = changeDataId;
            this.changeDataStore = changeDataStore;
            this.serializer = serializer;
            this.completionProcess = completionProcess;
        }

        @Override
        protected Runnable getRunnable() {
            return this;
        }

        @Override
        public long getHistoryEntryId() {
            return historyEntryId;
        }

        @Override
        public String getChangeDataId() {
            return changeDataId;
        }

        @Override
        public void run() {
            ChangeData<T> newChangeData = completionProcess.apply(storedChangeData);
            try {
                changeDataStore.store(newChangeData, historyEntryId, changeDataId, serializer, Optional.of(_reporter));
            } catch (IOException e) {
                _manager.onFailedProcess(this, e);
            }
        }
    }

}
