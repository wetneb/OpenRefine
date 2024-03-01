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

package com.google.refine;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import edu.mit.simile.butterfly.ButterflyModule;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.google.refine.expr.Evaluable;
import com.google.refine.expr.MetaParser;
import com.google.refine.expr.ParsingException;
import com.google.refine.grel.ControlFunctionRegistry;
import com.google.refine.grel.Function;
import com.google.refine.importers.SeparatorBasedImporter;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingManager;
import com.google.refine.io.FileProjectManager;
import com.google.refine.messages.OpenRefineMessage;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.ModelException;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.process.Process;
import com.google.refine.process.ProcessManager;
import com.google.refine.util.TestUtils;

/**
 * A base class containing various utilities to help testing Refine.
 */
public class RefineTest {

    public static final double EPSILON = 0.0000001;
    protected static Properties bindings = null;

    protected Logger logger;

    boolean testFailed;
    protected File workspaceDir;
    protected RefineServlet servlet;
    private List<Project> projects = new ArrayList<Project>();
    private List<ImportingJob> importingJobs = new ArrayList<ImportingJob>();

    @BeforeSuite
    public void init() {
        System.setProperty("log4j.configuration", "tests.log4j.properties");
        try {
            workspaceDir = TestUtils.createTempDirectory("openrefine-test-workspace-dir");
            File jsonPath = new File(workspaceDir, "workspace.json");
            FileUtils.writeStringToFile(jsonPath, "{\"projectIDs\":[]\n" +
                    ",\"preferences\":{\"entries\":{\"scripting.starred-expressions\":" +
                    "{\"class\":\"com.google.refine.preference.TopList\",\"top\":2147483647," +
                    "\"list\":[]},\"scripting.expressions\":{\"class\":\"com.google.refine.preference.TopList\",\"top\":100,\"list\":[]}}}}",
                    "UTF-8"); // JSON is always UTF-8
            FileProjectManager.initialize(workspaceDir);

        } catch (IOException e) {
            workspaceDir = null;
            e.printStackTrace();
        }
        // This just keeps track of any failed test, for cleanupWorkspace
        testFailed = false;
    }

    @BeforeMethod
    protected void initProjectManager() {
        servlet = new RefineServletStub();
        ProjectManager.singleton = new ProjectManagerStub();
        ImportingManager.initialize(servlet);
    }

    protected Project createProjectWithColumns(String projectName, String... columnNames) throws IOException, ModelException {
        Project project = new Project();
        ProjectMetadata pm = new ProjectMetadata();
        pm.setName(projectName);
        ProjectManager.singleton.registerProject(project, pm);

        if (columnNames != null) {
            for (String columnName : columnNames) {
                int index = project.columnModel.allocateNewCellIndex();
                Column column = new Column(index, columnName);
                project.columnModel.addColumn(index, column, true);
            }
        }
        return project;
    }

    /**
     * @deprecated use {@link #createProject(String[], Serializable[][])} instead, so that the project's contents are
     *             more readable in the test
     */
    @Deprecated
    protected Project createCSVProject(String input) {
        return createCSVProject("test project", input);
    }

    /**
     * @deprecated use {@link #createProject(String, String[], Serializable[][])} instead, so that the project's
     *             contents are more readable in the test
     */
    @Deprecated
    protected Project createCSVProject(String projectName, String input) {

        Project project = new Project();

        ProjectMetadata metadata = new ProjectMetadata();
        metadata.setName(projectName);

        ObjectNode options = mock(ObjectNode.class);
        prepareImportOptions(options, ",", -1, 0, 0, 1, false, false);

        ImportingJob job = ImportingManager.createJob();

        SeparatorBasedImporter importer = new SeparatorBasedImporter();

        List<Exception> exceptions = new ArrayList<Exception>();
        importer.parseOneFile(project, metadata, job, "filesource", new StringReader(input), -1, options, exceptions);
        ProjectManager.singleton.registerProject(project, metadata);
        project.update();

        projects.add(project);
        importingJobs.add(job);
        return project;
    }

    /**
     * Utility method to create a project with pre-defined contents.
     * 
     * @param name
     *            project name
     * @param columnNames
     *            names of the columns
     * @param grid
     *            contents of the project grid, which can be either {@link Cell} instances or just the cell values (for
     *            convenience)
     * @return a test project with the given contents
     */
    public Project createProject(String name, String[] columnNames, Serializable[][] grid) {
        Project project = new Project();
        ProjectMetadata pm = new ProjectMetadata();
        pm.setName(name);
        ProjectManager.singleton.registerProject(project, pm);

        try {
            for (String columnName : columnNames) {
                int index = project.columnModel.allocateNewCellIndex();
                Column column = new Column(index, columnName);
                project.columnModel.addColumn(index, column, true);
            }
        } catch (ModelException e) {
            fail("The column names provided to create a test project contain duplicates");
        }
        for (Serializable[] rawRow : grid) {
            assertEquals(columnNames.length, rawRow.length, "Unexpected row length in test grid data");
            Row row = new Row(columnNames.length);
            for (int i = 0; i != columnNames.length; i++) {
                Serializable rawCell = rawRow[i];
                if (rawCell == null || rawCell instanceof Cell) {
                    row.setCell(i, (Cell) rawCell);
                } else {
                    row.setCell(i, new Cell(rawCell, null));
                }
            }
            project.rows.add(row);
        }
        project.update();
        projects.add(project);
        return project;
    }

    /**
     * Utility method to create a project with pre-defined contents.
     * 
     * @param columnNames
     *            names of the columns
     * @param grid
     *            contents of the project grid, which can be either {@link Cell} instances or just the cell values (for
     *            convenience)
     * @return a test project with the given contents
     */
    public Project createProject(String[] columnNames, Serializable[][] grid) {
        return createProject("test project", columnNames, grid);
    }

    /**
     * Initializes the importing options for the CSV importer.
     * 
     * @param options
     * @param sep
     * @param limit
     * @param skip
     * @param ignoreLines
     * @param headerLines
     * @param guessValueType
     * @param ignoreQuotes
     */
    public static void prepareImportOptions(ObjectNode options,
            String sep, int limit, int skip, int ignoreLines,
            int headerLines, boolean guessValueType, boolean ignoreQuotes) {

        whenGetStringOption("separator", options, sep);
        whenGetIntegerOption("limit", options, limit);
        whenGetIntegerOption("skipDataLines", options, skip);
        whenGetIntegerOption("ignoreLines", options, ignoreLines);
        whenGetIntegerOption("headerLines", options, headerLines);
        whenGetBooleanOption("guessCellValueTypes", options, guessValueType);
        whenGetBooleanOption("processQuotes", options, !ignoreQuotes);
        whenGetBooleanOption("storeBlankCellsAsNulls", options, true);
    }

    /**
     * Cleans up the projects and jobs created with createCSVProject
     */
    @AfterMethod
    protected void cleanupProjectsAndJobs() {
        for (ImportingJob job : importingJobs) {
            ImportingManager.disposeJob(job.id);
        }
        for (Project project : projects) {
            ProjectManager.singleton.deleteProject(project.id);
        }
        servlet = null;
    }

    /**
     * Check that a project was created with the appropriate number of columns and rows.
     * 
     * @param project
     *            project to check
     * @param numCols
     *            expected column count
     * @param numRows
     *            expected row count
     */
    public static void assertProjectCreated(Project project, int numCols, int numRows) {
        Assert.assertNotNull(project);
        Assert.assertNotNull(project.columnModel);
        Assert.assertNotNull(project.columnModel.columns);
        Assert.assertEquals(project.columnModel.columns.size(), numCols);
        Assert.assertNotNull(project.rows);
        Assert.assertEquals(project.rows.size(), numRows);
    }

    /**
     * Check that a project was created with the appropriate number of columns, rows, and records.
     * 
     * @param project
     *            project to check
     * @param numCols
     *            expected column count
     * @param numRows
     *            expected row count
     * @param numRows
     *            expected record count
     */
    public static void assertProjectCreated(Project project, int numCols, int numRows, int numRecords) {
        assertProjectCreated(project, numCols, numRows);
        Assert.assertNotNull(project.recordModel);
        Assert.assertEquals(project.recordModel.getRecordCount(), numRecords);
    }

    public void log(Project project) {
        // some quick and dirty debugging
        StringBuilder sb = new StringBuilder();
        for (Column c : project.columnModel.columns) {
            sb.append(c.getName());
            sb.append("; ");
        }
        logger.info(sb.toString());
        for (Row r : project.rows) {
            sb = new StringBuilder();
            for (int i = 0; i < r.cells.size(); i++) {
                Cell c = r.getCell(i);
                if (c != null) {
                    sb.append(c.value);
                    sb.append("; ");
                } else {
                    sb.append("null; ");
                }
            }
            logger.info(sb.toString());
        }
    }

    // ----helpers----

    static public void whenGetBooleanOption(String name, ObjectNode options, Boolean def) {
        when(options.has(name)).thenReturn(true);
        when(options.get(name)).thenReturn(def ? BooleanNode.TRUE : BooleanNode.FALSE);
    }

    static public void whenGetIntegerOption(String name, ObjectNode options, int def) {
        when(options.has(name)).thenReturn(true);
        when(options.get(name)).thenReturn(new IntNode(def));
    }

    static public void whenGetStringOption(String name, ObjectNode options, String def) {
        when(options.has(name)).thenReturn(true);
        when(options.get(name)).thenReturn(new TextNode(def));
    }

    static public void whenGetObjectOption(String name, ObjectNode options, ObjectNode def) {
        when(options.has(name)).thenReturn(true);
        when(options.get(name)).thenReturn(def);
    }

    static public void whenGetArrayOption(String name, ObjectNode options, ArrayNode def) {
        when(options.has(name)).thenReturn(true);
        when(options.get(name)).thenReturn(def);
    }

    // Works for both int, String, and JSON arrays
    static public void verifyGetArrayOption(String name, ObjectNode options) {
        verify(options, times(1)).has(name);
        verify(options, times(1)).get(name);
    }

    /**
     * Lookup a control function by name and invoke it with a variable number of args
     */
    protected static Object invoke(String name, Object... args) {
        // registry uses static initializer, so no need to set it up
        Function function = ControlFunctionRegistry.getFunction(name);
        if (bindings == null) {
            bindings = new Properties();
        }
        if (function == null) {
            throw new IllegalArgumentException("Unknown function " + name);
        }
        if (args == null) {
            return function.call(bindings, new Object[0]);
        } else {
            return function.call(bindings, args);
        }
    }

    /**
     * Parse and evaluate a GREL expression and compare the result to the expect value
     *
     * @param bindings
     * @param test
     * @throws ParsingException
     */
    protected void parseEval(Properties bindings, String[] test)
            throws ParsingException {
        Evaluable eval = MetaParser.parse("grel:" + test[0]);
        Object result = eval.evaluate(bindings);
        if (test[1] != null) {
            Assert.assertNotNull(result, "Expected " + test[1] + " for test " + test[0]);
            Assert.assertEquals(result.toString(), test[1], "Wrong result for expression: " + test[0]);
        } else {
            Assert.assertNull(result, "Wrong result for expression: " + test[0]);
        }
    }

    /**
     * Parse and evaluate a GREL expression and compare the result an expected type using instanceof
     *
     * @param bindings
     * @param test
     * @throws ParsingException
     */
    protected void parseEvalType(Properties bindings, String test, @SuppressWarnings("rawtypes") Class clazz)
            throws ParsingException {
        Evaluable eval = MetaParser.parse("grel:" + test);
        Object result = eval.evaluate(bindings);
        Assert.assertTrue(clazz.isInstance(result), "Wrong result type for expression: " + test);
    }

    @AfterMethod
    public void TearDown() throws Exception {
        bindings = null;
    }

    protected ButterflyModule getCoreModule() {
        ButterflyModule coreModule = mock(ButterflyModule.class);
        when(coreModule.getName()).thenReturn("core");
        return coreModule;
    }

    protected void runAndWait(ProcessManager processManager, Process process, int timeout) {
        process.startPerforming(processManager);
        Assert.assertTrue(process.isRunning());
        int time = 0;
        try {
            while (process.isRunning() && time < timeout) {
                Thread.sleep(200);
                time += 200;
            }
        } catch (InterruptedException e) {
            Assert.fail("Test interrupted");
        }
        Assert.assertFalse(process.isRunning(), "Process failed to complete within timeout " + timeout);
    }

    public static void assertEqualsSystemLineEnding(String actual, String expected) {
        Assert.assertEquals(actual, expected.replaceAll("\n", System.lineSeparator()));
    }

    /**
     * Checks that the grid contents are equal in both projects. Differences in "cell indices" are not taken into
     * account as this is an internal detail: the goal is to assert equality of the user-facing parts of project data.
     * 
     * @param actual
     *            the actual project state
     * @param expected
     *            the expected project state
     */
    public static void assertProjectEquals(Project actual, Project expected) {
        assertEquals(actual.columnModel.getColumnNames(), expected.columnModel.getColumnNames(), "mismatching column names");
        int columnCount = actual.columnModel.columns.size();
        // TODO also check that ReconConfig and ReconStats are identical?
        assertEquals(actual.rows.size(), expected.rows.size(), "mismatching number of rows");

        List<Integer> actualCellIndices = actual.columnModel.columns.stream()
                .map(Column::getCellIndex)
                .collect(Collectors.toList());
        List<Integer> expectedCellIndices = expected.columnModel.columns.stream()
                .map(Column::getCellIndex)
                .collect(Collectors.toList());
        for (int i = 0; i != actual.rows.size(); i++) {
            Row actualRow = actual.rows.get(i);
            Row expectedRow = expected.rows.get(i);
            for (int j = 0; j != columnCount; j++) {
                Cell actualCell = actualRow.getCell(actualCellIndices.get(j));
                Cell expectedCell = expectedRow.getCell(expectedCellIndices.get(j));

                // special case for floating-point numbers to accept rounding errors
                if (expectedCell != null && expectedCell.value instanceof Double && actualCell != null && actualCell.value != null) {
                    assertEquals(
                            (double) actualCell.value,
                            (double) expectedCell.value,
                            EPSILON,
                            String.format("mismatching cells in row %d, column '%s'", i, actual.columnModel.columns.get(j)));
                } else {
                    assertEquals(
                            actualCell == null ? null : actualCell.value,
                            expectedCell == null ? null : expectedCell.value,
                            String.format("mismatching cells in row %d, column '%s'", i, actual.columnModel.columns.get(j)));
                }
            }
        }
    }

    /**
     * Utility method to return a default column name, for the purpose of generating expected grid contents in a concise
     * way in tests.
     */
    public static String numberedColumn(int index) {
        return OpenRefineMessage.importer_utilities_column() + " " + index;
    }

}
