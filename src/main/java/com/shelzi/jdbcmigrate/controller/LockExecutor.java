package com.shelzi.jdbcmigrate.controller;

import com.shelzi.jdbcmigrate.exception.LockException;
import com.shelzi.jdbcmigrate.util.LoggerFactory;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LockExecutor {
    private final Connection connection;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public LockExecutor(Connection connection) {
        this.connection = connection;
    }

    boolean acquireLock() throws SQLException, LockException {
        ensureLockTableExists();

        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        String insertSQL = "INSERT INTO migration_lock (lock_id, locked_at, pid) VALUES (1, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            pstmt.setTimestamp(1, new Timestamp(System.currentTimeMillis())); // todo уточнить вопрос с таймзонами
            pstmt.setString(2, pid);
            pstmt.executeUpdate();
            return true; // Блокировка успешно установлена
        } catch (SQLException e) {
            // Проверяем, что блокировка уже существует
            if (e.getSQLState().startsWith("23")) { // Код SQLState для нарушения уникального ограничения, я проверил, это для всех так
                if (isLockExpired()) {
                    releaseLock();
                    return acquireLock();
                } else {
                    return false; // Блокировка занята другим процессом
                }
            } else {
                throw new LockException("Error with lock table while trying to update it", e);
            }
        }
    }

    void refreshLock() throws SQLException {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        String updateSQL = "UPDATE migration_lock SET locked_at = ? WHERE lock_id = 1 AND pid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
            pstmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            pstmt.setString(2, pid);
            int updatedRows = pstmt.executeUpdate();
            if (updatedRows == 0) {
                throw new SQLException("Failed to update the lock. It belongs to another process or has been removed.");
            }
        }
    }

    boolean checkLockOwnership() throws SQLException {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        String selectSQL = "SELECT pid FROM migration_lock WHERE lock_id = 1";
        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String lockPid = rs.getString("pid");
                return pid.equals(lockPid);
            } else {
                return false; // Блокировка не существует
            }
        }
    }

    void releaseLock() throws SQLException {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        String deleteSQL = "DELETE FROM migration_lock WHERE lock_id = 1 AND pid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(deleteSQL)) {
            pstmt.setString(1, pid);
            pstmt.executeUpdate();
        }
    }

    boolean isLockExpired() throws SQLException {
        String selectSQL = "SELECT locked_at FROM migration_lock WHERE lock_id = 1";
        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Timestamp lockedAt = rs.getTimestamp("locked_at");
                long lockDuration = System.currentTimeMillis() - lockedAt.getTime();
                return lockDuration > 5 * 60 * 1000; // Тайм-аут блокировки 5 минут
            } else {
                return true; // Блокировка не существует
            }
        }
    }

    void ensureLockTableExists() throws SQLException {
        String createTableSQL = """
                    CREATE TABLE IF NOT EXISTS migration_lock (
                          lock_id INT PRIMARY KEY,
                          locked_at TIMESTAMP NOT NULL,
                          pid VARCHAR(100) NOT NULL
                      );
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    ScheduledExecutorService getLockRefresherService(int initialDelay, int c) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runnable refreshLockTask = () -> {
            try {
                refreshLock();
            } catch (SQLException e) {
                logger.error("Error when updating the lock", e);
            }
        };

        // Запускаем задачу обновления блокировки каждые 2 минут
        scheduler.scheduleAtFixedRate(refreshLockTask, initialDelay, initialDelay, TimeUnit.MINUTES);
        return scheduler;
    }
}
