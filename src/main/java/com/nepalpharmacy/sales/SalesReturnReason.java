package com.nepalpharmacy.sales;

public enum SalesReturnReason {
    WRONG_MEDICINE("Wrong medicine"),
    CUSTOMER_RETURN("Customer return"),
    DAMAGED("Damaged"),
    BILLING_ERROR("Billing error"),
    OTHER("Other");

    private final String displayName;

    SalesReturnReason(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
