package com.nuono.next.datapull.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class DataPullScopeBindingMapperSqlTest {
    @Test
    void cohortMutationIsSerializedByTheActiveCutoverRow() {
        String lock = sql("lockActiveOperation", Select.class);
        String open = sql("lockOpenBindings", Select.class);

        assertThat(lock).contains("state = 'ACTIVE'", "FOR UPDATE");
        assertThat(open).contains("effective_until_utc IS NULL", "FOR UPDATE");
    }

    @Test
    void insertedPayloadIsNeverUpdatedAndCloseUsesDigestCas() {
        String insert = sql("insertOpenBinding", Insert.class);
        String close = sql("closeBinding", Update.class);

        assertThat(insert).contains("payload_sha256", "payload", "effective_from_utc");
        assertThat(insert).contains(
                "open_scope_slot", "CONCAT(#{operationCode}, ':', #{scopeKey})"
        );
        assertThat(insert).contains("ON DUPLICATE KEY UPDATE binding_id = binding_id");
        assertThat(close).contains("payload_sha256 = BINARY #{payloadSha256}");
        assertThat(close).contains("effective_until_utc IS NULL");
        assertThat(close).contains("open_scope_slot = NULL");
    }

    private static String sql(String method, Class<? extends Annotation> annotation) {
        Method target = Arrays.stream(DataPullScopeBindingMapper.class.getDeclaredMethods())
                .filter((candidate) -> candidate.getName().equals(method))
                .findFirst().orElseThrow();
        String[] value;
        if (annotation == Select.class) {
            value = target.getAnnotation(Select.class).value();
        } else if (annotation == Insert.class) {
            value = target.getAnnotation(Insert.class).value();
        } else {
            value = target.getAnnotation(Update.class).value();
        }
        return String.join(" ", value).replaceAll("\\s+", " ");
    }
}
