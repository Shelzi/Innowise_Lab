package com.shelzi.jdbcmigrate.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionCreator {
    Connection getConnection() throws SQLException, ClassNotFoundException;
}
