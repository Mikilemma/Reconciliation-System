package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.ReconciliationResult;
import com.settlement.reconciliation.model.ReconciliationSession;
import com.settlement.reconciliation.model.TransactionPayable;
import com.settlement.reconciliation.repository.ReconciliationResultRepository;
import com.settlement.reconciliation.repository.ReconciliationSessionRepository;
import com.settlement.reconciliation.repository.TransactionPayableRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportService {

    private final ReconciliationResultRepository reconciliationResultRepository;
    private final ReconciliationSessionRepository reconciliationSessionRepository;
    private final TransactionPayableRepository transactionPayableRepository;
    private final EnhancedSettlementCalculationService settlementCalculationService;
    private final com.settlement.reconciliation.repository.UploadedFileRepository uploadedFileRepository;
    private final Map<String, Map<String, Object>> sessionReportCache = new ConcurrentHashMap<>();

    public ReportService(ReconciliationResultRepository reconciliationResultRepository,
                         ReconciliationSessionRepository reconciliationSessionRepository,
                         TransactionPayableRepository transactionPayableRepository,
                         EnhancedSettlementCalculationService settlementCalculationService,
                         com.settlement.reconciliation.repository.UploadedFileRepository uploadedFileRepository) {
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.reconciliationSessionRepository = reconciliationSessionRepository;
        this.transactionPayableRepository = transactionPayableRepository;
        this.settlementCalculationService = settlementCalculationService;
        this.uploadedFileRepository = uploadedFileRepository;
    }

    public Map<String, Object> generateSummaryReport(String sessionId) {
        System.out.println("DEBUG: generateSummaryReport for session: " + sessionId);
        List<ReconciliationResult> results = reconciliationResultRepository.findBySessionId(sessionId);
        List<TransactionPayable> payables = transactionPayableRepository.findBySessionId(sessionId);
        System.out.println("DEBUG: results size: " + results.size() + ", payables size: " + payables.size());
        
        Map<String, Object> report = new HashMap<>();
        
        BigDecimal atmCwAmount = BigDecimal.ZERO;
        BigDecimal posPurAmount = BigDecimal.ZERO;
        BigDecimal atmWithdrawalFee = BigDecimal.ZERO;
        BigDecimal posPurchaseFee = BigDecimal.ZERO;
        BigDecimal atmBalanceInquiryFee = BigDecimal.ZERO;
        
        int atmCwCount = 0;
        int posPurCount = 0;
        
        // Aggregate from reconciliation results
        for (ReconciliationResult result : results) {
            aggregateTransaction(result.getDetails(), result.getAmount(), 
                atmCwAmount, posPurAmount, atmWithdrawalFee, posPurchaseFee, atmBalanceInquiryFee, 
                atmCwCount, posPurCount, true);
            // Update accumulators (since Java is pass-by-value, we need a better way)
        }

        // Prefer the enhanced settlement calculation for accurate fees/commissions.
        try {
            EnhancedSettlementCalculationService.SettlementCalculationResult calc = 
                settlementCalculationService.calculateSettlement(sessionId);
            EnhancedSettlementCalculationService.DebitSideCalculation debit = calc.getDebitSide();
            EnhancedSettlementCalculationService.CreditSideCalculation credit = calc.getCreditSide();

            Map<String, Object> summaryData = new HashMap<>();
            summaryData.put("atmCwAmount", credit.getAtmCashWithdrawal());
            summaryData.put("posPurAmount", debit.getRemoteOnUsEposPurchase());
            summaryData.put("atmWithdrawalFee", credit.getAtmWithdrawalFee());
            summaryData.put("posPurchaseFee", BigDecimal.ZERO); // TODO: derive if needed from calculation
            summaryData.put("atmBalanceInquiryFee", credit.getAtmBalanceInquiryFee());

            // New fields exposed for UI/reporting
            summaryData.put("onUsBalanceInquiryCommissionToEthSwitch", debit.getOnUsBalanceInquiryCommissionToEthSwitch());
            summaryData.put("remoteOnUsEposPurchasePlusCommission", debit.getRemoteOnUsEposPurchasePlusCommission());
            summaryData.put("remoteOnUsDisputeChargebackAmountCommission", credit.getRemoteOnUsDisputeChargebackAmountCommission());

            summaryData.put("totalTransactions", results.size() + payables.size());

            report.put("sessionId", sessionId);
            report.put("summary", summaryData);
            report.put("title", "Member Net Position Summary Report");

            return report;
        } catch (Exception e) {
            // Fallback: if enhanced calculation fails, use legacy string-based aggregation
            System.out.println("WARN: Enhanced settlement calculation failed, falling back to legacy aggregation: " + e.getMessage());
        }

        // Functional approach to update sums (legacy fallback)
        final BigDecimal[] totals = new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        final int[] counts = new int[] {0, 0};

        for (ReconciliationResult result : results) {
            updateTotals(result.getDetails(), result.getAmount(), totals, counts);
        }
        
        // Also aggregate from payables (for summary reports that didn't match)
        for (TransactionPayable payable : payables) {
            BigDecimal amount = payable.getCredit().add(payable.getDebit());
            updateTotals(payable.getDescription(), amount, totals, counts);
        }
        
        Map<String, Object> summaryData = new HashMap<>();
        summaryData.put("atmCwAmount", totals[0]);
        summaryData.put("posPurAmount", totals[1]);
        summaryData.put("atmWithdrawalFee", totals[2]);
        summaryData.put("posPurchaseFee", totals[3]);
        summaryData.put("atmBalanceInquiryFee", totals[4]);

        // Ensure UI doesn't break if enhanced calculation wasn't available
        summaryData.put("onUsBalanceInquiryCommissionToEthSwitch", BigDecimal.ZERO);
        summaryData.put("remoteOnUsEposPurchasePlusCommission", BigDecimal.ZERO);
        summaryData.put("remoteOnUsDisputeChargebackAmountCommission", BigDecimal.ZERO);

        summaryData.put("totalTransactions", results.size() + payables.size());
        
        report.put("sessionId", sessionId);
        report.put("summary", summaryData);
        report.put("title", "Member Net Position Summary Report");
        
        return report;
    }

    private void updateTotals(String details, BigDecimal amount, BigDecimal[] totals, int[] counts) {
        if (details == null) return;
        details = details.toLowerCase();
        
        if (details.contains("atm issuing cash withdrawal") || details.contains("atm cash withdrawal") || details.contains("atm cw")) {
            totals[0] = totals[0].add(amount);
            counts[0]++;
        } else if (details.contains("pos purchase") || details.contains("pos pur")) {
            totals[1] = totals[1].add(amount);
            counts[1]++;
        } else if (details.contains("atm transaction charges") || details.contains("withdrawal fee")) {
            totals[2] = totals[2].add(amount);
        } else if (details.contains("pos") && (details.contains("fee") || details.contains("charge"))) {
            totals[3] = totals[3].add(amount);
        } else if (details.contains("balance inquiry")) {
            totals[4] = totals[4].add(amount);
        }
    }

    private void aggregateTransaction(String details, BigDecimal amount, BigDecimal atmCwAmount, BigDecimal posPurAmount, BigDecimal atmWithdrawalFee, BigDecimal posPurchaseFee, BigDecimal atmBalanceInquiryFee, int atmCwCount, int posPurCount, boolean isResult) {
        // This was a dummy method for structure illustration
    }

    public Optional<ReconciliationSession> getReconciliationSummary(String sessionId) {
        return reconciliationSessionRepository.findById(sessionId);
    }

    public Map<String, Object> generateDetailedReport(String sessionId) {
        Map<String, Object> detailedReport = new HashMap<>();
        List<ReconciliationResult> results = reconciliationResultRepository.findBySessionId(sessionId);
        detailedReport.put("results", results);
        detailedReport.put("summary", generateSummaryReport(sessionId).get("summary"));
        return detailedReport;
    }

    public Map<String, Object> generateSettlementReport(String settlementDate) {
        java.util.List<ReconciliationSession> sessions = reconciliationSessionRepository.findAllBySettlementDate(settlementDate);
        if (sessions == null || sessions.isEmpty()) {
            return new HashMap<>();
        }

        // Deterministic order (latest first) for metadata consistency.
        sessions.sort((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt()));
        if (sessions.size() == 1) {
            return generateSettlementReportBySession(sessions.get(0).getId());
        }

        List<Map<String, Object>> perSessionReports = new ArrayList<>();
        for (ReconciliationSession session : sessions) {
            Map<String, Object> sessionReport = generateSettlementReportBySession(session.getId());
            if (!sessionReport.isEmpty()) {
                perSessionReports.add(sessionReport);
            }
        }
        if (perSessionReports.isEmpty()) {
            return new HashMap<>();
        }

        return aggregateSettlementReports(perSessionReports, settlementDate, "Member Net Position Summary Report");
    }

    public Map<String, Object> generateSettlementReportBySession(String sessionId) {
        Map<String, Object> cached = sessionReportCache.get(sessionId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        Map<String, Object> report = new HashMap<>();
        
        // Get settlement calculation
        EnhancedSettlementCalculationService.SettlementCalculationResult calculation = 
            settlementCalculationService.calculateSettlement(sessionId);
        
        // Get session info
        Optional<ReconciliationSession> sessionOpt = reconciliationSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return report;
        }
        
        ReconciliationSession session = sessionOpt.get();
        
        // Build report structure matching frontend expectations
        EnhancedSettlementCalculationService.DebitSideCalculation debit = calculation.getDebitSide();
        EnhancedSettlementCalculationService.CreditSideCalculation credit = calculation.getCreditSide();
        BigDecimal debitDisplayTotal = calculateDebitSideDisplayTotal(debit);
        
        Map<String, Object> debitSide = new HashMap<>();
        debitSide.put("onUsATMCommission", debit.getOnUsAtmWithdrawal().getCommission().doubleValue());
        debitSide.put("remoteOnUsATMCommission", debit.getRemoteOnUsAtmWithdrawal().getCommission().doubleValue());
        debitSide.put("onUsBalanceInquiryCommissionToEthSwitch", debit.getOnUsBalanceInquiryCommissionToEthSwitch().doubleValue());
        debitSide.put("remoteOnUsEposPurchasePlusCommission", debit.getRemoteOnUsEposPurchasePlusCommission().doubleValue());
        debitSide.put("balanceInquiryFee", debit.getRemoteOnUsAtmWithdrawal().getBalanceInquiryFee().doubleValue());
        debitSide.put("remoteOnUsPOSAmount", debit.getRemoteOnUsEposPurchase().doubleValue());
        debitSide.put("outwardP2PAAmount", debit.getOutwardP2pAmount().doubleValue()); // Fixed typo in key if any, but kept consistent
        debitSide.put("outwardP2PAmount", debit.getOutwardP2pAmount().doubleValue());
        debitSide.put("p2PCommission", debit.getP2pCommissionToEthswitch().doubleValue());
        debitSide.put("onUsATMFee", debit.getOnUsAtmFeeToEthswitch().doubleValue());
        debitSide.put("remoteOnUsATMFee", debit.getRemoteOnUsAtmFeeToEthswitch().doubleValue());
        debitSide.put("totalAmount", debitDisplayTotal.doubleValue());
        
        Map<String, Object> creditSide = new HashMap<>();
        creditSide.put("atmCashWithdrawal", credit.getAtmCashWithdrawal().doubleValue());
        creditSide.put("incomingP2P", credit.getIncomingP2p().doubleValue());
        creditSide.put("remoteOnUsDisputeChargebackAmountCommission", credit.getRemoteOnUsDisputeChargebackAmountCommission().doubleValue());
        creditSide.put("onUsDisputeChargebackCommission", credit.getOnUsDisputeChargebackCommission().doubleValue());
        creditSide.put("atmWithdrawalFee", credit.getAtmWithdrawalFee().doubleValue());
        creditSide.put("balanceInquiryFee", credit.getAtmBalanceInquiryFee().doubleValue());
        creditSide.put("totalAmount", credit.getTotal().doubleValue());
        
        report.put("reportTitle", "Member Net Position Summary Report");
        report.put("settlementDate", session.getSettlementDate() != null ? session.getSettlementDate() : "");
        report.put("formattedDate", session.getSettlementDate() != null ? session.getSettlementDate() : "");
        report.put("debitSide", debitSide);
        report.put("creditSide", creditSide);
        report.put("totalDebitAmount", debitDisplayTotal.doubleValue());
        report.put("totalCreditAmount", credit.getTotal().doubleValue());
        // Keep net value aligned with the totals displayed in report UI/export.
        report.put("netSettlementAmount", credit.getTotal().subtract(debitDisplayTotal).doubleValue());
        report.put("sessionId", sessionId);
        
        sessionReportCache.put(sessionId, report);
        return report;
    }

    public String generateCsvSummaryReport(String settlementDate) {
        // Find all sessions for the date and pick the latest one
        java.util.List<ReconciliationSession> sessions = reconciliationSessionRepository.findAllBySettlementDate(settlementDate);
        if (sessions == null || sessions.isEmpty()) {
            return "No settlement session found for date: " + settlementDate;
        }
        
        // Sort by createdAt desc
        sessions.sort((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt()));
        ReconciliationSession session = sessions.get(0);
        
        EnhancedSettlementCalculationService.SettlementCalculationResult calculation = 
            settlementCalculationService.calculateSettlement(session.getId());

        return generateFormattedCsvReport(calculation, settlementDate, session);
    }

    public String generateCsvSummaryReportBySession(String sessionId) {
        Optional<ReconciliationSession> sessionOpt = reconciliationSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return "No settlement session found for session: " + sessionId;
        }
        ReconciliationSession session = sessionOpt.get();
        EnhancedSettlementCalculationService.SettlementCalculationResult calculation = 
            settlementCalculationService.calculateSettlement(sessionId);
        return generateFormattedCsvReport(calculation, session.getSettlementDate(), session);
    }

    public byte[] generateExcelSummaryReportBySession(String sessionId) {
        Optional<ReconciliationSession> sessionOpt = reconciliationSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return new byte[0];
        }
        ReconciliationSession session = sessionOpt.get();
        EnhancedSettlementCalculationService.SettlementCalculationResult calculation = 
            settlementCalculationService.calculateSettlement(sessionId);
        List<String> lines = buildFormattedCsvLines(calculation, session.getSettlementDate(), session);
        
        try {
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Settlement");

            // System Colors - Tsehay Bank
            byte[] greenRgb = new byte[]{(byte)37, (byte)91, (byte)48}; // #255B30
            byte[] yellowRgb = new byte[]{(byte)240, (byte)206, (byte)13}; // #F0CE0D
            byte[] lightGrayRgb = new byte[]{(byte)242, (byte)242, (byte)242}; // #F2F2F2
            
            org.apache.poi.xssf.usermodel.XSSFColor greenColor = new org.apache.poi.xssf.usermodel.XSSFColor(greenRgb, null);
            org.apache.poi.xssf.usermodel.XSSFColor yellowColor = new org.apache.poi.xssf.usermodel.XSSFColor(yellowRgb, null);
            org.apache.poi.xssf.usermodel.XSSFColor lightGrayColor = new org.apache.poi.xssf.usermodel.XSSFColor(lightGrayRgb, null);

            // Title Style - Green header
            org.apache.poi.xssf.usermodel.XSSFCellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short)14);
            titleFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(greenColor);
            titleStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            titleStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            // Header Style - Green background
            org.apache.poi.xssf.usermodel.XSSFCellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(greenColor);
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            // Alternating row style - Light gray
            org.apache.poi.xssf.usermodel.XSSFCellStyle altRowStyle = workbook.createCellStyle();
            altRowStyle.setFillForegroundColor(lightGrayColor);
            altRowStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            altRowStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            altRowStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            altRowStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            // Normal row style
            org.apache.poi.ss.usermodel.CellStyle normalStyle = workbook.createCellStyle();
            normalStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            normalStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            normalStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            // Number style
            org.apache.poi.ss.usermodel.CellStyle numberStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.DataFormat df = workbook.createDataFormat();
            numberStyle.setDataFormat(df.getFormat("#,##0.00"));
            numberStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
            numberStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            numberStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            numberStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            // Number style for alternating rows
            org.apache.poi.xssf.usermodel.XSSFCellStyle numberAltStyle = workbook.createCellStyle();
            numberAltStyle.setDataFormat(df.getFormat("#,##0.00"));
            numberAltStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
            numberAltStyle.setFillForegroundColor(lightGrayColor);
            numberAltStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            numberAltStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            numberAltStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            numberAltStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            // Total Style - Yellow highlight
            org.apache.poi.xssf.usermodel.XSSFCellStyle totalStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(yellowColor);
            totalStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            totalStyle.setDataFormat(df.getFormat("#,##0.00"));
            totalStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
            totalStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            totalStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            totalStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            totalStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            // Footer Style - Green background
            org.apache.poi.xssf.usermodel.XSSFCellStyle footerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font footerFont = workbook.createFont();
            footerFont.setBold(true);
            footerFont.setItalic(true);
            footerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            footerStyle.setFont(footerFont);
            footerStyle.setFillForegroundColor(greenColor);
            footerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            footerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            footerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);

            org.apache.poi.xssf.usermodel.XSSFCellStyle totalTextStyle = workbook.createCellStyle();
            totalTextStyle.cloneStyleFrom(totalStyle);
            totalTextStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);

            int rowNum = 0;
            int dataRowCounter = 0;
            int firstDataRow = -1;
            
            for (String line : lines) {
                java.util.List<String> fields = parseCsvLine(line);
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum);
                row.setHeightInPoints(20); // Slightly taller rows

                // Title row (first line)
                if (rowNum == 0) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(0);
                    cell.setCellValue(fields.size() > 0 ? fields.get(0) : "Settlement Report");
                    cell.setCellStyle(titleStyle);
                    row.setHeightInPoints(30);
                    int mergeTo = Math.max(11, fields.size() - 1);
                    try { sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,0,mergeTo)); } catch (Exception ignored) {}
                    rowNum++;
                    continue;
                }

                RowKind rowKind = classifyRow(fields, rowNum);
                boolean isAlternate = rowKind == RowKind.DATA && (dataRowCounter % 2 == 1);
                if (rowKind == RowKind.DATA) {
                    if (firstDataRow < 0) firstDataRow = rowNum;
                    dataRowCounter++;
                }

                for (int colNum = 0; colNum < fields.size(); colNum++) {
                    String field = fields.get(colNum);
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(colNum);
                    if (field == null || field.trim().isEmpty()) {
                        cell.setCellValue("");
                        cell.setCellStyle(resolveTextStyle(rowKind, isAlternate, headerStyle, totalTextStyle, altRowStyle, normalStyle));
                        continue;
                    }
                    
                    String cleaned = field.trim().replaceAll("^\"\\s*|\\s*\"$", "").trim();
                    if (cleaned.equals("-   ") || cleaned.equals("-")) {
                        cell.setCellValue("");
                        cell.setCellStyle(resolveTextStyle(rowKind, isAlternate, headerStyle, totalTextStyle, altRowStyle, normalStyle));
                        continue;
                    }

                    // Try to parse as number
                    String numericCandidate = cleaned.replaceAll(",", "");
                    boolean isNumber = false;
                    double val = 0.0;
                    try {
                        if (numericCandidate.startsWith("(") && numericCandidate.endsWith(")")) {
                            String inner = numericCandidate.substring(1, numericCandidate.length() - 1);
                            val = -Double.parseDouble(inner);
                        } else {
                            val = Double.parseDouble(numericCandidate);
                        }
                        isNumber = true;
                    } catch (Exception ignored) {}

                    if (isNumber) {
                        cell.setCellValue(val);
                        if (rowKind == RowKind.TOTAL) {
                            cell.setCellStyle(totalStyle);
                        } else {
                            cell.setCellStyle(isAlternate ? numberAltStyle : numberStyle);
                        }
                    } else {
                        cell.setCellValue(cleaned);
                        cell.setCellStyle(resolveTextStyle(rowKind, isAlternate, headerStyle, totalTextStyle, altRowStyle, normalStyle));
                    }
                }
                rowNum++;
            }

            // Add Tsehay Bank footer
            org.apache.poi.ss.usermodel.Row footerRow = sheet.createRow(rowNum + 1);
            footerRow.setHeightInPoints(25);
            org.apache.poi.ss.usermodel.Cell footerCell = footerRow.createCell(0);
            footerCell.setCellValue("© Tsehay Bank S.C. - Settlement Report");
            footerCell.setCellStyle(footerStyle);
            try { 
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum + 1, rowNum + 1, 0, 11)); 
            } catch (Exception ignored) {}

            // Freeze top 6 rows (header section)
            sheet.createFreezePane(0, firstDataRow > 0 ? firstDataRow : 6);

            // Auto-size columns
            for (int i = 0; i < 12; i++) {
                try { 
                    sheet.autoSizeColumn(i);
                    // Add some padding
                    sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 512);
                } catch (Exception ignored) {}
            }

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();
        } catch (Exception e) {
            System.out.println("ERROR generating Excel: " + e.getMessage());
            e.printStackTrace();
            return String.join("\n", lines).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public java.util.List<java.util.Map<String,Object>> findSessionsSummaryByDate(String settlementDate) {
        java.util.List<ReconciliationSession> sessions = reconciliationSessionRepository.findAllBySettlementDate(settlementDate);
        java.util.List<java.util.Map<String,Object>> result = new java.util.ArrayList<>();
        for (ReconciliationSession s : sessions) {
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("id", s.getId());
            m.put("settlementDate", s.getSettlementDate());
            m.put("createdAt", s.getCreatedAt());
            m.put("totalTransactions", s.getTotalTransactions());
            m.put("settledCount", s.getSettledCount());
            m.put("discrepantCount", s.getDiscrepantCount());
            result.add(m);
        }
        return result;
    }

    private String generateFormattedCsvReport(
            EnhancedSettlementCalculationService.SettlementCalculationResult calculation,
            String settlementDate,
            ReconciliationSession session) {
        return String.join("\n", buildFormattedCsvLines(calculation, settlementDate, session));
    }

    private List<String> buildFormattedCsvLines(
            EnhancedSettlementCalculationService.SettlementCalculationResult calculation,
            String settlementDate,
            ReconciliationSession session) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        List<String> lines = new ArrayList<>();

        // Header section - removed empty cols C and D
        lines.add("Member Net Position Summary Report,,,,,,,,,");
        lines.add("Settlement ," + (session.getSettlementDate() != null ? session.getSettlementDate() : settlementDate) + ",,,,,,,,");
        lines.add("Currency:,ETB,,,,,,,,");
        lines.add("Institution:,0017,,,,,,,,");
        lines.add("Member,,,,,,,,,");
        // Row 6: Removed C and D
        lines.add("0017 - Tsehay Bank,,Debit value,Credit value,Net value,Debit Side,,,Credit Side, Amount ");

        EnhancedSettlementCalculationService.DebitSideCalculation debit = calculation.getDebitSide();
        EnhancedSettlementCalculationService.CreditSideCalculation credit = calculation.getCreditSide();

        // Try to read TSEHAY summary data from database
        Map<String, BigDecimal[]> tsehayData = readTsehaySummaryData(session.getId());

        // Transaction rows helper
        java.util.function.Function<String, BigDecimal[]> getTsehay = (key) -> {
            if (tsehayData == null || tsehayData.isEmpty()) return null;

            String wanted = normalizeCategory(key);

            // Prefer exact normalized match for deterministic mapping.
            for (Map.Entry<String, BigDecimal[]> entry : tsehayData.entrySet()) {
                if (normalizeCategory(entry.getKey()).equals(wanted)) {
                    return entry.getValue();
                }
            }

            // Fallback to contains matching, but deterministic by key order.
            List<String> sortedKeys = new ArrayList<>(tsehayData.keySet());
            Collections.sort(sortedKeys);
            for (String tKey : sortedKeys) {
                if (normalizeCategory(tKey).contains(wanted)) {
                    return tsehayData.get(tKey);
                }
            }
            return null;
        };

        // Transaction rows
        // 1. ATM CW Transaction Amount
        BigDecimal[] atmValues = getTsehay.apply("ATM CW Transaction Amount");
        BigDecimal atmCwDebit = atmValues != null ? atmValues[0] : debit.getOnUsAtmWithdrawal().getAmount().add(debit.getRemoteOnUsAtmWithdrawal().getAmount());
        BigDecimal atmCwCredit = atmValues != null ? atmValues[1] : credit.getAtmCashWithdrawal();
        BigDecimal atmCwNet = atmValues != null ? atmValues[2] : atmCwCredit.subtract(atmCwDebit);
        lines.add(formatTransactionRow("  ATM CW Transaction Amount", atmCwDebit, atmCwCredit, atmCwNet,
            "Payment & Settlement  - Net", "",
            "ATM Cash Withdrawal", formatAmount(credit.getAtmCashWithdrawal(), df)));

        // 2. Account2Account credit
        BigDecimal[] a2aCreditValues = getTsehay.apply("Account2Account credit");
        BigDecimal a2aCreditDebit = a2aCreditValues != null ? a2aCreditValues[0] : debit.getOutwardP2pAmount();
        BigDecimal a2aCreditCredit = a2aCreditValues != null ? a2aCreditValues[1] : credit.getIncomingP2p();
        BigDecimal a2aCreditNet = a2aCreditValues != null ? a2aCreditValues[2] : a2aCreditCredit.subtract(a2aCreditDebit);
        lines.add(formatTransactionRow("  Account2Account credit ", a2aCreditDebit, a2aCreditCredit, a2aCreditNet,
            "Payment & Settlement  - Net ", "",
            "Incoming P2P ", formatAmount(credit.getIncomingP2p(), df)));

        // 3. Account2Account debit
        BigDecimal[] a2aDebitValues = getTsehay.apply("Account2Account debit");
        BigDecimal a2aDebitDebit = a2aDebitValues != null ? a2aDebitValues[0] : debit.getOutwardP2pAmount();
        BigDecimal a2aDebitCredit = a2aDebitValues != null ? a2aDebitValues[1] : debit.getOutwardP2pAmount();
        BigDecimal a2aDebitNet = a2aDebitValues != null ? a2aDebitValues[2] : BigDecimal.ZERO;
        lines.add(formatTransactionRow("  Account2Account debit ", a2aDebitDebit, a2aDebitCredit, a2aDebitNet,
            "Ethswitch Payable - OFF US Dispute Charge Back (CBE)", "", 
            "ATM Withdrawal Fee", formatAmount(credit.getAtmWithdrawalFee(), df)));

        // 4. POS PUR THEM-ON-THEM (use calculated value to ensure commission is included)
        BigDecimal posDebit = debit.getRemoteOnUsEposPurchasePlusCommission();
        BigDecimal posCredit = BigDecimal.ZERO;
        BigDecimal posNet = posCredit.subtract(posDebit);
        lines.add(formatTransactionRow("  POS PUR THEM-ON-THEM", posDebit, posCredit, posNet,
            "Ethswitch Payable - ATM Withdrawal Second Presentment", "", 
            "ATM Balance Inquiry Fee", formatAmount(credit.getAtmBalanceInquiryFee(), df)));

        // 5. ATM Withdrawal FEE
        BigDecimal[] atmFeeValues = getTsehay.apply("ATM Withdrawal FEE");
        BigDecimal atmFeeDebit = atmFeeValues != null ? atmFeeValues[0] : debit.getOnUsAtmWithdrawal().getCommission().add(debit.getRemoteOnUsAtmWithdrawal().getCommission());
        BigDecimal atmFeeCredit = atmFeeValues != null ? atmFeeValues[1] : credit.getAtmWithdrawalFee();
        BigDecimal atmFeeNet = atmFeeValues != null ? atmFeeValues[2] : atmFeeCredit.subtract(atmFeeDebit);
        lines.add(formatTransactionRow("  ATM Withdrawal FEE", atmFeeDebit, atmFeeCredit, atmFeeNet,
            "Ethswitch Payable - OnUs ATM CW & Comm", formatAmount(debit.getOnUsAtmWithdrawal().getCommission(), df),
            "Reversal for twice Debit Remote ON US POS Purchas   ", ""));

        // 6. P2P C2A Interinst fee (Acq part)
        BigDecimal[] p2pAcqValues = getTsehay.apply("P2P C2A Interinst fee (Acq part)");
        BigDecimal p2pAcqDebit = p2pAcqValues != null ? p2pAcqValues[0] : BigDecimal.ZERO;
        BigDecimal p2pAcqCredit = p2pAcqValues != null ? p2pAcqValues[1] : BigDecimal.ZERO;
        BigDecimal p2pAcqNet = p2pAcqValues != null ? p2pAcqValues[2] : p2pAcqCredit;
        lines.add(formatTransactionRow("  P2P C2A Interinst fee (Acq part)", p2pAcqDebit, p2pAcqCredit, p2pAcqNet,
            "Ethswitch Payable - Remote OnUs ATM Cash Withdrawal & BI Comm", 
            formatAmount(debit.getRemoteOnUsAtmWithdrawal().getAmount().add(debit.getRemoteOnUsAtmWithdrawal().getCommission()).add(debit.getRemoteOnUsAtmWithdrawal().getBalanceInquiryFee()), df),
            "On-Us Dispute Chargeback Commission", formatAmount(credit.getOnUsDisputeChargebackCommission(), df)));

        // 7. P2P C2A Interinst fee (Switch part)
        BigDecimal[] p2pSwitchValues = getTsehay.apply("P2P C2A Interinst fee (Switch part)");
        BigDecimal p2pSwitchDebit = p2pSwitchValues != null ? p2pSwitchValues[0] : debit.getP2pCommissionToEthswitch();
        BigDecimal p2pSwitchCredit = p2pSwitchValues != null ? p2pSwitchValues[1] : BigDecimal.ZERO;
        BigDecimal p2pSwitchNet = p2pSwitchValues != null ? p2pSwitchValues[2] : p2pSwitchCredit.subtract(p2pSwitchDebit);
        lines.add(formatTransactionRow("  P2P C2A Interinst fee (Switch part)", p2pSwitchDebit, p2pSwitchCredit, p2pSwitchNet,
            "Ethswitch Payable - Remote OnUs EPOS Purchas", formatAmount(debit.getRemoteOnUsEposPurchasePlusCommission(), df),
            "Remote On-Us Dispute Chargeback Amount & Commission", formatAmount(credit.getRemoteOnUsDisputeChargebackAmountCommission(), df)));

        // 8. POS Purchase Acq. FEE
        BigDecimal[] posFeeValues = getTsehay.apply("POS Purchase Acq. FEE");
        BigDecimal posFeeDebit = posFeeValues != null ? posFeeValues[0] : BigDecimal.ZERO;
        BigDecimal posFeeCredit = posFeeValues != null ? posFeeValues[1] : BigDecimal.ZERO;
        BigDecimal posFeeNet = posFeeValues != null ? posFeeValues[2] : BigDecimal.ZERO;
        lines.add(formatTransactionRow("  POS Purchase Acq. FEE", posFeeDebit, posFeeCredit, posFeeNet,
            "\"Ethswitch Payable - Not deducted fee Remote OnUs EPOS Purchas  Apr 12,2024 \"", "",
            "2nd Presentment of OFF US txn from - LIB", ""));

        // 9. ATM Balance Inquiry FEE
        BigDecimal[] biFeeValues = getTsehay.apply("ATM Balance Inquiry FEE");
        BigDecimal biFeeDebit = biFeeValues != null ? biFeeValues[0] : debit.getRemoteOnUsAtmWithdrawal().getBalanceInquiryFee();
        BigDecimal biFeeCredit = biFeeValues != null ? biFeeValues[1] : credit.getAtmBalanceInquiryFee();
        BigDecimal biFeeNet = biFeeValues != null ? biFeeValues[2] : biFeeCredit.subtract(biFeeDebit);
        lines.add(formatTransactionRow("  ATM Balance Inquiry FEE", biFeeDebit, biFeeCredit, biFeeNet,
            "Ethswitch Payable - Outward P2P Am't", formatAmount(debit.getOutwardP2pAmount(), df),
            "Wrong OFF US Dispute Chagrgeback Am't -LIB", ""));

        // Totals Trans
        // Always compute totals from current backend calculation so removed
        // Payment & Settlement amounts are not reintroduced from uploaded summary rows.
        BigDecimal totalTransDebit = calculation.getTotalsTrans().getDebitValue();
        BigDecimal totalTransCredit = calculation.getTotalsTrans().getCreditValue();
        BigDecimal totalTransNet = calculation.getTotalsTrans().getNetValue();
        lines.add(formatTransactionRow("Totals Trans", totalTransDebit, totalTransCredit, totalTransNet,
            "Ethswitch Payable - 60% of P2P Commission to Ethswitch", formatAmount(debit.getP2pCommissionToEthswitch(), df),
            "Ethswitch Receivable-Total", formatAmount(credit.getTotal(), df)));

        // Totals Fees
        BigDecimal totalFeesDebit = calculation.getTotalsFees().getDebitValue();
        BigDecimal totalFeesCredit = calculation.getTotalsFees().getCreditValue();
        BigDecimal totalFeesNet = calculation.getTotalsFees().getNetValue();
        lines.add(formatTransactionRow("Totals Fees", totalFeesDebit, totalFeesCredit, totalFeesNet,
            "Ethswitch Payable -Remote OnUs Mini Statement Comm to Ethswitch", " -   ",
            "Payment & Settlement Principal - Net", ""));

        // Additional debit side entries - removed empty cols C and D
        lines.add(",,,,,Expense - OnUs ATM Withdrawal Fee to Ethswitch, " + 
            formatAmount(debit.getOnUsAtmFeeToEthswitch(), df) + " ,,Payment & Settlement Commission - Net , ");
        lines.add(",,,,,Expense - OnUs Balance Inquiry Comm to Ethswitch," + 
            formatAmount(debit.getOnUsBalanceInquiryCommissionToEthSwitch(), df) + ",,,");
        lines.add(",,,,,\"Expense -Wrongly charged BI commotion without transaction detail Nov 07, 2025\",,,,");
        lines.add(",,,,,Expense- OnUs Mini Statement Comm to Ethswitch,,,,");
        lines.add(",,,,,Expense - Remote OnUs ATM Withdrawal Fee to Ethswitch, " + 
            formatAmount(debit.getRemoteOnUsAtmFeeToEthswitch(), df) + " ,,,");
        lines.add(",,,,,Expense_ Remote ON US POS Purchase fee to Ethswitch (Rounding POS Purchase fee),,,,");
        lines.add(",,,,,,,,,,");
        lines.add(",,,,,Total," + formatAmount(calculateDebitSideDisplayTotal(debit), df) + " ,,,");
        lines.add(",,,,,, Variation ,,,");

        return lines;
    }

    /**
     * Total shown under Debit Side in the formatted summary should follow the
     * payable/expense-side entries displayed in columns F/G, not raw transaction debit total.
     */
    private BigDecimal calculateDebitSideDisplayTotal(EnhancedSettlementCalculationService.DebitSideCalculation debit) {
        return debit.getRemoteOnUsAtmWithdrawal().getAmount()
            .add(debit.getRemoteOnUsAtmWithdrawal().getCommission())
            .add(debit.getRemoteOnUsAtmWithdrawal().getBalanceInquiryFee())
            .add(debit.getOnUsAtmWithdrawal().getCommission())
            .add(debit.getOnUsBalanceInquiryCommissionToEthSwitch())
            .add(debit.getOutwardP2pAmount())
            .add(debit.getRemoteOnUsEposPurchasePlusCommission())
            .add(debit.getP2pCommissionToEthswitch())
            .add(debit.getOnUsAtmFeeToEthswitch())
            .add(debit.getRemoteOnUsAtmFeeToEthswitch());
    }

    private Map<String, BigDecimal[]> readTsehaySummaryData(String sessionId) {
        try {
            Optional<com.settlement.reconciliation.model.UploadedFile> fileOpt = 
                uploadedFileRepository.findTsehaySummaryBySessionId(sessionId);
            
            if (fileOpt.isPresent() && fileOpt.get().getFileContent() != null) {
                String content = fileOpt.get().getFileContent();
                Map<String, BigDecimal[]> dataMap = new HashMap<>();
                String[] lines = content.split("\n");
                
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() >= 7) {
                        String category = fields.get(0).trim();
                        if (category.isEmpty() || category.equalsIgnoreCase("Member") || category.contains("Net Position")) continue;
                        
                        String debitStr = fields.get(4).trim();
                        String creditStr = fields.get(5).trim();
                        String netStr = fields.get(6).trim();

                        // If the row doesn't provide any numeric values (blank or just dashes), ignore it so our
                        // calculated values remain authoritative.
                        if ((debitStr.isEmpty() || debitStr.matches("^[\\s-]*$")) &&
                            (creditStr.isEmpty() || creditStr.matches("^[\\s-]*$")) &&
                            (netStr.isEmpty() || netStr.matches("^[\\s-]*$"))) {
                            continue;
                        }

                        try {
                            BigDecimal debit = parseAmount(debitStr);
                            BigDecimal credit = parseAmount(creditStr);
                            BigDecimal net = parseAmount(netStr);
                            dataMap.put(category, new BigDecimal[]{debit, credit, net});
                        } catch (Exception e) {
                            // Skip lines that don't have numeric values in the expected columns
                        }
                    }
                }
                return dataMap;
            }
        } catch (Exception e) {
            System.err.println("Error reading TSEHAY summary data: " + e.getMessage());
        }
        return null;
    }

    private BigDecimal parseAmount(String val) {
        if (val == null || val.trim().isEmpty() || val.trim().equals("-")) return BigDecimal.ZERO;
        String cleaned = val.trim()
            .replace("\u00A0", "")
            .replaceAll("^\"\\s*|\\s*\"$", "")
            .replaceAll(",", "");
        boolean negativeParen = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (negativeParen) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        try {
            BigDecimal parsed = new BigDecimal(cleaned);
            return negativeParen ? parsed.negate() : parsed;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String normalizeCategory(String value) {
        if (value == null) return "";
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private String formatTransactionRow(String description, BigDecimal debitValue, BigDecimal creditValue, 
                                       BigDecimal netValue, String debitSideDesc, String debitSideAmount,
                                       String creditSideDesc, String creditSideAmount) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String debitStr = debitValue.compareTo(BigDecimal.ZERO) > 0 ? 
            "\" " + df.format(debitValue) + " \"" : " -   ";
        String creditStr = creditValue.compareTo(BigDecimal.ZERO) > 0 ? 
            "\" " + df.format(creditValue) + " \"" : " -   ";
        String netStr = netValue.compareTo(BigDecimal.ZERO) >= 0 ? 
            "\" " + df.format(netValue) + " \"" : 
            "\" (" + df.format(netValue.abs()) + ")\"";

        // Removed empty columns C and D - but kept B for alignment
        // Result: Description, (empty Col B), Debit, Credit, Net, DebitSideDesc, DebitSideAmount, (empty), CreditSideDesc, CreditSideAmount
        return String.format("%s,,%s,%s,%s,%s,%s,,%s,%s",
            description, debitStr, creditStr, netStr, debitSideDesc, debitSideAmount, creditSideDesc, creditSideAmount);
    }

    private String formatAmount(BigDecimal amount, DecimalFormat df) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return " -   ";
        }
        // Return quoted amount to ensure commas in thousands separators do not split CSV columns
        return "\" " + df.format(amount) + " \"";
    }

    public byte[] generateExcelSummaryReport(String settlementDate) {
        List<Map<String, Object>> sessions = findSessionsSummaryByDate(settlementDate);
        if (sessions == null || sessions.isEmpty()) {
            return new byte[0];
        }

        sessions.sort((s1, s2) -> {
            Object c1 = s1.get("createdAt");
            Object c2 = s2.get("createdAt");
            String d1 = c1 != null ? c1.toString() : "";
            String d2 = c2 != null ? c2.toString() : "";
            return d2.compareTo(d1);
        });

        Object targetSessionId = sessions.get(0).get("id");
        return targetSessionId == null ? new byte[0] : generateExcelSummaryReportBySession(targetSessionId.toString());
    }

    private Map<String, Object> aggregateSettlementReports(
            List<Map<String, Object>> reports,
            String settlementDate,
            String reportTitle) {
        Map<String, Object> aggregated = new HashMap<>();

        Map<String, Object> debitSide = new HashMap<>();
        debitSide.put("onUsATMCommission", 0.0);
        debitSide.put("remoteOnUsATMCommission", 0.0);
        debitSide.put("balanceInquiryFee", 0.0);
        debitSide.put("remoteOnUsPOSAmount", 0.0);
        debitSide.put("outwardP2PAmount", 0.0);
        debitSide.put("p2PCommission", 0.0);
        debitSide.put("onUsATMFee", 0.0);
        debitSide.put("remoteOnUsATMFee", 0.0);
        debitSide.put("totalAmount", 0.0);

        Map<String, Object> creditSide = new HashMap<>();
        creditSide.put("atmCashWithdrawal", 0.0);
        creditSide.put("incomingP2P", 0.0);
        creditSide.put("atmWithdrawalFee", 0.0);
        creditSide.put("balanceInquiryFee", 0.0);
        creditSide.put("totalAmount", 0.0);

        BigDecimal totalDebitAmount = BigDecimal.ZERO;
        BigDecimal totalCreditAmount = BigDecimal.ZERO;
        BigDecimal netSettlementAmount = BigDecimal.ZERO;

        for (Map<String, Object> report : reports) {
            @SuppressWarnings("unchecked")
            Map<String, Object> debit = (Map<String, Object>) report.get("debitSide");
            @SuppressWarnings("unchecked")
            Map<String, Object> credit = (Map<String, Object>) report.get("creditSide");
            if (debit != null) {
                addNumber(debitSide, "onUsATMCommission", debit.get("onUsATMCommission"));
                addNumber(debitSide, "remoteOnUsATMCommission", debit.get("remoteOnUsATMCommission"));
                addNumber(debitSide, "balanceInquiryFee", debit.get("balanceInquiryFee"));
                addNumber(debitSide, "remoteOnUsPOSAmount", debit.get("remoteOnUsPOSAmount"));
                addNumber(debitSide, "outwardP2PAmount", debit.get("outwardP2PAmount"));
                addNumber(debitSide, "p2PCommission", debit.get("p2PCommission"));
                addNumber(debitSide, "onUsATMFee", debit.get("onUsATMFee"));
                addNumber(debitSide, "remoteOnUsATMFee", debit.get("remoteOnUsATMFee"));
                addNumber(debitSide, "totalAmount", debit.get("totalAmount"));
            }
            if (credit != null) {
                addNumber(creditSide, "atmCashWithdrawal", credit.get("atmCashWithdrawal"));
                addNumber(creditSide, "incomingP2P", credit.get("incomingP2P"));
                addNumber(creditSide, "atmWithdrawalFee", credit.get("atmWithdrawalFee"));
                addNumber(creditSide, "balanceInquiryFee", credit.get("balanceInquiryFee"));
                addNumber(creditSide, "totalAmount", credit.get("totalAmount"));
            }

            totalDebitAmount = totalDebitAmount.add(toBigDecimal(report.get("totalDebitAmount")));
            totalCreditAmount = totalCreditAmount.add(toBigDecimal(report.get("totalCreditAmount")));
            netSettlementAmount = netSettlementAmount.add(toBigDecimal(report.get("netSettlementAmount")));
        }

        aggregated.put("reportTitle", reportTitle);
        aggregated.put("settlementDate", settlementDate);
        aggregated.put("formattedDate", settlementDate);
        aggregated.put("debitSide", debitSide);
        aggregated.put("creditSide", creditSide);
        aggregated.put("totalDebitAmount", totalDebitAmount.doubleValue());
        aggregated.put("totalCreditAmount", totalCreditAmount.doubleValue());
        aggregated.put("netSettlementAmount", netSettlementAmount.doubleValue());
        aggregated.put("sessionId", "all");
        return aggregated;
    }

    private void addNumber(Map<String, Object> target, String key, Object value) {
        double current = ((Number) target.getOrDefault(key, 0.0)).doubleValue();
        target.put(key, current + toBigDecimal(value).doubleValue());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private enum RowKind {
        TITLE,
        META,
        DATA,
        TOTAL,
        NOTE
    }

    private RowKind classifyRow(List<String> fields, int rowNum) {
        if (rowNum == 0) return RowKind.TITLE;
        if (fields == null || fields.isEmpty()) return RowKind.NOTE;

        String first = fields.get(0) == null ? "" : fields.get(0).trim().toLowerCase();
        if (first.startsWith("settlement")
            || first.startsWith("currency:")
            || first.startsWith("institution:")
            || first.equals("member")
            || first.contains("tsehay bank")) {
            return RowKind.META;
        }
        if (first.startsWith("totals") || first.equals("total")) {
            return RowKind.TOTAL;
        }
        if (first.startsWith("expense") || first.startsWith("variation") || first.isEmpty()) {
            return RowKind.NOTE;
        }
        return RowKind.DATA;
    }

    private org.apache.poi.ss.usermodel.CellStyle resolveTextStyle(
            RowKind rowKind,
            boolean isAlternate,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle totalTextStyle,
            org.apache.poi.ss.usermodel.CellStyle altRowStyle,
            org.apache.poi.ss.usermodel.CellStyle normalStyle) {
        if (rowKind == RowKind.META) return headerStyle;
        if (rowKind == RowKind.TOTAL) return totalTextStyle;
        return isAlternate ? altRowStyle : normalStyle;
    }

    // Simple CSV parser that respects quoted fields
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null) return fields;
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                // handle double-quote escape
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
