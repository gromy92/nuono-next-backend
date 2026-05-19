package com.nuono.next.procurement;

import java.util.Locale;

public final class ProcurementAutoInquiryLifecycle {

    public static final String PLATFORM_1688 = "1688";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_RETRYING = "RETRYING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_CHATTING = "CHATTING";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_HANDOFF = "HANDOFF";

    public static final String STAGE_CREATED = "CREATED";
    public static final String STAGE_CLAIMED = "CLAIMED";
    public static final String STAGE_SESSION_READY = "SESSION_READY";
    public static final String STAGE_TARGET_RESOLVED = "TARGET_RESOLVED";
    public static final String STAGE_INPUT_READY = "INPUT_READY";
    public static final String STAGE_SEND_PREPARED = "SEND_PREPARED";
    public static final String STAGE_SEND_CONFIRMED = "SEND_CONFIRMED";

    public static final String SESSION_READY = "READY";
    public static final String SESSION_BUSY = "BUSY";
    public static final String SESSION_DEGRADED = "DEGRADED";
    public static final String SESSION_BLOCKED = "BLOCKED";
    public static final String SESSION_EXPIRED = "EXPIRED";

    public static final String EVENT_TASK_CREATED = "TASK_CREATED";
    public static final String EVENT_TASK_CLAIMED = "TASK_CLAIMED";
    public static final String EVENT_SESSION_ALLOCATED = "SESSION_ALLOCATED";
    public static final String EVENT_TARGET_RESOLVED = "TARGET_RESOLVED";
    public static final String EVENT_INPUT_PREPARED = "INPUT_PREPARED";
    public static final String EVENT_SEND_PREPARED = "SEND_PREPARED";
    public static final String EVENT_SEND_CONFIRMED = "SEND_CONFIRMED";
    public static final String EVENT_TASK_HANDOFF = "TASK_HANDOFF";

    private ProcurementAutoInquiryLifecycle() {
    }

    public static String statusLabel(String status) {
        switch (upper(status)) {
            case STATUS_PENDING:
                return "待执行";
            case STATUS_RUNNING:
                return "执行中";
            case STATUS_RETRYING:
                return "重试中";
            case STATUS_SENT:
                return "已发送";
            case STATUS_CHATTING:
                return "聊天中";
            case STATUS_CLOSED:
                return "聊天结束";
            case STATUS_HANDOFF:
                return "待人工接管";
            default:
                return "待确认";
        }
    }

    public static String stageLabel(String stage) {
        switch (upper(stage)) {
            case STAGE_CREATED:
                return "任务已创建";
            case STAGE_CLAIMED:
                return "执行器已接手";
            case STAGE_SESSION_READY:
                return "会话已就绪";
            case STAGE_TARGET_RESOLVED:
                return "目标已命中";
            case STAGE_INPUT_READY:
                return "输入内容已就绪";
            case STAGE_SEND_PREPARED:
                return "发送前证据已就绪";
            case STAGE_SEND_CONFIRMED:
                return "发送确认已完成";
            default:
                return "阶段待确认";
        }
    }

    public static String sessionStatusLabel(String status) {
        switch (upper(status)) {
            case SESSION_READY:
                return "可用";
            case SESSION_BUSY:
                return "占用中";
            case SESSION_DEGRADED:
                return "待复核";
            case SESSION_BLOCKED:
                return "被阻断";
            case SESSION_EXPIRED:
                return "已失效";
            default:
                return "未知";
        }
    }

    public static boolean isActiveStatus(String status) {
        String normalized = upper(status);
        return STATUS_PENDING.equals(normalized)
                || STATUS_RUNNING.equals(normalized)
                || STATUS_RETRYING.equals(normalized)
                || STATUS_SENT.equals(normalized)
                || STATUS_CHATTING.equals(normalized);
    }

    public static String upper(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed.toUpperCase(Locale.ROOT);
    }
}
