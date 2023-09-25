/*******************************************************************************
 * Copyright (C) 2018, 2020 OpenRefine contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package org.openrefine.importing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.openrefine.ProjectMetadata;
import org.openrefine.importers.ImporterTest;
import org.openrefine.importers.ImporterUtilities;
import org.openrefine.importers.ImportingParserBase;
import org.openrefine.importers.SeparatorBasedImporter;
import org.openrefine.importing.ImportingJob.RetrievalRecord;
import org.openrefine.importing.ImportingUtilities.Progress;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.GridState;
import org.openrefine.model.IndexedRow;
import org.openrefine.model.Row;
import org.openrefine.util.JSONUtilities;
import org.openrefine.util.ParsingUtilities;
import org.openrefine.util.TestUtils;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ImportingUtilitiesTests extends ImporterTest {

    @Override
    @BeforeMethod
    public void setUp(){
        super.setUp();
    }
    
    @Test
    public void createProjectMetadataTest()
            throws Exception {
        ObjectNode optionObj = ParsingUtilities.evaluateJsonStringToObjectNode(
                "{\"projectName\":\"acme\",\"projectTags\":[],\"created\":\"2017-12-18T13:28:40.659\",\"modified\":\"2017-12-20T09:28:06.654\",\"creator\":\"\",\"contributors\":\"\",\"subject\":\"\",\"description\":\"\",\"rowCount\":50,\"customMetadata\":{}}");
        ProjectMetadata pm = ImportingUtilities.createProjectMetadata(optionObj);
        Assert.assertEquals(pm.getName(), "acme");
        Assert.assertEquals(pm.getEncoding(), "UTF-8");
        Assert.assertTrue(pm.getTags().length == 0);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testZipSlip() throws IOException {
        File tempDir = TestUtils.createTempDirectory("openrefine-zip-slip-test");
        // For CVE-2018-19859, issue #1840
        ImportingUtilities.allocateFile(tempDir, "../../tmp/script.sh");
    }
    
    @Test
    public void testMostCommonFormatEmpty() {
    	Assert.assertNull(ImporterUtilities.mostCommonFormat(Collections.emptyList()));
    }
    
    @Test
    public void testMostCommonFormat() {
    	ImportingFileRecord recA = mock(ImportingFileRecord.class);
    	when(recA.getFormat()).thenReturn("foo");
    	ImportingFileRecord recB = mock(ImportingFileRecord.class);
    	when(recB.getFormat()).thenReturn("bar");
    	ImportingFileRecord recC = mock(ImportingFileRecord.class);
    	when(recC.getFormat()).thenReturn("foo");
    	ImportingFileRecord recD = mock(ImportingFileRecord.class);
    	when(recD.getFormat()).thenReturn(null);
    	List<ImportingFileRecord> records = Arrays.asList(recA, recB, recC, recD);
    	
    	Assert.assertEquals(ImporterUtilities.mostCommonFormat(records), "foo");
    }

    @Test
    public void urlImporting() throws IOException {

        String RESPONSE_BODY = "{code:401,message:Unauthorised}";

        MockWebServer server = new MockWebServer();
        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody(RESPONSE_BODY);
        mockResponse.setResponseCode(401);
        server.start();
        server.enqueue(mockResponse);
        HttpUrl url = server.url("/random");
        String MESSAGE = String.format("HTTP error %d : %s for URL %s", 401,
                "Client Error", url);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        StringBody stringBody = new StringBody(url.toString(), ContentType.MULTIPART_FORM_DATA);
        builder = builder.addPart("download", stringBody);
        HttpEntity entity = builder.build();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        entity.writeTo(os);
        ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());

        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn(entity.getContentType().getValue());
        when(req.getParameter("download")).thenReturn(url.toString());
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentLength()).thenReturn((int) entity.getContentLength());
        when(req.getInputStream()).thenReturn(new MockServletInputStream(is));

        Properties parameters = ParsingUtilities.parseUrlParameters(req);
        RetrievalRecord retrievalRecord = new RetrievalRecord();
        ObjectNode progress = ParsingUtilities.mapper.createObjectNode();
        try {
            ImportingUtilities.retrieveContentFromPostRequest(req, parameters, job.getRawDataDir(), retrievalRecord, new ImportingUtilities.Progress() {
                @Override
                public void setProgress(String message, int percent) {
                    if (message != null) {
                        JSONUtilities.safePut(progress, "message", message);
                    }
                    JSONUtilities.safePut(progress, "percent", percent);
                }

                @Override
                public boolean isCanceled() {
                    return job.canceled;
                }
            });
            fail("No Exception was thrown");
        } catch (Exception exception) {
            assertEquals(exception.getMessage(), MESSAGE);
        } finally {
            server.close();
        }
    }

    public static class MockServletInputStream extends ServletInputStream {

        private final InputStream delegate;
        private boolean finished = false;

        public MockServletInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
        	finished = true;
            return delegate.read();
        }

		@Override
		public boolean isFinished() {
			return finished;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			// TODO
		}

    }

    /**
     * This tests both exploding a zip archive into it's constituent files
     * as well as importing them all (both) and making sure that the
     * recording of archive names and file names works correctly.
     *
     * It's kind of a lot to have in one test, but it's a sequence
     * of steps that need to be done in order.
     * @throws Exception 
     */
    @SuppressWarnings("unchecked")
    @Test
    public void importArchive() throws Exception{
        String filename = "movies.zip";
        String filepath = ClassLoader.getSystemResource(filename).getPath();
        // Make a copy in our data directory where it's expected
        File tmp = File.createTempFile("openrefine-test-movies", ".zip", job.getRawDataDir());
        tmp.deleteOnExit();
        FileUtils.copyFile(new File(filepath), tmp);

        Progress dummyProgress = new Progress() {
            @Override
            public void setProgress(String message, int percent) {}

            @Override
            public boolean isCanceled() {
                return false;
            }
        };

        List<ImportingFileRecord> fileRecords = new ArrayList<>();
        ImportingFileRecord fileRecord = new ImportingFileRecord(
        		null, tmp.getName(), filename, 0L, "upload", "application/x-zip-compressed", null, null, "UTF-8", null, null, null);

        assertTrue(ImportingUtilities.postProcessRetrievedFile(job.getRawDataDir(), tmp, fileRecord, fileRecords, dummyProgress));
        assertEquals(fileRecords.size(), 2);
        assertEquals(fileRecords.get(0).getFileName(), "movies-condensed.tsv");
        assertEquals(fileRecords.get(0).getArchiveFileName(), "movies.zip");
        assertEquals(fileRecords.get(1).getFileName(), "movies.tsv");

        ObjectNode options = ParsingUtilities.mapper.createObjectNode();
        JSONUtilities.safePut(options, "includeArchiveFileName", true);
        JSONUtilities.safePut(options, "includeFileSources", true);

        ImportingParserBase parser = new SeparatorBasedImporter(runner());
        List<Exception> exceptions = new ArrayList<Exception>();
        GridState grid = parser.parse(
                metadata,
                job,
                IteratorUtils.toList(fileRecords.iterator()),
                "tsv",
                -1,
                options
                );
        assertEquals(exceptions.size(), 0);

        ColumnModel columnModel = grid.getColumnModel();
        List<Row> rows = grid.collectRows().stream().map(IndexedRow::getRow).collect(Collectors.toList());
        assertEquals(columnModel.getColumns().get(0).getName(),"Archive");
        assertEquals(rows.get(0).getCellValue(0),"movies.zip");
        assertEquals(columnModel.getColumns().get(1).getName(),"File");
        assertEquals(rows.get(0).getCellValue(1),"movies-condensed.tsv");
        assertEquals(columnModel.getColumns().get(2).getName(),"name");
        assertEquals(rows.get(0).getCell(2).getValue(),"Wayne's World");

        // Make sure we imported both files contained in the zip file
        assertEquals(rows.size(), 252);

        ArrayNode importOptionsArray = metadata.getImportOptionMetadata();
        assertEquals(importOptionsArray.size(), 2);
        ObjectNode importOptions = (ObjectNode)importOptionsArray.get(0);
        assertEquals(importOptions.get("archiveFileName").asText(), "movies.zip");
        assertEquals(importOptions.get("fileSource").asText(), "movies-condensed.tsv");
        assertTrue(importOptions.get("includeFileSources").asBoolean());
        assertTrue(importOptions.get("includeArchiveFileName").asBoolean());

        importOptions = (ObjectNode)importOptionsArray.get(1);
        assertEquals(importOptions.get("fileSource").asText(), "movies.tsv");
        assertEquals(importOptions.get("archiveFileName").asText(), "movies.zip");
    }
    
    @Test
    public void testExtractFilenameFromSparkURI() {
    	Assert.assertEquals(ImportingUtilities.extractFilenameFromSparkURI("hdfs:///data/records"), "records");
    	Assert.assertNull(ImportingUtilities.extractFilenameFromSparkURI("////"));
    }

}
