package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10ApplyStageMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10FactLookupMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class Ali1688Dp10StageContractTest {

    @Test
    void generationSealVerificationAndFactCursorAreDurableCompareAndSetWrites() throws Exception {
        String completeDetail = sql(Ali1688Dp10StageMapper.class, "completeItem", Update.class);
        String countUpsert = sql(
                Ali1688Dp10StageMapper.class, "upsertFingerprintCount", Insert.class);
        String sealRange = sql(
                Ali1688Dp10StageMapper.class, "selectFingerprintCounts", Select.class);
        String verification = sql(
                Ali1688Dp10ApplyStageMapper.class, "completeVerification", Update.class);
        String cursor = sql(
                Ali1688Dp10ApplyStageMapper.class, "advanceApplyCursor", Update.class);
        String businessSkip = sql(
                Ali1688Dp10ApplyStageMapper.class, "markBusinessSkipped", Update.class);

        assertThat(completeDetail).contains("generation_no = #{generationNo}")
                .contains("scan_pass = #{scanPass}")
                .contains("state = 'PENDING_DETAIL'")
                .doesNotContain("list_content_fingerprint");
        assertThat(countUpsert).contains("dp_pull_dp10_stage_fingerprint_count")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("pass_one_count = pass_one_count + #{passOneDelta}")
                .contains("pass_two_count = pass_two_count + #{passTwoDelta}");
        assertThat(sealRange).contains("list_content_fingerprint &gt; #{afterFingerprint}")
                .contains("ORDER BY list_content_fingerprint ASC")
                .contains("LIMIT #{fetchLimit}")
                .doesNotContain("UNION ALL", "GROUP BY");
        assertThat(verification).contains("verification_state = 'PENDING'");
        assertThat(cursor).contains("apply_state = 'READY'")
                .contains("apply_item_cursor = #{expectedCursor}");
        assertThat(businessSkip)
                .contains("state = 'SKIP_BUSINESS_ITEM'")
                .contains("validation_code = #{validationCode}")
                .contains("apply_state = 'SKIPPED'")
                .contains("task.fence_epoch = #{task.fenceEpoch}")
                .contains("item.generation_no = #{slice.generationNo}")
                .contains("item.scan_pass = 2")
                .contains("item.partition_name = BINARY #{slice.partition}")
                .contains("item.page_no = #{slice.pageNo}")
                .contains("item.item_ordinal = #{slice.itemOrdinal}")
                .contains("item.provider_order_no = BINARY #{slice.order.providerOrderNo}")
                .contains("item.apply_item_cursor = #{slice.itemCursor}")
                .doesNotContain("SET item.apply_item_cursor");
    }

    @Test
    void driftNeverUsesSynchronousGenerationDeleteAndEveryMutationIsTransactional() {
        assertThat(Arrays.stream(Ali1688Dp10StageMapper.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Delete.class))).isEmpty();

        assertTransactional(Ali1688Dp10MyBatisPageStageStore.class,
                List.of("stageList", "recordDetail", "readSealBatch"));
        assertTransactional(Ali1688Dp10BoundedStageStore.class,
                List.of("verifyNext", "nextApplySlice", "recordAppliedSegment",
                        "recordBusinessSkip", "markNextPageApplied", "allApplied"));
        assertTransactional(Ali1688Dp10MyBatisStageCleanup.class,
                List.of("cleanupOlderGenerations", "cleanupCurrentGeneration"));
    }

    @Test
    void canonicalCompatibilityLookupUsesTupleLocalOccurrenceNotGlobalOffset() throws Exception {
        String lookup = sql(
                Ali1688Dp10FactLookupMapper.class,
                "selectCanonicalItemIdByStableTuple",
                Select.class
        );

        assertThat(lookup).contains("ORDER BY id ASC")
                .contains("offer_id", "sku_id", "product_code", "single_product_code")
                .contains("LIMIT #{occurrenceOffset}, 1")
                .doesNotContain("LIMIT #{offset}, 1");
        assertThat(Ali1688Dp10FactLookupMapper.class
                .getDeclaredMethod(
                        "selectCanonicalItemIdByStableTuple",
                        Long.class, String.class, String.class,
                        String.class, String.class, int.class)
                .getReturnType()).isEqualTo(Long.class);
    }

    @Test
    void oversizedUtf8PayloadIsARecoverableContainerFailure() {
        Ali1688HistoricalOrderProvider.OrderSnapshot oversized = order("ORDER-LARGE");
        oversized.setRawSnapshotJson("大".repeat(80));
        Ali1688Dp10ValidatedPage page = page(List.of(oversized));
        Ali1688Dp10StageAssembler assembler = assembler(128);

        assertThatThrownBy(() -> assembler.itemRows(task(), 1L, 1, page))
                .isInstanceOf(Ali1688Dp10PageContractException.class)
                .hasMessage("DP10_STAGE_PAYLOAD_TOO_LARGE");
    }

    @Test
    void everyOversizedRawVariantFailsBeforeAStageRowCanBeBuilt() {
        Ali1688HistoricalOrderProvider.OrderSnapshot first = order("ORDER-LARGE");
        first.setRawSnapshotJson("A".repeat(256));
        Ali1688HistoricalOrderProvider.OrderSnapshot same = order("ORDER-LARGE");
        same.setRawSnapshotJson("A".repeat(256));
        Ali1688HistoricalOrderProvider.OrderSnapshot drifted = order("ORDER-LARGE");
        drifted.setRawSnapshotJson("B".repeat(256));
        Ali1688Dp10StageAssembler assembler = assembler(128);

        assertThatThrownBy(() -> assembler.itemRows(
                task(), 1L, 1, page(List.of(first))))
                .hasMessage("DP10_STAGE_PAYLOAD_TOO_LARGE");
        assertThatThrownBy(() -> assembler.itemRows(
                task(), 1L, 2, page(List.of(same))))
                .hasMessage("DP10_STAGE_PAYLOAD_TOO_LARGE");
        assertThatThrownBy(() -> assembler.itemRows(
                task(), 1L, 2, page(List.of(drifted))))
                .hasMessage("DP10_STAGE_PAYLOAD_TOO_LARGE");
    }

    @Test
    void rejectedUnpersistableLocatorsCannotAbortTheContainingPageInsert() {
        Ali1688HistoricalOrderProvider.OrderSnapshot oversizedIdentity =
                order("O".repeat(121));
        Ali1688HistoricalOrderProvider.OrderSnapshot outOfRangeModifiedAt =
                order("ORDER-TIME");
        outOfRangeModifiedAt.setProviderModifiedAt(Instant.MAX);
        Ali1688Dp10ValidatedPage page = pageEntries(List.of(
                new Ali1688Dp10ListEntry(
                        0,
                        oversizedIdentity,
                        Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
                        "DP10_ORDER_IDENTITY_INVALID",
                        Ali1688Dp10RawOrderFingerprint.fingerprint(oversizedIdentity)
                ),
                new Ali1688Dp10ListEntry(
                        1,
                        outOfRangeModifiedAt,
                        Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
                        "DP10_ORDER_MODIFIED_AT_INVALID",
                        Ali1688Dp10RawOrderFingerprint.fingerprint(outOfRangeModifiedAt)
                )
        ));

        List<Ali1688Dp10StageItemRow> rows =
                assembler(1_024_000).itemRows(task(), 1L, 1, page);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getProviderOrderNo()).isNull();
        assertThat(rows.get(0).getProviderModifiedAt()).isNotNull();
        assertThat(rows.get(1).getProviderOrderNo()).isEqualTo("ORDER-TIME");
        assertThat(rows.get(1).getProviderModifiedAt()).isNull();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getState()).isEqualTo("SKIP_BUSINESS_ITEM");
            assertThat(row.getPayload()).isNotNull();
            assertThat(row.getListContentFingerprint()).hasSize(64);
        });
    }

    @Test
    void systemMapperFailureRemainsContainerFailure() {
        Ali1688Dp10ValidatedPage page = page(List.of(order("ORDER-1")));
        ObjectMapper failedMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw JsonMappingException.fromUnexpectedIOE(new IOException("mapper unavailable"));
            }
        };

        assertThatThrownBy(() -> assembler(failedMapper, 1_024_000)
                .itemRows(task(), 1L, 1, page))
                .isInstanceOf(Ali1688Dp10PageContractException.class)
                .hasMessage("DP10_STAGE_PAYLOAD_ENCODE_FAILED");
    }

    @Test
    void oversizedDetailFailsWithoutMutatingTheStagedItem() {
        Ali1688HistoricalOrderProvider.OrderSnapshot oversized = order("ORDER-DETAIL");
        oversized.setRawSnapshotJson("大".repeat(80));
        Ali1688Dp10StageItemRow item = new Ali1688Dp10StageItemRow();
        item.setProviderOrderNo("ORDER-DETAIL");
        item.setProviderModifiedAt(java.time.LocalDateTime.ofInstant(
                Instant.parse("2026-08-02T03:00:00Z"), java.time.ZoneOffset.UTC));
        item.setListContentFingerprint("sealed-list-fingerprint");

        assertThatThrownBy(() -> assembler(128).applyDetailPayload(
                item, Ali1688Dp10ItemState.COMPLETE, null, oversized))
                .isInstanceOf(Ali1688Dp10PageContractException.class)
                .hasMessage("DP10_STAGE_PAYLOAD_TOO_LARGE");

        assertThat(item.getState()).isNull();
        assertThat(item.getValidationCode()).isNull();
        assertThat(item.getPayload()).isNull();
        assertThat(item.getListContentFingerprint()).isEqualTo("sealed-list-fingerprint");
    }

    private void assertTransactional(Class<?> type, List<String> names) {
        for (String name : names) {
            Method method = Arrays.stream(type.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name))
                    .findFirst().orElseThrow();
            Transactional transaction = method.getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.timeout())
                    .isEqualTo(DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS);
        }
    }

    private <T extends java.lang.annotation.Annotation> String sql(
            Class<?> type,
            String name,
            Class<T> annotationType
    ) throws Exception {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
        java.lang.annotation.Annotation annotation = method.getAnnotation(annotationType);
        if (annotation instanceof Update) return String.join(" ", ((Update) annotation).value());
        if (annotation instanceof Insert) return String.join(" ", ((Insert) annotation).value());
        return String.join(" ", ((Select) annotation).value());
    }

    private Ali1688Dp10StageAssembler assembler(int payloadBytes) {
        return assembler(new ObjectMapper().findAndRegisterModules(), payloadBytes);
    }

    private Ali1688Dp10StageAssembler assembler(ObjectMapper mapper, int payloadBytes) {
        return new Ali1688Dp10StageAssembler(null, mapper, payloadBytes);
    }

    private DataPullTask task() {
        DataPullTask task = new DataPullTask();
        task.setId(101L);
        task.setFenceEpoch(7L);
        return task;
    }

    private Ali1688Dp10ValidatedPage page(
            List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders
    ) {
        List<Ali1688Dp10ListEntry> entries = new ArrayList<>();
        for (int index = 0; index < orders.size(); index++) {
            Ali1688HistoricalOrderProvider.OrderSnapshot order = orders.get(index);
            entries.add(new Ali1688Dp10ListEntry(
                    index, order, Ali1688Dp10ItemState.COMPLETE, null,
                    Ali1688Dp10RawOrderFingerprint.fingerprint(order)));
        }
        return pageEntries(entries);
    }

    private Ali1688Dp10ValidatedPage pageEntries(List<Ali1688Dp10ListEntry> entries) {
        return new Ali1688Dp10ValidatedPage(
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                1, Math.max(entries.size(), 1), entries.size(), 1, entries);
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot order(String providerOrderNo) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo(providerOrderNo);
        order.setProviderModifiedAt(Instant.parse("2026-08-02T03:00:00Z"));
        return order;
    }

}
