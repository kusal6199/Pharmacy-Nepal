package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.purchasing.PurchaseLine;
import com.nepalpharmacy.purchasing.PurchaseLineRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public Optional<PurchaseLine> findById(TransactionContext transaction, UUID id) {
        return findById(JdbcTransactionContext.connection(transaction), id);
    }

    @Override
    public Optional<PurchaseLine> findById(UUID id) {
        try (Connection connection = connections.open()) {
            return findById(connection, id);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not find purchase line.", exception);
        }
    }

    private Optional<PurchaseLine> findById(Connection connection, UUID id) {
        String sql = """
                SELECT id, purchase_id, batch_id, quantity_received_base_units,
                       unit_purchase_price_paisa, line_total_paisa
                FROM purchase_line WHERE id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not find purchase line.", exception);
        }
    }

    @Override
    public List<PurchaseLine> findByPurchaseId(
            TransactionContext transaction, UUID purchaseId) {
        return findByPurchaseId(JdbcTransactionContext.connection(transaction), purchaseId);
    }

    @Override
    public List<PurchaseLine> findByPurchaseId(UUID purchaseId) {
        try (Connection connection = connections.open()) {
            return findByPurchaseId(connection, purchaseId);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not list purchase lines.", exception);
        }
    }

    private List<PurchaseLine> findByPurchaseId(Connection connection, UUID purchaseId) {
        String sql = """
                SELECT id, purchase_id, batch_id, quantity_received_base_units,
                       unit_purchase_price_paisa, line_total_paisa
                FROM purchase_line WHERE purchase_id = ? ORDER BY rowid
                """;
        List<PurchaseLine> lines = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, purchaseId.toString());
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    lines.add(map(results));
                }
            }
            return lines;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not list purchase lines.", exception);
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

    private static PurchaseLine map(ResultSet results) throws SQLException {
        return new PurchaseLine(
                UUID.fromString(results.getString("id")),
                UUID.fromString(results.getString("purchase_id")),
                UUID.fromString(results.getString("batch_id")),
                results.getInt("quantity_received_base_units"),
                results.getLong("unit_purchase_price_paisa"),
                results.getLong("line_total_paisa"));
    }
}
