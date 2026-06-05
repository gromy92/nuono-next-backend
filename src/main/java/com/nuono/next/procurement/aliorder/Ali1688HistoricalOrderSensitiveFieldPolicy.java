package com.nuono.next.procurement.aliorder;

import com.nuono.next.permission.access.BusinessAccessContext;

public final class Ali1688HistoricalOrderSensitiveFieldPolicy {

    private Ali1688HistoricalOrderSensitiveFieldPolicy() {
    }

    public static Ali1688HistoricalOrderWorkbenchView.SensitiveFieldsView apply(
            BusinessAccessContext context,
            Ali1688HistoricalOrderRow row
    ) {
        Ali1688HistoricalOrderWorkbenchView.SensitiveFieldsView view =
                new Ali1688HistoricalOrderWorkbenchView.SensitiveFieldsView();
        if (context != null && context.isBossAccount()) {
            view.setRedactionLevel("masked");
            view.setReceiverPhone(maskPhone(receiverPhone(row)));
            view.setReceiverAddress(maskAddress(row == null ? null : row.getReceiverAddress()));
            view.setBuyerRemark("已隐藏");
            view.setSupplierContact("已隐藏");
            return view;
        }

        view.setRedactionLevel("hidden");
        view.setReceiverPhone("已隐藏");
        view.setReceiverAddress("已隐藏");
        view.setBuyerRemark("已隐藏");
        view.setSupplierContact("已隐藏");
        return view;
    }

    private static String receiverPhone(Ali1688HistoricalOrderRow row) {
        if (row == null) {
            return null;
        }
        if (row.getReceiverMobile() != null && !row.getReceiverMobile().isBlank()) {
            return row.getReceiverMobile();
        }
        if (row.getReceiverPhone() != null && !row.getReceiverPhone().isBlank()) {
            return row.getReceiverPhone();
        }
        return row.getReceiverTelephone();
    }

    private static String maskPhone(String value) {
        if (value == null || value.isBlank()) {
            return "未返回";
        }
        String normalized = value.trim();
        if (normalized.length() < 7) {
            return "已脱敏";
        }
        return normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4);
    }

    private static String maskAddress(String value) {
        if (value == null || value.isBlank()) {
            return "未返回";
        }
        String normalized = value.trim();
        if (normalized.length() <= 6) {
            return "已脱敏";
        }
        return normalized.substring(0, Math.min(6, normalized.length())) + "***";
    }
}
