package com.nepalpharmacy.party.infrastructure;

import com.nepalpharmacy.party.Customer;
import com.nepalpharmacy.party.CustomerRepository;
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

public final class JdbcCustomerRepository implements CustomerRepository {

    private static final String COLUMNS =
            "id, name, phone, address, is_active, created_at, updated_at";

    private final ConnectionProvider connections;

    public JdbcCustomerRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(Customer customer) {
        String sql = """
                INSERT INTO customer (
                    id, name, phone, address, is_active, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, customer.id().toString());
            statement.setString(2, customer.name());
            setNullableString(statement, 3, customer.phone());
            setNullableString(statement, 4, customer.address());
            statement.setInt(5, customer.active() ? 1 : 0);
            statement.setString(6, customer.createdAt().toString());
            statement.setString(7, customer.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create customer.", exception);
        }
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        try (Connection connection = connections.open()) {
            return findById(connection, id);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load customer.", exception);
        }
    }

    @Override
    public Optional<Customer> findById(TransactionContext transaction, UUID id) {
        try {
            return findById(JdbcTransactionContext.connection(transaction), id);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load customer.", exception);
        }
    }

    @Override
    public List<Customer> findAllActive() {
        String sql = "SELECT " + COLUMNS
                + " FROM customer WHERE is_active = 1 ORDER BY name COLLATE NOCASE";
        List<Customer> customers = new ArrayList<>();
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql);
             var results = statement.executeQuery()) {
            while (results.next()) {
                customers.add(map(results));
            }
            return customers;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not list customers.", exception);
        }
    }

    private Optional<Customer> findById(Connection connection, UUID id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM customer WHERE id = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    private static Customer map(ResultSet results) throws SQLException {
        return new Customer(
                UUID.fromString(results.getString("id")),
                results.getString("name"),
                results.getString("phone"),
                results.getString("address"),
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
