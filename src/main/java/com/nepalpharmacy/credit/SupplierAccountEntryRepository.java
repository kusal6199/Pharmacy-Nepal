package com.nepalpharmacy.credit;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.UUID;

public interface SupplierAccountEntryRepository {
    boolean hasOpeningBalance(TransactionContext transaction, UUID supplierId);

    void insert(TransactionContext transaction, AccountEntry entry);
}
