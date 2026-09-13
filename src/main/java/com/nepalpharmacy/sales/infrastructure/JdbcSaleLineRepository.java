package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.sales.SaleLine;
import com.nepalpharmacy.sales.SaleLineRepository;
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

public final class JdbcSaleLineRepository implements SaleLineRepository {

    private final ConnectionProvider connections;

    public JdbcSaleLineRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(TransactionContext transaction, SaleLine line) {
        String sql = """
                INSERT INTO sale_line (
                    id, sale_id, batch_id, quantity_sold_base_units,
                    unit_sale_price_paisa, line_total_paisa
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, line.id().toString());
            statement.setString(2, line.saleId().toString());
            statement.setString(3, line.batchId().toString());
            statement.setInt(4, line.quantitySoldBaseUnits());
            statement.setLong(5, line.unitSalePricePaisa());
            statement.setLong(6, line.lineTotalPaisa());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create sale line.", exception);
        }
    }

    @Override
    public Optional<SaleLine> findById(TransactionContext transaction, UUID id) {
        return findById(JdbcTransactionContext.connection(transaction), id);
    }

    @Override
    public Optional<SaleLine> findById(UUID id) {
        try (Connection connection = connections.open()) {
            return findById(connection, id);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not find sale line.", exception);
        }
    }

    private Optional<SaleLine> findById(Connection connection, UUID id) {
        String sql = """
                SELECT id, sale_id, batch_id, quantity_sold_base_units,
                       unit_sale_price_paisa, line_total_paisa
                FROM sale_line WHERE id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not find sale line.", exception);
        }
    }

    @Override
    public List<SaleLine> findBySaleId(TransactionContext transaction, UUID saleId) {
        return findBySaleId(JdbcTransactionContext.connection(transaction), saleId);
    }

    @Override
    public List<SaleLine> findBySaleId(UUID saleId) {
        try (Connection connection = connections.open()) {
            return findBySaleId(connection, saleId);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not list sale lines.", exception);
        }
    }

    private List<SaleLine> findBySaleId(Connection connection, UUID saleId) {
        String sql = """
                SELECT id, sale_id, batch_id, quantity_sold_base_units,
                       unit_sale_price_paisa, line_total_paisa
                FROM sale_line WHERE sale_id = ? ORDER BY rowid
                """;
        List<SaleLine> lines = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, saleId.toString());
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    lines.add(map(results));
                }
            }
            return lines;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not list sale lines.", exception);
        }
    }

    @Override
    public long count() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM sale_line");
             var results = statement.executeQuery()) {
            results.next();
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not count sale lines.", exception);
        }
    }

    private static SaleLine map(ResultSet results) throws SQLException {
        return new SaleLine(
                UUID.fromString(results.getString("id")),
                UUID.fromString(results.getString("sale_id")),
                UUID.fromString(results.getString("batch_id")),
                results.getInt("quantity_sold_base_units"),
                results.getLong("unit_sale_price_paisa"),
                results.getLong("line_total_paisa"));
    }
}
