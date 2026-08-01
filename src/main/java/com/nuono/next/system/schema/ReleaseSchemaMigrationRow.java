package com.nuono.next.system.schema;

public class ReleaseSchemaMigrationRow {
    private String migrationKey;
    private String checksum;
    private String postcheckChecksum;
    private String state;
    private Integer attemptNo;
    private String attemptChecksum;
    private String attemptPostcheckChecksum;
    private String attemptState;
    private Integer joinedAttemptNo;

    public String getMigrationKey() {
        return migrationKey;
    }

    public void setMigrationKey(String migrationKey) {
        this.migrationKey = migrationKey;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getPostcheckChecksum() {
        return postcheckChecksum;
    }

    public void setPostcheckChecksum(String postcheckChecksum) {
        this.postcheckChecksum = postcheckChecksum;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public String getAttemptChecksum() {
        return attemptChecksum;
    }

    public void setAttemptChecksum(String attemptChecksum) {
        this.attemptChecksum = attemptChecksum;
    }

    public String getAttemptPostcheckChecksum() {
        return attemptPostcheckChecksum;
    }

    public void setAttemptPostcheckChecksum(String attemptPostcheckChecksum) {
        this.attemptPostcheckChecksum = attemptPostcheckChecksum;
    }

    public String getAttemptState() {
        return attemptState;
    }

    public void setAttemptState(String attemptState) {
        this.attemptState = attemptState;
    }

    public Integer getJoinedAttemptNo() {
        return joinedAttemptNo;
    }

    public void setJoinedAttemptNo(Integer joinedAttemptNo) {
        this.joinedAttemptNo = joinedAttemptNo;
    }
}
