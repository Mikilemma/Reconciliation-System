package com.settlement.reconciliation.util;

import com.settlement.reconciliation.model.Transaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TransactionDedupKeyUtils {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TransactionDedupKeyUtils() {
    }

    public static String buildDedupeKey(Transaction tx) {
        String source = norm(tx.getSource());
        String stan = norm(StanUtils.extractStan(tx.getStanNo()));
        String ref = norm(tx.getTransRef());
        String amount = normAmount(tx.getTxnAmount());
        String valueDate = normDate(tx.getValueDate());
        String terminal = norm(tx.getTerminalId());
        String mti = norm(tx.getMtiCode());
        String proc = norm(tx.getProcCode());
        String pan = norm(tx.getPanNumber());
        String issuer = norm(tx.getIssuer());
        String acquirer = norm(tx.getAcquirer());

        String raw = String.join("|",
            source, stan, ref, amount, valueDate, terminal, mti, proc, pan, issuer, acquirer
        );

        return sha256(raw);
    }

    private static String norm(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normAmount(BigDecimal amount) {
        if (amount == null) return "";
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String normDate(LocalDateTime date) {
        if (date == null) return "";
        return date.withNano(0).format(DATE_FMT);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
