package com.nepalpharmacy.inventory.infrastructure;

import com.nepalpharmacy.inventory.Batch;
import com.nepalpharmacy.inventory.BatchRepository;
import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcBatchRepository implements BatchRepository {

    private static final String COLUMNS = """
            b.id, b.product_id, b.batch_number, b.expiry_date, b.manufacturing_date,
            b.purchase_price_paisa, b.created_at
            """;

    private final ConnectionProvider connections;

    public JdbcBatchRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public Optional<Batch> findByIdentity(
            TransactionContext transaction, UUID productId, String batchNumber, LocalDate expiryDate) {
        String sql = "SELECT " + COLUMNS + " FROM product_batch b "
                + "WHERE b.product_id = ? AND b.batch_number = ? COLLATE NOCASE AND b.expiry_date = ?";
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, productId.toString());
            statement.setString(2, batchNumber);
            statement.setString(3, expiryDate.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not find product batch.", exception);
        }
    }

    @Override
    public void insert(TransactionContext transaction, Batch batch) {
        String sql = """
                INSERT INTO product_batch (
                    id, product_id, batch_number, expiry_date, manufacturing_date,
                    purchase_price_paisa, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, batch.id().toString());
            statement.setString(2, batch.productId().toString());
            statement.setString(3, batch.batchNumber());
            statement.setString(4, batch.expiryDate().toString());
            if (batch.manufacturingDate() == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, batch.manufacturingDate().toString());
            }
            statement.setLong(6, batch.purchasePricePaisa());
            statement.setString(7, batch.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create product batch.", exception);
        }
    }

    @Override
    public Optional<BatchStock> findByIdWithStock(TransactionContext transaction, UUID batchId) {
        String sql = """
                SELECT %s, COALESCE(s.quantity_base_units, 0) AS quantity_base_units
                FROM product_batch b
                LEFT JOIN batch_stock s ON s.batch_id = b.id
                WHERE b.id = ?
                """.formatted(COLUMNS);
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, batchId.toString());
            try (var results = statement.executeQuery()) {
                return results.next()
                        ? Optional.of(new BatchStock(map(results), results.getLong("quantity_base_units")))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load batch stock.", exception);
        }
    }

    @Override
    public List<BatchStock> findAvailableByProduct(UUID productId, LocalDate asOfDate) {
        String sql = """
                SELECT %s, s.quantity_base_units
                FROM product_batch b
                JOIN product p ON p.id = b.product_id AND p.is_active = 1
                JOIN batch_stock s ON s.batch_id = b.id
                WHERE b.product_id = ?
                  AND b.expiry_date >= ?
                  AND s.quantity_base_units > 0
                ORDER BY b.expiry_date ASC, b.created_at ASC, b.batch_number COLLATE NOCASE
                """.formatted(COLUMNS);
        List<BatchStock> batches = new ArrayList<>();
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, productId.toString());
            statement.setString(2, asOfDate.toString());
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    batches.add(new BatchStock(map(results), results.getLong("quantity_base_units")));
                }
            }
            return batches;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load available batches.", exception);
        }
    }

    @Override
    public long count() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM product_batch");
             var results = statement.executeQuery()) {
            results.next();
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not count batches.", exception);
        }
    }

    private static Batch map(ResultSet results) throws SQLException {
        String manufacturingDate = results.getString("manufacturing_date");
        return new Batch(
                UUID.fromString(results.getString("id")),
                UUID.fromString(results.getString("product_id")),
                results.getString("batch_number"),
                LocalDate.parse(results.getString("expiry_date")),
                manufacturingDate == null ? null : LocalDate.parse(manufacturingDate),
                results.getLong("purchase_price_paisa"),
                Instant.parse(results.getString("created_at")));
    }
}
