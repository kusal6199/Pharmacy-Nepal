package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.purchasing.PurchaseReturn;
import com.nepalpharmacy.purchasing.PurchaseReturnRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

public final class JdbcPurchaseReturnRepository implements PurchaseReturnRepository {

    private static final String COUNTER_NAME = "PURCHASE_RETURN";
    private final ConnectionProvider connections;

    public JdbcPurchaseReturnRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public long nextReturnNumber(TransactionContext transaction) {
        var connection = JdbcTransactionContext.connection(transaction);
        try {
            long nextValue;
            try (var select = connection.prepareStatement(
                    "SELECT next_value FROM invoice_counter WHERE counter_name = ?")) {
                select.setString(1, COUNTER_NAME);
                try (var results = select.executeQuery()) {
                    if (!results.next()) {
                        throw new DataAccessException("Purchase return counter does not exist.");
                    }
                    nextValue = results.getLong(1);
                }
            }
            try (var update = connection.prepareStatement("""
                    UPDATE invoice_counter SET next_value = ?
                    WHERE counter_name = ? AND next_value = ?
                    """)) {
                update.setLong(1, Math.addExact(nextValue, 1));
                update.setString(2, COUNTER_NAME);
                update.setLong(3, nextValue);
                if (update.executeUpdate() != 1) {
                    throw new DataAccessException("Purchase return counter changed concurrently.");
                }
            }
            return nextValue;
        } catch (SQLException | ArithmeticException exception) {
            throw new DataAccessException("Could not allocate purchase return number.", exception);
        }
    }

    @Override
    public void insert(TransactionContext transaction, PurchaseReturn purchaseReturn) {
        String sql = """
                INSERT INTO purchase_return (
                    id, return_number, original_purchase_id, supplier_id, return_date,
                    reason, notes, total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, purchaseReturn.id().toString());
            statement.setLong(2, purchaseReturn.returnNumber());
            statement.setString(3, purchaseReturn.originalPurchaseId().toString());
            statement.setString(4, purchaseReturn.supplierId().toString());
            statement.setString(5, purchaseReturn.returnDate().toString());
            statement.setString(6, purchaseReturn.reason().name());
            setNullableString(statement, 7, purchaseReturn.notes());
            statement.setLong(8, purchaseReturn.totalAmountPaisa());
            statement.setString(9, purchaseReturn.createdAt().toString());
            setNullableString(statement, 10,
                    purchaseReturn.createdBy() == null ? null : purchaseReturn.createdBy().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create purchase return.", exception);
        }
    }

    @Override
    public long count() {
        return scalar("SELECT COUNT(*) FROM purchase_return", "Could not count purchase returns.");
    }

    public long nextCounterValue() {
        return scalar("SELECT next_value FROM invoice_counter WHERE counter_name = 'PURCHASE_RETURN'",
                "Could not read purchase return counter.");
    }

    private long scalar(String sql, String message) {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql);
             var results = statement.executeQuery()) {
            if (!results.next()) {
                throw new DataAccessException(message);
            }
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException(message, exception);
        }
    }

    private static void setNullableString(
            java.sql.PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
