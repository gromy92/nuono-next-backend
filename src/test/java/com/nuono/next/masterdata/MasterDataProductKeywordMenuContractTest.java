package com.nuono.next.masterdata;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MasterDataProductKeywordMenuContractTest {

    @Test
    void roleAssignmentMenuWhitelistIncludesProductKeywordMenus() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/masterdata/LocalDbMasterDataService.java"
        ));

        assertTrue(source.contains("\"/operations\""), "角色分配菜单白名单必须包含运营父级菜单");
        assertTrue(source.contains("\"/operations/product-keywords\""), "角色分配菜单白名单必须包含关键词数据菜单路径");
        assertTrue(source.contains("\"运营\""), "角色分配菜单白名单必须包含运营父级菜单名称");
        assertTrue(source.contains("\"关键词数据\""), "角色分配菜单白名单必须包含关键词数据菜单名称");
    }
}
