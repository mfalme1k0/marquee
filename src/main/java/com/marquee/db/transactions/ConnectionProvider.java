package com.marquee.db.transactions;

import java.sql.Connection;
import java.sql.SQLException;

// Provides configured JDBC connections to database operations.
@FunctionalInterface
public interface ConnectionProvider {

    // Opens and returns a database connection.
    // Throws SQLException if a database connection cannot be established
    Connection getConnection() throws SQLException;
}