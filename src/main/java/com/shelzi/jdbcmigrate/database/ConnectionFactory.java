package com.shelzi.jdbcmigrate.database;

import java.util.Properties;

public class ConnectionFactory {
    public static ConnectionCreator createConnectionCreator(Properties properties) { // also we can make it get only necessary properties
        String driverClassName = properties.getProperty("db.driver");
        String url = properties.getProperty("db.url");
        String user = properties.getProperty("db.user");
        String password = properties.getProperty("db.password");

        if (driverClassName == null || url == null || user == null) {
            throw new IllegalArgumentException("Параметры подключения к базе данных заданы неверно.");
        }

        switch (driverClassName.toLowerCase()) {
            case "org.postgresql.driver":
                return new PostgreSQLConnectionCreator(url, user, password);
//          case "h2 driver":
//              return new H2ConnectionCreator(url, user, password);
//          case "mysql driver":
//              return new MySQLConnectionCreator(url, user, password);
            default:
                throw new IllegalArgumentException("Unsupported database type: " + driverClassName);
        }
    }
}
