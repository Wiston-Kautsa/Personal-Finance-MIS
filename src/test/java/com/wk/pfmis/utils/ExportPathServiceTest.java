package com.wk.pfmis.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportPathServiceTest {
    @TempDir
    Path downloads;

    @Test
    void createsPfmisDownloadsFolderAndAvoidsDuplicateFileOverwrite() throws Exception {
        String previousOverride = System.getProperty("pfmis.downloads.dir");
        System.setProperty("pfmis.downloads.dir", downloads.toString());
        try {
            Path pfmisFolder = downloads.resolve("PFMIS");
            assertFalse(Files.exists(pfmisFolder));

            Path first = ExportPathService.writeTextExport("PFMIS_Transactions_2026-08-11.csv", "first");
            Path second = ExportPathService.writeTextExport("PFMIS_Transactions_2026-08-11.csv", "second");

            assertTrue(Files.isDirectory(pfmisFolder));
            assertTrue(first.startsWith(pfmisFolder));
            assertTrue(second.startsWith(pfmisFolder));
            assertNotEquals(first, second);
            assertEquals("first", Files.readString(first));
            assertEquals("second", Files.readString(second));
        } finally {
            if (previousOverride == null) {
                System.clearProperty("pfmis.downloads.dir");
            } else {
                System.setProperty("pfmis.downloads.dir", previousOverride);
            }
        }
    }

    @Test
    void createsUniqueExportDirectoryUnderPfmisDownloads() throws Exception {
        String previousOverride = System.getProperty("pfmis.downloads.dir");
        System.setProperty("pfmis.downloads.dir", downloads.toString());
        try {
            Path first = ExportPathService.resolveExportDirectory("PFMIS_AI_Starter_Pack");
            Path second = ExportPathService.resolveExportDirectory("PFMIS_AI_Starter_Pack");

            assertTrue(first.startsWith(downloads.resolve("PFMIS")));
            assertTrue(second.startsWith(downloads.resolve("PFMIS")));
            assertNotEquals(first, second);
            assertTrue(Files.isDirectory(first));
            assertTrue(Files.isDirectory(second));
        } finally {
            if (previousOverride == null) {
                System.clearProperty("pfmis.downloads.dir");
            } else {
                System.setProperty("pfmis.downloads.dir", previousOverride);
            }
        }
    }
}
