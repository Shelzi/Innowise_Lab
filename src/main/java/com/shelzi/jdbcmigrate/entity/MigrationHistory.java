package com.shelzi.jdbcmigrate.entity;

import java.sql.Timestamp;
import java.util.Objects;

public class MigrationHistory {
    private int id;
    private String version;
    private Timestamp appliedAt;
    private boolean successful;
    private String checksum;

    public MigrationHistory(int id, String version, Timestamp appliedAt, boolean successful, String checksum) {
        this.id = id;
        this.version = version;
        this.appliedAt = appliedAt;
        this.successful = successful;
        this.checksum = checksum;
    }

    public int getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public Timestamp getAppliedAt() {
        return appliedAt;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setAppliedAt(Timestamp appliedAt) {
        this.appliedAt = appliedAt;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MigrationHistory that = (MigrationHistory) o;
        return id == that.id && successful == that.successful && Objects.equals(version, that.version) && Objects.equals(appliedAt, that.appliedAt) && Objects.equals(checksum, that.checksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, appliedAt, successful, checksum);
    }

    @Override
    public String toString() {
        return "MigrationHistory{" +
                "id=" + id +
                ", version='" + version + '\'' +
                ", appliedAt=" + appliedAt +
                ", successful=" + successful +
                ", checksum='" + checksum + '\'' +
                '}';
    }
}
