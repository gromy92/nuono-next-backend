package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportCsvParser.ReceivedRow;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnLineMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryLineProductMatchRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class OfficialWarehouseFbnImportSupport {
    private OfficialWarehouseFbnImportSupport() {
    }

    static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content == null ? new byte[0] : content);
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256。", exception);
        }
    }

    static boolean isComplete(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "COMPLETE".equals(normalized)
                || "COMPLETED".equals(normalized)
                || "SUCCESS".equals(normalized)
                || "READY".equals(normalized)
                || "DONE".equals(normalized);
    }

    static Long requireOwnerUserId(BusinessAccessContext access, String storeCode) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("无法识别当前业务老板账号。");
        }
        return ownerUserId;
    }

    static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    static String minDate(String left, String right) {
        if (!StringUtils.hasText(right)) {
            return left;
        }
        if (!StringUtils.hasText(left)) {
            return right;
        }
        return left.compareTo(right) <= 0 ? left : right;
    }

    static String maxDate(String left, String right) {
        if (!StringUtils.hasText(right)) {
            return left;
        }
        if (!StringUtils.hasText(left)) {
            return right;
        }
        return left.compareTo(right) >= 0 ? left : right;
    }

    static Long firstLong(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    static String matchStatus(
            InboundReceiptAsnMatchRecord asnMatch,
            InboundReceiptAsnLineMatchRecord lineMatch,
            InventoryLineProductMatchRecord productMatch
    ) {
        if (asnMatch == null) {
            return "NO_LOCAL_ASN";
        }
        if (lineMatch != null) {
            return "MATCHED";
        }
        return productMatch == null ? "PRODUCT_UNMATCHED" : "LINE_UNMATCHED";
    }

    static String receiptStatus(ReceivedRow row) {
        if (row.qcFailedQty > 0) {
            return "QC_FAILED";
        }
        if (row.unidentifiedQty > 0) {
            return "UNIDENTIFIED";
        }
        if (row.receivedQty < row.qtyExpected) {
            return "SHORT_RECEIVED";
        }
        return row.receivedQty > row.qtyExpected ? "OVER_RECEIVED" : "NORMAL";
    }
}
