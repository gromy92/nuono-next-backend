package com.nuono.next.datapull.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleApplyMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskPlanMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DataPullRuntimeMapperSqlRenderingTest {

    @Test
    void rendersRawAnnotationComparatorsAsSqlInsteadOfXmlEntities() {
        Configuration configuration = new Configuration();
        configuration.addMapper(DataPullScheduleScanMapper.class);
        configuration.addMapper(DataPullScheduleApplyMapper.class);
        configuration.addMapper(DataPullScheduleTaskPlanMapper.class);
        configuration.addMapper(DataPullReportArtifactChunkMapper.class);
        List<String> statements = List.of(
                statement(DataPullScheduleScanMapper.class, "advancePassOne"),
                statement(DataPullScheduleScanMapper.class, "advancePassTwo"),
                statement(DataPullScheduleScanMapper.class, "advanceManifestSeal"),
                statement(DataPullScheduleApplyMapper.class, "advanceAdmissionPhase"),
                statement(DataPullScheduleApplyMapper.class, "advanceBindingPresentPhase"),
                statement(DataPullScheduleApplyMapper.class, "advanceBindingMissingPhase"),
                statement(DataPullScheduleTaskPlanMapper.class, "advanceSchedulePhase"),
                statement(DataPullReportArtifactChunkMapper.class, "advanceDownloadProgress"),
                statement(DataPullReportArtifactChunkMapper.class, "selectOverlappingChunks")
        );

        for (String statement : statements) {
            BoundSql sql = configuration.getMappedStatement(statement).getBoundSql(Map.of());
            assertThat(sql.getSql()).as(statement)
                    .doesNotContain("&lt;", "&gt;");
        }
    }

    private static String statement(Class<?> mapper, String method) {
        return mapper.getName() + "." + method;
    }
}
