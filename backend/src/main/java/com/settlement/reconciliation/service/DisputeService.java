package com.settlement.reconciliation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.reconciliation.dto.AggregatedDisputePageResponse;
import com.settlement.reconciliation.model.AuditLog;
import com.settlement.reconciliation.model.Dispute;
import com.settlement.reconciliation.model.ReconciliationResult;
import com.settlement.reconciliation.model.ReconciliationSession;
import com.settlement.reconciliation.model.Transaction;
import com.settlement.reconciliation.dto.AggregatedDispute;
import com.settlement.reconciliation.repository.AuditLogRepository;
import com.settlement.reconciliation.repository.DisputeRepository;
import com.settlement.reconciliation.repository.ReconciliationResultRepository;
import com.settlement.reconciliation.repository.ReconciliationSessionRepository;
import com.settlement.reconciliation.repository.TransactionRepository;
import com.settlement.reconciliation.util.StanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ReconciliationSessionRepository reconciliationSessionRepository;
    private final ObjectMapper objectMapper;

    public DisputeService(
        DisputeRepository disputeRepository,
        ReconciliationResultRepository reconciliationResultRepository,
        TransactionRepository transactionRepository,
        AuditLogRepository auditLogRepository,
        ReconciliationSessionRepository reconciliationSessionRepository
    ) {
        this.disputeRepository = disputeRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.reconciliationSessionRepository = reconciliationSessionRepository;
        this.objectMapper = new ObjectMapper();
    }

    public List<Dispute> getAllDisputes() {
        return disputeRepository.findAll();
    }

    /**
     * Get all disputes aggregated by transaction reference.
     * This consolidates multiple dispute records for the same transaction
     * (e.g., same transactionRef appearing in ATM, Payable, Receivable)
     * into single entries for UI display.
     */
    public List<AggregatedDispute> getAggregatedDisputes() {
        List<Dispute> allDisputes = disputeRepository.findAll();
        return aggregateDisputes(allDisputes);
    }

    /**
     * Get aggregated disputes filtered by status.
     */
    public List<AggregatedDispute> getAggregatedDisputesByStatus(String status) {
        List<Dispute> disputes = disputeRepository.findByDisputeStatus(status);
        return aggregateDisputes(disputes);
    }

    public AggregatedDisputePageResponse getAggregatedDisputesPage(
        String search,
        String status,
        String discrepancyType,
        String transactionType,
        String bank,
        String onUs,
        String sessionId,
        String sessionDate,
        int page,
        int size
    ) {
        List<AggregatedDispute> aggregated = aggregateDisputes(disputeRepository.findAll());
        Map<String, String> sessionDateCache = new HashMap<>();

        Set<String> txTypes = new TreeSet<>();
        Set<String> banks = new TreeSet<>();
        Set<String> discrepancyTypes = new TreeSet<>();

        for (AggregatedDispute d : aggregated) {
            String txType = getTransactionType(d);
            if (txType != null && !txType.isBlank()) txTypes.add(txType);
            String discrepancy = d.getDiscrepancyType();
            if (discrepancy != null && !discrepancy.isBlank()) discrepancyTypes.add(discrepancy);
            String[] parties = getSwitchParties(d);
            if (!parties[0].isBlank()) banks.add(capitalize(parties[0]));
            if (!parties[1].isBlank()) banks.add(capitalize(parties[1]));
        }

        List<AggregatedDispute> filtered = aggregated.stream()
            .filter(d -> matchesSearch(d, search))
            .filter(d -> matchesStatus(d.getDisputeStatus(), status))
            .filter(d -> discrepancyType == null || discrepancyType.isBlank() || "all".equalsIgnoreCase(discrepancyType)
                || discrepancyType.equals(d.getDiscrepancyType()))
            .filter(d -> {
                if (transactionType == null || transactionType.isBlank() || "all".equalsIgnoreCase(transactionType)) return true;
                return transactionType.equals(getTransactionType(d));
            })
            .filter(d -> matchesBankAndOnUs(d, bank, onUs))
            .filter(d -> sessionId == null || sessionId.isBlank() || "all".equalsIgnoreCase(sessionId)
                || sessionId.equals(d.getSessionId()))
            .filter(d -> matchesSessionDate(d, sessionDate, sessionDateCache))
            .sorted(Comparator.comparing(AggregatedDispute::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        long openCount = filtered.stream().filter(d -> "open".equalsIgnoreCase(d.getDisputeStatus())).count();
        long pendingCount = filtered.stream().filter(d -> "pending".equalsIgnoreCase(d.getDisputeStatus())).count();
        long inProgressCount = filtered.stream().filter(d -> "in_progress".equalsIgnoreCase(d.getDisputeStatus())).count();
        long resolvedCount = filtered.stream().filter(d -> isResolvedStatusForDisplay(d.getDisputeStatus())).count();

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int from = safePage * safeSize;
        int to = Math.min(from + safeSize, filtered.size());
        List<AggregatedDispute> pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);

        AggregatedDisputePageResponse response = new AggregatedDisputePageResponse();
        response.setItems(pageItems);
        response.setTotalItems(filtered.size());
        response.setPage(safePage);
        response.setSize(safeSize);
        response.setTotalPages((int) Math.ceil((double) filtered.size() / safeSize));
        response.setTotalConflicts(filtered.size());
        response.setPendingAction(openCount);
        response.setPending(pendingCount);
        response.setInInvestigation(inProgressCount);
        response.setResolutionSet(resolvedCount);
        response.setAvailableTransactionTypes(new ArrayList<>(txTypes));
        response.setAvailableBanks(new ArrayList<>(banks));
        response.setAvailableDiscrepancyTypes(new ArrayList<>(discrepancyTypes));
        return response;
    }

    /**
     * Aggregate disputes by transactionRef, merging source data from all records.
     */
    private String normalizeIdentifier(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        String normalized = id.trim().toUpperCase();

        int slashIdx = normalized.indexOf('/');
        int backslashIdx = normalized.indexOf('\\');
        int cutIdx = -1;
        if (slashIdx > 0 && backslashIdx > 0) {
            cutIdx = Math.min(slashIdx, backslashIdx);
        } else if (slashIdx > 0) {
            cutIdx = slashIdx;
        } else if (backslashIdx > 0) {
            cutIdx = backslashIdx;
        }

        return cutIdx > 0 ? normalized.substring(0, cutIdx).trim() : normalized;
    }

    private List<AggregatedDispute> aggregateDisputes(List<Dispute> disputes) {
        // Pass 1: Build a mapping between normalized STANs and normalized References
        Map<String, String> stanToReferenceMap = new HashMap<>();
        for (Dispute d : disputes) {
            String normStan = normalizeIdentifier(d.getStan());
            String normRef = normalizeIdentifier(d.getTransactionRef());
            
            if (normStan != null && normRef != null) {
                stanToReferenceMap.put(normStan, normRef);
            }
        }

        // Pass 1b: Build reversal cross-map: if a key starts with 'R' (e.g. RFTxxx),
        // register the base key (FTxxx) as an alias pointing to the reversal key.
        // This ensures FTxxx disputes are grouped with their RFTxxx reversal counterpart.
        Map<String, String> baseKeyToReversalKey = new HashMap<>();
        for (Dispute d : disputes) {
            String normStan = normalizeIdentifier(d.getStan());
            String normRef = normalizeIdentifier(d.getTransactionRef());
            String candidateKey = normRef != null ? normRef : normStan;
            if (candidateKey != null && candidateKey.length() > 1
                    && candidateKey.charAt(0) == 'R' && Character.isLetterOrDigit(candidateKey.charAt(1))) {
                // e.g. RFT26021ZFJ2K → base is FT26021ZFJ2K
                String baseKey = candidateKey.substring(1);
                baseKeyToReversalKey.put(baseKey, candidateKey);
            }
        }

        // Pass 2: Group records by canonical key
        Map<String, List<Dispute>> grouped = new HashMap<>();
        for (Dispute d : disputes) {
            String normStan = normalizeIdentifier(d.getStan());
            String normRef = normalizeIdentifier(d.getTransactionRef());
            
            String canonicalKey;
            if (normRef != null) {
                canonicalKey = normRef;
            } else if (normStan != null && stanToReferenceMap.containsKey(normStan)) {
                canonicalKey = stanToReferenceMap.get(normStan);
            } else {
                canonicalKey = normStan != null ? normStan : d.getId();
            }

            // If this key is the base of a known reversal, redirect to the reversal's canonical key
            // so that FTxxx and RFTxxx are in the same group.
            if (baseKeyToReversalKey.containsKey(canonicalKey)) {
                canonicalKey = baseKeyToReversalKey.get(canonicalKey);
            }
            
            grouped.computeIfAbsent(canonicalKey, k -> new ArrayList<>()).add(d);
        }
        
        List<AggregatedDispute> result = new ArrayList<>();
        Map<String, String> txToSessionCache = new HashMap<>();
        Map<String, List<ReconciliationResult>> sessionResultsCache = new HashMap<>();
        Map<String, List<ReconciliationResult>> stanLookupCache = new HashMap<>();
        Map<String, List<ReconciliationResult>> refLookupCache = new HashMap<>();
        Map<String, Boolean> settledGroupCache = new HashMap<>();
        
        for (Map.Entry<String, List<Dispute>> entry : grouped.entrySet()) {
            List<Dispute> group = entry.getValue();
            boolean hasResolvedRecord = group.stream().anyMatch(d -> isResolvedStatusForDisplay(d.getDisputeStatus()));

            // Keep already-resolved disputes visible in dispute views/filters (with audit trace),
            // even when related reconciliation rows are now settled.
            boolean hasActiveDispute = group.stream().anyMatch(d -> {
                if (d.getDisputeStatus() == null) return false;
                String v = d.getDisputeStatus().trim().toLowerCase();
                return v.equals("open") || v.equals("pending") || v.equals("in_progress");
            });

            // If the group is effectively settled, drop it even if there are active (open/pending/in_progress) disputes,
            // as those disputes are likely stale/false-positives created by narrow SQL matching rules.
            if (!hasResolvedRecord && isEffectivelySettledDisputeGroup(group)) {
                continue;
            }
            if (!hasResolvedRecord && hasSettledReconciliationForGroup(
                group,
                entry.getKey(),
                txToSessionCache,
                sessionResultsCache,
                stanLookupCache,
                refLookupCache,
                settledGroupCache
            )) {
                continue;
            }
            AggregatedDispute agg = new AggregatedDispute();
            
            // Use first dispute as base
            Dispute first = group.get(0);
            agg.setId(first.getId());
            String sessionId = resolveSessionId(first.getTransactionId(), txToSessionCache);
            agg.setSessionId(sessionId);
            agg.setTransactionRef(first.getTransactionRef());
            agg.setStan(first.getStan());
            agg.setAmount(first.getAmount());
            agg.setTransactionDate(first.getTransactionDate());
            agg.setTerminalId(first.getTerminalId());
            agg.setDisputeReason(first.getDisputeReason());
            agg.setOriginalStatus(first.getOriginalStatus());
            agg.setDiscrepancyType(first.getDiscrepancyType());
            agg.setDetails(first.getDetails());
            agg.setCreatedAt(first.getCreatedAt());
            
            // Merge source data and collect IDs from all records
            List<String> underlyingIds = new ArrayList<>();
            List<String> sourceFiles = new ArrayList<>();
            String mostResolvedStatus = "open";
            
            for (Dispute d : group) {
                underlyingIds.add(d.getId());
                
                // Merge source data (take non-null values)
                if (d.getSwitchData() != null && agg.getSwitchData() == null) {
                    agg.setSwitchData(d.getSwitchData());
                    sourceFiles.add("Switch (ETHS)");
                }
                if (d.getAtmData() != null && agg.getAtmData() == null) {
                    agg.setAtmData(d.getAtmData());
                    sourceFiles.add("Third Party (ATM)");
                }
                if (d.getPayableData() != null && agg.getPayableData() == null) {
                    agg.setPayableData(d.getPayableData());
                    sourceFiles.add("Payable");
                }
                if (d.getReceivableData() != null && agg.getReceivableData() == null) {
                    agg.setReceivableData(d.getReceivableData());
                    sourceFiles.add("Receivable");
                }
                
                // Use STAN from any record that has it
                if (agg.getStan() == null && d.getStan() != null) {
                    agg.setStan(d.getStan());
                }
                
                // Use amount from any record that has it
                if (agg.getAmount() == null && d.getAmount() != null) {
                    agg.setAmount(d.getAmount());
                }
                
                // Use date from any record that has it
                if (agg.getTransactionDate() == null && d.getTransactionDate() != null) {
                    agg.setTransactionDate(d.getTransactionDate());
                }
                
                // Determine aggregate status (resolved > in_progress > pending > open)
                String status = d.getDisputeStatus();
                if ("resolved".equals(status) || "resolved_manually".equals(status) || "closed".equals(status)) {
                    mostResolvedStatus = status;
                    agg.setResolutionNotes(d.getResolutionNotes());
                    agg.setResolvedBy(d.getResolvedBy());
                    agg.setResolvedAt(d.getResolvedAt());
                } else if ("in_progress".equals(status)) {
                    // in_progress should take precedence over pending/open
                    if (!"resolved".equals(mostResolvedStatus) && !"resolved_manually".equals(mostResolvedStatus) && !"closed".equals(mostResolvedStatus)) {
                        mostResolvedStatus = "in_progress";
                    }
                } else if ("pending".equals(status)) {
                    // pending should take precedence over open but not over in_progress/resolved
                    if ("open".equals(mostResolvedStatus)) {
                        mostResolvedStatus = "pending";
                    }
                }
            }
            
            agg.setDisputeStatus(mostResolvedStatus);
            agg.setUnderlyingDisputeIds(underlyingIds);
            agg.setSourceFiles(sourceFiles);
            agg.setRecordCount(group.size());
            
            result.add(agg);
        }
        
        return result;
    }

    private String resolveSessionId(String transactionId, Map<String, String> txToSessionCache) {
        if (transactionId == null || transactionId.isBlank()) return null;
        return txToSessionCache.computeIfAbsent(transactionId, txId ->
            reconciliationResultRepository.findById(txId)
                .map(ReconciliationResult::getSessionId)
                .orElse(null)
        );
    }

    private boolean hasSettledReconciliationForGroup(
        List<Dispute> group,
        String canonicalKey,
        Map<String, String> txToSessionCache,
        Map<String, List<ReconciliationResult>> sessionResultsCache,
        Map<String, List<ReconciliationResult>> stanLookupCache,
        Map<String, List<ReconciliationResult>> refLookupCache,
        Map<String, Boolean> settledGroupCache
    ) {
        Set<String> keys = new HashSet<>();
        Set<String> sessionIds = new HashSet<>();
        Set<String> transactionIds = new HashSet<>();
        if (canonicalKey != null && !canonicalKey.isBlank()) {
            keys.add(normalizeIdentifier(canonicalKey));
        }

        for (Dispute d : group) {
            if (d.getTransactionId() != null && !d.getTransactionId().isBlank()) {
                transactionIds.add(d.getTransactionId());
            }
            if (d.getStan() != null && !d.getStan().isBlank()) {
                String normalizedStan = normalizeIdentifier(d.getStan());
                if (normalizedStan != null) keys.add(normalizedStan);
                String extractedStan = normalizeIdentifier(StanUtils.extractStan(d.getStan()));
                if (extractedStan != null) keys.add(extractedStan);
            }
            if (d.getTransactionRef() != null && !d.getTransactionRef().isBlank()) {
                String normalizedRef = normalizeIdentifier(d.getTransactionRef());
                if (normalizedRef != null) keys.add(normalizedRef);
            }
        }

        // Also look up the R-prefixed reversal counterpart of each key.
        // e.g. if this dispute is for FT26021ZFJ2K, also search for RFT26021ZFJ2K
        // in reconciliation_results so that the settled reversal can be matched.
        Set<String> reversalKeys = new HashSet<>();
        for (String k : new HashSet<>(keys)) {
            if (k != null && !k.isEmpty() && k.charAt(0) != 'R') {
                reversalKeys.add("R" + k);
            }
        }
        keys.addAll(reversalKeys);

        String cacheKey = buildSettledCacheKey(keys, transactionIds);
        Boolean cached = settledGroupCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (!transactionIds.isEmpty()) {
            for (String transactionId : transactionIds) {
                String sessionId = resolveSessionId(transactionId, txToSessionCache);
                if (sessionId != null && !sessionId.isBlank()) {
                    sessionIds.add(sessionId);
                }
            }
        }

        // Fast path: evaluate settled matches within known sessions only.
        if (!sessionIds.isEmpty()) {
            for (String sessionId : sessionIds) {
                List<ReconciliationResult> sessionResults = sessionResultsCache.computeIfAbsent(
                    sessionId,
                    reconciliationResultRepository::findBySessionId
                );
                for (ReconciliationResult rr : sessionResults) {
                    if (!isEffectivelySettled(rr)) continue;
                    if (matchesAnyKey(rr, keys)) {
                        settledGroupCache.put(cacheKey, true);
                        return true;
                    }
                }
            }
            // Do not return false here: a transaction may have been settled
            // in a later session while its original dispute belongs to an older session.
        }

        // Fallback path for legacy rows without transaction/session linkage.
        List<ReconciliationResult> candidates = new ArrayList<>();
        for (Dispute d : group) {
            if (d.getStan() != null && !d.getStan().isBlank()) {
                candidates.addAll(stanLookupCache.computeIfAbsent(
                    d.getStan(),
                    reconciliationResultRepository::findByStan
                ));
                String normalizedStan = normalizeIdentifier(d.getStan());
                if (normalizedStan != null && !normalizedStan.equals(d.getStan())) {
                    candidates.addAll(stanLookupCache.computeIfAbsent(
                        normalizedStan,
                        reconciliationResultRepository::findByStan
                    ));
                }
                String extractedStan = StanUtils.extractStan(d.getStan());
                if (extractedStan != null && !extractedStan.equals(d.getStan()) && !extractedStan.equals(normalizedStan)) {
                    candidates.addAll(stanLookupCache.computeIfAbsent(
                        extractedStan,
                        reconciliationResultRepository::findByStan
                    ));
                }
            }
            if (d.getTransactionRef() != null && !d.getTransactionRef().isBlank()) {
                candidates.addAll(refLookupCache.computeIfAbsent(
                    d.getTransactionRef(),
                    reconciliationResultRepository::findByTransactionRef
                ));
                String normalizedRef = normalizeIdentifier(d.getTransactionRef());
                if (normalizedRef != null && !normalizedRef.equals(d.getTransactionRef())) {
                    candidates.addAll(refLookupCache.computeIfAbsent(
                        normalizedRef,
                        reconciliationResultRepository::findByTransactionRef
                    ));
                }
            }
        }

        Set<String> seenResultIds = new HashSet<>();
        for (ReconciliationResult rr : candidates) {
            if (!seenResultIds.add(rr.getId())) continue;
            if (!isEffectivelySettled(rr)) continue;
            if (matchesAnyKey(rr, keys)) {
                settledGroupCache.put(cacheKey, true);
                return true;
            }
        }

        settledGroupCache.put(cacheKey, false);
        return false;
    }

    private String buildSettledCacheKey(Set<String> keys, Set<String> transactionIds) {
        List<String> normalizedKeys = new ArrayList<>(keys);
        normalizedKeys.sort(String::compareTo);
        List<String> normalizedTxIds = new ArrayList<>(transactionIds);
        normalizedTxIds.sort(String::compareTo);
        return String.join("|", normalizedKeys) + "::" + String.join("|", normalizedTxIds);
    }

    private boolean isEffectivelySettledDisputeGroup(List<Dispute> group) {
        boolean hasSwitch = group.stream().anyMatch(d -> d.getSwitchData() != null && !d.getSwitchData().isBlank());
        boolean hasAtm = group.stream().anyMatch(d -> d.getAtmData() != null && !d.getAtmData().isBlank());
        boolean hasPayable = group.stream().anyMatch(d -> d.getPayableData() != null && !d.getPayableData().isBlank());
        boolean hasReceivable = group.stream().anyMatch(d -> d.getReceivableData() != null && !d.getReceivableData().isBlank());
        boolean isBi = group.stream().anyMatch(d ->
            containsBalanceInquiry(d.getDetails())
                || containsBalanceInquiry(d.getSwitchData())
                || containsBalanceInquiry(d.getAtmData())
        );
        boolean isTransfer = group.stream().anyMatch(d -> 
            "Transfer In".equalsIgnoreCase(d.getOriginalStatus()) || 
            "Transfer Out".equalsIgnoreCase(d.getOriginalStatus()));
        boolean isReversal = group.stream().anyMatch(d ->
            "reversal".equalsIgnoreCase(d.getOriginalStatus()) ||
            "reversal".equalsIgnoreCase(d.getDisputeStatus()));
        return isTransfer || isReversal || (hasSwitch && hasAtm && (hasPayable || hasReceivable || isBi));
    }

    private boolean isResolvedStatusForDisplay(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase();
        return "resolved".equals(normalized)
            || "resolved_manually".equals(normalized)
            || "closed".equals(normalized);
    }

    private boolean matchesAnyKey(ReconciliationResult rr, Set<String> keys) {
        String rrStan = normalizeIdentifier(rr.getStan());
        String rrRef = normalizeIdentifier(rr.getTransactionRef());
        return (rrStan != null && keys.contains(rrStan)) || (rrRef != null && keys.contains(rrRef));
    }

    private boolean isEffectivelySettled(ReconciliationResult rr) {
        if ("settled".equalsIgnoreCase(rr.getStatus())) {
            return true;
        }
        if ("reversal".equalsIgnoreCase(rr.getStatus())) {
            return true;
        }
        if ("Transfer In".equalsIgnoreCase(rr.getStatus()) || "Transfer Out".equalsIgnoreCase(rr.getStatus())) {
            return true;
        }

        boolean hasSwitch = rr.getSwitchData() != null && !rr.getSwitchData().isBlank();
        boolean hasAtm = rr.getAtmData() != null && !rr.getAtmData().isBlank();
        boolean hasPayable = rr.getPayableData() != null && !rr.getPayableData().isBlank();
        boolean hasReceivable = rr.getReceivableData() != null && !rr.getReceivableData().isBlank();
        boolean isBi = containsBalanceInquiry(rr.getDetails())
            || containsBalanceInquiry(rr.getSwitchData())
            || containsBalanceInquiry(rr.getAtmData());

        return hasSwitch && hasAtm && (hasPayable || hasReceivable || isBi);
    }

    private boolean containsBalanceInquiry(String value) {
        if (value == null || value.isBlank()) return false;
        String v = value.toLowerCase();
        return v.contains("balance inquiry") || v.contains("balance enquiry");
    }

    private boolean matchesSearch(AggregatedDispute d, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.trim().toLowerCase();
        if (d.getStan() != null && d.getStan().toLowerCase().contains(q)) return true;
        if (d.getTransactionRef() != null && d.getTransactionRef().toLowerCase().contains(q)) return true;
        return d.getAmount() != null && d.getAmount().toPlainString().contains(q);
    }

    private boolean matchesStatus(String value, String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) return true;
        if ("resolved".equalsIgnoreCase(filter) || "resolved_manually".equalsIgnoreCase(filter)) {
            return isResolvedStatusForDisplay(value);
        }
        return filter.equalsIgnoreCase(value);
    }

    private boolean matchesSessionDate(AggregatedDispute d, String sessionDate, Map<String, String> sessionDateCache) {
        if (sessionDate == null || sessionDate.isBlank()) return true;
        if (d.getSessionId() == null || d.getSessionId().isBlank()) return false;
        String date = sessionDateCache.computeIfAbsent(d.getSessionId(), sid ->
            reconciliationSessionRepository.findById(sid)
                .map(ReconciliationSession::getSettlementDate)
                .orElse("")
        );
        return sessionDate.equals(date);
    }

    private boolean matchesBankAndOnUs(AggregatedDispute d, String bankFilter, String onUsFilter) {
        String normalizedBank = normalizeName(bankFilter);
        String normalizedOnUs = normalizeName(onUsFilter);
        String[] parties = getSwitchParties(d);
        String issuer = parties[0];
        String acquirer = parties[1];
        boolean matchesBank = normalizedBank.isBlank() || "all".equals(normalizedBank)
            || normalizedBank.equals(issuer) || normalizedBank.equals(acquirer);
        boolean isOnUs = "tsehay".equals(issuer) || "tsehay".equals(acquirer);
        boolean hasParty = !issuer.isBlank() || !acquirer.isBlank();
        boolean matchesOnUs = normalizedOnUs.isBlank() || "all".equals(normalizedOnUs)
            || ("on-us".equals(normalizedOnUs) && isOnUs)
            || ("off-us".equals(normalizedOnUs) && hasParty && !isOnUs);
        return matchesBank && matchesOnUs;
    }

    private String[] getSwitchParties(AggregatedDispute d) {
        JsonNode data = parseJsonSafe(d.getSwitchData());
        if (data == null) return new String[]{"", ""};
        String issuer = normalizeName(readText(data, "issuer", "Issuer"));
        String acquirer = normalizeName(readText(data, "acquirer", "Acquirer"));
        return new String[]{issuer, acquirer};
    }

    private String getTransactionType(AggregatedDispute d) {
        List<String> sources = List.of(
            d.getSwitchData() == null ? "" : d.getSwitchData(),
            d.getAtmData() == null ? "" : d.getAtmData(),
            d.getPayableData() == null ? "" : d.getPayableData(),
            d.getReceivableData() == null ? "" : d.getReceivableData(),
            d.getDetails() == null ? "" : d.getDetails()
        );
        String raw = "";
        for (String src : sources) {
            JsonNode data = parseJsonSafe(src);
            if (data == null) continue;
            raw = readText(data, "transactionDescription", "description", "TransactionType", "txnType", "type");
            if (raw != null && !raw.isBlank()) break;
        }
        String text = normalizeType(raw);
        if (text.contains("balance")) return "Balance Inquiry";
        if (text.contains("pos") || text.contains("purchase")) return "POS";
        if (text.contains("atm") || text.contains("withdrawal") || text.contains("cash")) return "ATM";
        if (text.contains("transfer") || text.contains("account2account")) return "Transfer";
        if (text.contains("reversal")) return "Reversal";
        return text.isBlank() ? "Other" : text.toUpperCase();
    }

    private JsonNode parseJsonSafe(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child != null && !child.isNull()) {
                String text = child.asText("");
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Synchronizes dispute status changes with corresponding ReconciliationResult records.
     * Updates ReconciliationResult status based on dispute resolution.
     */
    private void syncReconciliationResultStatus(Dispute dispute, String newDisputeStatus) {
        try {
            String newReconciliationStatus = mapDisputeStatusToReconciliationStatus(newDisputeStatus);
            System.out.println("DEBUG: Syncing dispute " + dispute.getId() + " with ref: " + dispute.getTransactionRef() + " and STAN: " + dispute.getStan());
            
            List<ReconciliationResult> matchingResults = new ArrayList<>();
            Set<String> seenResultIds = new HashSet<>();

            // Direct match by reconciliation result primary key (most reliable link)
            if (dispute.getTransactionId() != null && !dispute.getTransactionId().isEmpty()) {
                reconciliationResultRepository.findById(dispute.getTransactionId()).ifPresent(result -> {
                    matchingResults.add(result);
                    seenResultIds.add(result.getId());
                });
            }
            
            if (dispute.getStan() != null && !dispute.getStan().isEmpty()) {
                for (ReconciliationResult rr : reconciliationResultRepository.findByStan(dispute.getStan())) {
                    if (seenResultIds.add(rr.getId())) {
                        matchingResults.add(rr);
                    }
                }
                String normalizedStan = normalizeIdentifier(dispute.getStan());
                if (normalizedStan != null && !normalizedStan.equals(dispute.getStan())) {
                    for (ReconciliationResult rr : reconciliationResultRepository.findByStan(normalizedStan)) {
                        if (seenResultIds.add(rr.getId())) {
                            matchingResults.add(rr);
                        }
                    }
                }
                String extractedStan = StanUtils.extractStan(dispute.getStan());
                if (extractedStan != null && !extractedStan.equals(dispute.getStan()) && !extractedStan.equals(normalizedStan)) {
                    for (ReconciliationResult rr : reconciliationResultRepository.findByStan(extractedStan)) {
                        if (seenResultIds.add(rr.getId())) {
                            matchingResults.add(rr);
                        }
                    }
                }
            }
            
            if (dispute.getTransactionRef() != null && !dispute.getTransactionRef().isEmpty()) {
                for (ReconciliationResult rr : reconciliationResultRepository.findByTransactionRef(dispute.getTransactionRef())) {
                    if (seenResultIds.add(rr.getId())) {
                        matchingResults.add(rr);
                    }
                }
                String normalizedRef = normalizeIdentifier(dispute.getTransactionRef());
                if (normalizedRef != null && !normalizedRef.equals(dispute.getTransactionRef())) {
                    for (ReconciliationResult rr : reconciliationResultRepository.findByTransactionRef(normalizedRef)) {
                        if (seenResultIds.add(rr.getId())) {
                            matchingResults.add(rr);
                        }
                    }
                }
            }
            
            for (ReconciliationResult result : matchingResults) {
                result.setStatus(newReconciliationStatus);
                reconciliationResultRepository.save(result);
                System.out.println("DEBUG: Updated ReconciliationResult: " + result.getId() + " to status: " + newReconciliationStatus);
            }
            
            syncTransactionStatus(dispute, newDisputeStatus);
            
        } catch (Exception e) {
            System.err.println("Error synchronizing ReconciliationResult status for dispute " + dispute.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Synchronizes dispute status changes with corresponding Transaction records.
     */
    private void syncTransactionStatus(Dispute dispute, String newDisputeStatus) {
        try {
            String newTransactionStatus = mapDisputeStatusToTransactionStatus(newDisputeStatus);
            List<Transaction> matchingTransactions = new ArrayList<>();
            
            if (dispute.getStan() != null && !dispute.getStan().isEmpty()) {
                matchingTransactions.addAll(transactionRepository.findByStanNo(dispute.getStan()));
                // Also try normalized STAN
                String normalizedStan = normalizeIdentifier(dispute.getStan());
                if (normalizedStan != null && !normalizedStan.equals(dispute.getStan())) {
                    matchingTransactions.addAll(transactionRepository.findByStanNo(normalizedStan));
                }
                String extractedStan = StanUtils.extractStan(dispute.getStan());
                if (extractedStan != null && !extractedStan.equals(dispute.getStan()) && !extractedStan.equals(normalizedStan)) {
                    matchingTransactions.addAll(transactionRepository.findByStanNo(extractedStan));
                }
            }
            
            if (dispute.getTransactionRef() != null && !dispute.getTransactionRef().isEmpty()) {
                matchingTransactions.addAll(transactionRepository.findByTransRef(dispute.getTransactionRef()));
                // Also try normalized TransactionRef
                String normalizedRef = normalizeIdentifier(dispute.getTransactionRef());
                if (normalizedRef != null && !normalizedRef.equals(dispute.getTransactionRef())) {
                    matchingTransactions.addAll(transactionRepository.findByTransRef(normalizedRef));
                }
            }
            
            // Update all matching transaction statuses
            for (Transaction transaction : matchingTransactions) {
                transaction.setStatus(newTransactionStatus);
                transactionRepository.save(transaction);
            }
            
        } catch (Exception e) {
            System.err.println("Error synchronizing Transaction status for dispute " + dispute.getId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Maps dispute status to appropriate reconciliation result status.
     */
    private String mapDisputeStatusToReconciliationStatus(String disputeStatus) {
        switch (disputeStatus.toLowerCase()) {
            case "resolved":
            case "resolved_manually":
            case "closed":
                return "settled";
            case "pending":
            case "in_progress":
                return "pending";
            case "open":
            default:
                return "discrepant";
        }
    }
    
    /**
     * Maps dispute status to appropriate transaction status.
     */
    private String mapDisputeStatusToTransactionStatus(String disputeStatus) {
        switch (disputeStatus.toLowerCase()) {
            case "resolved":
            case "resolved_manually":
            case "closed":
                return "settled";
            case "pending":
            case "in_progress":
                return "pending";
            case "open":
            default:
                return "discrepant";
        }
    }

    public Optional<Dispute> getDisputeById(String id) {
        return disputeRepository.findById(id);
    }

    public List<Dispute> getDisputesByStatus(String status) {
        return disputeRepository.findByDisputeStatus(status);
    }

    public List<AuditLog> getAuditLogsByDisputeId(String disputeId) {
        return auditLogRepository.findByDisputeIdOrderByCreatedAtDesc(disputeId);
    }

    public List<AuditLog> getAuditLogsByDisputeIds(List<String> disputeIds) {
        if (disputeIds == null || disputeIds.isEmpty()) {
            return List.of();
        }
        List<AuditLog> logs = new ArrayList<>();
        for (String id : disputeIds) {
            logs.addAll(auditLogRepository.findByDisputeIdOrderByCreatedAtDesc(id));
        }
        logs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return logs;
    }

    @Transactional
    public Dispute updateDisputeStatus(String id, String newStatus, String resolutionNotes, String resolvedBy) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found with ID: " + id));

        String oldStatus = dispute.getDisputeStatus();
        dispute.setDisputeStatus(newStatus);
        dispute.setResolutionNotes(resolutionNotes);
        dispute.setResolvedBy(resolvedBy);
        dispute.setUpdatedAt(LocalDateTime.now());
        if ("resolved".equalsIgnoreCase(newStatus) || "resolved_manually".equalsIgnoreCase(newStatus)) {
            dispute.setResolvedAt(LocalDateTime.now());
        }
        
        // Synchronize status with ReconciliationResult table
        syncReconciliationResultStatus(dispute, newStatus);

        Dispute saved = disputeRepository.save(dispute);
        createAuditLog(saved, oldStatus, newStatus, resolutionNotes, resolvedBy);
        return saved;
    }

    /**
     * Update status for all underlying disputes in an aggregated dispute.
     * This ensures all records for the same transaction get resolved together.
     */
    @Transactional
    public void updateAggregatedDisputeStatus(List<String> disputeIds, String newStatus, String resolutionNotes, String resolvedBy) {
        for (String id : disputeIds) {
            Optional<Dispute> optDispute = disputeRepository.findById(id);
            if (optDispute.isPresent()) {
                Dispute dispute = optDispute.get();
                String oldStatus = dispute.getDisputeStatus();
                dispute.setDisputeStatus(newStatus);
                dispute.setResolutionNotes(resolutionNotes);
                dispute.setResolvedBy(resolvedBy);
                dispute.setUpdatedAt(LocalDateTime.now());
                if ("resolved".equalsIgnoreCase(newStatus) || "resolved_manually".equalsIgnoreCase(newStatus)) {
                    dispute.setResolvedAt(LocalDateTime.now());
                }
                
                // Synchronize status with ReconciliationResult table
                syncReconciliationResultStatus(dispute, newStatus);
                
                Dispute saved = disputeRepository.save(dispute);
                createAuditLog(saved, oldStatus, newStatus, resolutionNotes, resolvedBy);
            }
        }
    }

    private void createAuditLog(Dispute dispute, String oldStatus, String newStatus, String notes, String resolvedBy) {
        String username = (resolvedBy == null || resolvedBy.trim().isEmpty()) ? "unknown" : resolvedBy.trim();
        AuditLog log = new AuditLog();
        log.setUsername(username);
        log.setAction("DISPUTE_STATUS_UPDATE");
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setDisputeId(dispute.getId());
        log.setNotes(notes);
        log.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(log);
    }
}
