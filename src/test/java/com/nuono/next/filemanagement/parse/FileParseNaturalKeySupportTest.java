package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileParseNaturalKeySupportTest {

    @Test
    void shouldBuildStableCommissionKeyFromNormalizedPayloadWhenAiNaturalKeyVaries() {
        Map<String, Object> firstPayload = commissionPayload();
        firstPayload.put("platform", "Noon");
        firstPayload.put("fulfillmentType", null);
        firstPayload.put("commissionRate", "27%");

        Map<String, Object> secondPayload = commissionPayload();
        secondPayload.put("platform", null);
        secondPayload.put("fulfillmentType", "FBN");
        secondPayload.put("commissionRate", "28%");

        String firstKey = FileParseNaturalKeySupport.buildNaturalKey("commission_rule", firstPayload);
        String secondKey = FileParseNaturalKeySupport.buildNaturalKey("commission_rule", secondPayload);

        assertEquals(firstKey, secondKey);
        assertEquals(
                "KSA|Fashion > Apparel, Footwear|全部|ALL|SAR|2025-09-01",
                firstKey
        );
        assertEquals(
                FileParseNaturalKeySupport.naturalKeyHash("commission_rule", firstKey),
                FileParseNaturalKeySupport.naturalKeyHash("commission_rule", secondKey)
        );
    }

    @Test
    void shouldKeepAmountTierInCommissionKeyButNotRate() {
        Map<String, Object> basePayload = commissionPayload();
        basePayload.put("amountRangeLabel", "<= 5000 SAR");
        basePayload.put("amountMax", 5000);
        basePayload.put("amountMaxInclusive", true);
        basePayload.put("commissionRate", "15%");

        Map<String, Object> changedRatePayload = new LinkedHashMap<>(basePayload);
        changedRatePayload.put("commissionRate", "12%");

        Map<String, Object> changedTierPayload = new LinkedHashMap<>(basePayload);
        changedTierPayload.put("amountMax", 6000);

        String baseKey = FileParseNaturalKeySupport.buildNaturalKey("commission_rule", basePayload);

        assertEquals(
                baseKey,
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", changedRatePayload)
        );
        assertNotEquals(
                baseKey,
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", changedTierPayload)
        );
        assertTrue(baseKey.contains("MAX:5000:1"));
    }

    @Test
    void shouldKeepBrandRestrictionInCommissionKeyButNotRate() {
        Map<String, Object> genericPayload = commissionPayload();
        genericPayload.put("categoryName", "Colour Cosmetics");
        genericPayload.put("brandRestriction", "Generic brand");
        genericPayload.put("commissionRate", "15%");

        Map<String, Object> otherBrandsPayload = new LinkedHashMap<>(genericPayload);
        otherBrandsPayload.put("brandRestriction", "All other brands");
        otherBrandsPayload.put("commissionRate", "10%");

        Map<String, Object> changedGenericRatePayload = new LinkedHashMap<>(genericPayload);
        changedGenericRatePayload.put("commissionRate", "12%");

        String genericKey = FileParseNaturalKeySupport.buildNaturalKey("commission_rule", genericPayload);

        assertNotEquals(
                genericKey,
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", otherBrandsPayload)
        );
        assertEquals(
                genericKey,
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", changedGenericRatePayload)
        );
        assertTrue(genericKey.contains("Generic brand"));
    }

    @Test
    void shouldKeepParentCategoryInCommissionKey() {
        Map<String, Object> mobileAccessoriesPayload = commissionPayload();
        mobileAccessoriesPayload.put("parentCategoryName", "Mobiles");
        mobileAccessoriesPayload.put("categoryName", "Accessories");
        mobileAccessoriesPayload.put("categoryPath", "Mobiles > Accessories");
        mobileAccessoriesPayload.put("commissionRate", "20%");

        Map<String, Object> videoAccessoriesPayload = new LinkedHashMap<>(mobileAccessoriesPayload);
        videoAccessoriesPayload.put("parentCategoryName", "Video Games");
        videoAccessoriesPayload.put("categoryName", "Other Accessories");
        videoAccessoriesPayload.put("categoryPath", "Video Games > Other Accessories");
        videoAccessoriesPayload.put("commissionRate", "15%");

        String mobileKey = FileParseNaturalKeySupport.buildNaturalKey("commission_rule", mobileAccessoriesPayload);

        assertEquals("KSA|Mobiles > Accessories|全部|ALL|SAR|2025-09-01", mobileKey);
        assertNotEquals(
                mobileKey,
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", videoAccessoriesPayload)
        );
    }

    @Test
    void shouldNormalizeCommissionFieldsFromSourceRow() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "UAE");
        payload.put("categoryName", "Watches");
        payload.put("amountRangeLabel", "> 5000 AED");
        payload.put("amountCurrency", "AED");
        payload.put("commissionRate", "6%");
        payload.put("fulfillmentType", "FBN");
        String sourceText = "[[SOURCE_ROW_ID=91001;TYPE=pdf_ocr_line;LOC=page=1;line=6]]\n"
                + "Fashion > Watches | > 5000 AED | AED | 6% | 2026-05-01";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(91001L)
        );

        assertEquals("Fashion > Watches", normalized.get("categoryName"));
        assertEquals("> 5000 AED", normalized.get("amountRangeLabel"));
        assertEquals("AED", normalized.get("amountCurrency"));
        assertEquals("6%", normalized.get("commissionRate"));
        assertEquals("2026-05-01", normalized.get("effectiveDate"));
        assertEquals("全部", normalized.get("brandRestriction"));
        assertEquals("Fashion > Watches", normalized.get("categoryPath"));
        assertEquals("5000", normalized.get("amountMin"));
        assertEquals(false, normalized.get("amountMinInclusive"));
        assertNull(normalized.get("amountMax"));
        assertNull(normalized.get("fulfillmentType"));
        assertEquals(
                "UAE|Fashion > Watches|全部|MIN:5000:0|MAX:*:|CUR:AED|AED|2026-05-01",
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", normalized)
        );
    }

    @Test
    void shouldInferBrandRestrictionFromCommissionRateAndSourceRow() {
        Map<String, Object> genericPayload = commissionPayload();
        genericPayload.put("country", "KSA");
        genericPayload.put("categoryName", "Colour Cosmetics");
        genericPayload.put("commissionRate", "15%");

        Map<String, Object> otherBrandsPayload = new LinkedHashMap<>(genericPayload);
        otherBrandsPayload.put("commissionRate", "10%");

        String sourceText = "[[SOURCE_ROW_ID=92001;TYPE=pdf_ocr_line;LOC=page=1;line=8]]\n"
                + "Colour Cosmetics | All | - 15% for Generic brand - 10% for all other brands";

        Map<String, Object> genericNormalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                genericPayload,
                sourceText,
                List.of(92001L)
        );
        Map<String, Object> otherBrandsNormalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                otherBrandsPayload,
                sourceText,
                List.of(92001L)
        );

        assertEquals("Generic brand", genericNormalized.get("brandRestriction"));
        assertEquals("All other brands", otherBrandsNormalized.get("brandRestriction"));
        assertEquals("15%", genericNormalized.get("commissionRate"));
        assertEquals("10%", otherBrandsNormalized.get("commissionRate"));
        assertNotEquals(
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", genericNormalized),
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", otherBrandsNormalized)
        );
    }

    @Test
    void shouldInferParentCategoryFromSourceRowContext() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("categoryName", "Accessories");
        payload.put("commissionRate", "20%");
        String sourceText = "[[SOURCE_ROW_ID=93001;TYPE=pdf_text_line;LOC=page=3;line=105]]\n"
                + "Mobiles\n"
                + "[[SOURCE_ROW_ID=93002;TYPE=pdf_text_line;LOC=page=3;line=119]]\n"
                + "Accessories\n"
                + "[[SOURCE_ROW_ID=93003;TYPE=pdf_text_line;LOC=page=3;line=120]]\n"
                + "- 20% for items with sale price";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(93002L, 93003L)
        );

        assertEquals("Mobiles", normalized.get("parentCategoryName"));
        assertEquals("Mobiles > Accessories", normalized.get("categoryPath"));
        assertEquals(
                "KSA|Mobiles > Accessories|全部|ALL|SAR|2025-09-01",
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", normalized)
        );
    }

    @Test
    void shouldInferMissingCategoryFromPreviousReferralFeeContext() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("categoryName", null);
        payload.put("amountRangeLabel", "<= 30 SAR");
        payload.put("commissionRate", "20%");
        String sourceText = "[[SOURCE_ROW_ID=94001;TYPE=pdf_text_line;LOC=page=5;line=40]]\n"
                + "Other Categories\n"
                + "[[SOURCE_ROW_ID=94002;TYPE=pdf_text_line;LOC=page=5;line=41]]\n"
                + "Sports & Outdoors All\n"
                + "[[SOURCE_ROW_ID=94003;TYPE=pdf_text_line;LOC=page=5;line=42]]\n"
                + "- 20% for item with sales price\n"
                + "[[SOURCE_ROW_ID=94004;TYPE=pdf_text_line;LOC=page=5;line=43]]\n"
                + "of 30 SAR or less";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(94003L, 94004L)
        );

        assertEquals("Other Categories", normalized.get("parentCategoryName"));
        assertEquals("Sports & Outdoors", normalized.get("categoryName"));
        assertEquals("Other Categories > Sports & Outdoors", normalized.get("categoryPath"));
        assertEquals(
                "KSA|Other Categories > Sports & Outdoors|全部|MIN:*:|MAX:30:1|CUR:SAR|SAR|2025-09-01",
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", normalized)
        );
    }

    @Test
    void shouldCanonicalizeAllLeafCategoryFromSourceContext() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Watches");
        payload.put("categoryName", "All");
        payload.put("categoryPath", "Watches > All");
        payload.put("amountRangeLabel", "<= 5000 SAR");
        payload.put("amountMax", 5000);
        payload.put("amountMaxInclusive", true);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "15%");
        payload.put("effectiveDate", null);
        String sourceText = "[[SOURCE_ROW_ID=95001;TYPE=pdf_text_line;LOC=page=1;line=15]]\n"
                + "Fashion\n"
                + "[[SOURCE_ROW_ID=95002;TYPE=pdf_text_line;LOC=page=1;line=24]]\n"
                + "The below referral fees is applicable from 1st September 2025\n"
                + "[[SOURCE_ROW_ID=95003;TYPE=pdf_text_line;LOC=page=1;line=29]]\n"
                + "Watches All\n"
                + "[[SOURCE_ROW_ID=95004;TYPE=pdf_text_line;LOC=page=1;line=30]]\n"
                + "- 15% for the portion of the\n"
                + "[[SOURCE_ROW_ID=95005;TYPE=pdf_text_line;LOC=page=1;line=31]]\n"
                + "total sales price up to 5000";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(95003L, 95004L, 95005L)
        );

        assertEquals("Fashion", normalized.get("parentCategoryName"));
        assertEquals("Watches", normalized.get("categoryName"));
        assertEquals("Fashion > Watches", normalized.get("categoryPath"));
        assertEquals("2025-09-01", normalized.get("effectiveDate"));
        assertEquals(
                "KSA|Fashion > Watches|全部|MIN:*:|MAX:5000:1|CUR:SAR|SAR|2025-09-01",
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", normalized)
        );
    }

    @Test
    void shouldIgnorePrefaceRowsWhenSourceIdsContainExtraContext() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Watches");
        payload.put("categoryName", "All");
        payload.put("categoryPath", "Watches > All");
        payload.put("amountRangeLabel", "<= 5000 SAR");
        payload.put("amountMax", 5000);
        payload.put("amountMaxInclusive", true);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "15%");
        payload.put("effectiveDate", null);
        String sourceText = "[[SOURCE_ROW_ID=95101;TYPE=pdf_text_line;LOC=page=1;line=15]]\n"
                + "Fashion\n"
                + "[[SOURCE_ROW_ID=95102;TYPE=pdf_text_line;LOC=page=1;line=24]]\n"
                + "The below referral fees is applicable from 1st September 2025\n"
                + "[[SOURCE_ROW_ID=95103;TYPE=pdf_text_line;LOC=page=1;line=25]]\n"
                + "1. There are some PST and Brand Level exceptions that apply to the below mentioned fees.\n"
                + "[[SOURCE_ROW_ID=95104;TYPE=pdf_text_line;LOC=page=1;line=28]]\n"
                + "https://support.noon.partners/portal/en/kb/articles/fulfilled-by-noon-fbn-fees-in-ksa\n"
                + "[[SOURCE_ROW_ID=95105;TYPE=pdf_text_line;LOC=page=1;line=29]]\n"
                + "Watches All\n"
                + "[[SOURCE_ROW_ID=95106;TYPE=pdf_text_line;LOC=page=1;line=30]]\n"
                + "- 15% for the portion of the\n"
                + "[[SOURCE_ROW_ID=95107;TYPE=pdf_text_line;LOC=page=1;line=31]]\n"
                + "total sales price up to 5000";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(95102L, 95104L, 95105L, 95106L, 95107L)
        );

        assertEquals("Fashion", normalized.get("parentCategoryName"));
        assertEquals("Watches", normalized.get("categoryName"));
        assertEquals("Fashion > Watches", normalized.get("categoryPath"));
    }

    @Test
    void shouldPreferInlineCategoryRateOverEarlierContextRows() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Fashion");
        payload.put("categoryName", "Eyewear");
        payload.put("categoryPath", "Eyewear");
        payload.put("commissionRate", "15%");
        payload.put("effectiveDate", null);
        String sourceText = "[[SOURCE_ROW_ID=95201;TYPE=pdf_text_line;LOC=page=1;line=15]]\n"
                + "Fashion\n"
                + "[[SOURCE_ROW_ID=95202;TYPE=pdf_text_line;LOC=page=1;line=29]]\n"
                + "Watches All\n"
                + "[[SOURCE_ROW_ID=95203;TYPE=pdf_text_line;LOC=page=1;line=36]]\n"
                + "Eyewear All 15%";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(95202L, 95203L)
        );

        assertEquals("Fashion", normalized.get("parentCategoryName"));
        assertEquals("Eyewear", normalized.get("categoryName"));
        assertEquals("Fashion > Eyewear", normalized.get("categoryPath"));
    }

    @Test
    void shouldOverrideCategoryWhenEvidenceLinePointsToDifferentInlineCategory() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Jewelry");
        payload.put("categoryName", "Eyewear");
        payload.put("categoryPath", "Jewelry > Eyewear");
        payload.put("commissionRate", "5%");
        String sourceText = "[[SOURCE_ROW_ID=95301;TYPE=pdf_text_line;LOC=page=1;line=37]]\n"
                + "Jewelry\n"
                + "[[SOURCE_ROW_ID=95302;TYPE=pdf_text_line;LOC=page=1;line=38]]\n"
                + "Gold Bars & Coins 5%";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(95302L)
        );

        assertEquals("Jewelry", normalized.get("parentCategoryName"));
        assertEquals("Gold Bars & Coins", normalized.get("categoryName"));
        assertEquals("Jewelry > Gold Bars & Coins", normalized.get("categoryPath"));
    }

    @Test
    void shouldCanonicalizeSplitMultiLineCategoryFromSourceContext() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Wearables");
        payload.put("categoryName", "Smartwatches");
        payload.put("categoryPath", "Wearables > Smartwatches");
        payload.put("amountRangeLabel", "全部");
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "15%");
        payload.put("effectiveDate", null);
        String sourceText = "[[SOURCE_ROW_ID=96001;TYPE=pdf_text_line;LOC=page=4;line=160]]\n"
                + "Wearables\n"
                + "[[SOURCE_ROW_ID=96002;TYPE=pdf_text_line;LOC=page=4;line=168]]\n"
                + "Smartwatches, Fitness\n"
                + "[[SOURCE_ROW_ID=96003;TYPE=pdf_text_line;LOC=page=4;line=169]]\n"
                + "Bands, Smart Glasses,\n"
                + "[[SOURCE_ROW_ID=96004;TYPE=pdf_text_line;LOC=page=4;line=170]]\n"
                + "Smart Rings\n"
                + "[[SOURCE_ROW_ID=96005;TYPE=pdf_text_line;LOC=page=4;line=171]]\n"
                + "15%";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(96002L, 96003L, 96004L, 96005L)
        );

        assertEquals("Wearables", normalized.get("parentCategoryName"));
        assertEquals("Smartwatches, Fitness Bands, Smart Glasses, Smart Rings", normalized.get("categoryName"));
        assertEquals("Wearables > Smartwatches, Fitness Bands, Smart Glasses, Smart Rings", normalized.get("categoryPath"));
    }

    @Test
    void shouldCanonicalizeParentAndCategoryWhenAiUsesPreviousSection() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Wearables");
        payload.put("categoryName", "Camera");
        payload.put("categoryPath", "Camera");
        payload.put("amountRangeLabel", "<= 250 SAR");
        payload.put("amountMax", 250);
        payload.put("amountMaxInclusive", true);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "10%");
        payload.put("effectiveDate", null);
        String sourceText = "[[SOURCE_ROW_ID=97001;TYPE=pdf_text_line;LOC=page=4;line=173]]\n"
                + "Camera\n"
                + "[[SOURCE_ROW_ID=97002;TYPE=pdf_text_line;LOC=page=4;line=174]]\n"
                + "Cameras, Scopes, Lens,\n"
                + "[[SOURCE_ROW_ID=97003;TYPE=pdf_text_line;LOC=page=4;line=175]]\n"
                + "Studio Lights, Support\n"
                + "[[SOURCE_ROW_ID=97004;TYPE=pdf_text_line;LOC=page=4;line=176]]\n"
                + "Stabilizers, Blank Video\n"
                + "[[SOURCE_ROW_ID=97005;TYPE=pdf_text_line;LOC=page=4;line=177]]\n"
                + "Media\n"
                + "[[SOURCE_ROW_ID=97006;TYPE=pdf_text_line;LOC=page=4;line=178]]\n"
                + "- 10% for items with sale price";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(97001L, 97002L, 97003L, 97004L, 97005L, 97006L)
        );

        assertEquals("Camera", normalized.get("parentCategoryName"));
        assertEquals("Cameras, Scopes, Lens, Studio Lights, Support Stabilizers, Blank Video Media", normalized.get("categoryName"));
        assertEquals(
                "Camera > Cameras, Scopes, Lens, Studio Lights, Support Stabilizers, Blank Video Media",
                normalized.get("categoryPath")
        );
    }

    @Test
    void shouldIgnoreAmountDescriptionWhenInferringCategory() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Camera");
        payload.put("categoryName", "sales price greater than 250");
        payload.put("categoryPath", "Camera > sales price greater than 250");
        payload.put("amountRangeLabel", "> 250 SAR");
        payload.put("amountMin", 250);
        payload.put("amountMinInclusive", false);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "8%");
        String sourceText = "[[SOURCE_ROW_ID=98201;TYPE=pdf_text_line;LOC=page=4;line=173]]\n"
                + "Camera\n"
                + "[[SOURCE_ROW_ID=98202;TYPE=pdf_text_line;LOC=page=4;line=182]]\n"
                + "All Other Camera\n"
                + "[[SOURCE_ROW_ID=98203;TYPE=pdf_text_line;LOC=page=4;line=183]]\n"
                + "Accessories\n"
                + "[[SOURCE_ROW_ID=98204;TYPE=pdf_text_line;LOC=page=4;line=186]]\n"
                + "- 8% for any portion of the total\n"
                + "[[SOURCE_ROW_ID=98205;TYPE=pdf_text_line;LOC=page=4;line=187]]\n"
                + "sales price greater than 250\n"
                + "[[SOURCE_ROW_ID=98206;TYPE=pdf_text_line;LOC=page=4;line=188]]\n"
                + "SAR";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(98203L, 98205L, 98206L)
        );

        assertEquals("Camera", normalized.get("parentCategoryName"));
        assertEquals("All Other Camera Accessories", normalized.get("categoryName"));
        assertEquals("Camera > All Other Camera Accessories", normalized.get("categoryPath"));
    }

    @Test
    void shouldPreferMatchingRateWhenEvidenceContainsNeighborCategory() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Video Games");
        payload.put("categoryName", "Gift Cards");
        payload.put("categoryPath", "Video Games > Gift Cards");
        payload.put("amountRangeLabel", "全部");
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "14%");
        String sourceText = "[[SOURCE_ROW_ID=98301;TYPE=pdf_text_line;LOC=page=4;line=211]]\n"
                + "Video Games\n"
                + "[[SOURCE_ROW_ID=98302;TYPE=pdf_text_line;LOC=page=4;line=216]]\n"
                + "Video Games 14%\n"
                + "[[SOURCE_ROW_ID=98303;TYPE=pdf_text_line;LOC=page=4;line=217]]\n"
                + "Gift Cards 10%\n"
                + "[[SOURCE_ROW_ID=98304;TYPE=pdf_text_line;LOC=page=4;line=218]]\n"
                + "Other Accessories 15%";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(98301L, 98303L)
        );

        assertEquals("Video Games", normalized.get("categoryName"));
        assertEquals("Video Games", normalized.get("categoryPath"));
        assertEquals("14%", normalized.get("commissionRate"));
    }

    @Test
    void shouldTreatNullCurrencyStringAsMissingCurrency() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Bags & Luggage");
        payload.put("categoryName", "Travel Luggage");
        payload.put("categoryPath", "Bags & Luggage > Travel Luggage");
        payload.put("amountRangeLabel", "全部");
        payload.put("amountCurrency", "NULL");
        payload.put("commissionRate", "20%");
        String sourceText = "[[SOURCE_ROW_ID=98401;TYPE=pdf_text_line;LOC=page=1;line=48]]\n"
                + "Bags & Luggage\n"
                + "[[SOURCE_ROW_ID=98402;TYPE=pdf_text_line;LOC=page=1;line=49]]\n"
                + "Travel Luggage 20%";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(98402L)
        );

        assertEquals("SAR", normalized.get("amountCurrency"));
        assertEquals(
                "KSA|Bags & Luggage > Travel Luggage|全部|ALL|SAR|2025-09-01",
                FileParseNaturalKeySupport.buildNaturalKey("commission_rule", normalized)
        );
    }

    @Test
    void shouldIgnoreKindlyReferWhenInferringTierCategory() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Home");
        payload.put("categoryName", "apply, Kindly refer");
        payload.put("categoryPath", "Home > apply, Kindly refer");
        payload.put("amountRangeLabel", "> 750 SAR");
        payload.put("amountMin", 750);
        payload.put("amountMinInclusive", false);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "10%");
        String sourceText = "[[SOURCE_ROW_ID=98501;TYPE=pdf_text_line;LOC=page=1;line=58]]\n"
                + "Furniture All\n"
                + "[[SOURCE_ROW_ID=98502;TYPE=pdf_text_line;LOC=page=1;line=61]]\n"
                + "Some exceptions\n"
                + "[[SOURCE_ROW_ID=98503;TYPE=pdf_text_line;LOC=page=1;line=62]]\n"
                + "apply, Kindly refer\n"
                + "[[SOURCE_ROW_ID=98504;TYPE=pdf_text_line;LOC=page=1;line=65]]\n"
                + "- 10% for any portion of the\n"
                + "[[SOURCE_ROW_ID=98505;TYPE=pdf_text_line;LOC=page=1;line=66]]\n"
                + "total sales price greater than\n"
                + "[[SOURCE_ROW_ID=98506;TYPE=pdf_text_line;LOC=page=1;line=67]]\n"
                + "750 SAR";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(98501L, 98503L, 98504L, 98505L, 98506L)
        );

        assertEquals("Furniture", normalized.get("categoryName"));
        assertEquals("Home > Furniture", normalized.get("categoryPath"));
    }

    @Test
    void shouldKeepGenericBrandAsAllAmountRangeWhenSourceHasNoRangeOnRateLine() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Hair & Personal Care");
        payload.put("categoryName", "Hair, Skin & Personal Care");
        payload.put("categoryPath", "Hair & Personal Care > Hair, Skin & Personal Care");
        payload.put("amountRangeLabel", "<= 50 SAR");
        payload.put("amountMax", 50);
        payload.put("amountMaxInclusive", true);
        payload.put("amountCurrency", "SAR");
        payload.put("brandRestriction", "Generic brand");
        payload.put("commissionRate", "15%");
        String sourceText = "[[SOURCE_ROW_ID=98601;TYPE=pdf_text_line;LOC=page=1;line=74]]\n"
                + "Hair, Skin & Personal\n"
                + "[[SOURCE_ROW_ID=98602;TYPE=pdf_text_line;LOC=page=1;line=75]]\n"
                + "Care\n"
                + "[[SOURCE_ROW_ID=98603;TYPE=pdf_text_line;LOC=page=1;line=76]]\n"
                + "- 15% for all Generic brand\n"
                + "[[SOURCE_ROW_ID=98604;TYPE=pdf_text_line;LOC=page=1;line=77]]\n"
                + "- 9% for all other brand items\n"
                + "[[SOURCE_ROW_ID=98605;TYPE=pdf_text_line;LOC=page=1;line=78]]\n"
                + "with sale price of up to 50 SAR";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(98601L, 98602L, 98603L, 98604L, 98605L)
        );

        assertEquals("Generic brand", normalized.get("brandRestriction"));
        assertEquals("全部", normalized.get("amountRangeLabel"));
        assertNull(normalized.get("amountMax"));
    }

    @Test
    void shouldSplitKnownParentPrefixFromSingleSourceLine() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Camera");
        payload.put("categoryName", "Large Appliances Large Appliance");
        payload.put("categoryPath", "Camera > Large Appliances Large Appliance");
        payload.put("amountRangeLabel", "<= 250 SAR");
        payload.put("amountMax", 250);
        payload.put("amountMaxInclusive", true);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "12%");
        payload.put("effectiveDate", null);
        String sourceText = "[[SOURCE_ROW_ID=98001;TYPE=pdf_text_line;LOC=page=4;line=173]]\n"
                + "Camera\n"
                + "[[SOURCE_ROW_ID=98002;TYPE=pdf_text_line;LOC=page=4;line=189]]\n"
                + "Large Appliances Large Appliance\n"
                + "[[SOURCE_ROW_ID=98003;TYPE=pdf_text_line;LOC=page=4;line=190]]\n"
                + "- 12% for item with sales price";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(98002L, 98003L)
        );

        assertEquals("Large Appliances", normalized.get("parentCategoryName"));
        assertEquals("Large Appliance", normalized.get("categoryName"));
        assertEquals("Large Appliances > Large Appliance", normalized.get("categoryPath"));
    }

    @Test
    void shouldCorrectAiUsingParentAsLeafWhenSourceHasParentAndChild() {
        Map<String, Object> payload = commissionPayload();
        payload.put("country", "KSA");
        payload.put("parentCategoryName", "Camera");
        payload.put("categoryName", "Large Appliances");
        payload.put("categoryPath", "Camera > Large Appliances");
        payload.put("amountRangeLabel", "<= 250 SAR");
        payload.put("amountMax", 250);
        payload.put("amountMaxInclusive", true);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "12%");
        String sourceText = "[[SOURCE_ROW_ID=98101;TYPE=pdf_text_line;LOC=page=4;line=189]]\n"
                + "Large Appliances Large Appliance\n"
                + "[[SOURCE_ROW_ID=98102;TYPE=pdf_text_line;LOC=page=4;line=190]]\n"
                + "- 12% for item with sales price";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "commission_rule",
                payload,
                sourceText,
                List.of(98101L, 98102L)
        );

        assertEquals("Large Appliances", normalized.get("parentCategoryName"));
        assertEquals("Large Appliance", normalized.get("categoryName"));
        assertEquals("Large Appliances > Large Appliance", normalized.get("categoryPath"));
    }

    @Test
    void shouldBuildStableLogisticsKeyFromPayloadFieldsInsteadOfAiNaturalKey() {
        Map<String, Object> firstPayload = logisticsPayload();
        Map<String, Object> secondPayload = logisticsPayload();
        secondPayload.put("billingRule", "实重/300与材积取大");

        String firstKey = FileParseNaturalKeySupport.buildNaturalKey("logistics_channel_rule", firstPayload);
        String secondKey = FileParseNaturalKeySupport.buildNaturalKey("logistics_channel_rule", secondPayload);

        assertEquals(firstKey, secondKey);
        assertEquals("YT-SAU-UNDATED-001|KSA|Riyadh|海运|普货（A类）", firstKey);
    }

    @Test
    void shouldNormalizeLogisticsBillingRuleFromSourceRow() {
        Map<String, Object> payload = logisticsPayload();
        payload.put("billingRule", "实重/300与材积取大");
        String sourceText = "[[SOURCE_ROW_ID=90001;TYPE=excel_row;LOC=sheet=货代报价;row=2]]\n"
                + "货代文档\tYT-SAU-UNDATED-001\t义特物流\t中国-沙特利雅得 海运双清包税\tKSA/Riyadh\t海运\t普货（A类）\t实重/300与材积取大\tCBM\t1190.00\tCNY\tSheet1!A2:C2";

        Map<String, Object> normalized = FileParseCommissionPayloadNormalizer.normalize(
                "logistics_channel_rule",
                payload,
                sourceText,
                List.of(90001L)
        );

        assertEquals("YT-SAU-UNDATED-001", normalized.get("channelKey"));
        assertEquals("KSA", normalized.get("country"));
        assertEquals("Riyadh", normalized.get("city"));
        assertEquals("海运", normalized.get("shippingMethod"));
        assertEquals("普货（A类）", normalized.get("feeItem"));
        assertEquals("实重/300与材积取大，CBM，1190.00 CNY", normalized.get("billingRule"));
        assertEquals(
                "YT-SAU-UNDATED-001|KSA|Riyadh|海运|普货（A类）",
                FileParseNaturalKeySupport.buildNaturalKey("logistics_channel_rule", normalized)
        );
    }

    private Map<String, Object> commissionPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "ksa");
        payload.put("categoryName", " Fashion   >   Apparel, Footwear ");
        payload.put("amountRangeLabel", "all");
        payload.put("amountMin", null);
        payload.put("amountMinInclusive", null);
        payload.put("amountMax", null);
        payload.put("amountMaxInclusive", null);
        payload.put("amountCurrency", "sar");
        payload.put("effectiveDate", "2025-09-01");
        return payload;
    }

    private Map<String, Object> logisticsPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelKey", "YT-SAU-UNDATED-001");
        payload.put("country", "KSA");
        payload.put("city", "Riyadh");
        payload.put("shippingMethod", "海运");
        payload.put("feeItem", "普货（A类）");
        payload.put("billingRule", "实重/300与材积取大，CBM，1190.00 CNY");
        payload.put("leadTime", null);
        return payload;
    }
}
