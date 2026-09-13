package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.sales.PaymentMethod;
import com.nepalpharmacy.sales.SaleDetail;
import com.nepalpharmacy.sales.SaleDetailLine;
import com.nepalpharmacy.sales.SaleHistoryRepository;
import com.nepalpharmacy.sales.SaleReturnStatus;
import com.nepalpharmacy.sales.SaleSearchCriteria;
import com.nepalpharmacy.sales.SaleSummary;
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

public final class JdbcSaleHistoryRepository implements SaleHistoryRepository {

    private static final String RETURNED_LINES = """
            WITH returned AS (
                SELECT original_sale_line_id,
                       SUM(quantity_returned_base_units) AS returned_quantity
                FROM sales_return_line
                GROUP BY original_sale_line_id
            )
            """;

    private final ConnectionProvider connections;

    public JdbcSaleHistoryRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public List<SaleSummary> search(SaleSearchCriteria criteria, int limit) {
        StringBuilder sql = new StringBuilder(RETURNED_LINES).append("""
                SELECT s.id, s.invoice_number, s.sale_date,
                       COALESCE(c.name, 'Walk-in') AS customer_name,
                       s.payment_method, s.total_amount_paisa,
                       CASE
                           WHEN COALESCE(SUM(returned.returned_quantity), 0) = 0 THEN 'NONE'
                           WHEN COALESCE(SUM(returned.returned_quantity), 0)
                                >= SUM(sl.quantity_sold_base_units) THEN 'FULL'
                           ELSE 'PARTIAL'
                       END AS return_status
                FROM sale s
                LEFT JOIN customer c ON c.id = s.customer_id
                JOIN sale_line sl ON sl.sale_id = s.id
                LEFT JOIN returned ON returned.original_sale_line_id = sl.id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (criteria.invoiceNumber() != null) {
            sql.append(" AND s.invoice_number = ?");
            parameters.add(criteria.invoiceNumber());
        }
        if (criteria.fromDate() != null) {
            sql.append(" AND s.sale_date >= ?");
            parameters.add(criteria.fromDate().toString());
        }
        if (criteria.toDate() != null) {
            sql.append(" AND s.sale_date <= ?");
            parameters.add(criteria.toDate().toString());
        }
        if (criteria.customerName() != null) {
            sql.append(" AND lower(COALESCE(c.name, 'Walk-in')) LIKE ?");
            parameters.add("%" + criteria.customerName().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (criteria.paymentMethod() != null) {
            sql.append(" AND s.payment_method = ?");
            parameters.add(criteria.paymentMethod().name());
        }
        sql.append("""
                 GROUP BY s.id, s.invoice_number, s.sale_date, c.name,
                          s.payment_method, s.total_amount_paisa, s.created_at
                 ORDER BY s.sale_date DESC, s.created_at DESC, s.invoice_number DESC
                 LIMIT ?
                """);
        parameters.add(limit);

        List<SaleSummary> sales = new ArrayList<>();
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    sales.add(mapSummary(results));
                }
            }
            return sales;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not search sales history.", exception);
        }
    }

    @Override
    public Optional<SaleDetail> findDetail(UUID saleId) {
        try (Connection connection = connections.open()) {
            Optional<SaleSummary> summary = findSummary(connection, saleId);
            if (summary.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new SaleDetail(
                    summary.orElseThrow(), findLines(connection, saleId)));
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load historical sale.", exception);
        }
    }

    private Optional<SaleSummary> findSummary(Connection connection, UUID saleId)
            throws SQLException {
        String sql = RETURNED_LINES + """
                SELECT s.id, s.invoice_number, s.sale_date,
                       COALESCE(c.name, 'Walk-in') AS customer_name,
                       s.payment_method, s.total_amount_paisa,
                       CASE
                           WHEN COALESCE(SUM(returned.returned_quantity), 0) = 0 THEN 'NONE'
                           WHEN COALESCE(SUM(returned.returned_quantity), 0)
                                >= SUM(sl.quantity_sold_base_units) THEN 'FULL'
                           ELSE 'PARTIAL'
                       END AS return_status
                FROM sale s
                LEFT JOIN customer c ON c.id = s.customer_id
                JOIN sale_line sl ON sl.sale_id = s.id
                LEFT JOIN returned ON returned.original_sale_line_id = sl.id
                WHERE s.id = ?
                GROUP BY s.id, s.invoice_number, s.sale_date, c.name,
                         s.payment_method, s.total_amount_paisa
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, saleId.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapSummary(results)) : Optional.empty();
            }
        }
    }

    private List<SaleDetailLine> findLines(Connection connection, UUID saleId)
            throws SQLException {
        String sql = RETURNED_LINES + """
                SELECT sl.id, p.name AS product_name, b.batch_number, b.expiry_date,
                       sl.quantity_sold_base_units, sl.unit_sale_price_paisa,
                       sl.line_total_paisa,
                       COALESCE(returned.returned_quantity, 0) AS returned_quantity
                FROM sale_line sl
                JOIN product_batch b ON b.id = sl.batch_id
                JOIN product p ON p.id = b.product_id
                LEFT JOIN returned ON returned.original_sale_line_id = sl.id
                WHERE sl.sale_id = ?
                ORDER BY sl.rowid
                """;
        List<SaleDetailLine> lines = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, saleId.toString());
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    int sold = results.getInt("quantity_sold_base_units");
                    long returned = results.getLong("returned_quantity");
                    lines.add(new SaleDetailLine(
                            UUID.fromString(results.getString("id")),
                            results.getString("product_name"),
                            results.getString("batch_number"),
                            LocalDate.parse(results.getString("expiry_date")),
                            sold,
                            results.getLong("unit_sale_price_paisa"),
                            results.getLong("line_total_paisa"),
                            returned,
                            sold - returned));
                }
            }
        }
        return lines;
    }

    private static SaleSummary mapSummary(ResultSet results) throws SQLException {
        return new SaleSummary(
                UUID.fromString(results.getString("id")),
                results.getLong("invoice_number"),
                LocalDate.parse(results.getString("sale_date")),
                results.getString("customer_name"),
                PaymentMethod.valueOf(results.getString("payment_method")),
                results.getLong("total_amount_paisa"),
                SaleReturnStatus.valueOf(results.getString("return_status")));
    }

    private static void bind(
            java.sql.PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }
}
