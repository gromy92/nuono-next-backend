package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProductDisplayNameSupportTest {

    @Test
    void localChineseNameUsesConfirmedCacheBeforeDraftAndBaseline() {
        String title = ProductDisplayNameSupport.localChineseName(
                "  星耀琥珀香薰炉  ",
                "草稿中文名",
                "草稿旧中文名",
                "基线中文名",
                "基线旧中文名"
        );

        assertEquals("星耀琥珀香薰炉", title);
    }

    @Test
    void localChineseNameIgnoresBlankValuesAndFallsBackThroughDraftThenBaseline() {
        String title = ProductDisplayNameSupport.localChineseName(
                " ",
                null,
                " 草稿旧中文名 ",
                "基线中文名",
                "基线旧中文名"
        );

        assertEquals("草稿旧中文名", title);
    }

    @Test
    void displayTitleFallsBackToEnglishThenProductKey() {
        assertEquals(
                "星耀琥珀香薰炉",
                ProductDisplayNameSupport.displayTitle("星耀琥珀香薰炉", "Amber Burner", "ZTEST001")
        );
        assertEquals(
                "Amber Burner",
                ProductDisplayNameSupport.displayTitle(" ", "Amber Burner", "ZTEST001")
        );
        assertEquals(
                "ZTEST001",
                ProductDisplayNameSupport.displayTitle(null, null, "ZTEST001")
        );
    }
}
