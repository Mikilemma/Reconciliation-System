package com.settlement.reconciliation.service;

import com.settlement.reconciliation.dto.UploadResultDto;
import com.settlement.reconciliation.model.CrossSessionMatch;
import com.settlement.reconciliation.model.UnresolvedTransaction;
import com.settlement.reconciliation.repository.CrossSessionMatchRepository;
import com.settlement.reconciliation.repository.UnresolvedTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to integrate cross-session matches into current reports
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportIntegrationService {
    
    private final CrossSessionMatchingService crossSessionMatchingService;
    private final UnresolvedTransactionRepository unresolvedTransactionRepository;
    private final CrossSessionMatchRepository crossSessionMatchRepository;
    
    /**
     * Process cross-session matches and integrate them into the current report
     */
    @Transactional
    public UploadResultDto integrateMatchesIntoReport(UploadResultDto currentResult, String currentSessionId) {
        log.info("Integrating cross-session matches into report for session: {}", currentSessionId);
        
        // Get all matches for current session
        List<CrossSessionMatch> sessionMatches = crossSessionMatchRepository.findBySessionId(currentSessionId);
        
        if (sessionMatches.isEmpty()) {
            log.info("No cross-session matches found for session: {}", currentSessionId);
            return currentResult;
        }
        
        // Group matches by type and process
        Map<String, List<CrossSessionMatch>> matchesByType = sessionMatches.stream()
                .collect(Collectors.groupingBy(match -> match.getMatchType().toString()));
        
        // Process exact matches (highest confidence)
        List<CrossSessionMatch> exactMatches = matchesByType.getOrDefault("EXACT", Collections.emptyList());
        processExactMatches(exactMatches, currentResult, currentSessionId);
        
        // Process fuzzy matches (medium confidence)
        List<CrossSessionMatch> fuzzyMatches = matchesByType.getOrDefault("FUZZY", Collections.emptyList());
        processFuzzyMatches(fuzzyMatches, currentResult, currentSessionId);
        
        // Process manual matches (user confirmed)
        List<CrossSessionMatch> manualMatches = matchesByType.getOrDefault("MANUAL", Collections.emptyList());
        processManualMatches(manualMatches, currentResult, currentSessionId);
        
        // Update report statistics
        updateReportStatistics(currentResult, sessionMatches);
        
        log.info("Integrated {} cross-session matches into report for session: {}", 
                sessionMatches.size(), currentSessionId);
        
        return currentResult;
    }
    
    /**
     * Process exact matches - these should be fully integrated into the report
     */
    private void processExactMatches(List<CrossSessionMatch> exactMatches, UploadResultDto result, String currentSessionId) {
        for (CrossSessionMatch match : exactMatches) {
            try {
                // Get the original unresolved transaction
                Optional<UnresolvedTransaction> originalTxOpt = unresolvedTransactionRepository.findById(match.getOriginalTransactionId());
                
                if (originalTxOpt.isPresent()) {
                    UnresolvedTransaction originalTx = originalTxOpt.get();
                    
                    // Create integrated transaction entry
                    IntegratedTransaction integratedTx = createIntegratedTransaction(originalTx, match, "EXACT");
                    
                    // Add to report as settled transaction
                    result.setSettled(result.getSettled() + 1);
                    
                    // Reduce unresolved count
                    reduceUnresolvedCount(result, originalTx.getStatus());
                    
                    log.info("Integrated exact match: {} ({} {}) into session {}", 
                            originalTx.getStan(), originalTx.getAmount(), currentSessionId);
                }
            } catch (Exception e) {
                log.error("Error processing exact match: {}", match.getId(), e);
            }
        }
    }
    
    /**
     * Process fuzzy matches - these should be marked as resolved but may need review
     */
    private void processFuzzyMatches(List<CrossSessionMatch> fuzzyMatches, UploadResultDto result, String currentSessionId) {
        for (CrossSessionMatch match : fuzzyMatches) {
            try {
                // Get the original unresolved transaction
                Optional<UnresolvedTransaction> originalTxOpt = unresolvedTransactionRepository.findById(match.getOriginalTransactionId());
                
                if (originalTxOpt.isPresent()) {
                    UnresolvedTransaction originalTx = originalTxOpt.get();
                    
                    // For fuzzy matches, we can integrate high-confidence ones (>90%)
                    if (match.getConfidence() >= 90.0) {
                        // Add to report as settled transaction
                        result.setSettled(result.getSettled() + 1);
                        reduceUnresolvedCount(result, originalTx.getStatus());
                        
                        log.info("Integrated high-confidence fuzzy match: {} ({} {}) into session {}", 
                                originalTx.getStan(), originalTx.getAmount(), currentSessionId);
                    } else {
                        // Lower confidence matches remain for manual review
                        log.info("Low-confidence fuzzy match requires manual review: {} (confidence: {}%)", 
                                match.getId(), match.getConfidence());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing fuzzy match: {}", match.getId(), e);
            }
        }
    }
    
    /**
     * Process manual matches - these are user-confirmed and should be fully integrated
     */
    private void processManualMatches(List<CrossSessionMatch> manualMatches, UploadResultDto result, String currentSessionId) {
        for (CrossSessionMatch match : manualMatches) {
            try {
                // Get the original unresolved transaction
                Optional<UnresolvedTransaction> originalTxOpt = unresolvedTransactionRepository.findById(match.getOriginalTransactionId());
                
                if (originalTxOpt.isPresent()) {
                    UnresolvedTransaction originalTx = originalTxOpt.get();
                    
                    // Manual matches are fully integrated
                    result.setSettled(result.getSettled() + 1);
                    reduceUnresolvedCount(result, originalTx.getStatus());
                    
                    log.info("Integrated manual match: {} ({} {}) into session {}", 
                            originalTx.getStan(), originalTx.getAmount(), currentSessionId);
                }
            } catch (Exception e) {
                log.error("Error processing manual match: {}", match.getId(), e);
            }
        }
    }
    
    /**
     * Create integrated transaction for report
     */
    private IntegratedTransaction createIntegratedTransaction(UnresolvedTransaction originalTx, 
                                                              CrossSessionMatch match, 
                                                              String integrationType) {
        IntegratedTransaction integratedTx = new IntegratedTransaction();
        integratedTx.setId(originalTx.getId());
        integratedTx.setStan(originalTx.getStan());
        integratedTx.setAmount(originalTx.getAmount());
        integratedTx.setTransactionDate(originalTx.getTransactionDate());
        integratedTx.setTerminalId(originalTx.getTerminalId());
        integratedTx.setOriginalSessionId(originalTx.getOriginalSessionId());
        integratedTx.setMatchId(match.getId());
        integratedTx.setMatchType(match.getMatchType());
        integratedTx.setConfidence(match.getConfidence());
        integratedTx.setIntegrationType(integrationType);
        integratedTx.setIntegratedAt(LocalDateTime.now());
        integratedTx.setStatus("SETTLED");
        integratedTx.setNotes(String.format("Integrated from previous session %s via %s match (confidence: %.1f%%)", 
                originalTx.getOriginalSessionId(), match.getMatchType(), match.getConfidence()));
        
        return integratedTx;
    }
    
    /**
     * Reduce unresolved transaction counts based on original status
     */
    private void reduceUnresolvedCount(UploadResultDto result, UnresolvedTransaction.TransactionStatus status) {
        switch (status) {
            case DISCREPANT:
                result.setDiscrepant(Math.max(0, result.getDiscrepant() - 1));
                break;
            case MISSING:
                result.setMissing(Math.max(0, result.getMissing() - 1));
                break;
            case DUPLICATE:
                result.setDuplicate(Math.max(0, result.getDuplicate() - 1));
                break;
        }
    }
    
    /**
     * Update report statistics with match information
     */
    private void updateReportStatistics(UploadResultDto result, List<CrossSessionMatch> matches) {
        // Add match statistics to the result
        long exactMatches = matches.stream().filter(m -> m.getMatchType() == CrossSessionMatch.MatchType.EXACT).count();
        long fuzzyMatches = matches.stream().filter(m -> m.getMatchType() == CrossSessionMatch.MatchType.FUZZY).count();
        long manualMatches = matches.stream().filter(m -> m.getMatchType() == CrossSessionMatch.MatchType.MANUAL).count();
        
        double avgConfidence = matches.stream()
                .mapToDouble(CrossSessionMatch::getConfidence)
                .average()
                .orElse(0.0);
        
        // You could add these fields to UploadResultDto
        log.info("Match statistics - Exact: {}, Fuzzy: {}, Manual: {}, Avg Confidence: {:.1f}%", 
                exactMatches, fuzzyMatches, manualMatches, avgConfidence);
    }
    
    /**
     * Integrated transaction representation
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class IntegratedTransaction {
        private String id;
        private String stan;
        private BigDecimal amount;
        private LocalDateTime transactionDate;
        private String terminalId;
        private String originalSessionId;
        private String matchId;
        private CrossSessionMatch.MatchType matchType;
        private Double confidence;
        private String integrationType;
        private LocalDateTime integratedAt;
        private String status;
        private String notes;
    }
}
