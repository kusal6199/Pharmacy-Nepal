package com.nepalpharmacy.credit;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.Optional;
import java.util.UUID;

public interface CustomerAccountRepository {
    BoundedAccountResult<CustomerAccountSummary> search(
            String nameQuery, AccountBalanceFilter filter, int limit);

    Optional<CustomerAccountDetail> findDetail(UUID customerId);

    long currentBalance(TransactionContext transaction, UUID customerId);
}
