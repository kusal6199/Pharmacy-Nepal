package com.nepalpharmacy.shared.infrastructure;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.Connection;
import java.util.Objects;

public final class JdbcTransactionContext implements TransactionContext {

    private final Connection connection;

    JdbcTransactionContext(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public static Connection connection(TransactionContext context) {
        if (!(context instanceof JdbcTransactionContext jdbcContext)) {
            throw new IllegalArgumentException("Transaction context is not JDBC-backed.");
        }
        return jdbcContext.connection;
    }
}
