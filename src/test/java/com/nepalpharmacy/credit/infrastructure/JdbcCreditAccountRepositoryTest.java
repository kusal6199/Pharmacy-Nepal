package com.nepalpharmacy.credit.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.credit.AccountBalanceFilter;
import com.nepalpharmacy.credit.AccountBalancePresentation;
import com.nepalpharmacy.credit.AccountEntry;
import com.nepalpharmacy.credit.AccountEntryDraft;
import com.nepalpharmacy.credit.AccountEntryType;
import com.nepalpharmacy.credit.AccountValidationException;
import com.nepalpharmacy.credit.CustomerAccountDetail;
import com.nepalpharmacy.credit.CustomerAccountEntryRepository;
import com.nepalpharmacy.credit.CustomerAccountService;
import com.nepalpharmacy.credit.SupplierAccountDetail;
import com.nepalpharmacy.credit.SupplierAccountEntryRepository;
import com.nepalpharmacy.credit.SupplierAccountService;
import com.nepalpharmacy.party.CustomerDraft;
import com.nepalpharmacy.party.CustomerService;
import com.nepalpharmacy.party.SupplierDraft;
import com.nepalpharmacy.party.SupplierService;
import com.nepalpharmacy.party.infrastructure.JdbcCustomerRepository;
import com.nepalpharmacy.party.infrastructure.JdbcSupplierRepository;
import com.nepalpharmacy.sales.PaymentMethod;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionRunner;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.persistence.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCreditAccountRepositoryTest {
    private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 1);
    private static final Instant CREATED = Instant.parse("2026-09-14T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    private DatabaseBootstrap database;
    private JdbcTransactionRunner transactions;
    private JdbcCustomerRepository customerParties;
    private JdbcSupplierRepository supplierParties;
    private JdbcCustomerAccountRepository customerAccounts;
    private JdbcSupplierAccountRepository supplierAccounts;
    private JdbcCustomerAccountEntryRepository customerEntries;
    private JdbcSupplierAccountEntryRepository supplierEntries;
    private CustomerAccountService customerService;
    private SupplierAccountService supplierService;
    private UUID customerId;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        transactions = new JdbcTransactionRunner(database::openConnection);
        customerParties = new JdbcCustomerRepository(database::openConnection);
        supplierParties = new JdbcSupplierRepository(database::openConnection);
        customerAccounts = new JdbcCustomerAccountRepository(database::openConnection);
        supplierAccounts = new JdbcSupplierAccountRepository(database::openConnection);
        customerEntries = new JdbcCustomerAccountEntryRepository();
        supplierEntries = new JdbcSupplierAccountEntryRepository();
        Clock clock = Clock.fixed(CREATED, ZoneOffset.UTC);
        customerService = new CustomerAccountService(
                customerAccounts, customerEntries, customerParties, transactions, clock);
        supplierService = new SupplierAccountService(
                supplierAccounts, supplierEntries, supplierParties, transactions, clock);
        customerId = new CustomerService(customerParties).create(
                new CustomerDraft("Asha Pharmacy", "9800000000", null, true)).id();
        supplierId = new SupplierService(supplierParties).create(
                new SupplierDraft("Himal Supplier", "9811111111", null, null, true)).id();
    }

    @Test
    void customerLedgerCompletesExactRequiredLifecycle() throws Exception {
        customerService.record(entry(customerId, DAY_1, AccountEntryType.OPENING_BALANCE,
                1_000, null));
        insertCreditSale("sale-1", customerId, DAY_1.plusDays(1), 23, 500,
                "2026-09-02T01:00:00Z");
        customerService.record(entry(customerId, DAY_1.plusDays(2),
                AccountEntryType.PAYMENT_RECEIVED, 400, PaymentMethod.CASH));
        insertCreditSalesReturn("sales-return-1", "sale-1", DAY_1.plusDays(3),
                4, 200, "2026-09-04T01:00:00Z");
        customerService.record(entry(customerId, DAY_1.plusDays(4),
                AccountEntryType.PAYMENT_RECEIVED, 900, PaymentMethod.QR));

        CustomerAccountDetail detail = customerService.findDetail(customerId).orElseThrow();

        assertEquals(List.of(1_000L, 1_500L, 1_100L, 900L, 0L),
                detail.entries().stream().map(row -> row.runningBalancePaisa()).toList());
        assertEquals(List.of("Opening balance", "Credit sale", "Payment received",
                        "Sales return credit", "Payment received"),
                detail.entries().stream().map(row -> row.activity()).toList());
        assertEquals("Sale #23", detail.entries().get(1).reference());
        assertEquals("Sales return #4", detail.entries().get(3).reference());
        assertEquals(0, detail.summary().balancePaisa());
    }

    @Test
    void supplierLedgerCompletesExactRequiredLifecycle() throws Exception {
        supplierService.record(entry(supplierId, DAY_1, AccountEntryType.OPENING_BALANCE,
                2_000, null));
        insertCreditPurchase("purchase-1", supplierId, DAY_1.plusDays(1),
                "SUP-52", 5_000, "2026-09-02T01:00:00Z");
        supplierService.record(entry(supplierId, DAY_1.plusDays(2),
                AccountEntryType.PAYMENT_MADE, 3_000, PaymentMethod.CASH));
        insertCreditPurchaseReturn("purchase-return-1", "purchase-1", supplierId,
                DAY_1.plusDays(3), 7, 1_000, "2026-09-04T01:00:00Z");
        supplierService.record(entry(supplierId, DAY_1.plusDays(4),
                AccountEntryType.PAYMENT_MADE, 3_000, PaymentMethod.QR));

        SupplierAccountDetail detail = supplierService.findDetail(supplierId).orElseThrow();

        assertEquals(List.of(2_000L, 7_000L, 4_000L, 3_000L, 0L),
                detail.entries().stream().map(row -> row.runningBalancePaisa()).toList());
        assertEquals(List.of("Opening balance", "Credit purchase", "Payment made",
                        "Credit purchase return", "Payment made"),
                detail.entries().stream().map(row -> row.activity()).toList());
        assertEquals("Supplier invoice SUP-52", detail.entries().get(1).reference());
        assertEquals("Purchase return #7", detail.entries().get(3).reference());
        assertEquals(0, detail.summary().balancePaisa());
    }

    @Test
    void customerCreditPayoutPartiallyThenFullySettlesAndReloadsExactly() throws Exception {
        insertSale("customer-payout-source", customerId, "CASH", 100_000);
        insertSalesReturn("customer-payout-credit", "customer-payout-source",
                "CREDIT", 40_000, 81);

        CustomerAccountDetail credited = customerService.findDetail(customerId).orElseThrow();
        assertEquals(-40_000, credited.summary().balancePaisa());
        assertTrue(customerService.search(null, AccountBalanceFilter.CREDIT).accounts().stream()
                .anyMatch(row -> row.customerId().equals(customerId)
                        && row.balancePaisa() == -40_000));

        customerService.record(entry(customerId, DAY_1.plusDays(2),
                AccountEntryType.CREDIT_PAYOUT, 10_000, PaymentMethod.CASH,
                null));

        CustomerAccountDetail partial = customerService.findDetail(customerId).orElseThrow();
        assertEquals(-30_000, partial.summary().balancePaisa());
        assertEquals(List.of(-40_000L, -30_000L),
                partial.entries().stream().map(row -> row.runningBalancePaisa()).toList());
        assertTrue(customerService.search(null, AccountBalanceFilter.CREDIT).accounts().stream()
                .anyMatch(row -> row.customerId().equals(customerId)
                        && row.balancePaisa() == -30_000));

        customerService.record(entry(customerId, DAY_1.plusDays(3),
                AccountEntryType.CREDIT_PAYOUT, 30_000, PaymentMethod.QR,
                "PAYOUT-QR-300"));

        CustomerAccountDetail settled = customerService.findDetail(customerId).orElseThrow();
        CustomerAccountDetail reloaded = customerService.findDetail(customerId).orElseThrow();
        assertEquals(0, settled.summary().balancePaisa());
        assertEquals(List.of(-40_000L, -30_000L, 0L),
                settled.entries().stream().map(row -> row.runningBalancePaisa()).toList());
        assertEquals(List.of("Sales return credit", "Customer credit payout",
                        "Customer credit payout"),
                settled.entries().stream().map(row -> row.activity()).toList());
        assertEquals(List.of(0L, 10_000L, 30_000L),
                settled.entries().stream().map(row -> row.increasePaisa()).toList());
        assertEquals(List.of(40_000L, 0L, 0L),
                settled.entries().stream().map(row -> row.decreasePaisa()).toList());
        assertEquals(List.of("Sales return #81", "Cash payout", "PAYOUT-QR-300"),
                settled.entries().stream().map(row -> row.reference()).toList());
        assertEquals(settled.entries(), reloaded.entries());
        assertEquals(2, scalar("SELECT COUNT(*) FROM customer_account_entry "
                + "WHERE entry_type = 'CREDIT_PAYOUT'"));
        assertFalse(customerService.search(null, AccountBalanceFilter.CREDIT).accounts().stream()
                .anyMatch(row -> row.customerId().equals(customerId)));
        assertTrue(customerService.search(null, AccountBalanceFilter.SETTLED).accounts().stream()
                .anyMatch(row -> row.customerId().equals(customerId)
                        && row.balancePaisa() == 0));
    }

    @Test
    void supplierCreditRefundPartiallyThenFullySettlesAndReloadsExactly() throws Exception {
        insertPurchase("supplier-refund-source", supplierId, "CASH", 100_000);
        insertCreditPurchaseReturn("supplier-refund-credit", "supplier-refund-source",
                supplierId, DAY_1.plusDays(1), 82, 50_000, "2026-09-02T01:00:00Z");

        SupplierAccountDetail credited = supplierService.findDetail(supplierId).orElseThrow();
        assertEquals(-50_000, credited.summary().balancePaisa());
        assertEquals(0, scalar("SELECT COUNT(*) FROM inventory_movement"));
        assertTrue(supplierService.search(null, AccountBalanceFilter.CREDIT).accounts().stream()
                .anyMatch(row -> row.supplierId().equals(supplierId)
                        && row.balancePaisa() == -50_000));

        supplierService.record(entry(supplierId, DAY_1.plusDays(2),
                AccountEntryType.CREDIT_REFUND_RECEIVED, 20_000, PaymentMethod.CASH,
                null));

        SupplierAccountDetail partial = supplierService.findDetail(supplierId).orElseThrow();
        assertEquals(-30_000, partial.summary().balancePaisa());
        assertEquals(List.of(-50_000L, -30_000L),
                partial.entries().stream().map(row -> row.runningBalancePaisa()).toList());
        assertEquals(1, scalar("SELECT COUNT(*) FROM supplier_account_entry "
                + "WHERE entry_type = 'CREDIT_REFUND_RECEIVED'"));
        assertTrue(supplierService.search(null, AccountBalanceFilter.CREDIT).accounts().stream()
                .anyMatch(row -> row.supplierId().equals(supplierId)
                        && row.balancePaisa() == -30_000));

        supplierService.record(entry(supplierId, DAY_1.plusDays(3),
                AccountEntryType.CREDIT_REFUND_RECEIVED, 30_000, PaymentMethod.QR,
                "REFUND-QR-300"));

        SupplierAccountDetail settled = supplierService.findDetail(supplierId).orElseThrow();
        SupplierAccountDetail reloaded = supplierService.findDetail(supplierId).orElseThrow();
        assertEquals(0, settled.summary().balancePaisa());
        assertEquals(List.of(-50_000L, -30_000L, 0L),
                settled.entries().stream().map(row -> row.runningBalancePaisa()).toList());
        assertEquals(List.of("Credit purchase return", "Supplier credit refund received",
                        "Supplier credit refund received"),
                settled.entries().stream().map(row -> row.activity()).toList());
        assertEquals(List.of(0L, 20_000L, 30_000L),
                settled.entries().stream().map(row -> row.increasePaisa()).toList());
        assertEquals(List.of(50_000L, 0L, 0L),
                settled.entries().stream().map(row -> row.decreasePaisa()).toList());
        assertEquals(List.of("Purchase return #82", "Cash refund received", "REFUND-QR-300"),
                settled.entries().stream().map(row -> row.reference()).toList());
        assertEquals(settled.entries(), reloaded.entries());
        assertEquals(2, scalar("SELECT COUNT(*) FROM supplier_account_entry "
                + "WHERE entry_type = 'CREDIT_REFUND_RECEIVED'"));
        assertFalse(supplierService.search(null, AccountBalanceFilter.CREDIT).accounts().stream()
                .anyMatch(row -> row.supplierId().equals(supplierId)));
        assertTrue(supplierService.search(null, AccountBalanceFilter.SETTLED).accounts().stream()
                .anyMatch(row -> row.supplierId().equals(supplierId)
                        && row.balancePaisa() == 0));
        assertEquals(1, scalar("SELECT COUNT(*) FROM purchase "
                + "WHERE id = 'supplier-refund-source' AND payment_method = 'CASH' "
                + "AND total_amount_paisa = 100000"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM purchase_return "
                + "WHERE id = 'supplier-refund-credit' AND settlement_method = 'CREDIT' "
                + "AND total_amount_paisa = 50000"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM inventory_movement"));
    }

    @Test
    void supplierCashRefundSettlesExactCreditWithOneImmutableFinancialEntry() throws Exception {
        insertPurchase("supplier-full-refund-source", supplierId, "CASH", 100_000);
        insertCreditPurchaseReturn("supplier-full-refund-credit", "supplier-full-refund-source",
                supplierId, DAY_1.plusDays(1), 87, 50_000, "2026-09-02T01:00:00Z");
        assertEquals(-50_000,
                supplierService.findDetail(supplierId).orElseThrow().summary().balancePaisa());

        supplierService.record(entry(supplierId, DAY_1.plusDays(2),
                AccountEntryType.CREDIT_REFUND_RECEIVED,
                50_000, PaymentMethod.CASH, null));

        SupplierAccountDetail settled = supplierService.findDetail(supplierId).orElseThrow();
        assertEquals(0, settled.summary().balancePaisa());
        assertEquals("Settled", AccountBalancePresentation.supplier(
                settled.summary().balancePaisa()));
        assertEquals(List.of(-50_000L, 0L),
                settled.entries().stream().map(row -> row.runningBalancePaisa()).toList());
        assertEquals(1, scalar("SELECT COUNT(*) FROM supplier_account_entry "
                + "WHERE supplier_id = '" + supplierId + "' "
                + "AND entry_type = 'CREDIT_REFUND_RECEIVED' "
                + "AND amount_paisa = 50000 AND payment_method = 'CASH'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM purchase "
                + "WHERE id = 'supplier-full-refund-source' AND payment_method = 'CASH' "
                + "AND total_amount_paisa = 100000"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM purchase_return "
                + "WHERE id = 'supplier-full-refund-credit' AND settlement_method = 'CREDIT' "
                + "AND total_amount_paisa = 50000"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM inventory_movement"));
    }

    @Test
    void invalidCreditSettlementsLeaveNoPayoutOrRefundRows() throws Exception {
        insertSale("invalid-payout-source", customerId, "CASH", 100_000);
        insertSalesReturn("invalid-payout-credit", "invalid-payout-source",
                "CREDIT", 40_000, 83);
        insertPurchase("invalid-refund-source", supplierId, "CASH", 100_000);
        insertCreditPurchaseReturn("invalid-refund-credit", "invalid-refund-source",
                supplierId, DAY_1.plusDays(1), 84, 50_000, "2026-09-02T01:00:00Z");

        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(customerId, DAY_1.plusDays(2), AccountEntryType.CREDIT_PAYOUT,
                        40_001, PaymentMethod.CASH)));
        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(customerId, DAY_1.plusDays(2), AccountEntryType.CREDIT_PAYOUT,
                        10_000, PaymentMethod.CREDIT)));
        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(customerId, DAY_1.plusDays(2), AccountEntryType.CREDIT_PAYOUT,
                        0, PaymentMethod.CASH)));
        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(customerId, DAY_1.plusDays(2), AccountEntryType.CREDIT_PAYOUT,
                        -1, PaymentMethod.QR)));

        assertThrows(AccountValidationException.class, () -> supplierService.record(
                entry(supplierId, DAY_1.plusDays(2), AccountEntryType.CREDIT_REFUND_RECEIVED,
                        50_001, PaymentMethod.CASH)));
        assertThrows(AccountValidationException.class, () -> supplierService.record(
                entry(supplierId, DAY_1.plusDays(2), AccountEntryType.CREDIT_REFUND_RECEIVED,
                        10_000, PaymentMethod.CREDIT)));
        assertThrows(AccountValidationException.class, () -> supplierService.record(
                entry(supplierId, DAY_1.plusDays(2), AccountEntryType.CREDIT_REFUND_RECEIVED,
                        0, PaymentMethod.CASH)));
        assertThrows(AccountValidationException.class, () -> supplierService.record(
                entry(supplierId, DAY_1.plusDays(2), AccountEntryType.CREDIT_REFUND_RECEIVED,
                        -1, PaymentMethod.QR)));
        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(UUID.randomUUID(), DAY_1.plusDays(2), AccountEntryType.CREDIT_PAYOUT,
                        1, PaymentMethod.CASH)));
        assertThrows(AccountValidationException.class, () -> supplierService.record(
                entry(UUID.randomUUID(), DAY_1.plusDays(2),
                        AccountEntryType.CREDIT_REFUND_RECEIVED, 1, PaymentMethod.QR)));

        UUID zeroCustomer = new CustomerService(customerParties).create(
                new CustomerDraft("Zero Customer", null, null, true)).id();
        UUID positiveCustomer = new CustomerService(customerParties).create(
                new CustomerDraft("Positive Customer", null, null, true)).id();
        customerService.record(entry(positiveCustomer, DAY_1,
                AccountEntryType.OPENING_BALANCE, 100, null));
        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(zeroCustomer, DAY_1, AccountEntryType.CREDIT_PAYOUT,
                        1, PaymentMethod.CASH)));
        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(positiveCustomer, DAY_1, AccountEntryType.CREDIT_PAYOUT,
                        1, PaymentMethod.CASH)));

        UUID zeroSupplier = new SupplierService(supplierParties).create(
                new SupplierDraft("Zero Supplier", null, null, null, true)).id();
        UUID positiveSupplier = new SupplierService(supplierParties).create(
                new SupplierDraft("Positive Supplier", null, null, null, true)).id();
        supplierService.record(entry(positiveSupplier, DAY_1,
                AccountEntryType.OPENING_BALANCE, 100, null));
        assertThrows(AccountValidationException.class, () -> supplierService.record(
                entry(zeroSupplier, DAY_1, AccountEntryType.CREDIT_REFUND_RECEIVED,
                        1, PaymentMethod.CASH)));
        assertThrows(AccountValidationException.class, () -> supplierService.record(
                entry(positiveSupplier, DAY_1, AccountEntryType.CREDIT_REFUND_RECEIVED,
                        1, PaymentMethod.CASH)));

        assertEquals(0, scalar("SELECT COUNT(*) FROM customer_account_entry "
                + "WHERE entry_type = 'CREDIT_PAYOUT'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM supplier_account_entry "
                + "WHERE entry_type = 'CREDIT_REFUND_RECEIVED'"));
        assertEquals(-40_000,
                customerService.findDetail(customerId).orElseThrow().summary().balancePaisa());
        assertEquals(-50_000,
                supplierService.findDetail(supplierId).orElseThrow().summary().balancePaisa());
    }

    @Test
    void cashAndQrTransactionsDoNotChangeDerivedBalances() throws Exception {
        insertSale("cash-sale", customerId, "CASH", 700);
        insertSale("qr-sale", customerId, "QR", 800);
        insertSalesReturn("cash-return", "cash-sale", "CASH", 200, 71);
        insertSalesReturn("qr-return", "qr-sale", "QR", 300, 72);
        insertPurchase("cash-purchase", supplierId, "CASH", 900);
        insertPurchase("qr-purchase", supplierId, "QR", 1_100);
        insertPurchaseReturn("cash-purchase-return", "cash-purchase", supplierId,
                "CASH", 200);
        insertPurchaseReturn("qr-purchase-return", "qr-purchase", supplierId,
                "QR", 300);

        assertEquals(0, customerService.findDetail(customerId).orElseThrow().summary().balancePaisa());
        assertEquals(0, supplierService.findDetail(supplierId).orElseThrow().summary().balancePaisa());
    }

    @Test
    void legacyUnspecifiedTransactionsAreIgnored() throws Exception {
        insertPurchase("legacy-purchase", supplierId, "LEGACY_UNSPECIFIED", 9_999);
        insertPurchaseReturn("legacy-return", "legacy-purchase", supplierId,
                "LEGACY_UNSPECIFIED", 4_000);

        SupplierAccountDetail detail = supplierService.findDetail(supplierId).orElseThrow();

        assertTrue(detail.entries().isEmpty());
        assertEquals(0, detail.summary().balancePaisa());
    }

    @Test
    void legacyCustomerlessCreditRefundIsIgnored() throws Exception {
        execute("""
                INSERT INTO sale (
                    id, customer_id, sale_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES ('old-walk-in', NULL, '2026-09-01', 88, 'CASH',
                          500, '2026-09-01T01:00:00Z', NULL)
                """);
        insertSalesReturn("old-credit-refund", "old-walk-in", "CREDIT", 200, 89);

        assertEquals(0,
                customerService.findDetail(customerId).orElseThrow().summary().balancePaisa());
        assertTrue(customerService.findDetail(customerId).orElseThrow().entries().isEmpty());
    }

    @Test
    void filtersIncludeInactivePartiesAndSeparatePositiveCreditAndSettled() throws Exception {
        customerService.record(entry(customerId, DAY_1, AccountEntryType.OPENING_BALANCE,
                500, null));
        execute("UPDATE customer SET is_active = 0 WHERE id = ?", customerId);
        UUID creditCustomer = new CustomerService(customerParties).create(
                new CustomerDraft("Bina", null, null, true)).id();
        insertCreditSale("credit-origin", creditCustomer, DAY_1, 31, 100,
                "2026-09-01T02:00:00Z");
        insertCreditSalesReturn("credit-return", "credit-origin", DAY_1.plusDays(1),
                9, 150, "2026-09-02T02:00:00Z");

        var positive = customerService.search("asha", AccountBalanceFilter.POSITIVE);
        var credit = customerService.search(null, AccountBalanceFilter.CREDIT);
        var settled = customerService.search(null, AccountBalanceFilter.SETTLED);

        assertEquals(1, positive.accounts().size());
        assertFalse(positive.accounts().get(0).active());
        assertEquals(-50, credit.accounts().get(0).balancePaisa());
        assertFalse(settled.accounts().stream()
                .anyMatch(row -> row.customerName().equals("Bina")));
        assertFalse(positive.truncated());
    }

    @Test
    void duplicateOpeningAndOverpaymentAreRejectedWithoutExtraRows() {
        customerService.record(entry(customerId, DAY_1, AccountEntryType.OPENING_BALANCE,
                500, null));

        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(customerId, DAY_1, AccountEntryType.OPENING_BALANCE, 100, null)));
        assertThrows(AccountValidationException.class, () -> customerService.record(
                entry(customerId, DAY_1, AccountEntryType.PAYMENT_RECEIVED, 501,
                        PaymentMethod.CASH)));

        assertEquals(1, customerService.findDetail(customerId).orElseThrow().entries().size());
        assertEquals(500, customerService.findDetail(customerId).orElseThrow().summary().balancePaisa());
    }

    @Test
    void multipleCreditSalesAggregateAndSameTimestampOrderingIsStable() throws Exception {
        insertCreditSale("sale-b", customerId, DAY_1, 42, 200,
                "2026-09-01T02:00:00Z");
        insertCreditSale("sale-a", customerId, DAY_1, 41, 100,
                "2026-09-01T02:00:00Z");

        CustomerAccountDetail first = customerService.findDetail(customerId).orElseThrow();
        CustomerAccountDetail second = customerService.findDetail(customerId).orElseThrow();

        assertEquals(300, first.summary().balancePaisa());
        assertEquals(List.of(100L, 300L),
                first.entries().stream().map(row -> row.runningBalancePaisa()).toList());
        assertEquals(first.entries().stream().map(row -> row.stableKey()).toList(),
                second.entries().stream().map(row -> row.stableKey()).toList());
    }

    @Test
    void inactiveSupplierWithPayableRemainsVisibleAndSearchable() throws Exception {
        supplierService.record(entry(supplierId, DAY_1, AccountEntryType.OPENING_BALANCE,
                750, null));
        execute("UPDATE supplier SET is_active = 0 WHERE id = ?", supplierId);

        var result = supplierService.search("HIMAL", AccountBalanceFilter.POSITIVE);

        assertEquals(1, result.accounts().size());
        assertFalse(result.accounts().get(0).active());
        assertEquals(750, result.accounts().get(0).balancePaisa());
    }

    @Test
    void failedManualInsertRollsBackTheInsertedRow() throws Exception {
        CustomerAccountEntryRepository insertThenFail = new CustomerAccountEntryRepository() {
            @Override
            public boolean hasOpeningBalance(TransactionContext transaction, UUID id) {
                return customerEntries.hasOpeningBalance(transaction, id);
            }

            @Override
            public void insert(TransactionContext transaction, AccountEntry entry) {
                customerEntries.insert(transaction, entry);
                throw new IllegalStateException("forced failure");
            }
        };
        CustomerAccountService failing = new CustomerAccountService(
                customerAccounts, insertThenFail, customerParties, transactions,
                Clock.fixed(CREATED, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> failing.record(
                entry(customerId, DAY_1, AccountEntryType.OPENING_BALANCE, 100, null)));

        assertEquals(0, scalar("SELECT COUNT(*) FROM customer_account_entry"));
    }

    @Test
    void failedCustomerCreditPayoutInsertRollsBackAndLeavesCreditAvailable() throws Exception {
        insertSale("rollback-payout-source", customerId, "CASH", 100_000);
        insertSalesReturn("rollback-payout-credit", "rollback-payout-source",
                "CREDIT", 40_000, 85);
        CustomerAccountEntryRepository insertThenFail = new CustomerAccountEntryRepository() {
            @Override
            public boolean hasOpeningBalance(TransactionContext transaction, UUID id) {
                return customerEntries.hasOpeningBalance(transaction, id);
            }

            @Override
            public void insert(TransactionContext transaction, AccountEntry accountEntry) {
                customerEntries.insert(transaction, accountEntry);
                throw new IllegalStateException("forced payout failure");
            }
        };
        CustomerAccountService failing = new CustomerAccountService(
                customerAccounts, insertThenFail, customerParties, transactions,
                Clock.fixed(CREATED, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> failing.record(
                entry(customerId, DAY_1.plusDays(2), AccountEntryType.CREDIT_PAYOUT,
                        40_000, PaymentMethod.CASH)));

        assertEquals(0, scalar("SELECT COUNT(*) FROM customer_account_entry "
                + "WHERE entry_type = 'CREDIT_PAYOUT'"));
        assertEquals(-40_000,
                customerService.findDetail(customerId).orElseThrow().summary().balancePaisa());
    }

    @Test
    void failedSupplierCreditRefundInsertRollsBackAndLeavesCreditAvailable() throws Exception {
        insertPurchase("rollback-refund-source", supplierId, "CASH", 100_000);
        insertCreditPurchaseReturn("rollback-refund-credit", "rollback-refund-source",
                supplierId, DAY_1.plusDays(1), 86, 50_000, "2026-09-02T01:00:00Z");
        SupplierAccountEntryRepository insertThenFail = new SupplierAccountEntryRepository() {
            @Override
            public boolean hasOpeningBalance(TransactionContext transaction, UUID id) {
                return supplierEntries.hasOpeningBalance(transaction, id);
            }

            @Override
            public void insert(TransactionContext transaction, AccountEntry accountEntry) {
                supplierEntries.insert(transaction, accountEntry);
                throw new IllegalStateException("forced supplier-refund failure");
            }
        };
        SupplierAccountService failing = new SupplierAccountService(
                supplierAccounts, insertThenFail, supplierParties, transactions,
                Clock.fixed(CREATED, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> failing.record(
                entry(supplierId, DAY_1.plusDays(2),
                        AccountEntryType.CREDIT_REFUND_RECEIVED,
                        50_000, PaymentMethod.CASH)));

        assertEquals(0, scalar("SELECT COUNT(*) FROM supplier_account_entry "
                + "WHERE entry_type = 'CREDIT_REFUND_RECEIVED'"));
        assertEquals(-50_000,
                supplierService.findDetail(supplierId).orElseThrow().summary().balancePaisa());
    }

    @Test
    void accountListsAreBoundedAndReportTruncation() throws Exception {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO customer (id, name, is_active, created_at, updated_at)
                     VALUES (?, ?, 1, '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                     """)) {
            for (int index = 0; index < 151; index++) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, "Customer " + index);
                statement.addBatch();
            }
            statement.executeBatch();
        }

        var result = customerService.search(null, AccountBalanceFilter.ALL);

        assertEquals(CustomerAccountService.LIST_LIMIT, result.accounts().size());
        assertTrue(result.truncated());
    }

    @Test
    void derivedRunningBalanceFailsClearlyOnLongOverflow() throws Exception {
        customerService.record(entry(customerId, DAY_1, AccountEntryType.OPENING_BALANCE,
                Long.MAX_VALUE, null));
        insertCreditSale("overflow-sale", customerId, DAY_1.plusDays(1), 91, 1,
                "2026-09-02T01:00:00Z");

        assertThrows(DataAccessException.class, () -> customerService.findDetail(customerId));
    }

    private static AccountEntryDraft entry(
            UUID partyId, LocalDate date, AccountEntryType type, long amount,
            PaymentMethod paymentMethod) {
        return entry(partyId, date, type, amount, paymentMethod, "REF-1");
    }

    private static AccountEntryDraft entry(
            UUID partyId, LocalDate date, AccountEntryType type, long amount,
            PaymentMethod paymentMethod, String reference) {
        return new AccountEntryDraft(partyId, date, type, amount, paymentMethod,
                reference, "note", null);
    }

    private void insertCreditSale(
            String id, UUID customer, LocalDate date, long invoice, long amount, String createdAt)
            throws Exception {
        execute("""
                INSERT INTO sale (
                    id, customer_id, sale_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, 'CREDIT', ?, ?, NULL)
                """, id, customer, date, invoice, amount, createdAt);
    }

    private void insertCreditSalesReturn(
            String id, String saleId, LocalDate date, long number, long amount, String createdAt)
            throws Exception {
        execute("""
                INSERT INTO sales_return (
                    id, return_number, original_sale_id, return_date, reason, refund_method,
                    total_amount_paisa, notes, created_at, created_by
                ) VALUES (?, ?, ?, ?, 'CUSTOMER_RETURN', 'CREDIT', ?, NULL, ?, NULL)
                """, id, number, saleId, date, amount, createdAt);
    }

    private void insertCreditPurchase(
            String id, UUID supplier, LocalDate date, String invoice, long amount, String createdAt)
            throws Exception {
        execute("""
                INSERT INTO purchase (
                    id, supplier_id, purchase_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, 'CREDIT', ?, ?, NULL)
                """, id, supplier, date, invoice, amount, createdAt);
    }

    private void insertCreditPurchaseReturn(
            String id, String purchaseId, UUID supplier, LocalDate date,
            long number, long amount, String createdAt) throws Exception {
        execute("""
                INSERT INTO purchase_return (
                    id, return_number, original_purchase_id, supplier_id, return_date,
                    reason, settlement_method, notes, total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, 'DAMAGED', 'CREDIT', NULL, ?, ?, NULL)
                """, id, number, purchaseId, supplier, date, amount, createdAt);
    }

    private void insertSale(String id, UUID customer, String method, long amount) throws Exception {
        execute("""
                INSERT INTO sale (
                    id, customer_id, sale_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, '2026-09-01', ?, ?, ?, '2026-09-01T01:00:00Z', NULL)
                """, id, customer, Math.abs(id.hashCode()) + 100L, method, amount);
    }

    private void insertPurchase(String id, UUID supplier, String method, long amount)
            throws Exception {
        execute("""
                INSERT INTO purchase (
                    id, supplier_id, purchase_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, '2026-09-01', NULL, ?, ?, '2026-09-01T01:00:00Z', NULL)
                """, id, supplier, method, amount);
    }

    private void insertSalesReturn(
            String id, String saleId, String method, long amount, long number) throws Exception {
        execute("""
                INSERT INTO sales_return (
                    id, return_number, original_sale_id, return_date, reason, refund_method,
                    total_amount_paisa, notes, created_at, created_by
                ) VALUES (?, ?, ?, '2026-09-02', 'CUSTOMER_RETURN', ?, ?, NULL,
                          '2026-09-02T01:00:00Z', NULL)
                """, id, number, saleId, method, amount);
    }

    private void insertPurchaseReturn(
            String id, String purchaseId, UUID supplier, String method, long amount)
            throws Exception {
        execute("""
                INSERT INTO purchase_return (
                    id, return_number, original_purchase_id, supplier_id, return_date,
                    reason, settlement_method, notes, total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, '2026-09-02', 'DAMAGED', ?, NULL, ?,
                          '2026-09-02T01:00:00Z', NULL)
                """, id, Math.abs(id.hashCode()) + 100L, purchaseId, supplier, method, amount);
    }

    private void execute(String sql, Object... values) throws Exception {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                Object value = values[index];
                statement.setObject(index + 1, value instanceof UUID ? value.toString() : value);
            }
            statement.executeUpdate();
        }
    }

    private long scalar(String sql) throws Exception {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }
}
