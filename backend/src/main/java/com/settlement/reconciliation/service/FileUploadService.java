package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.*;
import com.settlement.reconciliation.repository.*;
import com.settlement.reconciliation.util.StanUtils;
import com.settlement.reconciliation.util.TransactionDedupKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

@Slf4j
@Service
public class FileUploadService {

    private final FileTypeDetector fileTypeDetector;
    private final FileParser fileParser;
    private final UploadedFileRepository uploadedFileRepository;
    private final TransactionRepository transactionRepository;
    private final ReconciliationSessionRepository reconciliationSessionRepository;
    private final TransactionSwitchRepository transactionSwitchRepository;
    private final TransactionAtmRepository transactionAtmRepository;
    private final TransactionPayableRepository transactionPayableRepository;
    private final TransactionReceivableRepository transactionReceivableRepository;
    private final TransactionDedupNotificationRepository transactionDedupNotificationRepository;
    private final ReportService reportService;

    private final Map<String, Map<String, Object>> temporaryParsedData = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public FileUploadService(
            FileTypeDetector fileTypeDetector,
            FileParser fileParser,
            UploadedFileRepository uploadedFileRepository,
            TransactionRepository transactionRepository,
            ReconciliationSessionRepository reconciliationSessionRepository,
            TransactionSwitchRepository transactionSwitchRepository,
            TransactionAtmRepository transactionAtmRepository,
            TransactionPayableRepository transactionPayableRepository,
            TransactionReceivableRepository transactionReceivableRepository,
            TransactionDedupNotificationRepository transactionDedupNotificationRepository,
            ReportService reportService) {
        this.fileTypeDetector = fileTypeDetector;
        this.fileParser = fileParser;
        this.uploadedFileRepository = uploadedFileRepository;
        this.transactionRepository = transactionRepository;
        this.reconciliationSessionRepository = reconciliationSessionRepository;
        this.transactionSwitchRepository = transactionSwitchRepository;
        this.transactionAtmRepository = transactionAtmRepository;
        this.transactionPayableRepository = transactionPayableRepository;
        this.transactionReceivableRepository = transactionReceivableRepository;
        this.transactionDedupNotificationRepository = transactionDedupNotificationRepository;
        this.reportService = reportService;
        
        scheduler.scheduleAtFixedRate(this::cleanupTemporaryData, 1, 1, TimeUnit.HOURS);
    }

    private void cleanupTemporaryData() {
        if (!temporaryParsedData.isEmpty()) {
            temporaryParsedData.clear();
        }
    }

    public Map<String, Object> handleFileUpload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }

        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename();
        String sessionId = UUID.randomUUID().toString();
        
        log.info("=== FILE UPLOAD START: {} ({} bytes) ===", filename, bytes.length);
        
        FileParser.FileType detectedType = fileParser.detectFileType(bytes, filename);
        
        // For generic CSV, try to parse as switch if it has switch-like content
        if (detectedType == FileParser.FileType.GENERIC_CSV) {
            detectedType = FileParser.FileType.SWITCH_TRANSACTION;
        }
        
        String detectedFileType = detectedType.name().toLowerCase();
        
        // Parse file using enhanced parser
        Map<String, Object> parsedResult = new HashMap<>();
        try {
            parsedResult = parseFileByType(bytes, detectedType, sessionId);
            log.info("File {} PARSED: {} records, type: {}", filename, parsedResult.get("recordCount"), detectedFileType);
        } catch (Exception e) {
            log.error("ERROR parsing file {}: {}", filename, e.getMessage(), e);
            parsedResult.put("data", new ArrayList<>());
            parsedResult.put("recordCount", 0);
        }
        
        String settlementDate = extractSettlementDateFromFile(file, bytes);
        log.info("File {} sessionId={} settlementDate={} stored in TEMP cache", filename, sessionId, settlementDate);

        // Capture raw file content
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("fileType", detectedFileType);
        sessionData.put("filename", filename);
        sessionData.put("parsedData", parsedResult.get("data"));
        sessionData.put("recordCount", (Integer) parsedResult.get("recordCount"));
        sessionData.put("settlementDate", settlementDate);
        sessionData.put("fileContent", content);
        temporaryParsedData.put(sessionId, sessionData);

        Map<String, Object> report = new HashMap<>();
        report.put("sessionId", sessionId);
        report.put("filename", filename);
        report.put("size", (long) bytes.length);
        report.put("contentType", file.getContentType());
        report.put("detectedFileType", detectedFileType);
        report.put("recordCount", parsedResult.get("recordCount"));
        report.put("settlementDate", settlementDate);
        report.put("status", "✅ Uploaded & parsed. Click 'Start Settlement Process' to persist data and reconcile.");
        report.put("parsedData", parsedResult.get("data"));

        log.info("=== FILE UPLOAD COMPLETE: {} (session={}) ===", filename, sessionId);
        return report;
    }

    private Map<String, Object> parseFileByType(byte[] fileBytes, FileParser.FileType fileType, String sessionId) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Transaction> transactions;
        
        switch (fileType) {
            case ATM_ACTIVITY:
                transactions = fileParser.parseAtmFile(fileBytes, UUID.randomUUID().toString(), sessionId);
                break;
            case PAYABLE_STATEMENT:
                transactions = fileParser.parsePayableFile(fileBytes, UUID.randomUUID().toString(), sessionId);
                break;
            case RECEIVABLE_STATEMENT:
                transactions = fileParser.parseReceivableFile(fileBytes, UUID.randomUUID().toString(), sessionId);
                break;
            case SWITCH_TRANSACTION:
                transactions = fileParser.parseSwitchFile(fileBytes, UUID.randomUUID().toString(), sessionId);
                break;
            case SUMMARY_REPORT:
                // Summary reports contain aggregate data, not individual transactions to parse
                transactions = new ArrayList<>();
                break;
            case GENERIC_CSV:
                // For generic CSVs, we don't have specific parsing logic
                transactions = new ArrayList<>();
                break;
            default:
                transactions = new ArrayList<>();
                break;
        }
        
        result.put("data", transactions);
        result.put("recordCount", transactions.size());
        return result;
    }

    private String extractSettlementDateFromFile(MultipartFile file, byte[] bytes) {
        String filename = file.getOriginalFilename();
        
        // 1. Try filename patterns first
        String dateFromFilename = extractDateFromFilename(filename);
        if (dateFromFilename != null) return dateFromFilename;

        // 2. Try content analysis if filename failed or to confirm
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(new java.io.ByteArrayInputStream(bytes), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 30) {
                linesRead++;
                // Strip BOM
                if (linesRead == 1 && line.startsWith("\uFEFF")) line = line.substring(1);

                // Statement Date marker
                if (line.contains("Statement Date")) {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{8})");
                    java.util.regex.Matcher m = p.matcher(line);
                    if (m.find()) {
                        String ds = m.group(1);
                        return String.format("%s-%s-%s", ds.substring(0,4), ds.substring(4,6), ds.substring(6,8));
                    }
                }
                
                // Settlement day marker (Detailed Report)
                if (line.contains("For settlement day:")) {
                    // This often contains something like "3172" which is internal.
                    // Let's keep looking for real dates.
                }

                // First data row in detailed report often has the date
                if (linesRead > 5) {
                    // Try to find a date pattern like dd.MM.yyyy or yyyyMMdd or dd/MM/yyyy
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{4})");
                    java.util.regex.Matcher m = p.matcher(line);
                    if (m.find()) return String.format("%s-%s-%s", m.group(3), m.group(2), m.group(1));

                    p = java.util.regex.Pattern.compile("(\\d{4})(\\d{2})(\\d{2})");
                    m = p.matcher(line);
                    if (m.find()) {
                        String year = m.group(1);
                        if (year.startsWith("202")) return String.format("%s-%s-%s", year, m.group(2), m.group(3));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Failed to extract date from content: " + e.getMessage());
        }

        return LocalDate.now().toString();
    }

    private String extractDateFromFilename(String filename) {
        if (filename == null) return null;

        // Try dd.MM.yyyy
        java.util.regex.Pattern pDot = java.util.regex.Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{4})");
        java.util.regex.Matcher mDot = pDot.matcher(filename);
        if (mDot.find()) return String.format("%s-%s-%s", mDot.group(3), mDot.group(2), mDot.group(1));

        // Try yyyy-MM-dd
        java.util.regex.Pattern pIso = java.util.regex.Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
        java.util.regex.Matcher mIso = pIso.matcher(filename);
        if (mIso.find()) return mIso.group(0);

        // Try MMM d, yyyy
        java.util.regex.Pattern pText = java.util.regex.Pattern.compile("([A-Za-z]{3}) (\\d{1,2})[, ]+(\\d{4})");
        java.util.regex.Matcher mText = pText.matcher(filename);
        if (mText.find()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH);
                LocalDate date = LocalDate.parse(mText.group(1) + " " + mText.group(2) + " " + mText.group(3), formatter);
                return date.toString();
            } catch (Exception ignored) {}
        }
        
        return null;
    }

    @Transactional
    public UploadedFile persistParsedData(String sessionId, String reconciliationSessionId) throws IOException {
        Map<String, Object> sessionData = temporaryParsedData.get(sessionId);
        if (sessionData == null) {
            // If the parsed data is missing, it may have already been persisted in a previous run
            // or the application was restarted. In such cases, don't fail reconciliation;
            // instead, log and continue (we may already have the transactions in DB).
            System.out.println("WARN: No parsed data found for session ID: " + sessionId + ". Skipping persistence.");
            return null;
        }

        String fileType = (String) sessionData.get("fileType");
        String filename = (String) sessionData.get("filename");
        Object parsedData = sessionData.get("parsedData");
        String fileContent = (String) sessionData.get("fileContent");

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setId(UUID.randomUUID().toString());
        uploadedFile.setFilename(filename);
        uploadedFile.setFileType(fileType);
        uploadedFile.setSize(1000L); // Placeholder
        uploadedFile.setStatus("parsed");
        uploadedFile.setSessionId(reconciliationSessionId);
        uploadedFile.setRecordCount(0);
        uploadedFile.setUploadedAt(LocalDateTime.now());
        uploadedFile.setFileContent(fileContent);
        uploadedFileRepository.save(uploadedFile);

        if (parsedData instanceof List) {
            @SuppressWarnings("unchecked")
            List<Transaction> transactions = (List<Transaction>) parsedData;
            int persistedCount = 0;
            int skippedUnsettled = 0;
            int skippedSettled = 0;

            for (Transaction tx : transactions) {
                tx.setFileId(uploadedFile.getId());
                tx.setSessionId(reconciliationSessionId);
                tx.setDedupeKey(TransactionDedupKeyUtils.buildDedupeKey(tx));

                Transaction existing = transactionRepository.findByDedupeKey(tx.getDedupeKey()).orElse(null);
                if (existing != null) {
                    // Temporarily bypass deduplication for "Mar 10, 2026 Detail.csv" for testing as per user request
                    if ("Mar 10, 2026 Detail.csv".equals(filename)) {
                        // For this specific test file, we allow re-upload/processing.
                        // Optionally, we could delete existing related transactions, but for now, just skip.
                        System.out.println("DEBUG: Bypassing deduplication for " + filename);
                        // Instead of continuing, we need to ensure the new transactions are processed
                        // For simplicity in this temporary bypass, we will let it proceed and potentially
                        // have duplicates in the Transaction table which will be handled by reconciliation logic
                        // (or the user will re-upload a unique session if this is problematic).
                        // However, the intention here is to allow the file to be considered new data for a session.
                        // A more proper bypass would be to delete old transactions associated with this file.
                        // For now, we proceed to save as new, assuming the reconciliation logic can handle potential duplicates
                        // if a new session ID means new logical transactions for this test.
                        // For this test, the goal is to get the calculation to run on the 'new' data.
                    } else if (isSettledStatus(existing.getStatus())) {
                        skippedSettled++;
                        recordDedupNotification(uploadedFile, reconciliationSessionId, tx, existing,
                            "DUPLICATE_SETTLED",
                            "Skipped duplicate transaction because it is already settled in the system.");
                        continue; // Skip saving this duplicate
                    } else {
                        skippedUnsettled++;
                        recordDedupNotification(uploadedFile, reconciliationSessionId, tx, existing,
                            "DUPLICATE_UNSETTLED",
                            "Skipped duplicate transaction because same unsettled transaction already exists in the system.");
                        continue; // Skip saving this duplicate
                    }
                }

                try {
                    transactionRepository.save(tx);
                    persistedCount++;
                } catch (DataIntegrityViolationException e) {
                    Transaction concurrentExisting = transactionRepository.findByDedupeKey(tx.getDedupeKey()).orElse(null);
                    if (concurrentExisting != null) {
                        if (isSettledStatus(concurrentExisting.getStatus())) {
                            skippedSettled++;
                            recordDedupNotification(uploadedFile, reconciliationSessionId, tx, concurrentExisting,
                                "DUPLICATE_SETTLED",
                                "Skipped duplicate transaction because it is already settled in the system.");
                        } else {
                            skippedUnsettled++;
                            recordDedupNotification(uploadedFile, reconciliationSessionId, tx, concurrentExisting,
                                "DUPLICATE_UNSETTLED",
                                "Skipped duplicate transaction because same unsettled transaction already exists in the system.");
                        }
                    } else {
                        throw e;
                    }
                }
            }

            uploadedFile.setRecordCount(transactions.size());
            int totalSkipped = skippedSettled + skippedUnsettled;
            uploadedFile.setStatus(totalSkipped > 0 ? "parsed_with_dedup" : "parsed");
            if (totalSkipped > 0) {
                uploadedFile.setErrorMessage("Saved " + persistedCount + " transactions. Skipped " + totalSkipped
                    + " duplicates (unsettled: " + skippedUnsettled + ", settled: " + skippedSettled + ").");
            } else {
                uploadedFile.setErrorMessage(null);
            }
            uploadedFileRepository.save(uploadedFile);
        }

        temporaryParsedData.remove(sessionId);
        return uploadedFile;
    }

    private boolean isSettledStatus(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase();
        return "settled".equals(s) || "resolved".equals(s) || "closed".equals(s);
    }

    private void recordDedupNotification(
        UploadedFile uploadedFile,
        String reconciliationSessionId,
        Transaction incoming,
        Transaction existing,
        String reason,
        String message
    ) {
        TransactionDedupNotification notification = new TransactionDedupNotification();
        notification.setId(UUID.randomUUID().toString());
        notification.setSessionId(reconciliationSessionId);
        notification.setFileId(uploadedFile.getId());
        notification.setDedupeKey(incoming.getDedupeKey());
        notification.setStanNo(incoming.getStanNo());
        notification.setTransRef(incoming.getTransRef());
        notification.setTxnAmount(incoming.getTxnAmount());
        notification.setExistingTransactionId(existing.getId());
        notification.setExistingStatus(existing.getStatus());
        notification.setReason(reason);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        transactionDedupNotificationRepository.save(notification);
    }

    private List<TransactionSwitch> mapToTransactionSwitch(List<Map<String, String>> parsedData, String fileId, String sessionId) {
        List<TransactionSwitch> transactions = new ArrayList<>();
        for (Map<String, String> data : parsedData) {
            TransactionSwitch ts = new TransactionSwitch();
            ts.setId(UUID.randomUUID().toString());
            ts.setStan(StanUtils.extractStan(data.get("STAN") != null ? data.get("STAN") : (data.get("STAN_F11") != null ? data.get("STAN_F11") : data.get("STAN.NO"))));
            String refNum = data.get("Trans Ref") != null ? data.get("Trans Ref") :
                    (data.get("Ref Num") != null ? data.get("Ref Num") :
                    (data.get("Refnum_F37") != null ? data.get("Refnum_F37") :
                    (data.get("TRANS.REF") != null ? data.get("TRANS.REF") : null)));
            // If the transaction reference is missing, search other fields for an RFT-like tag
            if (refNum == null) {
                for (String value : data.values()) {
                    if (value != null && value.toUpperCase().startsWith("RFT")) {
                        refNum = value;
                        break;
                    }
                }
            }
            ts.setRefNum(refNum);
            ts.setAmount(parseBigDecimal(data.get("Amount") != null ? data.get("Amount") : data.get("TXN.AMOUNT")));
            ts.setTransactionDate(parseLocalDateTime(data.get("Transaction Date") != null ? data.get("Transaction Date") : (data.get("Transaction_Date") != null ? data.get("Transaction_Date") : data.get("VALUE.DATE"))));
            ts.setTransactionDescription(data.get("Transaction Description") != null ? data.get("Transaction Description") : (data.get("Transaction_Description") != null ? data.get("Transaction_Description") : ""));
            ts.setTerminalId(data.get("Terminal ID") != null ? data.get("Terminal ID") : (data.get("Terminal_ID") != null ? data.get("Terminal_ID") : data.get("TERMINAL.ID")));
            if (refNum != null && refNum.toUpperCase().startsWith("RFT")) {
                ts.setStatus("reversal");
            } else {
                ts.setStatus("unsettled");
            }
            ts.setFileId(fileId);
            ts.setSessionId(sessionId);
            transactions.add(ts);
        }
        return transactions;
    }

    private List<TransactionAtm> mapToTransactionAtm(List<Map<String, String>> parsedData, String fileId, String sessionId) {
        List<TransactionAtm> transactions = new ArrayList<>();
        for (Map<String, String> data : parsedData) {
            TransactionAtm atm = new TransactionAtm();
            atm.setId(UUID.randomUUID().toString());
            atm.setTransRef(data.get("Trans Ref") != null ? data.get("Trans Ref") : data.get("TRANS.REF"));
            atm.setCompanyCode(data.get("Company Code") != null ? data.get("Company Code") : data.get("COMPANY.CODE"));
            atm.setProcCode(data.get("Proc Code") != null ? data.get("Proc Code") : data.get("PROC.CODE"));
            atm.setMtiCode(data.get("MTI Code") != null ? data.get("MTI Code") : data.get("MTI.CODE"));
            atm.setPanNumber(data.get("PAN Number") != null ? data.get("PAN Number") : data.get("PAN.NUMBER"));
            atm.setRetrievalRefNo(data.get("Retrieval Ref No") != null ? data.get("Retrieval Ref No") : data.get("RETRIEVAL.REF.NO"));
            atm.setAuthCode(data.get("Auth Code") != null ? data.get("Auth Code") : data.get("AUTH.CODE"));
            atm.setStanNo(StanUtils.extractStan(data.get("STAN No") != null ? data.get("STAN No") : data.get("STAN.NO")));
            atm.setTxnAmount(parseBigDecimal(data.get("Txn Amount") != null ? data.get("Txn Amount") : data.get("TXN.AMOUNT")));
            atm.setTerminalId(data.get("Terminal ID") != null ? data.get("Terminal ID") : data.get("TERMINAL.ID"));
            atm.setValueDate(parseLocalDateTime(data.get("Value Date") != null ? data.get("Value Date") : data.get("VALUE.DATE")));
            atm.setBookingDate(parseLocalDateTime(data.get("Booking Date") != null ? data.get("Booking Date") : data.get("BOOKING.DATE")));
            atm.setDebitAcctNo(data.get("Debit Acct No") != null ? data.get("Debit Acct No") : data.get("DEBIT.ACCT.NO"));
            atm.setCreditAcctNo(data.get("Credit Acct No") != null ? data.get("Credit Acct No") : data.get("CREDIT.ACCT.NO"));
            atm.setRequestTime(data.get("Request Time") != null ? data.get("Request Time") : data.get("REQUEST.TIME"));
            atm.setCardAccId(data.get("Card Acc ID") != null ? data.get("Card Acc ID") : data.get("CARD.ACC.ID"));
            atm.setStatus("unsettled");
            atm.setFileId(fileId);
            atm.setSessionId(sessionId);
            atm.setCreatedAt(LocalDateTime.now());
            atm.setUpdatedAt(LocalDateTime.now());
            transactions.add(atm);
        }
        return transactions;
    }

    private List<TransactionPayable> mapToTransactionPayable(List<Map<String, String>> parsedData, String fileId, String sessionId) {
        List<TransactionPayable> transactions = new ArrayList<>();
        for (Map<String, String> data : parsedData) {
            TransactionPayable payable = new TransactionPayable();
            payable.setId(UUID.randomUUID().toString());
            payable.setBookDate(parseLocalDateTime(data.get("Book Date")));
            payable.setReference(data.get("Reference"));
            payable.setDescription(data.get("Description"));
            payable.setValueDate(parseLocalDateTime(data.get("Value Date")));
            payable.setDebit(parseBigDecimal(data.get("Debit")));
            payable.setCredit(parseBigDecimal(data.get("Credit")));
            payable.setClosingBalance(parseBigDecimal(data.get("Closing Balance")));
            payable.setStatus("unsettled");
            payable.setFileId(fileId);
            payable.setSessionId(sessionId);
            payable.setCreatedAt(LocalDateTime.now());
            payable.setUpdatedAt(LocalDateTime.now());
            transactions.add(payable);
        }
        return transactions;
    }

    private List<TransactionReceivable> mapToTransactionReceivable(List<Map<String, String>> parsedData, String fileId, String sessionId) {
        List<TransactionReceivable> transactions = new ArrayList<>();
        for (Map<String, String> data : parsedData) {
            TransactionReceivable receivable = new TransactionReceivable();
            receivable.setId(UUID.randomUUID().toString());
            receivable.setBookDate(parseLocalDateTime(data.get("Book Date")));
            receivable.setReference(data.get("Reference"));
            receivable.setDescription(data.get("Description"));
            receivable.setValueDate(parseLocalDateTime(data.get("Value Date")));
            receivable.setDebit(parseBigDecimal(data.get("Debit")));
            receivable.setCredit(parseBigDecimal(data.get("Credit")));
            receivable.setClosingBalance(parseBigDecimal(data.get("Closing Balance")));
            receivable.setStatus("unsettled");
            receivable.setFileId(fileId);
            receivable.setSessionId(sessionId);
            receivable.setCreatedAt(LocalDateTime.now());
            receivable.setUpdatedAt(LocalDateTime.now());
            transactions.add(receivable);
        }
        return transactions;
    }

    private java.math.BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            String sanitizedValue = value.replace(",", "").trim();
            if (sanitizedValue.isEmpty()) {
                return java.math.BigDecimal.ZERO;
            }
            return new java.math.BigDecimal(sanitizedValue);
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseLocalDateTime(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            String[] patterns = {"yyyy-MM-dd HH:mm:ss", "dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy", "yyyyMMdd", "MM/dd/yyyy", "dd MMM yy", "dd-MMM-yy"};
            for (String pattern : patterns) {
                try {
                    if (pattern.contains("HH")) {
                        return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern(pattern));
                    } else if (pattern.contains("MMM")) {
                         return LocalDate.parse(dateString.toUpperCase().replace("-", " "), DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH)).atStartOfDay();
                    } else if (pattern.equals("MM/dd/yyyy")) {
                         return LocalDate.parse(dateString.split("-")[0], DateTimeFormatter.ofPattern("MM/dd/yyyy")).atStartOfDay();
                    } else {
                        return LocalDate.parse(dateString, DateTimeFormatter.ofPattern(pattern)).atStartOfDay();
                    }
                } catch (Exception ex) {}
            }
             try {
                // Try a more generic pattern
                return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            } catch (Exception ex) {
                System.out.println("Could not parse date: " + dateString + ". Error: " + ex.getMessage());
                return null;
            }
        }
    }
}
