package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProductImageWorkflowServiceTest {

    @Test
    void successfulPublishShouldKeepTheFinalVersionedCheckpointOnline() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProductImageNoonPublisher> publisherProvider = mock(ObjectProvider.class);
        ProductImageNoonPublisher publisher = mock(ProductImageNoonPublisher.class);
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setSuiteStatus(ProductImageSuiteStatus.PUBLISHING);
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        profile.setProductMasterId(9001L);
        ProductImageSuiteAssetRecord asset = approvedAsset();
        ProductImagePublishCheckpoint attempt =
                ProductImagePublishCheckpoint.start(List.of(asset));
        suite.setPublishManifestJson(attempt.toJson(objectMapper));
        when(mapper.selectSuiteByIdUnscoped(9901L)).thenReturn(suite);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSkuParentByProductMasterId(9001L)).thenReturn("PARENT-1");
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(mapper.claimSuitePublishExecution(
                eq(9901L), eq(attempt.attemptId()), any(), eq(10003L)
        )).thenReturn(1);
        when(publisher.publish(eq(307L), eq("STR108065-NAE"), eq("PARENT-1"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ProductImagePublishCheckpoint finalCheckpoint = ProductImagePublishCheckpoint.parse(
                            objectMapper,
                            invocation.getArgument(4)
                    );
                    finalCheckpoint.record(
                            asset.getImageUrl(),
                            asset.getSha256(),
                            "https://noon.test/main.png"
                    );
                    finalCheckpoint.markWriteAttempted();
                    Consumer<String> saver = invocation.getArgument(5);
                    saver.accept(finalCheckpoint.toJson(objectMapper));
                    return List.of("https://noon.test/main.png");
                });
        when(mapper.updateSuitePublishManifest(
                eq(9901L), eq(attempt.attemptId()), any(), any(), eq(10003L)
        )).thenReturn(1);
        org.mockito.ArgumentCaptor<String> finalManifest =
                org.mockito.ArgumentCaptor.forClass(String.class);
        when(mapper.markSuiteOnline(
                eq(9901L), eq(attempt.attemptId()), any(), finalManifest.capture()
        )).thenReturn(1);
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mapper,
                mock(ProductImageGenerator.class),
                publisherProvider,
                objectMapper
        );

        service.publish(
                9901L, 307L, "STR108065-NAE", 10003L, attempt.attemptId()
        );

        com.fasterxml.jackson.databind.JsonNode saved =
                objectMapper.readTree(finalManifest.getValue());
        assertEquals(2, saved.path("version").asInt());
        assertEquals(attempt.attemptId(), saved.path("attemptId").asText());
        assertTrue(saved.path("writeAttempted").asBoolean());
        assertEquals(5001L, saved.path("approvedImages").path(0).path("assetId").asLong());
        assertEquals(
                "https://noon.test/main.png",
                saved.path("uploads").path(0).path("noonUrl").asText()
        );
    }

    @Test
    void stalePublishAttemptShouldStopBeforeCallingPublisherOrMutatingWorkflow() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        ProductImageGenerator generator = mock(ProductImageGenerator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProductImageNoonPublisher> publisherProvider = mock(ObjectProvider.class);
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setSuiteStatus(ProductImageSuiteStatus.PUBLISHING);
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        ProductImageSuiteAssetRecord asset = approvedAsset();
        ProductImagePublishCheckpoint currentAttempt =
                ProductImagePublishCheckpoint.start(List.of(asset));
        suite.setPublishManifestJson(currentAttempt.toJson(objectMapper));
        when(mapper.selectSuiteByIdUnscoped(9901L)).thenReturn(suite);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mapper,
                generator,
                publisherProvider,
                objectMapper
        );

        service.publish(9901L, 307L, "STR108065-NAE", 10003L, "stale-attempt");

        verify(publisherProvider, never()).getIfAvailable();
        verify(mapper, never()).claimSuitePublishExecution(any(), any(), any(), any());
        verify(mapper, never()).updateSuitePublishManifest(any(), any(), any(), any(), any());
        verify(mapper, never()).markSuiteOnline(any(), any(), any(), any());
        verify(mapper, never()).failPublishingSuiteWorkflow(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void authFailureShouldKeepCheckpointAndRequireManualPublishRetry() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        ProductImageGenerator generator = mock(ProductImageGenerator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProductImageNoonPublisher> publisherProvider = mock(ObjectProvider.class);
        ProductImageNoonPublisher publisher = mock(ProductImageNoonPublisher.class);
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setSuiteStatus(ProductImageSuiteStatus.PUBLISHING);
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        profile.setProductMasterId(9001L);
        ProductImageSuiteAssetRecord asset = approvedAsset();
        ProductImagePublishCheckpoint attempt =
                ProductImagePublishCheckpoint.start(List.of(asset));
        suite.setPublishManifestJson(attempt.toJson(objectMapper));
        when(mapper.selectSuiteByIdUnscoped(9901L)).thenReturn(suite);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of(asset));
        when(mapper.selectSkuParentByProductMasterId(9001L)).thenReturn("PARENT-1");
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(mapper.claimSuitePublishExecution(
                eq(9901L), eq(attempt.attemptId()), any(), eq(10003L)
        )).thenReturn(1);
        when(publisher.publish(
                eq(307L),
                eq("STR108065-NAE"),
                eq("PARENT-1"),
                eq(List.of("/api/product-images/assets/STR108065-NAE/main.png")),
                eq(suite.getPublishManifestJson()),
                any()
        )).thenThrow(new ProductWriteAuthRequiredException(
                991L,
                true,
                "Noon Project 授权恢复中；recoveryId=991。",
                new IllegalStateException("auth_required")
        ));
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mapper,
                generator,
                publisherProvider,
                objectMapper
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> service.publish(
                        9901L, 307L, "STR108065-NAE", 10003L, attempt.attemptId()
                )
        );

        verify(mapper).failPublishingSuiteWorkflow(
                eq(9901L),
                eq(attempt.attemptId()),
                any(),
                eq("PUBLISH_AUTH_RECOVERY"),
                argThat(message -> message.contains("recoveryId=991")),
                eq(10003L)
        );
        verify(mapper, never()).markSuiteOnline(any(), any(), any(), any());
    }

    @Test
    void latePublisherFailureShouldNotOverwriteSuiteThatIsAlreadyOnline() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        ProductImageGenerator generator = mock(ProductImageGenerator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProductImageNoonPublisher> publisherProvider = mock(ObjectProvider.class);
        ProductImageNoonPublisher publisher = mock(ProductImageNoonPublisher.class);
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setSuiteStatus(ProductImageSuiteStatus.PUBLISHING);
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        profile.setProductMasterId(9001L);
        ProductImageSuiteAssetRecord asset = approvedAsset();
        ProductImagePublishCheckpoint attempt =
                ProductImagePublishCheckpoint.start(List.of(asset));
        suite.setPublishManifestJson(attempt.toJson(objectMapper));
        when(mapper.selectSuiteByIdUnscoped(9901L)).thenReturn(suite);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of(asset));
        when(mapper.selectSkuParentByProductMasterId(9001L)).thenReturn("PARENT-1");
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(mapper.claimSuitePublishExecution(
                eq(9901L), eq(attempt.attemptId()), any(), eq(10003L)
        )).thenReturn(1);
        when(publisher.publish(eq(307L), eq("STR108065-NAE"), eq("PARENT-1"), any(), any(), any()))
                .thenReturn(List.of("https://noon.test/main.png"));
        when(mapper.markSuiteOnline(
                eq(9901L), eq(attempt.attemptId()), any(), any()
        )).thenReturn(0);
        when(mapper.failPublishingSuiteWorkflow(
                eq(9901L), eq(attempt.attemptId()), any(), eq("PUBLISH"), any(), eq(10003L)
        ))
                .thenReturn(0);
        ProductImageWorkflowService service = new ProductImageWorkflowService(
                mapper,
                generator,
                publisherProvider,
                objectMapper
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.publish(
                        9901L, 307L, "STR108065-NAE", 10003L, attempt.attemptId()
                )
        );

        verify(mapper).failPublishingSuiteWorkflow(
                eq(9901L),
                eq(attempt.attemptId()),
                any(),
                eq("PUBLISH"),
                argThat(message -> message.contains("状态已变化")),
                eq(10003L)
        );
        verify(mapper, never()).updateSuiteWorkflowStatus(
                any(), any(), any(), any(), any()
        );
    }

    private ProductImageSuiteAssetRecord approvedAsset() {
        ProductImageSuiteAssetRecord asset = new ProductImageSuiteAssetRecord();
        asset.setId(5001L);
        asset.setImageUrl("/api/product-images/assets/STR108065-NAE/main.png");
        asset.setSha256("sha-main");
        asset.setSortOrder(10);
        return asset;
    }
}
