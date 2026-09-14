package com.nepalpharmacy.credit;

import com.nepalpharmacy.party.SupplierRepository;
import com.nepalpharmacy.shared.persistence.TransactionRunner;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SupplierAccountService {
    public static final int LIST_LIMIT = 150;

    private final SupplierAccountRepository accounts;
    private final SupplierAccountEntryRepository entries;
    private final SupplierRepository suppliers;
    private final TransactionRunner transactions;
    private final Clock clock;

    public SupplierAccountService(
            SupplierAccountRepository accounts,
            SupplierAccountEntryRepository entries,
            SupplierRepository suppliers,
            TransactionRunner transactions) {
        this(accounts, entries, suppliers, transactions, Clock.systemUTC());
    }

    public SupplierAccountService(
            SupplierAccountRepository accounts,
            SupplierAccountEntryRepository entries,
            SupplierRepository suppliers,
            TransactionRunner transactions,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.suppliers = Objects.requireNonNull(suppliers, "suppliers");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BoundedAccountResult<SupplierAccountSummary> search(
            String query, AccountBalanceFilter filter) {
        return accounts.search(normalize(query), Objects.requireNonNull(filter), LIST_LIMIT);
    }

    public Optional<SupplierAccountDetail> findDetail(UUID supplierId) {
        return accounts.findDetail(supplierId);
    }

    public AccountEntry record(AccountEntryDraft input) {
        AccountEntryDraft draft = input == null ? null : input.normalized();
        return transactions.inTransaction(transaction -> {
            if (draft == null || draft.partyId() == null
                    || suppliers.findById(transaction, draft.partyId()).isEmpty()) {
                throw error("party", "Supplier account no longer exists.");
            }
            long balance = accounts.currentBalance(transaction, draft.partyId());
            boolean hasOpening = entries.hasOpeningBalance(transaction, draft.partyId());
            AccountEntryValidator.validateSupplier(draft, balance, hasOpening);
            AccountEntry saved = toEntry(draft);
            entries.insert(transaction, saved);
            return saved;
        });
    }

    private AccountEntry toEntry(AccountEntryDraft draft) {
        return new AccountEntry(UUID.randomUUID(), draft.partyId(), draft.entryDate(),
                draft.entryType(), draft.amountPaisa(), draft.paymentMethod(),
                draft.referenceText(), draft.notes(), clock.instant(), draft.createdBy());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AccountValidationException error(String field, String message) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new AccountValidationException(errors);
    }
}
