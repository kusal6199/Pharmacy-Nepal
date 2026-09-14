package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.purchasing.PurchaseDetail;
import com.nepalpharmacy.purchasing.PurchaseDetailLine;
import com.nepalpharmacy.purchasing.PurchaseHistoryRepository;
import com.nepalpharmacy.purchasing.PurchasePaymentMethod;
import com.nepalpharmacy.purchasing.PurchaseSearchCriteria;
import com.nepalpharmacy.purchasing.PurchaseSummary;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcPurchaseHistoryRepository implements PurchaseHistoryRepository {

    private static final String RETURNED_LINES = """
            WITH returned AS (
                SELECT original_purchase_line_id,
                       SUM(quantity_returned_base_units) AS returned_quantity
                FROM purchase_return_line
                GROUP BY original_purchase_line_id
            )
            """;

    private final ConnectionProvider connections;

    public JdbcPurchaseHistoryRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public List<PurchaseSummary> search(PurchaseSearchCriteria criteria, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.purchase_date, s.name AS supplier_name,
                       p.invoice_number, p.payment_method, p.total_amount_paisa
                FROM purchase p
                JOIN supplier s ON s.id = p.supplier_id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (criteria.fromDate() != null) {
            sql.append(" AND p.purchase_date >= ?");
            parameters.add(criteria.fromDate().toString());
        }
        if (criteria.toDate() != null) {
            sql.append(" AND p.purchase_date <= ?");
            parameters.add(criteria.toDate().toString());
        }
        if (criteria.supplierName() != null) {
            sql.append(" AND lower(s.name) LIKE ?");
            parameters.add("%" + criteria.supplierName().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (criteria.supplierInvoice() != null) {
            sql.append(" AND lower(p.invoice_number) LIKE ?");
            parameters.add("%" + criteria.supplierInvoice().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        sql.append("""
                 ORDER BY p.purchase_date DESC, p.created_at DESC, p.id DESC
                 LIMIT ?
                """);
        parameters.add(limit);

        List<PurchaseSummary> purchases = new ArrayList<>();
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    purchases.add(mapSummary(results));
                }
            }
            return purchases;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not search purchase history.", exception);
        }
    }

    @Override
    public Optional<PurchaseDetail> findDetail(UUID purchaseId) {
        try (Connection connection = connections.open()) {
            Optional<PurchaseSummary> summary = findSummary(connection, purchaseId);
            if (summary.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new PurchaseDetail(
                    summary.orElseThrow(), findLines(connection, purchaseId)));
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load historical purchase.", exception);
        }
    }

    private Optional<PurchaseSummary> findSummary(Connection connection, UUID purchaseId)
            throws SQLException {
        String sql = """
                SELECT p.id, p.purchase_date, s.name AS supplier_name,
                       p.invoice_number, p.payment_method, p.total_amount_paisa
                FROM purchase p
                JOIN supplier s ON s.id = p.supplier_id
                WHERE p.id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, purchaseId.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapSummary(results)) : Optional.empty();
            }
        }
    }

    private List<PurchaseDetailLine> findLines(Connection connection, UUID purchaseId)
            throws SQLException {
        String sql = RETURNED_LINES + """
                SELECT pl.id, product.name AS product_name,
                       batch.batch_number, batch.expiry_date,
                       pl.quantity_received_base_units, pl.unit_purchase_price_paisa,
                       pl.line_total_paisa,
                       COALESCE(returned.returned_quantity, 0) AS returned_quantity,
                       COALESCE(stock.quantity_base_units, 0) AS current_stock
                FROM purchase_line pl
                JOIN product_batch batch ON batch.id = pl.batch_id
                JOIN product ON product.id = batch.product_id
                LEFT JOIN returned ON returned.original_purchase_line_id = pl.id
                LEFT JOIN batch_stock stock ON stock.batch_id = batch.id
                WHERE pl.purchase_id = ?
                ORDER BY pl.rowid
                """;
        List<PurchaseDetailLine> lines = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, purchaseId.toString());
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    int received = results.getInt("quantity_received_base_units");
                    long returned = results.getLong("returned_quantity");
                    lines.add(new PurchaseDetailLine(
                            UUID.fromString(results.getString("id")),
                            results.getString("product_name"),
                            results.getString("batch_number"),
                            LocalDate.parse(results.getString("expiry_date")),
                            received,
                            results.getLong("unit_purchase_price_paisa"),
                            results.getLong("line_total_paisa"),
                            returned,
                            received - returned,
                            results.getLong("current_stock")));
                }
            }
        }
        return lines;
    }

    private static PurchaseSummary mapSummary(ResultSet results) throws SQLException {
        return new PurchaseSummary(
                UUID.fromString(results.getString("id")),
                LocalDate.parse(results.getString("purchase_date")),
                results.getString("supplier_name"),
                results.getString("invoice_number"),
                PurchasePaymentMethod.valueOf(results.getString("payment_method")),
                results.getLong("total_amount_paisa"));
    }

    private static void bind(
            java.sql.PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }
}
