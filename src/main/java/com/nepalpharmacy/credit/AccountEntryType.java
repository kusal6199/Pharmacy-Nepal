package com.nepalpharmacy.credit;

public enum AccountEntryType {
    OPENING_BALANCE("Opening balance"),
    PAYMENT_RECEIVED("Payment received"),
    PAYMENT_MADE("Payment made"),
    CREDIT_PAYOUT("Pay out customer credit"),
    CREDIT_REFUND_RECEIVED("Receive supplier credit refund");

    private final String displayName;

    AccountEntryType(String displayName) {
        this.displayName = displayName;
    }

    public boolean requiresPaymentMethod() {
        return this != OPENING_BALANCE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
