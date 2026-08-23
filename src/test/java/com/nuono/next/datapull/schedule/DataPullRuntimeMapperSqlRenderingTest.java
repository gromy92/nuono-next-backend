package com.nuono.next.datapull.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullReportArtifactMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleApplyMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskBatchMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskPlanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeAdmissionMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeProgressMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCreationMapper;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DataPullRuntimeMapperSqlRenderingTest {
    private static final Pattern UNQUALIFIED_DUPLICATE_NO_OP = Pattern.compile(
            "(?i)ON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\s+([a-z_]+)\\s*=\\s*\\1(?:\\s|,|$)"
    );

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

    @Test
    void qualifiesDuplicateNoOpsAgainstTheirTargetTable() {
        List<Class<?>> mappers = List.of(
                DataPullScheduleAnchorMapper.class,
                DataPullScheduleApplyMapper.class,
                DataPullScheduleScanMapper.class,
                DataPullScheduleTaskBatchMapper.class,
                DataPullTaskCreationMapper.class,
                DataPullScopeAdmissionMapper.class,
                DataPullScopeBindingMapper.class,
                DataPullScopeProgressMapper.class,
                DataPullReportArtifactMapper.class,
                DataPullReportArtifactChunkMapper.class,
                ReportStageMapper.class,
                Dp08MemberSetMapper.class
        );

        for (Class<?> mapper : mappers) {
            for (Method method : mapper.getDeclaredMethods()) {
                Insert insert = method.getAnnotation(Insert.class);
                if (insert == null) continue;
                String sql = String.join(" ", insert.value());
                assertThat(UNQUALIFIED_DUPLICATE_NO_OP.matcher(sql).find())
                        .as(mapper.getSimpleName() + "." + method.getName())
                        .isFalse();
            }
        }
    }

    private static String statement(Class<?> mapper, String method) {
        return mapper.getName() + "." + method;
    }
}
