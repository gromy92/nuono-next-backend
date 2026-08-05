package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ReportArtifactChunkMapperSqlTest {
    @Test
    void insertAndRereadAreBothBoundToTheSameWriterFenceAndProgress() throws Exception {
        String insert = sql("insertChunkIfCurrentWriter", Insert.class);
        String reread = sql("selectCurrentWriterChunk", Select.class);

        for (String statement : java.util.List.of(insert, reread)) {
            assertThat(statement)
                    .contains("manifest.download_state='DOWNLOADING'")
                    .contains("manifest.download_fence_epoch=#{fenceEpoch}");
        }
        assertThat(insert)
                .contains("INSERT INTO dp_pull_report_artifact_chunk")
                .contains("FROM dp_pull_report_artifact manifest")
                .contains("manifest.downloaded_chunk_count=#{row.chunkNo}")
                .contains("manifest.downloaded_byte_count=#{row.byteOffset}")
                .contains("ON DUPLICATE KEY UPDATE");
        assertThat(reread)
                .contains("manifest.downloaded_chunk_count=#{chunkNo}")
                .contains("manifest.downloaded_byte_count=#{byteOffset}")
                .contains("chunk.content_bytes AS contentBytes");
    }

    @Test
    void progressAdvanceIsCompareAndSetAndNeverExceedsBoundResponse() throws Exception {
        assertThat(sql("advanceDownloadProgress", Update.class))
                .contains("download_fence_epoch=#{fenceEpoch}")
                .contains("downloaded_byte_count=#{expectedByteOffset}")
                .contains("downloaded_chunk_count=#{expectedChunkNo}")
                .contains("expected_content_length IS NOT NULL")
                .contains("#{nextByteOffset} <= expected_content_length");
    }

    @Test
    void completionAtomicallyChecksFenceDigestProgressAndChildAggregate() throws Exception {
        assertThat(sql("completeDownload", Update.class))
                .contains("manifest.download_fence_epoch=#{fenceEpoch}")
                .contains("manifest.downloaded_byte_count=#{contentLength}")
                .contains("manifest.downloaded_chunk_count=#{chunkCount}")
                .contains("manifest.expected_content_length=#{contentLength}")
                .contains("manifest.resumable_sha256_state=BINARY #{resumableSha256State}")
                .contains("SELECT COUNT(*) FROM dp_pull_report_artifact_chunk")
                .contains("SELECT COALESCE(SUM(chunk.content_length),0)")
                .contains("SELECT COALESCE(MAX(chunk.chunk_no),-1)")
                .contains("=#{chunkCount}-1");
    }

    @Test
    void everyChunkStatementHasTheRuntimeDatabaseTimeout() {
        for (Method method : DataPullReportArtifactChunkMapper.class.getDeclaredMethods()) {
            Options options = method.getAnnotation(Options.class);
            assertThat(options).as(method.getName()).isNotNull();
            assertThat(options.timeout()).as(method.getName()).isEqualTo(10);
        }
    }

    private String sql(
            String methodName,
            Class<? extends Annotation> annotationType
    ) throws Exception {
        Method method = java.util.Arrays.stream(
                DataPullReportArtifactChunkMapper.class.getDeclaredMethods()
        ).filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] fragments;
        if (annotation instanceof Insert) fragments = ((Insert) annotation).value();
        else if (annotation instanceof Select) fragments = ((Select) annotation).value();
        else fragments = ((Update) annotation).value();
        String raw = String.join("\n", fragments);
        new XMLLanguageDriver().createSqlSource(new Configuration(), raw, Object.class);
        return raw.replace("&lt;", "<").replaceAll("\\s+", " ");
    }
}
