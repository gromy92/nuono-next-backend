package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductImageProfileSummaryAssemblerTest {

    @Test
    void completeSummaryExposesIndependentProfileAndImageStatuses() {
        ProductImageProfileSummaryRecord record = completeRecord();
        record.setSuiteCount(3);
        record.setActiveSuiteCount(2);
        record.setPrimarySuiteStatus(ProductImageSuiteStatus.FAILED);

        ProductImageProfileSummaryView view = ProductImageProfileSummaryAssembler.fromRecord(record);

        assertEquals(ProductImageProfileReadinessStatus.COMPLETE, view.getProfileReadinessStatus());
        assertTrue(view.getMissingProfileFields().isEmpty());
        assertEquals(ProductImageSummaryStatus.ACTION_REQUIRED, view.getImageStatus());
        assertEquals(3, view.getSuiteCount());
        assertEquals(2, view.getActiveSuiteCount());
    }

    @Test
    void incompleteSummaryListsEveryMissingGenerationField() {
        ProductImageProfileSummaryRecord record = new ProductImageProfileSummaryRecord();
        record.setAssetCount(0);
        record.setPrimarySuiteStatus(ProductImageSuiteStatus.PENDING_REVIEW);

        ProductImageProfileSummaryView view = ProductImageProfileSummaryAssembler.fromRecord(record);

        assertEquals(ProductImageProfileReadinessStatus.INCOMPLETE, view.getProfileReadinessStatus());
        assertEquals(List.of(
                ProductImageProfileMissingField.BRAND,
                ProductImageProfileMissingField.BILINGUAL_TITLE,
                ProductImageProfileMissingField.SPEC_SUMMARY,
                ProductImageProfileMissingField.PRODUCT_FACTS,
                ProductImageProfileMissingField.BASE_IMAGE
        ), view.getMissingProfileFields());
        assertEquals(ProductImageSummaryStatus.PENDING_CONFIRMATION, view.getImageStatus());
    }

    @Test
    void rawSuiteStatusesMapToOperatorFacingImageStatuses() {
        Map<ProductImageSuiteStatus, ProductImageSummaryStatus> expected = new LinkedHashMap<>();
        expected.put(ProductImageSuiteStatus.DRAFT, ProductImageSummaryStatus.CANDIDATE);
        expected.put(ProductImageSuiteStatus.PENDING_GENERATION, ProductImageSummaryStatus.GENERATING);
        expected.put(ProductImageSuiteStatus.GENERATING, ProductImageSummaryStatus.GENERATING);
        expected.put(ProductImageSuiteStatus.REGENERATING, ProductImageSummaryStatus.GENERATING);
        expected.put(ProductImageSuiteStatus.PENDING_REVIEW, ProductImageSummaryStatus.PENDING_CONFIRMATION);
        expected.put(ProductImageSuiteStatus.ADOPTED, ProductImageSummaryStatus.PENDING_CONFIRMATION);
        expected.put(ProductImageSuiteStatus.PUBLISHING, ProductImageSummaryStatus.PUBLISHING);
        expected.put(ProductImageSuiteStatus.ONLINE, ProductImageSummaryStatus.ONLINE);
        expected.put(ProductImageSuiteStatus.FAILED, ProductImageSummaryStatus.ACTION_REQUIRED);
        expected.put(ProductImageSuiteStatus.HISTORICAL, ProductImageSummaryStatus.NOT_REQUESTED);
        expected.put(ProductImageSuiteStatus.DISCARDED, ProductImageSummaryStatus.NOT_REQUESTED);

        expected.forEach((rawStatus, summaryStatus) -> {
            ProductImageProfileSummaryRecord record = completeRecord();
            record.setPrimarySuiteStatus(rawStatus);
            assertEquals(summaryStatus, ProductImageProfileSummaryAssembler.fromRecord(record).getImageStatus());
        });
    }

    private ProductImageProfileSummaryRecord completeRecord() {
        ProductImageProfileSummaryRecord record = new ProductImageProfileSummaryRecord();
        record.setBrand("PAPERSAY");
        record.setTitleEn("Magnetic Whiteboard Markers");
        record.setSpecSummary("8 Colors");
        record.setProductFactText("Verified product facts");
        record.setAssetCount(7);
        return record;
    }
}
