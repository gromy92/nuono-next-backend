package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.Ali1688Dp10FailedStageRetentionMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageCleanupMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class Ali1688Dp10StageCleanupMapperSqlTest {

    @Test
    void liveDeletesAreExactGenerationAndDeterministicallyBounded() throws Exception {
        assertExactDelete("deleteFingerprintCountBatch",
                "dp_pull_dp10_stage_fingerprint_count",
                "partition_name ASC, list_content_fingerprint ASC");
        assertExactDelete("deleteIdentityBatch", "dp_pull_dp10_stage_identity",
                "provider_order_no ASC");
        assertExactDelete("deleteItemBatch", "dp_pull_dp10_stage_item",
                "scan_pass ASC, partition_name ASC,page_no ASC,item_ordinal ASC");
        assertExactDelete("deletePageBatch", "dp_pull_dp10_stage_page",
                "scan_pass ASC, partition_name ASC,page_no ASC");
    }

    @Test
    void olderCleanupSelectsOnlyTheOldestGenerationAcrossAllStageTables() throws Exception {
        String sql = sql(Ali1688Dp10StageCleanupMapper.class,
                "selectOldestGenerationBefore", Select.class);
        assertThat(sql)
                .contains("SELECT MIN(generation_no)")
                .contains("dp_pull_dp10_stage_fingerprint_count")
                .contains("dp_pull_dp10_stage_identity")
                .contains("dp_pull_dp10_stage_item")
                .contains("dp_pull_dp10_stage_page")
                .contains("generation_no<#{currentGenerationNo}")
                .doesNotContain("LIMIT #{batchSize}");
    }

    @Test
    void markerCreateRefreshAndDeleteNeverSwallowConflictingIdentity() throws Exception {
        String insert = sql(Ali1688Dp10StageCleanupMapper.class,
                "insertMarker", Insert.class);
        assertThat(insert).contains("INSERT INTO dp_pull_dp10_stage_cleanup")
                .doesNotContain("IGNORE", "ON DUPLICATE KEY");

        String refresh = sql(Ali1688Dp10StageCleanupMapper.class,
                "refreshMarker", Update.class);
        assertThat(refresh)
                .contains("task_id=#{taskId}")
                .contains("generation_no=#{generationNo}")
                .contains("reason=#{reason}")
                .contains("active_fence_epoch<=#{fenceEpoch}");

        String adopt = sql(Ali1688Dp10StageCleanupMapper.class,
                "adoptMarkerForFailedRetention", Update.class);
        assertThat(adopt)
                .contains("SET reason='FAILED_RETENTION'")
                .contains("task_id=#{taskId}")
                .contains("generation_no=#{generationNo}")
                .contains("reason=#{oldReason}")
                .contains("active_fence_epoch=#{oldFenceEpoch}")
                .contains("active_fence_epoch<=#{newFenceEpoch}");

        String delete = sql(Ali1688Dp10StageCleanupMapper.class,
                "deleteMarker", Delete.class);
        assertThat(delete)
                .contains("task_id=#{taskId}")
                .contains("generation_no=#{generationNo}")
                .contains("reason=#{reason}")
                .contains("active_fence_epoch=#{fenceEpoch}");
    }

    @Test
    void failedRetentionSelectsOneOldestTaskGenerationThenLocksEligibility() throws Exception {
        String marked = sql(Ali1688Dp10FailedStageRetentionMapper.class,
                "selectOldestEligibleMarker", Select.class);
        assertThat(marked)
                .contains("FROM dp_pull_dp10_stage_cleanup marker")
                .contains("TRUE AS markerCandidate")
                .contains("task.state='FAILED'")
                .contains("LIMIT 1")
                .doesNotContain("marker.reason=");

        String select = sql(Ali1688Dp10FailedStageRetentionMapper.class,
                "selectOldestEligibleGeneration", Select.class);
        assertThat(select)
                .contains("task.operation_code='DP10'")
                .contains("task.state='FAILED'")
                .contains("task.finished_at<#{cutoffUtc}")
                .contains("task.lease_owner IS NULL")
                .contains("task.lease_until IS NULL")
                .contains("FALSE AS markerCandidate")
                .contains("ORDER BY task.finished_at ASC,candidate.task_id ASC,candidate.generation_no ASC")
                .contains("LIMIT 1")
                .doesNotContain("DELETE FROM");

        String lock = sql(Ali1688Dp10FailedStageRetentionMapper.class,
                "lockEligibleTaskFence", Select.class);
        assertThat(lock)
                .contains("task.id=#{taskId}")
                .contains("task.state='FAILED'")
                .contains("task.step_code AS stepCode,task.checkpoint")
                .contains("FOR UPDATE");
    }

    private void assertExactDelete(String method, String table, String ordering)
            throws Exception {
        String sql = sql(Ali1688Dp10StageCleanupMapper.class, method, Delete.class);
        assertThat(sql)
                .contains("DELETE FROM " + table)
                .contains("task_id=#{taskId}")
                .contains("generation_no=#{generationNo}")
                .contains("ORDER BY " + ordering)
                .contains("LIMIT #{batchSize}")
                .doesNotContain("generation_no<", "older");
    }

    private String sql(
            Class<?> mapper,
            String name,
            Class<? extends Annotation> annotationType
    ) throws Exception {
        Method method = Arrays.stream(mapper.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] fragments;
        if (annotation instanceof Delete) fragments = ((Delete) annotation).value();
        else if (annotation instanceof Select) fragments = ((Select) annotation).value();
        else if (annotation instanceof Insert) fragments = ((Insert) annotation).value();
        else fragments = ((Update) annotation).value();
        String raw = String.join("\n", fragments);
        assertThat(raw).doesNotContain("&lt;", "&gt;");
        new XMLLanguageDriver().createSqlSource(new Configuration(), raw, Object.class);
        return raw.replaceAll("\\s+", " ");
    }
}
