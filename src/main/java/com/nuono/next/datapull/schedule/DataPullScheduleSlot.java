package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** One deterministic due time and its immutable business window. */
public final class DataPullScheduleSlot {
    private final OperationCode operationCode;
    private final ZonedDateTime scheduledAt;
    private final String scheduleSlotKey;
    private final DataPullBusinessWindow businessWindow;

    DataPullScheduleSlot(
            OperationCode operationCode,
            ZonedDateTime scheduledAt,
            DataPullBusinessWindow businessWindow
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
        this.businessWindow = Objects.requireNonNull(businessWindow, "businessWindow");
        this.scheduleSlotKey = operationCode.name()
                + "@"
                + scheduledAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public OperationCode getOperationCode() {
        return operationCode;
    }

    public ZonedDateTime getScheduledAt() {
        return scheduledAt;
    }

    public String getScheduleSlotKey() {
        return scheduleSlotKey;
    }

    public DataPullBusinessWindow getBusinessWindow() {
        return businessWindow;
    }
}
