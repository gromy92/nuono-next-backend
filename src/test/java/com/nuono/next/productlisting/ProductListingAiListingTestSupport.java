package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;


abstract class ProductListingAiListingTestSupport {


    @Mock
    protected ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;

    @Mock
    protected AiCapabilityService aiCapabilityService;

    protected BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90002L)
                .build();
    }

    protected ProductListingAiListingCommand commandWithDraft() {
        ProductListingAiListingCommand command = new ProductListingAiListingCommand();
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setStoreCode("STR245027-NAE");
        draft.setProductTitleCn("桌面收纳盒");
        draft.setProductFullType("Home Organization");
        command.setDraft(draft);
        return command;
    }

    protected Map<String, Object> successPayload() {
        return Map.ofEntries(
                Map.entry("inputCompleteness", Map.of(
                        "summary", "facts usable",
                        "missingCritical", List.of(),
                        "missingOptional", List.of("dimensions")
                )),
                Map.entry("productUnderstanding", Map.of(
                        "productType", "desk organizer",
                        "buyerUseCases", List.of("home desk"),
                        "confirmedFacts", List.of("organizer")
                )),
                Map.entry("styleDecision", Map.of("style", "practical", "rationale", "utility item")),
                Map.entry("keywords", Map.of("english", List.of("desk organizer"), "arabic", List.of("منظم مكتب"))),
                Map.entry("attributeGuardrails", Map.of(
                        "confirmedAttributes", List.of("organizer"),
                        "usableSellingPoints", List.of("tidy desk"),
                        "forbiddenClaims", List.of("premium material")
                )),
                Map.entry("listingStrategy", Map.of("english", "lead with organizer use", "arabic", "localized home context")),
                Map.entry("englishListing", Map.of(
                        "title", validEnglishTitle(),
                        "bullets", validEnglishHighlights(),
                        "longDescription", validEnglishDescription()
                )),
                Map.entry("arabicListing", Map.of(
                        "title", validArabicTitle(),
                        "bullets", validArabicHighlights(),
                        "longDescription", validArabicDescription()
                )),
                Map.entry("qualityCheck", Map.of(
                        "score", 86,
                        "findings", List.of("需确认尺寸"),
                        "uploadNotes", List.of("去除 review-only 标记"),
                        "removeMarkdownBeforeUpload", true
                )),
                Map.entry("warnings", List.of("missing dimensions")),
                Map.entry("needsHumanConfirmation", List.of()),
                Map.entry("noonUploadDraft", Map.of(
                        "productTitleEn", validEnglishTitle(),
                        "productTitleAr", validArabicTitle(),
                        "productHighlightsEn", validEnglishHighlights(),
                        "productHighlightsAr", validArabicHighlights(),
                        "productDescriptionEn", validEnglishDescription(),
                        "productDescriptionAr", validArabicDescription()
                ))
        );
    }

    protected void stubAi(Map<String, Object> factPayload, AiStructuredTextResult listingResult) {
        stubAi(factPayload, listingResult, listingResult);
    }

    protected void stubAi(
            Map<String, Object> factPayload,
            AiStructuredTextResult listingResult,
            AiStructuredTextResult repairResult
    ) {
        AiStructuredTextResult factResult = AiStructuredTextResult.success();
        factResult.setParsedJson(factPayload);
        when(aiCapabilityService.createStructuredText(org.mockito.ArgumentMatchers.any()))
                .thenReturn(factResult, listingResult, repairResult);
    }

    protected Map<String, Object> defaultFactPayload() {
        return Map.of(
                "facts", List.of(Map.of(
                        "factId", "F001",
                        "factType", "PRODUCT_IDENTITY",
                        "sourceField", "titleCn",
                        "sourceText", "桌面收纳盒",
                        "englishCanonical", "desk organizer",
                        "arabicCanonical", "منظم مكتب",
                        "titleRequired", true
                )),
                "warnings", List.of()
        );
    }

    protected Map<String, Object> scrapbookingFactPayload() {
        return Map.of(
                "facts", List.of(
                        Map.of(
                                "factId", "F001",
                                "factType", "PRODUCT_IDENTITY",
                                "sourceField", "titleEn",
                                "sourceText", "Scrapbooking Paper",
                                "englishCanonical", "scrapbooking paper",
                                "arabicCanonical", "ورق سكرابوكينغ",
                                "titleRequired", true
                        ),
                        Map.of(
                                "factId", "F002",
                                "factType", "QUANTITY",
                                "sourceField", "titleEn",
                                "sourceText", "30",
                                "englishCanonical", "30 pieces",
                                "arabicCanonical", "30 قطعة",
                                "titleRequired", true
                        )
                ),
                "warnings", List.of()
        );
    }

    protected Map<String, Object> unsafePayload() {
        Map<String, Object> payload = new LinkedHashMap<>(successPayload());
        payload.put("noonUploadDraft", Map.of(
                "productTitleEn", "SHORT",
                "productTitleAr", "قصير",
                "productHighlightsEn", List.of("【TIDY】 - Buy now."),
                "productHighlightsAr", List.of("【تنظيم】 - اشتر الآن."),
                "productDescriptionEn", "Product details are not confirmed and should be checked before final upload",
                "productDescriptionAr", "وصف قصير"
        ));
        return payload;
    }

    protected String validEnglishTitle() {
        return "Practical desk organizer for remote controls and daily accessories";
    }

    protected String validArabicTitle() {
        return "منظم مكتب عملي لأجهزة التحكم والإكسسوارات اليومية";
    }

    protected List<String> validEnglishHighlights() {
        return List.of(
                "Organizes remote controls and small daily accessories",
                "Compact form helps keep desks and side tables orderly",
                "Open layout keeps stored items easy to reach"
        );
    }

    protected List<String> validArabicHighlights() {
        return List.of(
                "ينظم أجهزة التحكم والإكسسوارات اليومية الصغيرة",
                "تصميم مدمج يساعد على ترتيب المكتب والطاولات الجانبية",
                "التصميم المفتوح يجعل الأغراض المخزنة سهلة الوصول"
        );
    }

    protected String validEnglishDescription() {
        return ("This practical desk organizer keeps remote controls and small daily accessories together in one convenient place. "
                + "Its compact form suits desks and side tables while helping the surrounding area stay orderly. "
                + "The open layout keeps stored items visible and easy to reach with other mailers and shipping supplies. ").repeat(2);
    }

    protected String validArabicDescription() {
        return ("يساعد منظم المكتب العملي على جمع أجهزة التحكم والإكسسوارات اليومية الصغيرة في مكان واحد مناسب. "
                + "يلائم التصميم المدمج المكاتب والطاولات الجانبية ويساعد على بقاء المساحة المحيطة مرتبة. "
                + "يجعل التصميم المفتوح الأغراض المخزنة واضحة وسهلة الوصول أثناء الاستخدام اليومي. ").repeat(2);
    }
}
