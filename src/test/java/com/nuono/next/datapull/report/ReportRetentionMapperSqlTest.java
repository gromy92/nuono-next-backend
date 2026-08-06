package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkRetentionMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportArtifactMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportLocatorMapper;
import com.nuono.next.infrastructure.mapper.ReportStageRetentionMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ReportRetentionMapperSqlTest {

    @Test
    void persistenceRowsAreBoundToTheRuntimeTaskIdentity() throws Exception {
        assertThat(sql(DataPullReportArtifactMapper.class, "insertIfAbsent", Insert.class))
                .contains("artifact_key, task_id, stable_request_key")
                .contains("#{row.taskId}");
        assertThat(sql(DataPullReportLocatorMapper.class, "insert", Insert.class))
                .contains("locator_ref, task_id, stable_request_key")
                .contains("#{row.taskId}");
    }

    @Test
    void artifactCleanupIsTerminalGraceBoundAndDeterministicallyBatched() throws Exception {
        String cleanup = sql(
                DataPullReportArtifactMapper.class,
                "deleteTerminalBatch",
                Delete.class
        );
        assertRetentionContract(cleanup, "artifact.created_at", "artifact.artifact_key");
        assertThat(cleanup)
                .contains("NOT EXISTS")
                .contains("FROM dp_pull_report_stage stage")
                .contains("stage.artifact_key=BINARY artifact.artifact_key")
                .contains("FROM dp_pull_report_artifact_chunk chunk")
                .contains("chunk.artifact_key=BINARY artifact.artifact_key");
    }

    @Test
    void artifactChunksAreDeletedInSmallDeterministicChildFirstBatches() throws Exception {
        String terminal = sql(
                DataPullReportArtifactChunkRetentionMapper.class,
                "deleteTerminalBatch",
                Delete.class
        );
        String abandoned = sql(
                DataPullReportArtifactChunkRetentionMapper.class,
                "deleteAbandonedBatch",
                Delete.class
        );
        assertThat(terminal)
                .contains("DELETE FROM dp_pull_report_artifact_chunk")
                .contains("INNER JOIN dp_pull_task task")
                .contains("task.state IN ('SUCCEEDED','SUPERSEDED')")
                .contains("ORDER BY task.finished_at ASC,artifact.created_at ASC,")
                .contains("artifact.artifact_key ASC,chunk.chunk_no ASC")
                .contains("LIMIT #{batchSize}");
        assertThat(abandoned)
                .contains("DELETE FROM dp_pull_report_artifact_chunk")
                .contains("task.state='FAILED'")
                .contains("artifact.artifact_key ASC,chunk.chunk_no ASC")
                .contains("LIMIT #{batchSize}");
    }

    @Test
    void locatorCleanupIsTerminalGraceBoundAndDeterministicallyBatched() throws Exception {
        assertRetentionContract(sql(
                DataPullReportLocatorMapper.class,
                "deleteTerminalBatch",
                Delete.class
        ), "locator.created_at", "locator.locator_ref");
    }

    @Test
    void stageCleanupDeletesBoundedRowsBeforeOnlyEmptyHeaders() throws Exception {
        String rows = sql(
                ReportStageRetentionMapper.class,
                "deleteTerminalRowsBatch",
                Delete.class
        );
        String stages = sql(
                ReportStageRetentionMapper.class,
                "deleteTerminalStagesBatch",
                Delete.class
        );

        assertRetentionContract(rows, "stage.gmt_updated", "stage_row.task_id");
        assertThat(rows)
                .contains("DELETE FROM dp_pull_report_stage_row")
                .contains("stage.state = 'APPLIED'")
                .contains("FROM official_warehouse_report_import imported")
                .contains("imported.is_deleted = b'0'")
                .contains("stage_row.`row_number` ASC")
                .contains("LIMIT #{batchSize}");
        assertRetentionContract(stages, "stage.gmt_updated", "stage.task_id");
        assertThat(stages)
                .contains("DELETE FROM dp_pull_report_stage")
                .contains("stage.state = 'APPLIED'")
                .contains("FROM official_warehouse_report_import imported")
                .contains("imported.is_deleted = b'0'")
                .contains("NOT EXISTS")
                .contains("FROM dp_pull_report_stage_row stage_row")
                .contains("LIMIT #{batchSize}");
    }

    @Test
    void failedArtifactsAreRetainedForLongRepairGraceThenDeletedInForeignKeyOrder()
            throws Exception {
        String rows = sql(
                ReportStageRetentionMapper.class, "deleteAbandonedRowsBatch", Delete.class
        );
        String stages = sql(
                ReportStageRetentionMapper.class, "deleteAbandonedStagesBatch", Delete.class
        );
        String artifacts = sql(
                DataPullReportArtifactMapper.class, "deleteAbandonedBatch", Delete.class
        );
        String locators = sql(
                DataPullReportLocatorMapper.class, "deleteAbandonedBatch", Delete.class
        );
        String chunks = sql(
                DataPullReportArtifactChunkRetentionMapper.class,
                "deleteAbandonedBatch",
                Delete.class
        );
        for (String statement : java.util.List.of(rows, stages, chunks, artifacts, locators)) {
            assertThat(statement)
                    .contains("task.state='FAILED'")
                    .contains("task.finished_at < #{cutoffUtc}")
                    .contains("task.lease_owner IS NULL")
                    .contains("task.lease_until IS NULL")
                    .contains("LIMIT #{batchSize}");
        }
        assertThat(stages).contains("NOT EXISTS (SELECT 1 FROM dp_pull_report_stage_row");
        assertThat(artifacts).contains("NOT EXISTS (SELECT 1 FROM dp_pull_report_stage stage");
    }

    @Test
    void everyArtifactAndRetentionStatementHasTheRuntimeDatabaseTimeout() {
        for (Class<?> mapper : java.util.List.of(
                DataPullReportArtifactMapper.class,
                DataPullReportArtifactChunkRetentionMapper.class,
                DataPullReportLocatorMapper.class,
                ReportStageRetentionMapper.class
        )) {
            for (Method method : mapper.getDeclaredMethods()) {
                Options options = method.getAnnotation(Options.class);
                assertThat(options).as(mapper.getSimpleName() + "." + method.getName())
                        .isNotNull();
                assertThat(options.timeout()).isEqualTo(10);
            }
        }
    }

    private void assertRetentionContract(String sql, String createdAt, String identity) {
        assertThat(sql)
                .contains("INNER JOIN dp_pull_task task ON task.id =")
                .contains("task.state IN ('SUCCEEDED', 'SUPERSEDED')")
                .contains("task.state = 'SUPERSEDED' OR")
                .contains("FROM dp_pull_report_apply applied")
                .contains("task.finished_at IS NOT NULL")
                .contains("task.finished_at < #{cutoffUtc}")
                .contains(createdAt + " < #{cutoffUtc}")
                .contains("task.lease_owner IS NULL")
                .contains("task.lease_until IS NULL")
                .contains("ORDER BY task.finished_at ASC")
                .contains(identity + " ASC")
                .contains("LIMIT #{batchSize}")
                .doesNotContain(
                        "'FAILED'",
                        "WAITING_REMOTE",
                        "WAITING_BACKOFF",
                        "WAITING_AUTH",
                        "RUNNING"
                );
    }

    private String sql(
            Class<?> mapperType,
            String methodName,
            Class<? extends Annotation> annotationType
    ) throws Exception {
        Method method = java.util.Arrays.stream(mapperType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] fragments = annotation instanceof Insert
                ? ((Insert) annotation).value()
                : ((Delete) annotation).value();
        String raw = String.join("\n", fragments);
        new XMLLanguageDriver().createSqlSource(new Configuration(), raw, Object.class);
        return raw.replace("&lt;", "<").replaceAll("\\s+", " ");
    }
}
