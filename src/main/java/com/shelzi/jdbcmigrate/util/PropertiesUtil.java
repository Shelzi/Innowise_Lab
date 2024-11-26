package com.shelzi.jdbcmigrate.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class PropertiesUtil {

    public static Properties loadProperties(String filePath) throws IOException {
        Properties properties = new Properties();

        try (InputStream input = new FileInputStream(filePath)) {
            properties.load(input);
        } catch (IOException e) {
            // Если файл не найден по указанному пути, пытаемся загрузить из ресурсов
            InputStream resourceStream = PropertiesUtil.class.getClassLoader().getResourceAsStream(filePath);
            if (resourceStream != null) {
                properties.load(resourceStream);
            } else {
                throw new IOException("Файл конфигурации не найден: " + filePath);
            }
        }

        // Переопределяем только пароль из переменной окружения
        overrideWithEnv(properties, "db.password", "DB_PASSWORD");

        return properties;
    }

    private static void overrideWithEnv(Properties properties, String propertyName, String envName) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isEmpty()) {
            properties.setProperty(propertyName, envValue);
        }
    }
}
