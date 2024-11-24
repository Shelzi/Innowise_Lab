package com.shelzi.jdbcmigrate.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgreSQLConnectionCreator implements ConnectionCreator {
    private final String url;
    private final String user;
    private final String password;

    public PostgreSQLConnectionCreator(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException, ClassNotFoundException {
        String driverClassName = "org.postgresql.Driver";
        Class.forName(driverClassName);
        return DriverManager.getConnection(url, user, password);
    }
}
