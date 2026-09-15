package com.nepalpharmacy.credit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountBalancePresentationTest {
    @Test
    void framesCustomerBalancesWithoutBareSigns() {
        assertEquals("Customer owes NPR 12.34", AccountBalancePresentation.customer(1234));
        assertEquals("Settled", AccountBalancePresentation.customer(0));
        assertEquals("Customer credit NPR 5.00", AccountBalancePresentation.customer(-500));
        assertEquals("Customer credit NPR 92233720368547758.08",
                AccountBalancePresentation.customer(Long.MIN_VALUE));
    }

    @Test
    void framesSupplierBalancesWithoutBareSigns() {
        assertEquals("Payable to supplier NPR 12.34", AccountBalancePresentation.supplier(1234));
        assertEquals("Settled", AccountBalancePresentation.supplier(0));
        assertEquals("Supplier credit NPR 5.00", AccountBalancePresentation.supplier(-500));
    }

    @Test
    void presentsContextualCustomerSettlementHints() {
        assertEquals("Outstanding customer balance: NPR 12.34",
                AccountBalancePresentation.customerSettlementHint(1234));
        assertEquals("Available customer credit: NPR 5.00",
                AccountBalancePresentation.customerSettlementHint(-500));
        assertEquals("No customer balance to settle.",
                AccountBalancePresentation.customerSettlementHint(0));
        assertEquals("Available customer credit: NPR 92233720368547758.08",
                AccountBalancePresentation.customerSettlementHint(Long.MIN_VALUE));
    }

    @Test
    void presentsContextualSupplierSettlementHints() {
        assertEquals("Outstanding supplier payable: NPR 12.34",
                AccountBalancePresentation.supplierSettlementHint(1234));
        assertEquals("Available supplier credit: NPR 5.00",
                AccountBalancePresentation.supplierSettlementHint(-500));
        assertEquals("No supplier balance to settle.",
                AccountBalancePresentation.supplierSettlementHint(0));
    }

    @Test
    void accountEntryTypesExposeReadableCashierLabels() {
        assertEquals("Opening balance", AccountEntryType.OPENING_BALANCE.toString());
        assertEquals("Payment received", AccountEntryType.PAYMENT_RECEIVED.toString());
        assertEquals("Payment made", AccountEntryType.PAYMENT_MADE.toString());
        assertEquals("Pay out customer credit", AccountEntryType.CREDIT_PAYOUT.toString());
        assertEquals("Receive supplier credit refund",
                AccountEntryType.CREDIT_REFUND_RECEIVED.toString());
        assertFalse(AccountEntryType.OPENING_BALANCE.requiresPaymentMethod());
        assertTrue(AccountEntryType.CREDIT_PAYOUT.requiresPaymentMethod());
        assertTrue(AccountEntryType.CREDIT_REFUND_RECEIVED.requiresPaymentMethod());
    }
}
