package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.Transaction;
import com.settlement.reconciliation.util.StanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class FileParser {

    private final FileTypeDetector fileTypeDetector;

    public FileParser(FileTypeDetector fileTypeDetector) {
        this.fileTypeDetector = fileTypeDetector;
    }

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy-HH:mm:ss-SSSS"),
        DateTimeFormatter.ofPattern("dd MMM yy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    public enum FileType {
        ATM_ACTIVITY,
        PAYABLE_STATEMENT,
        RECEIVABLE_STATEMENT,
        SWITCH_TRANSACTION,
        SUMMARY_REPORT,
        GENERIC_CSV,
        UNKNOWN
    }

    public FileType detectFileType(byte[] fileBytes, String filename) throws IOException {
        String detectedTypeString = fileTypeDetector.detectFileType(fileBytes, filename);

        switch (detectedTypeString) {
            case "ATM_ACTIVITY":
                return FileType.ATM_ACTIVITY;
            case "PAYABLE":
                return FileType.PAYABLE_STATEMENT;
            case "RECEIVABLE":
                return FileType.RECEIVABLE_STATEMENT;
            case "TSEHAY_SWITCH":
                return FileType.SWITCH_TRANSACTION;
            case "ETH_SWITCH_SUMMARY":
                return FileType.SUMMARY_REPORT;
            case "GENERIC_CSV":
                return FileType.GENERIC_CSV;
            default:
                return FileType.UNKNOWN;
        }
    }

    public List<Transaction> parseAtmFile(byte[] fileBytes, String fileId, String sessionId) throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(fileBytes)))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }
                
                if (line.trim().isEmpty()) continue;
                
                String[] fields = parseCSVLine(line);
                if (fields.length >= 12) { // Relaxed from 16
                    Transaction transaction = new Transaction();
                    transaction.setId(UUID.randomUUID().toString());
                    transaction.setTransRef(cleanField(fields[0]));
                    transaction.setCompanyCode(cleanField(fields[1]));
                    transaction.setProcCode(cleanField(fields[2]));
                    transaction.setMtiCode(cleanField(fields[3]));
                    transaction.setPanNumber(cleanField(fields[4]));
                    transaction.setRetrievalRefNo(cleanField(fields[5]));
                    transaction.setAuthCode(cleanField(fields[6]));
                    transaction.setStanNo(StanUtils.extractStan(cleanField(fields[7])));
                    transaction.setTxnAmount(parseAmount(fields[8]));
                    transaction.setTerminalId(cleanField(fields[9]));
                    transaction.setValueDate(parseDate(fields[10]));
                    
                    if (fields.length >= 12) transaction.setBookingDate(parseDate(fields[11]));
                    if (fields.length >= 13) transaction.setDebitAcctNo(cleanField(fields[12]));
                    if (fields.length >= 14) transaction.setCreditAcctNo(cleanField(fields[13]));
                    if (fields.length >= 15) transaction.setRequestTime(cleanField(fields[14]));
                    if (fields.length >= 16) transaction.setCardAccId(cleanField(fields[15]));
                    
                    transaction.setStatus("unsettled");
                    transaction.setFileId(fileId);
                    transaction.setSessionId(sessionId);
                    transaction.setSource("ATM");
                    
                    transactions.add(transaction);
                } else {
                    System.err.println("DEBUG: parseAtmFile - Skipping short line: " + line);
                }
            }
        }
        
        return transactions;
    }
    
    public List<Transaction> parseSwitchFile(byte[] fileBytes, String fileId, String sessionId) throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(fileBytes), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean headerFound = false;
            int totalLinesRead = 0;
            
            while ((line = reader.readLine()) != null) {
                totalLinesRead++;
                // Strip BOM if present on first line
                if (totalLinesRead == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                if (totalLinesRead < 20) {
                    System.err.println("DEBUG: parseSwitchFile - Line " + totalLinesRead + ": " + line);
                }
                
                if (line.trim().isEmpty()) continue;

                if (!headerFound) {
                    if (line.toLowerCase().contains("issuer") && line.toLowerCase().contains("acquirer")) {
                        headerFound = true;
                        System.err.println("DEBUG: parseSwitchFile - Header found on line " + totalLinesRead);
                    }
                    continue; // Skip lines until the header is found
                }
                
                String[] fields = parseCSVLine(line);
                if (fields.length >= 7) { 
                    Transaction transaction = new Transaction();
                    transaction.setId(UUID.randomUUID().toString());
                    transaction.setIssuer(cleanField(fields[0]));
                    transaction.setAcquirer(cleanField(fields[1]));
                    transaction.setMtiCode(cleanField(fields[2]));
                    transaction.setPanNumber(cleanField(fields[3]));
                    transaction.setTxnAmount(parseAmount(fields[4]));
                    transaction.setCurrency(cleanField(fields[5]));
                    
                    // Handle Date/Time (Field 6 usually has both in Nov 14 style)
                    String dateStr = cleanField(fields[6]);
                    transaction.setValueDate(parseDate(dateStr));
                    
                    // Robust Description Search
                    String description = "";
                    // Check expected indices first: 7 (old), 8 (new), 9 (P2P)
                    List<Integer> descIndices = Arrays.asList(8, 7, 9);
                    for (int idx : descIndices) {
                        if (fields.length > idx) {
                            String f = cleanField(fields[idx]);
                            if (isDescription(f)) {
                                description = f;
                                break;
                            }
                        }
                    }
                    
                    // If still not found, search all fields
                    if (description.isEmpty()) {
                        for (String f : fields) {
                            String cleaned = cleanField(f);
                            if (isDescription(cleaned)) {
                                description = cleaned;
                                break;
                            }
                        }
                    }
                    
                    // Fallback to field 8 if it's not empty but didn't match keywords (resilient)
                    if (description.isEmpty() && fields.length > 8) {
                        description = cleanField(fields[8]);
                    }

                    transaction.setDescription(description);
                    
                    // Ref & STAN identification
                    String ref = null;
                    String stan = null;
                    
                    for (int i = 8; i < fields.length; i++) {
                        String f = cleanField(fields[i]);
                        if (f.isEmpty()) continue;
                        
                        if (f.startsWith("FT")) {
                            ref = f;
                        } else if (StanUtils.extractStan(f) != null) {
                            // STAN can appear as 4-6 digits depending on source formatting.
                            // Prefer the first short numeric candidate in the switch detail segment.
                            if (stan == null) stan = StanUtils.extractStan(f);
                        } else if (f.length() >= 10 && f.matches("\\d+")) {
                            if (ref == null) ref = f;
                        }
                    }
                    
                    // Fallbacks for specific indices if not found by pattern
                    if (ref == null && fields.length >= 13) ref = cleanField(fields[12]);
                    if (ref == null && fields.length >= 12) ref = cleanField(fields[11]);
                    if (stan == null && fields.length >= 12) {
                        String f11 = cleanField(fields[11]);
                        stan = StanUtils.extractStan(f11);
                    }

                    transaction.setTransRef(ref);
                    transaction.setStanNo(StanUtils.extractStan(stan));

                    // Automatically label reversal transactions (e.g., TRANS.REF starting with "RFT")
                    // so they can be filtered separately in the reconciliation UI.
                    if (ref != null && ref.toUpperCase().startsWith("RFT")) {
                        transaction.setStatus("reversal");
                    } else {
                        transaction.setStatus("unsettled");
                    }
                    transaction.setSource("Switch");
                    transaction.setFileId(fileId);
                    transaction.setSessionId(sessionId);
                    
                    transactions.add(transaction);
                }
            }
            System.out.println("DEBUG: parseSwitchFile - Finished. Total parsed: " + transactions.size());
        }
        
        return transactions;
    }

    private boolean isDescription(String f) {
        if (f == null || f.isEmpty()) return false;
        String l = f.toLowerCase().trim();
        // Common switch description keywords
        if (l.contains("atm cw") || 
            l.contains("balance enquiry") ||
            l.contains("balance inquiry") ||
            l.contains("account2account") ||
            l.contains("pos pur") ||
            l.contains("dispute") ||
            l.contains("chargeback")) {
            return true;
        }
        // Fallback: treat any field that contains a space and at least one letter as a description
        return l.matches(".*[a-z].*") && l.contains(" ");
    }

    public List<Transaction> parsePayableFile(byte[] fileBytes, String fileId, String sessionId) throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(fileBytes), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean inDataSection = false;
            Transaction currentTransaction = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.contains("Book Date") && line.contains("Reference")) {
                    inDataSection = true;
                    continue;
                }
                
                if (inDataSection && !line.startsWith("\"\"") && !line.contains("Balance at Period")) {
                    String[] fields = parseCSVLine(line);
                    if (fields.length >= 7) {
                        String bookDateStr = cleanField(fields[0]);
                        String reference = cleanField(fields[1]);
                        String description = cleanField(fields[2]);
                        
                        // New transaction starts with a date and reference
                        if (!bookDateStr.isEmpty() && !reference.isEmpty()) {
                            currentTransaction = new Transaction();
                            currentTransaction.setId(UUID.randomUUID().toString());
                            currentTransaction.setBookingDate(parseStatementDate(bookDateStr));
                            currentTransaction.setTransRef(reference);
                            currentTransaction.setDescription(description);
                            
                            String valueDateStr = cleanField(fields[3]);
                            currentTransaction.setValueDate(parseStatementDate(valueDateStr));
                            
                            String debitStr = cleanField(fields[4]);
                            String creditStr = cleanField(fields[5]);
                            BigDecimal debitAmount = parseAmount(debitStr);
                            BigDecimal creditAmount = parseAmount(creditStr);
                            currentTransaction.setDebit(debitAmount);
                            currentTransaction.setCredit(creditAmount);
                            
                            if (debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                                currentTransaction.setTxnAmount(debitAmount);
                            } else if (creditAmount != null && creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                                currentTransaction.setTxnAmount(creditAmount);
                            }
                            
                            // Categorize Transfers
                            String descLower = description.toLowerCase();
                            if (descLower.equals("transfer in")) {
                                currentTransaction.setStatus("Transfer In");
                            } else if (descLower.equals("transfer out")) {
                                currentTransaction.setStatus("Transfer Out");
                            } else {
                                currentTransaction.setStatus("unsettled");
                            }
                            
                            currentTransaction.setFileId(fileId);
                            currentTransaction.setSessionId(sessionId);
                            currentTransaction.setSource("Payable");
                            
                            transactions.add(currentTransaction);
                        } else if (currentTransaction != null && bookDateStr.isEmpty() && reference.isEmpty() && !description.isEmpty()) {
                            // Subsequent detail line (e.g. "From Acc.No: ...")
                            String oldDesc = currentTransaction.getDescription();
                            currentTransaction.setDescription(oldDesc + " | " + description);
                        }
                    }
                }
            }
        }
        
        return transactions;
    }

    public List<Transaction> parseReceivableFile(byte[] fileBytes, String fileId, String sessionId) throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(fileBytes), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean inDataSection = false;
            Transaction currentTransaction = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.contains("Book Date") && line.contains("Reference")) {
                    inDataSection = true;
                    continue;
                }
                
                if (inDataSection && !line.startsWith("\"\"") && !line.contains("Balance at Period")) {
                    String[] fields = parseCSVLine(line);
                    if (fields.length >= 7) {
                        String bookDateStr = cleanField(fields[0]);
                        String reference = cleanField(fields[1]);
                        String description = cleanField(fields[2]);
                        
                        if (!bookDateStr.isEmpty() && !reference.isEmpty()) {
                            currentTransaction = new Transaction();
                            currentTransaction.setId(UUID.randomUUID().toString());
                            currentTransaction.setBookingDate(parseStatementDate(bookDateStr));
                            currentTransaction.setTransRef(reference);
                            currentTransaction.setDescription(description);
                            
                            String valueDateStr = cleanField(fields[3]);
                            currentTransaction.setValueDate(parseStatementDate(valueDateStr));
                            
                            String debitStr = cleanField(fields[4]);
                            String creditStr = cleanField(fields[5]);
                            BigDecimal debitAmount = parseAmount(debitStr);
                            BigDecimal creditAmount = parseAmount(creditStr);
                            currentTransaction.setDebit(debitAmount);
                            currentTransaction.setCredit(creditAmount);
                            
                            if (debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                                currentTransaction.setTxnAmount(debitAmount);
                            } else if (creditAmount != null && creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                                currentTransaction.setTxnAmount(creditAmount);
                            }
                            
                            // Categorize Transfers
                            String descLower = description.toLowerCase();
                            if (descLower.equals("transfer in")) {
                                currentTransaction.setStatus("Transfer In");
                            } else if (descLower.equals("transfer out")) {
                                currentTransaction.setStatus("Transfer Out");
                            } else {
                                currentTransaction.setStatus("unsettled");
                            }
                            
                            currentTransaction.setFileId(fileId);
                            currentTransaction.setSessionId(sessionId);
                            currentTransaction.setSource("Receivable");
                            
                            transactions.add(currentTransaction);
                        } else if (currentTransaction != null && bookDateStr.isEmpty() && reference.isEmpty() && !description.isEmpty()) {
                            // Subsequent detail line
                            String oldDesc = currentTransaction.getDescription();
                            currentTransaction.setDescription(oldDesc + " | " + description);
                        }
                    }
                }
            }
        }
        
        return transactions;
    }

    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++; // Skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        fields.add(currentField.toString());
        return fields.toArray(new String[0]);
    }

    private String cleanField(String field) {
        if (field == null) return "";
        String cleaned = field.trim().replaceAll("^[\"']+|[\"']+$", "");
        
        // Handle scientific notation for numeric strings (e.g., from Excel-mangled CSVs)
        if (cleaned.toUpperCase().contains("E+") || (cleaned.length() > 5 && cleaned.matches(".*\\d\\.\\d+E\\d+.*"))) {
            try {
                return new java.math.BigDecimal(cleaned).toPlainString();
            } catch (Exception e) {
                // Not a valid big decimal or scientific notation, return as is
            }
        }
        return cleaned;
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        try {
            String cleaned = amountStr.replaceAll("[^\\d.-]", "");
            if (cleaned.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        dateStr = dateStr.trim();
        
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                // Check if the formatter includes time components
                String pattern = formatter.toString(); 
                // Note: formatter.toString() might not directly reveal the pattern depending on implementation, 
                // but checking the parsing result is more reliable.
                
                // Try parsing as LocalDateTime first
                try {
                    return LocalDateTime.parse(dateStr, formatter);
                } catch (DateTimeParseException e) {
                    // unexpected, try LocalDate
                }
                
                // Fallback to LocalDate and convert to LocalDateTime
                try {
                    return java.time.LocalDate.parse(dateStr, formatter).atStartOfDay();
                } catch (DateTimeParseException e) {
                   // Continue to next formatter
                }

            } catch (Exception e) {
                // Ignore and continue
            }
        }
        
        // Final attempt for specific formats that might need strict handling
        try {
             // Handle 20241114 (yyyyMMdd) explicitly if not caught above
             if (dateStr.matches("\\d{8}")) {
                 return java.time.LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay();
             }
        } catch (Exception e) {}

        return null;
    }

    private LocalDateTime parseStatementDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        dateStr = dateStr.trim();
        
        // Handle "01 NOV 25" format
        if (Pattern.matches("\\d{2} [A-Z]{3} \\d{2}", dateStr)) {
            try {
                return java.time.LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd MMM yy", java.util.Locale.ENGLISH))
                    .atStartOfDay();
            } catch (DateTimeParseException e) {
                // Fall through to other formatters
            }
        }
        
        return parseDate(dateStr);
    }
}
