package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ProcurementCandidatePoolStatusPolicy {

    static final int MAX_POOL_SIZE = 5;
    static final int CANDIDATE_SOURCE_LIMIT = 10;
    static final String STATUS_POOL_CREATED = "POOL_CREATED";
    static final String STATUS_POOL_INQUIRY_RUNNING = "POOL_INQUIRY_RUNNING";
    static final String STATUS_POOL_PARTIAL_HANDOFF = "POOL_PARTIAL_HANDOFF";
    static final String STATUS_POOL_EMPTY_REQUIRES_ACTION = "POOL_EMPTY_REQUIRES_ACTION";
    static final String STATUS_POOL_INQUIRY_FINISHED = "POOL_INQUIRY_FINISHED";
    static final String STATUS_FINAL_TWO_CONFIRMED = "FINAL_TWO_CONFIRMED";
    static final String STATUS_SUMMARY_READY = "SUMMARY_READY";
    static final String ITEM_WAITING_SEND = "IN_POOL_WAITING_SEND";
    static final String ITEM_WAITING_REPLY = "IN_POOL_WAITING_REPLY";
    static final String ITEM_FOLLOW_UP_1_SENT = "FOLLOW_UP_1_SENT";
    static final String ITEM_FOLLOW_UP_2_SENT = "FOLLOW_UP_2_SENT";
    static final String ITEM_FOLLOW_UP_3_SENT = "FOLLOW_UP_3_SENT";
    static final String ITEM_REPLIED = "REPLIED";
    static final String ITEM_PARTIAL_REPLY = "PARTIAL_REPLY";
    static final String ITEM_NO_REPLY_HANDOFF = "NO_REPLY_HANDOFF";
    static final String ITEM_REPLY_PARSE_FAILED = "REPLY_PARSE_FAILED";
    static final String ITEM_REMOVED_TERMINATED = "REMOVED_TERMINATED";
    static final String JOIN_SYSTEM_AUTO = "SYSTEM_AUTO";
    static final String JOIN_BUYER_MANUAL = "BUYER_MANUAL";
    static final String PICK_PRIMARY = "PRIMARY";
    static final String PICK_BACKUP = "BACKUP";

    private static final Set<String> POOL_ITEM_CHANGE_ALLOWED_STATUSES = Set.of(
            STATUS_POOL_CREATED,
            STATUS_POOL_INQUIRY_RUNNING,
            STATUS_POOL_PARTIAL_HANDOFF,
            STATUS_POOL_EMPTY_REQUIRES_ACTION
    );
    private static final Set<String> CLOSABLE_ITEM_STATUSES = Set.of(
            "REPLIED",
            "PARTIAL_REPLY",
            "NO_REPLY_HANDOFF",
            "SEND_FAILED",
            "REPLY_PARSE_FAILED",
            "CLOSED"
    );
    private static final Set<String> HANDOFF_ITEM_STATUSES = Set.of(
            "NO_REPLY_HANDOFF",
            "SEND_FAILED",
            "REPLY_PARSE_FAILED",
            "PARTIAL_REPLY"
    );

    private ProcurementCandidatePoolStatusPolicy() {
    }

    static boolean canChangePoolItem(String poolStatus) {
        return POOL_ITEM_CHANGE_ALLOWED_STATUSES.contains(upper(poolStatus));
    }

    static boolean isClosableItemStatus(String itemStatus) {
        return CLOSABLE_ITEM_STATUSES.contains(upper(itemStatus));
    }

    static String resolvePoolStatusAfterCurrentItems(List<PoolItemRow> currentItems, String fallbackStatus) {
        if (currentItems.isEmpty()) {
            return STATUS_POOL_EMPTY_REQUIRES_ACTION;
        }
        for (PoolItemRow item : currentItems) {
            if (HANDOFF_ITEM_STATUSES.contains(upper(item.getStatus()))) {
                return STATUS_POOL_PARTIAL_HANDOFF;
            }
        }
        String normalizedFallback = upper(fallbackStatus);
        if (STATUS_POOL_PARTIAL_HANDOFF.equals(normalizedFallback)) {
            return STATUS_POOL_PARTIAL_HANDOFF;
        }
        return STATUS_POOL_INQUIRY_RUNNING;
    }

    static String nextFollowUpStatus(String beforeStatus) {
        if (ITEM_WAITING_REPLY.equals(beforeStatus)) {
            return ITEM_FOLLOW_UP_1_SENT;
        }
        if (ITEM_FOLLOW_UP_1_SENT.equals(beforeStatus)) {
            return ITEM_FOLLOW_UP_2_SENT;
        }
        if (ITEM_FOLLOW_UP_2_SENT.equals(beforeStatus)) {
            return ITEM_FOLLOW_UP_3_SENT;
        }
        return null;
    }

    static String followUpSummary(String status) {
        if (ITEM_FOLLOW_UP_1_SENT.equals(status)) {
            return "15 分钟无回复，已发送第一次催发“在吗亲”。";
        }
        if (ITEM_FOLLOW_UP_2_SENT.equals(status)) {
            return "第一次催发后仍无回复，已发送第二次催发。";
        }
        if (ITEM_FOLLOW_UP_3_SENT.equals(status)) {
            return "第二次催发后仍无回复，已发送第三次催发。";
        }
        return "已推进催发状态。";
    }

    static String buildReplySummary(String quotePriceText, String quoteMoqText, String quoteDeliveryText, String status) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(quotePriceText)) {
            parts.add("报价 " + quotePriceText);
        }
        if (StringUtils.hasText(quoteMoqText)) {
            parts.add("MOQ " + quoteMoqText);
        }
        if (StringUtils.hasText(quoteDeliveryText)) {
            parts.add("交期 " + quoteDeliveryText);
        }
        if (parts.isEmpty()) {
            return ITEM_PARTIAL_REPLY.equals(status)
                    ? "已收到供应商回复，但报价字段仍不完整。"
                    : "已收到供应商回复。";
        }
        return "已回复：" + String.join("，", parts) + "。";
    }

    private static String upper(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
