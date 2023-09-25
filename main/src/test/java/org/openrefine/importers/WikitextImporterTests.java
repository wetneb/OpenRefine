/*

Copyright 2010,2011 Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.openrefine.importers;


import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

import org.openrefine.importers.WikitextImporter;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.GridState;
import org.openrefine.model.Row;
import org.openrefine.model.recon.Recon;
import org.openrefine.model.recon.ReconCandidate;
import org.openrefine.model.recon.ReconJob;
import org.openrefine.model.recon.StandardReconConfig;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class WikitextImporterTests extends ImporterTest {

    private WikitextImporter importer = null;
    private Map<String, Recon> mockedRecons = null;
    
    @Override
    @BeforeTest
    public void init() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @Override
    @BeforeMethod
    public void setUp() {
        super.setUp();
        importer = new WikitextImporter(runner());
        mockedRecons = new HashMap<>();
    }

    @Override
    @AfterMethod
    public void tearDown(){
        importer = null;
        super.tearDown();
    }
    
    @Test
    public void readSimpleData() throws Exception {
    	String input = "\n"
		+ "{|\n"
		+ "|-\n"
		+ "| a || b<br/>2 || c \n"
		+ "|-\n"
		+ "| d || e || f<br>\n"
		+ "|-\n"
		+ "|}\n";
    	
		prepareOptions(0, 0, true, true, null);
		GridState parsed = parse(input);
		
		GridState expected = createGrid(
				new String[] { "Column 1", "Column 2", "Column 3"},
				new Serializable[][] {
					{ "a", "b\n2", "c" },
					{ "d", "e",    "f" }
				});
		
		assertGridEquals(parsed, expected);
    }
    
    /**
     * Issue #1448
     * https://github.com/OpenRefine/OpenRefine/issues/1448
     * @throws Exception 
     */
    @Test
    public void readTableWithMisplacedHeaders() throws Exception {
        String input = "\n"
                + "{|\n"
                + "|-\n"
                + "| a || b<br/>2 || c \n"
                + "|-\n"
                + "| d\n"
                + "! e\n"
                + "| f<br>\n"
                + "|-\n"
                + "|}\n";
        
        prepareOptions(0, 0, true, true, null);
        GridState parsed = parse(input);
    
        GridState expected = createGrid(
        		new String[] { "Column 1", "Column 2", "Column 3" },
        		new Serializable[][] {
        			{ "a", "b\n2", "c" },
        			{ "d", "e", "f" }
        		});
        
        assertGridEquals(parsed, expected);
    }
    
    @BeforeMethod
    public void mockReconCalls() throws Exception {
        StandardReconConfig cfg = Mockito.spy(new StandardReconConfig(
                "http://endpoint.com", "http://schemaspace", "http://schemaspace.com", null, true, Collections.emptyList(), 0));
        PowerMockito.whenNew(StandardReconConfig.class).withAnyArguments().thenReturn(cfg);
        Answer<List<Recon>> mockedResponse = new Answer<List<Recon>>() {
            @Override
            public List<Recon> answer(InvocationOnMock invocation) throws Throwable {
                return fakeReconCall(invocation.getArgument(0));
            }
        };
        PowerMockito.doAnswer(mockedResponse).when(cfg, "batchRecon", Mockito.any(), Mockito.anyLong());
    }
    
    private List<Recon> fakeReconCall(List<ReconJob> jobs) {
        List<Recon> result = new ArrayList<>();
        for(ReconJob job : jobs) {
            result.add(mockedRecons.get(job.toString()));
        }
        return result;
    }
    
    @Test(enabled = false) // disabled due to flakiness on CI
    public void readTableWithLinks() throws Exception {
        // This mock is used to avoid real network connection during test
        Recon ecdvt = Mockito.mock(Recon.class);
        Mockito.when(ecdvt.getBestCandidate()).thenReturn(
                new ReconCandidate("Q116214", "European Centre for the Development of Vocational Training", new String[] {"Q392918"}, 100));
        mockedRecons.put("{\"query\":\"https://de.wikipedia.org/wiki/Europäisches Zentrum für die Förderung der Berufsbildung\"}", ecdvt);
        Recon efilwc = Mockito.mock(Recon.class);
        Mockito.when(efilwc.getBestCandidate()).thenReturn(
                new ReconCandidate("Q1377549", "European Foundation for the Improvement of Living and Working Conditions", new String[] {"Q392918"}, 100));
        mockedRecons.put("{\"query\":\"https://de.wikipedia.org/wiki/Europäische Stiftung zur Verbesserung der Lebens- und Arbeitsbedingungen\"}", efilwc);
        Recon emcdda = Mockito.mock(Recon.class);
        Mockito.when(emcdda.getBestCandidate()).thenReturn(
                new ReconCandidate("Q1377256", "European Monitoring Centre for Drugs and Drug Addiction", new String[] {"Q392918"}, 100));
        mockedRecons.put("{\"query\":\"https://de.wikipedia.org/wiki/Europäische Beobachtungsstelle für Drogen und Drogensucht\"}", emcdda);

        // Data credits: Wikipedia contributors, https://de.wikipedia.org/w/index.php?title=Agenturen_der_Europäischen_Union&action=edit
        String input = "\n"
            +"{|\n"
            +"|-\n"
            +"| [[Europäisches Zentrum für die Förderung der Berufsbildung|Cedefop]] || Cedefop || http://www.cedefop.europa.eu/\n"
            +"|-\n"
            +"| [[Europäische Stiftung zur Verbesserung der Lebens- und Arbeitsbedingungen]] || EUROFOUND || [http://www.eurofound.europa.eu/]\n"
            +"|-\n"
            +"| [[Europäische Beobachtungsstelle für Drogen und Drogensucht]] || EMCDDA || [http://www.emcdda.europa.eu/ europa.eu]\n"
            +"|-\n"
            +"|}\n";

        
        prepareOptions(0, 0, true, true, "https://de.wikipedia.org/wiki/");
        GridState grid = parse(input);
        
        List<Row> rows = grid.collectRows().stream().map(ir -> ir.getRow()).collect(Collectors.toList());
        Assert.assertEquals(grid.getColumnModel().getColumns().size(), 3);
        Assert.assertEquals(rows.size(), 3);
        Assert.assertEquals(rows.get(0).cells.size(), 3);
        
        // Reconciled cells
        Assert.assertEquals(rows.get(0).cells.get(1).value, "Cedefop");
        Assert.assertEquals(rows.get(0).cells.get(1).recon, null);
        Assert.assertEquals(rows.get(2).cells.get(0).value, "Europäische Beobachtungsstelle für Drogen und Drogensucht");
        Assert.assertEquals(rows.get(2).cells.get(0).recon.getBestCandidate().id, "Q1377256");
        
        // various ways to input external links
        Assert.assertEquals(rows.get(1).cells.get(2).value, "http://www.eurofound.europa.eu/");
        Assert.assertEquals(rows.get(2).cells.get(2).value, "http://www.emcdda.europa.eu/");
        // Assert.assertEquals(project.rows.get(0).cells.get(2).value, "http://www.cedefop.europa.eu/");
        // unfortunately the above does not seem to be supported by the parser (parsed as blank instead)
    }

    @Test
    public void readStyledTableWithHeader() throws Exception {
        // Data credits: Wikipedia contributors, https://de.wikipedia.org/w/index.php?title=Agenturen_der_Europäischen_Union&action=edit
        String input = "\n"
            +"==Agenturen==\n"
            +"{| class=\"wikitable sortable\"\n"
            +"! style=\"text-align:left; width: 60em\" | Offizieller Name\n"
            +"! style=\"text-align:left; width: 9em\" | Abkürzung\n"
            +"! style=\"text-align:left; width: 6em\" | Website\n"
            +"! style=\"text-align:left; width: 15em\" | Standort\n"
            +"! style=\"text-align:left; width: 18em\" | Staat\n"
            +"! style=\"text-align:left; width: 6em\" | Gründung\n"
            +"! style=\"text-align:left; width: 50em\" | Anmerkungen\n"
            +"|-\n"
            +"| [[Europäisches Zentrum für die Förderung der Berufsbildung]] || '''Cedefop''' || [http://www.cedefop.europa.eu/] || [[Thessaloniki]] || {{Griechenland}} || 1975 ||\n"
            +"|-\n"
            +"| [[Europäische Stiftung zur Verbesserung der Lebens- und Arbeitsbedingungen]] || ''EUROFOUND'' || [http://www.eurofound.europa.eu/] || [[Dublin]] || {{Irland}} || 1975 ||\n"
            +"|-\n"
            +"| [[Europäische Beobachtungsstelle für Drogen und Drogensucht]] || EMCDDA || [http://www.emcdda.europa.eu/] || [[Lissabon]] || {{Portugal}} || 1993 ||\n"
            +"|-\n"
            +"|}\n";

        prepareOptions(-1, 1, true, true, null);
        GridState grid = parse(input);
        
        List<Row> rows = grid.collectRows().stream().map(ir -> ir.getRow()).collect(Collectors.toList());
        ColumnModel columnModel = grid.getColumnModel();
		Assert.assertEquals(columnModel.getColumns().size(), 7);
        Assert.assertEquals(rows.get(0).cells.get(0).value, "Europäisches Zentrum für die Förderung der Berufsbildung");
        Assert.assertEquals(rows.get(0).cells.get(1).value, "Cedefop");
        Assert.assertEquals(rows.get(1).cells.get(1).value, "EUROFOUND");
        Assert.assertEquals(columnModel.getColumns().get(0).getName(), "Offizieller Name");
        Assert.assertEquals(columnModel.getColumns().get(6).getName(), "Anmerkungen");
        Assert.assertEquals(rows.get(0).cells.size(), 7);  
    }

    @Test
    public void readTableWithSpanningCells() throws Exception {
        // inspired from https://www.mediawiki.org/wiki/Help:Tables
        String input = "{| class=\"wikitable\"\n"
        +"!colspan=\"6\"|Shopping List\n"
        +"|-\n"
        +"|Bread & Butter\n"
        +"|Pie\n"
        +"|Buns\n"
        +"|rowspan=\"2\"|Danish\n"
        +"|colspan=\"2\"|Croissant\n"
        +"|-\n"
        +"|Cheese\n"
        +"|colspan=\"2\"|Ice cream\n"
        +"|Butter\n"
        +"|Yogurt\n"
        +"|}\n";
        
        prepareOptions(-1, 1, true, true, null);
        GridState grid = parse(input);
        
        GridState expected = createGrid(
        		new String[] {
        				"Shopping List", "Column", "Column2", "Column3", "Column4", "Column5"
        		}, new Serializable[][] {
        			{ "Bread & Butter", "Pie", "Buns", "Danish", "Croissant", null },
        			{ "Cheese", "Ice cream", null, null, "Butter", "Yogurt" }
        		});
        
        assertGridEquals(grid, expected);
    }
    
    @Test
    public void readTableWithReferences() throws Exception {
        // inspired from https://www.mediawiki.org/wiki/Help:Tables
        String input = "{|\n"
        +"! price\n"
        +"! fruit\n"
        +"! merchant\n"
        +"|-\n"
        +"| a || b <ref name=\"myref\"> See [http://gnu.org here]</ref>  || c <ref name=\"ms\"> or http://microsoft.com/ </ref>\n"
        +"|-\n"
        +"| d || e <ref name=\"ms\"/>|| f <ref name=\"myref\" />\n"
        +"|-\n"
        +"|}\n";
        
        prepareOptions(-1, 1, true, true, null);
        GridState grid = parse(input);
        
        GridState expected = createGrid(
        		new String[] {
        				"price", "fruit",  "Column", "merchant", "Column2"
        		}, new Serializable[][] {
        			{"a", "b", "http://gnu.org", "c", "http://microsoft.com/"},
        			{"d", "e", "http://microsoft.com/", "f", "http://gnu.org"}
        		});
        assertGridEquals(grid, expected);
    }

    @Test
    public void readTableWithReferencesTemplates() throws Exception {
        // inspired from https://www.mediawiki.org/wiki/Help:Tables
        String input = "{|\n"
        +"! price\n"
        +"! fruit\n"
        +"! merchant\n"
        +"|-\n"
        +"| a || b <ref name=\"myref\">{{cite web|url=http://gnu.org|accessdate=2017-08-30}}</ref>  || c <ref name=\"ms\"> or {{cite journal|url=http://microsoft.com/|title=BLah}} </ref>\n"
        +"|-\n"
        +"| d || e <ref name=\"ms\"/>|| f <ref name=\"myref\" />\n"
        +"|-\n"
        +"|}\n";
        
        prepareOptions(-1, 1, true, true, null);
        GridState grid = parse(input);
        
        GridState expected = createGrid(
        		new String[] {
        				"price", "fruit", "Column", "merchant", "Column2"
        		}, new Serializable[][] {
        			{"a", "b", "http://gnu.org", "c", "http://microsoft.com/"},
        			{"d", "e", "http://microsoft.com/", "f", "http://gnu.org"}
        		});
        assertGridEquals(grid, expected);
    }
    
    /**
     * Include templates and image filenames
     * @throws Exception 
     */
    @Test
    public void readTableWithTemplates() throws Exception {
        String input = "\n"
                + "{|\n"
                + "|-\n"
                + "| {{free to read}} || b || c \n"
                + "|-\n"
                + "| d\n"
                + "| [[File:My logo.svg|70px]]\n"
                + "| f<br>\n"
                + "|-\n"
                + "|}\n";
        
        prepareOptions(0, 0, true, true, null);
        GridState grid = parse(input);
        
        GridState expected = createGrid(
        		new String[] {
        				"Column 1", "Column 2", "Column 3"
        		}, new Serializable[][] {
        			{ "{{free to read}}", "b", "c" },
        			{ "d", "[[File:My logo.svg]]", "f" }
        		});
        
        assertGridEquals(grid, expected);
    }

    //--helpers--
    
    private GridState parse(String wikitext) throws Exception {
    	return parseOneFile(importer, new StringReader(wikitext));
    }

    private void prepareOptions(
        int limit, int headerLines, boolean blankSpanningCells,
        boolean guessValueType, String wikiUrl) {
        
        options.put("limit", limit);
        options.put("headerLines", headerLines);
        options.put("guessCellValueTypes", guessValueType);
        options.put("blankSpanningCells", blankSpanningCells);
        options.put("storeBlankCellsAsNulls", true);
        options.put("parseReferences", true);
        options.put("includeRawTemplates", true);
        options.put("wikiUrl", wikiUrl);
        options.put("reconService", "https://wdreconcile.toolforge.org/en/api");
    }
}
