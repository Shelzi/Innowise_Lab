package com.shelzi.jdbcmigrate.exception;

public class MigrationManagerException extends MigrationException {
    public MigrationManagerException() {
        super();
    }

    public MigrationManagerException(String message) {
        super(message);
    }

    public MigrationManagerException(String message, Throwable cause) {
        super(message, cause);
    }

    public MigrationManagerException(Throwable cause) {
        super(cause);
    }
}
