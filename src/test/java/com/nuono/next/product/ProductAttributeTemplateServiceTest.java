package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductAttributeTemplateMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ProductAttributeTemplateServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProductAttributeTemplateMapper templateMapper;
    private ProductAttributeTemplateService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(ProductAttributeTemplateMapper.class);
        service = new ProductAttributeTemplateService(
                templateMapper,
                mock(ProductManagementMapper.class),
                mock(StoreSyncMapper.class),
                mock(ProductNoonAdapter.class),
                objectMapper
        );
    }

    @Test
    void shouldLoadAttributeTemplateFromGlobalProductFulltypeCacheAcrossStores() throws Exception {
        ProductAttributeTemplateRecord record = new ProductAttributeTemplateRecord();
        record.setProjectCode("*");
        record.setStoreCode("*");
        record.setProductFulltype("home_decor-lighting");
        record.setRawJson("{\"fundamental\":{\"attribute_properties\":{\"colour_family\":{\"is_visible_seller\":1}}}}");
        record.setFetchedAt(LocalDateTime.now());
        lenient().when(templateMapper.selectByScope("*", "*", "home_decor-lighting")).thenReturn(record);
        lenient().when(templateMapper.selectDictionaryFields("*", "*", "home_decor-lighting")).thenReturn(List.of());

        JsonNode first = service.loadTemplate(
                null,
                "PRJ108065",
                "STR245027-NSA",
                "home_decor-lighting",
                307L,
                List.of()
        );
        JsonNode second = service.loadTemplate(
                null,
                "PRJ245027",
                "STR999999-NAE",
                "home_decor-lighting",
                307L,
                List.of()
        );

        assertThat(first.path("fundamental").path("attribute_properties").path("colour_family").isObject()).isTrue();
        assertThat(second.path("fundamental").path("attribute_properties").path("colour_family").isObject()).isTrue();
        verify(templateMapper).selectByScope(eq("*"), eq("*"), eq("home_decor-lighting"));
    }

    @Test
    void shouldRunAttributeTemplateRefreshWeeklyByDefault() throws Exception {
        Scheduled scheduled = ProductAttributeTemplateService.class
                .getMethod("refreshStaleTemplates")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${nuono.product-management.attribute-template.scheduler.fixed-delay-ms:604800000}");
    }
}
