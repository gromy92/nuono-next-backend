package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class ProcurementPoolAutoInquiryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcurementPoolAutoInquiryScheduler.class);

    private final ProcurementRequirementConfirmationMapper mapper;
    private final LocalDbProcurementCandidatePoolService candidatePoolService;
    private final ProcurementAutoInquiryExecutor autoInquiryExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${nuono.procurement.auto-inquiry.scheduler.enabled:false}")
    private boolean enabled;

    @Value("${nuono.procurement.auto-inquiry.scheduler.dispatch-enabled:false}")
    private boolean dispatchEnabled;

    @Value("${nuono.procurement.auto-inquiry.scheduler.max-items-per-tick:10}")
    private int maxItemsPerTick;

    @Value("${nuono.procurement.auto-inquiry.scheduler.system-operator-user-id:0}")
    private Long systemOperatorUserId;

    public ProcurementPoolAutoInquiryScheduler(
            ProcurementRequirementConfirmationMapper mapper,
            LocalDbProcurementCandidatePoolService candidatePoolService,
            ProcurementAutoInquiryExecutor autoInquiryExecutor
    ) {
        this.mapper = mapper;
        this.candidatePoolService = candidatePoolService;
        this.autoInquiryExecutor = autoInquiryExecutor;
    }

    @Scheduled(
            initialDelayString = "${nuono.procurement.auto-inquiry.scheduler.initial-delay-ms:15000}",
            fixedDelayString = "${nuono.procurement.auto-inquiry.scheduler.fixed-delay-ms:60000}"
    )
    public void tick() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            int limit = Math.max(1, maxItemsPerTick);
            if (dispatchEnabled) {
                dispatchPendingPoolItems(limit);
            }
            handoffNoReplyPoolItems(limit);
            advanceDueFollowUps(limit);
        } finally {
            running.set(false);
        }
    }

    private void dispatchPendingPoolItems(int limit) {
        List<PoolItemLockRow> items = mapper.listPendingPoolItemsForAutoInquiryDispatch(limit);
        for (PoolItemLockRow item : items) {
            if (item.getInquiryTaskId() == null) {
                continue;
            }
            try {
                autoInquiryExecutor.execute(item.getInquiryTaskId(), systemOperatorUserId);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Failed to dispatch procurement auto inquiry task. poolItemId={}, taskId={}",
                        item.getPoolItemId(),
                        item.getInquiryTaskId(),
                        exception
                );
            } finally {
                mapper.syncPoolItemFromAutoInquiryTask(
                        item.getPoolItemId(),
                        item.getInquiryTaskId(),
                        systemOperatorUserId
                );
            }
        }
    }

    private void handoffNoReplyPoolItems(int limit) {
        List<PoolItemLockRow> items = mapper.listDuePoolItemsForNoReplyHandoff(limit);
        for (PoolItemLockRow item : items) {
            try {
                candidatePoolService.markNoReplyHandoffBySystem(
                        item.getOwnerUserId(),
                        item.getDemandItemId(),
                        item.getPoolItemId(),
                        item.getStatus(),
                        "24 小时无回复，系统自动转人工介入。",
                        "24 小时内供应商无回复，已要求人工介入。",
                        "自动询价超时未收到回复。"
                );
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Failed to handoff no-reply procurement pool item. demandItemId={}, poolItemId={}",
                        item.getDemandItemId(),
                        item.getPoolItemId(),
                        exception
                );
            }
        }
    }

    private void advanceDueFollowUps(int limit) {
        List<PoolItemLockRow> items = mapper.listDuePoolItemsForFollowUp(limit);
        for (PoolItemLockRow item : items) {
            try {
                candidatePoolService.advancePoolItemFollowUpBySystem(
                        item.getOwnerUserId(),
                        item.getDemandItemId(),
                        item.getPoolItemId(),
                        item.getStatus(),
                        "自动催发节点到期，系统推进催发状态。"
                );
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Failed to advance procurement pool follow-up. demandItemId={}, poolItemId={}",
                        item.getDemandItemId(),
                        item.getPoolItemId(),
                        exception
                );
            }
        }
    }
}
