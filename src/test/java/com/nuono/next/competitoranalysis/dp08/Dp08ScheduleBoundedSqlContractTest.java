package com.nuono.next.competitoranalysis.dp08;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.Dp08BoundedScheduleScopeMapper;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import com.nuono.next.infrastructure.mapper.Dp08ScheduleEvidenceMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class Dp08ScheduleBoundedSqlContractTest {

    @Test
    void scheduleSourcesUseNativeKeysetsAndEvidenceUsesOneSetQuery() throws Exception {
        String keywordMembers = sql(
                Dp08BoundedScheduleScopeMapper.class,
                "listKeywordMembersAfter",
                Select.class
        );
        String targetMembers = sql(
                Dp08BoundedScheduleScopeMapper.class,
                "listTargetMembersAfter",
                Select.class
        );
        for (String source : new String[]{keywordMembers, targetMembers}) {
            assertThat(source).contains("LIMIT #{limit}").doesNotContain("OFFSET");
        }

        String evidence = sql(
                Dp08ScheduleEvidenceMapper.class,
                "listEvidence",
                Select.class
        );
        assertThat(evidence)
                .contains("<foreach collection='requests'")
                .contains("UNION ALL")
                .contains("operations_competitor_rank_fact")
                .contains("operations_competitor_product_snapshot");

        String effectiveMembers = sql(
                Dp08MemberSetMapper.class,
                "listMemberItemsAfter",
                Select.class
        );
        String stagedMembers = sql(
                Dp08MemberSetMapper.class,
                "listStageItemsAfter",
                Select.class
        );
        assertThat(effectiveMembers).contains("LIMIT #{limit}").doesNotContain("OFFSET");
        assertThat(stagedMembers).contains("LIMIT #{limit}").doesNotContain("OFFSET");
    }

    private static String sql(
            Class<?> mapper,
            String methodName,
            Class<? extends Annotation> annotationType
    ) throws Exception {
        Method method = Arrays.stream(mapper.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        Method value = annotationType.getMethod("value");
        return String.join(" ", (String[]) value.invoke(annotation));
    }
}
