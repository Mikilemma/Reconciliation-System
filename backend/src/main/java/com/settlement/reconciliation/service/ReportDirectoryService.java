package com.settlement.reconciliation.service;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class ReportDirectoryService {
    
    private static final String BASE_REPORTS_DIR = "reports";
    private static final DateTimeFormatter DIR_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public Path createReportDirectory(String settlementDate) throws IOException {
        try {
            // Parse settlement date
            LocalDate date = LocalDate.parse(settlementDate, FILE_DATE_FORMATTER);
            
            // Create directory name like "Nov 02 2025"
            String dirName = date.format(DIR_DATE_FORMATTER);
            
            // Create directory structure: reports/Nov 02 2025/
            Path reportDir = Paths.get(BASE_REPORTS_DIR, dirName);
            Files.createDirectories(reportDir);
            
            return reportDir;
        } catch (Exception e) {
            throw new IOException("Failed to create report directory for date: " + settlementDate, e);
        }
    }
    
    public Path getReportFilePath(String settlementDate, String filename) throws IOException {
        Path reportDir = createReportDirectory(settlementDate);
        return reportDir.resolve(filename);
    }
    
    public String generateReportFilename(String settlementDate, String format) {
        try {
            LocalDate date = LocalDate.parse(settlementDate, FILE_DATE_FORMATTER);
            String formattedDate = date.format(DIR_DATE_FORMATTER);
            return formattedDate + " Summary." + format.toLowerCase();
        } catch (Exception e) {
            return "Settlement_Report." + format.toLowerCase();
        }
    }
}
