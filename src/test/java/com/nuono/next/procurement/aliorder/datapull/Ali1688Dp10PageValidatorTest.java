package com.nuono.next.procurement.aliorder.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10PageValidatorTest extends Ali1688Dp10JobTestSupport {
    private final Ali1688Dp10PageValidator validator = new Ali1688Dp10PageValidator();

    @Test
    void provesExactPageRowsFromStableTotalWithoutAnyOrderingAssumption() {
        Ali1688Dp10Checkpoint checkpoint = checkpoint();
        Ali1688HistoricalOrderProvider.Page page = page(
                List.of(order("ORDER-1", NEWEST, true), order("ORDER-2", OLDER, true)),
                1,
                2,
                3
        );

        Ali1688Dp10ValidatedPage validated = validator.validate(page, checkpoint);

        assertEquals(Ali1688HistoricalOrderProvider.Partition.CURRENT, validated.getPartition());
        assertEquals(1, validated.getPageNo());
        assertEquals(2, validated.getPageSize());
        assertEquals(3L, validated.getTotalRecord());
        assertEquals(2, validated.getExpectedPages());
        assertEquals(2, validated.getRawRowCount());
    }

    @Test
    void rejectsTotalDriftAndAnyShortOrOverfullPage() {
        Ali1688Dp10Checkpoint checkpoint = checkpoint();
        checkpoint = checkpoint.bindContract(3, 2);
        Ali1688HistoricalOrderProvider.Page drift = page(
                List.of(order("ORDER-1", NEWEST, true), order("ORDER-2", OLDER, true)),
                1,
                2,
                4
        );
        assertCode("DP10_PARTITION_TOTAL_DRIFT", drift, checkpoint);

        Ali1688HistoricalOrderProvider.Page shortPage = page(
                List.of(order("ORDER-1", NEWEST, true)),
                1,
                2,
                3
        );
        assertCode("DP10_PAGE_RAW_ROW_COUNT_INVALID", shortPage, checkpoint);
    }

    @Test
    void classifiesPageBeyondShrunkTotalAsPartitionDrift() {
        Ali1688Dp10Checkpoint checkpoint = checkpoint().bindContract(2L, 1);
        checkpoint.setPageNo(2);
        Ali1688HistoricalOrderProvider.Page overrun = page(List.of(), 2, 2, 1L);

        assertCode("DP10_PARTITION_TOTAL_DRIFT", overrun, checkpoint);
    }

    @Test
    void rejectsLongTotalWhenPageCountCannotFitSupportedLocator() {
        Ali1688Dp10Checkpoint checkpoint = checkpoint();
        checkpoint.setPageSize(1);
        Ali1688HistoricalOrderProvider.Page page = new Ali1688HistoricalOrderProvider.Page(
                List.of(order("ORDER-1", NEWEST, true)));
        page.setContainerProven(true);
        page.setPaginationProven(true);
        page.setPageNo(1);
        page.setPageSize(1);
        page.setTotalRecord((long) Integer.MAX_VALUE + 1L);
        page.setHasMore(true);

        assertCode("DP10_PAGE_COUNT_UNREPRESENTABLE", page, checkpoint);
    }

    @Test
    void rejectsAProviderResponseAboveTheDp10HardPageLimit() {
        Ali1688Dp10Checkpoint checkpoint = checkpoint();
        checkpoint.setPageSize(101);
        Ali1688HistoricalOrderProvider.Page oversized = page(List.of(), 1, 101, 0L);

        assertCode("DP10_PAGE_TOTAL_INVALID", oversized, checkpoint);
    }

    @Test
    void deterministicSingleOrderDefectsRemainRawRowsAndOnlySkipThatItem() {
        Ali1688HistoricalOrderProvider.Page page = page(
                List.of(order(null, NEWEST, true), order("ORDER-2", OLDER, true)),
                1,
                2,
                2
        );

        Ali1688Dp10ValidatedPage validated = validator.validate(page, checkpoint());

        assertEquals(2, validated.getRawRowCount());
        assertEquals(Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
                validated.getEntries().get(0).getState());
        assertEquals(Ali1688Dp10ItemState.COMPLETE,
                validated.getEntries().get(1).getState());
    }

    @Test
    void nullProviderDtoFailsTheWholePageBeforeAnyStageRowCanBeBuilt() {
        Ali1688HistoricalOrderProvider.Page page = page(
                java.util.Arrays.asList(null, order("ORDER-2", OLDER, true)),
                1,
                2,
                2
        );

        assertCode("DP10_PAGE_ROW_UNMAPPED", page, checkpoint());
    }

    @Test
    void deterministicFactConstraintViolationSkipsOnlyThatOrder() {
        Ali1688HistoricalOrderProvider.OrderSnapshot invalid =
                order("ORDER-1", NEWEST, true);
        invalid.setSupplierName("x".repeat(301));
        Ali1688HistoricalOrderProvider.Page page = page(
                List.of(invalid, order("ORDER-2", OLDER, true)),
                1,
                2,
                2
        );

        Ali1688Dp10ValidatedPage validated = validator.validate(page, checkpoint());

        assertEquals(Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
                validated.getEntries().get(0).getState());
        assertEquals("DP10_FACT_ORDER_TEXT_TOO_LONG",
                validated.getEntries().get(0).getSanitizedCode());
        assertEquals(Ali1688Dp10ItemState.COMPLETE,
                validated.getEntries().get(1).getState());
    }

    @Test
    void rawFingerprintIsCapturedBeforeUnusableChildrenAreCleaned() {
        Ali1688HistoricalOrderProvider.OrderSnapshot clean =
                order("ORDER-1", NEWEST, true);
        Ali1688HistoricalOrderProvider.OrderSnapshot withNull =
                orderWithExtraChild(null);
        Ali1688HistoricalOrderProvider.OrderItemSnapshot malformedA =
                malformedChild("unidentified-A");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot malformedB =
                malformedChild("unidentified-B");
        Ali1688HistoricalOrderProvider.OrderSnapshot withMalformedA =
                orderWithExtraChild(malformedA);
        Ali1688HistoricalOrderProvider.OrderSnapshot withMalformedB =
                orderWithExtraChild(malformedB);

        Ali1688Dp10ListEntry cleanEntry = validateOne(clean);
        Ali1688Dp10ListEntry nullEntry = validateOne(withNull);
        Ali1688Dp10ListEntry malformedAEntry = validateOne(withMalformedA);
        Ali1688Dp10ListEntry malformedBEntry = validateOne(withMalformedB);

        assertNotEquals(cleanEntry.getRawFingerprint(), nullEntry.getRawFingerprint());
        assertNotEquals(cleanEntry.getRawFingerprint(), malformedAEntry.getRawFingerprint());
        assertNotEquals(malformedAEntry.getRawFingerprint(), malformedBEntry.getRawFingerprint());
        assertEquals(1, nullEntry.getOrder().getItems().size());
        assertEquals(1, malformedAEntry.getOrder().getItems().size());
        assertEquals(Ali1688Dp10ItemState.COMPLETE, malformedAEntry.getState());
    }

    @Test
    void rawSerializationFailureFailsThePageBeforeBusinessCleaning() {
        Ali1688HistoricalOrderProvider.OrderSnapshot broken =
                new Ali1688HistoricalOrderProvider.OrderSnapshot() {
                    @Override
                    public String getBuyerRemark() {
                        throw new IllegalStateException("provider DTO getter failed");
                    }
                };
        broken.setProviderOrderNo("ORDER-BROKEN");
        broken.setProviderModifiedAt(NEWEST);

        assertCode("DP10_RAW_FINGERPRINT_ENCODE_FAILED",
                page(List.of(broken), 1, 1, 1), checkpoint(1));
    }

    private Ali1688Dp10ListEntry validateOne(
            Ali1688HistoricalOrderProvider.OrderSnapshot order
    ) {
        return validator.validate(page(List.of(order), 1, 1, 1), checkpoint(1))
                .getEntries().get(0);
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot orderWithExtraChild(
            Ali1688HistoricalOrderProvider.OrderItemSnapshot extra
    ) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                order("ORDER-1", NEWEST, true);
        java.util.List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> children =
                new java.util.ArrayList<>(order.getItems());
        children.add(extra);
        order.setItems(children);
        return order;
    }

    private Ali1688HistoricalOrderProvider.OrderItemSnapshot malformedChild(String title) {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot child =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        child.setTitle(title);
        return child;
    }

    private Ali1688Dp10Checkpoint checkpoint() {
        return checkpoint(2);
    }

    private Ali1688Dp10Checkpoint checkpoint(int pageSize) {
        return Ali1688Dp10Checkpoint.initial(
                progress(false, null, 0L).current,
                NOW,
                pageSize
        );
    }

    private void assertCode(
            String expected,
            Ali1688HistoricalOrderProvider.Page page,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        Ali1688Dp10PageContractException failure = assertThrows(
                Ali1688Dp10PageContractException.class,
                () -> validator.validate(page, checkpoint)
        );
        assertEquals(expected, failure.getSanitizedCode());
    }
}
