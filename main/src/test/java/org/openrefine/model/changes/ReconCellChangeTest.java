package org.openrefine.model.changes;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

import org.openrefine.RefineTest;
import org.openrefine.model.Cell;
import org.openrefine.model.GridState;
import org.openrefine.model.Row;
import org.openrefine.model.changes.Change.DoesNotApplyException;
import org.openrefine.model.recon.Recon;
import org.openrefine.model.recon.Recon.Judgment;
import org.openrefine.model.recon.ReconStats;
import org.openrefine.util.ParsingUtilities;
import org.openrefine.util.TestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class ReconCellChangeTest extends RefineTest {
	
	private GridState initialGrid;
	
	private String serializedChange = ""
			+ "{\n" + 
			"  \"columnName\" : \"foo\", \n" + 
			"  \"newRecon\" : {\n" + 
			"    \"id\" : 1234,\n" + 
			"    \"identifierSpace\" : \"http://my.service.com/space\",\n" + 
			"    \"j\" : \"matched\",\n" + 
			"    \"m\" : {\n" + 
			"      \"id\" : \"f\",\n" + 
			"      \"name\" : \"e 1\",\n" + 
			"      \"score\" : 98,\n" + 
			"      \"types\" : [ ]\n" + 
			"    },\n" + 
			"    \"schemaSpace\" : \"http://my.service.com/schema\",\n" + 
			"    \"service\" : \"http://my.service.com/api\"\n" + 
			"  },\n" + 
			"  \"newReconStats\" : {\n" + 
			"    \"matchedTopics\" : 1,\n" + 
			"    \"newTopics\" : 2,\n" + 
			"    \"nonBlanks\" : 3\n" + 
			"  },\n" + 
			"  \"rowId\" : 0,\n" + 
			"  \"type\" : \"org.openrefine.model.changes.ReconCellChange\"\n" + 
			"}";
	
	@BeforeTest
	public void setUpGrid() {
		initialGrid = createGrid(
				new String[] { "foo", "bar" },
				new Serializable[][] {
			{ "a", new Cell("b", testRecon("c", "d", Judgment.Matched)) },
			{ 3, true }
		});
	}
	
	@Test
	public void testReconCellChange() throws DoesNotApplyException {
		Recon newRecon = testRecon("e", "f", Judgment.Matched);
		ReconStats newStats = ReconStats.create(3L, 2L, 1L);
		Change change = new ReconCellChange(0L, "bar", newRecon, newStats);
		
		ChangeContext context = mock(ChangeContext.class);
		when(context.getHistoryEntryId()).thenReturn(5432L);
		GridState newGrid = change.apply(initialGrid, context);
		
		Assert.assertEquals(newGrid.getRow(0L),
				new Row(Arrays.asList(new Cell("a", null), new Cell("b", newRecon.withJudgmentHistoryEntry(5432L)))));
		Assert.assertEquals(newGrid.getRow(1L),
				new Row(Arrays.asList(new Cell(3, null), new Cell(true, null))));
	}
	
	@Test
	public void testSerialize() {
		Recon newRecon = testRecon("e", "f", Judgment.Matched);
		ReconStats newStats = ReconStats.create(3L, 2L, 1L);
		Change change = new ReconCellChange(0L, "foo", newRecon, newStats);
		TestUtils.isSerializedTo(change, serializedChange, ParsingUtilities.defaultWriter);
	}
	
	@Test
	public void testDeserialize() throws JsonParseException, JsonMappingException, IOException {
		Change change = ParsingUtilities.mapper.readValue(serializedChange, Change.class);
		TestUtils.isSerializedTo(change, serializedChange, ParsingUtilities.defaultWriter);
	}
}
