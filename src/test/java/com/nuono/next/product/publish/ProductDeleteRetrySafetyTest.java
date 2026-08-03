package com.nuono.next.product.publish;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.product.ProductPublishTaskRecord;
import org.junit.jupiter.api.Test;

class ProductDeleteRetrySafetyTest {

    @Test
    void explicitPreWriteFailureShouldBeRetryable() {
        ProductPublishTaskRecord task = deleteTask(
                "product_delete_failed",
                "{\"stage\":\"pre_delete_captured\",\"writeMayHaveOccurred\":false}"
        );

        assertTrue(ProductDeleteRetrySafety.canResume(task));
    }

    @Test
    void submittedDeleteShouldResumeWithReadbackInsteadOfBlindReplay() {
        ProductPublishTaskRecord task = deleteTask(
                "product_delete_result_unknown",
                "{\"stage\":\"delete_submitted\",\"writeMayHaveOccurred\":true}"
        );

        assertTrue(ProductDeleteRetrySafety.canResume(task));
    }

    @Test
    void malformedUnknownOutcomeShouldFailClosed() {
        ProductPublishTaskRecord task = deleteTask(
                "product_delete_result_unknown",
                "{\"stage\":\"garbled\",\"writeMayHaveOccurred\":true}"
        );

        assertFalse(ProductDeleteRetrySafety.canResume(task));
    }

    @Test
    void inconsistentUnknownOutcomeAtPreWriteStageShouldFailClosed() {
        ProductPublishTaskRecord task = deleteTask(
                "product_delete_result_unknown",
                "{\"stage\":\"pre_delete_captured\",\"writeMayHaveOccurred\":false}"
        );

        assertFalse(ProductDeleteRetrySafety.canResume(task));
    }

    private ProductPublishTaskRecord deleteTask(String errorCode, String resultJson) {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setTaskType("product-delete");
        task.setErrorCode(errorCode);
        task.setResultJson(resultJson);
        return task;
    }
}
