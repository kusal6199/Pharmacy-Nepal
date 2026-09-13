package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.purchasing.PurchaseLine;
import com.nepalpharmacy.purchasing.PurchaseLineRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;

public final class JdbcPurchaseLineRepository implements PurchaseLineRepository {

    private final ConnectionProvider connections;

    public JdbcPurchaseLineRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(TransactionContext transaction, PurchaseLine line) {
        String sql = """
                INSERT INTO purchase_line (
                    id, purchase_id, batch_id, quantity_received_base_units,
                    unit_purchase_price_paisa, line_total_paisa
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, line.id().toString());
            statement.setString(2, line.purchaseId().toString());
            statement.setString(3, line.batchId().toString());
            statement.setInt(4, line.quantityReceivedBaseUnits());
            statement.setLong(5, line.unitPurchasePricePaisa());
            statement.setLong(6, line.lineTotalPaisa());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create purchase line.", exception);
        }
    }

    @Override
    public long count() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM purchase_line");
             var results = statement.executeQuery()) {
            results.next();
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not count purchase lines.", exception);
        }
    }
}
