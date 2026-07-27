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


abstract class ProductListingRealRunServiceTest {
    protected ProductListingNoonWriteResult successResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("succeeded");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=1");
        return ProductListingNoonWriteResult.succeeded(List.of(create, readBack));
    }

    protected ProductListingNoonWriteResult readBackFailureAfterRemoteWriteResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("failed");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=13");
        readBack.setFailureCode("readback_mismatch");
        readBack.setFailureMessage("Noon product was created but readback fields differ.");
        return ProductListingNoonWriteResult.failed(
                "noon_readback",
                "readback_mismatch",
                "Noon product was created but readback fields differ.",
                List.of(create, readBack)
        );
    }

    protected ProductListingNoonWriteResult noonFailureBeforeRemoteWriteResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_write_failed");
        create.setFailureMessage("HTTP 503 temporary Noon error.");
        return ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_write_failed",
                "HTTP 503 temporary Noon error.",
                List.of(create)
        );
    }

    protected ProductListingNoonWriteResult unknownCreateOutcomeResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_create_outcome_unknown");
        create.setFailureMessage("connection reset after request write");
        return ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_create_outcome_unknown",
                "connection reset after request write",
                List.of(create)
        );
    }

    protected ProductListingNoonWriteResult unknownCreateAuthenticationResult() {
        ProductListingNoonWriteStepResult create =
                new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_create_outcome_unknown");
        create.setFailureMessage("authentication expired after request write");
        return ProductListingNoonWriteResult.failed(
                "authentication",
                "noon_auth_required",
                "authentication expired after request write",
                List.of(create)
        );
    }

    protected ProductListingNoonWriteResult
            explicitCreateAuthenticationRejectionResult() {
        ProductListingNoonWriteStepResult create =
                new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_auth_required");
        create.setFailureMessage(
                "Noon rejected create with HTTP 307 before processing."
        );
        return ProductListingNoonWriteResult.failed(
                "authentication",
                "noon_auth_required",
                "Noon rejected create with HTTP 307 before processing.",
                List.of(create)
        );
    }

    protected ProductListingNoonWriteStepResult successCreateReferenceLookupStep() {
        ProductListingNoonWriteStepResult lookup = new ProductListingNoonWriteStepResult();
        lookup.setStepKey("resolve_create_reference");
        lookup.setStatus("succeeded");
        lookup.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        return lookup;
    }

    protected ProductListingNoonWriteResult partnerSkuAlreadyExistsResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_write_failed");
        create.setFailureMessage("HTTP 400 {\"error\":\"Partner skus already exists: [['NN-TEST-PSKU']]\"}");
        return ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_write_failed",
                "HTTP 400 {\"error\":\"Partner skus already exists: [['NN-TEST-PSKU']]\"}",
                List.of(create)
        );
    }

    protected ProductListingNoonWriteResult imageUploadFailureAfterRemoteCreateResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteStepResult uploadImages = new ProductListingNoonWriteStepResult();
        uploadImages.setStepKey("upload_images");
        uploadImages.setStatus("failed");
        uploadImages.setExternalReference("uploadedImages=0");
        uploadImages.setFailureCode("noon_image_upload_failed");
        uploadImages.setFailureMessage("HTTP 400 Filetype <None> not supported.");
        return ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_write_failed",
                "HTTP 400 Filetype <None> not supported.",
                List.of(create, uploadImages)
        );
    }

    protected ProductListingNoonWriteResult continuationSuccessResult() {
        ProductListingNoonWriteStepResult uploadImages = new ProductListingNoonWriteStepResult();
        uploadImages.setStepKey("upload_images");
        uploadImages.setStatus("succeeded");
        uploadImages.setExternalReference("uploadedImages=1;uploadedImagePaths=noon-uploaded/sku-main.jpg");
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("succeeded");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=1");
        return ProductListingNoonWriteResult.succeeded(List.of(uploadImages, readBack));
    }

    protected ProductListingNoonWriteResult continuationReadBackFailureResult() {
        ProductListingNoonWriteStepResult uploadImages = new ProductListingNoonWriteStepResult();
        uploadImages.setStepKey("upload_images");
        uploadImages.setStatus("succeeded");
        uploadImages.setExternalReference("uploadedImages=1;uploadedImagePaths=noon-uploaded/sku-main.jpg");
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("failed");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=13");
        readBack.setFailureCode("readback_mismatch");
        readBack.setFailureMessage("Noon readback still differs.");
        return ProductListingNoonWriteResult.failed(
                "noon_readback",
                "readback_mismatch",
                "Noon readback still differs.",
                List.of(uploadImages, readBack)
        );
    }

    protected ProductListingNoonWriteStepResult successReadBackStep() {
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("succeeded");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=1");
        return readBack;
    }

    protected static ObjectProvider<ProductListingProjectionBackfill> objectProvider(
            ProductListingProjectionBackfill backfill
    ) {
        return new ObjectProvider<>() {
            @Override
            public ProductListingProjectionBackfill getObject(Object... args) {
                return backfill;
            }

            @Override
            public ProductListingProjectionBackfill getIfAvailable() {
                return backfill;
            }

            @Override
            public ProductListingProjectionBackfill getIfUnique() {
                return backfill;
            }

            @Override
            public ProductListingProjectionBackfill getObject() {
                return backfill;
            }
        };
    }

    protected static class TrackingProjectionBackfill implements ProductListingProjectionBackfill {
        protected int callCount;
        protected int draftBackfillCallCount;
        protected ProductListingTaskRecord task;
        protected ProductListingDraftRecord draftRecord;
        protected ProductListingDraftCommand draft;
        protected ProductListingDraftCommand draftProjection;
        protected ProductListingNoonWriteResult result;

        @Override
        public void backfillDraftListing(
                ProductListingDraftRecord record,
                ProductListingDraftCommand draft
        ) {
            this.draftBackfillCallCount += 1;
            this.draftRecord = record;
            this.draftProjection = draft;
        }

        @Override
        public boolean backfillSuccessfulListing(
                ProductListingTaskRecord task,
                ProductListingDraftCommand draft,
                ProductListingNoonWriteResult result
        ) {
            this.callCount += 1;
            this.task = task;
            this.draft = draft;
            this.result = result;
            return true;
        }
    }

    protected static class ThrowingProjectionBackfill implements ProductListingProjectionBackfill {
        @Override
        public void backfillDraftListing(
                ProductListingDraftRecord record,
                ProductListingDraftCommand draft
        ) {
        }

        @Override
        public boolean backfillSuccessfulListing(
                ProductListingTaskRecord task,
                ProductListingDraftCommand draft,
                ProductListingNoonWriteResult result
        ) {
            throw new IllegalStateException("projection unavailable");
        }
    }

    protected static class NoopSuccessfulProjectionBackfill implements ProductListingProjectionBackfill {
        @Override
        public void backfillDraftListing(ProductListingDraftRecord record, ProductListingDraftCommand draft) {
        }

        @Override
        public boolean backfillSuccessfulListing(
                ProductListingTaskRecord task,
                ProductListingDraftCommand draft,
                ProductListingNoonWriteResult result
        ) {
            return false;
        }
    }
}
