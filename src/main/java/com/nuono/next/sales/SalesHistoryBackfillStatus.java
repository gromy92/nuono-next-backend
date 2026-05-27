package com.nuono.next.sales;

import java.util.ArrayList;
import java.util.List;

public class SalesHistoryBackfillStatus {
    public static final String COVERED = "covered";
    public static final String NEEDS_BACKFILL = "needs_backfill";
    public static final String BACKFILL_PENDING = "backfill_pending";
    public static final String BACKFILL_QUEUED = "backfill_queued";
    public static final String BACKFILL_RUNNING = "backfill_running";
    public static final String BACKFILL_FAILED = "backfill_failed";
    public static final String RETENTION_LIMITED = "retention_limited";
    public static final String MANUAL_ACTION = "manual_action";

    private final String state;
    private final String label;
    private final String message;
    private final boolean actionAvailable;
    private final List<Long> gapIds;
    private final List<Long> taskIds;
    private final List<String> categories;

    public SalesHistoryBackfillStatus(
            String state,
            String label,
            String message,
            boolean actionAvailable,
            List<Long> gapIds,
            List<Long> taskIds,
            List<String> categories
    ) {
        this.state = state;
        this.label = label;
        this.message = message;
        this.actionAvailable = actionAvailable;
        this.gapIds = gapIds == null ? List.of() : List.copyOf(gapIds);
        this.taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
        this.categories = categories == null ? List.of() : List.copyOf(categories);
    }

    public static SalesHistoryBackfillStatus covered() {
        return new SalesHistoryBackfillStatus(
                COVERED,
                "历史范围完整",
                "当前选择范围已被真实销量与订单价格数据覆盖。",
                false,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public static SalesHistoryBackfillStatus needsBackfill() {
        return new SalesHistoryBackfillStatus(
                NEEDS_BACKFILL,
                "需要历史补全",
                "当前选择范围早于已接入的真实数据，可提交历史补全任务。",
                true,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public static SalesHistoryBackfillStatus of(
            String state,
            List<Long> gapIds,
            List<Long> taskIds,
            List<String> categories
    ) {
        String label;
        String message;
        boolean actionAvailable = false;
        if (BACKFILL_RUNNING.equals(state)) {
            label = "历史补全运行中";
            message = "历史补全任务正在执行，完成后会写入真实事实表。";
        } else if (BACKFILL_QUEUED.equals(state)) {
            label = "历史补全已排队";
            message = "历史补全任务已创建，等待调度执行。";
        } else if (BACKFILL_PENDING.equals(state)) {
            label = "历史补全待调度";
            message = "历史补全缺口已记录，等待生成拉取任务。";
            actionAvailable = true;
        } else if (BACKFILL_FAILED.equals(state)) {
            label = "历史补全失败";
            message = "历史补全任务失败，可重试的缺口会允许再次提交。";
            actionAvailable = true;
        } else if (RETENTION_LIMITED.equals(state)) {
            label = "超出平台保留期";
            message = "Noon 已不提供该范围的历史数据，不能自动补全。";
        } else if (MANUAL_ACTION.equals(state)) {
            label = "需要人工处理";
            message = "该历史缺口被标记为需要人工处理，暂不能自动补全。";
        } else {
            label = "需要历史补全";
            message = "当前选择范围早于已接入的真实数据，可提交历史补全任务。";
            actionAvailable = true;
        }
        return new SalesHistoryBackfillStatus(
                state,
                label,
                message,
                actionAvailable,
                safe(gapIds),
                safe(taskIds),
                categories == null ? List.of() : categories
        );
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }

    public String getState() {
        return state;
    }

    public String getLabel() {
        return label;
    }

    public String getMessage() {
        return message;
    }

    public boolean isActionAvailable() {
        return actionAvailable;
    }

    public List<Long> getGapIds() {
        return gapIds;
    }

    public List<Long> getTaskIds() {
        return taskIds;
    }

    public List<String> getCategories() {
        return categories;
    }
}
