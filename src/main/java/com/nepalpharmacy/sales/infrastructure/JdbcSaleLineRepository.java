package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.sales.SaleLine;
import com.nepalpharmacy.sales.SaleLineRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;

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
}
