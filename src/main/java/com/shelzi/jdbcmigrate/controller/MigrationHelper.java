package com.shelzi.jdbcmigrate.controller;

import com.shelzi.jdbcmigrate.entity.MigrationHistory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MigrationHelper {
    private final Connection connection;

    public MigrationHelper(Connection connection) {
        this.connection = connection;
    }

    void ensureMigrationTableExists() throws SQLException {
        String createTableSQL;
        String dbProductName = connection.getMetaData().getDatabaseProductName().toLowerCase();

        if (dbProductName.contains("postgresql")) {
            createTableSQL = """
            CREATE TABLE IF NOT EXISTS migration_history (
                id SERIAL PRIMARY KEY,
                version VARCHAR(100) NOT NULL UNIQUE,
                applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                successful BOOLEAN NOT NULL DEFAULT FALSE,
                checksum VARCHAR(64)
            );
        """;
        } else if (dbProductName.contains("mysql")) {
            createTableSQL = """
            CREATE TABLE IF NOT EXISTS migration_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                version VARCHAR(100) NOT NULL UNIQUE,
                applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                successful BOOLEAN NOT NULL DEFAULT FALSE,
                checksum VARCHAR(64)
            );
        """;
        } else if (dbProductName.contains("h2")) {
            createTableSQL = """
            CREATE TABLE IF NOT EXISTS migration_history (
                id INTEGER AUTO_INCREMENT PRIMARY KEY,
                version VARCHAR(100) NOT NULL UNIQUE,
                applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
                successful BOOLEAN NOT NULL DEFAULT FALSE,
                checksum VARCHAR(64)
            );
        """;
        } else {
            // По умолчанию используем синтаксис для MySQL
            createTableSQL = """
            CREATE TABLE IF NOT EXISTS migration_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                version VARCHAR(100) NOT NULL UNIQUE,
                applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                successful BOOLEAN NOT NULL DEFAULT FALSE,
                checksum VARCHAR(64)
            );
        """;
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    List<MigrationHistory> getAppliedMigrations() throws SQLException {
        String query = "SELECT id, version, applied_at, successful, checksum FROM migration_history";
        List<MigrationHistory> appliedMigrations = new ArrayList<>();

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                int id = rs.getInt("id");
                String version = rs.getString("version");
                Timestamp appliedAt = rs.getTimestamp("applied_at");
                boolean successful = rs.getBoolean("successful");
                String checksum = rs.getString("checksum");

                MigrationHistory migrationHistory = new MigrationHistory(id, version, appliedAt, successful, checksum);
                appliedMigrations.add(migrationHistory);
            }
        }
        return appliedMigrations;
    }

}
