package com.shelzi.jdbcmigrate;

import com.shelzi.jdbcmigrate.utils.LoggerFactory;
import com.shelzi.jdbcmigrate.utils.PropertiesUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.util.Properties;

public class MigrationTool {
    private static final Logger logger = LoggerFactory.getLogger(MigrationTool.class);

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1) {
            logger.log(Level.ERROR, "Usage: java -jar myapp.jar path-to-properties-file");
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

            logger.log(Level.DEBUG, "Succesful migration.");

        } catch (IOException e) {
            throw new RuntimeException(e);  // заменить на кастомное исключение
        }

    }
}
