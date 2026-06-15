package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessCapability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CompetitorAnalysisPermissionSeedContractTest {
    private static final Path PERMISSION_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "100_operations_competitor_analysis_menu_permission.sql"
    );

    @Test
    void competitorAnalysisMenuUsesDedicatedOperationsIdsAndBusinessRoleGrants() throws IOException {
        assertTrue(Files.exists(PERMISSION_PATH), "competitor analysis permission migration must exist");
        String sql = Files.readString(PERMISSION_PATH);

        assertTrue(sql.contains("9800"), "seed must define the operations parent menu");
        assertTrue(sql.contains("9801"), "seed must define the competitor analysis menu");
        assertTrue(sql.contains("运营"));
        assertTrue(sql.contains("竞品分析"));
        assertTrue(sql.contains("/operations"));
        assertTrue(sql.contains("/operations/competitor-analysis"));
        assertFalse(sql.contains("9701"), "competitor analysis must not reuse system reports store-data menu id");
        assertFalse(roleGrant(1, 9801).matcher(sql).find(), "system admin must not receive store-level competitor access by default");
        assertTrue(roleGrant(2, 9801).matcher(sql).find(), "boss role must receive competitor analysis access");
        assertTrue(roleGrant(3, 9801).matcher(sql).find(), "ops manager role must receive competitor analysis access");
        assertTrue(roleGrant(4, 9801).matcher(sql).find(), "operator role must receive competitor analysis access");
    }

    @Test
    void businessCapabilityRecognizesCompetitorAnalysisPaths() {
        assertTrue(BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS.getMenuPathPrefixes()
                .contains("/operations/competitor-analysis"));
        assertTrue(BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS.getMenuPathPrefixes()
                .contains("/api/competitor-analysis"));
    }

    @Test
    void localDbBootstrapStatusListsCompetitorAnalysisScripts() throws IOException {
        String source = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "com",
                "nuono",
                "next",
                "system",
                "LocalDbBootstrapStatusService.java"
        ));

        assertTrue(source.contains("classpath:db/init/099_operations_competitor_analysis.sql"));
        assertTrue(source.contains("classpath:db/init/100_operations_competitor_analysis_menu_permission.sql"));
    }

    private static Pattern roleGrant(int roleId, int menuId) {
        return Pattern.compile(
                "(\\(\\s*" + roleId + "\\s*,\\s*" + menuId + "\\s*\\))"
                        + "|(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*" + menuId + "\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*" + menuId + ")",
                Pattern.CASE_INSENSITIVE
        );
    }
}
