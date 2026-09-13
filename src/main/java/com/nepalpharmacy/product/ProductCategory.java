package com.nepalpharmacy.product;

public enum ProductCategory {
    TABLET("Tablet"),
    SYRUP("Syrup"),
    INJECTION("Injection"),
    OINTMENT("Ointment"),
    CAPSULE("Capsule"),
    OTHER("Other");

    private final String displayName;

    ProductCategory(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

