package com.wk.pfmis.models;

public class BackupRecord {
    private final String backupFile;
    private final String createdAt;
    private final long fileSize;
    private final String checksum;
    private final String contents;
    private final String status;

    public BackupRecord(String backupFile, String createdAt, long fileSize, String checksum, String contents, String status) {
        this.backupFile = backupFile;
        this.createdAt = createdAt;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.contents = contents;
        this.status = status;
    }

    public String getBackupFile() {
        return backupFile;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getContents() {
        return contents;
    }

    public String getStatus() {
        return status;
    }
}
