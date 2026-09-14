package com.nepalpharmacy.purchasing;

import java.util.List;

/** Settlement method used by supplier purchases and supplier returns. */
public enum PurchasePaymentMethod {
    CASH("Cash"),
    QR("QR / digital"),
    CREDIT("Credit / udharo"),
    LEGACY_UNSPECIFIED("Legacy / unspecified");

    private final String displayName;

    PurchasePaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static List<PurchasePaymentMethod> selectableValues() {
        return List.of(CASH, QR, CREDIT);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
