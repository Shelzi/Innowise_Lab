package com.shelzi.jdbcmigrate.controller;

import com.shelzi.jdbcmigrate.entity.MigrationHistory;
import com.shelzi.jdbcmigrate.exception.MigrationFileReaderException;
import com.shelzi.jdbcmigrate.exception.MigrationManagerException;
import com.shelzi.jdbcmigrate.io.MigrationFileReader;
import com.shelzi.jdbcmigrate.util.ChecksumUtil;
import com.shelzi.jdbcmigrate.util.LoggerFactory;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MigrationManager {
    private final Connection connection;
    private final String migrationDirectory;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    public MigrationManager(Connection connection, String migrationDirectory) {
        this.connection = connection;
        this.migrationDirectory = migrationDirectory;
    }

    public List<Path> getMigrations() throws MigrationFileReaderException, MigrationManagerException {
        MigrationHelper migrationHelper = new MigrationHelper(connection);
        List<MigrationHistory> appliedMigrations;
        try {
            migrationHelper.ensureMigrationTableExists();
            // Получаем список уже применённых миграций
            appliedMigrations = migrationHelper.getAppliedMigrations();
        } catch (SQLException e) {
            throw new MigrationManagerException(
                    "Migration table didn't exist or problem appear while trying to access one.", e);
        }

        // Создаём Map версий и их контрольных сумм
        Map<String, String> appliedChecksums = appliedMigrations.stream()
                .filter(MigrationHistory::isSuccessful)
                .collect(Collectors.toMap(MigrationHistory::getVersion, MigrationHistory::getChecksum));

        // Получаем файлы миграций
        List<Path> migrationFiles;
        try {
            migrationFiles = MigrationFileReader.getMigrationFiles(migrationDirectory);
        } catch (MigrationFileReaderException e) {
            throw new MigrationManagerException(
                    "No such directory or access violation while trying to obtain migration files.", e);
        }
        List<Path> pendingMigrationsList;
        try {
            pendingMigrationsList = getPendingMigrations(migrationFiles, appliedChecksums);
        } catch (IOException | MigrationManagerException e) {
            throw new MigrationManagerException("Error while trying to get pending migrations.", e);
        }


        return pendingMigrationsList;
    }

    private List<Path> getPendingMigrations(List<Path> migrationFiles, Map<String, String> appliedChecksums) throws IOException, MigrationManagerException {
        List<Path> pendingMigrationsList = new ArrayList<>();
        for (Path filePath : migrationFiles) {
            String fileName = filePath.getFileName().toString();
            String currentChecksum = ChecksumUtil.calculateChecksum(filePath);

            if (appliedChecksums.containsKey(fileName)) {
                String appliedChecksum = appliedChecksums.get(fileName);
                if (!currentChecksum.equals(appliedChecksum)) {
                    // Контрольная сумма изменилась
                    throw new MigrationManagerException("The migration checksum " + fileName + " has changed. The migration was modified after application.");
                } else {
                    // Миграция уже применена, пропускаем
                    continue;
                }
            }
            pendingMigrationsList.add(filePath);
        }
        return pendingMigrationsList;
    }
}