package com.nuono.next.competitoranalysis;

final class CompetitorDetailTargetStaleException extends RuntimeException {
    static final String ERROR_CODE = "DETAIL_TARGET_STALE";

    CompetitorDetailTargetStaleException() {
        super("详情写入前目标已发生变化。");
    }
}
