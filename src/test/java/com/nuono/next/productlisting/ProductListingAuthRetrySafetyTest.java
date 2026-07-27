package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductListingAuthRetrySafetyTest {

    @Test
    void preWriteAuthFailureReturnsTheSameReservedTaskAndCannotEnterCreateContinuation() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(authFailure(false));
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView firstAttempt = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        ProductListingTaskView failed = service.executeSubmittedRealRunTask(firstAttempt.getTaskId());
        ProductListingTaskView duplicate = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        assertEquals("failed", failed.getStatus());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, failed.getFailureCode());
        assertEquals(Boolean.FALSE, failed.getNoonResult().getWriteMayHaveOccurred());
        assertEquals(firstAttempt.getTaskId(), duplicate.getTaskId());
        assertEquals("failed", duplicate.getStatus());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, duplicate.getFailureCode());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.continueRealRunAfterCreate(context, failed.getTaskId())
        );
        assertEquals(1, adapter.callCount());
    }

    @Test
    void possibleWriteReturnsTheSameReservedTaskForAnotherConfirmation() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(authFailure(true));
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView firstAttempt = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        ProductListingTaskView uncertain = service.executeSubmittedRealRunTask(firstAttempt.getTaskId());
        ProductListingTaskView duplicate = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        assertEquals("written_verify_failed", uncertain.getStatus());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, uncertain.getFailureCode());
        assertEquals(Boolean.TRUE, uncertain.getNoonResult().getWriteMayHaveOccurred());
        assertEquals(firstAttempt.getTaskId(), duplicate.getTaskId());
        assertEquals("written_verify_failed", duplicate.getStatus());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, duplicate.getFailureCode());
        assertEquals(1, adapter.callCount());
    }

    @Test
    void rebuildLookupIgnoresRejectedGuardAuditsAndKeepsTheActualAttemptAuthoritative() {
        Method method = Arrays.stream(ProductListingMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("selectLatestRealRunTaskByDraftSource"))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("t.status <> 'rejected'"));
        assertFalse(sql.contains("'listing_auth_recovery_superseded'"));
    }

    private ProductListingNoonWriteResult authFailure(boolean writeMayHaveOccurred) {
        ProductListingNoonWriteStepResult authStep = new ProductListingNoonWriteStepResult();
        authStep.setStepKey(writeMayHaveOccurred ? "create_product" : "authorization_recovery");
        authStep.setStatus("failed");
        authStep.setFailureCode(ProductListingWriteAuthRecovery.FAILURE_CODE);
        authStep.setFailureMessage("Noon Project 授权恢复中");
        authStep.setRecoveryId(991L);
        authStep.setWriteMayHaveOccurred(writeMayHaveOccurred);
        return ProductListingNoonWriteResult.failed(
                "authorization",
                ProductListingWriteAuthRecovery.FAILURE_CODE,
                "Noon Project 授权恢复中",
                List.of(authStep)
        );
    }
}
