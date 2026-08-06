package com.nuono.next.competitoranalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.dp08.Dp08FactWriter;
import com.nuono.next.competitoranalysis.dp08.Dp08KeywordScope;
import com.nuono.next.competitoranalysis.dp08.Dp08ListTarget;
import com.nuono.next.competitoranalysis.dp08.Dp08TaskFenceRow;
import com.nuono.next.competitoranalysis.dp08.Dp08TrackedProduct;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorListingObservationMapper;
import com.nuono.next.infrastructure.mapper.Dp08RuntimeMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Dp08ImmutableFactWriterTest {

    @Test
    void rankingUsesTheTaskBoundIdentityAfterTheLiveKeywordWasDeletedOrReplaced() {
        Dp08RuntimeMapper runtime = mock(Dp08RuntimeMapper.class);
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        Dp08ImmutableRankingPageWriter pageWriter = mock(Dp08ImmutableRankingPageWriter.class);
        DataPullTask task = runningTask(OperationCode.DP08A, 71L);
        stubFence(runtime, task);
        when(mapper.nextSearchRunId()).thenReturn(501L);
        when(mapper.nextKeywordRunId()).thenReturn(601L);
        when(runtime.selectAppliedKeywordRun(
                30L, LocalDateTime.of(2026, 8, 2, 0, 0)
        )).thenReturn(null);
        when(mapper.insertSearchRun(any())).thenReturn(1);
        when(mapper.insertKeywordRun(any())).thenReturn(1);
        when(runtime.completeRankSearchRun(501L, 0, 4)).thenReturn(1);
        CompetitorKeywordRefreshOutcome outcome = CompetitorKeywordRefreshOutcome.success(200);
        outcome.setCandidateUpsertedCount(0);
        outcome.setRankFactWrittenCount(4);
        when(pageWriter.apply(any(), any(), any())).thenReturn(outcome);
        Dp08KeywordScope oldScope = keywordScope();
        NoonSearchPage page = new NoonSearchPage();
        LocalDateTime actualCapturedAt = LocalDateTime.of(2026, 8, 3, 9, 17);
        page.setCapturedAt(actualCapturedAt);

        Dp08FactWriter.ApplyResult result = new Dp08RankingFactTransaction(
                runtime, mapper, pageWriter
        ).apply(task, oldScope, page);

        assertThat(result).isEqualTo(Dp08FactWriter.ApplyResult.APPLIED);
        ArgumentCaptor<CompetitorKeywordRefreshContext> context =
                ArgumentCaptor.forClass(CompetitorKeywordRefreshContext.class);
        verify(pageWriter).apply(context.capture(), any(), any());
        assertThat(context.getValue().getWatchProduct().getId()).isEqualTo(20L);
        assertThat(context.getValue().getWatchProduct().getSelfNoonProductCode())
                .isEqualTo("N700001");
        assertThat(context.getValue().getKeyword().getId()).isEqualTo(30L);
        assertThat(context.getValue().getKeyword().getKeyword()).isEqualTo("paper");
        ArgumentCaptor<CompetitorKeywordRunInsertCommand> keywordRun =
                ArgumentCaptor.forClass(CompetitorKeywordRunInsertCommand.class);
        verify(mapper).insertKeywordRun(keywordRun.capture());
        assertThat(keywordRun.getValue().getCapturedAt()).isEqualTo(actualCapturedAt);
        assertThat(keywordRun.getValue().getCapturedAt()).isNotEqualTo(
                task.getScheduleSlot().toInstant(ZoneOffset.UTC)
                        .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime()
        );
        verify(mapper, never()).selectWatchProductForRefresh(any());
        verify(mapper, never()).selectKeywordById(any());
    }

    @Test
    void listFoundWritesSnapshotsToOldReferencesWithoutReadingTheLiveCatalog() {
        Dp08RuntimeMapper runtime = mock(Dp08RuntimeMapper.class);
        CompetitorListingObservationMapper observations =
                mock(CompetitorListingObservationMapper.class);
        CompetitorProductSnapshotService snapshots =
                mock(CompetitorProductSnapshotService.class);
        DataPullTask task = runningTask(OperationCode.DP08B, 72L);
        stubFence(runtime, task);
        when(observations.nextListingObservationId()).thenReturn(701L);
        when(runtime.upsertListFound(any())).thenReturn(1);
        Dp08ListTarget oldTarget = listTarget();
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode("N700002");
        detail.setCapturedAt(LocalDateTime.of(2026, 8, 2, 2, 5));

        Dp08FactWriter.ApplyResult result = new Dp08ListFactTransaction(
                runtime, observations, snapshots
        ).applyFound(task, oldTarget, detail);

        assertThat(result).isEqualTo(Dp08FactWriter.ApplyResult.APPLIED);
        ArgumentCaptor<CompetitorWatchProductRow> watches =
                ArgumentCaptor.forClass(CompetitorWatchProductRow.class);
        ArgumentCaptor<CompetitorProductRow> products =
                ArgumentCaptor.forClass(CompetitorProductRow.class);
        verify(snapshots, org.mockito.Mockito.times(2)).recordProductDetailSnapshot(
                watches.capture(), products.capture(), org.mockito.ArgumentMatchers.same(detail),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()
        );
        assertThat(watches.getAllValues()).extracting(CompetitorWatchProductRow::getId)
                .containsExactly(20L, 21L);
        assertThat(products.getAllValues().get(0)).isNull();
        assertThat(products.getAllValues().get(1).getId()).isEqualTo(201L);
        assertThat(products.getAllValues().get(1).getWatchProductId()).isEqualTo(21L);
    }

    @Test
    void listNotFoundStillPersistsTheOldDailyIdentityAfterAllReferencesWereDeleted() {
        Dp08RuntimeMapper runtime = mock(Dp08RuntimeMapper.class);
        CompetitorListingObservationMapper observations =
                mock(CompetitorListingObservationMapper.class);
        CompetitorProductSnapshotService snapshots =
                mock(CompetitorProductSnapshotService.class);
        DataPullTask task = runningTask(OperationCode.DP08B, 73L);
        stubFence(runtime, task);
        when(observations.nextListingObservationId()).thenReturn(702L);
        when(runtime.upsertListNotFound(any())).thenReturn(1);

        Dp08FactWriter.ApplyResult result = new Dp08ListFactTransaction(
                runtime, observations, snapshots
        ).applyNotFound(task, listTarget(), new NoonSearchPage());

        assertThat(result).isEqualTo(Dp08FactWriter.ApplyResult.APPLIED);
        ArgumentCaptor<CompetitorListingObservationCommand> command =
                ArgumentCaptor.forClass(CompetitorListingObservationCommand.class);
        verify(runtime).upsertListNotFound(command.capture());
        assertThat(command.getValue().getOwnerUserId()).isEqualTo(307L);
        assertThat(command.getValue().getNoonProductCode()).isEqualTo("N700002");
        assertThat(command.getValue().getFactDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        verify(snapshots, never()).recordProductDetailSnapshot(
                any(), any(), any(), any(), any()
        );
    }

    private static Dp08KeywordScope keywordScope() {
        return new Dp08KeywordScope(
                307L, 10L, 20L, 30L, "STORE", "SA", "paper", "en-SA", "scope-a",
                List.of(
                        new Dp08TrackedProduct(
                                Dp08TrackedProduct.SubjectType.SELF, null, "N700001"
                        ),
                        new Dp08TrackedProduct(
                                Dp08TrackedProduct.SubjectType.COMPETITOR, 201L, "N700002"
                        )
                )
        );
    }

    private static Dp08ListTarget listTarget() {
        return new Dp08ListTarget(
                307L, 10L, "STORE", "SA", "N700002", "scope-b",
                LocalDate.of(2026, 8, 2), true,
                List.of(
                        new Dp08ListTarget.Reference(20L, null),
                        new Dp08ListTarget.Reference(21L, 201L)
                )
        );
    }

    private static DataPullTask runningTask(OperationCode operation, long id) {
        DataPullTask task = new DataPullTask();
        task.setId(id);
        task.setOperationCode(operation);
        task.setFenceEpoch(7L);
        task.setLeaseOwner("worker-a");
        task.setScheduleSlot(LocalDateTime.of(2026, 8, 1, 16, 0));
        return task;
    }

    private static void stubFence(Dp08RuntimeMapper mapper, DataPullTask task) {
        Dp08TaskFenceRow row = new Dp08TaskFenceRow();
        row.setId(task.getId());
        row.setOperationCode(task.getOperationCode().name());
        row.setState("RUNNING");
        row.setFenceEpoch(task.getFenceEpoch());
        row.setLeaseOwner(task.getLeaseOwner());
        row.setLeaseUntil(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        when(mapper.lockRuntimeTask(task.getId())).thenReturn(row);
        when(mapper.countLiveRuntimeTask(
                task.getId(), task.getFenceEpoch(), task.getLeaseOwner()
        )).thenReturn(1);
    }
}
