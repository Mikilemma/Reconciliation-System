package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.*;
import com.settlement.reconciliation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SettlementCalculationService {

    private final TransactionAtmRepository transactionAtmRepository;
    private final TransactionPayableRepository transactionPayableRepository;
    private final TransactionReceivableRepository transactionReceivableRepository;
    private final TransactionSwitchRepository transactionSwitchRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final DisputeRepository disputeRepository;

    public SettlementCalculationService(
            TransactionAtmRepository transactionAtmRepository,
            TransactionPayableRepository transactionPayableRepository,
            TransactionReceivableRepository transactionReceivableRepository,
            TransactionSwitchRepository transactionSwitchRepository,
            ReconciliationResultRepository reconciliationResultRepository,
            DisputeRepository disputeRepository) {
        this.transactionAtmRepository = transactionAtmRepository;
        this.transactionPayableRepository = transactionPayableRepository;
        this.transactionReceivableRepository = transactionReceivableRepository;
        this.transactionSwitchRepository = transactionSwitchRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.disputeRepository = disputeRepository;
    }

    @Transactional
    public Map<String, Object> calculateSettlement(String sessionId) {
        Map<String, Object> settlementResult = new HashMap<>();
        
        // Fetch all transactions for the session
        List<TransactionAtm> atmTransactions = transactionAtmRepository.findBySessionId(sessionId);
        List<TransactionPayable> payableTransactions = transactionPayableRepository.findBySessionId(sessionId);
        List<TransactionReceivable> receivableTransactions = transactionReceivableRepository.findBySessionId(sessionId);
        List<TransactionSwitch> switchTransactions = transactionSwitchRepository.findBySessionId(sessionId);

        // Calculate settlement amounts
        SettlementSummary summary = calculateSettlementSummary(
            atmTransactions, payableTransactions, receivableTransactions, switchTransactions);

        // Perform reconciliation
        ReconciliationResults reconciliation = performReconciliation(
            atmTransactions, payableTransactions, receivableTransactions, switchTransactions, sessionId);

        // Build result
        settlementResult.put("sessionId", sessionId);
        settlementResult.put("calculatedAt", LocalDateTime.now());
        settlementResult.put("summary", summary);
        settlementResult.put("reconciliation", reconciliation);
        settlementResult.put("netSettlementAmount", summary.getNetSettlementAmount());
        settlementResult.put("totalTransactions", summary.getTotalTransactions());
        settlementResult.put("settledTransactions", reconciliation.getSettledCount());
        settlementResult.put("discrepantTransactions", reconciliation.getDiscrepantCount());
        settlementResult.put("matchRate", calculateMatchRate(summary.getTotalTransactions(), reconciliation.getSettledCount()));

        return settlementResult;
    }

    private SettlementSummary calculateSettlementSummary(
            List<TransactionAtm> atmTransactions,
            List<TransactionPayable> payableTransactions,
            List<TransactionReceivable> receivableTransactions,
            List<TransactionSwitch> switchTransactions) {

        SettlementSummary summary = new SettlementSummary();

        // ATM Transaction Summary
        BigDecimal atmTotalAmount = atmTransactions.stream()
            .map(TransactionAtm::getTxnAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Payable Transaction Summary (Credits)
        BigDecimal payableTotalCredit = payableTransactions.stream()
            .map(TransactionPayable::getCredit)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal payableTotalDebit = payableTransactions.stream()
            .map(TransactionPayable::getDebit)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Receivable Transaction Summary (Debits)
        BigDecimal receivableTotalCredit = receivableTransactions.stream()
            .map(TransactionReceivable::getCredit)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal receivableTotalDebit = receivableTransactions.stream()
            .map(TransactionReceivable::getDebit)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Switch Transaction Summary
        BigDecimal switchTotalAmount = switchTransactions.stream()
            .map(TransactionSwitch::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate net settlement
        // Net Settlement = Payable Credits - Receivable Debits
        BigDecimal netSettlementAmount = payableTotalCredit.subtract(receivableTotalDebit);

        summary.setAtmTotalAmount(atmTotalAmount);
        summary.setAtmTransactionCount(atmTransactions.size());
        summary.setPayableTotalCredit(payableTotalCredit);
        summary.setPayableTotalDebit(payableTotalDebit);
        summary.setPayableTransactionCount(payableTransactions.size());
        summary.setReceivableTotalCredit(receivableTotalCredit);
        summary.setReceivableTotalDebit(receivableTotalDebit);
        summary.setReceivableTransactionCount(receivableTransactions.size());
        summary.setSwitchTotalAmount(switchTotalAmount);
        summary.setSwitchTransactionCount(switchTransactions.size());
        summary.setNetSettlementAmount(netSettlementAmount);
        summary.setTotalTransactions(atmTransactions.size() + payableTransactions.size() + 
                                    receivableTransactions.size() + switchTransactions.size());

        return summary;
    }

    private ReconciliationResults performReconciliation(
            List<TransactionAtm> atmTransactions,
            List<TransactionPayable> payableTransactions,
            List<TransactionReceivable> receivableTransactions,
            List<TransactionSwitch> switchTransactions,
            String sessionId) {

        ReconciliationResults results = new ReconciliationResults();
        List<ReconciliationResult> reconciliationResults = new ArrayList<>();
        List<Dispute> disputes = new ArrayList<>();

        int settledCount = 0;
        int discrepantCount = 0;

        // Reconcile ATM transactions with Payable/Receivable
        Map<String, TransactionPayable> payableByRef = payableTransactions.stream()
            .filter(t -> t.getReference() != null)
            .collect(Collectors.toMap(TransactionPayable::getReference, t -> t, (existing, replacement) -> existing));

        Map<String, TransactionReceivable> receivableByRef = receivableTransactions.stream()
            .filter(t -> t.getReference() != null)
            .collect(Collectors.toMap(TransactionReceivable::getReference, t -> t, (existing, replacement) -> existing));

        // Match ATM transactions with payable/receivable by reference
        for (TransactionAtm atmTx : atmTransactions) {
            String reference = atmTx.getTransRef();
            if (reference != null) {
                TransactionPayable payable = payableByRef.get(reference);
                TransactionReceivable receivable = receivableByRef.get(reference);

                if (payable != null || receivable != null) {
                    // Create reconciliation result
                    ReconciliationResult result = createReconciliationResult(atmTx, payable, receivable, sessionId);
                    
                    if (isAmountMatched(atmTx, payable, receivable)) {
                        result.setStatus("settled");
                        settledCount++;
                    } else {
                        result.setStatus("discrepant");
                        result.setDiscrepancyType("amount_mismatch");
                        discrepantCount++;
                        
                        // Create dispute
                        Dispute dispute = createDispute(result, "Amount mismatch between ATM and Statement transactions");
                        disputes.add(dispute);
                    }
                    
                    reconciliationResults.add(result);
                } else {
                    // Unmatched ATM transaction
                    ReconciliationResult result = createUnmatchedResult(atmTx, sessionId);
                    reconciliationResults.add(result);
                    discrepantCount++;
                    
                    Dispute dispute = createDispute(result, "ATM transaction with no matching statement entry");
                    disputes.add(dispute);
                }
            }
        }

        // Save results
        reconciliationResultRepository.saveAll(reconciliationResults);
        disputeRepository.saveAll(disputes);

        results.setSettledCount(settledCount);
        results.setDiscrepantCount(discrepantCount);
        results.setTotalProcessed(reconciliationResults.size());
        results.setResults(reconciliationResults);
        results.setDisputes(disputes);

        return results;
    }

    private ReconciliationResult createReconciliationResult(
            TransactionAtm atmTx, 
            TransactionPayable payable, 
            TransactionReceivable receivable, 
            String sessionId) {
        
        ReconciliationResult result = new ReconciliationResult();
        result.setId(UUID.randomUUID().toString());
        result.setSessionId(sessionId);
        result.setStan(atmTx.getStanNo());
        result.setTransactionRef(atmTx.getTransRef());
        result.setAmount(atmTx.getTxnAmount());
        result.setTransactionDate(atmTx.getValueDate() != null ? atmTx.getValueDate().toString() : null);
        result.setTerminalId(atmTx.getTerminalId());
        
        StringBuilder sourceFiles = new StringBuilder();
        sourceFiles.append("ATM:").append(atmTx.getFileId());
        if (payable != null) {
            sourceFiles.append(",Payable:").append(payable.getFileId());
        }
        if (receivable != null) {
            sourceFiles.append(",Receivable:").append(receivable.getFileId());
        }
        result.setSourceFiles(sourceFiles.toString());
        
        // Set transaction data (simplified JSON representation)
        result.setAtmData(convertToSimpleJson(atmTx));
        if (payable != null) {
            result.setPayableData(convertToSimpleJson(payable));
        }
        if (receivable != null) {
            result.setReceivableData(convertToSimpleJson(receivable));
        }
        
        return result;
    }

    private ReconciliationResult createUnmatchedResult(TransactionAtm atmTx, String sessionId) {
        ReconciliationResult result = new ReconciliationResult();
        result.setId(UUID.randomUUID().toString());
        result.setSessionId(sessionId);
        result.setStan(atmTx.getStanNo());
        result.setTransactionRef(atmTx.getTransRef());
        result.setAmount(atmTx.getTxnAmount());
        result.setTransactionDate(atmTx.getValueDate() != null ? atmTx.getValueDate().toString() : null);
        result.setTerminalId(atmTx.getTerminalId());
        result.setStatus("discrepant");
        result.setDiscrepancyType("unmatched");
        result.setSourceFiles("ATM:" + atmTx.getFileId());
        result.setAtmData(convertToSimpleJson(atmTx));
        result.setDetails("ATM transaction with no matching statement entry");
        
        return result;
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

    private boolean isAmountMatched(TransactionAtm atmTx, TransactionPayable payable, TransactionReceivable receivable) {
        BigDecimal atmAmount = atmTx.getTxnAmount();
        if (atmAmount == null) return false;

        if (payable != null && payable.getCredit() != null) {
            return atmAmount.compareTo(payable.getCredit()) == 0;
        }
        
        if (receivable != null && receivable.getDebit() != null) {
            return atmAmount.compareTo(receivable.getDebit()) == 0;
        }
        
        return false;
    }

    private BigDecimal calculateMatchRate(int totalTransactions, int settledCount) {
        if (totalTransactions == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf((double) settledCount / totalTransactions * 100)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String convertToSimpleJson(Object obj) {
        // Simple JSON representation - in production, use proper JSON library
        return obj.toString();
    }

    // Inner classes for structured results
    public static class SettlementSummary {
        private BigDecimal atmTotalAmount = BigDecimal.ZERO;
        private int atmTransactionCount = 0;
        private BigDecimal payableTotalCredit = BigDecimal.ZERO;
        private BigDecimal payableTotalDebit = BigDecimal.ZERO;
        private int payableTransactionCount = 0;
        private BigDecimal receivableTotalCredit = BigDecimal.ZERO;
        private BigDecimal receivableTotalDebit = BigDecimal.ZERO;
        private int receivableTransactionCount = 0;
        private BigDecimal switchTotalAmount = BigDecimal.ZERO;
        private int switchTransactionCount = 0;
        private BigDecimal netSettlementAmount = BigDecimal.ZERO;
        private int totalTransactions = 0;

        // Getters and setters
        public BigDecimal getAtmTotalAmount() { return atmTotalAmount; }
        public void setAtmTotalAmount(BigDecimal atmTotalAmount) { this.atmTotalAmount = atmTotalAmount; }
        public int getAtmTransactionCount() { return atmTransactionCount; }
        public void setAtmTransactionCount(int atmTransactionCount) { this.atmTransactionCount = atmTransactionCount; }
        public BigDecimal getPayableTotalCredit() { return payableTotalCredit; }
        public void setPayableTotalCredit(BigDecimal payableTotalCredit) { this.payableTotalCredit = payableTotalCredit; }
        public BigDecimal getPayableTotalDebit() { return payableTotalDebit; }
        public void setPayableTotalDebit(BigDecimal payableTotalDebit) { this.payableTotalDebit = payableTotalDebit; }
        public int getPayableTransactionCount() { return payableTransactionCount; }
        public void setPayableTransactionCount(int payableTransactionCount) { this.payableTransactionCount = payableTransactionCount; }
        public BigDecimal getReceivableTotalCredit() { return receivableTotalCredit; }
        public void setReceivableTotalCredit(BigDecimal receivableTotalCredit) { this.receivableTotalCredit = receivableTotalCredit; }
        public BigDecimal getReceivableTotalDebit() { return receivableTotalDebit; }
        public void setReceivableTotalDebit(BigDecimal receivableTotalDebit) { this.receivableTotalDebit = receivableTotalDebit; }
        public int getReceivableTransactionCount() { return receivableTransactionCount; }
        public void setReceivableTransactionCount(int receivableTransactionCount) { this.receivableTransactionCount = receivableTransactionCount; }
        public BigDecimal getSwitchTotalAmount() { return switchTotalAmount; }
        public void setSwitchTotalAmount(BigDecimal switchTotalAmount) { this.switchTotalAmount = switchTotalAmount; }
        public int getSwitchTransactionCount() { return switchTransactionCount; }
        public void setSwitchTransactionCount(int switchTransactionCount) { this.switchTransactionCount = switchTransactionCount; }
        public BigDecimal getNetSettlementAmount() { return netSettlementAmount; }
        public void setNetSettlementAmount(BigDecimal netSettlementAmount) { this.netSettlementAmount = netSettlementAmount; }
        public int getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }
    }

    public static class ReconciliationResults {
        private int settledCount = 0;
        private int discrepantCount = 0;
        private int totalProcessed = 0;
        private List<ReconciliationResult> results = new ArrayList<>();
        private List<Dispute> disputes = new ArrayList<>();

        // Getters and setters
        public int getSettledCount() { return settledCount; }
        public void setSettledCount(int settledCount) { this.settledCount = settledCount; }
        public int getDiscrepantCount() { return discrepantCount; }
        public void setDiscrepantCount(int discrepantCount) { this.discrepantCount = discrepantCount; }
        public int getTotalProcessed() { return totalProcessed; }
        public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }
        public List<ReconciliationResult> getResults() { return results; }
        public void setResults(List<ReconciliationResult> results) { this.results = results; }
        public List<Dispute> getDisputes() { return disputes; }
        public void setDisputes(List<Dispute> disputes) { this.disputes = disputes; }
    }
}