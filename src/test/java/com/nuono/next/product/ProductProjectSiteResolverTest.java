package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductProjectSiteResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductNoonAdapter productNoonAdapter;

    private ProductProjectSiteResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProductProjectSiteResolver(objectMapper, storeSyncMapper, productNoonAdapter);
    }

    @Test
    void shouldResolveProjectCodeFromNoonProjectListAndCacheResult() throws Exception {
        JsonNode projectList = objectMapper.readTree("{"
                + "\"projects\":["
                + "{\"projectCode\":\"PRJ108065\",\"projectName\":\"canman\"}"
                + "]"
                + "}");
        when(productNoonAdapter.postJson(
                nullable(NoonSession.class),
                eq(NoonProductGateway.PROJECT_LIST_URL),
                any(JsonNode.class),
                eq(false)
        )).thenReturn(projectList);
        StoreSyncStoreRecord store = store("STR245027-NSA", "canman", "PRJ0108065", "SA");
        List<String> warnings = new ArrayList<>();

        assertEquals("PRJ108065", resolver.resolveProjectCode(null, "PRJ0108065", store, warnings));
        assertEquals("PRJ108065", resolver.resolveProjectCode(null, "PRJ0108065", store, warnings));

        assertEquals(List.of(), warnings);
        verify(productNoonAdapter, times(1)).postJson(
                nullable(NoonSession.class),
                eq(NoonProductGateway.PROJECT_LIST_URL),
                any(JsonNode.class),
                eq(false)
        );
    }

    @Test
    void shouldCacheFallbackProjectCodeWarning() throws Exception {
        JsonNode projectList = objectMapper.readTree("{"
                + "\"projects\":["
                + "{\"projectCode\":\"PRJ999999\",\"projectName\":\"other\"}"
                + "]"
                + "}");
        when(productNoonAdapter.postJson(
                nullable(NoonSession.class),
                eq(NoonProductGateway.PROJECT_LIST_URL),
                any(JsonNode.class),
                eq(false)
        )).thenReturn(projectList);
        StoreSyncStoreRecord store = store("STR245027-NSA", "canman", "PRJ108065", "SA");
        List<String> firstWarnings = new ArrayList<>();
        List<String> secondWarnings = new ArrayList<>();

        assertEquals("PRJ999999", resolver.resolveProjectCode(null, "PRJ108065", store, firstWarnings));
        assertEquals("PRJ999999", resolver.resolveProjectCode(null, "PRJ108065", store, secondWarnings));

        assertEquals(1, firstWarnings.size());
        assertEquals(firstWarnings, secondWarnings);
        verify(productNoonAdapter, times(1)).postJson(
                nullable(NoonSession.class),
                eq(NoonProductGateway.PROJECT_LIST_URL),
                any(JsonNode.class),
                eq(false)
        );
    }

    @Test
    void shouldLoadProjectSiteContextsFromLocalStoresAndNoonStatus() throws Exception {
        StoreSyncStoreRecord reference = store("STR245027-NSA", "canman", "PRJ108065", "SA");
        StoreSyncStoreRecord aeStore = store("STR245027-NAE", "canman", "PRJ108065", "AE");
        when(storeSyncMapper.listOwnerStores(10002L)).thenReturn(List.of(reference, aeStore));
        JsonNode storeList = objectMapper.readTree("{"
                + "\"noonStores\":["
                + "{\"noonStoreCode\":\"STR245027-NSA\",\"countryCode\":\"SA\",\"statusCode\":\"ACTIVE\"},"
                + "{\"noonStoreCode\":\"STR245027-NAE\",\"countryCode\":\"AE\",\"statusCode\":\"PAUSED\"}"
                + "]"
                + "}");
        when(productNoonAdapter.postJson(
                nullable(NoonSession.class),
                eq(NoonProductGateway.STORE_LIST_URL),
                any(JsonNode.class),
                eq(true)
        )).thenReturn(storeList);

        List<ProductProjectSiteContext> sites = resolver.loadProjectSiteContexts(
                null,
                10002L,
                reference,
                new ArrayList<>()
        );

        assertEquals(2, sites.size());
        assertEquals("STR245027-NSA", sites.get(0).getStoreCode());
        assertEquals("ACTIVE", sites.get(0).getStatusCode());
        assertEquals("AE", sites.get(1).getSite());
        assertEquals("PAUSED", sites.get(1).getStatusCode());
        assertEquals("SA", resolver.resolveReferenceSite(sites, "STR245027-NSA"));
        assertEquals("AE / STR245027-NAE", resolver.describeSite(sites.get(1)));
    }

    @Test
    void shouldKeepReferenceStoreWhenLocalProjectListIsEmpty() throws Exception {
        StoreSyncStoreRecord reference = store("STR245027-NSA", "canman", "PRJ108065", null);
        when(storeSyncMapper.listOwnerStores(10002L)).thenReturn(List.of());
        when(productNoonAdapter.postJson(
                nullable(NoonSession.class),
                eq(NoonProductGateway.STORE_LIST_URL),
                any(JsonNode.class),
                eq(true)
        )).thenReturn(objectMapper.readTree("{\"noonStores\":[]}"));

        List<ProductProjectSiteContext> sites = resolver.loadProjectSiteContexts(
                null,
                10002L,
                reference,
                new ArrayList<>()
        );

        assertEquals(1, sites.size());
        assertEquals("STR245027-NSA", sites.get(0).getStoreCode());
        assertEquals("SA", sites.get(0).getSite());
    }

    private StoreSyncStoreRecord store(String storeCode, String projectName, String projectCode, String site) {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode(storeCode);
        store.setProjectName(projectName);
        store.setProjectCode(projectCode);
        store.setSite(site);
        return store;
    }
}
