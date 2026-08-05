package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class Ali1688Dp10ScheduleSourceSqlContractTest {

    @Test
    void openApiAuthorizationsUseANativeKeyset() throws Exception {
        String source = sql(
                Ali1688Dp10RuntimeMapper.class,
                "listEffectiveOpenApiAuthorizationsAfter",
                Select.class
        );

        assertThat(source)
                .contains("LIMIT #{limit}")
                .doesNotContain("OFFSET");
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
