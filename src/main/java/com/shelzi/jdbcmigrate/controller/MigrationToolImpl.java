package com.shelzi.jdbcmigrate.controller;

import com.shelzi.jdbcmigrate.database.ConnectionCreator;
import com.shelzi.jdbcmigrate.database.ConnectionFactory;
import com.shelzi.jdbcmigrate.exception.SchemaNotFoundException;
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
                logger.log(Level.DEBUG, "Используется файл конфигурации: " + configFilePath);
            } else {
                // Используем файл из ресурсов по умолчанию
                configFilePath = "application.properties";
                logger.log(Level.DEBUG, "Используется файл конфигурации по умолчанию из ресурсов: " + configFilePath);
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
                MigrationManager migrationManager = new MigrationManager(connection, migrationDirectory, properties);

                // Применяем миграции
                migrationManager.applyMigrations();

                logger.log(Level.DEBUG, "Миграции успешно применены!");
            }
        } catch (ClassNotFoundException e) {
            logger.log(Level.ERROR, "Драйвер базы данных не найден: " + e.getMessage());
        } catch (SQLException e) {
            logger.log(Level.ERROR, "Ошибка подключения к базе данных: " + e.getMessage());
        } catch (IOException e) {
            logger.log(Level.ERROR, "Ошибка при работе с файлами конфигурации или миграций: " + e.getMessage());
        } catch (SchemaNotFoundException e) {
            logger.log(Level.ERROR, "No such schema found in database: " + e.getMessage());
        }
    }

    private void setDatabaseSchemaFromProperty(Properties properties, Connection connection) throws SchemaNotFoundException {
        // Устанавливаем схему базы данных, если указано
        String schema = properties.getProperty("db.schema");
        if (schema != null && !schema.isEmpty()) {
            try {
                connection.setSchema(schema);
                logger.log(Level.DEBUG, "Установлена схема базы данных: " + schema);
            } catch (SQLException e) {
                throw new SchemaNotFoundException(e);
            }
        }
    }
}


