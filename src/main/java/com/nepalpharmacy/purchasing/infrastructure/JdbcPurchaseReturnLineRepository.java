package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.purchasing.PurchaseReturnLine;
import com.nepalpharmacy.purchasing.PurchaseReturnLineRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

public final class JdbcPurchaseReturnLineRepository implements PurchaseReturnLineRepository {

    private final ConnectionProvider connections;

    public JdbcPurchaseReturnLineRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(TransactionContext transaction, PurchaseReturnLine line) {
        String sql = """
                INSERT INTO purchase_return_line (
                    id, purchase_return_id, original_purchase_line_id, product_id, batch_id,
                    quantity_returned_base_units, unit_cost_paisa, line_total_paisa
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, line.id().toString());
            statement.setString(2, line.purchaseReturnId().toString());
            statement.setString(3, line.originalPurchaseLineId().toString());
            statement.setString(4, line.productId().toString());
            statement.setString(5, line.batchId().toString());
            statement.setInt(6, line.quantityReturnedBaseUnits());
            statement.setLong(7, line.unitCostPaisa());
            statement.setLong(8, line.lineTotalPaisa());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create purchase return line.", exception);
        }
    }

    @Override
    public long returnedQuantityForOriginalLine(
            TransactionContext transaction, UUID originalPurchaseLineId) {
        String sql = """
                SELECT COALESCE(SUM(quantity_returned_base_units), 0)
                FROM purchase_return_line WHERE original_purchase_line_id = ?
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, originalPurchaseLineId.toString());
            try (var results = statement.executeQuery()) {
                results.next();
                return results.getLong(1);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not calculate returned purchase quantity.", exception);
        }
    }

    @Override
    public long count() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM purchase_return_line");
             var results = statement.executeQuery()) {
            results.next();
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not count purchase return lines.", exception);
        }
    }
}
