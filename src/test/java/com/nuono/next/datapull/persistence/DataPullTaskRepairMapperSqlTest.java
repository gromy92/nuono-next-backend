package com.nuono.next.datapull.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullTaskRepairMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DataPullTaskRepairMapperSqlTest {

    @Test
    void repairIsFailedVersionFenceBoundAndPreservesResumeIdentity() throws Exception {
        String sql = sql();

        assertThat(sql)
                .contains("target.state = 'FAILED'")
                .contains("target.version_no = #{command.expectedVersion}")
                .contains("target.fence_epoch = #{command.expectedFenceEpoch}")
                .contains("target.sanitized_failure_code = BINARY #{command.expectedFailureCode}")
                .contains("successor.state NOT IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED')")
                .contains("successor.id IS NULL")
                .contains("target.state = 'QUEUED'")
                .contains("target.version_no = target.version_no + 1")
                .doesNotContain("target.step_code =")
                .doesNotContain("target.remote_handle =")
                .doesNotContain("target.checkpoint =")
                .doesNotContain("target.business_window_key =");
    }

    private String sql() throws Exception {
        Method method = Arrays.stream(DataPullTaskRepairMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("requeueFailed"))
                .findFirst()
                .orElseThrow();
        String raw = String.join("\n", method.getAnnotation(Update.class).value());
        new XMLLanguageDriver().createSqlSource(new Configuration(), raw, Object.class);
        return raw.replaceAll("\\s+", " ");
    }
}
