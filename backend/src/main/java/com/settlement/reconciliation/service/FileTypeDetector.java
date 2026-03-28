package com.settlement.reconciliation.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

@Service
public class FileTypeDetector {

    public String detectFileType(byte[] fileBytes, String filename) throws IOException {
        if (filename == null) filename = "";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(fileBytes), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            String line1 = null;
            String line3 = null;
            
            while ((line = reader.readLine()) != null && lineCount < 10) {
                lineCount++;
                // Strip BOM if present on first line
                if (lineCount == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                
                if (lineCount == 1) line1 = line;
                if (lineCount == 3) line3 = line;

                // 1. Summary Report
                if (line.contains("Member Net Position Summery Report") || line.contains("Member Net Position Summary Report")) {
                    return "ETH_SWITCH_SUMMARY";
                }
                
                // 2. Switch/Detailed Report
                if (line.contains("BANK Reconciliation Report")) {
                    return "TSEHAY_SWITCH";
                }

                if ((line.contains("STAN") || line.contains("STAN_F11")) && line.contains("Issuer") && line.contains("Acquirer")) {
                    return "TSEHAY_SWITCH";
                }

                // 3. ATM Activity
                if (line.contains("\"TRANS.REF\",\"COMPANY.CODE\"") || (line.contains("TRANS.REF") && line.contains("PAN.NUMBER"))) {
                    return "ATM_ACTIVITY";
                }

                // 4. Bank Statement (Payable/Receivable)
                if (line.contains("\"Book Date\",\"Reference\",\"Description\"")) {
                    // Refine based on earlier lines if possible
                    if (line3 != null) {
                        if (line3.contains("Receivable")) return "RECEIVABLE";
                        if (line3.contains("Payable")) return "PAYABLE";
                    }
                    // Fallback to filename within the content check block
                    if (filename.toLowerCase().contains("receivable")) return "RECEIVABLE";
                    if (filename.toLowerCase().contains("payable")) return "PAYABLE";
                    
                    return "PAYABLE"; // Default fallback for statements
                }
            }
            
            // Final check on first line if not caught in loop
            if (line1 != null && line1.contains("Account Statement -") && line1.contains("TSEHAY BANK S.C.")) {
                // If we reach here, we know it's a statement but haven't seen "Book Date" yet.
                // We'll trust the line3 analysis if it was set
                if (line3 != null) {
                    if (line3.contains("Receivable")) return "RECEIVABLE";
                    if (line3.contains("Payable")) return "PAYABLE";
                }
            }

        } catch (Exception e) {
            System.err.println("Error reading file content in FileTypeDetector: " + e.getMessage());
        }

        // Fallback to filename patterns only if content detection failed completely
        if (filename.toLowerCase().contains("atm_activity_report")) {
            return "ATM_ACTIVITY";
        }
        if (filename.toLowerCase().contains("payable")) {
            return "PAYABLE";
        }
        if (filename.toLowerCase().contains("receivable")) {
            return "RECEIVABLE";
        }
        if (filename.toLowerCase().contains("summary")) {
             return "ETH_SWITCH_SUMMARY";
        }
        
        // Enhanced switch detection - check for switch-related keywords in filename
        if (filename.toLowerCase().contains("switch") || 
            filename.toLowerCase().contains("detail") ||
            filename.toLowerCase().contains("reconciliation")) {
            return "TSEHAY_SWITCH";
        }

        if (filename.toLowerCase().endsWith(".csv")) {
            return "GENERIC_CSV";
        }

        return "UNKNOWN";
    }
}
