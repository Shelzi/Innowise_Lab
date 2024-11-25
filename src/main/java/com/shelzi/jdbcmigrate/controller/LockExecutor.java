package com.shelzi.jdbcmigrate.controller;

import java.lang.management.ManagementFactory;
import java.sql.*;

public class LockExecutor {
    private final Connection connection;

    public LockExecutor(Connection connection) {
        this.connection = connection;
    }

    boolean acquireLock() throws SQLException {
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
                // Проверяем, не истекла ли блокировка
                if (isLockExpired()) {
                    // Попробуем удалить старую блокировку и установить новую
                    releaseLock(); // Освобождаем блокировку (она истекла)
                    return acquireLock(); // Рекурсивно пытаемся установить блокировку снова
                } else {
                    return false; // Блокировка занята другим процессом
                }
            } else {
                throw e; // Перебрасываем другие исключения
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
                throw new SQLException("Не удалось обновить блокировку. Она принадлежит другому процессу или была снята.");
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

    private boolean isLockExpired() throws SQLException {
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
}
