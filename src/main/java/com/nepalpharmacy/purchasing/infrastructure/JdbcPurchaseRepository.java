package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.purchasing.Purchase;
import com.nepalpharmacy.purchasing.PurchaseRepository;
import com.nepalpharmacy.purchasing.RecentPurchase;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcPurchaseRepository implements PurchaseRepository {

    private final ConnectionProvider connections;

    public JdbcPurchaseRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(TransactionContext transaction, Purchase purchase) {
        String sql = """
                INSERT INTO purchase (
                    id, supplier_id, purchase_date, invoice_number,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement(sql)) {
            statement.setString(1, purchase.id().toString());
            statement.setString(2, purchase.supplierId().toString());
            statement.setString(3, purchase.purchaseDate().toString());
            setNullableString(statement, 4, purchase.invoiceNumber());
            statement.setLong(5, purchase.totalAmountPaisa());
            statement.setString(6, purchase.createdAt().toString());
            setNullableString(statement, 7,
                    purchase.createdBy() == null ? null : purchase.createdBy().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not create purchase.", exception);
        }
    }

    @Override
    public List<RecentPurchase> findRecent(int limit) {
        String sql = """
                SELECT p.id, p.purchase_date, s.name AS supplier_name,
                       p.invoice_number, p.total_amount_paisa
                FROM purchase p
                JOIN supplier s ON s.id = p.supplier_id
                ORDER BY p.purchase_date DESC, p.created_at DESC
                LIMIT ?
                """;
        List<RecentPurchase> purchases = new ArrayList<>();
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    purchases.add(new RecentPurchase(
                            UUID.fromString(results.getString("id")),
                            LocalDate.parse(results.getString("purchase_date")),
                            results.getString("supplier_name"),
                            results.getString("invoice_number"),
                            results.getLong("total_amount_paisa")));
                }
            }
            return purchases;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not list recent purchases.", exception);
        }
    }

    @Override
    public long count() {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM purchase");
             var results = statement.executeQuery()) {
            results.next();
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not count purchases.", exception);
        }
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
