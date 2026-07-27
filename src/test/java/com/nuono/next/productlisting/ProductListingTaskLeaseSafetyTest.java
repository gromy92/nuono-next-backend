package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ProductListingTaskLeaseSafetyTest {

    @Test
    void transactionBoundRecoveryDoesNotStartABlockingBackgroundHeartbeat() {
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(88003L);
        task.setOwnerUserId(10002L);
        task.setStartedAt(LocalDateTime.now());
        when(mapper.heartbeatRunningTask(
                task.getId(), task.getOwnerUserId(), task.getStartedAt()
        )).thenReturn(1);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (ProductListingTaskLease lease = ProductListingTaskLease.start(mapper, task)) {
            assertNull(ReflectionTestUtils.getField(lease, "heartbeatFuture"));
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void staleWorkerCannotOverwriteTheTaskAfterItsLeaseWasTakenOver() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingNoonWriteAdapter adapter = new ProductListingNoonWriteAdapter() {
            @Override
            public ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request) {
                mapper.forceLeaseLoss(request.getRealRunTaskId());
                return ProductListingNoonWriteResult.succeeded(java.util.List.of());
            }

            @Override
            public ProductListingNoonWriteResult continueAfterCreate(
                    ProductListingNoonWriteRequest request,
                    String skuParent,
                    String pskuCode
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ProductListingNoonWriteStepResult verifyReadBack(
                    ProductListingNoonWriteRequest request,
                    String skuParent,
                    String pskuCode,
                    java.util.List<String> expectedImageValues
            ) {
                throw new UnsupportedOperationException();
            }
        };
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.executeSubmittedRealRunTask(submitted.getTaskId())
        );

        ProductListingTaskView current = service.loadTask(context, submitted.getTaskId());
        assertEquals("written_verify_failed", current.getStatus());
        assertEquals("real_run_interrupted", current.getFailureCode());
    }
}
