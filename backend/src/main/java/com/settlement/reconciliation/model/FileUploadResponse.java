package com.settlement.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private UploadedFile uploadedFile;
    private Map<String, Object> summaryReportData;
}
