package com.nuono.next.datapull.schedule;

import java.time.LocalDateTime;

/** Exact temporal DP08 binding selected for one proposed schedule slot. */
public class ScheduleTaskBindingRow {
    private String scopeKey;
    private LocalDateTime scheduleSlot;
    private String bindingId;
    private String payloadType;
    private String payloadSha256;
    private String payload;
    private LocalDateTime effectiveFromUtc;

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public LocalDateTime getScheduleSlot() { return scheduleSlot; }
    public void setScheduleSlot(LocalDateTime value) { scheduleSlot = value; }
    public String getBindingId() { return bindingId; }
    public void setBindingId(String value) { bindingId = value; }
    public String getPayloadType() { return payloadType; }
    public void setPayloadType(String value) { payloadType = value; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String value) { payloadSha256 = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { payload = value; }
    public LocalDateTime getEffectiveFromUtc() { return effectiveFromUtc; }
    public void setEffectiveFromUtc(LocalDateTime value) { effectiveFromUtc = value; }
}
