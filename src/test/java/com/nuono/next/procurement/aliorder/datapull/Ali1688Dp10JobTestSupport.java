package com.nuono.next.procurement.aliorder.datapull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgress;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressCommit;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressStore;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRefreshResult;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderRequest;
import com.nuono.next.procurement.aliorder.Ali1688PaginationMath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

abstract class Ali1688Dp10JobTestSupport {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 4, 0);
    static final Instant NEWEST = Instant.parse("2026-08-02T03:00:00Z");
    static final Instant OLDER = Instant.parse("2026-08-01T03:00:00Z");

    Ali1688Dp10Job job(
            Ali1688Dp10ScopeSource scopes,
            ScriptedProvider provider,
            Ali1688Dp10InMemoryStageStore stage,
            RecordingWriter writer,
            MutableProgressStore progress
    ) {
        writer.stage = stage;
        return new Ali1688Dp10Job(
                scopes,
                provider,
                stage,
                stage,
                writer,
                progress,
                new ProviderWaitTransition(new BackoffPolicy(
                        Duration.ofMinutes(1), Duration.ofHours(1), 0.0d
                )),
                new ObjectMapper()
        );
    }

    Ali1688Dp10ScopeSource scopeSource(Ali1688HistoricalOrderAuthorizationRow authorization) {
        Ali1688Dp10RuntimeMapper mapper = mock(Ali1688Dp10RuntimeMapper.class);
        when(mapper.listEffectiveOpenApiAuthorizations()).thenReturn(List.of(authorization));
        return new Ali1688Dp10ScopeSource(mapper);
    }

    DataPullTask task(Ali1688HistoricalOrderAuthorizationRow authorization) {
        DataPullTask task = DataPullTask.queued(
                10_001L,
                OperationCode.DP10,
                Ali1688Dp10ScopeIdentity.PROVIDER_CHANNEL,
                authorization.getOwnerUserId(),
                null,
                Ali1688Dp10ScopeIdentity.accountKey(authorization),
                null,
                null,
                null,
                null,
                Ali1688Dp10ScopeIdentity.scopeKey(authorization),
                LocalDateTime.of(2026, 8, 1, 19, 0),
                "DP10:full-then-high-watermark-incremental:2026-08-02",
                Ali1688Dp10Job.INITIAL_STEP,
                NOW.minusHours(2)
        );
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(NOW.plusHours(1));
        task.setFenceEpoch(1L);
        task.setVersion(1L);
        return task;
    }

    void continueTask(DataPullTask task, AdvanceResult result) {
        task.setStepCode(result.getStepCode());
        task.setCheckpoint(result.getCheckpoint());
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(task.getFenceEpoch() + 1L);
        task.setVersion(task.getVersion() + 1L);
    }

    ExecutionContext context(DataPullTask task) {
        task.setState(TaskState.RUNNING);
        return new ExecutionContext(task, NOW);
    }

    AdvanceResult runToTerminal(Ali1688Dp10Job job, DataPullTask task) {
        for (int advance = 0; advance < 100; advance++) {
            AdvanceResult result = job.advance(context(task));
            if (result.getNextState() == TaskState.SUCCEEDED
                    || result.getNextState() == TaskState.FAILED) return result;
            continueTask(task, result);
        }
        throw new AssertionError("DP10 test task did not terminate within 100 advances");
    }

    Ali1688HistoricalOrderAuthorizationRow authorization() {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(91_001L);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("member-307");
        row.setStatus("authorized");
        return row;
    }

    MutableProgressStore progress(boolean full, Instant highWater, long version) {
        DataPullScopeProgress progress = DataPullScopeProgress.initial(
                OperationCode.DP10,
                Ali1688Dp10ScopeIdentity.scopeKey(authorization()),
                NOW.minusDays(2)
        );
        progress.setInitialFullCompleted(full);
        progress.setOfficialModifiedHighWaterUtc(
                highWater == null ? null : LocalDateTime.ofInstant(highWater, ZoneOffset.UTC)
        );
        progress.setVersion(version);
        progress.setUpdatedAt(NOW.minusDays(1));
        return new MutableProgressStore(progress);
    }

    Ali1688HistoricalOrderProvider.Page page(
            List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders,
            int pageNo,
            int pageSize,
            long totalRecord
    ) {
        Ali1688HistoricalOrderProvider.Page page =
                new Ali1688HistoricalOrderProvider.Page(orders);
        page.setContainerProven(true);
        page.setPaginationProven(true);
        page.setPageNo(pageNo);
        page.setPageSize(pageSize);
        page.setTotalRecord(totalRecord);
        int pages = Ali1688PaginationMath.expectedPages(totalRecord, pageSize);
        page.setExpectedPages(pages);
        page.setHasMore(pageNo < pages);
        page.setNextCursor(pageNo < pages ? String.valueOf(pageNo + 1) : null);
        return page;
    }

    Ali1688HistoricalOrderProvider.OrderSnapshot order(
            String number,
            Instant modifiedAt,
            boolean withItem
    ) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo(number);
        order.setProviderModifiedAt(modifiedAt);
        if (withItem) {
            Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                    new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
            item.setOfferId("offer-" + number);
            order.setItems(List.of(item));
        } else {
            order.setItems(List.of());
        }
        return order;
    }

    static final class ScriptedProvider implements Ali1688HistoricalOrderProvider {
        final Deque<Page> pages = new ArrayDeque<>();
        final Deque<DetailResult> details = new ArrayDeque<>();
        final List<Ali1688HistoricalOrderRequest> listRequests = new ArrayList<>();
        final List<String> detailRequests = new ArrayList<>();
        boolean refreshRequired;
        int refreshRequests;
        RuntimeException listFailure;
        RuntimeException detailFailure;
        Ali1688HistoricalOrderAuthorizationRefreshResult refreshResult =
                Ali1688HistoricalOrderAuthorizationRefreshResult.success();
        int pageSize = 1;

        @Override
        public Page fetchPage(Ali1688HistoricalOrderAuthorizationRow authorization, String cursor) {
            throw new AssertionError("DP10 must not use the legacy provider call");
        }

        @Override
        public Page fetchOrderList(Ali1688HistoricalOrderRequest request) {
            listRequests.add(request);
            if (listFailure != null) {
                RuntimeException failure = listFailure;
                listFailure = null;
                throw failure;
            }
            return pages.removeFirst();
        }

        @Override
        public int listPageSize() {
            return pageSize;
        }

        @Override
        public DetailResult fetchOrderDetail(
                Ali1688HistoricalOrderAuthorizationRow authorization,
                String providerOrderNo
        ) {
            detailRequests.add(providerOrderNo);
            if (detailFailure != null) {
                RuntimeException failure = detailFailure;
                detailFailure = null;
                throw failure;
            }
            return details.removeFirst();
        }

        @Override
        public boolean requiresAuthorizationRefresh(
                Ali1688HistoricalOrderAuthorizationRow authorization
        ) {
            return refreshRequired;
        }

        @Override
        public Ali1688HistoricalOrderAuthorizationRefreshResult refreshAuthorization(
                Ali1688HistoricalOrderAuthorizationRow authorization
        ) {
            refreshRequests++;
            if (refreshResult.isSuccess()) refreshRequired = false;
            return refreshResult;
        }
    }

    static final class RecordingWriter implements Ali1688Dp10FactWriter {
        final List<Ali1688Dp10ApplyCommand> commands = new ArrayList<>();
        final List<List<Ali1688HistoricalOrderProvider.OrderSnapshot>> batches = new ArrayList<>();
        Ali1688Dp10InMemoryStageStore stage;
        RuntimeException applyFailure;
        boolean progressConflict;

        @Override
        public Ali1688Dp10FactAdvance advance(Ali1688Dp10ApplyCommand command) {
            if (applyFailure != null) {
                RuntimeException failure = applyFailure;
                applyFailure = null;
                throw failure;
            }
            if (progressConflict) {
                progressConflict = false;
                throw new Ali1688Dp10ProgressConflictException();
            }
            if (Ali1688Dp10Job.VERIFY_STEP.equals(command.getTask().getStepCode())) {
                return Ali1688Dp10FactAdvance.APPLYING;
            }
            commands.add(command);
            batches.add(stage.acceptedForTest(command.getGenerationNo()));
            return Ali1688Dp10FactAdvance.COMPLETE;
        }
    }

    static final class MutableProgressStore implements DataPullScopeProgressStore {
        final DataPullScopeProgress current;

        MutableProgressStore(DataPullScopeProgress current) { this.current = current; }

        @Override
        public DataPullScopeProgress getOrCreate(
                OperationCode operationCode,
                String scopeKey,
                LocalDateTime nowUtc
        ) {
            return current;
        }

        @Override
        public Optional<DataPullScopeProgress> commitCompletedWindow(
                DataPullScopeProgressCommit commit
        ) {
            return Optional.of(current);
        }
    }

}
