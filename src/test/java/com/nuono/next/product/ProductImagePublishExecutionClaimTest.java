package com.nuono.next.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProductImagePublishExecutionClaimTest {

    @Test
    void duplicateDeliveryShouldAllowOnlyTheDatabaseClaimOwnerToCallNoon() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProductImageNoonPublisher> publisherProvider = mock(ObjectProvider.class);
        ProductImageNoonPublisher publisher = mock(ProductImageNoonPublisher.class);
        ProductImageSuiteAssetRecord asset = new ProductImageSuiteAssetRecord();
        asset.setId(5001L);
        asset.setImageUrl("/api/product-images/assets/STR108065-NAE/main.png");
        asset.setSha256("sha-main");
        asset.setSortOrder(10);
        ProductImagePublishCheckpoint attempt =
                ProductImagePublishCheckpoint.start(List.of(asset));
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setSuiteStatus(ProductImageSuiteStatus.PUBLISHING);
        suite.setPublishManifestJson(attempt.toJson(objectMapper));
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        profile.setProductMasterId(9001L);
        when(mapper.selectSuiteByIdUnscoped(9901L)).thenReturn(suite);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE"))
                .thenReturn(profile);
        when(mapper.selectSkuParentByProductMasterId(9001L)).thenReturn("PARENT-1");
        when(mapper.claimSuitePublishExecution(
                eq(9901L), eq(attempt.attemptId()), any(), eq(10003L)
        )).thenReturn(1, 0);
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(publisher.publish(
                eq(307L), eq("STR108065-NAE"), eq("PARENT-1"), any(), any(), any()
        )).thenReturn(List.of("https://noon.test/main.png"));
        when(mapper.markSuiteOnline(
                eq(9901L), eq(attempt.attemptId()), any(), any()
        )).thenReturn(1);
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mapper,
                mock(ProductImageGenerator.class),
                publisherProvider,
                objectMapper
        );

        service.publish(9901L, 307L, "STR108065-NAE", 10003L, attempt.attemptId());
        service.publish(9901L, 307L, "STR108065-NAE", 10003L, attempt.attemptId());

        verify(publisher, times(1)).publish(
                eq(307L), eq("STR108065-NAE"), eq("PARENT-1"), any(), any(), any()
        );
    }
}
