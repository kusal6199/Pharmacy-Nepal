package com.nepalpharmacy.credit.infrastructure;

import com.nepalpharmacy.credit.AccountBalanceFilter;
import com.nepalpharmacy.credit.AccountLedgerEntry;
import com.nepalpharmacy.credit.BoundedAccountResult;
import com.nepalpharmacy.credit.SupplierAccountDetail;
import com.nepalpharmacy.credit.SupplierAccountRepository;
import com.nepalpharmacy.credit.SupplierAccountSummary;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcSupplierAccountRepository implements SupplierAccountRepository {
    private static final String EVENTS = """
            WITH supplier_event AS (
                SELECT supplier_id, entry_date AS business_date, created_at,
                       'MANUAL_' || entry_type AS event_kind, id AS source_id,
                       CASE entry_type
                           WHEN 'OPENING_BALANCE' THEN amount_paisa
                           WHEN 'PAYMENT_MADE' THEN -amount_paisa
                           WHEN 'CREDIT_REFUND_RECEIVED' THEN amount_paisa
                       END AS delta_paisa,
                       CASE entry_type
                           WHEN 'CREDIT_REFUND_RECEIVED' THEN COALESCE(
                               reference_text,
                               CASE payment_method
                                   WHEN 'CASH' THEN 'Cash refund received'
                                   WHEN 'QR' THEN 'QR / digital refund received'
                               END)
                           ELSE reference_text
                       END AS reference_text,
                       notes
                FROM supplier_account_entry
                UNION ALL
                SELECT supplier_id, purchase_date, created_at, 'CREDIT_PURCHASE', id,
                       total_amount_paisa,
                       CASE WHEN invoice_number IS NULL
                           THEN 'Purchase on ' || purchase_date
                           ELSE 'Supplier invoice ' || invoice_number END,
                       NULL
                FROM purchase
                WHERE payment_method = 'CREDIT'
                UNION ALL
                SELECT supplier_id, return_date, created_at, 'CREDIT_PURCHASE_RETURN', id,
                       -total_amount_paisa,
                       'Purchase return #' || return_number, notes
                FROM purchase_return
                WHERE settlement_method = 'CREDIT'
            )
            """;

    private final ConnectionProvider connections;

    public JdbcSupplierAccountRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public BoundedAccountResult<SupplierAccountSummary> search(
            String query, AccountBalanceFilter filter, int limit) {
        String balanceCondition = switch (filter) {
            case ALL -> "1 = 1";
            case POSITIVE -> "balance_paisa > 0";
            case CREDIT -> "balance_paisa < 0";
            case SETTLED -> "balance_paisa = 0";
        };
        String sql = EVENTS + """
                , account AS (
                    SELECT s.id, s.name, s.phone, s.is_active,
                           COALESCE(SUM(e.delta_paisa), 0) AS balance_paisa,
                           MAX(e.business_date) AS last_activity_date
                    FROM supplier s
                    LEFT JOIN supplier_event e ON e.supplier_id = s.id
                    GROUP BY s.id, s.name, s.phone, s.is_active
                )
                SELECT id, name, phone, is_active, balance_paisa, last_activity_date
                FROM account
                WHERE (? IS NULL OR lower(name) LIKE ?)
                  AND """ + " " + balanceCondition + "\n" + """
                ORDER BY abs(balance_paisa) DESC, name COLLATE NOCASE, id
                LIMIT ?
                """;
        List<SupplierAccountSummary> results = new ArrayList<>();
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql)) {
            String pattern = query == null ? null : "%" + query.toLowerCase(java.util.Locale.ROOT) + "%";
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setInt(3, limit + 1);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) results.add(mapSummary(rows));
            }
            boolean truncated = results.size() > limit;
            if (truncated) results.remove(results.size() - 1);
            return new BoundedAccountResult<>(results, truncated);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not search supplier credit accounts.", exception);
        }
    }

    @Override
    public Optional<SupplierAccountDetail> findDetail(UUID supplierId) {
        try (Connection connection = connections.open()) {
            PartyRow party = findParty(connection, supplierId);
            if (party == null) return Optional.empty();
            List<AccountLedgerEntry> ledger = loadLedger(connection, supplierId);
            long balance = ledger.isEmpty() ? 0 : ledger.get(ledger.size() - 1).runningBalancePaisa();
            LocalDate last = ledger.isEmpty() ? null : ledger.get(ledger.size() - 1).businessDate();
            return Optional.of(new SupplierAccountDetail(new SupplierAccountSummary(
                    supplierId, party.name(), party.phone(), party.active(), balance, last), ledger));
        } catch (SQLException | ArithmeticException exception) {
            throw new DataAccessException("Could not load supplier credit account.", exception);
        }
    }

    @Override
    public long currentBalance(TransactionContext transaction, UUID supplierId) {
        try {
            return sumDeltas(JdbcTransactionContext.connection(transaction), supplierId);
        } catch (SQLException | ArithmeticException exception) {
            throw new DataAccessException("Could not calculate supplier balance.", exception);
        }
    }

    private PartyRow findParty(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT name, phone, is_active FROM supplier WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var row = statement.executeQuery()) {
                return row.next() ? new PartyRow(row.getString(1), row.getString(2), row.getInt(3) == 1) : null;
            }
        }
    }

    private List<AccountLedgerEntry> loadLedger(Connection connection, UUID supplierId)
            throws SQLException {
        String sql = EVENTS + """
                SELECT business_date, created_at, event_kind, source_id,
                       delta_paisa, reference_text, notes
                FROM supplier_event
                WHERE supplier_id = ?
                ORDER BY business_date, created_at, event_kind, source_id
                """;
        List<AccountLedgerEntry> entries = new ArrayList<>();
        long running = 0;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, supplierId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    long delta = rows.getLong("delta_paisa");
                    running = Math.addExact(running, delta);
                    String kind = rows.getString("event_kind");
                    entries.add(new AccountLedgerEntry(
                            LocalDate.parse(rows.getString("business_date")),
                            Instant.parse(rows.getString("created_at")),
                            kind + ":" + rows.getString("source_id"),
                            supplierActivity(kind), rows.getString("reference_text"),
                            rows.getString("notes"), Math.max(delta, 0),
                            delta < 0 ? Math.negateExact(delta) : 0, running));
                }
            }
        }
        return entries;
    }

    private long sumDeltas(Connection connection, UUID supplierId) throws SQLException {
        String sql = EVENTS + """
                SELECT delta_paisa FROM supplier_event
                WHERE supplier_id = ?
                ORDER BY business_date, created_at, event_kind, source_id
                """;
        long total = 0;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, supplierId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) total = Math.addExact(total, rows.getLong(1));
            }
        }
        return total;
    }

    private static SupplierAccountSummary mapSummary(ResultSet row) throws SQLException {
        String last = row.getString("last_activity_date");
        return new SupplierAccountSummary(UUID.fromString(row.getString("id")),
                row.getString("name"), row.getString("phone"), row.getInt("is_active") == 1,
                row.getLong("balance_paisa"), last == null ? null : LocalDate.parse(last));
    }

    private static String supplierActivity(String kind) {
        return switch (kind) {
            case "MANUAL_OPENING_BALANCE" -> "Opening balance";
            case "MANUAL_PAYMENT_MADE" -> "Payment made";
            case "MANUAL_CREDIT_REFUND_RECEIVED" -> "Supplier credit refund received";
            case "CREDIT_PURCHASE" -> "Credit purchase";
            case "CREDIT_PURCHASE_RETURN" -> "Credit purchase return";
            default -> throw new IllegalArgumentException("Unknown supplier ledger event: " + kind);
        };
    }

    private record PartyRow(String name, String phone, boolean active) { }
}
