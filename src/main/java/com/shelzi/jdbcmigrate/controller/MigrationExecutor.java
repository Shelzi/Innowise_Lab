package com.shelzi.jdbcmigrate.controller;

import com.shelzi.jdbcmigrate.exception.LockException;
import com.shelzi.jdbcmigrate.exception.MigrationException;
import com.shelzi.jdbcmigrate.exception.MigrationExecutorException;
import com.shelzi.jdbcmigrate.io.MigrationFileReader;
import com.shelzi.jdbcmigrate.util.ChecksumUtil;
import com.shelzi.jdbcmigrate.util.LoggerFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;


public class MigrationExecutor {
    private final Connection connection;
    private final String migrationDirectory;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final int START_DELAY_IN_MINUTES = 2;
    private static final int UPDATE_PERIOD_IN_MINUTES = 2;

    public MigrationExecutor(Connection connection, String migrationDirectory) {
        this.connection = connection;
        this.migrationDirectory = migrationDirectory;
    }

    public void applyMigrations() throws SQLException, IOException, MigrationException {
        MigrationManager migrationManager = new MigrationManager(connection, migrationDirectory);

        try {
            LockExecutor lockExecutor = new LockExecutor(connection);
            lockExecutor.ensureLockTableExists();

            if (lockExecutor.acquireLock()) { // todo нет цикла опроса и таймаута для другого пользователя.
                // Проверяем, что блокировка принадлежит нам
                if (!lockExecutor.checkLockOwnership()) {
                    throw new LockException("The lock has been lost or belongs to another process. Migration cannot be continued.");
                    // Didn't have enough time to make timeout for that one.
                }
            } else {
                throw new LockException("Another migration has already been launched.");
            }

            // Сервис обновления блокировки
            ScheduledExecutorService lockRefresherService =
                    lockExecutor.getLockRefresherService(START_DELAY_IN_MINUTES, UPDATE_PERIOD_IN_MINUTES);

            List<Path> pendingMigrationsList = migrationManager.getMigrations();
            try {
                for (Path migrationPath : pendingMigrationsList) {
                    applyMigrationWithHandling(migrationPath, lockExecutor);
                }
            } finally {
                // Останавливаем планировщик и освобождаем блокировку
                lockRefresherService.shutdown();
                lockExecutor.releaseLock();
            }
        } catch (SQLException e) {
            throw new MigrationException("Error when applying migrations: ", e);
        }
    }

    private void applyMigration(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    private int recordMigration(String version, boolean successful, String checksum) throws SQLException {
        String insertSQL = "INSERT INTO migration_history (version, successful, checksum) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, version);
            pstmt.setBoolean(2, successful);
            pstmt.setString(3, checksum);
            pstmt.executeUpdate();

            // Получаем сгенерированный ID
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Failed to retrieve the ID of the migration record.");
                }
            }
        }
    }

    private void updateMigrationSuccess(int migrationId, boolean successful) throws SQLException {
        String updateSQL = "UPDATE migration_history SET successful = ?, applied_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
            pstmt.setBoolean(1, successful);
            pstmt.setInt(2, migrationId);
            pstmt.executeUpdate();
        }
    }

    private void applyMigrationWithHandling(Path migrationPath, LockExecutor lockExecutor) throws SQLException, MigrationExecutorException {
        int migrationId = -1;

        try {
            String fileName = migrationPath.getFileName().toString();
            String currentChecksum = ChecksumUtil.calculateChecksum(migrationPath);

            // Записываем миграцию с successful = false перед применением
            migrationId = recordMigration(fileName, false, currentChecksum);

            String sql = MigrationFileReader.readFile(migrationPath);

            if (!lockExecutor.checkLockOwnership()) { // Проверка блокировки
                throw new LockException("The lock has been lost or belongs to another process. Migration cannot be continued.");
            }

            applyMigration(sql);

            // Обновляем запись миграции, устанавливая successful = true
            updateMigrationSuccess(migrationId, true);
            logger.log(Level.DEBUG, "Migration applied: " + fileName);

        } catch (SQLException | LockException | IOException e) {
            if (migrationId != -1) {
                // Обновляем запись миграции, устанавливая successful = false
                updateMigrationSuccess(migrationId, false);
            }
            throw new MigrationExecutorException("Error trying to apply migrations: " + e); // Перебрасываем исключение для дальнейшей обработки
        }
    }
}
