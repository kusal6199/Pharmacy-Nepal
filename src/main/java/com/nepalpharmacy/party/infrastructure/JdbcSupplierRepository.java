package com.nepalpharmacy.party.infrastructure;

import com.nepalpharmacy.party.Supplier;
import com.nepalpharmacy.party.SupplierRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcSupplierRepository implements SupplierRepository {

    private static final String COLUMNS =
            "id, name, phone, address, pan, is_active, created_at, updated_at";

    private final ConnectionProvider connections;

    public JdbcSupplierRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(Supplier supplier) {
        try (Connection connection = connections.open()) {
            insert(connection, supplier);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create supplier.", exception);
        }
    }

    public void insert(Connection connection, Supplier supplier) throws SQLException {
        String sql = """
                INSERT INTO supplier (
                    id, name, phone, address, pan, is_active, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, supplier.id().toString());
            statement.setString(2, supplier.name());
            setNullableString(statement, 3, supplier.phone());
            setNullableString(statement, 4, supplier.address());
            setNullableString(statement, 5, supplier.pan());
            statement.setInt(6, supplier.active() ? 1 : 0);
            statement.setString(7, supplier.createdAt().toString());
            statement.setString(8, supplier.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Supplier> findById(UUID id) {
        try (Connection connection = connections.open()) {
            return findById(connection, id);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load supplier.", exception);
        }
    }

    @Override
    public Optional<Supplier> findById(TransactionContext transaction, UUID id) {
        try {
            return findById(JdbcTransactionContext.connection(transaction), id);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load supplier.", exception);
        }
    }

    private Optional<Supplier> findById(Connection connection, UUID id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM supplier WHERE id = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Supplier> findAllActive() {
        String sql = "SELECT " + COLUMNS
                + " FROM supplier WHERE is_active = 1 ORDER BY name COLLATE NOCASE";
        List<Supplier> suppliers = new ArrayList<>();
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql);
             var results = statement.executeQuery()) {
            while (results.next()) {
                suppliers.add(map(results));
            }
            return suppliers;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not list suppliers.", exception);
        }
    }

    private static Supplier map(ResultSet results) throws SQLException {
        return new Supplier(
                UUID.fromString(results.getString("id")),
                results.getString("name"),
                results.getString("phone"),
                results.getString("address"),
                results.getString("pan"),
                results.getInt("is_active") == 1,
                Instant.parse(results.getString("created_at")),
                Instant.parse(results.getString("updated_at")));
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
