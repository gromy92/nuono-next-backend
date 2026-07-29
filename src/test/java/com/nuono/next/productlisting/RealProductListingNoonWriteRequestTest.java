package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RealProductListingNoonWriteRequestTest extends RealProductListingNoonWriteAdapterTest {
    @Test
    void defaultCreateEndpointUsesCatalogHostAcceptedByCreateServiceAuthentication() {
        assertEquals(
                "https://catalog.noon.partners/_svc/mp-partner-catalog/catalog/product/create",
                ProductListingRealWriteProperties.Endpoints.DEFAULT_CREATE_PRODUCT_URL
        );
    }

    @Test
    void continuationServiceWriteEndpointsUseAuthenticatedCatalogHost() {
        assertEquals(
                "https://catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/sku/cache",
                ProductListingRealWriteProperties.Endpoints.DEFAULT_SKU_CACHE_URL
        );
        assertEquals(
                "https://catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/zsku/upsert",
                ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_ZSKU_URL
        );
        assertEquals(
                "https://catalog.noon.partners/_svc/mp-partner-catalog/catalog/asset/upload",
                ProductListingRealWriteProperties.Endpoints.DEFAULT_UPLOAD_IMAGE_URL
        );
        assertEquals(
                "https://catalog.noon.partners/_svc/mp-partner-catalog/pbarcode/upsert",
                ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_BARCODE_URL
        );
    }

    @Test
    void strictReadbackEndpointsUseProviderVerifiedRoutes() {
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-content/catplat/zsku/retrieve",
                ProductListingRealWriteProperties.Endpoints.DEFAULT_RETRIEVE_ZSKU_URL
        );
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-rocket/offer/list/noon",
                ProductListingRealWriteProperties.Endpoints.DEFAULT_OFFER_LIST_URL
        );
        assertEquals(
                "https://catalog.noon.partners/_svc/mp-pricing-api/pricing/info",
                ProductListingRealWriteProperties.Endpoints.DEFAULT_PRICING_INFORMATION_URL
        );
    }

    @Test
    void realAdapterBuildsExpectedNoonWriteRequests() {
        FakeBindingResolver bindingResolver = new FakeBindingResolver();
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                bindingResolver,
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );

        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setProductDescriptionEn("English long description");
        request.getDraft().setProductDescriptionAr("Arabic long description");
        request.getDraft().setProductHighlightsEn(List.of("Noise cancelling", "USB-C charging"));
        request.getDraft().setProductHighlightsAr(List.of("Arabic noise cancelling"));

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        assertEquals(List.of(
                "create_product",
                "sku_cache",
                "upsert_zsku_base",
                "upload_images",
                "upsert_zsku_content_en",
                "upsert_zsku_content_ar",
                "upsert_price",
                "upsert_offer",
                "upsert_warranty",
                "upsert_barcode",
                "verify_noon_readback"
        ), result.getSteps().stream()
                .map(ProductListingNoonWriteStepResult::getStepKey)
                .collect(Collectors.toList()));
        assertEquals(10002L, bindingResolver.request.getOwnerUserId());
        assertEquals("STR245027-NAE", bindingResolver.request.getStoreCode());
        assertEquals(NoonPullDataDomain.PRODUCT, bindingResolver.request.getDataDomain());
        assertEquals(8, sessionFactory.session.calls.size());
        assertEquals(1, sessionFactory.session.uploadCalls.size());
        assertEquals(1, sessionFactory.session.retrieveCallCount);

        FakeSession.Call createProduct = sessionFactory.session.calls.get(0);
        assertEquals(ProductListingRealWriteProperties.Endpoints.DEFAULT_CREATE_PRODUCT_URL, createProduct.url);
        assertEquals("AE", createProduct.extraHeaders.get("Country-Code"));
        assertEquals("240053", createProduct.extraHeaders.get("Id-Partner"));
        assertEquals("NN-TEST-PSKU", createProduct.body.at("/productCreate/0/variations/0/partnerSku").asText());
        assertEquals(false, createProduct.body.at("/productCreate/0/gated_zsku").asBoolean());

        JsonNode upsertZskuBase = sessionFactory.session.calls.get(2).body;
        assertEquals("ZPARENT", upsertZskuBase.at("/skuParent").asText());
        assertEquals("Generic", upsertZskuBase.at("/attributes/brand").asText());
        assertEquals("Electronic Accessories", upsertZskuBase.at("/attributes/family").asText());
        assertEquals("Headphones", upsertZskuBase.at("/attributes/product_type").asText());
        assertEquals("Wired Headphones", upsertZskuBase.at("/attributes/product_subtype").asText());

        JsonNode contentEn = sessionFactory.session.calls.get(3).body;
        assertEquals("en", contentEn.at("/lang").asText());
        assertEquals("Wired headphones with microphone", contentEn.at("/attributes/product_title").asText());
        assertEquals("English long description", contentEn.at("/attributes/long_description").asText());
        assertEquals("Noise cancelling", contentEn.at("/attributes/feature_bullet_1").asText());
        assertEquals("USB-C charging", contentEn.at("/attributes/feature_bullet_2").asText());
        assertEquals("noon-uploaded/sku-main.jpg", contentEn.at("/attributes/image_url_1").asText());

        JsonNode contentAr = sessionFactory.session.calls.get(4).body;
        assertEquals("ar", contentAr.at("/lang").asText());
        assertEquals("Arabic wired headphones title", contentAr.at("/attributes/product_title").asText());
        assertEquals("Arabic long description", contentAr.at("/attributes/long_description").asText());
        assertEquals("Arabic noise cancelling", contentAr.at("/attributes/feature_bullet_1").asText());

        FakeSession.UploadCall uploadImage = sessionFactory.session.uploadCalls.get(0);
        assertEquals(ProductListingRealWriteProperties.Endpoints.DEFAULT_UPLOAD_IMAGE_URL, uploadImage.url);
        assertEquals("file", uploadImage.fieldName);
        assertEquals("sku-main.jpg", uploadImage.fileName);
        assertEquals("image/jpeg", uploadImage.contentType);

        JsonNode price = sessionFactory.session.calls.get(5).body;
        assertEquals("PSKU_CODE_1", price.at("/pskuCode").asText());
        assertEquals("NN-TEST-PSKU", price.at("/partnerSku").asText());
        assertEquals("manual", price.at("/pricingMethod").asText());

        ProductListingNoonWriteStepResult offer = result.getSteps().get(7);
        assertEquals("upsert_offer", offer.getStepKey());
        assertEquals("skipped", offer.getStatus());
        assertEquals("noon_offer_stock_write_not_enabled", offer.getFailureCode());

        JsonNode warranty = sessionFactory.session.calls.get(6).body;
        assertEquals("PSKU_CODE_1", warranty.at("/pskuCode").asText());
        assertEquals("NN-TEST-PSKU", warranty.at("/partnerSku").asText());
        assertEquals(24, warranty.at("/idWarranty").asInt());

        JsonNode barcode = sessionFactory.session.calls.get(7).body;
        assertEquals("NN-TEST-PSKU", barcode.at("/pbarcodeUpsert/0/partnerSku").asText());
        assertEquals("6290000000001", barcode.at("/pbarcodeUpsert/0/partnerBarcode").asText());
        assertFalse(barcode.at("/forceMapping").asBoolean());
    }

}
