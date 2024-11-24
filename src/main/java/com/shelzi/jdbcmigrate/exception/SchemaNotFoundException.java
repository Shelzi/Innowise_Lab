package com.shelzi.jdbcmigrate.exception;

public class SchemaNotFoundException extends MigrationException{
    public SchemaNotFoundException() {
        super();
    }

    public SchemaNotFoundException(String message) {
        super(message);
    }

    public SchemaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public SchemaNotFoundException(Throwable cause) {
        super(cause);
    }
}
