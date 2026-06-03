package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductLifecycleClassifierFormulaParametersSqlContractTest {

    @Test
    void appendsFormulaParametersIndependentlyAndOnlyUpdatesDefaultSummary() throws IOException {
        Path migration = Path.of(
                "src/main/resources/db/init/073_product_lifecycle_classifier_formula_parameters.sql"
        );

        assertTrue(Files.exists(migration), "Missing product lifecycle formula parameter migration.");

        String sql = Files.readString(migration);
        List<String> itemNames = List.of(
                "爆发惯性系数",
                "稳健系数",
                "阶梯增长倍数",
                "波动去极值比例",
                "波动增长动量阈值",
                "衰退比例阈值",
                "成熟期上升短期权重",
                "成熟期下滑短期权重"
        );

        assertFalse(sql.contains("ELSE JSON_ARRAY()"));
        assertFalse(sql.contains("条生命周期配置"));
        assertTrue(sql.contains("`summary` = CONCAT(JSON_LENGTH(`content_json`), ' 条 DEFAULT_V1 配置')"));
        assertTrue(sql.contains("'defaultValue', '2'"));
        assertFalse(sql.contains("'defaultValue', '2.0'"));

        for (String itemName : itemNames) {
            String updateBlock = updateBlockForItem(sql, itemName);
            assertTrue(updateBlock.contains("JSON_ARRAY_APPEND("));
            assertTrue(updateBlock.contains("AND `content_json` IS NOT NULL"));
            assertTrue(updateBlock.contains("AND JSON_VALID(`content_json`)"));
            assertTrue(updateBlock.contains(
                    "JSON_SEARCH(`content_json`, 'one', '" + itemName + "', NULL, '$[*].itemName') IS NULL"
            ));
        }

        String itemCountBlock = updateBlockContaining(sql, "`item_count` = JSON_LENGTH(`content_json`)");
        assertTrue(itemCountBlock.contains("WHERE `config_type` = 'PRODUCT_LIFECYCLE'"));
        assertTrue(itemCountBlock.contains("AND `content_json` IS NOT NULL"));
        assertTrue(itemCountBlock.contains("AND JSON_VALID(`content_json`)"));
        assertFalse(itemCountBlock.contains("`summary`"));

        String summaryBlock = updateBlockContaining(sql, "条 DEFAULT_V1 配置");
        assertFalse(summaryBlock.contains("`item_count`"));
        assertTrue(summaryBlock.contains("`summary` = CONCAT(JSON_LENGTH(`content_json`), ' 条 DEFAULT_V1 配置')"));
        assertTrue(summaryBlock.contains("WHERE `config_type` = 'PRODUCT_LIFECYCLE'"));
        assertTrue(summaryBlock.contains("AND `version_no` = 'DEFAULT_LIFECYCLE_CONFIG'"));
        assertTrue(summaryBlock.contains("AND `content_json` IS NOT NULL"));
        assertTrue(summaryBlock.contains("AND JSON_VALID(`content_json`)"));
    }

    private String updateBlockContaining(String sql, String token) {
        int tokenIndex = sql.indexOf(token);
        assertTrue(tokenIndex >= 0, "Missing SQL token: " + token);
        int start = sql.lastIndexOf("UPDATE `operation_config_typed_version`", tokenIndex);
        int end = sql.indexOf(";", tokenIndex);
        assertTrue(start >= 0 && end > start, "Missing update block containing " + token);
        return sql.substring(start, end + 1);
    }

    private String updateBlockForItem(String sql, String itemName) {
        int itemIndex = sql.indexOf("'itemName', '" + itemName + "'");
        assertTrue(itemIndex >= 0, "Missing append for " + itemName);
        int start = sql.lastIndexOf("UPDATE `operation_config_typed_version`", itemIndex);
        int end = sql.indexOf(";", itemIndex);
        assertTrue(start >= 0 && end > start, "Missing update block for " + itemName);
        return sql.substring(start, end + 1);
    }
}
