package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ProductListingContinuationGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void continuationEntryKeepsRealRunLockInsideTransaction() throws Exception {
        assertNotNull(ProductListingService.class.getMethod(
                "continueRealRunAfterCreate",
                BusinessAccessContext.class,
                Long.class
        ).getAnnotation(Transactional.class));
    }

    @Test
    void readbackAndProjectionRecoveryEntriesKeepRealRunLockInsideTransaction() throws Exception {
        assertNotNull(ProductListingService.class.getMethod(
                "verifyRealRunReadBack",
                BusinessAccessContext.class,
                Long.class
        ).getAnnotation(Transactional.class));
        assertNotNull(ProductListingService.class.getMethod(
                "replaySuccessfulProjectionBackfill",
                BusinessAccessContext.class,
                Long.class
        ).getAnnotation(Transactional.class));
    }

    @Test
    void projectionRecoveryCannotInvokePostCreateContinuation() throws Exception {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter = adapter();
        mapper.insertTask(task(
                "written_verify_failed",
                "projection_backfill_failed",
                ProductListingNoonWriteResult.succeeded(List.of(createdStep()))
        ));
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.continueRealRunAfterCreate(context(), 20002L)
        );

        assertEquals(0, adapter.continueAfterCreateCallCount());
    }

    @Test
    void readbackOnlyRecoveryCannotInvokePostCreateContinuation() throws Exception {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter = adapter();
        ProductListingNoonWriteStepResult readback = new ProductListingNoonWriteStepResult();
        readback.setStepKey("verify_noon_readback");
        readback.setStatus("failed");
        readback.setFailureCode("readback_mismatch");
        mapper.insertTask(task(
                "written_verify_failed",
                "readback_mismatch",
                ProductListingNoonWriteResult.failed(
                        "noon_readback",
                        "readback_mismatch",
                        "mismatch",
                        List.of(createdStep(), readback)
                )
        ));
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.continueRealRunAfterCreate(context(), 20002L)
        );

        assertEquals(0, adapter.continueAfterCreateCallCount());
    }

    @Test
    void continuationRechecksLatestTaskStateUnderRowLock() throws Exception {
        ProductListingTaskRecord[] lockedTask = new ProductListingTaskRecord[1];
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper() {
                    @Override
                    public ProductListingTaskRecord selectTaskByIdForUpdate(
                            Long taskId,
                            Long ownerUserId
                    ) {
                        return lockedTask[0] == null
                                ? super.selectTaskByIdForUpdate(taskId, ownerUserId)
                                : lockedTask[0];
                    }
                };
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter = adapter();
        mapper.insertTask(task(
                "written_verify_failed",
                "noon_write_failed",
                postCreateFailure()
        ));
        lockedTask[0] = task("succeeded", null, postCreateFailure());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.continueRealRunAfterCreate(context(), 20002L)
        );

        assertEquals(0, adapter.continueAfterCreateCallCount());
    }

    @Test
    void readbackRecoveryRechecksItsUniqueActionUnderRowLock() throws Exception {
        ProductListingTaskRecord[] lockedTask = new ProductListingTaskRecord[1];
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                lockingMapper(lockedTask);
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter = adapter();
        mapper.insertTask(task(
                "written_verify_failed",
                "readback_mismatch",
                readbackFailure()
        ));
        lockedTask[0] = task(
                "written_verify_failed",
                "noon_write_failed",
                postCreateFailure()
        );
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyRealRunReadBack(context(), 20002L)
        );

        assertEquals(0, adapter.verifyReadBackCallCount());
    }

    @Test
    void projectionRecoveryRechecksItsUniqueActionUnderRowLock() throws Exception {
        ProductListingTaskRecord[] lockedTask = new ProductListingTaskRecord[1];
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                lockingMapper(lockedTask);
        mapper.insertTask(task(
                "written_verify_failed",
                "projection_backfill_failed",
                ProductListingNoonWriteResult.succeeded(List.of(createdStep()))
        ));
        lockedTask[0] = task(
                "written_verify_failed",
                "readback_mismatch",
                readbackFailure()
        );
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                adapter()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.replaySuccessfulProjectionBackfill(context(), 20002L)
        );
    }

    private ProductListingTestFixtures.FakeProductListingMapper lockingMapper(
            ProductListingTaskRecord[] lockedTask
    ) {
        return new ProductListingTestFixtures.FakeProductListingMapper() {
            @Override
            public ProductListingTaskRecord selectTaskByIdForUpdate(
                    Long taskId,
                    Long ownerUserId
            ) {
                return lockedTask[0] == null
                        ? super.selectTaskByIdForUpdate(taskId, ownerUserId)
                        : lockedTask[0];
            }
        };
    }

    private ProductListingTaskRecord task(
            String status,
            String failureCode,
            ProductListingNoonWriteResult result
    ) throws Exception {
        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(20002L);
        task.setDraftId(10001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setMode("REAL_RUN");
        task.setStatus(status);
        task.setSourceTaskId(20001L);
        task.setInputSnapshotJson("{\"psku\":\"NN-TEST-PSKU\"}");
        task.setValidationJson("[]");
        task.setConfirmationJson("{\"confirmRealNoonWrite\":true}");
        task.setFailureCode(failureCode);
        task.setNoonResultJson(objectMapper.writeValueAsString(result));
        return task;
    }

    private ProductListingNoonWriteResult postCreateFailure() {
        ProductListingNoonWriteStepResult upload = new ProductListingNoonWriteStepResult();
        upload.setStepKey("upload_images");
        upload.setStatus("failed");
        upload.setFailureCode("noon_image_upload_failed");
        return ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_write_failed",
                "upload failed",
                List.of(createdStep(), upload)
        );
    }

    private ProductListingNoonWriteResult readbackFailure() {
        ProductListingNoonWriteStepResult readback = new ProductListingNoonWriteStepResult();
        readback.setStepKey("verify_noon_readback");
        readback.setStatus("failed");
        readback.setFailureCode("readback_mismatch");
        return ProductListingNoonWriteResult.failed(
                "noon_readback",
                "readback_mismatch",
                "mismatch",
                List.of(createdStep(), readback)
        );
    }

    private ProductListingNoonWriteStepResult createdStep() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        return create;
    }

    private ProductListingTestFixtures.TrackingNoonWriteAdapter adapter() {
        return new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                null,
                ProductListingNoonWriteResult.succeeded(List.of()),
                null
        );
    }

    private BusinessAccessContext context() {
        return ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE");
    }
}
