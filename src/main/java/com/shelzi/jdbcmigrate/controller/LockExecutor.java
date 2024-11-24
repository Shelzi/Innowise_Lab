package com.shelzi.jdbcmigrate.controller;

import java.sql.*;

public class LockExecutor {
    private final Connection connection;

    public LockExecutor(Connection connection) {
        this.connection = connection;
    }

    private boolean acquireLock() throws SQLException {
        String insertLockSQL = "INSERT INTO migration_lock (lock_id) VALUES (1)";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(insertLockSQL);
            return true;
        } catch (SQLException e) {
            if (isDuplicateKeyError(e)) {
                if (isLockExpired()) {
                    releaseLock();
                    return acquireLock();
                } else {
                    return false;
                }
            } else {
                throw e;
            }
        }
    }

    private void refreshLock() throws SQLException {
        String updateLockSQL = "UPDATE migration_lock SET locked_at = ? WHERE lock_id = 1";
        try (PreparedStatement pstmt = connection.prepareStatement(updateLockSQL)) {
            pstmt.setTimestamp(1, new Timestamp(System.currentTimeMillis())); //todo время может быть в разных часовых поясах.
            pstmt.executeUpdate();
        }
    }


    private void releaseLock() throws SQLException {
        String deleteLockSQL = "DELETE FROM migration_lock WHERE lock_id = 1";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(deleteLockSQL);
        }
    }

    private boolean isLockExpired() throws SQLException {
        String query = "SELECT locked_at FROM migration_lock WHERE lock_id = 1";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                Timestamp lockedAt = rs.getTimestamp("locked_at");
                long lockDuration = System.currentTimeMillis() - lockedAt.getTime();
                // Устанавливаем тайм-аут блокировки, например, 5 минут
                return lockDuration > 5 * 60 * 1000;
            }
        }
        return false;
    }

    private boolean isDuplicateKeyError(SQLException e) { //todo возможно не надо когда появится состояние лока
        String sqlState = e.getSQLState();
        int errorCode = e.getErrorCode();
        String dbProductName = null;
        try {
            dbProductName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);  //todo handel this mf
        }

        // PostgreSQL и H2
        if (dbProductName.contains("postgresql") || dbProductName.contains("h2")) {
            return "23505".equals(sqlState);
        }
        // MySQL
        else if (dbProductName.contains("mysql")) {
            return errorCode == 1062;
        }
        // SQLite (если поддерживается)
        else if (dbProductName.contains("sqlite")) {
            return "SQLITE_CONSTRAINT_UNIQUE".equals(e.getMessage());
        }
        // Другие базы данных
        else {
            // Обработка по умолчанию
            return "23000".equals(sqlState);
        }
    }

    private void ensureLockTableExists() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS migration_lock (
                lock_id INT PRIMARY KEY,
                locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }
}
