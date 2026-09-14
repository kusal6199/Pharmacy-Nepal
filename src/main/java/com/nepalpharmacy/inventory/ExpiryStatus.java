package com.nepalpharmacy.inventory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public enum ExpiryStatus {
    EXPIRED("Expired"),
    DAYS_0_TO_30("0-30 days"),
    DAYS_31_TO_60("31-60 days"),
    DAYS_61_TO_90("61-90 days"),
    LATER("Later");

    private final String displayName;

    ExpiryStatus(String displayName) {
        this.displayName = displayName;
    }

    public static ExpiryStatus classify(LocalDate asOfDate, LocalDate expiryDate) {
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(expiryDate, "expiryDate");
        long days = ChronoUnit.DAYS.between(asOfDate, expiryDate);
        if (days < 0) {
            return EXPIRED;
        }
        if (days <= 30) {
            return DAYS_0_TO_30;
        }
        if (days <= 60) {
            return DAYS_31_TO_60;
        }
        if (days <= 90) {
            return DAYS_61_TO_90;
        }
        return LATER;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
