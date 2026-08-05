package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.SnapshotCurrentFactMapper;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class SnapshotCanonicalReadSqlTest {

    @Test
    void applyCanonicalizesOnceAndCurrentReadUsesOneMaterializedHead() {
        String apply = selectSql(SnapshotFactApplyMapper.class, "selectCanonicalChunk");
        assertThat(apply)
                .contains("validated_identity_candidate=b'1'")
                .contains("earlier.stable_identity=")
                .contains("valid_item.stable_identity=")
                .doesNotContain("BINARY earlier.stable_identity")
                .contains("page_no>")
                .contains("item_ordinal>")
                .contains("LIMIT #{limit}");

        String current = selectSql(SnapshotCurrentFactMapper.class, "selectCurrentChunk");
        assertThat(current)
                .contains("dp_pull_snapshot_effective_item")
                .contains("head.task_id=#{headTaskId}")
                .contains("item.stable_identity>COALESCE(#{afterStableIdentity},'')")
                .contains("ORDER BY item.stable_identity ASC")
                .contains("LIMIT #{limit}")
                .doesNotContain("WITH RECURSIVE")
                .doesNotContain("predecessor");
    }

    private String selectSql(Class<?> mapper, String methodName) {
        Method method = Arrays.stream(mapper.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Select annotation = method.getAnnotation(Select.class);
        assertThat(annotation).isNotNull();
        String raw = String.join("\n", annotation.value());
        new XMLLanguageDriver().createSqlSource(new Configuration(), raw, Object.class);
        return raw.replaceAll("\\s+", " ");
    }
}
