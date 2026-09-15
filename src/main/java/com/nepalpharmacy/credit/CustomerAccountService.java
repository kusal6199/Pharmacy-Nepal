package com.nepalpharmacy.credit;

import com.nepalpharmacy.party.CustomerRepository;
import com.nepalpharmacy.shared.persistence.TransactionRunner;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CustomerAccountService {
    public static final int LIST_LIMIT = 150;

    private final CustomerAccountRepository accounts;
    private final CustomerAccountEntryRepository entries;
    private final CustomerRepository customers;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CustomerAccountService(
            CustomerAccountRepository accounts,
            CustomerAccountEntryRepository entries,
            CustomerRepository customers,
            TransactionRunner transactions) {
        this(accounts, entries, customers, transactions, Clock.systemUTC());
    }

    public CustomerAccountService(
            CustomerAccountRepository accounts,
            CustomerAccountEntryRepository entries,
            CustomerRepository customers,
            TransactionRunner transactions,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.customers = Objects.requireNonNull(customers, "customers");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BoundedAccountResult<CustomerAccountSummary> search(
            String query, AccountBalanceFilter filter) {
        return accounts.search(normalize(query), Objects.requireNonNull(filter), LIST_LIMIT);
    }

    public Optional<CustomerAccountDetail> findDetail(UUID customerId) {
        return accounts.findDetail(customerId);
    }

    public List<AccountEntryType> allowedEntryTypes(long currentBalancePaisa) {
        if (currentBalancePaisa > 0) {
            return List.of(AccountEntryType.OPENING_BALANCE, AccountEntryType.PAYMENT_RECEIVED);
        }
        if (currentBalancePaisa < 0) {
            return List.of(AccountEntryType.OPENING_BALANCE, AccountEntryType.CREDIT_PAYOUT);
        }
        return List.of(AccountEntryType.OPENING_BALANCE);
    }

    public AccountEntry record(AccountEntryDraft input) {
        AccountEntryDraft draft = input == null ? null : input.normalized();
        return transactions.inTransaction(transaction -> {
            if (draft == null || draft.partyId() == null
                    || customers.findById(transaction, draft.partyId()).isEmpty()) {
                throw error("party", "Customer account no longer exists.");
            }
            long balance = accounts.currentBalance(transaction, draft.partyId());
            boolean hasOpening = entries.hasOpeningBalance(transaction, draft.partyId());
            AccountEntryValidator.validateCustomer(draft, balance, hasOpening);
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
