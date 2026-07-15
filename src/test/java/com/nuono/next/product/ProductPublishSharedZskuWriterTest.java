package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPublishSharedZskuWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProductNoonAdapter productNoonAdapter;

    private ProductPublishSharedZskuWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ProductPublishSharedZskuWriter(
                objectMapper,
                productNoonAdapter,
                new ProductPublishChangedDomainComparator(objectMapper)
        );
    }

    @Test
    void shouldBuildEnglishZskuBodyForSharedContentAndWritableAttributesOnly() {
        ProductMasterSnapshotView baseline = snapshot(
                "Old Brand",
                "old_fulltype",
                "Old title",
                List.of("Old bullet"),
                List.of("https://img.example/old.jpg"),
                "Small"
        );
        ProductMasterSnapshotView draft = snapshot(
                "New Brand",
                "new_fulltype",
                "New title",
                List.of("New bullet"),
                List.of("https://img.example/new.jpg"),
                "Large"
        );
        baseline.setKeyAttributes(List.of(
                attribute("material", "Cotton blend", "Cotton blend", null),
                attribute("seller_gtin", "1111111111111", "1111111111111", null)
        ));
        draft.setKeyAttributes(List.of(
                attribute("material", "Pure cotton", "Pure cotton", "percent"),
                attribute("seller_gtin", "2222222222222", "2222222222222", null),
                attribute("care_symbols", List.of("wash"), List.of("wash"), null),
                attribute("size_guide", "Manual only", "Manual only", null)
        ));
        ProductPublishUnsupportedChanges unsupportedChanges = new ProductPublishUnsupportedChanges();
        unsupportedChanges.getUnsupportedAttributeCodes().add("size_guide");

        ObjectNode body = writer.buildZskuUpsertBody(draft, baseline, "en", unsupportedChanges);
        JsonNode attributes = body.path("attributes");

        assertEquals("PARENT-001", body.path("skuParent").asText());
        assertEquals("en", body.path("lang").asText());
        assertEquals("New Brand", attributes.path("brand").asText());
        assertEquals("new_fulltype", attributes.path("product_fulltype").asText());
        assertTrue(attributes.path("update_fulltype").asBoolean());
        assertEquals("New title", attributes.path("product_title").asText());
        assertEquals("New bullet", attributes.path("feature_bullet_1").asText());
        assertEquals("https://img.example/new.jpg", attributes.path("image_url_1").asText());
        assertEquals("Pure cotton", attributes.path("material").asText());
        assertEquals("percent", attributes.path("material_unit").asText());
        assertFalse(attributes.has("seller_gtin"));
        assertFalse(attributes.has("care_symbols"));
        assertFalse(attributes.has("size_guide"));
    }

    @Test
    void shouldPublishVariantSizeCacheAndChangedEnglishZskuBody() {
        ProductMasterSnapshotView baseline = snapshot(
                "Same Brand",
                "same_fulltype",
                "Old title",
                List.of("Same bullet"),
                List.of("https://img.example/same.jpg"),
                "Small"
        );
        ProductMasterSnapshotView draft = snapshot(
                "Same Brand",
                "same_fulltype",
                "New title",
                List.of("Same bullet"),
                List.of("https://img.example/same.jpg"),
                "Large"
        );
        ProductMasterSnapshotView liveBeforePublish = snapshot(
                "Same Brand",
                "same_fulltype",
                "Old title",
                List.of("Same bullet"),
                List.of("https://img.example/same.jpg"),
                "Small"
        );
        liveBeforePublish.getTaxonomy().put("familyNameEn", "Fashion");
        liveBeforePublish.getTaxonomy().put("productTypeNameEn", "Clothing");
        liveBeforePublish.getTaxonomy().put("productSubtypeNameEn", "Shirts");

        writer.publishSharedAttributes(
                null,
                draft,
                baseline,
                liveBeforePublish,
                new ProductPublishUnsupportedChanges(),
                new ArrayList<>()
        );

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> bodyCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(productNoonAdapter, times(3)).postWriteJson(
                nullable(NoonSession.class),
                urlCaptor.capture(),
                bodyCaptor.capture(),
                eq(true)
        );
        assertEquals(
                List.of(
                        NoonProductGateway.PRODUCT_UPDATE_URL,
                        NoonProductGateway.CATPLAT_SKU_CACHE_URL,
                        NoonProductGateway.ZSKU_UPSERT_URL
                ),
                urlCaptor.getAllValues()
        );
        assertEquals("Large", bodyCaptor.getAllValues().get(0)
                .path("productUpdate").get(0)
                .path("childrenUpdate").get(0)
                .path("size").asText());
        assertEquals("PARENT-001", bodyCaptor.getAllValues().get(1).path("skuParent").asText());
        assertEquals("New title", bodyCaptor.getAllValues().get(2).path("attributes").path("product_title").asText());
        assertEquals("Large", bodyCaptor.getAllValues().get(2)
                .path("variants").get(0)
                .path("attributes")
                .path("size").asText());
    }

    @Test
    void shouldUploadLocalImageAssetsBeforePublishingImageUrls() throws Exception {
        Path uploadDir = ProductImageAssetFileSupport.productImageUploadDir();
        Files.createDirectories(uploadDir);
        String filename = UUID.randomUUID() + ".jpg";
        Path localImage = uploadDir.resolve(filename);
        Files.write(localImage, new byte[] {1, 2, 3});
        try {
            ProductMasterSnapshotView baseline = snapshot(
                    "Same Brand",
                    "same_fulltype",
                    "Same title",
                    List.of("Same bullet"),
                    List.of("https://img.example/old.jpg"),
                    "Small"
            );
            ProductMasterSnapshotView draft = snapshot(
                    "Same Brand",
                    "same_fulltype",
                    "Same title",
                    List.of("Same bullet"),
                    List.of("/api/product-master/image-assets/" + filename),
                    "Small"
            );
            ObjectNode uploadResponse = objectMapper.createObjectNode();
            uploadResponse.put("upload_path", "noon-uploaded/" + filename);
            when(productNoonAdapter.postMultipartFile(
                    nullable(NoonSession.class),
                    eq("https://noon-catalog.noon.partners/_svc/mp-partner-catalog/catalog/asset/upload"),
                    eq("file"),
                    eq(filename),
                    eq("image/jpeg"),
                    any(byte[].class),
                    eq(true),
                    nullable(Map.class)
            )).thenReturn(uploadResponse);
            List<String> warnings = new ArrayList<>();

            writer.publishSharedAttributes(
                    null,
                    draft,
                    baseline,
                    baseline,
                    new ProductPublishUnsupportedChanges(),
                    warnings
            );

            ArgumentCaptor<JsonNode> bodyCaptor = ArgumentCaptor.forClass(JsonNode.class);
            verify(productNoonAdapter).postWriteJson(
                    nullable(NoonSession.class),
                    eq(NoonProductGateway.ZSKU_UPSERT_URL),
                    bodyCaptor.capture(),
                    eq(true)
            );
            assertEquals(
                    "noon-uploaded/" + filename,
                    bodyCaptor.getValue().path("attributes").path("image_url_1").asText()
            );
            assertTrue(warnings.stream().anyMatch((warning) -> warning.contains("本地上传图片")));
        } finally {
            Files.deleteIfExists(localImage);
        }
    }

    private ProductMasterSnapshotView snapshot(
            String brand,
            String productFulltype,
            String titleEn,
            List<String> highlightsEn,
            List<String> images,
            String sizeEn
    ) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("skuParent", "PARENT-001");
        identity.put("brand", brand);
        identity.put("partnerSku", "PARTNER-001");
        identity.put("pskuCode", "PSKU-001");
        snapshot.setIdentity(identity);

        Map<String, Object> taxonomy = new LinkedHashMap<>();
        taxonomy.put("family", "fashion");
        taxonomy.put("productType", "clothing");
        taxonomy.put("productSubtype", "shirts");
        taxonomy.put("productFulltype", productFulltype);
        taxonomy.put("grade", "new");
        taxonomy.put("itemCondition", "new");
        snapshot.setTaxonomy(taxonomy);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("titleEn", titleEn);
        content.put("titleAr", "Arabic title");
        content.put("descriptionEn", "English description");
        content.put("descriptionAr", "Arabic description");
        content.put("highlightsEn", highlightsEn);
        content.put("highlightsAr", List.of("Arabic bullet"));
        content.put("images", images);
        snapshot.setContent(content);

        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", "SKU-001");
        variant.put("partnerSku", "PARTNER-001");
        variant.put("pskuCode", "PSKU-001");
        variant.put("sizeEn", sizeEn);
        variant.put("sizeAr", "Small ar");
        variant.put("variantIndex", 1);
        snapshot.setVariants(List.of(variant));
        return snapshot;
    }

    private Map<String, Object> attribute(String code, Object commonValue, Object enValue, String unit) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("code", code);
        attribute.put("commonValue", commonValue);
        attribute.put("enValue", enValue);
        if (unit != null) {
            attribute.put("unit", unit);
        }
        return attribute;
    }
}
