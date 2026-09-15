package com.nepalpharmacy.credit;

import com.nepalpharmacy.party.CustomerRepository;
import com.nepalpharmacy.party.SupplierRepository;
import com.nepalpharmacy.shared.persistence.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountEntryOptionsTest {
    private final CustomerAccountService customers = new CustomerAccountService(
            unused(CustomerAccountRepository.class),
            unused(CustomerAccountEntryRepository.class),
            unused(CustomerRepository.class),
            unused(TransactionRunner.class));
    private final SupplierAccountService suppliers = new SupplierAccountService(
            unused(SupplierAccountRepository.class),
            unused(SupplierAccountEntryRepository.class),
            unused(SupplierRepository.class),
            unused(TransactionRunner.class));

    @Test
    void customerEntryTypesFollowTheBalanceSign() {
        assertEquals(List.of(AccountEntryType.OPENING_BALANCE,
                        AccountEntryType.PAYMENT_RECEIVED),
                customers.allowedEntryTypes(1));
        assertEquals(List.of(AccountEntryType.OPENING_BALANCE,
                        AccountEntryType.CREDIT_PAYOUT),
                customers.allowedEntryTypes(-1));
        assertEquals(List.of(AccountEntryType.OPENING_BALANCE),
                customers.allowedEntryTypes(0));
    }

    @Test
    void supplierEntryTypesFollowTheBalanceSign() {
        assertEquals(List.of(AccountEntryType.OPENING_BALANCE,
                        AccountEntryType.PAYMENT_MADE),
                suppliers.allowedEntryTypes(1));
        assertEquals(List.of(AccountEntryType.OPENING_BALANCE,
                        AccountEntryType.CREDIT_REFUND_RECEIVED),
                suppliers.allowedEntryTypes(-1));
        assertEquals(List.of(AccountEntryType.OPENING_BALANCE),
                suppliers.allowedEntryTypes(0));
    }

    @SuppressWarnings("unchecked")
    private static <T> T unused(Class<T> contract) {
        return (T) Proxy.newProxyInstance(
                contract.getClassLoader(),
                new Class<?>[] {contract},
                (proxy, method, arguments) -> {
                    throw new AssertionError("Unexpected dependency call: " + method.getName());
                });
    }
}
