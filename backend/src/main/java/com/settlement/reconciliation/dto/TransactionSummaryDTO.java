package com.settlement.reconciliation.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionSummaryDTO {
    private String sessionId;
    private Totals summary = new Totals();

    @Data
    public static class Totals {
        private int totalTransactions;
        private BigDecimal onUsATMCommission = BigDecimal.ZERO;
        private BigDecimal remoteOnUsATMCommission = BigDecimal.ZERO;
        private BigDecimal balanceInquiryFee = BigDecimal.ZERO;
        private BigDecimal remoteOnUsPOSAmount = BigDecimal.ZERO;
        private BigDecimal outwardP2PAmount = BigDecimal.ZERO;
        private BigDecimal p2pCommission = BigDecimal.ZERO;
        private BigDecimal onUsATMFee = BigDecimal.ZERO;
        private BigDecimal remoteOnUsATMFee = BigDecimal.ZERO;
        private BigDecimal atmCashWithdrawal = BigDecimal.ZERO;
        private BigDecimal incomingP2P = BigDecimal.ZERO;
        private BigDecimal atmWithdrawalFee = BigDecimal.ZERO;
        private BigDecimal creditBalanceInquiryFee = BigDecimal.ZERO;
    }
}
