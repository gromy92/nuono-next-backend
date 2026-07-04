package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductKeywordControllerAccessTest {
    private static final String STORE_CODE = "STR108065-NSA";
    private static final String SITE_CODE = "SA";
    private static final String PARTNER_SKU = "PSKU-1";

    @Mock
    private ProductKeywordService service;

    @Mock
    private BusinessAccessResolver accessResolver;

    private ProductKeywordController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductKeywordController(service, accessResolver);
    }

    @Test
    void listRequiresStoreAccessAndForwardsNormalizedQuery() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE))
                .thenReturn(context);
        ProductKeywordViews.KeywordListView view =
                new ProductKeywordViews.KeywordListView(List.of(ProductKeywordViews.keyword(keyword(300001L))));
        when(service.listKeywords(eq(context), any(ProductKeywordListQuery.class))).thenReturn(view);

        ProductKeywordViews.KeywordListView result = controller.list(
                " str108065-nsa ",
                " sa ",
                " PSKU-1 ",
                " Milk ",
                " active ",
                request
        );

        assertThat(result).isSameAs(view);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE);
        ArgumentCaptor<ProductKeywordListQuery> queryCaptor = ArgumentCaptor.forClass(ProductKeywordListQuery.class);
        verify(service).listKeywords(eq(context), queryCaptor.capture());
        ProductKeywordListQuery query = queryCaptor.getValue();
        assertThat(query.getStoreCode()).isEqualTo(STORE_CODE);
        assertThat(query.getSiteCode()).isEqualTo(SITE_CODE);
        assertThat(query.getPartnerSku()).isEqualTo(PARTNER_SKU);
        assertThat(query.getKeywordNorm()).isEqualTo("milk");
        assertThat(query.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void productKeywordPanelRequiresStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE))
                .thenReturn(context);
        ProductKeywordViews.ProductKeywordPanelView view =
                new ProductKeywordViews.ProductKeywordPanelView(STORE_CODE, SITE_CODE, PARTNER_SKU, List.of(), List.of());
        when(service.productKeywords(context, STORE_CODE, SITE_CODE, PARTNER_SKU)).thenReturn(view);

        ProductKeywordViews.ProductKeywordPanelView result = controller.productKeywords(
                PARTNER_SKU,
                " str108065-nsa ",
                " sa ",
                request
        );

        assertThat(result).isSameAs(view);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE);
        verify(service).productKeywords(context, STORE_CODE, SITE_CODE, PARTNER_SKU);
    }

    @Test
    void createResolvesAccessFromBodyStoreCodeAndAddsManualKeyword() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context();
        ProductKeywordCommand command = command(" str108065-nsa ", " sa ", PARTNER_SKU, "Milk Bottle");
        ProductKeywordRecord created = keyword(300001L);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE))
                .thenReturn(context);
        when(service.addManualKeyword(context, command)).thenReturn(created);

        ProductKeywordViews.KeywordItemView result = controller.create(command, request);

        assertThat(result.getId()).isEqualTo(300001L);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE);
        verify(service).addManualKeyword(context, command);
    }

    @Test
    void patchResolvesAccessAndForwardsCommand() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context();
        ProductKeywordCommand command = command(STORE_CODE, SITE_CODE, PARTNER_SKU, "Milk Cup");
        ProductKeywordRecord updated = keyword(300001L);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE))
                .thenReturn(context);
        when(service.updateKeyword(context, 300001L, command)).thenReturn(updated);

        ProductKeywordViews.KeywordItemView result = controller.update(300001L, command, request);

        assertThat(result.getId()).isEqualTo(300001L);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE);
        verify(service).updateKeyword(context, 300001L, command);
    }

    @Test
    void rebuildIndexRequiresStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context();
        ProductKeywordListQuery query = new ProductKeywordListQuery();
        query.setStoreCode(" str108065-nsa ");
        query.setSiteCode(" sa ");
        query.setPartnerSku(PARTNER_SKU);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE))
                .thenReturn(context);
        ProductKeywordViews.RebuildIndexResultView view =
                new ProductKeywordViews.RebuildIndexResultView(STORE_CODE, SITE_CODE, PARTNER_SKU, "QUEUED");
        when(service.rebuildIndex(eq(context), any(ProductKeywordListQuery.class))).thenReturn(view);

        ProductKeywordViews.RebuildIndexResultView result = controller.rebuildIndex(query, request);

        assertThat(result).isSameAs(view);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT, STORE_CODE);
        verify(service).rebuildIndex(eq(context), any(ProductKeywordListQuery.class));
    }

    @Test
    void missingStoreOrSiteReturnsBadRequestBeforeAccessCheck() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> controller.list(" ", SITE_CODE, PARTNER_SKU, null, null, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> controller.list(STORE_CODE, " ", PARTNER_SKU, null, null, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(accessResolver, service);
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(7L)
                .businessOwnerUserId(99L)
                .storeCodes(Set.of(STORE_CODE))
                .storeOwnerUserIds(Map.of(STORE_CODE, 99L))
                .menuPaths(Set.of("/operations/product-keywords"))
                .build();
    }

    private static ProductKeywordCommand command(String storeCode, String siteCode, String partnerSku, String keyword) {
        ProductKeywordCommand command = new ProductKeywordCommand();
        command.setStoreCode(storeCode);
        command.setSiteCode(siteCode);
        command.setPartnerSku(partnerSku);
        command.setKeyword(keyword);
        command.setIntentTags(List.of("CORE"));
        return command;
    }

    private static ProductKeywordRecord keyword(Long id) {
        ProductKeywordRecord record = new ProductKeywordRecord();
        record.setId(id);
        record.setOwnerUserId(99L);
        record.setStoreCode(STORE_CODE);
        record.setSiteCode(SITE_CODE);
        record.setPartnerSku(PARTNER_SKU);
        record.setKeyword("Milk Bottle");
        record.setKeywordNorm("milk bottle");
        record.setStatus("ACTIVE");
        record.setIntentTagsJson("[\"CORE\"]");
        return record;
    }
}
