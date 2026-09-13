package com.nepalpharmacy.shared.infrastructure;

import com.nepalpharmacy.shared.persistence.TransactionRunner;
import com.nepalpharmacy.shared.persistence.TransactionWork;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class JdbcTransactionRunner implements TransactionRunner {

    private final ConnectionProvider connections;

    public JdbcTransactionRunner(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public <T> T inTransaction(TransactionWork<T> work) {
        Objects.requireNonNull(work, "work");
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(new JdbcTransactionContext(connection));
                connection.commit();
                return result;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not complete database transaction.", exception);
        }
    }

    private static void rollback(Connection connection, RuntimeException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }
}
