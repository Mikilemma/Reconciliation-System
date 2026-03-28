package com.settlement.reconciliation.controller;

import com.settlement.reconciliation.model.ReconciliationSession;
import com.settlement.reconciliation.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/p2p/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{sessionId}/summary")
    public ResponseEntity<ReconciliationSession> getReconciliationSummary(@PathVariable String sessionId) {
        Optional<ReconciliationSession> summary = reportService.getReconciliationSummary(sessionId);
        return summary.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{sessionId}/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedReport(@PathVariable String sessionId) {
        Map<String, Object> detailedReport = reportService.generateDetailedReport(sessionId);
        if (detailedReport.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detailedReport);
    }

    @GetMapping("/settlement/{settlementDate}")
    public ResponseEntity<Map<String, Object>> getSettlementReport(@PathVariable String settlementDate) {
        try {
            // Validate date format
            LocalDate.parse(settlementDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            Map<String, Object> report = reportService.generateSettlementReport(settlementDate);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format. Use yyyy-MM-dd"));
        }
    }

    @GetMapping("/settlement/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSettlementReportBySession(@PathVariable String sessionId) {
        try {
            Map<String, Object> report = reportService.generateSettlementReportBySession(sessionId);
            if (report.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to generate report", "message", e.getMessage()));
        }
    }

    @GetMapping("/settlement/{settlementDate}/export/csv")
    public ResponseEntity<?> exportCSVReport(@PathVariable String settlementDate) {
        try {
            // Validate date format
            LocalDate.parse(settlementDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            java.util.List<java.util.Map<String,Object>> sessions = reportService.findSessionsSummaryByDate(settlementDate);
            if (sessions == null || sessions.isEmpty()) {
                return ResponseEntity.status(404).body(java.util.Map.of("error", "No settlement session found for date: " + settlementDate));
            }
            
            // Sort to get latest if multiple
            if (sessions.size() > 1) {
                sessions.sort((s1, S2) -> {
                    String d1 = (String) s1.get("createdAt"); // Assuming createdAt is in the map
                    String d2 = (String) S2.get("createdAt");
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    return d2.compareTo(d1);
                });
            }

            // Always pick the first (latest) session
            String targetSessionId = (String)sessions.get(0).get("id");
            String csvReport = reportService.generateCsvSummaryReportBySession(targetSessionId);

            LocalDate date = LocalDate.parse(settlementDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String formattedDate = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
            String filename = formattedDate + " Summary.csv";

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csvReport);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Error generating CSV report: " + e.getMessage()));
        }
    }

    @GetMapping("/settlement/{settlementDate}/export/excel")
    public ResponseEntity<?> exportExcelReport(@PathVariable String settlementDate) {
        try {
            // Validate date format
            LocalDate.parse(settlementDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            java.util.List<java.util.Map<String,Object>> sessions = reportService.findSessionsSummaryByDate(settlementDate);
            if (sessions == null || sessions.isEmpty()) {
                return ResponseEntity.status(404).body(java.util.Map.of("error", "No settlement session found for date: " + settlementDate));
            }
            
            // Sort to get latest if multiple
            if (sessions.size() > 1) {
                sessions.sort((s1, S2) -> {
                    // Assuming findSessionsSummaryByDate returns maps with createdAt
                    // If not, we might need to rely on the default order or fetch differently.
                    // Checking ReportService.findSessionsSummaryByDate: it puts "createdAt" in the map.
                    Object c1 = s1.get("createdAt");
                    Object c2 = S2.get("createdAt");
                    String d1 = c1 != null ? c1.toString() : "";
                    String d2 = c2 != null ? c2.toString() : "";
                    return d2.compareTo(d1);
                });
            }

            // Always pick the first (latest) session
            String targetSessionId = (String)sessions.get(0).get("id");
            byte[] excelContent = reportService.generateExcelSummaryReportBySession(targetSessionId);

            LocalDate date = LocalDate.parse(settlementDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String formattedDate = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
            String filename = formattedDate + " Summary.xlsx";

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(java.util.Map.of("error", "An unexpected error occurred", "message", e.getMessage()));
        }
    }

    // Session-scoped export endpoints
    @GetMapping("/settlement/session/{sessionId}/export/csv")
    public ResponseEntity<String> exportCSVReportBySession(@PathVariable String sessionId) {
        String csvReport = reportService.generateCsvSummaryReportBySession(sessionId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"session-" + sessionId + "-Summary.csv\"")
            .contentType(MediaType.TEXT_PLAIN)
            .body(csvReport);
    }

    @GetMapping("/settlement/session/{sessionId}/export/excel")
    public ResponseEntity<byte[]> exportExcelReportBySession(@PathVariable String sessionId) {
        byte[] excelContent = reportService.generateExcelSummaryReportBySession(sessionId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"session-" + sessionId + "-Summary.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excelContent);
    }
}
