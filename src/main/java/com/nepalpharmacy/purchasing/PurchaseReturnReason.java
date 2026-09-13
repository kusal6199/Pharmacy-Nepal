package com.nepalpharmacy.purchasing;

public enum PurchaseReturnReason {
    DAMAGED("Damaged"),
    WRONG_ITEM("Wrong item"),
    EXPIRED_ON_RECEIPT("Expired on receipt"),
    SUPPLIER_RECALL("Supplier recall"),
    OTHER("Other");

    private final String displayName;

    PurchaseReturnReason(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
