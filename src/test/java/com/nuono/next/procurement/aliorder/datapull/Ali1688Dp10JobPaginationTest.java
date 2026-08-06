package com.nuono.next.procurement.aliorder.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Ali1688Dp10JobPaginationTest extends Ali1688Dp10JobTestSupport {

    @Test
    void providerPageSizeAccepts100AndRejectsZeroOr101BeforeAnyTaskRuns() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider accepted = new ScriptedProvider();
        accepted.pageSize = 100;
        DataPullTask task = task(authorization);
        Ali1688Dp10Job acceptedJob = job(
                scopeSource(authorization), accepted, new Ali1688Dp10InMemoryStageStore(),
                new RecordingWriter(), progress(false, null, 0L));

        AdvanceResult initialized = acceptedJob.advance(context(task));
        Ali1688Dp10Checkpoint checkpoint = new Ali1688Dp10CheckpointCodec(
                new com.fasterxml.jackson.databind.ObjectMapper()).decode(
                        initialized.getCheckpoint());
        assertEquals(100, checkpoint.getPageSize());

        for (int invalid : List.of(0, 101)) {
            ScriptedProvider rejected = new ScriptedProvider();
            rejected.pageSize = invalid;
            assertThrows(IllegalArgumentException.class, () -> job(
                    scopeSource(authorization), rejected,
                    new Ali1688Dp10InMemoryStageStore(), new RecordingWriter(),
                    progress(false, null, 0L)));
        }
    }

    @Test
    void currentThenHistoryCloseBeforeOneFinalApplyAndFirstIdentityWins() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(order("SAME", OLDER, true)), 1, 1, 1));
        provider.pages.add(page(List.of(order("SAME", NEWEST, true)), 1, 1, 1));
        provider.pages.add(page(List.of(order("SAME", OLDER, true)), 1, 1, 1));
        provider.pages.add(page(List.of(order("SAME", NEWEST, true)), 1, 1, 1));
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, writer,
                progress(false, null, 0L));

        assertEquals(TaskState.SUCCEEDED, runToTerminal(job, task).getNextState());
        assertEquals(List.of(
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                Ali1688HistoricalOrderProvider.Partition.HISTORY,
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                Ali1688HistoricalOrderProvider.Partition.HISTORY
        ), provider.listRequests.stream().map(Ali1688HistoricalOrderRequest::getPartition)
                .collect(Collectors.toList()));
        assertEquals(1, writer.commands.size());
        assertEquals(List.of("SAME"), writer.batches.get(0).stream()
                .map(Ali1688HistoricalOrderProvider.OrderSnapshot::getProviderOrderNo)
                .collect(Collectors.toList()));
    }

    @Test
    void exactMultisetSealAllowsReorderingAndPreservesDuplicateMultiplicity() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pageSize = 3;
        provider.pages.add(page(List.of(
                order("A", NEWEST, true), order("A", NEWEST, true),
                order("B", OLDER, true)), 1, 3, 3));
        provider.pages.add(page(List.of(), 1, 3, 0));
        provider.pages.add(page(List.of(
                order("B", OLDER, true), order("A", NEWEST, true),
                order("A", NEWEST, true)), 1, 3, 3));
        provider.pages.add(page(List.of(), 1, 3, 0));
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, writer,
                progress(false, null, 0L));

        assertEquals(TaskState.SUCCEEDED, runToTerminal(job, task).getNextState());
        assertEquals(4, provider.listRequests.size());
        assertEquals(List.of("B", "A"), writer.batches.get(0).stream()
                .map(Ali1688HistoricalOrderProvider.OrderSnapshot::getProviderOrderNo)
                .collect(Collectors.toList()));
    }

    @Test
    void sameTotalWithOneSidedFingerprintMultiplicityRestartsBeforeDetailsOrFacts() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pageSize = 2;
        provider.pages.add(page(List.of(
                order("A", NEWEST, true), order("B", OLDER, true)), 1, 2, 2));
        provider.pages.add(page(List.of(), 1, 2, 0));
        provider.pages.add(page(List.of(
                order("A", NEWEST, true), order("A", NEWEST, true)), 1, 2, 2));
        provider.pages.add(page(List.of(), 1, 2, 0));
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, writer,
                progress(false, null, 0L));
        continueTask(task, job.advance(context(task)));
        for (int pageAdvance = 0; pageAdvance < 4; pageAdvance++) {
            continueTask(task, job.advance(context(task)));
        }

        AdvanceResult drift = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, drift.getNextState());
        assertEquals("DP10_MULTIPASS_MULTISET_DRIFT", drift.getSanitizedCode());
        assertTrue(provider.detailRequests.isEmpty());
        assertTrue(writer.commands.isEmpty());
        Ali1688Dp10Checkpoint restarted = new Ali1688Dp10CheckpointCodec(
                new com.fasterxml.jackson.databind.ObjectMapper()).decode(drift.getCheckpoint());
        assertEquals(2L, restarted.getGenerationNo());
    }

    @Test
    void onePassMalformedChildCausesRawMultisetDriftBeforeDetailOrFactWork() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        Ali1688HistoricalOrderProvider.OrderSnapshot firstPass =
                order("A", NEWEST, true);
        Ali1688HistoricalOrderProvider.OrderItemSnapshot malformed =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        malformed.setTitle("no-stable-child-identity");
        java.util.List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> children =
                new java.util.ArrayList<>(firstPass.getItems());
        children.add(malformed);
        firstPass.setItems(children);
        provider.pages.add(page(List.of(firstPass), 1, 1, 1));
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.pages.add(page(List.of(order("A", NEWEST, true)), 1, 1, 1));
        provider.pages.add(page(List.of(), 1, 1, 0));
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, writer,
                progress(false, null, 0L));
        continueTask(task, job.advance(context(task)));
        for (int pageAdvance = 0; pageAdvance < 4; pageAdvance++) {
            continueTask(task, job.advance(context(task)));
        }

        AdvanceResult drift = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, drift.getNextState());
        assertEquals("DP10_MULTIPASS_MULTISET_DRIFT", drift.getSanitizedCode());
        assertTrue(provider.detailRequests.isEmpty());
        assertTrue(writer.commands.isEmpty());
    }

    @Test
    void shrinkingTotalClearsOnlyActivePartitionAndRetriesPageOneWithSameWindow() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(order("CURRENT", NEWEST, true)), 1, 1, 1));
        provider.pages.add(page(List.of(order("HISTORY-1", OLDER, true)), 1, 1, 2));
        provider.pages.add(page(List.of(), 2, 1, 1));
        provider.pages.add(page(List.of(order("CURRENT-RETRY", NEWEST, true)), 1, 1, 1));
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, new RecordingWriter(),
                progress(false, null, 0L));

        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        InstantWindow original = InstantWindow.of(provider.listRequests.get(1));

        AdvanceResult drift = job.advance(context(task));
        assertEquals(TaskState.WAITING_BACKOFF, drift.getNextState());
        assertEquals("DP10_PARTITION_TOTAL_DRIFT", drift.getSanitizedCode());
        Ali1688Dp10Checkpoint restarted = new Ali1688Dp10CheckpointCodec(
                new com.fasterxml.jackson.databind.ObjectMapper()).decode(drift.getCheckpoint());
        assertEquals(2L, restarted.getGenerationNo());
        assertEquals(1, restarted.getScanPass());
        assertEquals(Ali1688HistoricalOrderProvider.Partition.CURRENT, restarted.getPartition());
        continueTask(task, drift);

        AdvanceResult recovered = job.advance(context(task));
        Ali1688HistoricalOrderRequest retried = provider.listRequests.get(3);
        assertEquals(Ali1688HistoricalOrderProvider.Partition.CURRENT, retried.getPartition());
        assertEquals(1, retried.getPageNo());
        assertEquals(original.start, retried.getModifiedFrom());
        assertEquals(original.end, retried.getModifiedTo());
    }

    @Test
    void shortPageClearsCurrentPartitionInsteadOfApplyingPartialFacts() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pageSize = 2;
        provider.pages.add(page(List.of(order("ONLY-ONE", NEWEST, true)), 1, 2, 3));
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, writer,
                progress(false, null, 0L));
        continueTask(task, job.advance(context(task)));

        AdvanceResult rejected = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, rejected.getNextState());
        assertEquals("DP10_PAGE_RAW_ROW_COUNT_INVALID", rejected.getSanitizedCode());
        Ali1688Dp10Checkpoint restarted = new Ali1688Dp10CheckpointCodec(
                new com.fasterxml.jackson.databind.ObjectMapper()).decode(rejected.getCheckpoint());
        assertEquals(2L, restarted.getGenerationNo());
        assertTrue(writer.commands.isEmpty());
    }

    private static final class InstantWindow {
        private final java.time.Instant start;
        private final java.time.Instant end;

        private InstantWindow(java.time.Instant start, java.time.Instant end) {
            this.start = start;
            this.end = end;
        }

        private static InstantWindow of(Ali1688HistoricalOrderRequest request) {
            return new InstantWindow(request.getModifiedFrom(), request.getModifiedTo());
        }
    }
}
