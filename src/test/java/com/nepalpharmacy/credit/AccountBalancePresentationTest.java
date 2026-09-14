package com.nepalpharmacy.credit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
