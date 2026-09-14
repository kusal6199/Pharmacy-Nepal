package com.nepalpharmacy.credit;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.UUID;

public interface CustomerAccountEntryRepository {
    boolean hasOpeningBalance(TransactionContext transaction, UUID customerId);

    void insert(TransactionContext transaction, AccountEntry entry);
}
