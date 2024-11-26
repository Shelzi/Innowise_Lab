package com.shelzi.jdbcmigrate.exception;

public class MigrationExecutorException extends MigrationException {
  public MigrationExecutorException() {
    super();
  }

  public MigrationExecutorException(String message) {
    super(message);
  }

  public MigrationExecutorException(String message, Throwable cause) {
    super(message, cause);
  }

  public MigrationExecutorException(Throwable cause) {
    super(cause);
  }
}
