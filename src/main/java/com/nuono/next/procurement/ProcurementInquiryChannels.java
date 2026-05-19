package com.nuono.next.procurement;

final class ProcurementInquiryChannels {

    static final String ALI_AI_BULK_INQUIRY = "ALI_AI_BULK_INQUIRY";
    static final String ALI_UNPAID_ORDER_INQUIRY = "ALI_UNPAID_ORDER_INQUIRY";
    static final String NUONO_CHAT_INQUIRY = "NUONO_CHAT_INQUIRY";
    static final String CHAT_THREAD_REPLY_SOURCE = "CHAT_THREAD";
    static final String REPLY_PARSE_PENDING = "PENDING";
    static final String AI_BULK_ADAPTER_NOT_ENABLED =
            "1688 AI bulk inquiry adapter is not enabled; fallback to Nuono chat inquiry.";
    static final String UNPAID_ORDER_ADAPTER_NOT_ENABLED =
            "1688 unpaid order adapter is not enabled; fallback to Nuono chat inquiry.";

    private ProcurementInquiryChannels() {
    }
}
