package com.billing.simple.billsoft.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupExportResult {
    private boolean success;
    private String filePath;
    private String filename;
    private long fileSizeBytes;
    private String checksumSha256;
    private String format;
    private String exportedAt;
    private int firmsCount;
    private int recordCount;
    private String message;
}
