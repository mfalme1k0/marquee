package com.marquee.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseInitializerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private DatabaseInitializer databaseInitializer;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(
                tempDir.resolve("marquee.db")
        );

        databaseInitializer = new DatabaseInitializer(databaseManager);
    }

    @Test
    void shouldCreateAllDatabaseTables() throws Exception {
        databaseInitializer.initialize();

        assertTrue(tableExists("films"));
        assertTrue(tableExists("genres"));
        assertTrue(tableExists("film_genres"));
        assertTrue(tableExists("screenings"));
    }

    @Test
    void shouldCreateAllDatabaseIndexes() throws Exception {
        databaseInitializer.initialize();

        assertTrue(indexExists("idx_film_genres_genre"));
        assertTrue(indexExists("idx_screenings_film"));
        assertTrue(indexExists("idx_screenings_venue"));
        assertTrue(indexExists("idx_screenings_time"));
    }

    @Test
    void shouldInsertSeedDataWhenInitializingFreshDatabase()
            throws Exception {

        databaseInitializer.initialize();

        assertEquals(15, countRows("films"));
        assertEquals(10, countRows("genres"));
        assertEquals(38, countRows("film_genres"));
        assertEquals(15, countRows("screenings"));
    }

    @Test
    void shouldNotInsertDuplicateSeedDataWhenInitializingExistingDatabase()
            throws Exception {

        databaseInitializer.initialize();

        databaseInitializer.initialize();

        assertEquals(15, countRows("films"));
        assertEquals(10, countRows("genres"));
        assertEquals(38, countRows("film_genres"));
        assertEquals(15, countRows("screenings"));
    }

    @Test
    void shouldPreserveExistingRecordsWhenInitializingExistingDatabase()
            throws Exception {

        databaseInitializer.initialize();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO films (title, year, description)
                     VALUES (?, ?, ?)
                     """)) {

            statement.setString(1, "Custom Test Film");
            statement.setInt(2, 2026);
            statement.setString(3, "A film created during testing.");

            statement.executeUpdate();
        }

        databaseInitializer.initialize();

        assertEquals(16, countRows("films"));
        assertEquals(1, countRows(
                "films",
                "title = 'Custom Test Film'"
        ));
    }

    private boolean tableExists(String tableName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM sqlite_schema
                    WHERE type = 'table'
                      AND name = ?
                )
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());

                return resultSet.getBoolean(1);
            }
        }
    }

    private boolean indexExists(String indexName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM sqlite_schema
                    WHERE type = 'index'
                      AND name = ?
                )
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, indexName);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());

                return resultSet.getBoolean(1);
            }
        }
    }

    private int countRows(String tableName) throws SQLException {
        return countRows(tableName, null);
    }

    private int countRows(String tableName, String condition)
            throws SQLException {

        String sql = "SELECT COUNT(*) FROM " + tableName;

        if (condition != null) {
            sql += " WHERE " + condition;
        }

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            assertTrue(resultSet.next());

            return resultSet.getInt(1);
        }
    }
}