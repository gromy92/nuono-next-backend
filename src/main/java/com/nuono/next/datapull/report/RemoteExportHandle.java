package com.nuono.next.datapull.report;

import java.util.Objects;

/** Opaque provider identity that must be reused for every poll and download. */
public final class RemoteExportHandle {

    private static final int MAX_LENGTH = 512;

    private final String value;

    public RemoteExportHandle(String value) {
        String handle = ReportContract.requireIdentity(value, "remoteHandle");
        if (handle.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("remoteHandle exceeds its persistence column");
        }
        this.value = handle;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RemoteExportHandle
                && value.equals(((RemoteExportHandle) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
