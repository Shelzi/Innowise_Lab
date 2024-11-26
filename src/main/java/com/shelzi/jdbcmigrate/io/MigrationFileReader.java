package com.shelzi.jdbcmigrate.io;

import com.shelzi.jdbcmigrate.exception.MigrationFileReaderException;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MigrationFileReader {
    public static List<Path> getMigrationFiles(String directory) throws MigrationFileReaderException {
        Path migrationPath = Paths.get(directory);

        if (!Files.exists(migrationPath) || !Files.isDirectory(migrationPath)) {
            throw new MigrationFileReaderException(
                    "The migrations directory does not exist or is not a directory: " + directory);
        }

        try (Stream<Path> stream = Files.walk(migrationPath)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("^V[\\d._]+__.+\\.sql$"))
                    .sorted((path1, path2) -> {
                        String fileName1 = path1.getFileName().toString();
                        String fileName2 = path2.getFileName().toString();

                        // Извлекаем номер версии из имени файла
                        String version1 = extractVersion(fileName1);
                        String version2 = extractVersion(fileName2);

                        // Сравниваем версии
                        return compareVersions(version1, version2);
                    }) // if only one migration - comparator not will be called - skip everything
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MigrationFileReaderException("Wrong path or access violation while trying to get migration: " + e);
        }
    }

    public static String readFile(Path filePath) throws IOException {
        return Files.readString(filePath);
    }

    private static String extractVersion(String fileName) {
        String versionPart = fileName.split("__")[0]; // "V1_0"
        return versionPart.substring(1); // удаляем "V" и получаем "1_0"
    }

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

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0; // Если часть не является числом, возвращаем 0
        }
    }
}
