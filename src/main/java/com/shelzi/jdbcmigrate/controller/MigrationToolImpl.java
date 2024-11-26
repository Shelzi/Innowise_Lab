package com.shelzi.jdbcmigrate.controller;

import com.shelzi.jdbcmigrate.database.ConnectionCreator;
import com.shelzi.jdbcmigrate.database.ConnectionFactory;
import com.shelzi.jdbcmigrate.exception.MigrationException;
import com.shelzi.jdbcmigrate.util.LoggerFactory;
import com.shelzi.jdbcmigrate.util.PropertiesUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class MigrationToolImpl implements MigrationTool {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public MigrationToolImpl() { //todo делать ли синглтоном?
    }

    @Override
    public void migrate(String[] args) {
        try {
            // Определяем путь к файлу конфигурации
            String configFilePath;
            if (args.length > 0) {
                configFilePath = args[0];
                logger.log(Level.DEBUG, "A configuration file is used: " + configFilePath);
            } else {
                // Используем файл из ресурсов по умолчанию
                configFilePath = "application.properties";
                logger.log(Level.DEBUG, "The default configuration file from the resources is used: " + configFilePath);
            }

            // Загружаем конфигурацию
            Properties properties = PropertiesUtil.loadProperties(configFilePath);

            // Создаём ConnectionCreator через фабрику
            ConnectionCreator connectionCreator = ConnectionFactory.createConnectionCreator(properties);

            // Получаем соединение
            try (Connection connection = connectionCreator.getConnection()) {

                setDatabaseSchemaFromProperty(properties, connection);

                String migrationDirectory = properties.getProperty("migration.directory");

                // Инициализируем менеджер миграций
                MigrationExecutor migrationExecutor = new MigrationExecutor(connection, migrationDirectory);

                // Применяем миграции
                migrationExecutor.applyMigrations();

                logger.log(Level.DEBUG, "The migrations have been successfully applied!");
            }
        } catch (ClassNotFoundException e) {
            logger.log(Level.ERROR, "Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            logger.log(Level.ERROR, "Database connection error: " + e.getMessage());
        } catch (IOException e) {
            logger.log(Level.ERROR, "Error when working with configuration or migration files: " + e.getMessage());
        } catch (MigrationException e) {
            logger.log(Level.ERROR, "No such schema found in database: " + e.getMessage());
        }
    }

    private void setDatabaseSchemaFromProperty(Properties properties, Connection connection) throws MigrationException {
        // Устанавливаем схему базы данных, если указано
        String schema = properties.getProperty("db.schema");
        if (schema != null && !schema.isEmpty()) {
            try {
                connection.setSchema(schema);
                logger.log(Level.DEBUG, "A database schema has been established: " + schema);
            } catch (SQLException e) {
                throw new MigrationException(e);
            }
        } else {
            String msg = "A database schema not found in property file.";
            logger.log(Level.ERROR, msg);
            throw new MigrationException(msg);
        }
    }
}


