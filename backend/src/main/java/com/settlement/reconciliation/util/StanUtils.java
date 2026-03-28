package com.settlement.reconciliation.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StanUtils {

    private static final Pattern STAN_PATTERN = Pattern.compile("\\b(\\d{4,6})\\b");

    private StanUtils() {
    }

    public static String extractStan(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;

        Matcher matcher = STAN_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
