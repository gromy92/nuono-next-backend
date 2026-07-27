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

abstract class RealProductListingNoonWriteAdapterTest {
    protected ProductListingNoonWriteRequest writeRequest() {
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(10002L);
        request.setStoreCode("STR245027-NAE");
        request.setDraftId(10001L);
        request.setDryRunTaskId(20001L);
        request.setRealRunTaskId(20002L);
        request.setSubmittedBy(90001L);
        request.setDraft(ProductListingTestFixtures.validCommand());
        request.setConfirmation(ProductListingTestFixtures.confirmedCommand());
        return request;
    }

    protected static class FakeBindingResolver extends NoonPullStoreBindingResolver {
        NoonInterfacePullRequest request;
        RuntimeException failure;

        FakeBindingResolver() {
            super(null);
        }

        @Override
        public NoonPullStoreBinding resolve(NoonInterfacePullRequest request) {
            this.request = request;
            if (failure != null) {
                throw failure;
            }
            return new NoonPullStoreBinding(
                    request.getOwnerUserId(),
                    "PRJ240053",
                    request.getStoreCode(),
                    "AE",
                    "240053",
                    "merchant@example.test",
                    "secret",
                    null,
                    "sid=test"
            );
        }
    }

    protected static class FakeSessionFactory implements NoonPullGatewaySessionFactory {
        final FakeSession session = new FakeSession();

        @Override
        public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
            return session;
        }
    }

    protected static class FakeSession implements NoonPullGatewaySession {
        final ObjectMapper objectMapper = new ObjectMapper();
        final List<Call> calls = new ArrayList<>();
        final List<UploadCall> uploadCalls = new ArrayList<>();
        int retrieveCallCount;
        int readBackImagesAvailableAfterAttempt = 1;
        boolean baseUpsertReturnsInvalid;
        boolean createReturnsInvalid;
        int zskuUpsertCount;
        String readBackBrand = "Generic";
        String readBackProductFullType = "electronic_accessories-headphones-wired_headphones";
        String readBackProductFullTypeCode;
        String taxonomyProductFullTypeCode = "electronic_accessories-headphones-wired_headphones";
        String taxonomyFamilyNameEn = "Electronic Accessories";
        String taxonomyProductTypeNameEn = "Headphones";
        String taxonomyProductSubTypeNameEn = "Wired Headphones";
        boolean failOnIdProductFullTypeLookup;
        RuntimeException taxonomyFailure;
        boolean failCreateTransport;
        boolean failCreateAuthentication;
        boolean createResponseMissingReferences;
        boolean offerListContainsProduct;
        boolean failOfferListAuthentication;
        int offerListCallCount;
        boolean standaloneReadBackSeeded;
        boolean omitReadBackRichContent;
        boolean omitReadBackBarcode;
        boolean omitReadBackPricing;
        String readBackOfferNote;
        Boolean readBackIsActive;

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            if (ProductListingRealWriteProperties.Endpoints.DEFAULT_OFFER_LIST_URL.equals(url)) {
                offerListCallCount++;
                if (failOfferListAuthentication) {
                    throw new NoonHttpException(
                            307,
                            "Catalog session expired before create",
                            url
                    );
                }
                ObjectNode root = objectMapper.createObjectNode();
                ObjectNode data = root.putObject("data");
                ArrayNode hits = data.putArray("hits");
                if (offerListContainsProduct || standaloneReadBackSeeded || hasWriteCall(
                        ProductListingRealWriteProperties.Endpoints.DEFAULT_CREATE_PRODUCT_URL
                )) {
                    ObjectNode hit = hits.addObject()
                            .put("partner_sku", "NN-TEST-PSKU")
                            .put("zsku_parent", "ZPARENT")
                            .put("psku_code", "PSKU_CODE_1");
                    JsonNode barcodeBody = latestWriteBody(
                            ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_BARCODE_URL
                    );
                    if (!omitReadBackBarcode && barcodeBody != null
                            && barcodeBody.at("/pbarcodeUpsert/0/partnerBarcode").isTextual()) {
                        hit.putArray("partner_barcodes").add(
                                barcodeBody.at("/pbarcodeUpsert/0/partnerBarcode").asText()
                        );
                    } else if (!omitReadBackBarcode && standaloneReadBackSeeded) {
                        hit.putArray("partner_barcodes").add("6290000000001");
                    }
                }
                data.put("total", hits.size());
                return root;
            }
            if (ProductListingRealWriteProperties.Endpoints.DEFAULT_PRICING_INFORMATION_URL.equals(url)) {
                ObjectNode root = objectMapper.createObjectNode();
                ObjectNode pricing = root.putArray("data").addObject();
                if (!omitReadBackPricing) {
                    copyFields(
                            latestWriteBody(
                                    ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_PRICE_URL
                            ),
                            pricing
                    );
                    copyFields(
                            latestWriteBody(
                                    ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_WARRANTY_URL
                            ),
                            pricing
                    );
                    copyFields(
                            latestWriteBody(
                                    ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_OFFER_NOTE_URL
                            ),
                            pricing
                    );
                    copyFields(
                            latestWriteBody(
                                    ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_IS_ACTIVE_URL
                            ),
                            pricing
                    );
                }
                if (readBackOfferNote != null) {
                    pricing.put("offer_note", readBackOfferNote);
                }
                if (readBackIsActive != null) {
                    pricing.put("is_active", readBackIsActive);
                }
                if (!omitReadBackPricing && standaloneReadBackSeeded) {
                    pricing.put("price", "49.90");
                    pricing.put("id_warranty", 24);
                }
                return root;
            }
            retrieveCallCount++;
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode product = root.putObject("ZPARENT");
            ObjectNode attributes = product.putObject("attributes");
            ObjectNode common = attributes.putObject("common");
            common.put("brand", readBackBrand);
            if (readBackProductFullType != null) {
                common.put("product_fulltype", readBackProductFullType);
            }
            if (readBackProductFullTypeCode != null) {
                common.put("product_fulltype_code", readBackProductFullTypeCode);
            }
            if (retrieveCallCount >= readBackImagesAvailableAfterAttempt) {
                JsonNode contentEn = latestContentBody("en");
                if (contentEn != null) {
                    contentEn.path("attributes").fields().forEachRemaining(entry -> {
                        if (entry.getKey().startsWith("image_url_")) {
                            common.set(entry.getKey(), entry.getValue());
                        }
                    });
                } else if (standaloneReadBackSeeded) {
                    common.put("image_url_1", "noon-uploaded/sku-main.jpg");
                }
            }
            ObjectNode en = attributes.putObject("en");
            copyFields(
                    latestContentBody("en") == null
                            ? null
                            : latestContentBody("en").path("attributes"),
                    en
            );
            if (standaloneReadBackSeeded) {
                en.put("product_title", "Wired headphones with microphone");
            }
            if (omitReadBackRichContent) {
                en.remove(List.of(
                        "long_description",
                        "feature_bullet_1",
                        "base_material"
                ));
            }
            ObjectNode ar = attributes.putObject("ar");
            copyFields(
                    latestContentBody("ar") == null
                            ? null
                            : latestContentBody("ar").path("attributes"),
                    ar
            );
            if (standaloneReadBackSeeded) {
                ar.put("product_title", "Arabic wired headphones title");
            }
            if (omitReadBackRichContent) {
                ar.remove(List.of(
                        "long_description",
                        "feature_bullet_1",
                        "base_material"
                ));
            }
            return root;
        }

        boolean hasWriteCall(String url) {
            return latestWriteBody(url) != null;
        }

        JsonNode latestWriteBody(String url) {
            for (int index = calls.size() - 1; index >= 0; index--) {
                Call call = calls.get(index);
                if (url.equals(call.url)) {
                    return call.body;
                }
            }
            return null;
        }

        JsonNode latestContentBody(String lang) {
            for (int index = calls.size() - 1; index >= 0; index--) {
                Call call = calls.get(index);
                if (ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_ZSKU_URL.equals(
                        call.url
                )
                        && lang.equals(call.body.path("lang").asText())
                        && call.body.path("attributes").has("product_title")) {
                    return call.body;
                }
            }
            return null;
        }

        void copyFields(JsonNode source, ObjectNode target) {
            if (source == null || !source.isObject()) {
                return;
            }
            source.fields().forEachRemaining(entry ->
                    target.set(toSnakeCase(entry.getKey()), entry.getValue()));
        }

        String toSnakeCase(String value) {
            return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        }

        @Override
        public JsonNode postWriteJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            calls.add(new Call(url, body, withProject, extraHeaders));
            if (ProductListingRealWriteProperties.Endpoints.DEFAULT_CREATE_PRODUCT_URL.equals(url)) {
                if (failCreateAuthentication) {
                    throw new NoonHttpException(
                            307,
                            "authentication expired after request write",
                            url
                    );
                }
                if (failCreateTransport) {
                    throw new IllegalStateException("connection reset after request write");
                }
                ObjectNode response = objectMapper.createObjectNode();
                if (createReturnsInvalid) {
                    response.put("invalid", 1);
                    response.putObject("error")
                            .put("partner_error", "create payload rejected");
                    return response;
                }
                if (createResponseMissingReferences) {
                    return response;
                }
                ArrayNode products = response.putArray("products");
                ObjectNode product = products.addObject();
                product.set("parent", objectMapper.createObjectNode().put("skuParent", "ZPARENT"));
                product.putArray("children").addObject().put("pskuCode", "PSKU_CODE_1");
                return response;
            }
            if (ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_ZSKU_URL.equals(url)) {
                zskuUpsertCount++;
                if (baseUpsertReturnsInvalid && zskuUpsertCount == 1) {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("invalid", 1);
                    response.putObject("error").put("partner_error", "fulltype is invalid");
                    return response;
                }
            }
            return objectMapper.createObjectNode();
        }

        @Override
        public JsonNode postMultipartFile(
                String url,
                String fieldName,
                String fileName,
                String contentType,
                byte[] content,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            uploadCalls.add(new UploadCall(url, fieldName, fileName, contentType, content, withProject, extraHeaders));
            ObjectNode response = objectMapper.createObjectNode();
            response.put("upload_path", "noon-uploaded/sku-main.jpg");
            return response;
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            if (taxonomyFailure != null) {
                throw taxonomyFailure;
            }
            if (failOnIdProductFullTypeLookup && url != null && url.contains("id_product_fulltype")) {
                throw new AssertionError("stale id_product_fulltype lookup should not be used");
            }
            return ("{"
                    + "\"data\":[{"
                    + "\"id_product_fulltype\":3066,"
                    + "\"product_fulltype_code\":\"" + taxonomyProductFullTypeCode + "\","
                    + "\"family_name_en\":\"" + taxonomyFamilyNameEn + "\","
                    + "\"product_type_name_en\":\"" + taxonomyProductTypeNameEn + "\","
                    + "\"product_subtype_name_en\":\"" + taxonomyProductSubTypeNameEn + "\""
                    + "}]"
                    + "}").getBytes();
        }

        static class Call {
            final String url;
            final JsonNode body;
            final boolean withProject;
            final Map<String, String> extraHeaders;

            Call(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
                this.url = url;
                this.body = body;
                this.withProject = withProject;
                this.extraHeaders = extraHeaders;
            }
        }

        static class UploadCall {
            final String url;
            final String fieldName;
            final String fileName;
            final String contentType;
            final byte[] content;
            final boolean withProject;
            final Map<String, String> extraHeaders;

            UploadCall(
                    String url,
                    String fieldName,
                    String fileName,
                    String contentType,
                    byte[] content,
                    boolean withProject,
                    Map<String, String> extraHeaders
            ) {
                this.url = url;
                this.fieldName = fieldName;
                this.fileName = fileName;
                this.contentType = contentType;
                this.content = content;
                this.withProject = withProject;
                this.extraHeaders = extraHeaders;
            }
        }
    }

    protected static class FakeImageDownloader implements ProductListingImageDownloader {
        @Override
        public ProductListingImageDownload download(String imageUrl) {
            return new ProductListingImageDownload(
                    "sku-main.jpg",
                    "image/jpeg",
                    new byte[] {1, 2, 3}
            );
        }
    }

    protected static class FakeOfferStockWriteAdapter implements ProductListingOfferStockWriteAdapter {
        int callCount;
        ProductListingOfferStockWriteRequest request;

        @Override
        public ProductListingNoonWriteStepResult writeOfferStock(
                ProductListingOfferStockWriteRequest request,
                NoonPullGatewaySession session,
                ProductListingRealWriteProperties.Endpoints endpoints,
                Map<String, String> headers
        ) {
            callCount++;
            this.request = request;
            ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
            step.setStepKey("upsert_offer");
            step.setStatus("succeeded");
            step.setExternalReference("offerStockAdapter=called");
            return step;
        }
    }
}
