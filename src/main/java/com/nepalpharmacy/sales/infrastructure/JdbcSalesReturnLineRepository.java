package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.sales.SalesReturnLine;
import com.nepalpharmacy.sales.SalesReturnLineRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

public final class JdbcSalesReturnLineRepository implements SalesReturnLineRepository {

    private final ConnectionProvider connections;

    public JdbcSalesReturnLineRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(TransactionContext transaction, SalesReturnLine line) {
        String sql = """
                INSERT INTO sales_return_line (
                    id, sales_return_id, original_sale_line_id, product_id, batch_id,
                    quantity_returned_base_units, unit_price_paisa, line_total_paisa
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, line.id().toString());
            statement.setString(2, line.salesReturnId().toString());
            statement.setString(3, line.originalSaleLineId().toString());
            statement.setString(4, line.productId().toString());
            statement.setString(5, line.batchId().toString());
            statement.setInt(6, line.quantityReturnedBaseUnits());
            statement.setLong(7, line.unitPricePaisa());
            statement.setLong(8, line.lineTotalPaisa());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create sales return line.", exception);
        }
    }

    @Override
    public long returnedQuantityForOriginalLine(
            TransactionContext transaction, UUID originalSaleLineId) {
        String sql = """
                SELECT COALESCE(SUM(quantity_returned_base_units), 0)
                FROM sales_return_line WHERE original_sale_line_id = ?
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, originalSaleLineId.toString());
            try (var results = statement.executeQuery()) {
                results.next();
                return results.getLong(1);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not calculate returned sale quantity.", exception);
        }
    }

    @Override
    public long count() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM sales_return_line");
             var results = statement.executeQuery()) {
            results.next();
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not count sales return lines.", exception);
        }
    }
}
