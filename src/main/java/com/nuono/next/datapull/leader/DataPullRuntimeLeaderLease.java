package com.nuono.next.datapull.leader;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable owner/epoch token issued only from the database leader row. */
public final class DataPullRuntimeLeaderLease {

    public static final int MAXIMUM_OWNER_LENGTH = 200;

    private final String owner;
    private final long epoch;
    private final LocalDateTime leaseUntil;
    private final LocalDateTime databaseTime;

    public DataPullRuntimeLeaderLease(
            String owner,
            long epoch,
            LocalDateTime leaseUntil,
            LocalDateTime databaseTime
    ) {
        this.owner = requireOwner(owner);
        if (epoch <= 0L) {
            throw new IllegalArgumentException("leader epoch must be positive");
        }
        this.epoch = epoch;
        this.leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil");
        this.databaseTime = Objects.requireNonNull(databaseTime, "databaseTime");
        if (!leaseUntil.isAfter(databaseTime)) {
            throw new IllegalArgumentException("leader lease must be live at database time");
        }
    }

    public String getOwner() { return owner; }
    public long getEpoch() { return epoch; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public LocalDateTime getDatabaseTime() { return databaseTime; }

    public static String requireOwner(String value) {
        String owner = Objects.requireNonNull(value, "owner");
        if (owner.isEmpty() || !owner.equals(owner.trim())) {
            throw new IllegalArgumentException("leader owner must be a stable non-blank identity");
        }
        if (owner.length() > MAXIMUM_OWNER_LENGTH) {
            throw new IllegalArgumentException("leader owner exceeds its persistence bound");
        }
        return owner;
    }
}
