package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ProductImageSingleVersionWorkflowTest {

    @Test
    void generationShouldStayInProgressWhenARequiredReviewImageIsIncomplete() {
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        ProductImageGenerator generator = mock(ProductImageGenerator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProductImageNoonPublisher> publisherProvider = mock(ObjectProvider.class);
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mapper, generator, publisherProvider, new ObjectMapper()
        );
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setDraftPromptText("draft");
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        ProductImageSuiteAssetRecord incompleteDetail =
                asset(5004L, ProductImageSuiteAssetRole.MATERIAL_DETAIL, "/detail.png", 40);
        incompleteDetail.setSha256(null);
        List<ProductImageSuiteAssetRecord> assets = List.of(
                asset(5001L, ProductImageSuiteAssetRole.MAIN, "/main.png", 10),
                asset(5002L, ProductImageSuiteAssetRole.SIZE, "/size.png", 20),
                asset(5003L, ProductImageSuiteAssetRole.CORE_FEATURE, "/feature.png", 30),
                incompleteDetail,
                asset(5005L, ProductImageSuiteAssetRole.USAGE_SCENE, "/scene.png", 50),
                asset(5006L, ProductImageSuiteAssetRole.PACKAGE_LIST, "/package.png", 60)
        );
        when(mapper.selectSuiteByIdUnscoped(9901L)).thenReturn(suite);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(assets);
        when(mapper.selectReviewTargets(9901L)).thenReturn(List.of());

        service.generate(9901L, 307L, "STR108065-NAE", 10003L);

        verify(mapper).updateSuiteWorkflowStatus(
                9901L, ProductImageSuiteStatus.GENERATING, null, null, 10003L
        );
        verify(mapper, never()).updateSuiteWorkflowStatus(
                9901L, ProductImageSuiteStatus.PENDING_REVIEW, null, null, 10003L
        );
    }

    @Test
    void reworkShouldReplaceSelectedAssetInsideTheSameSuite() {
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        ProductImageGenerator generator = mock(ProductImageGenerator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProductImageNoonPublisher> publisherProvider = mock(ObjectProvider.class);
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mapper, generator, publisherProvider, new ObjectMapper()
        );
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setDraftPromptText("draft");
        suite.setReviewComment("尺寸不清楚");
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        ProductImageSuiteAssetRecord main =
                asset(5001L, ProductImageSuiteAssetRole.MAIN, "/main.png", 10);
        ProductImageSuiteAssetRecord size =
                asset(5002L, ProductImageSuiteAssetRole.SIZE, "/size.png", 20);
        ProductImageSuiteReviewTargetRecord target = new ProductImageSuiteReviewTargetRecord();
        target.setSuiteId(9901L);
        target.setTargetScope("IMAGE");
        target.setAssetId(5002L);
        target.setImageRole(ProductImageSuiteAssetRole.SIZE);
        target.setRoleOrdinal(1);
        when(mapper.selectSuiteByIdUnscoped(9901L)).thenReturn(suite);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of(
                main,
                size,
                asset(5003L, ProductImageSuiteAssetRole.CORE_FEATURE, "/feature.png", 30),
                asset(5004L, ProductImageSuiteAssetRole.MATERIAL_DETAIL, "/detail.png", 40),
                asset(5005L, ProductImageSuiteAssetRole.USAGE_SCENE, "/scene.png", 50),
                asset(5006L, ProductImageSuiteAssetRole.PACKAGE_LIST, "/package.png", 60)
        ));
        when(mapper.selectReviewTargets(9901L)).thenReturn(List.of(target));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(generator.generate(anyString(), any())).thenReturn(new GeneratedProductImage(
                new byte[] {1, 2, 3},
                "image/png"
        ));

        service.generate(9901L, 307L, "STR108065-NAE", 10003L);

        verify(mapper).updateSuiteWorkflowStatus(
                9901L, ProductImageSuiteStatus.REGENERATING, null, null, 10003L
        );
        ArgumentCaptor<List<String>> references = ArgumentCaptor.forClass(List.class);
        verify(generator).generate(anyString(), references.capture());
        assertTrue(references.getValue().contains("/size.png"));
        verify(mapper).updateSuiteAssetContent(
                eq(9901L), eq(5002L), anyString(), eq("image/png"), eq(3L), anyString()
        );
        verify(mapper, never()).insertSuiteAsset(any());
        verify(mapper).updateSuiteWorkflowStatus(
                9901L, ProductImageSuiteStatus.PENDING_REVIEW, null, null, 10003L
        );
    }

    @Test
    void packageReworkShouldGenerateOnlyATransparentContentLayer() throws Exception {
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        ProductImageGenerator generator = mock(ProductImageGenerator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProductImageNoonPublisher> publisherProvider = mock(ObjectProvider.class);
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mapper, generator, publisherProvider, new ObjectMapper()
        );
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setSkinName("PAPERSAY 黄框主图皮肤");
        suite.setDraftPromptText("draft");
        suite.setReviewComment("背景颜色跟其他的不搭调");
        suite.setDraftPackageJson("{"
                + "\"profile\":{\"specSummary\":\"2 Pieces\"},"
                + "\"imageRequirements\":{\"packageList\":{"
                + "\"copies\":[\"2 computer monitor memo boards\"]"
                + "}}"
                + "}");
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        ProductImageSuiteAssetRecord packageAsset =
                asset(5006L, ProductImageSuiteAssetRole.PACKAGE_LIST, "/package.png", 60);
        ProductImageSuiteReviewTargetRecord target = new ProductImageSuiteReviewTargetRecord();
        target.setSuiteId(9901L);
        target.setTargetScope("IMAGE");
        target.setAssetId(5006L);
        target.setImageRole(ProductImageSuiteAssetRole.PACKAGE_LIST);
        target.setRoleOrdinal(1);
        when(mapper.selectSuiteByIdUnscoped(9901L)).thenReturn(suite);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of(
                asset(5001L, ProductImageSuiteAssetRole.MAIN, "/main.png", 10),
                asset(5002L, ProductImageSuiteAssetRole.SIZE, "/size.png", 20),
                asset(5003L, ProductImageSuiteAssetRole.CORE_FEATURE, "/feature.png", 30),
                asset(5004L, ProductImageSuiteAssetRole.MATERIAL_DETAIL, "/detail.png", 40),
                asset(5005L, ProductImageSuiteAssetRole.USAGE_SCENE, "/scene.png", 50),
                packageAsset
        ));
        when(mapper.selectReviewTargets(9901L)).thenReturn(List.of(target));
        ProductImageProfileAssetRecord mainSource = new ProductImageProfileAssetRecord();
        mainSource.setImageRole(ProductImageRole.MAIN);
        mainSource.setImageUrl("https://images.test/main-source.png");
        ProductImageProfileAssetRecord packageSource = new ProductImageProfileAssetRecord();
        packageSource.setImageRole(ProductImageRole.PACKAGE);
        packageSource.setImageUrl("https://images.test/package-source.png");
        when(mapper.selectAssets(7001L)).thenReturn(List.of(mainSource, packageSource));
        when(generator.generate(anyString(), any())).thenReturn(transparentProductContent());

        service.generate(9901L, 307L, "STR108065-NAE", 10003L);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<String>> references = ArgumentCaptor.forClass(List.class);
        verify(generator).generate(prompt.capture(), references.capture());
        assertTrue(prompt.getValue().contains("只生成透明商品内容层"));
        assertTrue(prompt.getValue().contains("不要生成品牌皮肤、Logo、图标、文字、边框或背景"));
        assertEquals("https://images.test/package-source.png", references.getValue().get(0));
    }

    @Test
    void nonPapersayPackageShouldKeepGeneratingCompleteArtwork() {
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mock(ProductImageProfileMapper.class),
                mock(ProductImageGenerator.class),
                mock(ObjectProvider.class),
                new ObjectMapper()
        );
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setSkinName("OTHER STORE SKIN");
        suite.setDraftPromptText("draft");

        String prompt = service.rolePrompt(suite, ProductImageSuiteAssetRole.PACKAGE_LIST);

        assertTrue(prompt.contains("包装图1：只展示已确认包含的商品和配件"));
        assertTrue(!prompt.contains(PapersayPackageImageComposer.CONTENT_LAYER_MARKER));
    }

    private GeneratedProductImage transparentProductContent() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GRAY);
        graphics.fillRoundRect(25, 20, 50, 60, 8, 8);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new GeneratedProductImage(output.toByteArray(), "image/png");
    }

    private ProductImageSuiteAssetRecord asset(
            Long id,
            ProductImageSuiteAssetRole role,
            String imageUrl,
            int sortOrder
    ) {
        ProductImageSuiteAssetRecord asset = new ProductImageSuiteAssetRecord();
        asset.setId(id);
        asset.setSuiteId(9901L);
        asset.setImageRole(role);
        asset.setRoleOrdinal(1);
        asset.setImageUrl(imageUrl);
        asset.setContentType("image/png");
        asset.setSizeBytes(128L);
        asset.setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        asset.setSortOrder(sortOrder);
        return asset;
    }
}
