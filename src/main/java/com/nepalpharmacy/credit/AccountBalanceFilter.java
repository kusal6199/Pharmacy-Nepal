package com.nepalpharmacy.credit;

public enum AccountBalanceFilter {
    ALL("All accounts"),
    POSITIVE("Owes / payable"),
    CREDIT("Party credit"),
    SETTLED("Settled");

    private final String displayName;

    AccountBalanceFilter(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
