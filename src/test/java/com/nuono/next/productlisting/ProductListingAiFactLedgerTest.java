package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
class ProductListingAiFactLedgerTest {

    @Test
    void shouldReconcileChineseOnlySourceAgainstBothGeneratedTitles() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleCn("蓝色金属桌面收纳盒");
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(
                        fact("F001", "PRODUCT_IDENTITY", "桌面收纳盒", "desk organizer", "منظم مكتب"),
                        fact("F002", "COLOUR", "蓝色", "blue", "أزرق"),
                        fact("F003", "MATERIAL", "金属", "metal", "معدني")
                )
        ));
        assertTrue(ledger.validateSource(draft).isEmpty());
        assertTrue(ledger.validateOutput(Map.of(
                "productTitleEn", "Blue Metal Desk Organizer for Daily Accessories",
                "productTitleAr", "منظم مكتب معدني أزرق للإكسسوارات اليومية"
        )).isEmpty());
    }
    @Test
    void shouldBlockFactThatCannotBeTracedBackToOriginalInput() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("Wooden Desk Organizer");
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(Map.of(
                        "factId", "F001",
                        "factType", "PRODUCT_IDENTITY",
                        "sourceField", "titleEn",
                        "sourceText", "Waterproof Storage Box",
                        "englishCanonical", "waterproof storage box",
                        "arabicCanonical", "صندوق تخزين مقاوم للماء",
                        "titleRequired", true
                ))
        ));
        assertTrue(ledger.validateSource(draft).stream().anyMatch(item -> item.contains("无法回指")));
    }
    @Test
    void shouldBlockGeneratedTitleThatOmitsAnyRequiredFact() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("Blue Metal Desk Organizer");
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(
                        Map.of(
                                "factId", "F001",
                                "factType", "PRODUCT_IDENTITY",
                                "sourceField", "titleEn",
                                "sourceText", "Desk Organizer",
                                "englishCanonical", "desk organizer",
                                "arabicCanonical", "منظم مكتب",
                                "titleRequired", true
                        ),
                        Map.of(
                                "factId", "F002",
                                "factType", "COLOUR",
                                "sourceField", "titleEn",
                                "sourceText", "Blue",
                                "englishCanonical", "blue",
                                "arabicCanonical", "أزرق",
                                "titleRequired", true
                        )
                )
        ));
        assertTrue(ledger.validateSource(draft).isEmpty());
        assertTrue(ledger.validateOutput(Map.of(
                "productTitleEn", "Practical Desk Organizer for Daily Accessories",
                "productTitleAr", "منظم مكتب عملي للإكسسوارات اليومية"
        )).stream().anyMatch(item -> item.contains("F002")));
    }
    @Test
    void shouldTraceFactsReturnedFromKeyAttributesInputField() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("Vintage Scrapbooking Paper Set");
        draft.setKeyAttributes(List.of(
                Map.of("code", "country_of_origin", "commonValue", "China"),
                Map.of("code", "mpn", "enValue", "PAPERSAYS440")
        ));
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(
                        Map.of(
                                "factId", "F010",
                                "factType", "OTHER",
                                "sourceField", "keyAttributes",
                                "sourceText", "china",
                                "englishCanonical", "China",
                                "arabicCanonical", "الصين",
                                "titleRequired", false
                        ),
                        Map.of(
                                "factId", "F011",
                                "factType", "MODEL",
                                "sourceField", "keyAttributes",
                                "sourceText", "PAPERSAYS440",
                                "englishCanonical", "PAPERSAYS440",
                                "arabicCanonical", "PAPERSAYS440",
                                "titleRequired", false
                        ),
                        Map.of(
                                "factId", "F001",
                                "factType", "PRODUCT_IDENTITY",
                                "sourceField", "titleEn",
                                "sourceText", "Scrapbooking Paper Set",
                                "englishCanonical", "scrapbooking paper set",
                                "arabicCanonical", "مجموعة ورق سكرابوكينغ",
                                "titleRequired", true
                        )
                )
        ));
        assertTrue(ledger.validateSource(draft).isEmpty());
    }
    @Test
    void shouldTraceSelectedAttributeJsonFragmentsButRejectUnselectedOptionFragments() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("Galaxy Projector Bedroom Night Light");
        draft.setKeyAttributes(List.of(
                Map.of("code", "colour_family", "commonValue", "black"),
                Map.of("code", "country_of_origin", "commonValue", "china"),
                Map.of("code", "lighting_technology", "options", List.of(
                        Map.of("value", "led", "en", "LED", "ar", "LED")
                )),
                Map.of("code", "brand", "commonValue", "milkyway"),
                Map.of("code", "grade", "commonValue", "new"),
                Map.of("code", "mpn", "enValue", "MILKYWAYA04")
        ));
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(
                        attributeFact("F16", "\"commonValue\":\"black\""),
                        attributeFact("F17", "\"commonValue\":\"china\""),
                        attributeFact("F18", "\"value\":\"led\",\"en\":\"LED\",\"ar\":\"LED\""),
                        attributeFact("F19", "\"commonValue\":\"milkyway\""),
                        attributeFact("F20", "\"commonValue\":\"new\""),
                        attributeFact("F21", "\"enValue\":\"MILKYWAYA04\""),
                        Map.of(
                                "factId", "F01",
                                "factType", "PRODUCT_IDENTITY",
                                "sourceField", "titleEn",
                                "sourceText", "Galaxy Projector",
                                "englishCanonical", "galaxy projector",
                                "arabicCanonical", "جهاز عرض المجرة",
                                "titleRequired", true
                        )
                )
        ));
        List<String> issues = ledger.validateSource(draft);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("F18"));
    }
    @Test
    void shouldAllowRequiredCanonicalWordsToBeNaturallySeparatedInTheTitle() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("LED Night Light");
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(Map.of(
                        "factId", "F006",
                        "factType", "PRODUCT_IDENTITY",
                        "sourceField", "titleEn",
                        "sourceText", "LED Night Light",
                        "englishCanonical", "LED light",
                        "arabicCanonical", "مصباح LED",
                        "titleRequired", true
                ))
        ));
        assertTrue(ledger.validateOutput(Map.of(
                "productTitleEn", "Galaxy LED Projector with Bedroom Night Light",
                "productTitleAr", "جهاز عرض للمجرة مع مصباح ليلي LED"
        )).isEmpty());
    }
    @Test
    void shouldDropUntraceableOptionalFactsButKeepRequiredFactsFailClosed() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleAr("جهاز عرض المجرة للسقف");
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(
                        Map.of(
                                "factId", "F001",
                                "factType", "PRODUCT_IDENTITY",
                                "sourceField", "titleAr",
                                "sourceText", "جهاز عرض المجرة",
                                "englishCanonical", "galaxy projector",
                                "arabicCanonical", "جهاز عرض المجرة",
                                "titleRequired", true
                        ),
                        Map.of(
                                "factId", "F014",
                                "factType", "OTHER",
                                "sourceField", "titleAr",
                                "sourceText", "على السقف",
                                "englishCanonical", "ceiling use",
                                "arabicCanonical", "على السقف",
                                "titleRequired", false
                        ),
                        Map.of(
                                "factId", "F015",
                                "factType", "COLOUR",
                                "sourceField", "titleAr",
                                "sourceText", "أبيض",
                                "englishCanonical", "white",
                                "arabicCanonical", "أبيض",
                                "titleRequired", true
                        )
                )
        ));
        ProductListingAiFactLedger sanitized = ledger.withoutUntraceableOptionalFacts(draft);
        assertEquals(2, sanitized.promptFacts().size());
        assertEquals("F001", sanitized.promptFacts().get(0).get("factId"));
        assertEquals("F015", sanitized.promptFacts().get(1).get("factId"));
        assertTrue(sanitized.validateSource(draft).stream().anyMatch(item -> item.contains("F015")));
        assertTrue(sanitized.validateSource(draft).stream().noneMatch(item -> item.contains("F014")));
    }
    @Test
    void shouldMatchArabicCanonicalFactsAcrossConjunctionAndDefiniteArticleForms() {
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(
                        Map.of(
                                "factId", "F009",
                                "factType", "DESIGN",
                                "sourceField", "titleAr",
                                "sourceText", "نظام شمسي",
                                "englishCanonical", "solar system",
                                "arabicCanonical", "النظام الشمسي",
                                "titleRequired", true
                        ),
                        Map.of(
                                "factId", "F010",
                                "factType", "PRODUCT_IDENTITY",
                                "sourceField", "titleAr",
                                "sourceText", "ضوء ليلي",
                                "englishCanonical", "night light",
                                "arabicCanonical", "ضوء ليلي",
                                "titleRequired", true
                        ),
                        Map.of(
                                "factId", "F011",
                                "factType", "OTHER",
                                "sourceField", "titleEn",
                                "sourceText", "Large Projection Area",
                                "englishCanonical", "large projection area",
                                "arabicCanonical", "مساحة عرض كبيرة",
                                "titleRequired", true
                        )
                )
        ));
        assertTrue(ledger.validateOutput(Map.of(
                "productTitleEn", "Galaxy Projector with Large Area, Solar System Discs and Night Light",
                "productTitleAr", "جهاز عرض المجرة بمساحة عرض كبيرة ومصباح ليلي ونجوم واقعية ونظام شمسي"
        )).isEmpty());
    }
    @Test
    void shouldNotRequireVagueBasicStyleLabelInGeneratedTitles() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleCn("基础款透明手机壳");
        ProductListingAiFactLedger ledger = ProductListingAiFactLedger.from(Map.of(
                "facts", List.of(
                        fact("F001", "PRODUCT_IDENTITY", "手机壳", "phone case", "غطاء هاتف"),
                        fact("F002", "STYLE", "基础款", "basic style", "تصميم أساسي")
                )
        ));
        assertTrue(ledger.validateSource(draft).isEmpty());
        assertTrue(ledger.validateOutput(Map.of(
                "productTitleEn", "Transparent Protective Phone Case for Everyday Use",
                "productTitleAr", "غطاء هاتف شفاف للحماية والاستخدام اليومي"
        )).isEmpty());
        assertEquals(false, ledger.promptFacts().get(1).get("titleRequired"));
    }
    private Map<String, Object> fact(
            String id,
            String type,
            String sourceText,
            String english,
            String arabic
    ) {
        return Map.of(
                "factId", id,
                "factType", type,
                "sourceField", "titleCn",
                "sourceText", sourceText,
                "englishCanonical", english,
                "arabicCanonical", arabic,
                "titleRequired", true
        );
    }
    private Map<String, Object> attributeFact(String id, String sourceText) {
        return Map.of(
                "factId", id,
                "factType", "OTHER",
                "sourceField", "keyAttributes",
                "sourceText", sourceText,
                "englishCanonical", "attribute",
                "arabicCanonical", "سمة",
                "titleRequired", false
        );
    }
}
