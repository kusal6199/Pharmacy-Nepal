package com.nepalpharmacy.credit;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.Optional;
import java.util.UUID;

public interface SupplierAccountRepository {
    BoundedAccountResult<SupplierAccountSummary> search(
            String nameQuery, AccountBalanceFilter filter, int limit);

    Optional<SupplierAccountDetail> findDetail(UUID supplierId);

    long currentBalance(TransactionContext transaction, UUID supplierId);
}
