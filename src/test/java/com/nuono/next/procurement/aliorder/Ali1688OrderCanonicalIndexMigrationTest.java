package com.nuono.next.procurement.aliorder;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Ali1688OrderCanonicalIndexMigrationTest {
    @Test
    void releaseMigrationRegistersCanonicalProviderOrderLookupIndex() throws Exception {
        String migration = read("src/main/resources/db/init/254_procurement_ali1688_order_canonical_index.sql");
        String postcheck = read("src/main/resources/db/postcheck/254_procurement_ali1688_order_canonical_index.sql");
        String catalog = read("src/main/resources/db/init/release-migrations.tsv");

        assertInitScriptsInclude(
                "classpath:db/init/254_procurement_ali1688_order_canonical_index.sql");
        assertThat(migration)
                .contains("add column `superseded_by_order_id` bigint default null")
                .contains("create table if not exists `procurement_ali1688_order_dedup_audit`")
                .contains("`original_gmt_updated` datetime default null")
                .contains("idx_proc_ali1688_order_canonical")
                .contains("(`owner_user_id`, `provider_order_no`, `superseded_by_order_id`, `is_deleted`, `authorization_id`, `gmt_updated`, `id`)")
                .contains("algorithm=inplace, lock=none");
        assertThat(postcheck)
                .contains("owner_user_id,provider_order_no,superseded_by_order_id,is_deleted,authorization_id,gmt_updated,id")
                .contains("procurement_ali1688_order_dedup_audit")
                .contains("correction_code,entity_type,entity_id")
                .contains("superseded_by_order_id");
        assertThat(catalog).contains(String.join("\t",
                "254",
                "254_procurement_ali1688_order_canonical_index.sql",
                "auto_additive",
                "db/init/254_procurement_ali1688_order_canonical_index.sql",
                "db/postcheck/254_procurement_ali1688_order_canonical_index.sql",
                "db/postcheck/254_procurement_ali1688_order_canonical_index.sql"));
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path)).toLowerCase();
    }
}
