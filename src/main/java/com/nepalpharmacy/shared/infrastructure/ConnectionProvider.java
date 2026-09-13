package com.nepalpharmacy.shared.infrastructure;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionProvider {

    Connection open() throws SQLException;
}
