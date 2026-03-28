package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.CrossSessionMatch;
import com.settlement.reconciliation.model.UnresolvedTransaction;
import com.settlement.reconciliation.repository.CrossSessionMatchRepository;
import com.settlement.reconciliation.repository.UnresolvedTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fast, batch-based cross-session matching for disputed transactions only.
 * Matches strictly by STAN/Ref + Amount and runs asynchronously.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeMatchService {

    private static final double EXACT_MATCH_CONFIDENCE = 100.0;

    private final UnresolvedTransactionRepository unresolvedTransactionRepository;
    private final CrossSessionMatchRepository crossSessionMatchRepository;

    @Async
    public void matchDisputedAsync(List<UnresolvedTransaction> newTransactions) {
        if (newTransactions == null || newTransactions.isEmpty()) {
            return;
        }

        try {
            // Collect candidate keys
            Set<String> stans = new HashSet<>();
            Set<String> refs = new HashSet<>();
            Set<BigDecimal> amounts = new HashSet<>();

            for (UnresolvedTransaction tx : newTransactions) {
                if (tx.getAmount() != null) {
                    amounts.add(tx.getAmount());
                }
                if (tx.getStan() != null && !tx.getStan().isEmpty()) {
                    stans.add(tx.getStan());
                }
                if (tx.getTransactionRef() != null && !tx.getTransactionRef().isEmpty()) {
                    refs.add(tx.getTransactionRef());
                }
            }

            Map<String, List<UnresolvedTransaction>> byStanAmount = new HashMap<>();
            Map<String, List<UnresolvedTransaction>> byRefAmount = new HashMap<>();

            if (!stans.isEmpty() && !amounts.isEmpty()) {
                List<UnresolvedTransaction> stanCandidates =
                    unresolvedTransactionRepository.findByStanInAndAmountInUnresolved(new ArrayList<>(stans), new ArrayList<>(amounts));
                for (UnresolvedTransaction ut : stanCandidates) {
                    if (ut.getStan() == null || ut.getAmount() == null) continue;
                    String key = ut.getStan() + "|" + ut.getAmount();
                    byStanAmount.computeIfAbsent(key, k -> new ArrayList<>()).add(ut);
                }
            }

            if (!refs.isEmpty() && !amounts.isEmpty()) {
                List<UnresolvedTransaction> refCandidates =
                    unresolvedTransactionRepository.findByTransactionRefInAndAmountInUnresolved(new ArrayList<>(refs), new ArrayList<>(amounts));
                for (UnresolvedTransaction ut : refCandidates) {
                    if (ut.getTransactionRef() == null || ut.getAmount() == null) continue;
                    String key = ut.getTransactionRef() + "|" + ut.getAmount();
                    byRefAmount.computeIfAbsent(key, k -> new ArrayList<>()).add(ut);
                }
            }

            List<CrossSessionMatch> matches = new ArrayList<>();

            for (UnresolvedTransaction newTx : newTransactions) {
                if (newTx.getAmount() == null) continue;

                if (newTx.getStan() != null && !newTx.getStan().isEmpty()) {
                    String key = newTx.getStan() + "|" + newTx.getAmount();
                    addMatchesForKey(newTx, byStanAmount.get(key), matches);
                }
                if (newTx.getTransactionRef() != null && !newTx.getTransactionRef().isEmpty()) {
                    String key = newTx.getTransactionRef() + "|" + newTx.getAmount();
                    addMatchesForKey(newTx, byRefAmount.get(key), matches);
                }
            }

            if (!matches.isEmpty()) {
                crossSessionMatchRepository.saveAll(matches);
                markResolved(matches);
                log.info("DisputeMatchService: saved {} cross-session matches", matches.size());
            }
        } catch (Exception e) {
            log.error("DisputeMatchService failed: {}", e.getMessage(), e);
        }
    }

    private void addMatchesForKey(UnresolvedTransaction newTx, List<UnresolvedTransaction> candidates, List<CrossSessionMatch> out) {
        if (candidates == null || candidates.isEmpty()) return;

        for (UnresolvedTransaction candidate : candidates) {
            if (candidate.getId().equals(newTx.getId())) continue;
            if (Objects.equals(candidate.getOriginalSessionId(), newTx.getOriginalSessionId())) continue;

            // Avoid duplicate matches
            if (crossSessionMatchRepository.findMatchBetweenTransactions(newTx.getId(), candidate.getId()).isPresent()) {
                continue;
            }

            CrossSessionMatch match = new CrossSessionMatch();
            match.setOriginalTransactionId(candidate.getId());
            match.setOriginalSessionId(candidate.getOriginalSessionId());
            match.setMatchedTransactionId(newTx.getId());
            match.setMatchedSessionId(newTx.getOriginalSessionId());
            match.setMatchType(CrossSessionMatch.MatchType.EXACT);
            match.setConfidence(EXACT_MATCH_CONFIDENCE);
            match.setResolvedBy(CrossSessionMatch.ResolvedBy.SYSTEM);
            match.setMatchNotes("Exact match on STAN/Ref + Amount");

            out.add(match);
        }
    }

    private void markResolved(List<CrossSessionMatch> matches) {
        Map<String, List<String>> bySession = new HashMap<>();
        for (CrossSessionMatch m : matches) {
            String resolverSession = m.getMatchedSessionId();
            bySession.computeIfAbsent(resolverSession, k -> new ArrayList<>()).add(m.getOriginalTransactionId());
            bySession.computeIfAbsent(resolverSession, k -> new ArrayList<>()).add(m.getMatchedTransactionId());
        }

        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, List<String>> entry : bySession.entrySet()) {
            unresolvedTransactionRepository.markAsResolved(entry.getValue(), now, entry.getKey());
        }
    }
}
