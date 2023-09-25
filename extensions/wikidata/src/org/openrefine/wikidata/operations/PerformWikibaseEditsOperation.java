/*******************************************************************************
 * MIT License
 * 
 * Copyright (c) 2018 Antonin Delpeuch
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package org.openrefine.wikidata.operations;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.openrefine.RefineModel;
import org.openrefine.browsing.Engine;
import org.openrefine.browsing.EngineConfig;
import org.openrefine.history.History;
import org.openrefine.history.HistoryEntry;
import org.openrefine.model.Cell;
import org.openrefine.model.GridState;
import org.openrefine.model.Row;
import org.openrefine.model.RowFilter;
import org.openrefine.model.changes.Change;
import org.openrefine.model.changes.ChangeContext;
import org.openrefine.model.changes.ChangeData;
import org.openrefine.model.changes.ChangeDataSerializer;
import org.openrefine.model.changes.RowChangeDataJoiner;
import org.openrefine.model.changes.RowChangeDataProducer;
import org.openrefine.model.recon.Recon;
import org.openrefine.model.recon.Recon.Judgment;
import org.openrefine.model.recon.ReconCandidate;
import org.openrefine.model.recon.ReconStatsImpl;
import org.openrefine.operations.EngineDependentOperation;
import org.openrefine.process.LongRunningProcess;
import org.openrefine.process.Process;
import org.openrefine.process.ProcessManager;
import org.openrefine.util.ParsingUtilities;
import org.openrefine.wikidata.editing.ConnectionManager;
import org.openrefine.wikidata.editing.EditBatchProcessor;
import org.openrefine.wikidata.editing.NewItemLibrary;
import org.openrefine.wikidata.schema.WikibaseSchema;
import org.openrefine.wikidata.updates.ItemUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.util.WebResourceFetcherImpl;
import org.wikidata.wdtk.wikibaseapi.ApiConnection;
import org.wikidata.wdtk.wikibaseapi.WikibaseDataEditor;
import org.wikidata.wdtk.wikibaseapi.WikibaseDataFetcher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

public class PerformWikibaseEditsOperation extends EngineDependentOperation {

    static final Logger logger = LoggerFactory.getLogger(PerformWikibaseEditsOperation.class);
    
    static final String description = "Perform Wikibase edits";
    static final protected String changeDataId = "newEntities";

    @JsonProperty("summary")
    private String summary;

    @JsonCreator
    public PerformWikibaseEditsOperation(
    		@JsonProperty("engineConfig")
    		EngineConfig engineConfig,
    		@JsonProperty("summary")
    		String summary) {
        super(engineConfig);
        Validate.notNull(summary, "An edit summary must be provided.");
        Validate.notEmpty(summary, "An edit summary must be provided.");
        this.summary = summary;
    }

    @Override
	public String getDescription() {
        return description;
    }

    @Override
    public Process createProcess(History history, ProcessManager processManager)
            throws Exception {
        GridState currentGridState = history.getCurrentGridState();
		return new PerformEditsProcess(history, processManager, currentGridState, createEngine(currentGridState), summary);
    }

    static public class PerformWikibaseEditsChange implements Change {
     
        public PerformWikibaseEditsChange() {
        }
        
		@Override
		public GridState apply(GridState projectState, ChangeContext context) throws DoesNotApplyException {
			ChangeData<RowNewReconUpdate> changeData = null;
			try {
				changeData = context.getChangeData(changeDataId, new RowNewReconUpdateSerializer());
			} catch (IOException e) {
				throw new DoesNotApplyException(String.format("Unable to retrieve change data '%s'", changeDataId));
			}
			NewReconRowJoiner joiner = new NewReconRowJoiner();
			GridState joined = projectState.join(changeData, joiner, projectState.getColumnModel());
			GridState updated = ReconStatsImpl.updateReconStats(joined);
			return updated;
		}

		@Override
		public boolean isImmediate() {
			return false;
		}

    }

    public class PerformEditsProcess extends LongRunningProcess implements Runnable {

    	protected History _history;
    	protected ProcessManager _processManager;
        protected GridState _grid;
        protected Engine _engine;
        protected WikibaseSchema _schema;
        protected String _summary;
        protected List<String> _tags;
        protected final long _historyEntryID;

        protected PerformEditsProcess(History history, ProcessManager processManager, GridState grid, Engine engine, String summary) {
            super(description);
            this._history = history;
            this._processManager = processManager;
            this._grid = grid;
            this._engine = engine;
            this._schema = (WikibaseSchema) grid.getOverlayModels().get("wikibaseSchema");
            this._summary = summary;
            // TODO this is one of the attributes that should be configured on a per-wiki basis
            // TODO enable this tag once 3.3 final is released and create 3.4 tag without AbuseFilter
            String tag = "openrefine";
            Pattern pattern = Pattern.compile("^(\\d+\\.\\d+).*$");
            Matcher matcher = pattern.matcher(RefineModel.VERSION);
            if (matcher.matches()) {
                tag += "-"+matcher.group(1);
            }
            this._tags = Collections.emptyList(); // TODO Arrays.asList(tag);
            this._historyEntryID = HistoryEntry.allocateID();
        }

        @Override
        public void run() {

            WebResourceFetcherImpl.setUserAgent("OpenRefine Wikidata extension");
            ConnectionManager manager = ConnectionManager.getInstance();
            if (!manager.isLoggedIn()) {
                return;
            }
            ApiConnection connection = manager.getConnection();

            WikibaseDataFetcher wbdf = new WikibaseDataFetcher(connection, _schema.getBaseIri());
            WikibaseDataEditor wbde = new WikibaseDataEditor(connection, _schema.getBaseIri());
            
            // Generate batch token
            long token = (new Random()).nextLong();
            // The following replacement is a fix for: https://github.com/Wikidata/editgroups/issues/4
            // Because commas and colons are used by Wikibase to separate the auto-generated summaries
            // from the user-supplied ones, we replace these separators by similar unicode characters to
            // make sure they can be told apart.
            String summaryWithoutCommas = _summary.replaceAll(", ","ꓹ ").replaceAll(": ","։ ");
            String summary = summaryWithoutCommas + String.format(" ([[:toollabs:editgroups/b/OR/%s|details]])",
                    (Long.toHexString(token).substring(0, 10)));

            // Evaluate the schema
            List<ItemUpdate> itemDocuments = _schema.evaluate(_grid, _engine);

            // Prepare the edits
            NewItemLibrary newItemLibrary = new NewItemLibrary();
            EditBatchProcessor processor = new EditBatchProcessor(wbdf, wbde, itemDocuments, newItemLibrary, summary,
                    _tags, 50);

            // Perform edits
            logger.info("Performing edits");
            while (processor.remainingEdits() > 0) {
                try {
                    processor.performEdit();
                } catch (InterruptedException e) {
                    _canceled = true;
                }
                _progress = processor.progress();
                if (_canceled) {
                    break;
                }
            }
            
            // Saving the ids of the newly created entities should take much less time
            // hence it is okay to do it only when we reached progress 100.
            NewReconChangeDataProducer rowMapper = new NewReconChangeDataProducer(newItemLibrary);
            ChangeData<RowNewReconUpdate> changeData = _grid.mapRows(RowFilter.ANY_ROW, rowMapper);
            try {
				_history.getChangeDataStore().store(changeData , _historyEntryID, changeDataId, new RowNewReconUpdateSerializer());
	
	            _progress = 100;
	
	            if (!_canceled) {
	            	
	                Change change = new PerformWikibaseEditsChange();
	                
	                HistoryEntry entry = new HistoryEntry(
	                		_historyEntryID,
	                		_description,
	                		PerformWikibaseEditsOperation.this,
	                		change);
	
	                _history.addEntry(entry);
	                _processManager.onDoneProcess(this);
	            }
            } catch(Exception e) {
            	_processManager.onFailedProcess(this, e);
            }
        }

        @Override
        protected Runnable getRunnable() {
            return this;
        }
    }
    
    protected static class RowNewReconUpdate implements Serializable {

		private static final long serialVersionUID = 4071296846913437839L;
		private final Map<Integer, String> newEntities;
    	
    	@JsonCreator
    	protected RowNewReconUpdate(
    			@JsonProperty("newEntities")
    			Map<Integer, String> newEntities) {
    		this.newEntities = newEntities;
    	}
    	
    	@JsonProperty("newEntities")
    	public Map<Integer, String> getNewEntities() {
    		return newEntities;
    	}
    }
    
    protected static class NewReconChangeDataProducer implements RowChangeDataProducer<RowNewReconUpdate> {

		private static final long serialVersionUID = -1754921123832421920L;
		protected final NewItemLibrary library;
    	
    	protected NewReconChangeDataProducer(NewItemLibrary newItemLibrary) {
    		library = newItemLibrary;
    	}

		@Override
		public RowNewReconUpdate call(long rowId, Row row) {
			Map<Integer, String> map = new HashMap<>();
			List<Cell> cells = row.getCells();
			for (int i = 0; i != cells.size(); i++) {
				Cell cell = cells.get(i);
				if (cell != null && cell.recon != null &&
						Judgment.New.equals(cell.recon.judgment)) {
					long id = cell.recon.id;
					String qid = library.getQid(id);
					if (qid != null) {
						map.put(i, qid);
					}
				}
			}
			if (map.isEmpty()) {
				return null;
			} else {
				return new RowNewReconUpdate(map);
			}
		}
    	
    }
    
    protected static class NewReconRowJoiner implements RowChangeDataJoiner<RowNewReconUpdate> {

		private static final long serialVersionUID = -1042195464154951531L;

		@Override
		public Row call(long rowId, Row row, RowNewReconUpdate changeData) {
			if (changeData == null) {
				return row;
			}
			Row newRow = row;
			for (Entry<Integer, String> t : changeData.getNewEntities().entrySet()) {
				Cell cell = row.getCell(t.getKey());
                if (cell == null || cell.recon == null) {
                    continue;
                }
                Recon recon = cell.recon;
                if (Recon.Judgment.New.equals(recon.judgment)) {
                	ReconCandidate newMatch = new ReconCandidate(t.getValue(), cell.value.toString(),
					new String[0], 100);
					recon = recon
                			.withJudgment(Recon.Judgment.Matched)
                			.withMatch(newMatch)
                			.withCandidate(newMatch);
                    
                }
                Cell newCell = new Cell(cell.value, recon);
				newRow = newRow.withCell(t.getKey(), newCell);
			}
			return newRow;
		}
    	
    }
    
    protected static class RowNewReconUpdateSerializer implements ChangeDataSerializer<RowNewReconUpdate> {

		private static final long serialVersionUID = -165445357950934740L;

		@Override
		public String serialize(RowNewReconUpdate changeDataItem) {
			try {
				return ParsingUtilities.mapper.writeValueAsString(changeDataItem);
			} catch (JsonProcessingException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public RowNewReconUpdate deserialize(String serialized) throws IOException {
			return ParsingUtilities.mapper.readValue(serialized, RowNewReconUpdate.class);
		}
    	
    }
}
