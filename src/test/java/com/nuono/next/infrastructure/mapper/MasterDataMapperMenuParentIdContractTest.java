package com.nuono.next.infrastructure.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MasterDataMapperMenuParentIdContractTest {

    @Test
    void menuQueriesCoerceLegacyStringParentIdsBeforeMappingToLong() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/infrastructure/mapper/MasterDataMapper.java"
        ));
        assertTrue(source.contains("WHEN m.parent_id REGEXP '^[0-9]+$' THEN CAST(m.parent_id AS UNSIGNED)"));
        assertTrue(source.contains("WHEN parent_menu.id IS NOT NULL THEN parent_menu.id"));
        assertTrue(source.contains("LOWER(TRIM(m.parent_id)) = LOWER(TRIM(BOTH '/' FROM parent_menu.url_path))"));
        assertTrue(source.contains("m.id ASC"));
        assertTrue(
                source.contains("CASE WHEN parent_id REGEXP '^[0-9]+$' THEN CAST(parent_id AS UNSIGNED) ELSE NULL END AS parent_id"),
                "selectMenuView must sanitize menu.parent_id before MyBatis maps it to Long"
        );
    }
}
