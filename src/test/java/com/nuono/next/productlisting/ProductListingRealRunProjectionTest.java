package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;


class ProductListingRealRunProjectionTest extends ProductListingRealRunServiceTest {
    @Test
    void earlierWriteStepFailureRejectsDirectReadBackRecovery() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        imageUploadFailureAfterRemoteCreateResult(),
                        successReadBackStep()
                );
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView partialWrite = service.executeSubmittedRealRunTask(submitted.getTaskId());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyRealRunReadBack(context, partialWrite.getTaskId())
        );

        assertEquals("written_verify_failed", partialWrite.getStatus());
        assertEquals("noon_write_failed", partialWrite.getFailureCode());
        assertTrue(error.getMessage().contains("不允许执行该恢复操作"));
        assertEquals(1, adapter.callCount());
        assertEquals(0, adapter.verifyReadBackCallCount());
    }

    @Test
    void successfulNoonWriteWithProjectionFailureDoesNotReportTaskSuccess() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        ProductListingService service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties,
                adapter,
                null,
                objectProvider(new ThrowingProjectionBackfill())
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView executed = service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals("written_verify_failed", executed.getStatus());
        assertEquals("projection_backfill_failed", executed.getFailureCode());
        assertTrue(executed.getFailureMessage().contains("本地商品列表同步失败"));
    }

    @Test
    void successfulNoonWriteWithProjectionNoopDoesNotReportTaskSuccess() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        ProductListingService service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties,
                adapter,
                null,
                objectProvider(new NoopSuccessfulProjectionBackfill())
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView executed = service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals("written_verify_failed", executed.getStatus());
        assertEquals("projection_backfill_failed", executed.getFailureCode());
    }

    @Test
    void workerDoesNotExecuteAlreadyCompletedRealRunTwice() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView first = service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingTaskView second = service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals("succeeded", first.getStatus());
        assertEquals("succeeded", second.getStatus());
        assertEquals(1, adapter.callCount());
    }

    @Test
    void workerExecutesSubmittedRealRunTasksFromDurableQueue() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        List<ProductListingTaskView> executed = service.executeRunnableRealRunTasks(5);

        assertEquals(1, executed.size());
        assertEquals(submitted.getTaskId(), executed.get(0).getTaskId());
        assertEquals("succeeded", executed.get(0).getStatus());
        assertEquals(1, adapter.callCount());
    }

    @Test
    void staleRunningRealRunTasksRequireManualVerificationAndAreNotReplayed() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );
        mapper.forceRunning(submitted.getTaskId(), LocalDateTime.now().minusHours(2));

        int recovered = service.recoverStaleRunningRealRunTasks(Duration.ofMinutes(30));
        List<ProductListingTaskView> executed = service.executeRunnableRealRunTasks(5);

        assertEquals(1, recovered);
        assertEquals(0, executed.size());
        ProductListingTaskView recoveredTask = service.loadTask(context, submitted.getTaskId());
        assertEquals("written_verify_failed", recoveredTask.getStatus());
        assertEquals("real_run_interrupted", recoveredTask.getFailureCode());
        assertEquals(0, adapter.callCount());
    }

}
