package com.shelzi.jdbcmigrate;

import com.shelzi.jdbcmigrate.utils.PropertiesUtils;
import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.util.Properties;
import java.util.SplittableRandom;

public class MigrationTool {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar myapp.jar path-to-properties-file");
            return;
        }

        Properties properties;

        try {
            properties = PropertiesUtils.loadExternalProperties(args[0]); //todo args

            String url = properties.getProperty("db.url");
            String user = properties.getProperty("db.user");
            String password = properties.getProperty("db.password");
            String flywaySchemas = properties.getProperty("flyway.schemas");
            String flywayDefaultSchema = properties.getProperty("flyway.defaultSchema");
            String flywayLocations = properties.getProperty("flyway.locations");

            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, password)
                    .schemas(flywaySchemas)
                    .defaultSchema(flywayDefaultSchema)
                    .locations(flywayLocations)
                    .baselineOnMigrate(true) // создаёт базовую запись, если таблицы миграции нет
                    .load();
            // конфигурием флайвей через метод чейнинг

            flyway.migrate();

            System.out.println("Миграции успешно применены!");
        } catch (IOException e) {
            throw new RuntimeException(e);  // заменить на кастомное исключение
        }

    }
}
