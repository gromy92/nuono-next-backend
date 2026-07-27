package com.nuono.next.product;

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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ProductImageSingleVersionWorkflowTest {

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
        asset.setSortOrder(sortOrder);
        return asset;
    }
}
