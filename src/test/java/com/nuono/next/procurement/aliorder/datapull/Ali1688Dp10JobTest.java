package com.nuono.next.procurement.aliorder.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderFailureCode;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Ali1688Dp10JobTest extends Ali1688Dp10JobTestSupport {

    @Test
    void fullOmitsStartAndIncrementalFreezesHighWaterOverlapAndEndForBothPartitions() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider fullProvider = twoEmptyPartitions();
        DataPullTask fullTask = task(authorization);
        Ali1688Dp10Job fullJob = job(
                scopeSource(authorization), fullProvider, new Ali1688Dp10InMemoryStageStore(),
                new RecordingWriter(), progress(false, null, 0L));
        runToTerminal(fullJob, fullTask);

        assertEquals(4, fullProvider.listRequests.size());
        assertTrue(fullProvider.listRequests.stream()
                .allMatch(request -> request.getMode()
                        == Ali1688HistoricalOrderProvider.SyncMode.FULL));
        assertTrue(fullProvider.listRequests.stream()
                .allMatch(request -> request.getModifiedFrom() == null));
        assertTrue(fullProvider.listRequests.stream().allMatch(request ->
                request.getModifiedTo().equals(fullProvider.listRequests.get(0).getModifiedTo())));

        ScriptedProvider incrementalProvider = twoEmptyPartitions();
        DataPullTask incrementalTask = task(authorization);
        Ali1688Dp10Job incrementalJob = job(
                scopeSource(authorization), incrementalProvider,
                new Ali1688Dp10InMemoryStageStore(), new RecordingWriter(),
                progress(true, OLDER, 1L));
        runToTerminal(incrementalJob, incrementalTask);

        assertTrue(incrementalProvider.listRequests.stream().allMatch(request ->
                request.getModifiedFrom().equals(OLDER.minus(Duration.ofHours(24)))));
        assertEquals(incrementalProvider.listRequests.get(0).getModifiedTo(),
                incrementalProvider.listRequests.get(1).getModifiedTo());
    }

    @Test
    void tokenRefreshAndListUseSeparateAdvances() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = twoEmptyPartitions();
        provider.refreshRequired = true;
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, new Ali1688Dp10InMemoryStageStore(),
                new RecordingWriter(), progress(false, null, 0L));
        continueTask(task, job.advance(context(task)));

        AdvanceResult refreshed = job.advance(context(task));

        assertEquals(Ali1688Dp10Job.LIST_STEP, refreshed.getStepCode());
        assertEquals(1, provider.refreshRequests);
        assertTrue(provider.listRequests.isEmpty());
        continueTask(task, refreshed);
        job.advance(context(task));
        assertEquals(1, provider.listRequests.size());
    }

    @Test
    void everyAdvanceMakesAtMostOneExternalCall() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(order("ONE", NEWEST, false)), 1, 1, 1));
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.pages.add(page(List.of(order("ONE", NEWEST, false)), 1, 1, 1));
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.details.add(Ali1688HistoricalOrderProvider.DetailResult.success(
                order("ONE", NEWEST, true)
        ));
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider,
                new Ali1688Dp10InMemoryStageStore(), new RecordingWriter(),
                progress(false, null, 0L)
        );

        for (int advance = 0; advance < 30; advance++) {
            int before = provider.listRequests.size()
                    + provider.detailRequests.size()
                    + provider.refreshRequests;
            AdvanceResult result = job.advance(context(task));
            int after = provider.listRequests.size()
                    + provider.detailRequests.size()
                    + provider.refreshRequests;
            assertTrue(after - before <= 1, "one advance exceeded its external-call budget");
            if (result.getNextState() == TaskState.SUCCEEDED) return;
            continueTask(task, result);
        }
        throw new AssertionError("DP10 did not complete within the bounded test advances");
    }

    @Test
    void detailsNeverStartUntilCurrentAndHistoryListsAreBothClosed() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(order("NEEDS-DETAIL", NEWEST, false)), 1, 1, 1));
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.pages.add(page(List.of(order("NEEDS-DETAIL", NEWEST, false)), 1, 1, 1));
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.details.add(Ali1688HistoricalOrderProvider.DetailResult.success(
                order("NEEDS-DETAIL", NEWEST, true)));
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, writer,
                progress(false, null, 0L));
        continueTask(task, job.advance(context(task)));

        AdvanceResult terminal = runToTerminal(job, task);

        assertEquals(TaskState.SUCCEEDED, terminal.getNextState());
        assertEquals(List.of("NEEDS-DETAIL"), provider.detailRequests);
        assertEquals(1, writer.commands.size());
        assertEquals(List.of("NEEDS-DETAIL"), writer.batches.get(0).stream()
                .map(Ali1688HistoricalOrderProvider.OrderSnapshot::getProviderOrderNo)
                .collect(Collectors.toList()));
    }

    @Test
    void invalidFirstDuplicateDoesNotPreventLaterValidOrderFromWinning() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(order("SAME", NEWEST, false)), 1, 1, 1));
        provider.pages.add(page(List.of(order("SAME", OLDER, true)), 1, 1, 1));
        provider.pages.add(page(List.of(order("SAME", NEWEST, false)), 1, 1, 1));
        provider.pages.add(page(List.of(order("SAME", OLDER, true)), 1, 1, 1));
        provider.details.add(Ali1688HistoricalOrderProvider.DetailResult.notFound());
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, writer,
                progress(false, null, 0L));
        assertEquals(TaskState.SUCCEEDED, runToTerminal(job, task).getNextState());
        assertEquals(List.of("SAME"), writer.batches.get(0).stream()
                .map(Ali1688HistoricalOrderProvider.OrderSnapshot::getProviderOrderNo)
                .collect(Collectors.toList()));
        assertNull(writer.batches.get(0).get(0).getBuyerRemark());
    }

    @Test
    void uncertainTokenRefreshNeverReplaysThePost() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = twoEmptyPartitions();
        provider.refreshRequired = true;
        provider.refreshResult = com.nuono.next.procurement.aliorder
                .Ali1688HistoricalOrderAuthorizationRefreshResult.failure(
                        Ali1688HistoricalOrderFailureCode.AUTH_REFRESH_OUTCOME_UNKNOWN, null);
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, new Ali1688Dp10InMemoryStageStore(),
                new RecordingWriter(), progress(false, null, 0L));
        continueTask(task, job.advance(context(task)));

        AdvanceResult waiting = job.advance(context(task));
        task.setSanitizedFailureCode(waiting.getSanitizedCode());
        continueTask(task, waiting);
        AdvanceResult poll = job.advance(context(task));

        assertEquals(TaskState.WAITING_AUTH, poll.getNextState());
        assertEquals(1, provider.refreshRequests);
        assertTrue(provider.listRequests.isEmpty());
    }

    private ScriptedProvider twoEmptyPartitions() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.pages.add(page(List.of(), 1, 1, 0));
        return provider;
    }
}
