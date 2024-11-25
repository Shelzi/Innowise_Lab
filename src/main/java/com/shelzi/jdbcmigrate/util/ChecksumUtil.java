package com.shelzi.jdbcmigrate.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.codec.digest.DigestUtils;

public class ChecksumUtil {

    public static String calculateChecksum(Path filePath) throws IOException {
        // Читаем содержимое файла в InputStream
        try (InputStream is = Files.newInputStream(filePath)) {
            // Вычисляем SHA-256 хеш
            return DigestUtils.sha256Hex(is);
        }
    }
}
