package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OfficialWarehouseBatchSummaryMapperSqlTest {

    @Test
    void rawBatchSummaryIsOwnerScopedReadOnlyAndUsesPhysicalShippedQuantity() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/infrastructure/mapper/"
                        + "OfficialWarehouseBatchSummaryMapper.java"
        ));

        assertThat(source).contains(
                "WHERE b.owner_user_id = #{ownerUserId}",
                "AND b.is_deleted = b'0'",
                "line.is_deleted = b'0'",
                "b.id IN",
                "GREATEST(COALESCE(line.shipped_quantity, 0), 0)"
        );
        assertThat(source).doesNotContain("@Insert", "@Update", "@Delete");
    }
}
