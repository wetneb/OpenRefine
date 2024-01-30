
package org.openrefine.importing;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.testng.annotations.Test;

import org.openrefine.importing.ImportingJob.ImportingJobConfig;
import org.openrefine.importing.ImportingJob.RetrievalRecord;

public class EncodingGuesserTests {

    // Guessing isn't as reliable for single-byte encodings, so we focus on a few multibyte
    // non-UTF8 encodings which are still in use (but <1% prevalence on web)
    static String[] ENCODINGS = {
            "big5",
            "euc-jp",
            "euc-kr",
            "shift_jis",
    };

    private static File getTestDir() {
        String dir = ClassLoader.getSystemResource(ENCODINGS[0] + ".txt").getPath();
        dir = dir.substring(0, dir.lastIndexOf('/'));
        return new File(dir);
    }

    public class ImportingJobStub extends ImportingJob {

        public ImportingJobStub() {
            super(1, getTestDir());
        }

        @Override
        public File getRawDataDir() {
            return this.dir;
        }
    }

    @Test
    public void testEncodingGuesser() throws IOException {

        for (String encoding : ENCODINGS) {
            checkEncoding(encoding + ".txt", encoding);
        }

        checkEncoding("example-latin1.tsv", "windows-1252"); // close enough - these overlap a lot
        checkEncoding("example-utf8.tsv", "utf-8");
        checkEncoding("csv-with-bom.csv", "utf-8-bom");
    }

    private void checkEncoding(String filename, String encoding) throws IOException {
        ImportingJob job = new ImportingJobStub();
        ImportingJobConfig config = job.getJsonConfig();
        RetrievalRecord retrievalRecord = new RetrievalRecord();
        ImportingFileRecord importingFileRecord = new ImportingFileRecord(filename, filename,
                0, null, null, null, null, null, null, null, null);
        retrievalRecord.files = Collections.singletonList(importingFileRecord);
        config.retrievalRecord = retrievalRecord;

        EncodingGuesser.guess(job);

        List<ImportingFileRecord> fileRecords = retrievalRecord.files;
        assertNotNull(fileRecords);
        assertEquals(fileRecords.size(), 1);
        ImportingFileRecord record = fileRecords.get(0);
        assertEquals(record.getEncoding().toLowerCase(), encoding);
    }
}
