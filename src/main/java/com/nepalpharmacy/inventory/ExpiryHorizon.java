package com.nepalpharmacy.inventory;

public enum ExpiryHorizon {
    ALL("All alerts"),
    EXPIRED("Expired"),
    DAYS_0_TO_30("0-30 days"),
    DAYS_31_TO_60("31-60 days"),
    DAYS_61_TO_90("61-90 days");

    private final String displayName;

    ExpiryHorizon(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
