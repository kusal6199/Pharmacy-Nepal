package com.nepalpharmacy.inventory.infrastructure;

import com.nepalpharmacy.inventory.ExpiryBatchAlert;
import com.nepalpharmacy.inventory.ExpiryHorizon;
import com.nepalpharmacy.inventory.ExpiryStatus;
import com.nepalpharmacy.inventory.InventoryAlertCriteria;
import com.nepalpharmacy.inventory.InventoryAlertRepository;
import com.nepalpharmacy.inventory.InventoryAlertSummary;
import com.nepalpharmacy.inventory.ProductStockAlert;
import com.nepalpharmacy.inventory.ProductStockFilter;
import com.nepalpharmacy.inventory.ProductStockStatus;
import com.nepalpharmacy.product.UnitOfSale;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcInventoryAlertRepository implements InventoryAlertRepository {

    private static final String PRODUCT_STOCK_CTES = ("""
            WITH product_stock AS (
                SELECT p.id AS product_id, p.name AS product_name, p.generic_name,
                       p.manufacturer, p.unit_of_sale, p.reorder_threshold_base_units,
                       COALESCE(SUM(COALESCE(s.quantity_base_units, 0)), 0)
                           AS physical_stock,
                       COALESCE(SUM(CASE WHEN %s
                                         THEN s.quantity_base_units ELSE 0 END), 0)
                           AS sellable_stock,
                       COALESCE(SUM(CASE WHEN %s
                                         THEN s.quantity_base_units ELSE 0 END), 0)
                           AS expired_stock
                FROM product p
                LEFT JOIN product_batch b ON b.product_id = p.id
                LEFT JOIN batch_stock s ON s.batch_id = b.id
                WHERE p.is_active = 1
                GROUP BY p.id, p.name, p.generic_name, p.manufacturer, p.unit_of_sale,
                         p.reorder_threshold_base_units
            ),
            stock_status AS (
                SELECT product_stock.*,
                       CASE
                           WHEN sellable_stock <= 0 THEN 'OUT_OF_STOCK'
                           WHEN reorder_threshold_base_units > 0
                                AND sellable_stock <= reorder_threshold_base_units
                               THEN 'LOW_STOCK'
                           ELSE 'OK'
                       END AS status
                FROM product_stock
            )
            """).formatted(
            InventoryStockSql.SELLABLE_ON_DATE,
            InventoryStockSql.POSITIVE_EXPIRED_STOCK);

    private final ConnectionProvider connections;

    public JdbcInventoryAlertRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public InventoryAlertSummary loadSummary(LocalDate asOfDate) {
        try (Connection connection = connections.open()) {
            int[] expiryCounts = loadExpiryCounts(connection, asOfDate);
            int[] stockCounts = loadProductStockCounts(connection, asOfDate);
            return new InventoryAlertSummary(
                    expiryCounts[0], expiryCounts[1], expiryCounts[2], expiryCounts[3],
                    stockCounts[0], stockCounts[1]);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load inventory alert summary.", exception);
        }
    }

    @Override
    public List<ExpiryBatchAlert> findExpiryAlerts(
            LocalDate asOfDate, InventoryAlertCriteria criteria, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT b.id AS batch_id, p.name AS product_name, p.generic_name,
                       p.manufacturer, p.unit_of_sale, p.is_active,
                       b.batch_number, b.expiry_date, s.quantity_base_units
                FROM product_batch b
                JOIN product p ON p.id = b.product_id
                JOIN batch_stock s ON s.batch_id = b.id
                WHERE
                """).append(InventoryStockSql.POSITIVE_PHYSICAL_STOCK);
        List<Object> parameters = new ArrayList<>();
        appendExpiryHorizon(sql, parameters, criteria.expiryHorizon(), asOfDate);
        if (criteria.expiryText() != null) {
            sql.append("""
                     AND (
                         instr(lower(p.name), lower(?)) > 0
                         OR instr(lower(COALESCE(p.generic_name, '')), lower(?)) > 0
                         OR instr(lower(COALESCE(p.manufacturer, '')), lower(?)) > 0
                     )
                    """);
            parameters.add(criteria.expiryText());
            parameters.add(criteria.expiryText());
            parameters.add(criteria.expiryText());
        }
        sql.append("""
                 ORDER BY CASE WHEN b.expiry_date < ? THEN 0 ELSE 1 END,
                          b.expiry_date ASC, p.name COLLATE NOCASE,
                          b.batch_number COLLATE NOCASE
                 LIMIT ?
                """);
        parameters.add(asOfDate.toString());
        parameters.add(limit);

        List<ExpiryBatchAlert> alerts = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    LocalDate expiryDate = LocalDate.parse(results.getString("expiry_date"));
                    alerts.add(new ExpiryBatchAlert(
                            UUID.fromString(results.getString("batch_id")),
                            ExpiryStatus.classify(asOfDate, expiryDate),
                            results.getString("product_name"),
                            results.getString("generic_name"),
                            results.getString("manufacturer"),
                            results.getString("batch_number"),
                            expiryDate,
                            ChronoUnit.DAYS.between(asOfDate, expiryDate),
                            results.getLong("quantity_base_units"),
                            UnitOfSale.valueOf(results.getString("unit_of_sale")),
                            results.getInt("is_active") == 1));
                }
            }
            return alerts;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load expiry alerts.", exception);
        }
    }

    @Override
    public List<ProductStockAlert> findProductStockAlerts(
            LocalDate asOfDate, InventoryAlertCriteria criteria, int limit) {
        StringBuilder sql = new StringBuilder(PRODUCT_STOCK_CTES).append("""
                SELECT product_id, product_name, generic_name, manufacturer, unit_of_sale,
                       reorder_threshold_base_units, physical_stock, sellable_stock,
                       expired_stock, status
                FROM stock_status
                WHERE
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(asOfDate.toString());
        parameters.add(asOfDate.toString());
        appendProductStatusFilter(sql, criteria.productStockFilter());
        if (criteria.productText() != null) {
            sql.append(" AND instr(lower(product_name), lower(?)) > 0");
            parameters.add(criteria.productText());
        }
        sql.append("""
                 ORDER BY CASE status WHEN 'OUT_OF_STOCK' THEN 0 ELSE 1 END,
                          (sellable_stock - reorder_threshold_base_units) ASC,
                          product_name COLLATE NOCASE
                 LIMIT ?
                """);
        parameters.add(limit);

        List<ProductStockAlert> alerts = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    alerts.add(new ProductStockAlert(
                            UUID.fromString(results.getString("product_id")),
                            ProductStockStatus.valueOf(results.getString("status")),
                            results.getString("product_name"),
                            results.getString("generic_name"),
                            results.getString("manufacturer"),
                            results.getLong("sellable_stock"),
                            results.getLong("physical_stock"),
                            results.getLong("expired_stock"),
                            results.getInt("reorder_threshold_base_units"),
                            UnitOfSale.valueOf(results.getString("unit_of_sale"))));
                }
            }
            return alerts;
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load low-stock alerts.", exception);
        }
    }

    private static int[] loadExpiryCounts(Connection connection, LocalDate asOfDate)
            throws SQLException {
        String sql = ("""
                SELECT
                    COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS expired_count,
                    COALESCE(SUM(CASE WHEN %s AND b.expiry_date <= ?
                                      THEN 1 ELSE 0 END), 0) AS days_0_30_count,
                    COALESCE(SUM(CASE WHEN b.expiry_date >= ? AND b.expiry_date <= ?
                                      THEN 1 ELSE 0 END), 0) AS days_31_60_count,
                    COALESCE(SUM(CASE WHEN b.expiry_date >= ? AND b.expiry_date <= ?
                                      THEN 1 ELSE 0 END), 0) AS days_61_90_count
                FROM product_batch b
                JOIN batch_stock s ON s.batch_id = b.id
                WHERE %s
                """).formatted(
                InventoryStockSql.EXPIRED_ON_DATE,
                InventoryStockSql.NOT_EXPIRED_ON_DATE,
                InventoryStockSql.POSITIVE_PHYSICAL_STOCK);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, asOfDate.toString());
            statement.setString(2, asOfDate.toString());
            statement.setString(3, asOfDate.plusDays(30).toString());
            statement.setString(4, asOfDate.plusDays(31).toString());
            statement.setString(5, asOfDate.plusDays(60).toString());
            statement.setString(6, asOfDate.plusDays(61).toString());
            statement.setString(7, asOfDate.plusDays(90).toString());
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return new int[]{
                        results.getInt("expired_count"),
                        results.getInt("days_0_30_count"),
                        results.getInt("days_31_60_count"),
                        results.getInt("days_61_90_count")};
            }
        }
    }

    private static int[] loadProductStockCounts(Connection connection, LocalDate asOfDate)
            throws SQLException {
        String sql = PRODUCT_STOCK_CTES + """
                SELECT COALESCE(SUM(CASE WHEN status = 'LOW_STOCK' THEN 1 ELSE 0 END), 0)
                           AS low_count,
                       COALESCE(SUM(CASE WHEN status = 'OUT_OF_STOCK' THEN 1 ELSE 0 END), 0)
                           AS out_count
                FROM stock_status
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, asOfDate.toString());
            statement.setString(2, asOfDate.toString());
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return new int[]{results.getInt("low_count"), results.getInt("out_count")};
            }
        }
    }

    private static void appendExpiryHorizon(
            StringBuilder sql,
            List<Object> parameters,
            ExpiryHorizon horizon,
            LocalDate asOfDate
    ) {
        switch (horizon) {
            case ALL -> {
                sql.append(" AND b.expiry_date <= ?");
                parameters.add(asOfDate.plusDays(90).toString());
            }
            case EXPIRED -> {
                sql.append(" AND ").append(InventoryStockSql.EXPIRED_ON_DATE);
                parameters.add(asOfDate.toString());
            }
            case DAYS_0_TO_30 -> {
                sql.append(" AND ").append(InventoryStockSql.NOT_EXPIRED_ON_DATE)
                        .append(" AND b.expiry_date <= ?");
                parameters.add(asOfDate.toString());
                parameters.add(asOfDate.plusDays(30).toString());
            }
            case DAYS_31_TO_60 -> {
                sql.append(" AND b.expiry_date >= ? AND b.expiry_date <= ?");
                parameters.add(asOfDate.plusDays(31).toString());
                parameters.add(asOfDate.plusDays(60).toString());
            }
            case DAYS_61_TO_90 -> {
                sql.append(" AND b.expiry_date >= ? AND b.expiry_date <= ?");
                parameters.add(asOfDate.plusDays(61).toString());
                parameters.add(asOfDate.plusDays(90).toString());
            }
        }
    }

    private static void appendProductStatusFilter(
            StringBuilder sql, ProductStockFilter filter) {
        switch (filter) {
            case ALL -> sql.append("status IN ('OUT_OF_STOCK', 'LOW_STOCK')");
            case OUT_OF_STOCK -> sql.append("status = 'OUT_OF_STOCK'");
            case LOW_STOCK -> sql.append("status = 'LOW_STOCK'");
        }
    }

    private static void bind(PreparedStatement statement, List<Object> parameters)
            throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }
}
