package com.nepalpharmacy.credit.infrastructure;

import com.nepalpharmacy.credit.AccountBalanceFilter;
import com.nepalpharmacy.credit.AccountLedgerEntry;
import com.nepalpharmacy.credit.BoundedAccountResult;
import com.nepalpharmacy.credit.CustomerAccountDetail;
import com.nepalpharmacy.credit.CustomerAccountRepository;
import com.nepalpharmacy.credit.CustomerAccountSummary;
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

public final class JdbcCustomerAccountRepository implements CustomerAccountRepository {
    private static final String EVENTS = """
            WITH customer_event AS (
                SELECT customer_id, entry_date AS business_date, created_at,
                       'MANUAL_' || entry_type AS event_kind, id AS source_id,
                       CASE entry_type
                           WHEN 'OPENING_BALANCE' THEN amount_paisa
                           ELSE -amount_paisa
                       END AS delta_paisa,
                       reference_text, notes
                FROM customer_account_entry
                UNION ALL
                SELECT customer_id, sale_date, created_at, 'CREDIT_SALE', id,
                       total_amount_paisa,
                       'Sale #' || invoice_number, NULL
                FROM sale
                WHERE customer_id IS NOT NULL AND payment_method = 'CREDIT'
                UNION ALL
                SELECT s.customer_id, sr.return_date, sr.created_at,
                       'CREDIT_SALES_RETURN', sr.id, -sr.total_amount_paisa,
                       'Sales return #' || sr.return_number, sr.notes
                FROM sales_return sr
                JOIN sale s ON s.id = sr.original_sale_id
                WHERE s.customer_id IS NOT NULL AND sr.refund_method = 'CREDIT'
            )
            """;

    private final ConnectionProvider connections;

    public JdbcCustomerAccountRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public BoundedAccountResult<CustomerAccountSummary> search(
            String query, AccountBalanceFilter filter, int limit) {
        String balanceCondition = switch (filter) {
            case ALL -> "1 = 1";
            case POSITIVE -> "balance_paisa > 0";
            case CREDIT -> "balance_paisa < 0";
            case SETTLED -> "balance_paisa = 0";
        };
        String sql = EVENTS + """
                , account AS (
                    SELECT c.id, c.name, c.phone, c.is_active,
                           COALESCE(SUM(e.delta_paisa), 0) AS balance_paisa,
                           MAX(e.business_date) AS last_activity_date
                    FROM customer c
                    LEFT JOIN customer_event e ON e.customer_id = c.id
                    GROUP BY c.id, c.name, c.phone, c.is_active
                )
                SELECT id, name, phone, is_active, balance_paisa, last_activity_date
                FROM account
                WHERE (? IS NULL OR lower(name) LIKE ?)
                  AND """ + " " + balanceCondition + "\n" + """
                ORDER BY abs(balance_paisa) DESC, name COLLATE NOCASE, id
                LIMIT ?
                """;
        List<CustomerAccountSummary> results = new ArrayList<>();
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
            throw new DataAccessException("Could not search customer credit accounts.", exception);
        }
    }

    @Override
    public Optional<CustomerAccountDetail> findDetail(UUID customerId) {
        try (Connection connection = connections.open()) {
            PartyRow party = findParty(connection, customerId);
            if (party == null) return Optional.empty();
            List<AccountLedgerEntry> ledger = loadLedger(connection, customerId);
            long balance = ledger.isEmpty() ? 0 : ledger.get(ledger.size() - 1).runningBalancePaisa();
            LocalDate last = ledger.isEmpty() ? null : ledger.get(ledger.size() - 1).businessDate();
            return Optional.of(new CustomerAccountDetail(new CustomerAccountSummary(
                    customerId, party.name(), party.phone(), party.active(), balance, last), ledger));
        } catch (SQLException | ArithmeticException exception) {
            throw new DataAccessException("Could not load customer credit account.", exception);
        }
    }

    @Override
    public long currentBalance(TransactionContext transaction, UUID customerId) {
        try {
            return sumDeltas(JdbcTransactionContext.connection(transaction), customerId);
        } catch (SQLException | ArithmeticException exception) {
            throw new DataAccessException("Could not calculate customer balance.", exception);
        }
    }

    private PartyRow findParty(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT name, phone, is_active FROM customer WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var row = statement.executeQuery()) {
                return row.next() ? new PartyRow(row.getString(1), row.getString(2), row.getInt(3) == 1) : null;
            }
        }
    }

    private List<AccountLedgerEntry> loadLedger(Connection connection, UUID customerId)
            throws SQLException {
        String sql = EVENTS + """
                SELECT business_date, created_at, event_kind, source_id,
                       delta_paisa, reference_text, notes
                FROM customer_event
                WHERE customer_id = ?
                ORDER BY business_date, created_at, event_kind, source_id
                """;
        List<AccountLedgerEntry> entries = new ArrayList<>();
        long running = 0;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    long delta = rows.getLong("delta_paisa");
                    running = Math.addExact(running, delta);
                    String kind = rows.getString("event_kind");
                    entries.add(new AccountLedgerEntry(
                            LocalDate.parse(rows.getString("business_date")),
                            Instant.parse(rows.getString("created_at")),
                            kind + ":" + rows.getString("source_id"),
                            customerActivity(kind), rows.getString("reference_text"),
                            rows.getString("notes"), Math.max(delta, 0),
                            delta < 0 ? Math.negateExact(delta) : 0, running));
                }
            }
        }
        return entries;
    }

    private long sumDeltas(Connection connection, UUID customerId) throws SQLException {
        String sql = EVENTS + """
                SELECT delta_paisa FROM customer_event
                WHERE customer_id = ?
                ORDER BY business_date, created_at, event_kind, source_id
                """;
        long total = 0;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) total = Math.addExact(total, rows.getLong(1));
            }
        }
        return total;
    }

    private static CustomerAccountSummary mapSummary(ResultSet row) throws SQLException {
        String last = row.getString("last_activity_date");
        return new CustomerAccountSummary(UUID.fromString(row.getString("id")),
                row.getString("name"), row.getString("phone"), row.getInt("is_active") == 1,
                row.getLong("balance_paisa"), last == null ? null : LocalDate.parse(last));
    }

    private static String customerActivity(String kind) {
        return switch (kind) {
            case "MANUAL_OPENING_BALANCE" -> "Opening balance";
            case "MANUAL_PAYMENT_RECEIVED" -> "Payment received";
            case "CREDIT_SALE" -> "Credit sale";
            case "CREDIT_SALES_RETURN" -> "Sales return credit";
            default -> throw new IllegalArgumentException("Unknown customer ledger event: " + kind);
        };
    }

    private record PartyRow(String name, String phone, boolean active) { }
}
