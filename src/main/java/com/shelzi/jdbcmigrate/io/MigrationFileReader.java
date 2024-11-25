package com.shelzi.jdbcmigrate.io;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MigrationFileReader {

    /**
     * Получает список файлов миграций из указанной директории.
     * Файлы миграций должны соответствовать шаблону: V{номер}__{описание}.sql
     * Пример: V1_0__initial_setup.sql
     *
     * @param directory путь к директории с миграциями
     * @return отсортированный список путей к файлам миграций
     * @throws IOException если возникает ошибка при чтении файлов
     */
    public static List<Path> getMigrationFiles(String directory) throws IOException {
        Path migrationPath = Paths.get(directory);

        if (!Files.exists(migrationPath) || !Files.isDirectory(migrationPath)) {
            throw new IOException("Директория миграций не существует или не является директорией: " + directory);
        }

        try (Stream<Path> stream = Files.walk(migrationPath)) { // Используем try-with-resources
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("^V[\\d._]+__.+\\.sql$"))
                    .sorted((path1, path2) -> {
                        String fileName1 = path1.getFileName().toString();
                        String fileName2 = path2.getFileName().toString();

                        // Извлекаем номер версии из имени файла
                        String version1 = extractVersion(fileName1);
                        String version2 = extractVersion(fileName2);

                        // Сравниваем версии
                        return compareVersions(version1, version2);
                    })
                    .collect(Collectors.toList());
        } // Поток автоматически закрывается здесь
    }

    /**
     * Читает содержимое файла миграции.
     *
     * @param filePath путь к файлу миграции
     * @return содержимое файла в виде строки
     * @throws IOException если возникает ошибка при чтении файла
     */
    public static String readFile(Path filePath) throws IOException {
        return Files.readString(filePath);
    }

    /**
     * Извлекает номер версии из имени файла миграции.
     * Пример: V1_0__initial_setup.sql -> "1_0"
     *
     * @param fileName имя файла миграции
     * @return номер версии в виде строки
     */
    private static String extractVersion(String fileName) {
        String versionPart = fileName.split("__")[0]; // "V1_0"
        return versionPart.substring(1); // удаляем "V" и получаем "1_0"
    }

    /**
     * Сравнивает две версии, разделяя их на части по символам '.', '_', '-'.
     *
     * @param version1 первая версия
     * @param version2 вторая версия
     * @return отрицательное число, если version1 < version2; 0, если version1 == version2; положительное число, если version1 > version2
     */
    private static int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("[._-]");
        String[] parts2 = version2.split("[._-]");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int v1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int v2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            int compare = Integer.compare(v1, v2);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    /**
     * Парсит часть версии в целое число.
     *
     * @param part часть версии
     * @return целое число, представляющее часть версии
     */
    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0; // Если часть не является числом, возвращаем 0
        }
    }
}
