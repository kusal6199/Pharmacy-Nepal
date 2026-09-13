package com.nepalpharmacy.product.infrastructure;

import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductCategory;
import com.nepalpharmacy.product.ProductNotFoundException;
import com.nepalpharmacy.product.ProductRepository;
import com.nepalpharmacy.product.ProductRepositoryException;
import com.nepalpharmacy.product.UnitOfSale;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcProductRepository implements ProductRepository {

    private static final String COLUMNS = """
            id, name, generic_name, manufacturer, category, unit_of_sale, pack_size,
            purchase_price_paisa, sale_price_paisa, mrp_paisa, tax_rate_basis_points,
            reorder_threshold_base_units, is_active, created_at, updated_at
            """;

    private final ConnectionProvider connections;

    public JdbcProductRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(Product product) {
        String sql = """
                INSERT INTO product (
                    id, name, generic_name, manufacturer, category, unit_of_sale, pack_size,
                    purchase_price_paisa, sale_price_paisa, mrp_paisa, tax_rate_basis_points,
                    reorder_threshold_base_units, is_active, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (var connection = connections.open();
             var statement = connection.prepareStatement(sql)) {
            bind(statement, product);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ProductRepositoryException("Could not create product.", exception);
        }
    }

    @Override
    public void update(Product product) {
        String sql = """
                UPDATE product SET
                    name = ?, generic_name = ?, manufacturer = ?, category = ?, unit_of_sale = ?,
                    pack_size = ?, purchase_price_paisa = ?, sale_price_paisa = ?, mrp_paisa = ?,
                    tax_rate_basis_points = ?, reorder_threshold_base_units = ?, is_active = ?,
                    updated_at = ?
                WHERE id = ?
                """;

        try (var connection = connections.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.name());
            setNullableString(statement, 2, product.genericName());
            setNullableString(statement, 3, product.manufacturer());
            statement.setString(4, product.category().name());
            statement.setString(5, product.unitOfSale().name());
            setNullableInteger(statement, 6, product.packSize());
            statement.setLong(7, product.purchasePricePaisa());
            statement.setLong(8, product.salePricePaisa());
            setNullableLong(statement, 9, product.mrpPaisa());
            statement.setInt(10, product.taxRateBasisPoints());
            statement.setInt(11, product.reorderThresholdBaseUnits());
            statement.setInt(12, product.active() ? 1 : 0);
            statement.setString(13, product.updatedAt().toString());
            statement.setString(14, product.id().toString());

            if (statement.executeUpdate() != 1) {
                throw new ProductNotFoundException(product.id());
            }
        } catch (ProductNotFoundException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new ProductRepositoryException("Could not update product.", exception);
        }
    }

    @Override
    public Optional<Product> findById(UUID id) {
        try (var connection = connections.open()) {
            return findById(connection, id);
        } catch (SQLException exception) {
            throw new ProductRepositoryException("Could not load product.", exception);
        }
    }

    @Override
    public Optional<Product> findById(TransactionContext transaction, UUID id) {
        try {
            return findById(JdbcTransactionContext.connection(transaction), id);
        } catch (SQLException exception) {
            throw new ProductRepositoryException("Could not load product.", exception);
        }
    }

    private Optional<Product> findById(java.sql.Connection connection, UUID id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM product WHERE id = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Product> findAll() {
        String sql = "SELECT " + COLUMNS
                + " FROM product ORDER BY is_active DESC, name COLLATE NOCASE, manufacturer COLLATE NOCASE";
        List<Product> products = new ArrayList<>();

        try (var connection = connections.open();
             var statement = connection.prepareStatement(sql);
             var results = statement.executeQuery()) {
            while (results.next()) {
                products.add(map(results));
            }
            return products;
        } catch (SQLException exception) {
            throw new ProductRepositoryException("Could not list products.", exception);
        }
    }

    @Override
    public List<Product> searchActiveByName(String query, int limit) {
        String sql = "SELECT " + COLUMNS + " FROM product "
                + "WHERE is_active = 1 AND instr(lower(name), lower(?)) > 0 "
                + "ORDER BY name COLLATE NOCASE, manufacturer COLLATE NOCASE LIMIT ?";
        List<Product> products = new ArrayList<>();
        try (var connection = connections.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, query);
            statement.setInt(2, limit);
            try (var results = statement.executeQuery()) {
                while (results.next()) {
                    products.add(map(results));
                }
            }
            return products;
        } catch (SQLException exception) {
            throw new ProductRepositoryException("Could not search products.", exception);
        }
    }

    @Override
    public boolean existsActiveWithNameAndManufacturer(
            String name,
            String manufacturer,
            UUID excludedProductId
    ) {
        String sql = """
                SELECT 1
                FROM product
                WHERE is_active = 1
                  AND lower(trim(name)) = lower(trim(?))
                  AND lower(trim(coalesce(manufacturer, ''))) = lower(trim(coalesce(?, '')))
                  AND (? IS NULL OR id <> ?)
                LIMIT 1
                """;

        try (var connection = connections.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            setNullableString(statement, 2, manufacturer);
            String excludedId = excludedProductId == null ? null : excludedProductId.toString();
            setNullableString(statement, 3, excludedId);
            setNullableString(statement, 4, excludedId);
            try (var results = statement.executeQuery()) {
                return results.next();
            }
        } catch (SQLException exception) {
            throw new ProductRepositoryException("Could not check for duplicate product.", exception);
        }
    }

    private static void bind(PreparedStatement statement, Product product) throws SQLException {
        statement.setString(1, product.id().toString());
        statement.setString(2, product.name());
        setNullableString(statement, 3, product.genericName());
        setNullableString(statement, 4, product.manufacturer());
        statement.setString(5, product.category().name());
        statement.setString(6, product.unitOfSale().name());
        setNullableInteger(statement, 7, product.packSize());
        statement.setLong(8, product.purchasePricePaisa());
        statement.setLong(9, product.salePricePaisa());
        setNullableLong(statement, 10, product.mrpPaisa());
        statement.setInt(11, product.taxRateBasisPoints());
        statement.setInt(12, product.reorderThresholdBaseUnits());
        statement.setInt(13, product.active() ? 1 : 0);
        statement.setString(14, product.createdAt().toString());
        statement.setString(15, product.updatedAt().toString());
    }

    private static Product map(ResultSet results) throws SQLException {
        return new Product(
                UUID.fromString(results.getString("id")),
                results.getString("name"),
                results.getString("generic_name"),
                results.getString("manufacturer"),
                ProductCategory.valueOf(results.getString("category")),
                UnitOfSale.valueOf(results.getString("unit_of_sale")),
                nullableInteger(results, "pack_size"),
                results.getLong("purchase_price_paisa"),
                results.getLong("sale_price_paisa"),
                nullableLong(results, "mrp_paisa"),
                results.getInt("tax_rate_basis_points"),
                results.getInt("reorder_threshold_base_units"),
                results.getInt("is_active") == 1,
                Instant.parse(results.getString("created_at")),
                Instant.parse(results.getString("updated_at"))
        );
    }

    private static Integer nullableInteger(ResultSet results, String column) throws SQLException {
        int value = results.getInt(column);
        return results.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet results, String column) throws SQLException {
        long value = results.getLong(column);
        return results.wasNull() ? null : value;
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }
}
