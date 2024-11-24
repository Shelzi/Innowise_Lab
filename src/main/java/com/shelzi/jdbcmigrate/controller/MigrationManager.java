package com.shelzi.jdbcmigrate.controller;

import com.shelzi.jdbcmigrate.io.MigrationFileReader;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

import java.sql.*;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MigrationManager {

    private final Connection connection;
    private final String migrationDirectory;
    private final LockExecutor lockExecutor;

    public MigrationManager(Connection connection, String migrationDirectory, Properties properties) {
        this.connection = connection;
        this.migrationDirectory = migrationDirectory; // todo вынести на шаг назад и проверить на null
        this.lockExecutor = new LockExecutor(connection);
    }

    public void applyMigrations() throws SQLException, IOException {
        ensureLockTableExists();
        ensureMigrationTableExists();

        if (acquireLock()) {
            // Создаём планировщик для обновления блокировки
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            Runnable refreshLockTask = () -> {
                try {
                    refreshLock(); //todo добавить лог
                } catch (SQLException e) {
                    e.printStackTrace(); //todo?
                }
            };

            // Запускаем задачу обновления блокировки каждые 2 минут
            scheduler.scheduleAtFixedRate(refreshLockTask, 2, 2, TimeUnit.MINUTES);

            try {
                // Получаем список файлов миграций
                List<Path> migrationFiles = MigrationFileReader.getMigrationFiles(migrationDirectory);

                for (Path filePath : migrationFiles) {
                    String fileName = filePath.getFileName().toString();
                    if (!isMigrationApplied(fileName)) {
                        String sql = MigrationFileReader.readFile(filePath);
                        applyMigration(sql);
                        recordMigration(fileName);
                    }
                }
            } finally {
                // Останавливаем планировщик
                scheduler.shutdown();
                releaseLock();
            }
        } else {
            throw new SQLException("Другая миграция уже запущена."); //todo заменить на лог
        }
    }

    private void ensureMigrationTableExists() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS migration_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                version VARCHAR(100) NOT NULL UNIQUE,
                applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """;
        // Проверяем тип базы данных для корректного синтаксиса
        String dbProductName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        if (dbProductName.contains("postgresql")) {
            createTableSQL = """
                CREATE TABLE IF NOT EXISTS migration_history (
                    id SERIAL PRIMARY KEY,
                    version VARCHAR(100) NOT NULL UNIQUE,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
            """;
        } else if (dbProductName.contains("h2")) {
            createTableSQL = """
                CREATE TABLE IF NOT EXISTS migration_history (
                    id INTEGER AUTO_INCREMENT PRIMARY KEY,
                    version VARCHAR(100) NOT NULL UNIQUE,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
            """;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    private boolean isMigrationApplied(String version) throws SQLException { // todo если нигде больше не будешь юзать это, то поменять на isNot
        String query = "SELECT 1 FROM migration_history WHERE version = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, version);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        }
    }

    private void applyMigration(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void recordMigration(String version) throws SQLException {
        String insertSQL = "INSERT INTO migration_history (version) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            pstmt.setString(1, version);
            pstmt.executeUpdate();
        }
    }
}
