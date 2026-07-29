package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RealProductListingDurableCheckpointTest
        extends RealProductListingNoonWriteAdapterTest {

    @Test
    void checkpointsUnknownCreateIntentBeforeSendingCreate() {
        List<ProductListingNoonWriteResult> checkpoints =
                new ArrayList<>();
        FakeSession session = new FakeSession() {
            @Override
            public JsonNode postWriteJson(
                    String url,
                    JsonNode body,
                    boolean withProject,
                    Map<String, String> headers
            ) {
                if (ProductListingRealWriteProperties.Endpoints
                        .DEFAULT_CREATE_PRODUCT_URL.equals(url)) {
                    assertEquals(1, checkpoints.size());
                    throw new SimulatedJvmTermination();
                }
                return super.postWriteJson(
                        url, body, withProject, headers);
            }
        };
        ProductListingNoonWriteRequest request = writeRequest();
        RealProductListingNoonWriteAdapter adapter =
                adapter(session, request, checkpoints);

        assertThrows(
                SimulatedJvmTermination.class,
                () -> adapter.execute(request)
        );

        ProductListingNoonWriteResult checkpoint =
                checkpoints.get(0);
        assertFalse(checkpoint.isSuccess());
        assertEquals(2, checkpoint.getSteps().size());
        ProductListingNoonWriteStepResult absence =
                checkpoint.getSteps().get(0);
        ProductListingNoonWriteStepResult create =
                checkpoint.getSteps().get(1);
        assertEquals(
                "pre_create_absence_verified", absence.getStepKey());
        assertEquals("succeeded", absence.getStatus());
        assertEquals("create_product", create.getStepKey());
        assertEquals(
                "noon_create_outcome_unknown", create.getFailureCode());
        assertEquals(Boolean.TRUE, create.getWriteMayHaveOccurred());
        assertNotNull(absence.getExternalReference());
    }

    @Test
    void checkpointsCreateReferencesBeforeTheNextProviderWrite() {
        List<ProductListingNoonWriteResult> checkpoints =
                new ArrayList<>();
        FakeSession session = new FakeSession() {
            @Override
            public JsonNode postWriteJson(
                    String url,
                    JsonNode body,
                    boolean withProject,
                    Map<String, String> headers
            ) {
                if (ProductListingRealWriteProperties.Endpoints
                        .DEFAULT_SKU_CACHE_URL.equals(url)) {
                    ProductListingNoonWriteStepResult create =
                            latestCreate(checkpoints);
                    assertEquals("succeeded", create.getStatus());
                    assertTrue(create.getExternalReference()
                            .contains("skuParent=ZPARENT"));
                    assertTrue(create.getExternalReference()
                            .contains("pskuCode=PSKU_CODE_1"));
                    throw new SimulatedJvmTermination();
                }
                return super.postWriteJson(
                        url, body, withProject, headers);
            }
        };
        ProductListingNoonWriteRequest request = writeRequest();
        RealProductListingNoonWriteAdapter adapter =
                adapter(session, request, checkpoints);

        assertThrows(
                SimulatedJvmTermination.class,
                () -> adapter.execute(request)
        );

        assertTrue(checkpoints.size() >= 2);
        assertEquals(
                "succeeded",
                latestCreate(checkpoints).getStatus()
        );
        ProductListingNoonWriteResult checkpoint =
                checkpoints.get(checkpoints.size() - 1);
        assertEquals(
                "real_run_in_progress", checkpoint.getFailureCode());
        ProductListingTaskView task = new ProductListingTaskView();
        task.setMode("REAL_RUN");
        task.setStatus("written_verify_failed");
        task.setFailureCode(checkpoint.getFailureCode());
        task.setNoonResult(checkpoint);
        assertEquals(
                ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE,
                new ProductListingWorkflowProjector()
                        .project(null, null, task).getNextAction()
        );
    }

    @Test
    void checkpointFailureStopsAllSubsequentProviderWrites() {
        FakeSession session = new FakeSession();
        ProductListingNoonWriteRequest request = writeRequest();
        AtomicInteger checkpointCount = new AtomicInteger();
        request.setNoonResultCheckpoint(result -> {
            if (checkpointCount.incrementAndGet() == 2) {
                throw new IllegalStateException(
                        "checkpoint store unavailable");
            }
        });
        RealProductListingNoonWriteAdapter adapter = adapter(session);

        assertThrows(
                IllegalStateException.class,
                () -> adapter.execute(request)
        );

        assertEquals(2, checkpointCount.get());
        assertEquals(1, session.calls.size());
        assertEquals(
                ProductListingRealWriteProperties.Endpoints
                        .DEFAULT_CREATE_PRODUCT_URL,
                session.calls.get(0).url
        );
    }

    @Test
    void completionCheckpointFailureDoesNotOverwriteDurableContinuationSuccess() {
        AtomicInteger checkpointAttempts = new AtomicInteger();
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper() {
                    @Override
                    public int checkpointRunningTaskNoonResult(
                            Long taskId, Long ownerUserId,
                            String json, java.time.LocalDateTime startedAt
                    ) {
                        if (checkpointAttempts.incrementAndGet() == 3) {
                            throw new IllegalStateException("transient checkpoint failure");
                        }
                        return super.checkpointRunningTaskNoonResult(taskId, ownerUserId, json, startedAt);
                    }
                };
        ProductListingNoonWriteResult continuation = ProductListingNoonWriteResult.succeeded(
                List.of(step("upsert_zsku_content_en", "succeeded", null)));
        AtomicInteger continuationCalls = new AtomicInteger();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        postCreateFailure(), continuation, null) {
                    @Override
                    public ProductListingNoonWriteResult continueAfterCreate(
                            ProductListingNoonWriteRequest request,
                            String skuParent,
                            String pskuCode
                    ) {
                        continuationCalls.incrementAndGet();
                        ProductListingNoonWriteResult result =
                                super.continueAfterCreate(
                                        request, skuParent, pskuCode);
                        request.checkpointNoonResultOrThrow(result);
                        return result;
                    }
                };
        ProductListingService service =
                ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun =
                ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView failed =
                service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertThrows(IllegalStateException.class, () ->
                service.continueRealRunAfterCreate(context, failed.getTaskId()));

        ProductListingTaskView durable = service.loadTask(context, failed.getTaskId());
        assertEquals("running", durable.getStatus());
        assertTrue(durable.getNoonResult().isSuccess());
        assertEquals(1, continuationCalls.get());
        assertEquals(3, checkpointAttempts.get());
    }

    private ProductListingNoonWriteResult postCreateFailure() {
        return ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_write_failed",
                "post-create write failed",
                List.of(
                        step("create_product", "succeeded",
                                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1"),
                        step("upsert_zsku_content_en", "failed", null)
                )
        );
    }

    private ProductListingNoonWriteStepResult step(
            String key,
            String status,
            String reference
    ) {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey(key);
        step.setStatus(status);
        step.setExternalReference(reference);
        if ("failed".equals(status)) {
            step.setFailureCode("noon_write_failed");
        }
        return step;
    }

    private RealProductListingNoonWriteAdapter adapter(
            FakeSession session,
            ProductListingNoonWriteRequest request,
            List<ProductListingNoonWriteResult> checkpoints
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        request.setNoonResultCheckpoint(result -> checkpoints.add(
                objectMapper.convertValue(
                        result, ProductListingNoonWriteResult.class)));
        return adapter(session, objectMapper);
    }

    private RealProductListingNoonWriteAdapter adapter(
            FakeSession session
    ) {
        return adapter(session, new ObjectMapper());
    }

    private RealProductListingNoonWriteAdapter adapter(
            FakeSession session,
            ObjectMapper objectMapper
    ) {
        NoonPullGatewaySessionFactory sessionFactory =
                new NoonPullGatewaySessionFactory() {
                    @Override
                    public NoonPullGatewaySession login(
                            NoonPullStoreBinding binding
                    ) {
                        return session;
                    }
                };
        return new RealProductListingNoonWriteAdapter(
                objectMapper,
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );
    }

    private ProductListingNoonWriteStepResult latestCreate(
            List<ProductListingNoonWriteResult> checkpoints
    ) {
        List<ProductListingNoonWriteStepResult> steps =
                checkpoints.get(checkpoints.size() - 1).getSteps();
        for (int index = steps.size() - 1; index >= 0; index--) {
            ProductListingNoonWriteStepResult step = steps.get(index);
            if ("create_product".equals(step.getStepKey())) {
                return step;
            }
        }
        throw new AssertionError("create checkpoint missing");
    }

    private static final class SimulatedJvmTermination extends Error {
        private static final long serialVersionUID = 1L;
    }
}
