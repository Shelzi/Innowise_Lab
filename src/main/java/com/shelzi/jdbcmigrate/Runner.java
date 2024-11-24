package com.shelzi.jdbcmigrate;

import com.shelzi.jdbcmigrate.controller.MigrationTool;
import com.shelzi.jdbcmigrate.controller.MigrationToolImpl;

public class Runner {
    public static void main(String[] args) {
        MigrationTool migrationTool = new MigrationToolImpl();
        migrationTool.migrate(args);
    }
}
