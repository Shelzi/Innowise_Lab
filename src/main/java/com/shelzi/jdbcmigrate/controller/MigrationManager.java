package com.shelzi.jdbcmigrate.controller;

import com.shelzi.jdbcmigrate.entity.MigrationHistory;
import com.shelzi.jdbcmigrate.exception.MigrationException;
import com.shelzi.jdbcmigrate.io.MigrationFileReader;
import com.shelzi.jdbcmigrate.util.ChecksumUtil;
import com.shelzi.jdbcmigrate.util.LoggerFactory;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MigrationManager {
    private final Connection connection;
    private final String migrationDirectory;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    public MigrationManager(Connection connection, String migrationDirectory) {
        this.connection = connection;
        this.migrationDirectory = migrationDirectory;
    }

    public void applyMigrations() throws SQLException, IOException, MigrationException {
        try {
            LockExecutor lockExecutor = new LockExecutor(connection);
            MigrationHelper migrationHelper = new MigrationHelper(connection);
            lockExecutor.ensureLockTableExists();
            migrationHelper.ensureMigrationTableExists();

            if (lockExecutor.acquireLock()) { // todo нет цикла опроса и таймаута для другого пользователя.
                // Проверяем, что блокировка принадлежит нам
                if (!lockExecutor.checkLockOwnership()) {
                    throw new MigrationException("Блокировка потеряна или принадлежит другому процессу. Миграция не может быть продолжена.");
                }

                // Планировщик для обновления блокировки
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                Runnable refreshLockTask = () -> {
                    try {
                        lockExecutor.refreshLock();
                    } catch (SQLException e) {
                        logger.error("Ошибка при обновлении блокировки", e);
                    }
                };

                // Запускаем задачу обновления блокировки каждые 2 минут
                scheduler.scheduleAtFixedRate(refreshLockTask, 2, 2, TimeUnit.MINUTES);

                try {
                    // Получаем список уже применённых миграций
                    List<MigrationHistory> appliedMigrations = migrationHelper.getAppliedMigrations();

                    // Создаём Map версий и их контрольных сумм
                    Map<String, String> appliedChecksums = appliedMigrations.stream()
                            .filter(MigrationHistory::isSuccessful)
                            .collect(Collectors.toMap(MigrationHistory::getVersion, MigrationHistory::getChecksum));

                    // Получаем файлы миграций
                    List<Path> migrationFiles = MigrationFileReader.getMigrationFiles(migrationDirectory);

                    for (Path filePath : migrationFiles) {
                        String fileName = filePath.getFileName().toString();
                        String currentChecksum = ChecksumUtil.calculateChecksum(filePath);

                        if (appliedChecksums.containsKey(fileName)) {
                            String appliedChecksum = appliedChecksums.get(fileName);
                            if (!currentChecksum.equals(appliedChecksum)) {
                                // Контрольная сумма изменилась
                                throw new MigrationException("Контрольная сумма миграции " + fileName + " изменилась. Миграция была модифицирована после применения.");
                            } else {
                                // Миграция уже применена, пропускаем
                                continue;
                            }
                        }

                        // Записываем миграцию с successful = false перед применением
                        int migrationId = recordMigration(fileName, false, currentChecksum);

                        try {
                            String sql = MigrationFileReader.readFile(filePath);

                            if (!lockExecutor.checkLockOwnership()) {
                                throw new MigrationException("Блокировка потеряна или принадлежит другому процессу. Миграция не может быть продолжена.");
                            }

                            applyMigration(sql);

                            // Обновляем запись миграции, устанавливая successful = true
                            updateMigrationSuccess(migrationId, true);
                        } catch (SQLException e) {
                            // Обновляем запись миграции, устанавливая successful = false
                            updateMigrationSuccess(migrationId, false);
                            throw e; // Перебрасываем исключение для дальнейшей обработки
                        }
                    }

                } finally {
                    // Останавливаем планировщик и освобождаем блокировку
                    scheduler.shutdown();
                    lockExecutor.releaseLock();
                }
            } else {
                throw new MigrationException("Другая миграция уже запущена.");
            }
        } catch (SQLException e) {
            throw new MigrationException("Ошибка при применении миграций", e);
        } catch (IOException e) {
            throw new MigrationException("Ошибка при чтении файлов миграций", e);
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
                    throw new SQLException("Не удалось получить ID записи миграции.");
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
}
