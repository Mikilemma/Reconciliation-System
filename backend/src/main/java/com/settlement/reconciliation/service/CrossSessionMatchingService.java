package com.settlement.reconciliation.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CrossSessionMatchingService {
    
    private final UnresolvedTransactionRepository unresolvedTransactionRepository;
    private final CrossSessionMatchRepository crossSessionMatchRepository;
    
    // Matching configuration
    private static final double EXACT_MATCH_CONFIDENCE = 100.0;
    private static final double FUZZY_MATCH_CONFIDENCE = 85.0;
    private static final double DATE_RANGE_MATCH_CONFIDENCE = 75.0;
    private static final int FUZZY_DATE_RANGE_DAYS = 7;
    private static final double AMOUNT_TOLERANCE = 0.01; // Small amount difference tolerance
    
    /**
     * Process new transactions and find cross-session matches
     */
    public List<CrossSessionMatch> processNewTransactions(List<UnresolvedTransaction> newTransactions) {
        List<CrossSessionMatch> matches = new ArrayList<>();
        
        for (UnresolvedTransaction newTx : newTransactions) {
            List<CrossSessionMatch> transactionMatches = findMatchesForTransaction(newTx);
            matches.addAll(transactionMatches);
        }
        
        // Save all matches
        crossSessionMatchRepository.saveAll(matches);
        
        // Mark matched transactions as resolved
        markTransactionsAsResolved(matches);
        
        log.info("Found {} cross-session matches for {} new transactions", 
                matches.size(), newTransactions.size());
        
        return matches;
    }
    
    /**
     * Find matches for a single transaction
     */
    private List<CrossSessionMatch> findMatchesForTransaction(UnresolvedTransaction transaction) {
        List<CrossSessionMatch> matches = new ArrayList<>();
        
        // 1. Exact Match: STAN + Amount
        List<UnresolvedTransaction> exactMatches = findExactMatches(transaction);
        matches.addAll(createMatches(transaction, exactMatches, CrossSessionMatch.MatchType.EXACT, EXACT_MATCH_CONFIDENCE));
        
        // 2. Fuzzy Match: Amount + Terminal ID
        List<UnresolvedTransaction> fuzzyMatches = findFuzzyMatches(transaction);
        matches.addAll(createMatches(transaction, fuzzyMatches, CrossSessionMatch.MatchType.FUZZY, FUZZY_MATCH_CONFIDENCE));
        
        // 3. Date Range Match: Amount + Date Range
        List<UnresolvedTransaction> dateRangeMatches = findDateRangeMatches(transaction);
        matches.addAll(createMatches(transaction, dateRangeMatches, CrossSessionMatch.MatchType.FUZZY, DATE_RANGE_MATCH_CONFIDENCE));
        
        // Remove duplicates and check for existing matches
        return deduplicateMatches(matches);
    }
    
    /**
     * Find exact matches based on STAN and Amount
     */
    private List<UnresolvedTransaction> findExactMatches(UnresolvedTransaction transaction) {
        if (transaction.getStan() == null || transaction.getAmount() == null) {
            return Collections.emptyList();
        }
        
        return unresolvedTransactionRepository.findPotentialExactMatches(
                transaction.getStan(), transaction.getAmount())
                .stream()
                .filter(match -> !match.getId().equals(transaction.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Find fuzzy matches based on Amount and Terminal ID
     */
    private List<UnresolvedTransaction> findFuzzyMatches(UnresolvedTransaction transaction) {
        if (transaction.getAmount() == null || transaction.getTerminalId() == null) {
            return Collections.emptyList();
        }
        
        return unresolvedTransactionRepository.findPotentialFuzzyMatches(
                transaction.getAmount(), transaction.getTerminalId())
                .stream()
                .filter(match -> !match.getId().equals(transaction.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Find matches within a date range based on Amount
     */
    private List<UnresolvedTransaction> findDateRangeMatches(UnresolvedTransaction transaction) {
        if (transaction.getAmount() == null || transaction.getTransactionDate() == null) {
            return Collections.emptyList();
        }
        
        LocalDateTime startDate = transaction.getTransactionDate().minusDays(FUZZY_DATE_RANGE_DAYS);
        LocalDateTime endDate = transaction.getTransactionDate().plusDays(FUZZY_DATE_RANGE_DAYS);
        
        return unresolvedTransactionRepository.findPotentialDateRangeMatches(
                transaction.getAmount(), startDate, endDate)
                .stream()
                .filter(match -> !match.getId().equals(transaction.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Create CrossSessionMatch objects
     */
    private List<CrossSessionMatch> createMatches(UnresolvedTransaction newTransaction, 
                                                  List<UnresolvedTransaction> matchedTransactions,
                                                  CrossSessionMatch.MatchType matchType,
                                                  double confidence) {
        List<CrossSessionMatch> matches = new ArrayList<>();
        
        for (UnresolvedTransaction matchedTx : matchedTransactions) {
            // Check if match already exists
            if (crossSessionMatchRepository.findMatchBetweenTransactions(
                    newTransaction.getId(), matchedTx.getId()).isPresent()) {
                continue;
            }
            
            CrossSessionMatch match = new CrossSessionMatch();
            match.setOriginalTransactionId(matchedTx.getId());
            match.setOriginalSessionId(matchedTx.getOriginalSessionId());
            match.setMatchedTransactionId(newTransaction.getId());
            match.setMatchedSessionId(newTransaction.getOriginalSessionId());
            match.setMatchType(matchType);
            match.setConfidence(confidence);
            match.setResolvedBy(CrossSessionMatch.ResolvedBy.SYSTEM);
            match.setMatchNotes(generateMatchNotes(newTransaction, matchedTx, matchType));
            
            matches.add(match);
        }
        
        return matches;
    }
    
    /**
     * Generate match notes for documentation
     */
    private String generateMatchNotes(UnresolvedTransaction tx1, UnresolvedTransaction tx2, 
                                    CrossSessionMatch.MatchType matchType) {
        StringBuilder notes = new StringBuilder();
        notes.append("Match Type: ").append(matchType).append("\n");
        
        switch (matchType) {
            case EXACT:
                notes.append("Exact match on STAN (").append(tx1.getStan())
                      .append(") and Amount (").append(tx1.getAmount()).append(")");
                break;
            case FUZZY:
                notes.append("Fuzzy match on Amount (").append(tx1.getAmount())
                      .append(") and Terminal ID (").append(tx1.getTerminalId()).append(")");
                break;
        }
        
        notes.append("\nOriginal Session: ").append(tx1.getOriginalSessionId());
        notes.append("\nMatched Session: ").append(tx2.getOriginalSessionId());
        
        return notes.toString();
    }
    
    /**
     * Remove duplicate matches and check for existing matches
     */
    private List<CrossSessionMatch> deduplicateMatches(List<CrossSessionMatch> matches) {
        // Group by transaction pairs and keep the highest confidence match
        Map<String, CrossSessionMatch> bestMatches = new HashMap<>();
        
        for (CrossSessionMatch match : matches) {
            String key = createMatchKey(match);
            CrossSessionMatch existing = bestMatches.get(key);
            
            if (existing == null || match.getConfidence() > existing.getConfidence()) {
                bestMatches.put(key, match);
            }
        }
        
        return new ArrayList<>(bestMatches.values());
    }
    
    /**
     * Create a unique key for a match pair
     */
    private String createMatchKey(CrossSessionMatch match) {
        String tx1 = match.getOriginalTransactionId();
        String tx2 = match.getMatchedTransactionId();
        return tx1.compareTo(tx2) < 0 ? tx1 + "-" + tx2 : tx2 + "-" + tx1;
    }
    
    /**
     * Mark transactions as resolved based on matches
     */
    private void markTransactionsAsResolved(List<CrossSessionMatch> matches) {
        Set<String> transactionIds = new HashSet<>();
        Set<String> sessionIds = new HashSet<>();
        
        for (CrossSessionMatch match : matches) {
            transactionIds.add(match.getOriginalTransactionId());
            transactionIds.add(match.getMatchedTransactionId());
            sessionIds.add(match.getMatchedSessionId());
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Mark all matched transactions as resolved
        for (String sessionId : sessionIds) {
            unresolvedTransactionRepository.markAsResolved(
                    new ArrayList<>(transactionIds), now, sessionId);
        }
        
        log.info("Marked {} transactions as resolved", transactionIds.size());
    }
    
    /**
     * Create manual match between two transactions
     */
    public CrossSessionMatch createManualMatch(String originalTransactionId, String matchedTransactionId, 
                                             String notes, String reviewedBy) {
        // Check if match already exists
        Optional<CrossSessionMatch> existing = crossSessionMatchRepository
                .findMatchBetweenTransactions(originalTransactionId, matchedTransactionId);
        
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Match already exists between these transactions");
        }
        
        // Get transaction details
        Optional<UnresolvedTransaction> originalTx = unresolvedTransactionRepository.findById(originalTransactionId);
        Optional<UnresolvedTransaction> matchedTx = unresolvedTransactionRepository.findById(matchedTransactionId);
        
        if (originalTx.isEmpty() || matchedTx.isEmpty()) {
            throw new IllegalArgumentException("One or both transactions not found");
        }
        
        CrossSessionMatch match = new CrossSessionMatch();
        match.setOriginalTransactionId(originalTransactionId);
        match.setOriginalSessionId(originalTx.get().getOriginalSessionId());
        match.setMatchedTransactionId(matchedTransactionId);
        match.setMatchedSessionId(matchedTx.get().getOriginalSessionId());
        match.setMatchType(CrossSessionMatch.MatchType.MANUAL);
        match.setConfidence(100.0); // Manual matches are 100% confidence
        match.setResolvedBy(CrossSessionMatch.ResolvedBy.USER);
        match.setMatchNotes(notes);
        match.setReviewStatus(CrossSessionMatch.ReviewStatus.APPROVED);
        match.setReviewedBy(reviewedBy);
        match.setReviewedAt(LocalDateTime.now());
        
        CrossSessionMatch savedMatch = crossSessionMatchRepository.save(match);
        
        // Mark transactions as resolved
        markTransactionsAsResolved(List.of(savedMatch));
        
        return savedMatch;
    }
    
    /**
     * Reject a match
     */
    public void rejectMatch(String matchId, String reason, String reviewedBy) {
        Optional<CrossSessionMatch> matchOpt = crossSessionMatchRepository.findById(matchId);
        
        if (matchOpt.isEmpty()) {
            throw new IllegalArgumentException("Match not found");
        }
        
        CrossSessionMatch match = matchOpt.get();
        match.setReviewStatus(CrossSessionMatch.ReviewStatus.REJECTED);
        match.setRejectionReason(reason);
        match.setReviewedBy(reviewedBy);
        match.setReviewedAt(LocalDateTime.now());
        
        crossSessionMatchRepository.save(match);
        
        log.info("Match {} rejected by {} for reason: {}", matchId, reviewedBy, reason);
    }
    
    /**
     * Get statistics for cross-session matching
     */
    public Map<String, Object> getMatchingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Total unresolved transactions
        long totalUnresolved = unresolvedTransactionRepository.findUnresolvedTransactions().size();
        stats.put("totalUnresolved", totalUnresolved);
        
        // Total matches
        long totalMatches = crossSessionMatchRepository.count();
        stats.put("totalMatches", totalMatches);
        
        // Matches by type
        List<Object[]> matchesByType = crossSessionMatchRepository.countMatchesByType();
        Map<String, Long> matchesByTypeMap = matchesByType.stream()
                .collect(Collectors.toMap(
                        arr -> arr[0].toString(),
                        arr -> (Long) arr[1]
                ));
        stats.put("matchesByType", matchesByTypeMap);
        
        // Matches by confidence level
        List<Object[]> matchesByConfidence = crossSessionMatchRepository.countMatchesByConfidenceLevel();
        Map<String, Long> matchesByConfidenceMap = matchesByConfidence.stream()
                .collect(Collectors.toMap(
                        arr -> arr[0].toString(),
                        arr -> (Long) arr[1]
                ));
        stats.put("matchesByConfidence", matchesByConfidenceMap);
        
        // Match rate
        double matchRate = totalUnresolved > 0 ? (double) totalMatches / totalUnresolved * 100 : 0;
        stats.put("matchRate", Math.round(matchRate));
        
        return stats;
    }
    
    /**
     * Get matches needing review
     */
    public List<CrossSessionMatch> getMatchesNeedingReview(double threshold) {
        return crossSessionMatchRepository.findMatchesNeedingReview(
                threshold, CrossSessionMatch.ReviewStatus.PENDING);
    }
    
    /**
     * Get high confidence rejected matches
     */
    public List<CrossSessionMatch> getHighConfidenceRejectedMatches(double threshold) {
        return crossSessionMatchRepository.findHighConfidenceRejectedMatches(
                threshold, CrossSessionMatch.ReviewStatus.REJECTED);
    }

    /**
     * Get cross-session matches for a specific session
     */
    public List<CrossSessionMatch> getMatchesBySession(String sessionId) {
        return crossSessionMatchRepository.findBySessionId(sessionId);
    }
}
