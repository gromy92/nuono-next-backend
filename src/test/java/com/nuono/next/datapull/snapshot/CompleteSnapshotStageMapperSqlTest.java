package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class CompleteSnapshotStageMapperSqlTest {

    @Test
    void taskAndHeaderLocksUseTheAuthoritativeRuntimeFenceInOneLockOrder() throws Exception {
        String taskLock = sql("selectTaskForUpdate", Select.class, long.class);
        String headerLock = sql("selectAggregateForUpdate", Select.class, long.class);
        String adopt = sql("adoptFence", Update.class, long.class, long.class);

        assertThat(taskLock)
                .contains("FROM dp_pull_task")
                .contains("fence_epoch AS fenceEpoch")
                .contains("state")
                .contains("lease_until > UTC_TIMESTAMP(3)")
                .contains("AS leaseValid")
                .contains("WHERE id = #{taskId}")
                .contains("FOR UPDATE");
        assertThat(headerLock)
                .contains("FROM dp_pull_snapshot_stage")
                .contains("WHERE task_id = #{taskId}")
                .contains("FOR UPDATE");
        assertThat(adopt)
                .contains("active_fence_epoch < #{fenceEpoch}")
                .contains("version_no = version_no + 1");
    }

    @Test
    void pageAndItemsPersistMetadataIdentityFingerprintAndExplicitPayload() throws Exception {
        String headerInsert = sql(
                "insertAggregateIfAbsent",
                Insert.class,
                long.class,
                long.class
        );
        String pageInsert = sql("insertPage", Insert.class, SnapshotStagePageRow.class);
        String itemInsert = sql("insertItems", Insert.class, java.util.List.class);
        String metadata = sql(
                "updateMetadata", Update.class, long.class, long.class,
                Integer.class, Integer.class, String.class, String.class,
                LocalDateTime.class, Long.class
        );

        assertThat(headerInsert)
                .contains("INSERT IGNORE INTO dp_pull_snapshot_stage")
                .contains("active_fence_epoch");
        assertThat(pageInsert)
                .contains("INSERT INTO dp_pull_snapshot_stage_page")
                .contains("page_no, next_page, is_last_page, total_pages, item_count")
                .contains("source_item_count, business_skipped_item_count");
        assertThat(metadata)
                .contains("authority_kind = #{authorityKind}")
                .contains("authority_token_sha256 = #{authorityTokenSha256}")
                .contains("declared_collection_count = #{declaredCollectionCount}");
        assertThat(itemInsert)
                .contains("INSERT INTO dp_pull_snapshot_stage_item")
                .contains("item_ordinal, stable_identity, content_fingerprint, payload")
                .contains("validated_identity_candidate, absence_reconciliation_safe")
                .contains("<foreach collection='rows' item='row' separator=','>")
                .doesNotContain("ObjectOutputStream")
                .doesNotContain("java_class");
    }

    @Test
    void proofReadsEveryPageAndItemInDeterministicProviderOrderWithoutALimit() throws Exception {
        String pages = sql("selectPages", Select.class, long.class);
        String items = sql("selectItems", Select.class, long.class);
        String pageItems = sql("selectPageItems", Select.class, long.class, int.class);

        assertThat(pages)
                .contains("ORDER BY page_no ASC")
                .doesNotContain("LIMIT");
        assertThat(items)
                .contains("ORDER BY page_no ASC, item_ordinal ASC")
                .contains("stable_identity AS stableIdentity")
                .contains("content_fingerprint AS contentFingerprint")
                .contains("payload")
                .doesNotContain("LIMIT");
        assertThat(pageItems)
                .contains("page_no = #{pageNo}")
                .contains("ORDER BY item_ordinal ASC");
    }

    @Test
    void poisonIsFirstFailureWinsAndClearOnlyAcceptsANonStaleFence() throws Exception {
        String poison = sql("poison", Update.class, long.class, long.class, String.class);
        String clear = sql("deleteAggregate", Delete.class, long.class, long.class);

        assertThat(poison)
                .contains("poison_code = COALESCE(poison_code, #{poisonCode})")
                .contains("active_fence_epoch = #{fenceEpoch}")
                .contains("poison_code IS NULL");
        assertThat(clear)
                .contains("DELETE FROM dp_pull_snapshot_stage")
                .contains("active_fence_epoch <= #{fenceEpoch}")
                .contains("NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_apply_progress")
                .contains("progress.prepared_item_count>0")
                .contains("progress.state='SEALED'");
    }

    @Test
    void resetDeletesChildrenInExplicitBoundedBatchesBeforeTheAggregate() throws Exception {
        String items = sql("deleteStageItemsBounded", Delete.class, long.class, int.class);
        String pages = sql("deleteEmptyStagePagesBounded", Delete.class, long.class, int.class);

        assertThat(items)
                .contains("WHERE task_id = #{taskId}")
                .contains("ORDER BY page_no ASC, item_ordinal ASC")
                .contains("LIMIT #{batchSize}");
        assertThat(pages)
                .contains("NOT EXISTS")
                .contains("LIMIT #{batchSize}");
    }

    private String sql(
            String methodName,
            Class<? extends Annotation> annotationType,
            Class<?>... parameterTypes
    ) throws Exception {
        Method method = CompleteSnapshotStageMapper.class.getMethod(methodName, parameterTypes);
        Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] fragments;
        if (annotation instanceof Select) {
            fragments = ((Select) annotation).value();
        } else if (annotation instanceof Insert) {
            fragments = ((Insert) annotation).value();
        } else if (annotation instanceof Update) {
            fragments = ((Update) annotation).value();
        } else {
            fragments = ((Delete) annotation).value();
        }
        String rawSql = String.join("\n", fragments);
        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
        return rawSql.replaceAll("\\s+", " ");
    }
}
