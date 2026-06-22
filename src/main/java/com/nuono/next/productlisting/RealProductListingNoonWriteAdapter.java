package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Primary
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(prefix = "nuono.product-listing.real-write", name = "enabled", havingValue = "true")
public class RealProductListingNoonWriteAdapter implements ProductListingNoonWriteAdapter {
    private static final int MAX_IMAGE_UPLOADS = 15;

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final ProductListingRealWriteProperties properties;
    private final ProductListingImageDownloader imageDownloader;

    @Autowired
    public RealProductListingNoonWriteAdapter(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            ProductListingRealWriteProperties properties
    ) {
        this(objectMapper, bindingResolver, sessionFactory, properties, new HttpClientProductListingImageDownloader());
    }

    RealProductListingNoonWriteAdapter(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            ProductListingRealWriteProperties properties,
            ProductListingImageDownloader imageDownloader
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        this.properties = properties == null ? new ProductListingRealWriteProperties() : properties;
        this.imageDownloader = imageDownloader == null
                ? new HttpClientProductListingImageDownloader()
                : imageDownloader;
    }

    @Override
    public ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        try {
            ProductListingDraftCommand draft = requireDraft(request);
            NoonPullStoreBinding binding = bindingResolver.resolve(interfaceRequest(request));
            NoonPullGatewaySession session = sessionFactory.login(binding);
            Map<String, String> headers = ProductListingNoonHeaders.writeHeaders(binding);
            ProductListingRealWriteProperties.Endpoints endpoints = properties.getEndpoints();
            ProductFullTypeLabels fullTypeLabels = resolveProductFullTypeLabels(session, endpoints, draft, headers);

            JsonNode createProduct = postStep(
                    steps,
                    "create_product",
                    session,
                    endpoints.getCreateProductUrl(),
                    createProductBody(draft),
                    headers
            );
            String skuParent = requiredText(createProduct, "/products/0/parent/skuParent", "skuParent");
            String pskuCode = requiredText(createProduct, "/products/0/children/0/pskuCode", "pskuCode");
            setLastStepExternalReference(steps, externalReference(skuParent, pskuCode));

            postStep(steps, "sku_cache", session, endpoints.getSkuCacheUrl(), skuCacheBody(skuParent), headers);
            postStep(
                    steps,
                    "upsert_zsku_base",
                    session,
                    endpoints.getUpsertZskuUrl(),
                    upsertZskuBaseBody(draft, fullTypeLabels, skuParent),
                    headers
            );
            List<String> uploadedImagePaths = uploadImages(steps, session, endpoints, draft, headers);
            postZskuContentStep(steps, "upsert_zsku_content_en", session, endpoints, draft, uploadedImagePaths, skuParent, "en", headers);
            postZskuContentStep(steps, "upsert_zsku_content_ar", session, endpoints, draft, uploadedImagePaths, skuParent, "ar", headers);
            postStep(steps, "upsert_price", session, endpoints.getUpsertPriceUrl(), upsertPriceBody(request, draft, binding, pskuCode), headers);
            setLastStepExternalReference(steps, externalReference(skuParent, pskuCode));
            if (properties.isOfferUpsertEnabled()) {
                appendUnsupportedOfferStep(steps);
            }
            if (draft.getIdWarranty() != null) {
                postStep(
                        steps,
                        "upsert_warranty",
                        session,
                        endpoints.getUpsertWarrantyUrl(),
                        upsertWarrantyBody(request, draft, binding, pskuCode),
                        headers
                );
            }
            if (StringUtils.hasText(draft.getBarcode())) {
                postStep(steps, "upsert_barcode", session, endpoints.getUpsertBarcodeUrl(), upsertBarcodeBody(draft), headers);
            }

            ProductListingNoonWriteStepResult readBack = verifyNoonReadBack(
                    session,
                    endpoints,
                    draft,
                    uploadedImagePaths,
                    skuParent,
                    pskuCode,
                    headers
            );
            steps.add(readBack);
            if (!"succeeded".equals(readBack.getStatus())) {
                return ProductListingNoonWriteResult.failed(
                        "noon_readback",
                        readBack.getFailureCode(),
                        readBack.getFailureMessage(),
                        steps
                );
            }

            return ProductListingNoonWriteResult.succeeded(steps);
        } catch (RuntimeException exception) {
            return ProductListingNoonWriteResult.failed(
                    "noon_api",
                    "noon_write_failed",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Product listing Noon write failed.",
                    steps
            );
        }
    }

    private void appendUnsupportedOfferStep(List<ProductListingNoonWriteStepResult> steps) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("upsert_offer");
        step.setStatus("skipped");
        step.setFailureCode("noon_offer_upsert_not_supported_for_new_listing");
        step.setFailureMessage("Noon offer/upsert is not part of the legacy create SKU chain for newly created PSKUs; "
                + "offer and stock writes remain disabled until a proven Noon endpoint is integrated.");
        steps.add(step);
    }

    private ProductListingNoonWriteStepResult verifyNoonReadBack(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            List<String> expectedImageValues,
            String skuParent,
            String pskuCode,
            Map<String, String> headers
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        int maxAttempts = Math.max(1, properties.getReadBackMaxAttempts());
        long retryDelayMillis = Math.max(0L, properties.getReadBackRetryDelayMillis());
        RuntimeException lastException = null;
        List<String> lastMismatchedFields = List.of();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            step.setExternalReference(externalReference(skuParent, pskuCode) + ";readBackAttempts=" + attempt);
            try {
                JsonNode root = session.postJson(endpoints.getRetrieveZskuUrl(), retrieveZskuBody(skuParent), true, headers);
                JsonNode product = root.path(skuParent);
                JsonNode attributes = product.path("attributes");
                JsonNode common = attributes.path("common");
                JsonNode en = attributes.path("en");
                JsonNode ar = attributes.path("ar");
                List<String> mismatchedFields = mismatchedReadBackFields(draft, expectedImageValues, common, en, ar);
                if (mismatchedFields.isEmpty()) {
                    step.setStatus("succeeded");
                    return step;
                }
                lastMismatchedFields = mismatchedFields;
                lastException = null;
            } catch (RuntimeException exception) {
                lastException = exception;
                lastMismatchedFields = List.of();
            }
            if (attempt < maxAttempts) {
                sleepBeforeReadBackRetry(retryDelayMillis);
            }
        }
        if (!lastMismatchedFields.isEmpty()) {
            step.setStatus("failed");
            step.setFailureCode("noon_listing_readback_incomplete");
            step.setFailureMessage("Noon listing read-back missing or mismatched fields: "
                    + String.join(", ", lastMismatchedFields));
            return step;
        }
        step.setStatus("failed");
        step.setFailureCode("noon_listing_readback_failed");
        step.setFailureMessage(lastException != null && StringUtils.hasText(lastException.getMessage())
                ? lastException.getMessage()
                : "Noon listing read-back failed.");
        return step;
    }

    private void sleepBeforeReadBackRetry(long retryDelayMillis) {
        if (retryDelayMillis <= 0L) {
            return;
        }
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Noon listing read-back retry interrupted: " + exception.getMessage(), exception);
        }
    }

    private List<String> mismatchedReadBackFields(
            ProductListingDraftCommand draft,
            List<String> expectedImageValues,
            JsonNode common,
            JsonNode en,
            JsonNode ar
    ) {
        List<String> fields = new ArrayList<>();
        requireReadBackText(
                fields,
                "brand",
                firstNonBlank(draft.getProductBrand(), draft.getProductBrandCode()),
                text(common, "brand"),
                true
        );
        requireReadBackText(
                fields,
                "product_fulltype",
                draft.getProductFullType(),
                text(common, "product_fulltype"),
                false
        );
        requireReadBackText(
                fields,
                "product_title_en",
                draft.getProductTitleEn(),
                text(en, "product_title"),
                false
        );
        requireReadBackText(
                fields,
                "product_title_ar",
                draft.getProductTitleAr(),
                text(ar, "product_title"),
                false
        );
        List<String> expectedImages = expectedImageValues == null ? List.of() : expectedImageValues;
        int expectedIndex = 1;
        for (String expectedImage : expectedImages) {
            if (!StringUtils.hasText(expectedImage)) {
                continue;
            }
            String actualImage = text(common, "image_url_" + expectedIndex);
            if (!sameText(expectedImage, actualImage, false)) {
                fields.add("image_url_" + expectedIndex);
            }
            expectedIndex++;
            if (expectedIndex > 15) {
                break;
            }
        }
        return fields;
    }

    private void requireReadBackText(
            List<String> fields,
            String field,
            String expected,
            String actual,
            boolean ignoreCase
    ) {
        if (!StringUtils.hasText(expected)) {
            return;
        }
        if (!sameText(expected, actual, ignoreCase)) {
            fields.add(field);
        }
    }

    private boolean sameText(String expected, String actual, boolean ignoreCase) {
        String normalizedExpected = normalize(expected);
        String normalizedActual = normalize(actual);
        if (!StringUtils.hasText(normalizedExpected)) {
            return true;
        }
        if (!StringUtils.hasText(normalizedActual)) {
            return false;
        }
        return ignoreCase
                ? normalizedExpected.equalsIgnoreCase(normalizedActual)
                : normalizedExpected.equals(normalizedActual);
    }

    private ProductListingDraftCommand requireDraft(ProductListingNoonWriteRequest request) {
        if (request == null || request.getDraft() == null) {
            throw new IllegalArgumentException("Product listing Noon write request draft is required.");
        }
        if (!StringUtils.hasText(request.getDraft().getPsku())) {
            throw new IllegalArgumentException("Product listing Noon write request PSKU is required.");
        }
        return request.getDraft();
    }

    private NoonInterfacePullRequest interfaceRequest(ProductListingNoonWriteRequest request) {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(request.getOwnerUserId())
                .storeCode(request.getStoreCode())
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .requestName("product-listing-real-run")
                .targetIdentity(request.getDraft().getPsku())
                .requestSummary("product listing real-run task " + request.getRealRunTaskId())
                .build();
    }

    private JsonNode postStep(
            List<ProductListingNoonWriteStepResult> steps,
            String stepKey,
            NoonPullGatewaySession session,
            String url,
            JsonNode body,
            Map<String, String> headers
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey(stepKey);
        boolean stepRecorded = false;
        try {
            JsonNode response = session.postWriteJson(url, body, true, headers);
            String failureMessage = noonWriteFailureMessage(response);
            if (StringUtils.hasText(failureMessage)) {
                step.setStatus("failed");
                step.setFailureCode("noon_write_failed");
                step.setFailureMessage(failureMessage);
                steps.add(step);
                stepRecorded = true;
                throw new IllegalStateException(failureMessage);
            }
            step.setStatus("succeeded");
            steps.add(step);
            stepRecorded = true;
            return response == null ? objectMapper.createObjectNode() : response;
        } catch (RuntimeException exception) {
            if (!stepRecorded) {
                step.setStatus("failed");
                step.setFailureCode("noon_write_failed");
                step.setFailureMessage(exception.getMessage());
                steps.add(step);
            }
            throw exception;
        }
    }

    private List<String> uploadImages(
            List<ProductListingNoonWriteStepResult> steps,
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            Map<String, String> headers
    ) {
        List<String> sourceImages = draft.getImageUrls() == null ? List.of() : draft.getImageUrls();
        List<String> uploadedPaths = new ArrayList<>();
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("upload_images");
        try {
            for (String sourceImage : sourceImages) {
                if (!StringUtils.hasText(sourceImage)) {
                    continue;
                }
                if (uploadedPaths.size() >= MAX_IMAGE_UPLOADS) {
                    break;
                }
                ProductListingImageDownload download = imageDownloader.download(sourceImage.trim());
                JsonNode response = session.postMultipartFile(
                        endpoints.getUploadImageUrl(),
                        "file",
                        download.fileName,
                        download.contentType,
                        download.content,
                        true,
                        headers
                );
                String failureMessage = noonWriteFailureMessage(response);
                if (StringUtils.hasText(failureMessage)) {
                    throw new IllegalStateException(failureMessage);
                }
                String uploadPath = firstNonBlank(
                        text(response, "upload_path"),
                        text(response, "uploadPath"),
                        text(response, "path"),
                        text(response, "url")
                );
                if (!StringUtils.hasText(uploadPath)) {
                    throw new IllegalStateException("Noon image upload response missing upload_path.");
                }
                uploadedPaths.add(uploadPath);
            }
            step.setStatus("succeeded");
            step.setExternalReference("uploadedImages=" + uploadedPaths.size());
            steps.add(step);
            return uploadedPaths;
        } catch (RuntimeException exception) {
            step.setStatus("failed");
            step.setFailureCode("noon_image_upload_failed");
            step.setFailureMessage(StringUtils.hasText(exception.getMessage())
                    ? exception.getMessage()
                    : "Noon image upload failed.");
            step.setExternalReference("uploadedImages=" + uploadedPaths.size());
            steps.add(step);
            throw exception;
        }
    }

    private ObjectNode createProductBody(ProductListingDraftCommand draft) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode products = root.putArray("productCreate");
        ObjectNode product = products.addObject();
        product.set("parent", objectMapper.createObjectNode());
        product.put("gated_zsku", false);
        product.putArray("variations").addObject().put("partnerSku", draft.getPsku());
        return root;
    }

    private ObjectNode skuCacheBody(String skuParent) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("skuParent", skuParent);
        return root;
    }

    private ObjectNode retrieveZskuBody(String skuParent) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("skuParents").add(skuParent);
        root.putArray("attributeCodes");
        return root;
    }

    private ObjectNode upsertZskuBaseBody(
            ProductListingDraftCommand draft,
            ProductFullTypeLabels fullTypeLabels,
            String skuParent
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("skuParent", skuParent);
        root.putArray("variants");
        root.put("lang", "en");
        ObjectNode attributes = root.putObject("attributes");
        putIfHasText(attributes, "brand", firstNonBlank(draft.getProductBrand(), draft.getProductBrandCode(), "Generic"));
        putIfHasText(attributes, "family", fullTypeLabels.family);
        putIfHasText(attributes, "product_type", fullTypeLabels.productType);
        putIfHasText(attributes, "product_subtype", fullTypeLabels.productSubType);
        attributes.put("item_condition", "new");
        attributes.put("update_fulltype", "True");
        return root;
    }

    private void postZskuContentStep(
            List<ProductListingNoonWriteStepResult> steps,
            String stepKey,
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            List<String> imageAttributeValues,
            String skuParent,
            String lang,
            Map<String, String> headers
    ) {
        ObjectNode body = upsertZskuContentBody(draft, imageAttributeValues, skuParent, lang);
        if (body.path("attributes").size() == 0) {
            return;
        }
        postStep(steps, stepKey, session, endpoints.getUpsertZskuUrl(), body, headers);
    }

    private ObjectNode upsertZskuContentBody(
            ProductListingDraftCommand draft,
            List<String> imageAttributeValues,
            String skuParent,
            String lang
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("skuParent", skuParent);
        root.putArray("variants");
        root.put("lang", lang);
        ObjectNode attributes = root.putObject("attributes");
        if ("ar".equals(lang)) {
            putIfHasText(attributes, "product_title", draft.getProductTitleAr());
            return root;
        }

        attributes.put("grade", "new");
        putIfHasText(attributes, "product_title", draft.getProductTitleEn());
        if (imageAttributeValues != null) {
            int index = 1;
            for (String imageUrl : imageAttributeValues) {
                if (!StringUtils.hasText(imageUrl)) {
                    continue;
                }
                putIfHasText(attributes, "image_url_" + index, imageUrl);
                index++;
                if (index > MAX_IMAGE_UPLOADS) {
                    break;
                }
            }
        }
        return root;
    }

    private ObjectNode upsertPriceBody(
            ProductListingNoonWriteRequest request,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            String pskuCode
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("pskuCode", pskuCode);
        root.put("partnerSku", draft.getPsku());
        root.put("countryCode", upper(binding.getSiteCode()));
        root.put("pricingMethod", "manual");
        root.put("updatedBy", request.getSubmittedBy() == null ? "" : String.valueOf(request.getSubmittedBy()));
        if (draft.getPrice() != null) {
            root.put("price", draft.getPrice());
        }
        return root;
    }

    private ObjectNode upsertWarrantyBody(
            ProductListingNoonWriteRequest request,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            String pskuCode
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("pskuCode", pskuCode);
        root.put("idWarranty", draft.getIdWarranty());
        root.put("countryCode", upper(binding.getSiteCode()));
        root.put("baseCountryCode", upper(binding.getSiteCode()));
        root.put("updatedBy", request.getSubmittedBy() == null ? "" : String.valueOf(request.getSubmittedBy()));
        return root;
    }

    private ObjectNode upsertBarcodeBody(ProductListingDraftCommand draft) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode barcode = root.putArray("barcodeReqList").addObject();
        barcode.put("partnerSku", draft.getPsku());
        barcode.put("partnerBarcode", draft.getBarcode());
        root.put("forceMapping", true);
        return root;
    }

    private ProductFullTypeParts productFullTypeParts(ProductListingDraftCommand draft) {
        String[] parts = StringUtils.hasText(draft.getProductFullType())
                ? draft.getProductFullType().split("-")
                : new String[0];
        String family = firstNonBlank(draft.getFamily(), parts.length > 0 ? parts[0] : null);
        String productType = firstNonBlank(draft.getProductType(), parts.length > 1 ? parts[1] : null);
        String productSubType = firstNonBlank(draft.getProductSubType(), parts.length > 2 ? parts[2] : null);
        return new ProductFullTypeParts(family, productType, productSubType);
    }

    private ProductFullTypeLabels resolveProductFullTypeLabels(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            Map<String, String> headers
    ) {
        ProductFullTypeParts parts = productFullTypeParts(draft);
        if (parts.looksLikeLabels()) {
            return new ProductFullTypeLabels(parts.family, parts.productType, parts.productSubType);
        }
        ProductFullTypeLabels labels = fetchProductFullTypeLabels(session, endpoints, draft, headers);
        if (labels.complete()) {
            return labels;
        }
        throw new IllegalStateException("Noon product fulltype taxonomy labels missing for "
                + normalize(draft.getProductFullType()) + ".");
    }

    private ProductFullTypeLabels fetchProductFullTypeLabels(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            Map<String, String> headers
    ) {
        List<String> urls = productFullTypeLookupUrls(endpoints, draft);
        ProductFullTypeLabels bestPartial = ProductFullTypeLabels.empty();
        for (String url : urls) {
            try {
                byte[] bytes = session.getBytes(url, false, taxonomyHeaders(headers));
                JsonNode root = objectMapper.readTree(bytes);
                ProductFullTypeLabels labels = labelsFromTaxonomy(root, draft);
                if (labels.complete()) {
                    return labels;
                }
                if (!bestPartial.hasAny() && labels.hasAny()) {
                    bestPartial = labels;
                }
            } catch (Exception ignored) {
                // Try the next taxonomy endpoint before failing the real write.
            }
        }
        return bestPartial;
    }

    private List<String> productFullTypeLookupUrls(
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft
    ) {
        List<String> urls = new ArrayList<>();
        String suggestUrl = normalize(endpoints.getProductFulltypeSuggestUrl());
        if (StringUtils.hasText(suggestUrl)) {
            if (draft.getIdProductFullType() != null) {
                addUrl(urls, appendQuery(suggestUrl, "id_product_fulltype", String.valueOf(draft.getIdProductFullType())));
            }
            if (StringUtils.hasText(draft.getProductFullType())) {
                addUrl(urls, appendQuery(suggestUrl, "query", draft.getProductFullType()));
                ProductFullTypeParts parts = productFullTypeParts(draft);
                addSuggestQuery(urls, suggestUrl, humanizeCode(parts.productSubType));
                addSuggestQuery(urls, suggestUrl, humanizeCode(parts.productType));
                addSuggestQuery(urls, suggestUrl, humanizeCode(parts.family));
            }
        }
        String taxonomyUrl = normalize(endpoints.getProductFulltypeTaxonomyUrl());
        if (StringUtils.hasText(taxonomyUrl)) {
            urls.add(taxonomyUrl);
        }
        return urls;
    }

    private void addSuggestQuery(List<String> urls, String suggestUrl, String query) {
        if (StringUtils.hasText(query)) {
            addUrl(urls, appendQuery(suggestUrl, "query", query));
        }
    }

    private void addUrl(List<String> urls, String url) {
        if (StringUtils.hasText(url) && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private String humanizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace('_', ' ');
    }

    private String appendQuery(String baseUrl, String key, String value) {
        String separator = baseUrl.contains("?")
                ? baseUrl.endsWith("?") || baseUrl.endsWith("&") ? "" : "&"
                : "?";
        return baseUrl + separator + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> taxonomyHeaders(Map<String, String> headers) {
        return headers == null
                ? Map.of("Accept", "application/json")
                : Map.of(
                "Accept", "application/json",
                "x-project", firstNonBlank(headers.get("x-project"), headers.get("X-Project")),
                "x-locale", firstNonBlank(headers.get("x-locale"), headers.get("X-Locale")),
                "Country-Code", firstNonBlank(headers.get("Country-Code"), headers.get("country-code")),
                "Id-Partner", firstNonBlank(headers.get("Id-Partner"), headers.get("id-partner"))
        );
    }

    private ProductFullTypeLabels labelsFromTaxonomy(JsonNode root, ProductListingDraftCommand draft) {
        List<JsonNode> candidates = new ArrayList<>();
        collectTaxonomyCandidates(root, candidates);
        for (JsonNode candidate : candidates) {
            if (!matchesProductFullType(candidate, draft)) {
                continue;
            }
            return new ProductFullTypeLabels(
                    firstNonBlank(text(candidate, "family_name_en"), text(candidate, "familyNameEn")),
                    firstNonBlank(text(candidate, "product_type_name_en"), text(candidate, "productTypeNameEn")),
                    firstNonBlank(text(candidate, "product_subtype_name_en"), text(candidate, "productSubtypeNameEn"))
            );
        }
        return ProductFullTypeLabels.empty();
    }

    private String noonWriteFailureMessage(JsonNode response) {
        if (response == null || response.isNull() || response.isMissingNode()) {
            return "";
        }
        int invalid = response.path("invalid").asInt(0);
        JsonNode error = response.path("error");
        if (invalid > 0 || hasNonEmptyError(error)) {
            return "Noon write response contains business error: " + (hasNonEmptyError(error)
                    ? error.toString()
                    : response.toString());
        }
        return "";
    }

    private boolean hasNonEmptyError(JsonNode error) {
        if (error == null || error.isMissingNode() || error.isNull()) {
            return false;
        }
        if (error.isObject() || error.isArray()) {
            return error.size() > 0;
        }
        return StringUtils.hasText(error.asText(""));
    }

    private void collectTaxonomyCandidates(JsonNode node, List<JsonNode> candidates) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            if (hasTaxonomyIdentity(node)) {
                candidates.add(node);
            }
            node.fields().forEachRemaining(entry -> collectTaxonomyCandidates(entry.getValue(), candidates));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectTaxonomyCandidates(child, candidates));
        }
    }

    private boolean hasTaxonomyIdentity(JsonNode node) {
        return StringUtils.hasText(firstNonBlank(
                text(node, "product_fulltype_code"),
                text(node, "productFulltypeCode"),
                text(node, "id_product_fulltype"),
                text(node, "idProductFulltype")
        ));
    }

    private boolean matchesProductFullType(JsonNode candidate, ProductListingDraftCommand draft) {
        String expectedFullType = normalize(draft.getProductFullType());
        if (StringUtils.hasText(expectedFullType)) {
            String actualFullType = firstNonBlank(
                    text(candidate, "product_fulltype_code"),
                    text(candidate, "productFulltypeCode"),
                    text(candidate, "product_fulltype"),
                    text(candidate, "productFulltype")
            );
            if (sameText(expectedFullType, actualFullType, false)) {
                return true;
            }
        }
        if (draft.getIdProductFullType() == null) {
            return false;
        }
        String expectedId = String.valueOf(draft.getIdProductFullType());
        String actualId = firstNonBlank(
                text(candidate, "id_product_fulltype"),
                text(candidate, "idProductFulltype"),
                text(candidate, "idProductFullType")
        );
        return expectedId.equals(actualId);
    }

    private String requiredText(JsonNode node, String pointer, String label) {
        String value = node == null ? null : node.at(pointer).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Noon create_product response missing " + label + ".");
        }
        return value;
    }

    private void setLastStepExternalReference(List<ProductListingNoonWriteStepResult> steps, String externalReference) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        steps.get(steps.size() - 1).setExternalReference(externalReference);
    }

    private String externalReference(String skuParent, String pskuCode) {
        return "skuParent=" + normalize(skuParent) + ";pskuCode=" + normalize(pskuCode);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private void putIfHasText(ObjectNode target, String field, String value) {
        if (StringUtils.hasText(value)) {
            target.put(field, value.trim());
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return "";
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return normalize(value.asText(""));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String upper(String value) {
        return ProductListingNoonHeaders.upper(value);
    }

    private static class ProductFullTypeParts {
        private final String family;
        private final String productType;
        private final String productSubType;

        private ProductFullTypeParts(String family, String productType, String productSubType) {
            this.family = family;
            this.productType = productType;
            this.productSubType = productSubType;
        }

        private boolean looksLikeLabels() {
            return looksLikeLabel(family) && looksLikeLabel(productType) && looksLikeLabel(productSubType);
        }

        private static boolean looksLikeLabel(String value) {
            return StringUtils.hasText(value) && !value.contains("_");
        }
    }

    private static class ProductFullTypeLabels {
        private final String family;
        private final String productType;
        private final String productSubType;

        private ProductFullTypeLabels(String family, String productType, String productSubType) {
            this.family = family;
            this.productType = productType;
            this.productSubType = productSubType;
        }

        private static ProductFullTypeLabels empty() {
            return new ProductFullTypeLabels("", "", "");
        }

        private boolean complete() {
            return StringUtils.hasText(family)
                    && StringUtils.hasText(productType)
                    && StringUtils.hasText(productSubType);
        }

        private boolean hasAny() {
            return StringUtils.hasText(family)
                    || StringUtils.hasText(productType)
                    || StringUtils.hasText(productSubType);
        }
    }
}
