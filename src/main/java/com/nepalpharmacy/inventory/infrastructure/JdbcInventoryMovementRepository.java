package com.nepalpharmacy.inventory.infrastructure;

import com.nepalpharmacy.inventory.InventoryMovement;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;

public final class JdbcInventoryMovementRepository implements InventoryMovementRepository {

    private final ConnectionProvider connections;

    public JdbcInventoryMovementRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(TransactionContext transaction, InventoryMovement movement) {
        String sql = """
                INSERT INTO inventory_movement (
                    id, batch_id, movement_type, quantity_base_units, reference_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, movement.id().toString());
            statement.setString(2, movement.batchId().toString());
            statement.setString(3, movement.movementType().name());
            statement.setInt(4, movement.quantityBaseUnits());
            statement.setString(5, movement.referenceId().toString());
            statement.setString(6, movement.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not append inventory movement.", exception);
        }
    }

    @Override
    public long count() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM inventory_movement");
             var results = statement.executeQuery()) {
            results.next();
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not count inventory movements.", exception);
        }
    }
}
