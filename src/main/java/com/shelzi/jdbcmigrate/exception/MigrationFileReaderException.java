package com.shelzi.jdbcmigrate.exception;

public class MigrationFileReaderException extends MigrationException {
    public MigrationFileReaderException() {
        super();
    }

    public MigrationFileReaderException(String message) {
        super(message);
    }

    public MigrationFileReaderException(String message, Throwable cause) {
        super(message, cause);
    }

    public MigrationFileReaderException(Throwable cause) {
        super(cause);
    }
}
