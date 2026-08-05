package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.InventorySnapshotRuntimeMapper;
import com.nuono.next.infrastructure.mapper.NoonAdvertisingMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.infrastructure.mapper.PostSaleProfitBatchAttributionMapper;
import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import com.nuono.next.infrastructure.mapper.SnapshotStageProofMapper;
import com.nuono.next.infrastructure.mapper.SnapshotStageRetentionMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassRetentionMapper;
import com.nuono.next.infrastructure.mapper.SnapshotCarryProgressMapper;
import com.nuono.next.infrastructure.mapper.SnapshotEffectiveItemMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class SnapshotBoundedSqlContractTest {

    @Test
    void metadataProofNeverMaterializesPayloadAndValidatesEveryStoredItem() throws Exception {
        String manifest = sql(SnapshotStageProofMapper.class, "selectManifest", Select.class);
        String invalidItems = sql(
                SnapshotStageProofMapper.class, "countInvalidItems", Select.class
        );

        assertThat(manifest)
                .contains("COUNT(*) FROM dp_pull_snapshot_stage_item")
                .contains("COUNT(DISTINCT stable_identity)")
                .contains("SUM(source_item_count)")
                .doesNotContain("SELECT payload");
        assertThat(invalidItems)
                .contains("validated_identity_candidate IS NULL")
                .contains("absence_reconciliation_safe IS NULL")
                .contains("LOWER(SHA2(payload,256))<>content_fingerprint");
    }

    @Test
    void retentionDeletesOnlyBoundedSuccessfulSupersededGenerations() throws Exception {
        for (String method : List.of(
                "deleteSupersededEffectiveItemsBatch",
                "deleteSupersededItemsBatch",
                "deleteSupersededPagesBatch",
                "deleteSupersededStagesBatch"
        )) {
            String statement = sql(SnapshotStageRetentionMapper.class, method, Delete.class);
            assertThat(statement)
                    .contains("dp_pull_snapshot_current_head")
                    .contains("h.task_id<>")
                    .contains("t.state='SUCCEEDED'")
                    .contains("t.finished_at<#{cutoffUtc}")
                    .contains("LIMIT #{limit}");
            if (method.equals("deleteSupersededEffectiveItemsBatch")) {
                assertActiveCarryFence(statement, "t");
            }
        }
        for (String method : List.of(
                "retireSupersededInventoryLinesBatch",
                "retireSupersededInventoryBatchesBatch"
        )) {
            String statement = sql(InventorySnapshotRuntimeMapper.class, method, Update.class);
            assertThat(statement)
                    .contains("dp_pull_snapshot_current_head")
                    .contains("current_head.task_id<>progress.task_id")
                    .contains("task.state='SUCCEEDED'")
                    .contains("LIMIT #{limit}");
            assertActiveCarryFence(statement, "task");
        }
    }

    @Test
    void twoPassRetentionIsBoundedAndKeepsActiveCarrySources() throws Exception {
        for (String method : List.of(
                "deleteSupersededVerifyPages",
                "deleteSupersededFingerprintCounts"
        )) {
            String statement = sql(SnapshotTwoPassRetentionMapper.class, method, Delete.class);
            assertThat(statement)
                    .contains("dp_pull_snapshot_current_head")
                    .contains("t.state='SUCCEEDED'")
                    .contains("LIMIT #{limit}");
            assertActiveCarryFence(statement, "t");
        }
        for (String method : List.of(
                "deleteAbandonedVerifyPages",
                "deleteAbandonedFingerprintCounts"
        )) {
            assertAbandonedRetentionFence(
                    sql(SnapshotTwoPassRetentionMapper.class, method, Delete.class), "t"
            );
        }
    }

    @Test
    void abandonedRetentionRequiresAQuiescentUnreferencedTerminalTask() throws Exception {
        for (String method : List.of(
                "deleteAbandonedEffectiveItemsBatch",
                "deleteAbandonedItemsBatch",
                "deleteAbandonedPagesBatch",
                "deleteAbandonedStagesBatch"
        )) {
            String statement = sql(SnapshotStageRetentionMapper.class, method, Delete.class);
            assertAbandonedRetentionFence(statement, "t");
        }
        for (String method : List.of(
                "retireAbandonedInventoryLinesBatch",
                "retireAbandonedInventoryBatchesBatch"
        )) {
            String statement = sql(InventorySnapshotRuntimeMapper.class, method, Update.class);
            assertAbandonedRetentionFence(statement, "task");
            assertThat(statement).contains("operation_code='DP07A'");
        }
    }

    @Test
    void genericAbandonedStageRetentionIncludesDp06RawAcquisitionRows() {
        assertThat(SnapshotStageRetentionMapper.ABANDONED_TASK)
                .contains("t.operation_code IN ('DP04','DP06','DP07A')");
    }

    @Test
    void inventoryPreparationUsesOneIdBlockAndKeepsRowsInvisibleUntilSeal() throws Exception {
        String reserve = sql(
                InventorySnapshotRuntimeMapper.class,
                "reserveInventorySnapshotLineIds",
                Insert.class
        );
        String stage = sql(
                InventorySnapshotRuntimeMapper.class,
                "insertStagedInventorySnapshotLine",
                Insert.class
        );
        String seal = sql(
                InventorySnapshotRuntimeMapper.class,
                "markInventorySyncBatchImported",
                Update.class
        );

        assertThat(reserve)
                .contains("LAST_INSERT_ID(#{initialValue}+#{blockSize})")
                .contains("LAST_INSERT_ID(next_id+#{blockSize})");
        assertThat(stage)
                .contains("snapshot_stable_identity")
                .contains("#{snapshotStableIdentity}")
                .contains("b'0', b'0'");
        assertThat(seal)
                .contains("status='IMPORTED'")
                .contains("status='STAGING'")
                .doesNotContain("official_warehouse_inventory_snapshot_line");
    }

    @Test
    void effectiveCarryIsKeysetBoundedAndNeverBuildsARecursiveLineage() throws Exception {
        String start = sql(
                SnapshotCarryProgressMapper.class, "startCarry", Update.class
        );
        String advance = sql(
                SnapshotCarryProgressMapper.class, "advanceCarry", Update.class
        );
        String full = sql(
                SnapshotEffectiveItemMapper.class, "selectFullCarryChunk", Select.class
        );
        String targeted = sql(
                SnapshotEffectiveItemMapper.class, "selectTargetedCarryChunk", Select.class
        );
        String inventory = sql(
                InventorySnapshotRuntimeMapper.class, "selectInventoryCarryChunk", Select.class
        );

        assertThat(start)
                .contains("state='CARRYING'")
                .contains("carry_source_head_version=#{sourceHeadVersion}");
        assertThat(advance)
                .contains("carry_cursor_identity <=> #{expectedStableIdentity}")
                .contains("effective_item_count=progress.effective_item_count+#{effectiveDelta}");
        for (String statement : List.of(full, inventory)) {
            assertThat(statement)
                    .contains(">COALESCE(#{afterStableIdentity},'')")
                    .contains("LIMIT #{limit}")
                    .doesNotContain("WITH RECURSIVE")
                    .doesNotContain("predecessor");
        }
        assertThat(targeted)
                .contains("problem.validated_identity_candidate=b'0'")
                .contains("problem.absence_reconciliation_safe=b'1'")
                .contains("problem.stable_identity=old.stable_identity")
                .contains("LIMIT #{limit}");
    }

    @Test
    void everyKnownCurrentInventoryReaderUsesTheCompatibilityView() {
        assertViewReads(OfficialWarehouseStatisticsMapper.class, 4);
        assertViewReads(PostSaleProfitBatchAttributionMapper.class, 2);
        assertViewReads(ProductKeywordMapper.class, 1);
        assertViewReads(SalesDataMapper.class, 1);
        assertViewReads(NoonAdvertisingMapper.class, 4);
    }

    private void assertViewReads(Class<?> mapper, long minimumCount) {
        long count = Arrays.stream(mapper.getMethods())
                .map(method -> method.getAnnotation(Select.class))
                .filter(java.util.Objects::nonNull)
                .map(annotation -> String.join(" ", annotation.value()))
                .mapToLong(statement -> statement.split(
                        "official_warehouse_effective_inventory_snapshot_line", -1
                ).length - 1L)
                .sum();
        assertThat(count).as(mapper.getSimpleName()).isGreaterThanOrEqualTo(minimumCount);
    }

    private void assertAbandonedRetentionFence(String statement, String taskAlias) {
        assertThat(statement)
                .contains(taskAlias + ".state IN ('FAILED','SUPERSEDED')")
                .contains(taskAlias + ".finished_at<#{cutoffUtc}")
                .contains(taskAlias + ".lease_owner IS NULL")
                .contains(taskAlias + ".lease_until IS NULL")
                .contains("dp_pull_snapshot_current_head active_head")
                .contains("active_head.task_id=" + taskAlias + ".id")
                .contains("LIMIT #{limit}")
                .doesNotContain("active_carry.state='CARRYING'");
        assertActiveCarryFence(statement, taskAlias);
    }

    private void assertActiveCarryFence(String statement, String taskAlias) {
        assertThat(statement)
                .contains("active_carry.carry_source_task_id=" + taskAlias + ".id")
                .contains("carry_task.state NOT IN ('SUCCEEDED','FAILED','SUPERSEDED')")
                .doesNotContain("active_carry.state='CARRYING'");
    }

    private String sql(
            Class<?> mapper,
            String methodName,
            Class<? extends Annotation> annotationType
    ) throws Exception {
        Method method = Arrays.stream(mapper.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] fragments;
        if (annotation instanceof Select) fragments = ((Select) annotation).value();
        else if (annotation instanceof Insert) fragments = ((Insert) annotation).value();
        else if (annotation instanceof Update) fragments = ((Update) annotation).value();
        else fragments = ((Delete) annotation).value();
        String raw = String.join("\n", fragments);
        new XMLLanguageDriver().createSqlSource(new Configuration(), raw, Object.class);
        return raw.replaceAll("\\s+", " ");
    }
}
