package com.settlement.reconciliation.controller;

import com.settlement.reconciliation.service.FileUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/p2p/files")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "File is required and cannot be empty"));
        }
        
        try {
            Map<String, Object> report = fileUploadService.handleFileUpload(file);
            
            // Debug logging
            System.out.println("DEBUG: Backend report keys: " + report.keySet());
            System.out.println("DEBUG: parsedData type: " + (report.get("parsedData") != null ? report.get("parsedData").getClass() : "null"));
            System.out.println("DEBUG: parsedData size: " + (report.get("parsedData") instanceof List ? ((List<?>) report.get("parsedData")).size() : "N/A"));
            
            // Add the parsed records to the response for frontend preview
            Map<String, Object> enhancedResponse = new HashMap<>(report);
            enhancedResponse.put("records", report.get("parsedData"));
            enhancedResponse.put("totalRecords", report.get("recordCount"));
            
            System.out.println("DEBUG: Enhanced response keys: " + enhancedResponse.keySet());
            System.out.println("DEBUG: records in response: " + enhancedResponse.get("records"));
            
            return ResponseEntity.ok(enhancedResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }
}
