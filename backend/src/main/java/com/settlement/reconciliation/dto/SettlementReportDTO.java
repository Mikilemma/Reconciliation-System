package com.settlement.reconciliation.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SettlementReportDTO {
    private String reportTitle;
    private String settlementDate;
    private String formattedDate;
    private DebitSide debitSide = new DebitSide();
    private CreditSide creditSide = new CreditSide();
    private BigDecimal totalDebitAmount = BigDecimal.ZERO;
    private BigDecimal totalCreditAmount = BigDecimal.ZERO;
    private BigDecimal netSettlementAmount = BigDecimal.ZERO;

    @Data
    public static class DebitSide {
        private BigDecimal onUsATMCommission = BigDecimal.ZERO;
        private BigDecimal remoteOnUsATMCommission = BigDecimal.ZERO;
        private BigDecimal balanceInquiryFee = BigDecimal.ZERO;
        private BigDecimal remoteOnUsPOSAmount = BigDecimal.ZERO;
        private BigDecimal outwardP2PAmount = BigDecimal.ZERO;
        private BigDecimal p2PCommission = BigDecimal.ZERO;
        private BigDecimal onUsATMFee = BigDecimal.ZERO;
        private BigDecimal remoteOnUsATMFee = BigDecimal.ZERO;
        private BigDecimal paymentSettlementNet = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;
    }

    @Data
    public static class CreditSide {
        private BigDecimal atmCashWithdrawal = BigDecimal.ZERO;
        private BigDecimal incomingP2P = BigDecimal.ZERO;
        private BigDecimal atmWithdrawalFee = BigDecimal.ZERO;
        private BigDecimal balanceInquiryFee = BigDecimal.ZERO;
        private BigDecimal paymentSettlementCommissionNet = BigDecimal.ZERO;
        private BigDecimal ethswitchReceivableTotal = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;
    }
}
