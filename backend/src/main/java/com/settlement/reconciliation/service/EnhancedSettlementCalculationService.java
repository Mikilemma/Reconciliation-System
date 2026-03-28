package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.*;
import com.settlement.reconciliation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced Settlement Calculation Service based on Node.js server logic
 * Implements comprehensive settlement rules and calculations
 */
@Service
public class EnhancedSettlementCalculationService {

    // Settlement Rules Constants (from Node.js settlementProcessor.ts)
    private static final BigDecimal ATM_WITHDRAWAL_COMMISSION_RATE = new BigDecimal("0.005"); // 0.5%
    private static final BigDecimal BALANCE_INQUIRY_FEE_DEBIT = new BigDecimal("0.5"); // 0.5 ETB per inquiry
    private static final BigDecimal BALANCE_INQUIRY_FEE_CREDIT = new BigDecimal("0.2"); // 0.2 ETB per inquiry
    private static final BigDecimal ON_US_ATM_FEE_RATE = new BigDecimal("0.0005"); // 0.05%
    private static final BigDecimal REMOTE_ON_US_ATM_FEE_RATE = new BigDecimal("0.005"); // 0.5%
    private static final BigDecimal ATM_WITHDRAWAL_FEE_RATE = new BigDecimal("0.0045"); // 0.45%
    private static final BigDecimal DISPUTE_CHARGEBACK_COMMISSION = new BigDecimal("0.25");

    // Tsehay Bank identifiers
    private static final List<String> TSEHAY_BANK_CODES = Arrays.asList(
        "ET0010003", "ET0010004", "ET0010010", "ET0010012", "ET0010015", "ET0010020",
        "ET0010021", "ET0010028", "ET0010030", "ET0010036", "ET0010039", "ET0010046",
        "ET0010047", "ET0010048", "ET0010053", "ET0010062", "ET0010064", "ET0010067",
        "ET0010068", "ET0010069", "ET0010076", "ET0010079", "ET0010089", "ET0010091",
        "ET0010093", "ET0013019", "ET0013078", "ET0013082", "ET0013083"
    );

    private final TransactionRepository transactionRepository;

    public EnhancedSettlementCalculationService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Calculate comprehensive settlement based on Node.js logic
     */
    @Transactional
    public SettlementCalculationResult calculateSettlement(String sessionId) {
        // Get all switch transactions (primary source)
        List<Transaction> switchTransactions = transactionRepository.findBySessionIdAndSource(sessionId, "Switch");
        System.out.println("DEBUG: calculateSettlement sessionId: " + sessionId + ", switchTransactions size: " + switchTransactions.size());
        
        if (!switchTransactions.isEmpty()) {
            System.out.println("DEBUG: Scanning first 10 transactions for Tsehay matches:");
            for (int i = 0; i < Math.min(10, switchTransactions.size()); i++) {
                Transaction t = switchTransactions.get(i);
                System.out.println("  Txn[" + i + "]: Issuer=[" + t.getIssuer() + "], Acquirer=[" + t.getAcquirer() + "], Desc=[" + t.getDescription() + "], Amount=" + t.getTxnAmount());
                System.out.println("    IsTsehayIssuer=" + isTsehayIdentifier(t.getIssuer()) + ", IsTsehayAcquirer=" + isTsehayIdentifier(t.getAcquirer()));
            }
        }
        
        // Calculate debit side
        DebitSideCalculation debitSide = calculateDebitSide(switchTransactions);
        
        // Calculate credit side
        CreditSideCalculation creditSide = calculateCreditSide(switchTransactions);

        System.out.println("DEBUG: calc results - Debit Total=" + debitSide.getTotal() + ", Credit Total=" + creditSide.getTotal());

        // Build result
        SettlementCalculationResult result = new SettlementCalculationResult();
        result.setSessionId(sessionId);
        result.setCalculatedAt(LocalDateTime.now());
        result.setDebitSide(debitSide);
        result.setCreditSide(creditSide);
        result.setNetPosition(creditSide.getTotal().subtract(debitSide.getTotal()));
        result.setTotalsTrans(calculateTotalsTrans(debitSide, creditSide));
        result.setTotalsFees(calculateTotalsFees(debitSide, creditSide));

        return result;
    }

    private DebitSideCalculation calculateDebitSide(List<Transaction> switchTransactions) {
        DebitSideCalculation debit = new DebitSideCalculation();

        // Filter Tsehay as Issuer transactions
        List<Transaction> tsehayIssuerTxns = switchTransactions.stream()
            .filter(txn -> isTsehayIdentifier(txn.getIssuer()))
            .collect(Collectors.toList());

        // 1. OnUs ATM Cash Withdrawal & Commission
        List<Transaction> onUsAtmTxns = tsehayIssuerTxns.stream()
            .filter(txn -> isTsehayIdentifier(txn.getAcquirer()) && 
                    matchesDescription(txn.getDescription(), "atm cw transaction amount"))
            .collect(Collectors.toList());
        
        BigDecimal onUsAtmAmount = sumAmounts(onUsAtmTxns);
        BigDecimal onUsAtmCommission = onUsAtmAmount.multiply(ATM_WITHDRAWAL_COMMISSION_RATE);
        debit.setOnUsAtmWithdrawal(new AtmWithdrawalDetail(onUsAtmAmount, onUsAtmCommission));

        // NEW 1.1 On-Us Balance Inquiry Commission to EthSwitch
        long onUsBICount = tsehayIssuerTxns.stream()
            .filter(txn -> isTsehayIdentifier(txn.getAcquirer()) && 
                    matchesAnyDescription(txn, "Balance enquiry", "Balance inquiry"))
            .count();
        BigDecimal onUsBICommission = BigDecimal.valueOf(onUsBICount).multiply(new BigDecimal("0.30"));
        debit.setOnUsBalanceInquiryCommissionToEthSwitch(onUsBICommission);
        System.out.println("DEBUG: On-Us Balance Inquiry Commission to EthSwitch: count=" + onUsBICount + ", commission=" + onUsBICommission);

        // 2. Remote OnUs ATM Cash Withdrawal & Balance Inquiry Commission
        List<Transaction> remoteOnUsAtmTxns = tsehayIssuerTxns.stream()
            .filter(txn -> !isTsehayIdentifier(txn.getAcquirer()) && 
                    matchesDescription(txn.getDescription(), "atm cw transaction amount"))
            .collect(Collectors.toList());
        
        BigDecimal remoteOnUsAtmAmount = sumAmounts(remoteOnUsAtmTxns);
        BigDecimal remoteOnUsAtmCommission = remoteOnUsAtmAmount.multiply(ATM_WITHDRAWAL_COMMISSION_RATE);
        
        long balanceInquiryCount = tsehayIssuerTxns.stream()
            .filter(txn -> !isTsehayIdentifier(txn.getAcquirer()) && 
                    matchesDescription(txn.getDescription(), "balance enquiry", "balance inquiry"))
            .count();
        BigDecimal balanceInquiryFees = BALANCE_INQUIRY_FEE_DEBIT.multiply(BigDecimal.valueOf(balanceInquiryCount));
        
        debit.setRemoteOnUsAtmWithdrawal(new RemoteAtmWithdrawalDetail(
            remoteOnUsAtmAmount, remoteOnUsAtmCommission, balanceInquiryFees));

        // NEW 1.2 Remote OnUs EPOS Purchase + Commission
        List<Transaction> remoteEposTxns = tsehayIssuerTxns.stream()
            .filter(txn -> !isTsehayIdentifier(txn.getAcquirer()) && 
                    matchesAnyDescription(txn, "POS PUR THEM-ON-THEM", "POS PUR THEM", "POS PUR"))
            .collect(Collectors.toList());

        // Fallback: if no matches found, use broader heuristics on description to capture variations
        if (remoteEposTxns.isEmpty()) {
            remoteEposTxns = tsehayIssuerTxns.stream()
                .filter(txn -> !isTsehayIdentifier(txn.getAcquirer()))
                .filter(txn -> {
                    String desc = (txn.getDescription() != null ? txn.getDescription() : txn.getTransactionDescription());
                    if (desc == null) return false;
                    String norm = desc.toLowerCase();
                    boolean hasPos = norm.contains("pos");
                    boolean hasPur = norm.contains("pur") || norm.contains("purchase");
                    return hasPos && hasPur;
                })
                .collect(Collectors.toList());
        }

        BigDecimal eposAmount = sumAmounts(remoteEposTxns);
        BigDecimal eposCommission = eposAmount.multiply(new BigDecimal("0.001"));
        debit.setRemoteOnUsEposPurchasePlusCommission(eposAmount.add(eposCommission));
        System.out.println("DEBUG: Remote OnUs EPOS Purchase + Commission: amount=" + eposAmount + ", commission=" + eposCommission + ", total=" + debit.getRemoteOnUsEposPurchasePlusCommission());

        // 3. Remote OnUs EPOS Purchase
        BigDecimal remoteOnUsPosAmount = tsehayIssuerTxns.stream()
            .filter(txn -> !isTsehayIdentifier(txn.getAcquirer()) && 
                    matchesAnyDescription(txn, "POS PUR THEM-ON-THEM", "POS PUR THEM", "POS PUR"))
            .map(txn -> parseAmount(txn.getTxnAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        debit.setRemoteOnUsEposPurchase(remoteOnUsPosAmount);

        // 4. Outward P2P Amount
        BigDecimal outwardP2pAmount = tsehayIssuerTxns.stream()
            .filter(txn -> isTsehayIdentifier(txn.getAcquirer()) && 
                    matchesDescription(txn.getDescription(), "account2account debit"))
            .map(txn -> parseAmount(txn.getTxnAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        debit.setOutwardP2pAmount(outwardP2pAmount);

        // 5. P2P Commission to Ethswitch (60%) - placeholder for now
        debit.setP2pCommissionToEthswitch(BigDecimal.ZERO);

        // 6. Fees to Ethswitch
        BigDecimal onUsAtmFeeToEthswitch = onUsAtmAmount.multiply(ON_US_ATM_FEE_RATE);
        BigDecimal remoteOnUsAtmFeeToEthswitch = remoteOnUsAtmAmount.multiply(REMOTE_ON_US_ATM_FEE_RATE);
        debit.setOnUsAtmFeeToEthswitch(onUsAtmFeeToEthswitch);
        debit.setRemoteOnUsAtmFeeToEthswitch(remoteOnUsAtmFeeToEthswitch);

        // 7. Payment & Settlement Net (from payable/receivable - placeholder)
        debit.setPaymentSettlementNet(BigDecimal.ZERO);

        // Calculate total (include new fields)
        BigDecimal remoteOnUsEposPurchasePlusCommission = eposAmount.add(eposCommission);

        BigDecimal total = onUsAtmAmount.add(onUsAtmCommission)
            .add(remoteOnUsAtmAmount).add(remoteOnUsAtmCommission).add(balanceInquiryFees)
            .add(remoteOnUsEposPurchasePlusCommission)
            .add(outwardP2pAmount).add(onUsBICommission)
            .add(onUsAtmFeeToEthswitch).add(remoteOnUsAtmFeeToEthswitch);
        debit.setTotal(total);

        return debit;
    }

    private CreditSideCalculation calculateCreditSide(List<Transaction> switchTransactions) {
        CreditSideCalculation credit = new CreditSideCalculation();

        // Filter Tsehay as Acquirer transactions
        List<Transaction> tsehayAcquirerTxns = switchTransactions.stream()
            .filter(txn -> isTsehayIdentifier(txn.getAcquirer()))
            .collect(Collectors.toList());

        // NEW 2.1 Remote On-Us Dispute Chargeback Amount & Commission
        List<Transaction> remoteDisputeTxns = tsehayAcquirerTxns.stream()
            .filter(txn -> !isTsehayIdentifier(txn.getIssuer()) &&
                matchesAnyDescription(txn, "Dispute Chargeback Amount", "Dispute Chargeback", "Chargeback"))
            .collect(Collectors.toList());

        // Fallback: broaden matching if no disputes were captured due to formatting differences
        if (remoteDisputeTxns.isEmpty()) {
            remoteDisputeTxns = tsehayAcquirerTxns.stream()
                .filter(txn -> !isTsehayIdentifier(txn.getIssuer()))
                .filter(txn -> {
                    String desc = (txn.getTransactionDescription() != null ? txn.getTransactionDescription() : txn.getDescription());
                    if (desc == null) return false;
                    String norm = desc.toLowerCase();
                    return norm.contains("dispute") && norm.contains("chargeback");
                })
                .collect(Collectors.toList());
        }

        BigDecimal remoteDisputeAmount = sumAmounts(remoteDisputeTxns);
        BigDecimal remoteDisputeCommission = BigDecimal.valueOf(remoteDisputeTxns.size()).multiply(DISPUTE_CHARGEBACK_COMMISSION);
        credit.setRemoteOnUsDisputeChargebackAmountCommission(remoteDisputeAmount.add(remoteDisputeCommission));

        // NEW 2.2 On-Us Dispute Chargeback Commission
        long onUsDisputeCount = tsehayAcquirerTxns.stream()
            .filter(txn -> isTsehayIdentifier(txn.getIssuer()) &&
                matchesAnyDescription(txn, "Dispute Chargeback Amount", "Dispute Chargeback", "Chargeback"))
            .count();
        BigDecimal onUsDisputeCommission = BigDecimal.valueOf(onUsDisputeCount).multiply(DISPUTE_CHARGEBACK_COMMISSION);
        credit.setOnUsDisputeChargebackCommission(onUsDisputeCommission);

        // 1. ATM Cash Withdrawal (Other banks as Issuer, Tsehay as Acquirer)
        BigDecimal atmWithdrawalAmount = tsehayAcquirerTxns.stream()
            .filter(txn -> !isTsehayIdentifier(txn.getIssuer()) &&
                    matchesDescription(txn.getDescription(), "atm cw transaction amount"))
            .map(txn -> parseAmount(txn.getTxnAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        credit.setAtmCashWithdrawal(atmWithdrawalAmount);

        // 2. Incoming P2P
        BigDecimal incomingP2pAmount = tsehayAcquirerTxns.stream()
            .filter(txn -> !isTsehayIdentifier(txn.getIssuer()) &&
                    matchesDescription(txn.getDescription(), "account2account credit"))
            .map(txn -> parseAmount(txn.getTxnAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        credit.setIncomingP2p(incomingP2pAmount);

        // 3. ATM Withdrawal Fee
        BigDecimal atmWithdrawalFee = atmWithdrawalAmount.multiply(ATM_WITHDRAWAL_FEE_RATE);
        credit.setAtmWithdrawalFee(atmWithdrawalFee);

        // 4. ATM Balance Inquiry Fee
        long balanceInquiryCount = tsehayAcquirerTxns.stream()
            .filter(txn -> !isTsehayIdentifier(txn.getIssuer()) &&
                    matchesDescription(txn.getDescription(), "balance enquiry", "balance inquiry"))
            .count();
        BigDecimal atmBalanceInquiryFee = BALANCE_INQUIRY_FEE_CREDIT.multiply(BigDecimal.valueOf(balanceInquiryCount));
        credit.setAtmBalanceInquiryFee(atmBalanceInquiryFee);

        // 5. Payment & Settlement Commission Net (placeholder)
        credit.setPaymentSettlementCommission(BigDecimal.ZERO);

        // Calculate total (include new fields)
        BigDecimal total = atmWithdrawalAmount.add(incomingP2pAmount)
            .add(credit.getRemoteOnUsDisputeChargebackAmountCommission())
            .add(credit.getOnUsDisputeChargebackCommission())
            .add(atmWithdrawalFee).add(atmBalanceInquiryFee);
        credit.setTotal(total);

        return credit;
    }

    // Helper methods
    private boolean isTsehayIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        String normalized = identifier.toLowerCase().trim();
        if (normalized.equals("tsehay bank")) return true;
        if (TSEHAY_BANK_CODES.contains(identifier.toUpperCase())) return true;
        return normalized.contains("tsehay");
    }

    private boolean matchesDescription(String description, String... patterns) {
        if (description == null) return false;
        // Normalize whitespace to avoid mismatches due to double spaces or irregular spacing
        String normalized = description.toLowerCase().trim().replaceAll("\\s+", " ");
        for (String pattern : patterns) {
            String normalizedPattern = pattern.toLowerCase().trim().replaceAll("\\s+", " ");
            if (normalized.contains(normalizedPattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyDescription(Transaction txn, String... patterns) {
        return matchesDescription(txn.getTransactionDescription(), patterns) ||
               matchesDescription(txn.getDescription(), patterns);
    }

    private BigDecimal sumAmounts(List<Transaction> transactions) {
        return transactions.stream()
            .map(txn -> parseAmount(txn.getTxnAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal parseAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private Totals calculateTotalsTrans(DebitSideCalculation debit, CreditSideCalculation credit) {
        Totals totals = new Totals();
        totals.setDebitValue(debit.getTotal());
        totals.setCreditValue(credit.getTotal());
        totals.setNetValue(credit.getTotal().subtract(debit.getTotal()));
        return totals;
    }

    private Totals calculateTotalsFees(DebitSideCalculation debit, CreditSideCalculation credit) {
        Totals totals = new Totals();
        BigDecimal debitFees = debit.getOnUsAtmFeeToEthswitch()
            .add(debit.getRemoteOnUsAtmFeeToEthswitch())
            .add(debit.getOnUsAtmWithdrawal().getCommission())
            .add(debit.getRemoteOnUsAtmWithdrawal().getCommission())
            .add(debit.getRemoteOnUsAtmWithdrawal().getBalanceInquiryFee());
        BigDecimal creditFees = credit.getAtmWithdrawalFee().add(credit.getAtmBalanceInquiryFee());
        totals.setDebitValue(debitFees);
        totals.setCreditValue(creditFees);
        totals.setNetValue(creditFees.subtract(debitFees));
        return totals;
    }

    // Inner classes for structured results
    public static class SettlementCalculationResult {
        private String sessionId;
        private LocalDateTime calculatedAt;
        private DebitSideCalculation debitSide;
        private CreditSideCalculation creditSide;
        private BigDecimal netPosition;
        private Totals totalsTrans;
        private Totals totalsFees;

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public LocalDateTime getCalculatedAt() { return calculatedAt; }
        public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
        public DebitSideCalculation getDebitSide() { return debitSide; }
        public void setDebitSide(DebitSideCalculation debitSide) { this.debitSide = debitSide; }
        public CreditSideCalculation getCreditSide() { return creditSide; }
        public void setCreditSide(CreditSideCalculation creditSide) { this.creditSide = creditSide; }
        public BigDecimal getNetPosition() { return netPosition; }
        public void setNetPosition(BigDecimal netPosition) { this.netPosition = netPosition; }
        public Totals getTotalsTrans() { return totalsTrans; }
        public void setTotalsTrans(Totals totalsTrans) { this.totalsTrans = totalsTrans; }
        public Totals getTotalsFees() { return totalsFees; }
        public void setTotalsFees(Totals totalsFees) { this.totalsFees = totalsFees; }
    }

    public static class DebitSideCalculation {
        private AtmWithdrawalDetail onUsAtmWithdrawal = new AtmWithdrawalDetail();
        private RemoteAtmWithdrawalDetail remoteOnUsAtmWithdrawal = new RemoteAtmWithdrawalDetail();
        private BigDecimal remoteOnUsEposPurchase = BigDecimal.ZERO;
        private BigDecimal onUsBalanceInquiryCommissionToEthSwitch = BigDecimal.ZERO;
        private BigDecimal remoteOnUsEposPurchasePlusCommission = BigDecimal.ZERO;
        private BigDecimal outwardP2pAmount = BigDecimal.ZERO;
        private BigDecimal p2pCommissionToEthswitch = BigDecimal.ZERO;
        private BigDecimal onUsAtmFeeToEthswitch = BigDecimal.ZERO;
        private BigDecimal remoteOnUsAtmFeeToEthswitch = BigDecimal.ZERO;
        private BigDecimal paymentSettlementNet = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;

        // Getters and setters
        public AtmWithdrawalDetail getOnUsAtmWithdrawal() { return onUsAtmWithdrawal; }
        public void setOnUsAtmWithdrawal(AtmWithdrawalDetail onUsAtmWithdrawal) { this.onUsAtmWithdrawal = onUsAtmWithdrawal; }
        public RemoteAtmWithdrawalDetail getRemoteOnUsAtmWithdrawal() { return remoteOnUsAtmWithdrawal; }
        public void setRemoteOnUsAtmWithdrawal(RemoteAtmWithdrawalDetail remoteOnUsAtmWithdrawal) { this.remoteOnUsAtmWithdrawal = remoteOnUsAtmWithdrawal; }
        public BigDecimal getRemoteOnUsEposPurchase() { return remoteOnUsEposPurchase; }
        public void setRemoteOnUsEposPurchase(BigDecimal remoteOnUsEposPurchase) { this.remoteOnUsEposPurchase = remoteOnUsEposPurchase; }
        public BigDecimal getOnUsBalanceInquiryCommissionToEthSwitch() { return onUsBalanceInquiryCommissionToEthSwitch; }
        public void setOnUsBalanceInquiryCommissionToEthSwitch(BigDecimal onUsBalanceInquiryCommissionToEthSwitch) { this.onUsBalanceInquiryCommissionToEthSwitch = onUsBalanceInquiryCommissionToEthSwitch; }
        public BigDecimal getRemoteOnUsEposPurchasePlusCommission() { return remoteOnUsEposPurchasePlusCommission; }
        public void setRemoteOnUsEposPurchasePlusCommission(BigDecimal remoteOnUsEposPurchasePlusCommission) { this.remoteOnUsEposPurchasePlusCommission = remoteOnUsEposPurchasePlusCommission; }
        public BigDecimal getOutwardP2pAmount() { return outwardP2pAmount; }
        public void setOutwardP2pAmount(BigDecimal outwardP2pAmount) { this.outwardP2pAmount = outwardP2pAmount; }
        public BigDecimal getP2pCommissionToEthswitch() { return p2pCommissionToEthswitch; }
        public void setP2pCommissionToEthswitch(BigDecimal p2pCommissionToEthswitch) { this.p2pCommissionToEthswitch = p2pCommissionToEthswitch; }
        public BigDecimal getOnUsAtmFeeToEthswitch() { return onUsAtmFeeToEthswitch; }
        public void setOnUsAtmFeeToEthswitch(BigDecimal onUsAtmFeeToEthswitch) { this.onUsAtmFeeToEthswitch = onUsAtmFeeToEthswitch; }
        public BigDecimal getRemoteOnUsAtmFeeToEthswitch() { return remoteOnUsAtmFeeToEthswitch; }
        public void setRemoteOnUsAtmFeeToEthswitch(BigDecimal remoteOnUsAtmFeeToEthswitch) { this.remoteOnUsAtmFeeToEthswitch = remoteOnUsAtmFeeToEthswitch; }
        public BigDecimal getPaymentSettlementNet() { return paymentSettlementNet; }
        public void setPaymentSettlementNet(BigDecimal paymentSettlementNet) { this.paymentSettlementNet = paymentSettlementNet; }
        public BigDecimal getTotal() { return total; }
        public void setTotal(BigDecimal total) { this.total = total; }
    }

    public static class CreditSideCalculation {
        private BigDecimal atmCashWithdrawal = BigDecimal.ZERO;
        private BigDecimal incomingP2p = BigDecimal.ZERO;
        private BigDecimal remoteOnUsDisputeChargebackAmountCommission = BigDecimal.ZERO;
        private BigDecimal onUsDisputeChargebackCommission = BigDecimal.ZERO;
        private BigDecimal atmWithdrawalFee = BigDecimal.ZERO;
        private BigDecimal atmBalanceInquiryFee = BigDecimal.ZERO;
        private BigDecimal paymentSettlementCommission = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;

        // Getters and setters
        public BigDecimal getAtmCashWithdrawal() { return atmCashWithdrawal; }
        public void setAtmCashWithdrawal(BigDecimal atmCashWithdrawal) { this.atmCashWithdrawal = atmCashWithdrawal; }
        public BigDecimal getIncomingP2p() { return incomingP2p; }
        public void setIncomingP2p(BigDecimal incomingP2p) { this.incomingP2p = incomingP2p; }
        public BigDecimal getRemoteOnUsDisputeChargebackAmountCommission() { return remoteOnUsDisputeChargebackAmountCommission; }
        public void setRemoteOnUsDisputeChargebackAmountCommission(BigDecimal remoteOnUsDisputeChargebackAmountCommission) { this.remoteOnUsDisputeChargebackAmountCommission = remoteOnUsDisputeChargebackAmountCommission; }
        public BigDecimal getOnUsDisputeChargebackCommission() { return onUsDisputeChargebackCommission; }
        public void setOnUsDisputeChargebackCommission(BigDecimal onUsDisputeChargebackCommission) { this.onUsDisputeChargebackCommission = onUsDisputeChargebackCommission; }
        public BigDecimal getAtmWithdrawalFee() { return atmWithdrawalFee; }
        public void setAtmWithdrawalFee(BigDecimal atmWithdrawalFee) { this.atmWithdrawalFee = atmWithdrawalFee; }
        public BigDecimal getAtmBalanceInquiryFee() { return atmBalanceInquiryFee; }
        public void setAtmBalanceInquiryFee(BigDecimal atmBalanceInquiryFee) { this.atmBalanceInquiryFee = atmBalanceInquiryFee; }
        public BigDecimal getPaymentSettlementCommission() { return paymentSettlementCommission; }
        public void setPaymentSettlementCommission(BigDecimal paymentSettlementCommission) { this.paymentSettlementCommission = paymentSettlementCommission; }
        public BigDecimal getTotal() { return total; }
        public void setTotal(BigDecimal total) { this.total = total; }
    }

    public static class AtmWithdrawalDetail {
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;

        public AtmWithdrawalDetail() {}
        public AtmWithdrawalDetail(BigDecimal amount, BigDecimal commission) {
            this.amount = amount;
            this.commission = commission;
        }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public BigDecimal getCommission() { return commission; }
        public void setCommission(BigDecimal commission) { this.commission = commission; }
    }

    public static class RemoteAtmWithdrawalDetail {
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;
        private BigDecimal balanceInquiryFee = BigDecimal.ZERO;

        public RemoteAtmWithdrawalDetail() {}
        public RemoteAtmWithdrawalDetail(BigDecimal amount, BigDecimal commission, BigDecimal balanceInquiryFee) {
            this.amount = amount;
            this.commission = commission;
            this.balanceInquiryFee = balanceInquiryFee;
        }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public BigDecimal getCommission() { return commission; }
        public void setCommission(BigDecimal commission) { this.commission = commission; }
        public BigDecimal getBalanceInquiryFee() { return balanceInquiryFee; }
        public void setBalanceInquiryFee(BigDecimal balanceInquiryFee) { this.balanceInquiryFee = balanceInquiryFee; }
    }

    public static class Totals {
        private BigDecimal debitValue = BigDecimal.ZERO;
        private BigDecimal creditValue = BigDecimal.ZERO;
        private BigDecimal netValue = BigDecimal.ZERO;

        public BigDecimal getDebitValue() { return debitValue; }
        public void setDebitValue(BigDecimal debitValue) { this.debitValue = debitValue; }
        public BigDecimal getCreditValue() { return creditValue; }
        public void setCreditValue(BigDecimal creditValue) { this.creditValue = creditValue; }
        public BigDecimal getNetValue() { return netValue; }
        public void setNetValue(BigDecimal netValue) { this.netValue = netValue; }
    }
}
