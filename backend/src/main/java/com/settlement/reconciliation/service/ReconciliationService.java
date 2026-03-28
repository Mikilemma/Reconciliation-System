package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.ReconciliationSession;
import com.settlement.reconciliation.model.ReconciliationResult;
import com.settlement.reconciliation.model.Transaction;
import com.settlement.reconciliation.model.Dispute;
import com.settlement.reconciliation.model.UnresolvedTransaction;
import com.settlement.reconciliation.repository.ReconciliationSessionRepository;
import com.settlement.reconciliation.repository.ReconciliationResultRepository;
import com.settlement.reconciliation.repository.TransactionRepository;
import com.settlement.reconciliation.repository.DisputeRepository;
import com.settlement.reconciliation.repository.UploadedFileRepository;
import com.settlement.reconciliation.repository.UnresolvedTransactionRepository;
import com.settlement.reconciliation.model.UploadedFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class ReconciliationService {

    private final ReconciliationSessionRepository reconciliationSessionRepository;
    private final TransactionRepository transactionRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final DisputeRepository disputeRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileUploadService fileUploadService;
    private final ReportService reportService;
    private final UnresolvedTransactionRepository unresolvedTransactionRepository;
    private final DisputeMatchService disputeMatchService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public ReconciliationService(ReconciliationSessionRepository reconciliationSessionRepository,
                                 TransactionRepository transactionRepository,
                                 ReconciliationResultRepository reconciliationResultRepository,
                                 DisputeRepository disputeRepository,
                                 UploadedFileRepository uploadedFileRepository,
                                 FileUploadService fileUploadService,
                                 ReportService reportService,
                                 UnresolvedTransactionRepository unresolvedTransactionRepository,
                                 DisputeMatchService disputeMatchService) {
        this.reconciliationSessionRepository = reconciliationSessionRepository;
        this.transactionRepository = transactionRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.disputeRepository = disputeRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.fileUploadService = fileUploadService;
        this.reportService = reportService;
        this.unresolvedTransactionRepository = unresolvedTransactionRepository;
        this.disputeMatchService = disputeMatchService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public List<ReconciliationSession> getAllSessions() {
        return reconciliationSessionRepository.findAll();
    }

    public List<ReconciliationResult> getReconciliationResults(String sessionId) {
        return reconciliationResultRepository.findBySessionId(sessionId);
    }

    /**
     * Get aggregated reconciliation results for a session.
     * Consolidates multiple results for the same transaction (e.g. Switch-ATM and Payable-Receivable)
     * into a single entry for UI display.
     */
    public List<com.settlement.reconciliation.dto.AggregatedReconciliationResult> getAggregatedReconciliationResults(String sessionId) {
        List<ReconciliationResult> results = reconciliationResultRepository.findBySessionId(sessionId);
        return aggregateResults(results);
    }

    private String normalizeIdentifier(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        String normalized = id.trim().toUpperCase();

        // Keep identifiers intact except explicit path-like suffixes.
        // Avoid splitting on '_' or whitespace to prevent over-merging unrelated refs.
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

    private boolean isBalanceInquiryText(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String v = text.toLowerCase();
        return v.contains("balance inquiry") || v.contains("balance enquiry");
    }

    private boolean isBalanceInquiryGroup(List<ReconciliationResult> group) {
        for (ReconciliationResult r : group) {
            if (isBalanceInquiryText(r.getDetails())) return true;
            if (isBalanceInquiryText(r.getSwitchData())) return true;
            if (isBalanceInquiryText(r.getAtmData())) return true;
        }
        return false;
    }

    private List<com.settlement.reconciliation.dto.AggregatedReconciliationResult> aggregateResults(List<ReconciliationResult> results) {
        class AggGroup {
            final List<ReconciliationResult> items = new ArrayList<>();
            final java.util.Set<String> keys = new java.util.HashSet<>();
        }

        Map<String, AggGroup> keyToGroup = new java.util.HashMap<>();
        List<AggGroup> groups = new ArrayList<>();

        for (ReconciliationResult r : results) {
            String stanKey = normalizeIdentifier(r.getStan());
            String refKey = normalizeIdentifier(r.getTransactionRef());

            java.util.Set<String> keys = new java.util.HashSet<>();
            if (stanKey != null && !stanKey.isEmpty()) keys.add(stanKey);
            if (refKey != null && !refKey.isEmpty()) keys.add(refKey);

            // Cross-link reversal identifiers: if an id starts with 'R' followed by a letter/digit,
            // add the base id (without leading 'R') as an additional key so that RFTxxx and FTxxx
            // are treated as the same transaction group.
            java.util.Set<String> extraKeys = new java.util.HashSet<>();
            for (String k : keys) {
                if (k.length() > 1 && k.charAt(0) == 'R' && Character.isLetterOrDigit(k.charAt(1))) {
                    extraKeys.add(k.substring(1));
                }
            }
            keys.addAll(extraKeys);

            AggGroup target = null;
            for (String k : keys) {
                AggGroup g = keyToGroup.get(k);
                if (g != null) {
                    target = g;
                    break;
                }
            }

            if (target == null) {
                target = new AggGroup();
                groups.add(target);
            } else {
                // Merge any other groups referenced by keys into target
                for (String k : keys) {
                    AggGroup g = keyToGroup.get(k);
                    if (g != null && g != target) {
                        target.items.addAll(g.items);
                        target.keys.addAll(g.keys);
                        for (String existingKey : g.keys) {
                            keyToGroup.put(existingKey, target);
                        }
                        groups.remove(g);
                    }
                }
            }

            target.items.add(r);
            for (String k : keys) {
                target.keys.add(k);
                keyToGroup.put(k, target);
            }

            if (keys.isEmpty()) {
                String fallbackKey = "ID:" + r.getId();
                target.keys.add(fallbackKey);
                keyToGroup.put(fallbackKey, target);
            }
        }

        List<com.settlement.reconciliation.dto.AggregatedReconciliationResult> aggregatedList = new ArrayList<>();
        
        for (AggGroup groupObj : groups) {
            List<ReconciliationResult> group = groupObj.items;
            com.settlement.reconciliation.dto.AggregatedReconciliationResult agg = new com.settlement.reconciliation.dto.AggregatedReconciliationResult();
            
            ReconciliationResult first = group.get(0);
            agg.setId(first.getId());
            agg.setSessionId(first.getSessionId());
            agg.setStan(first.getStan());
            agg.setTransactionRef(first.getTransactionRef());
            agg.setAmount(first.getAmount());
            agg.setTransactionDate(first.getTransactionDate());
            agg.setTerminalId(first.getTerminalId());
            agg.setDiscrepancyType(first.getDiscrepancyType());
            agg.setDetails(first.getDetails());
            
            List<String> sourceFiles = new ArrayList<>();
            List<String> payableDataList = new ArrayList<>();
            List<String> receivableDataList = new ArrayList<>();
            // Check if we have the required sources for settlement
            boolean hasSwitch = group.stream().anyMatch(r -> r.getSwitchData() != null);
            boolean hasAtm = group.stream().anyMatch(r -> r.getAtmData() != null);
            boolean hasPayable = group.stream().anyMatch(r -> r.getPayableData() != null);
            boolean hasReceivable = group.stream().anyMatch(r -> r.getReceivableData() != null);

            // Business rule:
            // 1) If any member record is already settled (e.g. manually resolved dispute sync),
            //    keep aggregate as settled.
            // 2) Otherwise infer from source coverage.
            //    IF ATM & Switch & (Payable OR Receivable) => Settled
            //    OR if ATM & Switch and transaction is BI => Settled
            //    ELSE => Discrepant
            boolean isBalanceInquiry = isBalanceInquiryGroup(group);
            boolean hasExplicitSettled = group.stream()
                .anyMatch(r -> "settled".equalsIgnoreCase(r.getStatus()));
            boolean hasReversal = group.stream()
                .anyMatch(r -> "reversal".equalsIgnoreCase(r.getStatus()));
            boolean hasTransferIn = group.stream()
                .anyMatch(r -> "Transfer In".equalsIgnoreCase(r.getStatus()));
            boolean hasTransferOut = group.stream()
                .anyMatch(r -> "Transfer Out".equalsIgnoreCase(r.getStatus()));
            boolean hasPending = group.stream()
                .anyMatch(r -> "pending".equalsIgnoreCase(r.getStatus()));
            boolean isSettledBySources = hasSwitch && hasAtm && (hasPayable || hasReceivable || isBalanceInquiry);
            String status;
            if (hasExplicitSettled) {
                status = "settled";
            } else if (hasReversal) {
                status = "reversal";
            } else if (hasTransferIn) {
                status = "Transfer In";
            } else if (hasTransferOut) {
                status = "Transfer Out";
            } else if (hasPending) {
                status = "pending";
            } else if (isSettledBySources) {
                status = "settled";
            } else {
                status = "discrepant";
            }

            for (ReconciliationResult r : group) {
                if ((agg.getStan() == null || agg.getStan().isEmpty()) && r.getStan() != null && !r.getStan().isEmpty()) {
                    agg.setStan(r.getStan());
                }
                if ((agg.getTransactionRef() == null || agg.getTransactionRef().isEmpty()) && r.getTransactionRef() != null && !r.getTransactionRef().isEmpty()) {
                    agg.setTransactionRef(r.getTransactionRef());
                }
                if (agg.getAmount() == null && r.getAmount() != null) {
                    agg.setAmount(r.getAmount());
                }
                if ((agg.getTransactionDate() == null || agg.getTransactionDate().isEmpty()) && r.getTransactionDate() != null && !r.getTransactionDate().isEmpty()) {
                    agg.setTransactionDate(r.getTransactionDate());
                }
                if ((agg.getTerminalId() == null || agg.getTerminalId().isEmpty()) && r.getTerminalId() != null && !r.getTerminalId().isEmpty()) {
                    agg.setTerminalId(r.getTerminalId());
                }
                if ((agg.getDiscrepancyType() == null || agg.getDiscrepancyType().isEmpty()) && r.getDiscrepancyType() != null && !r.getDiscrepancyType().isEmpty()) {
                    agg.setDiscrepancyType(r.getDiscrepancyType());
                }
                if ((agg.getDetails() == null || agg.getDetails().isEmpty()) && r.getDetails() != null && !r.getDetails().isEmpty()) {
                    agg.setDetails(r.getDetails());
                }

                // Merge source data - preserve existing data, don't overwrite with null
                if (r.getSwitchData() != null && agg.getSwitchData() == null) {
                    agg.setSwitchData(r.getSwitchData());
                }
                if (r.getAtmData() != null && agg.getAtmData() == null) {
                    agg.setAtmData(r.getAtmData());
                }
                if (r.getPayableData() != null) {
                    payableDataList.add(r.getPayableData());
                }
                if (r.getReceivableData() != null) {
                    receivableDataList.add(r.getReceivableData());
                }
                
                // Merge source files string
                if (r.getSourceFiles() != null && !r.getSourceFiles().isEmpty()) {
                    sourceFiles.add(r.getSourceFiles());
                }
                
                // If aggregate is discrepant, prefer a specific discrepancy from any member
                if ("discrepant".equals(status) && "discrepant".equals(r.getStatus())) {
                    agg.setDiscrepancyType(r.getDiscrepancyType());
                    agg.setDetails(r.getDetails());
                }
            }
            
            agg.setStatus(status);
            if ("settled".equals(status)) {
                agg.setDiscrepancyType(null);
                agg.setDetails(null);
            }

            agg.setPayableData(mergeJsonList(payableDataList));
            agg.setReceivableData(mergeJsonList(receivableDataList));
            agg.setSourceFiles(String.join(" | ", sourceFiles));
            agg.setRecordCount(group.size());
            
            aggregatedList.add(agg);
        }
        
        return aggregatedList;
    }

    public List<Transaction> getTransactionsBySession(String sessionId) {
        return transactionRepository.findBySessionId(sessionId);
    }

    @Transactional
    public ReconciliationSession startNewReconciliationSession(String settlementDate, List<String> fileSessionIds) {
        ReconciliationSession session = new ReconciliationSession();
        session.setId(UUID.randomUUID().toString());
        session.setSettlementDate(settlementDate);
        
        session.setCreatedAt(LocalDateTime.now());
        session.setProcessedAt(LocalDateTime.now());
        session.setNetSettlementAmount(BigDecimal.ZERO);
        session.setMatchRate(BigDecimal.ZERO);
        
        session.setTotalTransactions(0);
        session.setSettledCount(0);
        session.setDiscrepantCount(0);
        session.setMissingCount(0);
        session.setDuplicateCount(0);

        ReconciliationSession savedSession = reconciliationSessionRepository.save(session);
        
        if (fileSessionIds != null) {
            for (String fileSessionId : fileSessionIds) {
                try {
                    UploadedFile persisted = fileUploadService.persistParsedData(fileSessionId, savedSession.getId());
                    if (persisted == null) {
                        System.out.println("WARN: Skipped persistence for file session " + fileSessionId + " (no parsed data available).");
                    }
                } catch (java.io.IOException e) {
                    // Log and continue: if the parsing data is missing, reconciliation may still proceed
                    // based on existing persisted transactions.
                    System.err.println("ERROR: Failed to persist file data for session " + fileSessionId + ": " + e.getMessage());
                }
            }
        }
        
        return savedSession;
    }

    @Transactional
    public ReconciliationSession performReconciliation(String sessionId) {
        ReconciliationSession session = reconciliationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation session not found: " + sessionId));

        cleanupSessionData(sessionId);

        // Ensure any RFT reversal references are treated as reversals (even for previously-inserted uploads)
        entityManager.createNativeQuery(
            "UPDATE transactions SET status = 'reversal' " +
            "WHERE session_id = ? AND source IN ('ATM','Switch') " +
            "AND LOWER(trans_ref) LIKE 'rft%'")
            .setParameter(1, sessionId)
            .executeUpdate();

        // Categorize Transfer In/Out based on description (for previously uploaded data)
        entityManager.createNativeQuery(
            "UPDATE transactions SET status = 'Transfer In' " +
            "WHERE session_id = ? AND source IN ('Payable','Receivable') " +
            "AND LOWER(description) LIKE 'transfer in%'")
            .setParameter(1, sessionId)
            .executeUpdate();

        entityManager.createNativeQuery(
            "UPDATE transactions SET status = 'Transfer Out' " +
            "WHERE session_id = ? AND source IN ('Payable','Receivable') " +
            "AND LOWER(description) LIKE 'transfer out%'")
            .setParameter(1, sessionId)
            .executeUpdate();

        long switchCount = countBySource(sessionId, "Switch");
        long atmCount = countBySource(sessionId, "ATM");
        long payableCount = countBySource(sessionId, "Payable");
        long receivableCount = countBySource(sessionId, "Receivable");

        long totalTransactions = switchCount + atmCount + payableCount + receivableCount;

        // SQL-based reconciliation (set-based)
        insertAtmSwitchSettled(sessionId);
        insertSwitchReversalTransactions(sessionId);
        insertAtmReversalTransactions(sessionId);
        insertTransferTransactions(sessionId);
        insertAtmSwitchAmountMismatch(sessionId);
        insertAtmMissingSwitch(sessionId);

        insertPayableReceivableSettled(sessionId);
        insertPayableReceivableAmountMismatch(sessionId);
        insertPayableMissingReceivable(sessionId);
        insertReceivableMissingPayable(sessionId);

        // Create disputes for discrepant results
        insertDisputesForSession(sessionId);
        removeEffectivelySettledDisputes(sessionId);

        List<com.settlement.reconciliation.dto.AggregatedReconciliationResult> aggregatedResults =
            getAggregatedReconciliationResults(sessionId);
        long settledCount = aggregatedResults.stream().filter(r -> 
            "settled".equals(r.getStatus()) || 
            "reversal".equals(r.getStatus()) ||
            "Transfer In".equals(r.getStatus()) ||
            "Transfer Out".equals(r.getStatus())
        ).count();
        long discrepantCount = aggregatedResults.stream().filter(r -> "discrepant".equals(r.getStatus())).count();
        long missingCount = 0;

        session.setTotalTransactions(aggregatedResults.size());
        session.setSettledCount((int) settledCount);
        session.setDiscrepantCount((int) discrepantCount);
        session.setMissingCount((int) missingCount);
        session.setProcessedAt(LocalDateTime.now());
        
        BigDecimal netSettlementAmount = sumSettledAmount(sessionId);
        session.setNetSettlementAmount(netSettlementAmount);
        
        int aggregatedTotal = aggregatedResults.size();
        BigDecimal matchRate = aggregatedTotal > 0 ?
            BigDecimal.valueOf((double) settledCount / aggregatedTotal * 100) : BigDecimal.ZERO;
        session.setMatchRate(matchRate);

        // Save disputed/missing as unresolved and trigger fast cross-session matching (async)
        insertUnresolvedFromResults(sessionId);
        List<UnresolvedTransaction> unresolved = unresolvedTransactionRepository.findByOriginalSession(sessionId);
        if (!unresolved.isEmpty()) {
            disputeMatchService.matchDisputedAsync(unresolved);
        }

        return reconciliationSessionRepository.save(session);
    }

    public Map<String, Object> getSessionSummaryReport(String sessionId) {
        return reportService.generateSummaryReport(sessionId);
    }

    private int reconcileSwitch(List<Transaction> switchTransactions, List<ReconciliationResult> results, String sessionId, Map<String, String> fileNames) {
        // Switch transactions are not added here - they're added when matched with ATM
        // or marked as unmatched if no ATM record is found
        return 0;
    }

    private int reconcileAtmOptimized(List<Transaction> atmTransactions, Map<String, Transaction> switchLookup,
                             List<ReconciliationResult> results, List<Dispute> disputes, String sessionId, Map<String, String> fileNames) {
        int discrepantCount = 0;

        for (Transaction atmTx : atmTransactions) {
            if (atmTx.getStanNo() == null || atmTx.getStanNo().isEmpty()) {
                // Skip transactions without STAN
                continue;
            }
            
            Transaction matchingSwitch = switchLookup.get(normalizeIdentifier(atmTx.getStanNo()));
            
            if (matchingSwitch != null) {
                if (atmTx.getTxnAmount() == null || matchingSwitch.getTxnAmount() == null ||
                    atmTx.getTxnAmount().compareTo(matchingSwitch.getTxnAmount()) != 0) {
                    
                    System.out.println("DEBUG: Mismatch for STAN " + atmTx.getStanNo() + 
                                     " | ATM Amt: " + atmTx.getTxnAmount() + ", Switch Amt: " + matchingSwitch.getTxnAmount() +
                                     " | ATM Date: " + atmTx.getValueDate() + ", Switch Date: " + matchingSwitch.getValueDate());
                    
                    ReconciliationResult result = createDiscrepancyResult(atmTx, matchingSwitch, sessionId, "amount_or_date_mismatch", fileNames);
                    results.add(result);
                    
                    Dispute dispute = createDispute(result, "Amount or date mismatch between ATM and Switch");
                    disputes.add(dispute);
                    discrepantCount++;
                } else {
                    ReconciliationResult result = new ReconciliationResult();
                    result.setId(UUID.randomUUID().toString());
                    result.setSessionId(sessionId);
                    result.setStan(atmTx.getStanNo());
                    result.setTransactionRef(matchingSwitch.getTransRef());
                    result.setAmount(atmTx.getTxnAmount());
                    result.setTransactionDate(atmTx.getValueDate() != null ? atmTx.getValueDate().toString() : null);
                    result.setTerminalId(atmTx.getTerminalId());
                    result.setStatus("settled");
                    result.setDetails(matchingSwitch.getDescription() != null ? matchingSwitch.getDescription() : "ATM transaction matched with Switch");
                    String atmFile = fileNames.getOrDefault(atmTx.getFileId(), atmTx.getFileId());
                    String switchFile = fileNames.getOrDefault(matchingSwitch.getFileId(), matchingSwitch.getFileId());
                    result.setSourceFiles("ATM: " + atmFile + " | Switch: " + switchFile);
                    result.setAtmData(convertToJson(atmTx));
                    result.setSwitchData(convertToJson(matchingSwitch));
                    results.add(result);
                }
            } else {
                ReconciliationResult result = createDiscrepancyResult(atmTx, null, sessionId, "missing_in_switch", fileNames);
                results.add(result);
                
                Dispute dispute = createDispute(result, "ATM transaction with no matching Switch entry");
                disputes.add(dispute);
                discrepantCount++;
            }
        }
        return discrepantCount;
    }

    private int reconcilePayableReceivableOptimized(List<Transaction> payableTransactions, Map<String, Transaction> receivableLookup,
                                         List<ReconciliationResult> results, List<Dispute> disputes, String sessionId, Map<String, String> fileNames) {
        int discrepantCount = 0;
        System.out.println("DEBUG: reconcilePayableReceivableOptimized - Start. Payables: " + payableTransactions.size() + ", Receivables in lookup: " + receivableLookup.size());

        for (Transaction payableTx : payableTransactions) {
            if (payableTx.getTransRef() == null || payableTx.getTransRef().isEmpty()) {
                continue;
            }
            
            String normalizedRef = normalizeIdentifier(payableTx.getTransRef());
            // System.out.println("DEBUG: Looking up Payable Ref: " + payableTx.getTransRef() + " -> Normalized: " + normalizedRef);
            Transaction matchingReceivable = receivableLookup.get(normalizedRef);
            
            if (matchingReceivable != null) {
                BigDecimal payableAmount = payableTx.getTxnAmount() != null ? payableTx.getTxnAmount() : BigDecimal.ZERO;
                BigDecimal receivableAmount = matchingReceivable.getTxnAmount() != null ? matchingReceivable.getTxnAmount() : BigDecimal.ZERO;
                
                if (payableAmount.compareTo(receivableAmount) != 0) {
                    ReconciliationResult result = createDiscrepancyResult(payableTx, matchingReceivable, sessionId, "amount_mismatch", fileNames);
                    results.add(result);
                    
                    Dispute dispute = createDispute(result, "Amount mismatch between Payable and Receivable");
                    disputes.add(dispute);
                    discrepantCount++;
                } else {
                    ReconciliationResult result = new ReconciliationResult();
                    result.setId(UUID.randomUUID().toString());
                    result.setSessionId(sessionId);
                    result.setTransactionRef(payableTx.getTransRef());
                    result.setStan(payableTx.getStanNo() != null ? payableTx.getStanNo() : matchingReceivable.getStanNo());
                    result.setAmount(payableAmount);
                    result.setTransactionDate(payableTx.getBookingDate() != null ? payableTx.getBookingDate().toString() : null);
                    result.setStatus("settled");
                    result.setDetails("Payable matched with Receivable");
                    String payableFile = fileNames.getOrDefault(payableTx.getFileId(), payableTx.getFileId());
                    String receivableFile = fileNames.getOrDefault(matchingReceivable.getFileId(), matchingReceivable.getFileId());
                    result.setSourceFiles("Payable: " + payableFile + " | Receivable: " + receivableFile);
                    result.setPayableData(convertToJson(payableTx));
                    result.setReceivableData(convertToJson(matchingReceivable));
                    results.add(result);
                }
            } else {
                ReconciliationResult result = createDiscrepancyResult(payableTx, null, sessionId, "missing_in_receivable", fileNames);
                results.add(result);
                
                Dispute dispute = createDispute(result, "Payable transaction with no matching Receivable");
                disputes.add(dispute);
                discrepantCount++;
            }
        }

        // Add records for Receivable transactions that were not matched
        java.util.Set<String> matchedRefs = payableTransactions.stream()
                .filter(tx -> tx.getTransRef() != null)
                .map(tx -> normalizeIdentifier(tx.getTransRef()))
                .collect(java.util.stream.Collectors.toSet());

        for (Transaction receivableTx : receivableLookup.values()) {
            if (receivableTx.getTransRef() != null && !matchedRefs.contains(normalizeIdentifier(receivableTx.getTransRef()))) {
                ReconciliationResult result = createDiscrepancyResult(receivableTx, null, sessionId, "missing_in_payable", fileNames);
                results.add(result);
                
                Dispute dispute = createDispute(result, "Receivable transaction with no matching Payable");
                disputes.add(dispute);
                discrepantCount++;
            }
        }
        
        return discrepantCount;
    }

    private ReconciliationResult createDiscrepancyResult(Transaction transaction, Transaction counterpart, String sessionId, String discrepancyType, Map<String, String> fileNames) {
        ReconciliationResult result = new ReconciliationResult();
        result.setId(UUID.randomUUID().toString());
        result.setSessionId(sessionId);
        result.setDiscrepancyType(discrepancyType);
        result.setStatus("discrepant");
        
        result.setStan(transaction.getStanNo());
        result.setTransactionRef(transaction.getTransRef());
        result.setAmount(transaction.getTxnAmount());
        result.setTransactionDate(transaction.getValueDate() != null ? transaction.getValueDate().toString() : null);
        result.setTerminalId(transaction.getTerminalId());
        String fileName = fileNames.getOrDefault(transaction.getFileId(), transaction.getFileId());
        result.setSourceFiles(transaction.getSource() + ": " + fileName);
        
        if (counterpart != null) {
            String cpFileName = fileNames.getOrDefault(counterpart.getFileId(), counterpart.getFileId());
            result.setSourceFiles(result.getSourceFiles() + " | " + counterpart.getSource() + ": " + cpFileName);
        }

        result.setDetails(discrepancyType); 

        // Set data for both sides
        setSourceData(result, transaction);
        if (counterpart != null) {
            setSourceData(result, counterpart);
        }
        
        return result;
    }

    private void setSourceData(ReconciliationResult result, Transaction tx) {
        String json = convertToJson(tx);
        if ("ATM".equals(tx.getSource())) {
            result.setAtmData(json);
        } else if ("Payable".equals(tx.getSource())) {
            result.setPayableData(json);
        } else if ("Receivable".equals(tx.getSource())) {
            result.setReceivableData(json);
        } else if ("Switch".equals(tx.getSource())) {
            result.setSwitchData(json);
        }
    }

    private Dispute createDispute(ReconciliationResult result, String reason) {
        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID().toString());
        dispute.setTransactionId(result.getId());
        dispute.setStan(result.getStan());
        dispute.setTransactionRef(result.getTransactionRef());
        dispute.setAmount(result.getAmount());
        dispute.setTransactionDate(result.getTransactionDate());
        dispute.setTerminalId(result.getTerminalId());
        dispute.setDisputeStatus("open");
        dispute.setDisputeReason(reason);
        dispute.setOriginalStatus(result.getStatus());
        dispute.setDiscrepancyType(result.getDiscrepancyType());
        dispute.setDetails(result.getDetails());
        return dispute;
    }

    private boolean areDatesEqual(LocalDateTime date1, LocalDateTime date2) {
        if (date1 == null || date2 == null) return false;
        return date1.toLocalDate().equals(date2.toLocalDate());
    }

    private BigDecimal calculateNetSettlement(List<ReconciliationResult> results) {
        return results.stream()
                .filter(r -> "settled".equals(r.getStatus()))
                .map(ReconciliationResult::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String convertToJson(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            // Don't return empty objects
            if (json == null || json.equals("{}") || json.equals("null")) {
                return null;
            }
            return json;
        } catch (Exception e) {
            System.err.println("ERROR: convertToJson failed for object: " + obj.getClass().getSimpleName());
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private long countBySource(String sessionId, String source) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM transactions WHERE session_id = ? AND source = ?")
            .setParameter(1, sessionId)
            .setParameter(2, source)
            .getSingleResult();
        return count.longValue();
    }

    private long countResultsByStatus(String sessionId, String status) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM reconciliation_results WHERE session_id = ? AND status = ?")
            .setParameter(1, sessionId)
            .setParameter(2, status)
            .getSingleResult();
        return count.longValue();
    }

    private BigDecimal sumSettledAmount(String sessionId) {
        Number sum = (Number) entityManager.createNativeQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM reconciliation_results WHERE session_id = ? AND status = 'settled'")
            .setParameter(1, sessionId)
            .getSingleResult();
        return new BigDecimal(sum.toString());
    }

    private String normExpr(String col) {
        // Mimic normalizeIdentifier: uppercase and strip path-like suffixes only (\ or /).
        return "UPPER(SUBSTRING_INDEX(SUBSTRING_INDEX(TRIM(" + col + "), '/', 1), '\\\\', 1))";
    }

    private String mergeJsonList(List<String> items) {
        if (items == null || items.isEmpty()) return null;
        List<String> deduped = items.stream()
            .filter(s -> s != null && !s.isEmpty())
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        if (deduped.isEmpty()) return null;
        if (deduped.size() == 1) return deduped.get(0);
        List<Object> parsed = new ArrayList<>();
        for (String s : deduped) {
            try {
                parsed.add(objectMapper.readValue(s, Object.class));
            } catch (Exception e) {
                parsed.add(s);
            }
        }
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception e) {
            return deduped.get(0);
        }
    }

    private void insertAtmSwitchSettled(String sessionId) {
        String nStanA = normExpr("a.stan_no");
        String nStanS = normExpr("s.stan_no");
        String stanMatch = "(" + nStanA + " = " + nStanS + " OR a.stan_no = s.stan_no)";
        String biMatch = "(" +
                "LOWER(COALESCE(a.description, '')) LIKE '%%balance enquiry%%' OR " +
                "LOWER(COALESCE(a.description, '')) LIKE '%%balance inquiry%%' OR " +
                "LOWER(COALESCE(s.description, '')) LIKE '%%balance enquiry%%' OR " +
                "LOWER(COALESCE(s.description, '')) LIKE '%%balance inquiry%%'" +
                ")";
        String atmJson = "JSON_OBJECT(" +
                "'stanNo', a.stan_no, 'transRef', a.trans_ref, 'txnAmount', a.txn_amount, " +
                "'terminalId', a.terminal_id, 'valueDate', a.value_date, 'description', a.description)";
        String switchJson = "JSON_OBJECT(" +
                "'stanNo', s.stan_no, 'transRef', s.trans_ref, 'txnAmount', s.txn_amount, " +
                "'terminalId', s.terminal_id, 'valueDate', s.value_date, 'description', s.description, " +
                "'issuer', s.issuer, 'acquirer', s.acquirer, 'mtiCode', s.mti_code)";
        String sql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), a.session_id, a.stan_no, COALESCE(a.trans_ref, s.trans_ref), a.txn_amount,
                   COALESCE(a.value_date, s.value_date), a.terminal_id,
                   'settled', NULL,
                   IFNULL(s.description, 'ATM transaction matched with Switch'),
                   CONCAT('ATM: ', af.filename, ' | Switch: ', sf.filename),
                   %s, %s, NULL, NULL
            FROM transactions a
            JOIN transactions s
              ON a.session_id = ? AND s.session_id = ?
             AND a.source = 'ATM' AND s.source = 'Switch'
             AND a.stan_no IS NOT NULL AND a.stan_no <> ''
             AND %s
             AND (a.txn_amount = s.txn_amount OR %s)
            LEFT JOIN uploaded_files af ON af.id = a.file_id
            LEFT JOIN uploaded_files sf ON sf.id = s.file_id
            """.formatted(atmJson, switchJson, stanMatch, biMatch);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .executeUpdate();
    }

    /**
     * Insert reversal transactions from the Switch feed so they appear in reconciliation results.
     * This enables filtering by reversal transactions and counts them as settled for match rate.
     */
    private void insertTransferTransactions(String sessionId) {
        String json = "JSON_OBJECT('stanNo', s.stan_no, 'transRef', s.trans_ref, 'txnAmount', s.txn_amount, " +
                "'valueDate', s.value_date, 'description', s.description)";
        String sql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), s.session_id, s.stan_no, s.trans_ref, s.txn_amount,
                   s.value_date, s.terminal_id,
                   s.status, NULL,
                   CONCAT(s.status, ' transaction'),
                   CONCAT(s.source, ': ', sf.filename),
                   NULL, NULL, 
                   IF(s.source = 'Payable', %s, NULL),
                   IF(s.source = 'Receivable', %s, NULL)
            FROM transactions s
            LEFT JOIN uploaded_files sf ON sf.id = s.file_id
            WHERE s.session_id = ?
              AND s.source IN ('Payable', 'Receivable')
              AND s.status IN ('Transfer In', 'Transfer Out')
            """.formatted(json, json);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .executeUpdate();
    }

    private void insertSwitchReversalTransactions(String sessionId) {
        String switchJson = "JSON_OBJECT('stanNo', s.stan_no, 'transRef', s.trans_ref, 'txnAmount', s.txn_amount, " +
                "'terminalId', s.terminal_id, 'valueDate', s.value_date, 'description', s.description, " +
                "'issuer', s.issuer, 'acquirer', s.acquirer, 'mtiCode', s.mti_code)";
        String sql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), s.session_id, s.stan_no, s.trans_ref, s.txn_amount,
                   s.value_date, s.terminal_id,
                   'reversal', NULL,
                   'Switch reversal transaction',
                   CONCAT('Switch: ', sf.filename),
                   NULL, %s, NULL, NULL
            FROM transactions s
            LEFT JOIN uploaded_files sf ON sf.id = s.file_id
            WHERE s.session_id = ?
              AND s.source = 'Switch'
              AND LOWER(s.status) = 'reversal'
            """.formatted(switchJson);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .executeUpdate();
    }

    private void insertAtmReversalTransactions(String sessionId) {
        String atmJson = "JSON_OBJECT('stanNo', a.stan_no, 'transRef', a.trans_ref, 'txnAmount', a.txn_amount, " +
                "'terminalId', a.terminal_id, 'valueDate', a.value_date, 'description', a.description)";
        String sql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), a.session_id, a.stan_no, a.trans_ref, a.txn_amount,
                   a.value_date, a.terminal_id,
                   'reversal', NULL,
                   'ATM reversal transaction',
                   CONCAT('ATM: ', af.filename),
                   %s, NULL, NULL, NULL
            FROM transactions a
            LEFT JOIN uploaded_files af ON af.id = a.file_id
            WHERE a.session_id = ?
              AND a.source = 'ATM'
              AND LOWER(a.status) = 'reversal'
            """.formatted(atmJson);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .executeUpdate();
    }

    private void insertAtmSwitchAmountMismatch(String sessionId) {
        String nStanA = normExpr("a.stan_no");
        String nStanS = normExpr("s.stan_no");
        String stanMatch = "(" + nStanA + " = " + nStanS + " OR a.stan_no = s.stan_no)";
        String biMatch = "(" +
                "LOWER(COALESCE(a.description, '')) LIKE '%%balance enquiry%%' OR " +
                "LOWER(COALESCE(a.description, '')) LIKE '%%balance inquiry%%' OR " +
                "LOWER(COALESCE(s.description, '')) LIKE '%%balance enquiry%%' OR " +
                "LOWER(COALESCE(s.description, '')) LIKE '%%balance inquiry%%'" +
                ")";
        String atmJson = "JSON_OBJECT(" +
                "'stanNo', a.stan_no, 'transRef', a.trans_ref, 'txnAmount', a.txn_amount, " +
                "'terminalId', a.terminal_id, 'valueDate', a.value_date, 'description', a.description)";
        String switchJson = "JSON_OBJECT(" +
                "'stanNo', s.stan_no, 'transRef', s.trans_ref, 'txnAmount', s.txn_amount, " +
                "'terminalId', s.terminal_id, 'valueDate', s.value_date, 'description', s.description, " +
                "'issuer', s.issuer, 'acquirer', s.acquirer, 'mtiCode', s.mti_code)";
        String sql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), a.session_id, a.stan_no, COALESCE(a.trans_ref, s.trans_ref), a.txn_amount,
                   COALESCE(a.value_date, s.value_date), a.terminal_id,
                   'discrepant', 'amount_or_date_mismatch',
                   'amount_or_date_mismatch',
                   CONCAT('ATM: ', af.filename, ' | Switch: ', sf.filename),
                   %s, %s, NULL, NULL
            FROM transactions a
            JOIN transactions s
              ON a.session_id = ? AND s.session_id = ?
             AND a.source = 'ATM' AND s.source = 'Switch'
             AND a.stan_no IS NOT NULL AND a.stan_no <> ''
             AND %s
             AND a.txn_amount <> s.txn_amount
             AND NOT %s
             AND LOWER(COALESCE(a.trans_ref, '')) NOT LIKE 'rft%%'
             AND LOWER(COALESCE(s.trans_ref, '')) NOT LIKE 'rft%%'
            LEFT JOIN uploaded_files af ON af.id = a.file_id
            LEFT JOIN uploaded_files sf ON sf.id = s.file_id
            """.formatted(atmJson, switchJson, stanMatch, biMatch);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .executeUpdate();
    }

    private void insertAtmMissingSwitch(String sessionId) {
        String nStanA = normExpr("a.stan_no");
        String nStanS = normExpr("s.stan_no");
        String stanMatch = "(" + nStanA + " = " + nStanS + " OR a.stan_no = s.stan_no)";
        String atmJson = "JSON_OBJECT(" +
                "'stanNo', a.stan_no, 'transRef', a.trans_ref, 'txnAmount', a.txn_amount, " +
                "'terminalId', a.terminal_id, 'valueDate', a.value_date, 'description', a.description)";
        String sql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), a.session_id, a.stan_no, a.trans_ref, a.txn_amount,
                   a.value_date, a.terminal_id,
                   'discrepant', 'missing_in_switch',
                   'missing_in_switch',
                   CONCAT('ATM: ', af.filename),
                   %s, NULL, NULL, NULL
            FROM transactions a
            LEFT JOIN transactions s
              ON a.session_id = ? AND s.session_id = ?
             AND a.source = 'ATM' AND s.source = 'Switch'
             AND a.stan_no IS NOT NULL AND a.stan_no <> ''
             AND %s
            LEFT JOIN uploaded_files af ON af.id = a.file_id
            WHERE a.session_id = ? AND a.source = 'ATM'
              AND LOWER(a.status) <> 'reversal'
              AND LOWER(COALESCE(a.trans_ref, '')) NOT LIKE 'rft%%'
              AND a.stan_no IS NOT NULL AND a.stan_no <> ''
              AND s.id IS NULL
            """.formatted(atmJson, stanMatch);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .setParameter(3, sessionId)
            .executeUpdate();
    }

    private void insertPayableReceivableSettled(String sessionId) {
        String nRefP = normExpr("p.trans_ref");
        String nRefR = normExpr("r.trans_ref");
        String refMatch = "(" + nRefP + " = " + nRefR + " OR p.trans_ref = r.trans_ref)";
        String payableJson = "JSON_OBJECT(" +
                "'transRef', p.trans_ref, 'txnAmount', p.txn_amount, 'credit', p.credit, 'debit', p.debit, " +
                "'bookingDate', p.booking_date, 'description', p.description)";
        String receivableJson = "JSON_OBJECT(" +
                "'transRef', r.trans_ref, 'txnAmount', r.txn_amount, 'credit', r.credit, 'debit', r.debit, " +
                "'bookingDate', r.booking_date, 'description', r.description)";
        String sql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), p.session_id, p.stan_no, p.trans_ref, p.txn_amount,
                   p.booking_date, p.terminal_id,
                   'settled', NULL,
                   'Payable matched with Receivable',
                   CONCAT('Payable: ', pf.filename, ' | Receivable: ', rf.filename),
                   NULL, NULL, %s, %s
            FROM transactions p
            JOIN transactions r
              ON p.session_id = ? AND r.session_id = ?
             AND p.source = 'Payable' AND r.source = 'Receivable'
             AND p.status = 'unsettled' AND r.status = 'unsettled'
             AND p.trans_ref IS NOT NULL AND p.trans_ref <> ''
             AND %s
             AND p.txn_amount = r.txn_amount
            LEFT JOIN uploaded_files pf ON pf.id = p.file_id
            LEFT JOIN uploaded_files rf ON rf.id = r.file_id
            """.formatted(payableJson, receivableJson, refMatch);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .executeUpdate();
    }

    private void insertPayableReceivableAmountMismatch(String sessionId) {
        String nRefP = normExpr("p.trans_ref");
        String nRefR = normExpr("r.trans_ref");
        String refMatch = "(" + nRefP + " = " + nRefR + " OR p.trans_ref = r.trans_ref)";
        String payableJson = "JSON_OBJECT(" +
                "'transRef', p.trans_ref, 'txnAmount', p.txn_amount, 'credit', p.credit, 'debit', p.debit, " +
                "'bookingDate', p.booking_date, 'description', p.description)";
        String receivableJson = "JSON_OBJECT(" +
                "'transRef', r.trans_ref, 'txnAmount', r.txn_amount, 'credit', r.credit, 'debit', r.debit, " +
                "'bookingDate', r.booking_date, 'description', r.description)";
        String sql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), p.session_id, p.stan_no, p.trans_ref, p.txn_amount,
                   p.booking_date, p.terminal_id,
                   'discrepant', 'amount_mismatch',
                   'amount_mismatch',
                   CONCAT('Payable: ', pf.filename, ' | Receivable: ', rf.filename),
                   NULL, NULL, %s, %s
            FROM transactions p
            JOIN transactions r
              ON p.session_id = ? AND r.session_id = ?
             AND p.source = 'Payable' AND r.source = 'Receivable'
             AND p.status = 'unsettled' AND r.status = 'unsettled'
             AND p.trans_ref IS NOT NULL AND p.trans_ref <> ''
             AND %s
             AND p.txn_amount <> r.txn_amount
            LEFT JOIN uploaded_files pf ON pf.id = p.file_id
            LEFT JOIN uploaded_files rf ON rf.id = r.file_id
            """.formatted(payableJson, receivableJson, refMatch);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .executeUpdate();
    }

    private void insertPayableMissingReceivable(String sessionId) {
        String nRefP = normExpr("p.trans_ref");
        String nRefR = normExpr("r.trans_ref");
        String refMatch = "(" + nRefP + " = " + nRefR + " OR p.trans_ref = r.trans_ref)";
        String nRefRes = normExpr("rr.transaction_ref");
        String nStanRes = normExpr("rr.stan");
        String nStanP = normExpr("p.stan_no");
        String payableJson = "JSON_OBJECT(" +
                "'transRef', p.trans_ref, 'txnAmount', p.txn_amount, 'credit', p.credit, 'debit', p.debit, " +
                "'bookingDate', p.booking_date, 'description', p.description)";
        String settledSql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), p.session_id, p.stan_no, p.trans_ref, p.txn_amount,
                   p.booking_date, p.terminal_id,
                   'settled', NULL,
                   'Payable matched (receivable missing)',
                   CONCAT('Payable: ', pf.filename),
                   NULL, NULL, %s, NULL
            FROM transactions p
            LEFT JOIN transactions r
              ON p.session_id = ? AND r.session_id = ?
             AND p.source = 'Payable' AND r.source = 'Receivable'
             AND p.status = 'unsettled' AND r.status = 'unsettled'
             AND p.trans_ref IS NOT NULL AND p.trans_ref <> ''
             AND %s
            LEFT JOIN uploaded_files pf ON pf.id = p.file_id
            WHERE p.session_id = ? AND p.source = 'Payable'
              AND p.status = 'unsettled'
              AND p.trans_ref IS NOT NULL AND p.trans_ref <> ''
              AND r.id IS NULL
              AND EXISTS (
                SELECT 1 FROM reconciliation_results rr
                WHERE rr.session_id = ?
                  AND rr.status = 'settled'
                  AND rr.atm_data IS NOT NULL
                  AND rr.switch_data IS NOT NULL
                  AND (
                    (rr.transaction_ref IS NOT NULL AND rr.transaction_ref <> '' AND (%3$s = %4$s OR %3$s = %6$s OR rr.transaction_ref = p.trans_ref))
                    OR
                    (rr.stan IS NOT NULL AND rr.stan <> '' AND (%5$s = %6$s OR %5$s = %4$s OR rr.stan = p.stan_no))
                  )
              )
            """.formatted(payableJson, refMatch, nRefRes, nRefP, nStanRes, nStanP);
        entityManager.createNativeQuery(settledSql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .setParameter(3, sessionId)
            .setParameter(4, sessionId)
            .executeUpdate();

        String discrepantSql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), p.session_id, p.stan_no, p.trans_ref, p.txn_amount,
                   p.booking_date, p.terminal_id,
                   'discrepant', 'missing_in_receivable',
                   'missing_in_receivable',
                   CONCAT('Payable: ', pf.filename),
                   NULL, NULL, %s, NULL
            FROM transactions p
            LEFT JOIN transactions r
              ON p.session_id = ? AND r.session_id = ?
             AND p.source = 'Payable' AND r.source = 'Receivable'
             AND p.status = 'unsettled' AND r.status = 'unsettled'
             AND p.trans_ref IS NOT NULL AND p.trans_ref <> ''
             AND %s
            LEFT JOIN uploaded_files pf ON pf.id = p.file_id
            WHERE p.session_id = ? AND p.source = 'Payable'
              AND p.status = 'unsettled'
              AND p.trans_ref IS NOT NULL AND p.trans_ref <> ''
              AND r.id IS NULL
              AND NOT EXISTS (
                SELECT 1 FROM reconciliation_results rr
                WHERE rr.session_id = ?
                  AND rr.status = 'settled'
                  AND rr.atm_data IS NOT NULL
                  AND rr.switch_data IS NOT NULL
                  AND (
                    (rr.transaction_ref IS NOT NULL AND rr.transaction_ref <> '' AND (%3$s = %4$s OR %3$s = %6$s OR rr.transaction_ref = p.trans_ref))
                    OR
                    (rr.stan IS NOT NULL AND rr.stan <> '' AND (%5$s = %6$s OR %5$s = %4$s OR rr.stan = p.stan_no))
                  )
              )
            """.formatted(payableJson, refMatch, nRefRes, nRefP, nStanRes, nStanP);
        entityManager.createNativeQuery(discrepantSql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .setParameter(3, sessionId)
            .setParameter(4, sessionId)
            .executeUpdate();
    }

    private void insertReceivableMissingPayable(String sessionId) {
        String nRefR = normExpr("r.trans_ref");
        String nRefP = normExpr("p.trans_ref");
        String refMatch = "(" + nRefR + " = " + nRefP + " OR p.trans_ref = r.trans_ref)";
        String nRefRes = normExpr("rr.transaction_ref");
        String nStanRes = normExpr("rr.stan");
        String nStanR = normExpr("r.stan_no");
        String receivableJson = "JSON_OBJECT(" +
                "'transRef', r.trans_ref, 'txnAmount', r.txn_amount, 'credit', r.credit, 'debit', r.debit, " +
                "'bookingDate', r.booking_date, 'description', r.description)";

        String settledSql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), r.session_id, r.stan_no, r.trans_ref, r.txn_amount,
                   r.booking_date, r.terminal_id,
                   'settled', NULL,
                   'Receivable matched (payable missing)',
                   CONCAT('Receivable: ', rf.filename),
                   NULL, NULL, NULL, %s
            FROM transactions r
            LEFT JOIN transactions p
              ON r.session_id = ? AND p.session_id = ?
             AND r.source = 'Receivable' AND p.source = 'Payable'
             AND r.status = 'unsettled' AND p.status = 'unsettled'
             AND r.trans_ref IS NOT NULL AND r.trans_ref <> ''
             AND %s
            LEFT JOIN uploaded_files rf ON rf.id = r.file_id
            WHERE r.session_id = ? AND r.source = 'Receivable'
              AND r.status = 'unsettled'
              AND r.trans_ref IS NOT NULL AND r.trans_ref <> ''
              AND p.id IS NULL
              AND EXISTS (
                SELECT 1 FROM reconciliation_results rr
                WHERE rr.session_id = ?
                  AND rr.status = 'settled'
                  AND rr.atm_data IS NOT NULL
                  AND rr.switch_data IS NOT NULL
                  AND (
                    (rr.transaction_ref IS NOT NULL AND rr.transaction_ref <> '' AND (%3$s = %4$s OR %3$s = %6$s OR rr.transaction_ref = r.trans_ref))
                    OR
                    (rr.stan IS NOT NULL AND rr.stan <> '' AND (%5$s = %6$s OR %5$s = %4$s OR rr.stan = r.stan_no))
                  )
              )
            """.formatted(receivableJson, refMatch, nRefRes, nRefR, nStanRes, nStanR);
        entityManager.createNativeQuery(settledSql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .setParameter(3, sessionId)
            .setParameter(4, sessionId)
            .executeUpdate();

        String discrepantSql = """
            INSERT INTO reconciliation_results
              (id, session_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, source_files, atm_data, switch_data, payable_data, receivable_data)
            SELECT UUID(), r.session_id, r.stan_no, r.trans_ref, r.txn_amount,
                   r.booking_date, r.terminal_id,
                   'discrepant', 'missing_in_payable',
                   'missing_in_payable',
                   CONCAT('Receivable: ', rf.filename),
                   NULL, NULL, NULL, %s
            FROM transactions r
            LEFT JOIN transactions p
              ON r.session_id = ? AND p.session_id = ?
             AND r.source = 'Receivable' AND p.source = 'Payable'
             AND r.status = 'unsettled' AND p.status = 'unsettled'
             AND r.trans_ref IS NOT NULL AND r.trans_ref <> ''
             AND %s
            LEFT JOIN uploaded_files rf ON rf.id = r.file_id
            WHERE r.session_id = ? AND r.source = 'Receivable'
              AND r.status = 'unsettled'
              AND r.trans_ref IS NOT NULL AND r.trans_ref <> ''
              AND p.id IS NULL
              AND NOT EXISTS (
                SELECT 1 FROM reconciliation_results rr
                WHERE rr.session_id = ?
                  AND rr.status = 'settled'
                  AND rr.atm_data IS NOT NULL
                  AND rr.switch_data IS NOT NULL
                  AND (
                    (rr.transaction_ref IS NOT NULL AND rr.transaction_ref <> '' AND (%3$s = %4$s OR %3$s = %6$s OR rr.transaction_ref = r.trans_ref))
                    OR
                    (rr.stan IS NOT NULL AND rr.stan <> '' AND (%5$s = %6$s OR %5$s = %4$s OR rr.stan = r.stan_no))
                  )
              )
            """.formatted(receivableJson, refMatch, nRefRes, nRefR, nStanRes, nStanR);
        entityManager.createNativeQuery(discrepantSql)
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .setParameter(3, sessionId)
            .setParameter(4, sessionId)
            .executeUpdate();
    }

    private void insertDisputesForSession(String sessionId) {
        String nRefR = normExpr("r.transaction_ref");
        String nRefS = normExpr("rr.transaction_ref");
        String nStanR = normExpr("r.stan");
        String nStanS = normExpr("rr.stan");
        String sql = """
            INSERT INTO disputes
              (id, transaction_id, stan, transaction_ref, amount, transaction_date, terminal_id,
               dispute_status, dispute_reason, original_status, discrepancy_type, details,
               switch_data, atm_data, payable_data, receivable_data)
            SELECT UUID(), r.id, r.stan, r.transaction_ref, r.amount, r.transaction_date, r.terminal_id,
                   'open',
                   CASE r.discrepancy_type
                     WHEN 'amount_or_date_mismatch' THEN 'Amount or date mismatch between ATM and Switch'
                     WHEN 'missing_in_switch' THEN 'ATM transaction with no matching Switch entry'
                     WHEN 'amount_mismatch' THEN 'Amount mismatch between Payable and Receivable'
                     WHEN 'missing_in_receivable' THEN 'Payable transaction with no matching Receivable'
                     WHEN 'missing_in_payable' THEN 'Receivable transaction with no matching Payable'
                     ELSE r.details
                   END,
                   r.status, r.discrepancy_type, r.details,
                   r.switch_data, r.atm_data, r.payable_data, r.receivable_data
            FROM reconciliation_results r
            WHERE r.session_id = ? AND r.status = 'discrepant'
              AND (
                r.discrepancy_type NOT IN ('missing_in_receivable', 'missing_in_payable')
                OR NOT EXISTS (
                    SELECT 1
                    FROM reconciliation_results rr
                    WHERE rr.session_id = r.session_id
                      AND rr.status = 'settled'
                      AND rr.atm_data IS NOT NULL
                      AND rr.switch_data IS NOT NULL
                      AND (
                        (r.transaction_ref IS NOT NULL AND r.transaction_ref <> ''
                          AND rr.transaction_ref IS NOT NULL AND rr.transaction_ref <> ''
                          AND (%1$s = %2$s OR %1$s = %4$s OR rr.transaction_ref = r.transaction_ref))
                        OR
                        (r.stan IS NOT NULL AND r.stan <> ''
                          AND rr.stan IS NOT NULL AND rr.stan <> ''
                          AND (%3$s = %4$s OR %3$s = %2$s OR rr.stan = r.stan))
                      )
                )
              )
            """.formatted(nRefS, nRefR, nStanS, nStanR);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .executeUpdate();
    }

    private void insertUnresolvedFromResults(String sessionId) {
        String nRefR = normExpr("r.transaction_ref");
        String nRefS = normExpr("rr.transaction_ref");
        String nStanR = normExpr("r.stan");
        String nStanS = normExpr("rr.stan");
        String sql = """
            INSERT INTO unresolved_transactions
              (id, stan, transaction_ref, amount, transaction_date, terminal_id,
               status, discrepancy_type, details, original_session_id, created_at, source_files)
            SELECT UUID(), r.stan, r.transaction_ref, r.amount, r.transaction_date, r.terminal_id,
                   CASE WHEN r.status = 'discrepant' THEN 'DISCREPANT' ELSE 'MISSING' END,
                   r.discrepancy_type, r.details, r.session_id, NOW(), r.source_files
            FROM reconciliation_results r
            WHERE r.session_id = ? AND r.status IN ('discrepant','missing')
              AND (
                r.discrepancy_type NOT IN ('missing_in_receivable', 'missing_in_payable')
                OR NOT EXISTS (
                    SELECT 1
                    FROM reconciliation_results rr
                    WHERE rr.session_id = r.session_id
                      AND rr.status = 'settled'
                      AND rr.atm_data IS NOT NULL
                      AND rr.switch_data IS NOT NULL
                      AND (
                        (r.transaction_ref IS NOT NULL AND r.transaction_ref <> ''
                          AND rr.transaction_ref IS NOT NULL AND rr.transaction_ref <> ''
                          AND (%s = %s OR rr.transaction_ref = r.transaction_ref))
                        OR
                        (r.stan IS NOT NULL AND r.stan <> ''
                          AND rr.stan IS NOT NULL AND rr.stan <> ''
                          AND (%s = %s OR rr.stan = r.stan))
                      )
                )
              )
            """.formatted(nRefS, nRefR, nStanS, nStanR);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .executeUpdate();
    }

    private void removeEffectivelySettledDisputes(String sessionId) {
        String nRefD = normExpr("d.transaction_ref");
        String nRefS = normExpr("rr.transaction_ref");
        String nStanD = normExpr("d.stan");
        String nStanS = normExpr("rr.stan");
        String sql = """
            DELETE d
            FROM disputes d
            JOIN reconciliation_results r ON r.id = d.transaction_id
            WHERE r.session_id = ?
              AND r.discrepancy_type IN ('missing_in_receivable', 'missing_in_payable')
              AND EXISTS (
                  SELECT 1
                  FROM reconciliation_results rr
                  WHERE rr.session_id = r.session_id
                    AND rr.status = 'settled'
                    AND rr.atm_data IS NOT NULL
                    AND rr.switch_data IS NOT NULL
                    AND (
                      (d.transaction_ref IS NOT NULL AND d.transaction_ref <> ''
                        AND rr.transaction_ref IS NOT NULL AND rr.transaction_ref <> ''
                        AND (%1$s = %2$s OR %1$s = %4$s OR rr.transaction_ref = d.transaction_ref))
                      OR
                      (d.stan IS NOT NULL AND d.stan <> ''
                        AND rr.stan IS NOT NULL AND rr.stan <> ''
                        AND (%3$s = %4$s OR %3$s = %2$s OR rr.stan = d.stan))
                    )
              )
            """.formatted(nRefS, nRefD, nStanS, nStanD);
        entityManager.createNativeQuery(sql)
            .setParameter(1, sessionId)
            .executeUpdate();
    }

    private void cleanupSessionData(String sessionId) {
        // Remove disputes tied to this session's prior results
        entityManager.createNativeQuery(
                "DELETE d FROM disputes d " +
                "JOIN reconciliation_results r ON r.id = d.transaction_id " +
                "WHERE r.session_id = ?")
            .setParameter(1, sessionId)
            .executeUpdate();

        // Remove cross-session matches referencing this session
        entityManager.createNativeQuery(
                "DELETE FROM cross_session_matches WHERE original_session_id = ? OR matched_session_id = ?")
            .setParameter(1, sessionId)
            .setParameter(2, sessionId)
            .executeUpdate();

        // Remove unresolved rows for this session
        entityManager.createNativeQuery(
                "DELETE FROM unresolved_transactions WHERE original_session_id = ?")
            .setParameter(1, sessionId)
            .executeUpdate();

        // Remove prior reconciliation results for this session
        entityManager.createNativeQuery(
                "DELETE FROM reconciliation_results WHERE session_id = ?")
            .setParameter(1, sessionId)
            .executeUpdate();
    }

    private List<UnresolvedTransaction> createUnresolvedTransactions(List<ReconciliationResult> results, String sessionId) {
        List<UnresolvedTransaction> unresolvedList = new ArrayList<>();

        for (ReconciliationResult result : results) {
            if ("discrepant".equals(result.getStatus()) || "missing".equals(result.getStatus())) {
                UnresolvedTransaction unresolved = new UnresolvedTransaction();
                unresolved.setStan(result.getStan());
                unresolved.setTransactionRef(result.getTransactionRef());
                unresolved.setAmount(result.getAmount());
                unresolved.setTransactionDate(result.getTransactionDate() != null ?
                        java.time.LocalDateTime.parse(result.getTransactionDate()) : null);
                unresolved.setTerminalId(result.getTerminalId());
                unresolved.setStatus("discrepant".equals(result.getStatus()) ?
                        UnresolvedTransaction.TransactionStatus.DISCREPANT :
                        UnresolvedTransaction.TransactionStatus.MISSING);
                unresolved.setDiscrepancyType(result.getDiscrepancyType());
                unresolved.setDetails(result.getDetails());
                unresolved.setOriginalSessionId(sessionId);
                unresolved.setSourceFiles(result.getSourceFiles());

                unresolvedList.add(unresolved);
            }
        }

        return unresolvedList;
    }
}
