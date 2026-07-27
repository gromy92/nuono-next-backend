package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.infrastructure.mapper.OperationsSkinMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.operationsskin.OperationsSkinComponentRecord;
import com.nuono.next.operationsskin.OperationsSkinRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProductImageSingleVersionServiceTest {

    @Mock
    private ProductImageProfileMapper mapper;
    @Mock
    private OperationsSkinMapper operationsSkinMapper;
    @Mock
    private ProductPublicDetailMapper productPublicDetailMapper;
    @Mock
    private AiCapabilityService aiCapabilityService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProductImageProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProductImageProfileService(
                mapper,
                operationsSkinMapper,
                productPublicDetailMapper,
                aiCapabilityService,
                eventPublisher
        );
    }

    @Test
    void createAiSuiteDraftShouldRestartTheExistingSuiteInsteadOfAddingVersion() {
        ProductImageProfileRecord profile = profile();
        ProductImageProfileAssetRecord currentImage = new ProductImageProfileAssetRecord();
        currentImage.setImageUrl("https://example.test/product-main.jpg");
        currentImage.setImageRole(ProductImageRole.MAIN);
        currentImage.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        ProductImageSuiteRecord existing = new ProductImageSuiteRecord();
        existing.setId(9901L);
        existing.setProfileId(7001L);
        existing.setSuiteStatus(ProductImageSuiteStatus.PENDING_REVIEW);

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(operationsSkinMapper.selectSkinById(3001L, 307L, "STR108065-NAE")).thenReturn(skin());
        when(operationsSkinMapper.selectComponents(3001L, 307L)).thenReturn(heroComponents());
        when(mapper.selectCurrentProductImages(9001L)).thenReturn(List.of(currentImage));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectLatestSuiteForUpdate(7001L)).thenReturn(existing);
        when(mapper.restartSuiteGeneration(
                eq(9901L), eq(7001L), eq("PAPERSAYSB106 通用 V1"), eq(3001L), anyString(), anyString(),
                anyString(), anyString(), eq(10003L)
        )).thenReturn(1);
        when(mapper.selectSuites(7001L)).thenReturn(List.of(existing));
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of());

        service.createAiSuiteDraft(307L, "STR108065-NAE", 7001L, 3001L, 10003L);

        verify(mapper, never()).insertSuite(any());
        verify(mapper).deleteReviewTargets(9901L);
        verify(mapper).insertReviewTarget(9901L, "SUITE", null, null, null, 10003L);
        ArgumentCaptor<ProductImageGenerationSubmittedEvent> event =
                ArgumentCaptor.forClass(ProductImageGenerationSubmittedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(9901L, event.getValue().suiteId());
    }

    @Test
    void firstAiSuiteDraftShouldUseTheCanonicalV1Name() {
        ProductImageProfileRecord profile = profile();
        ProductImageProfileAssetRecord currentImage = new ProductImageProfileAssetRecord();
        currentImage.setImageUrl("https://example.test/product-main.jpg");
        currentImage.setImageRole(ProductImageRole.MAIN);
        currentImage.setAssetStatus(ProductImageAssetStatus.ACTIVE);

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(operationsSkinMapper.selectSkinById(3001L, 307L, "STR108065-NAE")).thenReturn(skin());
        when(operationsSkinMapper.selectComponents(3001L, 307L)).thenReturn(heroComponents());
        when(mapper.selectCurrentProductImages(9001L)).thenReturn(List.of(currentImage));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(invocation -> {
            ProductImageSuiteRecord suite = invocation.getArgument(0);
            suite.setId(9901L);
            return 1;
        }).when(mapper).insertSuite(any());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.createAiSuiteDraft(307L, "STR108065-NAE", 7001L, 3001L, 10003L);

        ArgumentCaptor<ProductImageSuiteRecord> suite = ArgumentCaptor.forClass(ProductImageSuiteRecord.class);
        verify(mapper).insertSuite(suite.capture());
        assertEquals("PAPERSAYSB106 通用 V1", suite.getValue().getSuiteName());
    }

    private ProductImageProfileRecord profile() {
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        profile.setOwnerUserId(307L);
        profile.setStoreCode("STR108065-NAE");
        profile.setPskuCode("PAPERSAYSB106");
        profile.setProductMasterId(9001L);
        profile.setBrand("PAPERSAY");
        profile.setTitleEn("Computer Monitor Memo Board");
        profile.setSpecSummary("2 pieces");
        profile.setProductFactText("Verified facts");
        return profile;
    }

    private OperationsSkinRecord skin() {
        OperationsSkinRecord skin = new OperationsSkinRecord();
        skin.setId(3001L);
        skin.setOwnerUserId(307L);
        skin.setStoreCode("STR108065-NAE");
        skin.setSkinName("PAPERSAY");
        skin.setStatus("ACTIVE");
        skin.setHeroComponentCount(4);
        return skin;
    }

    private List<OperationsSkinComponentRecord> heroComponents() {
        return List.of(
                component("FRAME"),
                component("BRAND_LOCKUP"),
                component("SPEC_BG"),
                component("MAIN_TITLE_BG")
        );
    }

    private OperationsSkinComponentRecord component(String key) {
        OperationsSkinComponentRecord component = new OperationsSkinComponentRecord();
        component.setSkinId(3001L);
        component.setTemplateRole("HERO_MAIN");
        component.setComponentKey(key);
        component.setImageUrl("/skin/" + key + ".png");
        component.setRequired(true);
        component.setLocked(true);
        component.setUpdatedAt(LocalDateTime.of(2026, 7, 27, 12, 0));
        return component;
    }
}
