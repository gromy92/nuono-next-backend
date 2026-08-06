package com.nuono.next.datapull.leader;

import java.time.LocalDateTime;

/** MyBatis row shape carrying the database clock used to prove a live lease. */
public final class DataPullRuntimeLeaderRow {

    private String owner;
    private Long epoch;
    private LocalDateTime leaseUntil;
    private LocalDateTime databaseTime;

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public Long getEpoch() { return epoch; }
    public void setEpoch(Long epoch) { this.epoch = epoch; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public LocalDateTime getDatabaseTime() { return databaseTime; }
    public void setDatabaseTime(LocalDateTime databaseTime) { this.databaseTime = databaseTime; }

    public DataPullRuntimeLeaderLease toLease() {
        if (epoch == null) {
            throw new IllegalStateException("database leader row has no epoch");
        }
        return new DataPullRuntimeLeaderLease(owner, epoch, leaseUntil, databaseTime);
    }
}
