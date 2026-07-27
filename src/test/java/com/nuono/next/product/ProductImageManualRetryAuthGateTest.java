package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.infrastructure.mapper.OperationsSkinMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProductImageManualRetryAuthGateTest {

    @Test
    void generationRetryShouldNotConsultTheProjectAuthGate() {
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        ProductImageProfileService service = new ProductImageProfileService(
                mapper,
                mock(OperationsSkinMapper.class),
                mock(ProductPublicDetailMapper.class),
                mock(AiCapabilityService.class),
                events
        );
        service.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(
                mock(NoonProjectAuthRecoveryQueue.class),
                authGate
        ));
        ProductImageProfileRecord profile = profile();
        ProductImageSuiteRecord suite = failedGenerationSuite();
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteById(9901L, 7001L)).thenReturn(suite);
        when(mapper.selectSuiteByIdForUpdate(9901L, 7001L)).thenReturn(suite);
        when(mapper.retryFailedSuiteWorkflow(
                9901L, 7001L, ProductImageSuiteStatus.PENDING_GENERATION, 10003L
        )).thenReturn(1);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.retrySuite(307L, "STR108065-NAE", 7001L, 9901L, 10003L);

        verify(authGate, never()).isBlocked(eq(307L), anyString());
        verify(mapper).retryFailedSuiteWorkflow(
                9901L, 7001L, ProductImageSuiteStatus.PENDING_GENERATION, 10003L
        );
        verify(events).publishEvent(any(ProductImageGenerationSubmittedEvent.class));
        verify(events, never()).publishEvent(any(ProductImagePublishSubmittedEvent.class));
    }

    @Test
    void authRecoveryRetryShouldStayFailedUntilASecondManualAttemptSeesCurrentProjectAvailable() {
        ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductWriteAuthRecovery authRecovery = new ProductWriteAuthRecovery(
                mock(NoonProjectAuthRecoveryQueue.class),
                authGate
        );
        authRecovery.setStoreSyncMapper(storeSyncMapper);
        ProductImageProfileService service = new ProductImageProfileService(
                mapper,
                mock(OperationsSkinMapper.class),
                mock(ProductPublicDetailMapper.class),
                mock(AiCapabilityService.class),
                events
        );
        service.setProductWriteAuthRecovery(authRecovery);

        ProductImageProfileRecord profile = profile();
        ProductImageSuiteRecord suite = failedAuthSuite();
        StoreSyncStoreRecord blockedProject = project("PRJ-BLOCKED");
        StoreSyncStoreRecord restoredProject = project("PRJ-RESTORED");
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteById(9901L, 7001L)).thenReturn(suite);
        when(storeSyncMapper.selectOwnerProject(307L, "STR108065-NAE"))
                .thenReturn(blockedProject, restoredProject, restoredProject);
        when(authGate.isBlocked(307L, "PRJ-BLOCKED")).thenReturn(true);
        when(authGate.isBlocked(307L, "PRJ-RESTORED")).thenReturn(false);

        assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> service.retrySuite(307L, "STR108065-NAE", 7001L, 9901L, 10003L)
        );

        verify(mapper, never()).selectSuiteByIdForUpdate(9901L, 7001L);
        verify(mapper, never()).selectSuiteAssets(9901L);
        verify(mapper, never()).retryFailedSuitePublishWorkflow(
                eq(9901L), eq(7001L), anyString(), eq(10003L)
        );
        verify(events, never()).publishEvent(any(Object.class));

        when(mapper.selectSuiteByIdForUpdate(9901L, 7001L)).thenReturn(suite);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of(asset()));
        when(mapper.retryFailedSuitePublishWorkflow(
                eq(9901L), eq(7001L), anyString(), eq(10003L)
        )).thenReturn(1);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.retrySuite(307L, "STR108065-NAE", 7001L, 9901L, 10003L);

        verify(mapper).retryFailedSuitePublishWorkflow(
                eq(9901L), eq(7001L), anyString(), eq(10003L)
        );
        verify(events).publishEvent(any(ProductImagePublishSubmittedEvent.class));
        verify(storeSyncMapper, times(3)).selectOwnerProject(307L, "STR108065-NAE");
        verify(authGate).isBlocked(307L, "PRJ-BLOCKED");
        verify(authGate, times(2)).isBlocked(307L, "PRJ-RESTORED");
    }

    private ProductImageProfileRecord profile() {
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        profile.setOwnerUserId(307L);
        profile.setStoreCode("STR108065-NAE");
        profile.setProductMasterId(9001L);
        return profile;
    }

    private ProductImageSuiteRecord failedAuthSuite() {
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setSuiteStatus(ProductImageSuiteStatus.FAILED);
        suite.setFailureStage("PUBLISH_AUTH_RECOVERY");
        return suite;
    }

    private ProductImageSuiteRecord failedGenerationSuite() {
        ProductImageSuiteRecord suite = failedAuthSuite();
        suite.setFailureStage("GENERATION");
        return suite;
    }

    private ProductImageSuiteAssetRecord asset() {
        ProductImageSuiteAssetRecord asset = new ProductImageSuiteAssetRecord();
        asset.setId(5001L);
        asset.setImageUrl("/api/product-images/assets/STR108065-NAE/main.png");
        asset.setSha256("sha-main");
        asset.setSortOrder(10);
        return asset;
    }

    private StoreSyncStoreRecord project(String projectCode) {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode(projectCode);
        return project;
    }
}
