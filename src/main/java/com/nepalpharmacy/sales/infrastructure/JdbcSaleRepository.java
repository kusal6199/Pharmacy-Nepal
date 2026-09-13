package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.sales.Sale;
import com.nepalpharmacy.sales.SaleRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

public final class JdbcSaleRepository implements SaleRepository {

    private static final String COUNTER_NAME = "SALE";

    private final ConnectionProvider connections;

    public JdbcSaleRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public long nextInvoiceNumber(TransactionContext transaction) {
        var connection = JdbcTransactionContext.connection(transaction);
        try {
            long nextValue;
            try (var select = connection.prepareStatement(
                    "SELECT next_value FROM invoice_counter WHERE counter_name = ?")) {
                select.setString(1, COUNTER_NAME);
                try (var results = select.executeQuery()) {
                    if (!results.next()) {
                        throw new DataAccessException("Sale invoice counter does not exist.");
                    }
                    nextValue = results.getLong(1);
                }
            }

            long followingValue = Math.addExact(nextValue, 1);
            try (var update = connection.prepareStatement("""
                    UPDATE invoice_counter
                    SET next_value = ?
                    WHERE counter_name = ? AND next_value = ?
                    """)) {
                update.setLong(1, followingValue);
                update.setString(2, COUNTER_NAME);
                update.setLong(3, nextValue);
                if (update.executeUpdate() != 1) {
                    throw new DataAccessException("Sale invoice counter changed concurrently.");
                }
            }
            return nextValue;
        } catch (SQLException | ArithmeticException exception) {
            throw new DataAccessException("Could not allocate sale invoice number.", exception);
        }
    }

    @Override
    public void insert(TransactionContext transaction, Sale sale) {
        String sql = """
                INSERT INTO sale (
                    id, customer_id, sale_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, sale.id().toString());
            setNullableString(statement, 2,
                    sale.customerId() == null ? null : sale.customerId().toString());
            statement.setString(3, sale.saleDate().toString());
            statement.setLong(4, sale.invoiceNumber());
            statement.setString(5, sale.paymentMethod().name());
            statement.setLong(6, sale.totalAmountPaisa());
            statement.setString(7, sale.createdAt().toString());
            setNullableString(statement, 8,
                    sale.createdBy() == null ? null : sale.createdBy().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create sale.", exception);
        }
    }

    @Override
    public long count() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM sale");
             var results = statement.executeQuery()) {
            results.next();
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not count sales.", exception);
        }
    }

    public long nextCounterValue() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT next_value FROM invoice_counter WHERE counter_name = 'SALE'");
             var results = statement.executeQuery()) {
            if (!results.next()) {
                throw new DataAccessException("Sale invoice counter does not exist.");
            }
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not read sale invoice counter.", exception);
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
