package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.sales.SalesReturn;
import com.nepalpharmacy.sales.SalesReturnRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

public final class JdbcSalesReturnRepository implements SalesReturnRepository {

    private static final String COUNTER_NAME = "SALES_RETURN";
    private final ConnectionProvider connections;

    public JdbcSalesReturnRepository(ConnectionProvider connections) {
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
                        throw new DataAccessException("Sales return counter does not exist.");
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
                    throw new DataAccessException("Sales return counter changed concurrently.");
                }
            }
            return nextValue;
        } catch (SQLException | ArithmeticException exception) {
            throw new DataAccessException("Could not allocate sales return number.", exception);
        }
    }

    @Override
    public void insert(TransactionContext transaction, SalesReturn salesReturn) {
        String sql = """
                INSERT INTO sales_return (
                    id, return_number, original_sale_id, customer_id, return_date, reason,
                    refund_method, total_amount_paisa, notes, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, salesReturn.id().toString());
            statement.setLong(2, salesReturn.returnNumber());
            statement.setString(3, salesReturn.originalSaleId().toString());
            setNullableString(statement, 4,
                    salesReturn.customerId() == null ? null : salesReturn.customerId().toString());
            statement.setString(5, salesReturn.returnDate().toString());
            statement.setString(6, salesReturn.reason().name());
            statement.setString(7, salesReturn.refundMethod().name());
            statement.setLong(8, salesReturn.totalAmountPaisa());
            setNullableString(statement, 9, salesReturn.notes());
            statement.setString(10, salesReturn.createdAt().toString());
            setNullableString(statement, 11,
                    salesReturn.createdBy() == null ? null : salesReturn.createdBy().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create sales return.", exception);
        }
    }

    @Override
    public long count() {
        return scalar("SELECT COUNT(*) FROM sales_return", "Could not count sales returns.");
    }

    public long nextCounterValue() {
        return scalar("SELECT next_value FROM invoice_counter WHERE counter_name = 'SALES_RETURN'",
                "Could not read sales return counter.");
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
