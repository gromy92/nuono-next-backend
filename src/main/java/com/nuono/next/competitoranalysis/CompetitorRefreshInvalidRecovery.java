package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import java.time.LocalDateTime;

final class CompetitorRefreshInvalidRecovery {
    private static final String DETAIL_CODE = "INVALID_DETAIL_RETRY_PAYLOAD";
    private static final String DETAIL_MESSAGE =
            "陈旧竞品详情任务缺少可信的目标重试状态，已终止以避免全量重抓。";
    private static final String GENERIC_CODE = "INVALID_REFRESH_RECOVERY_PAYLOAD";
    private static final String GENERIC_MESSAGE =
            "陈旧竞品刷新任务的恢复身份或载荷无效，已安全终止。";

    private final CompetitorRefreshTaskFactory taskFactory;

    CompetitorRefreshInvalidRecovery(CompetitorRefreshTaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    boolean failQueued(OperationalTask task, CompetitorSearchRunRow run) {
        if (task == null || task.getId() == null || run == null
                || run.getId() == null || run.getWatchProductId() == null) {
            return false;
        }
        return taskFactory.executionFinalizer().failQueued(
                task.getId(),
                run.getId(),
                run.getWatchProductId(),
                GENERIC_CODE,
                GENERIC_MESSAGE
        );
    }

    boolean failStale(
            OperationalTask task,
            CompetitorSearchRunRow run,
            LocalDateTime staleBefore,
            boolean detailRetry
    ) {
        return taskFactory.failStale(
                task,
                run,
                staleBefore,
                detailRetry ? DETAIL_CODE : GENERIC_CODE,
                detailRetry ? DETAIL_MESSAGE : GENERIC_MESSAGE
        );
    }
}
