package com.nepalpharmacy.sales;

public enum PaymentMethod {
    CASH("Cash"),
    QR("QR / digital"),
    CREDIT("Credit / Udharo");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
