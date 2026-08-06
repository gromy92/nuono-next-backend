package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable DP-06 scope and T-1 report window reconstructed from the persisted task. */
public final class AdvertisingPullRequest {
    private static final Pattern WINDOW = Pattern.compile(
            "^DP06:date-range:(\\d{4}-\\d{2}-\\d{2})\\.\\.(\\d{4}-\\d{2}-\\d{2})$"
    );

    private final long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate reportDate;

    private AdvertisingPullRequest(
            long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            LocalDate reportDate
    ) {
        if (ownerUserId <= 0L) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        this.ownerUserId = ownerUserId;
        this.projectCode = AdvertisingAdvertiser.requireIdentity(projectCode, "projectCode");
        this.storeCode = AdvertisingAdvertiser.requireIdentity(storeCode, "storeCode");
        this.siteCode = AdvertisingAdvertiser.requireIdentity(siteCode, "siteCode");
        this.reportDate = Objects.requireNonNull(reportDate, "reportDate");
    }

    public static AdvertisingPullRequest from(DataPullTask task) {
        DataPullTask value = Objects.requireNonNull(task, "task");
        if (value.getOperationCode() != OperationCode.DP06) {
            throw new IllegalArgumentException("DP-06 task is required");
        }
        Matcher matcher = WINDOW.matcher(Objects.requireNonNull(
                value.getBusinessWindowKey(),
                "businessWindowKey"
        ));
        if (!matcher.matches() || !matcher.group(1).equals(matcher.group(2))) {
            throw new IllegalArgumentException("DP-06 requires one exact report date");
        }
        return new AdvertisingPullRequest(
                Objects.requireNonNull(value.getOwnerUserId(), "ownerUserId"),
                value.getProjectCode(),
                value.getStoreCode(),
                value.getSiteCode(),
                LocalDate.parse(matcher.group(1))
        );
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }
}
