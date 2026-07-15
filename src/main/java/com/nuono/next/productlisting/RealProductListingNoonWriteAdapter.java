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
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Primary
@ConditionalOnProperty(prefix = "nuono.product-listing.real-write", name = "enabled", havingValue = "true")
public class RealProductListingNoonWriteAdapter implements ProductListingNoonWriteAdapter {
    private static final int MAX_IMAGE_UPLOADS = 15;
    private static final DateTimeFormatter NOON_OFFER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int DEFAULT_SALE_WINDOW_YEARS = 20;
    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MISSING_SESSION_FACTORY_MESSAGE =
            "Product listing real Noon writes require nuono.noon.pull.real-provider.enabled=true "
                    + "so a NoonPullGatewaySessionFactory bean is available.";

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final ProductListingRealWriteProperties properties;
    private final ProductListingImageDownloader imageDownloader;
    private final ProductListingOfferStockWriteAdapter offerStockWriteAdapter;

    @Autowired
    public RealProductListingNoonWriteAdapter(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            ObjectProvider<NoonPullGatewaySessionFactory> sessionFactoryProvider,
            ProductListingRealWriteProperties properties,
            ProductListingOfferStockWriteAdapter offerStockWriteAdapter
    ) {
        this(
                objectMapper,
                bindingResolver,
                requireAvailableSessionFactory(sessionFactoryProvider),
                properties,
                new HttpClientProductListingImageDownloader(),
                offerStockWriteAdapter
        );
    }

    RealProductListingNoonWriteAdapter(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            ProductListingRealWriteProperties properties,
            ProductListingImageDownloader imageDownloader
    ) {
        this(
                objectMapper,
                bindingResolver,
                sessionFactory,
                properties,
                imageDownloader,
                new UnavailableProductListingOfferStockWriteAdapter()
        );
    }

    RealProductListingNoonWriteAdapter(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            ProductListingRealWriteProperties properties,
            ProductListingImageDownloader imageDownloader,
            ProductListingOfferStockWriteAdapter offerStockWriteAdapter
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        this.properties = properties == null ? new ProductListingRealWriteProperties() : properties;
        this.imageDownloader = imageDownloader == null
                ? new HttpClientProductListingImageDownloader()
                : imageDownloader;
        this.offerStockWriteAdapter = offerStockWriteAdapter == null
                ? new UnavailableProductListingOfferStockWriteAdapter()
                : offerStockWriteAdapter;
    }

    @Override
    public ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        try {
            ProductListingDraftCommand draft = requireDraft(request);
            NoonPullStoreBinding binding = bindingResolver.resolve(interfaceRequest(request));
            NoonPullGatewaySession session = requireSessionFactory().login(binding);
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

            return writeAfterCreate(request, draft, binding, session, endpoints, headers, fullTypeLabels, skuParent, pskuCode, steps);
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

    @Override
    public ProductListingNoonWriteResult continueAfterCreate(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode
    ) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        try {
            ProductListingDraftCommand draft = requireDraft(request);
            if (!StringUtils.hasText(skuParent)) {
                throw new IllegalArgumentException("Product listing continuation skuParent is required.");
            }
            if (!StringUtils.hasText(pskuCode)) {
                throw new IllegalArgumentException("Product listing continuation pskuCode is required.");
            }
            NoonPullStoreBinding binding = bindingResolver.resolve(interfaceRequest(request));
            NoonPullGatewaySession session = requireSessionFactory().login(binding);
            Map<String, String> headers = ProductListingNoonHeaders.writeHeaders(binding);
            ProductListingRealWriteProperties.Endpoints endpoints = properties.getEndpoints();
            ProductFullTypeLabels fullTypeLabels = resolveProductFullTypeLabels(session, endpoints, draft, headers);
            return writeAfterCreate(request, draft, binding, session, endpoints, headers, fullTypeLabels, skuParent, pskuCode, steps);
        } catch (RuntimeException exception) {
            return ProductListingNoonWriteResult.failed(
                    "noon_api",
                    "noon_write_failed",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Product listing Noon write continuation failed.",
                    steps
            );
        }
    }

    @Override
    public ProductListingNoonWriteStepResult verifyReadBack(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode,
            List<String> expectedImageValues
    ) {
        try {
            ProductListingDraftCommand draft = requireDraft(request);
            if (!StringUtils.hasText(skuParent)) {
                throw new IllegalArgumentException("Product listing read-back skuParent is required.");
            }
            NoonPullStoreBinding binding = bindingResolver.resolve(interfaceRequest(request));
            NoonPullGatewaySession session = requireSessionFactory().login(binding);
            Map<String, String> headers = ProductListingNoonHeaders.writeHeaders(binding);
            return verifyNoonReadBack(
                    session,
                    properties.getEndpoints(),
                    draft,
                    expectedImageValues,
                    skuParent,
                    pskuCode,
                    headers
            );
        } catch (RuntimeException exception) {
            ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
            step.setStepKey("verify_noon_readback");
            step.setStatus("failed");
            step.setExternalReference(externalReference(skuParent, pskuCode) + ";readBackAttempts=0");
            step.setFailureCode("noon_listing_readback_failed");
            step.setFailureMessage(StringUtils.hasText(exception.getMessage())
                    ? exception.getMessage()
                    : "Noon listing read-back failed.");
            return step;
        }
    }

    private ProductListingNoonWriteResult writeAfterCreate(
            ProductListingNoonWriteRequest request,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            Map<String, String> headers,
            ProductFullTypeLabels fullTypeLabels,
            String skuParent,
            String pskuCode,
            List<ProductListingNoonWriteStepResult> steps
    ) {
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
        if (!properties.isOfferUpsertEnabled() && hasOfferControlledFields(draft)) {
            appendOfferStockNotEnabledStep(steps);
        } else if (hasSplitOfferOrWarehouseStockFields(draft)) {
            appendOfferStockStep(steps, request, draft, binding, session, endpoints, headers, skuParent, pskuCode);
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
    }

    private void appendOfferStockStep(
            List<ProductListingNoonWriteStepResult> steps,
            ProductListingNoonWriteRequest request,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            Map<String, String> headers,
            String skuParent,
            String pskuCode
    ) {
        ProductListingNoonWriteStepResult step = offerStockWriteAdapter.writeOfferStock(offerStockWriteRequest(
                request,
                draft,
                binding,
                skuParent,
                pskuCode
        ), session, endpoints, headers);
        if (step == null) {
            step = new ProductListingNoonWriteStepResult();
            step.setStepKey("upsert_offer");
            step.setStatus("failed");
            step.setFailureCode("noon_offer_stock_adapter_empty_result");
            step.setFailureMessage("Product listing offer/stock adapter returned no result.");
        }
        steps.add(step);
        if ("failed".equals(step.getStatus())) {
            throw new IllegalStateException(StringUtils.hasText(step.getFailureMessage())
                    ? step.getFailureMessage()
                    : "Product listing offer/stock write failed.");
        }
    }

    private ProductListingOfferStockWriteRequest offerStockWriteRequest(
            ProductListingNoonWriteRequest request,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            String skuParent,
            String pskuCode
    ) {
        ProductListingOfferStockWriteRequest offerRequest = new ProductListingOfferStockWriteRequest();
        offerRequest.setOwnerUserId(request.getOwnerUserId());
        offerRequest.setStoreCode(request.getStoreCode());
        offerRequest.setSiteCode(binding.getSiteCode());
        offerRequest.setIdPartner(binding.getPartnerId());
        offerRequest.setDraftId(request.getDraftId());
        offerRequest.setDryRunTaskId(request.getDryRunTaskId());
        offerRequest.setRealRunTaskId(request.getRealRunTaskId());
        offerRequest.setSubmittedBy(request.getSubmittedBy());
        offerRequest.setPartnerSku(draft.getPsku());
        offerRequest.setSkuParent(skuParent);
        offerRequest.setPskuCode(pskuCode);
        offerRequest.setPriceMin(draft.getPriceMin());
        offerRequest.setPriceMax(draft.getPriceMax());
        offerRequest.setSalePrice(draft.getSalePrice());
        offerRequest.setSaleStart(draft.getSaleStart());
        offerRequest.setSaleEnd(draft.getSaleEnd());
        offerRequest.setFbp(draft.getFbp());
        offerRequest.setWarehouseId(draft.getWarehouseId());
        offerRequest.setWarehouseCode(draft.getWarehouseCode());
        offerRequest.setQuantity(draft.getQuantity());
        offerRequest.setIsActive(draft.getIsActive());
        offerRequest.setOfferNote(draft.getOfferNote());
        return offerRequest;
    }

    private void appendOfferStockNotEnabledStep(List<ProductListingNoonWriteStepResult> steps) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("upsert_offer");
        step.setStatus("skipped");
        step.setFailureCode("noon_offer_stock_write_not_enabled");
        step.setFailureMessage("Offer price range, sale-window, offer note, warehouse, stock, and active-state fields were not written to Noon; "
                + "nuono.product-listing.real-write.offer-upsert-enabled is false.");
        steps.add(step);
    }

    private boolean hasOfferControlledFields(ProductListingDraftCommand draft) {
        return draft.getPriceMin() != null
                || draft.getPriceMax() != null
                || draft.getSalePrice() != null
                || StringUtils.hasText(draft.getSaleStart())
                || StringUtils.hasText(draft.getSaleEnd())
                || draft.getFbp() != null
                || StringUtils.hasText(draft.getWarehouseId())
                || StringUtils.hasText(draft.getWarehouseCode())
                || draft.getQuantity() != null
                || draft.getIsActive() != null
                || draft.getOfferNote() != null;
    }

    private boolean hasSplitOfferOrWarehouseStockFields(ProductListingDraftCommand draft) {
        return draft.getFbp() != null
                || StringUtils.hasText(draft.getWarehouseId())
                || StringUtils.hasText(draft.getWarehouseCode())
                || draft.getQuantity() != null
                || draft.getIsActive() != null
                || draft.getOfferNote() != null;
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
        requireReadBackProductFullType(fields, draft, common);
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
            if (expectedIndex > MAX_IMAGE_UPLOADS) {
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
        if ("brand".equals(field) && sameBrandText(expected, actual)) {
            return;
        }
        if (!sameText(expected, actual, ignoreCase)) {
            fields.add(field);
        }
    }

    private void requireReadBackProductFullType(
            List<String> fields,
            ProductListingDraftCommand draft,
            JsonNode common
    ) {
        if (!StringUtils.hasText(draft.getProductFullType())) {
            return;
        }
        String actualFullType = firstNonBlank(
                text(common, "product_fulltype_code"),
                text(common, "productFulltypeCode"),
                text(common, "product_fulltype"),
                text(common, "productFulltype")
        );
        if (sameText(draft.getProductFullType(), actualFullType, false)) {
            return;
        }
        String actualFullTypeId = firstNonBlank(
                text(common, "id_product_fulltype"),
                text(common, "idProductFulltype"),
                text(common, "idProductFullType")
        );
        if (draft.getIdProductFullType() != null
                && String.valueOf(draft.getIdProductFullType()).equals(actualFullTypeId)) {
            return;
        }
        if (!StringUtils.hasText(actualFullType) && !StringUtils.hasText(actualFullTypeId)) {
            return;
        }
        fields.add("product_fulltype");
    }

    private boolean sameBrandText(String expected, String actual) {
        String normalizedExpected = normalizeBrand(expected);
        String normalizedActual = normalizeBrand(actual);
        return StringUtils.hasText(normalizedExpected)
                && StringUtils.hasText(normalizedActual)
                && normalizedExpected.equals(normalizedActual);
    }

    private NoonPullGatewaySessionFactory requireSessionFactory() {
        if (sessionFactory == null) {
            throw new IllegalStateException(MISSING_SESSION_FACTORY_MESSAGE);
        }
        return sessionFactory;
    }

    private static NoonPullGatewaySessionFactory requireAvailableSessionFactory(
            ObjectProvider<NoonPullGatewaySessionFactory> sessionFactoryProvider
    ) {
        NoonPullGatewaySessionFactory available = sessionFactoryProvider == null
                ? null
                : sessionFactoryProvider.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException(MISSING_SESSION_FACTORY_MESSAGE);
        }
        return available;
    }

    private String normalizeBrand(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
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
                        imageUploadUrl(endpoints.getUploadImageUrl()),
                        "file",
                        uploadFileName(download),
                        uploadContentType(download),
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
            step.setExternalReference(uploadedImagesReference(uploadedPaths));
            steps.add(step);
            return uploadedPaths;
        } catch (RuntimeException exception) {
            step.setStatus("failed");
            step.setFailureCode("noon_image_upload_failed");
            step.setFailureMessage(StringUtils.hasText(exception.getMessage())
                    ? exception.getMessage()
                    : "Noon image upload failed.");
            step.setExternalReference(uploadedImagesReference(uploadedPaths));
            steps.add(step);
            throw exception;
        }
    }

    private String imageUploadUrl(String baseUrl) {
        return StringUtils.hasText(baseUrl)
                ? baseUrl.trim()
                : ProductListingRealWriteProperties.Endpoints.DEFAULT_UPLOAD_IMAGE_URL;
    }

    private String uploadFileName(ProductListingImageDownload download) {
        String fileType = supportedUploadFileType(download);
        String fileName = download == null ? "" : normalize(download.fileName);
        if (!StringUtils.hasText(fileName)) {
            return "image." + fileType;
        }
        String lower = fileName.toLowerCase();
        if ("png".equals(fileType) && !lower.endsWith(".png")) {
            return stripKnownImageExtension(fileName) + ".png";
        }
        if ("jpg".equals(fileType) && !(lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
            return stripKnownImageExtension(fileName) + ".jpg";
        }
        if ("jpg".equals(fileType) && lower.endsWith(".jpeg")) {
            return fileName.substring(0, fileName.length() - 5) + ".jpg";
        }
        return fileName;
    }

    private String uploadContentType(ProductListingImageDownload download) {
        String fileType = supportedUploadFileType(download);
        return "png".equals(fileType) ? "image/png" : "image/jpeg";
    }

    private String stripKnownImageExtension(String fileName) {
        String normalized = normalize(fileName);
        String lower = normalized.toLowerCase();
        for (String extension : List.of(".jpg", ".jpeg", ".png", ".webp", ".avif", ".gif")) {
            if (lower.endsWith(extension)) {
                return normalized.substring(0, normalized.length() - extension.length());
            }
        }
        return StringUtils.hasText(normalized) ? normalized : "image";
    }

    private String supportedUploadFileType(ProductListingImageDownload download) {
        String fileName = download == null ? "" : normalize(download.fileName);
        String contentType = download == null ? "" : normalize(download.contentType).toLowerCase();
        String normalizedName = fileName.toLowerCase();
        if (normalizedName.endsWith(".png") || contentType.contains("png")) {
            return "png";
        }
        if (normalizedName.endsWith(".jpg")
                || normalizedName.endsWith(".jpeg")
                || contentType.contains("jpeg")
                || contentType.contains("jpg")) {
            return "jpg";
        }
        throw new IllegalStateException("Unsupported Noon image upload file type: "
                + firstNonBlank(fileName, contentType, "unknown"));
    }

    private String uploadedImagesReference(List<String> uploadedPaths) {
        List<String> paths = uploadedPaths == null ? List.of() : uploadedPaths;
        String reference = "uploadedImages=" + paths.size();
        if (!paths.isEmpty()) {
            reference += ";uploadedImagePaths=" + String.join(",", paths);
        }
        return reference;
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
            putIfHasMeaningfulText(attributes, "long_description", draft.getProductDescriptionAr());
            putFeatureBullets(attributes, draft.getProductHighlightsAr());
            putDetailedAttributes(attributes, draft, lang);
            return root;
        }

        attributes.put("grade", "new");
        putIfHasText(attributes, "product_title", draft.getProductTitleEn());
        putIfHasMeaningfulText(attributes, "long_description", draft.getProductDescriptionEn());
        putFeatureBullets(attributes, draft.getProductHighlightsEn());
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
        putDetailedAttributes(attributes, draft, lang);
        return root;
    }

    private void putDetailedAttributes(ObjectNode attributes, ProductListingDraftCommand draft, String lang) {
        List<Map<String, Object>> keyAttributes = draft.getKeyAttributes();
        if (keyAttributes == null || keyAttributes.isEmpty()) {
            return;
        }
        for (Map<String, Object> attribute : keyAttributes) {
            if (attribute == null) {
                continue;
            }
            String code = normalizeAttributeCode(attribute.get("code"));
            if (!StringUtils.hasText(code) || isCoreAttribute(code) || isBarcodeAttribute(code)) {
                continue;
            }
            Object value = "ar".equals(lang)
                    ? firstLocalizedAttributeValue(attribute.get("arValue"), attribute.get("commonValue"))
                    : firstLocalizedAttributeValue(attribute.get("enValue"), attribute.get("commonValue"));
            putScalarAttributeValue(attributes, code, value);
            if (StringUtils.hasText(textValue(attribute.get("unit")))) {
                putScalarAttributeValue(attributes, code + "_unit", attribute.get("unit"));
            }
        }
    }

    private void putFeatureBullets(ObjectNode attributes, List<String> highlights) {
        if (highlights == null || highlights.isEmpty()) {
            return;
        }
        int index = 1;
        for (String highlight : highlights) {
            if (!hasMeaningfulText(highlight)) {
                continue;
            }
            attributes.put("feature_bullet_" + index, highlight.trim());
            index++;
        }
    }

    private Object firstLocalizedAttributeValue(Object localizedValue, Object commonValue) {
        if (isScalarAttributeValue(localizedValue) && StringUtils.hasText(textValue(localizedValue))) {
            return localizedValue;
        }
        return commonValue;
    }

    private boolean isScalarAttributeValue(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private void putScalarAttributeValue(ObjectNode target, String field, Object value) {
        if (!StringUtils.hasText(field) || !isScalarAttributeValue(value) || !StringUtils.hasText(textValue(value))) {
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            target.set(field, objectMapper.valueToTree(value));
            return;
        }
        target.put(field, textValue(value));
    }

    private String normalizeAttributeCode(Object value) {
        String text = textValue(value);
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isCoreAttribute(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        return "brand".equals(normalized)
                || "family".equals(normalized)
                || "product_type".equals(normalized)
                || "product_subtype".equals(normalized)
                || "product_fulltype".equals(normalized)
                || "item_condition".equals(normalized)
                || "grade".equals(normalized)
                || "product_title".equals(normalized)
                || "long_description".equals(normalized);
    }

    private boolean isBarcodeAttribute(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("barcode")) {
            return true;
        }
        for (String token : normalized.split("[^a-z0-9]+")) {
            if ("gtin".equals(token) || "ean".equals(token) || "upc".equals(token)) {
                return true;
            }
        }
        return false;
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
        if (properties.isOfferUpsertEnabled()) {
            putDecimalOrNull(root, "priceMin", priceMinForNoon(draft));
            putDecimalOrNull(root, "priceMax", priceMaxForNoon(draft));
            putDecimalOrNull(root, "salePrice", draft.getSalePrice());
            putTextOrNull(root, "saleStart", saleStartForPrice(draft));
            putTextOrNull(root, "saleEnd", saleEndForPrice(draft));
        }
        return root;
    }

    private BigDecimal priceMinForNoon(ProductListingDraftCommand draft) {
        if (draft.getPriceMin() != null) {
            return draft.getPriceMin();
        }
        return draft.getPrice();
    }

    private BigDecimal priceMaxForNoon(ProductListingDraftCommand draft) {
        if (draft.getPriceMax() != null) {
            return draft.getPriceMax();
        }
        return draft.getPrice();
    }

    private String saleStartForPrice(ProductListingDraftCommand draft) {
        String explicitSaleStart = normalizeOfferDateForNoon(draft.getSaleStart());
        if (StringUtils.hasText(explicitSaleStart)) {
            return explicitSaleStart;
        }
        if (draft.getSalePrice() == null) {
            return null;
        }
        return LocalDate.now().format(NOON_OFFER_DATE_FORMATTER);
    }

    private String saleEndForPrice(ProductListingDraftCommand draft) {
        String explicitSaleEnd = normalizeOfferDateForNoon(draft.getSaleEnd());
        if (StringUtils.hasText(explicitSaleEnd)) {
            return explicitSaleEnd;
        }
        String explicitSaleStart = normalizeOfferDateForNoon(draft.getSaleStart());
        if (StringUtils.hasText(explicitSaleStart)) {
            return parseNoonOfferDate(explicitSaleStart)
                    .plusYears(DEFAULT_SALE_WINDOW_YEARS)
                    .format(NOON_OFFER_DATE_FORMATTER);
        }
        if (draft.getSalePrice() == null) {
            return null;
        }
        return LocalDate.now().plusYears(DEFAULT_SALE_WINDOW_YEARS).format(NOON_OFFER_DATE_FORMATTER);
    }

    private LocalDate parseNoonOfferDate(String value) {
        try {
            return LocalDate.parse(value, NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return LocalDate.now();
        }
    }

    private void putDecimalOrNull(ObjectNode target, String key, BigDecimal value) {
        if (value == null) {
            target.putNull(key);
            return;
        }
        target.put(key, value);
    }

    private void putTextOrNull(ObjectNode target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        } else {
            target.putNull(key);
        }
    }

    private String normalizeOfferDateForNoon(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Try other local timestamp formats below.
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Try other local timestamp formats below.
        }
        try {
            return LocalDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Try other local timestamp formats below.
        }
        try {
            return LocalDateTime.parse(text, FETCH_TIME_FORMATTER).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Try local date below.
        }
        try {
            return LocalDate.parse(text).format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    private ObjectNode upsertWarrantyBody(
            ProductListingNoonWriteRequest request,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            String pskuCode
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("pskuCode", pskuCode);
        root.put("partnerSku", draft.getPsku());
        root.put("idWarranty", draft.getIdWarranty());
        root.put("countryCode", upper(binding.getSiteCode()));
        root.put("baseCountryCode", upper(binding.getSiteCode()));
        root.put("updatedBy", request.getSubmittedBy() == null ? "" : String.valueOf(request.getSubmittedBy()));
        return root;
    }

    private ObjectNode upsertBarcodeBody(ProductListingDraftCommand draft) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode barcode = root.putArray("pbarcodeUpsert").addObject();
        barcode.put("partnerSku", draft.getPsku());
        barcode.put("partnerBarcode", draft.getBarcode());
        root.put("forceMapping", true);
        return root;
    }

    private ProductFullTypeParts productFullTypeParts(ProductListingDraftCommand draft) {
        String[] parts = StringUtils.hasText(draft.getProductFullType())
                ? draft.getProductFullType().split("-")
                : new String[0];
        String family = firstNonBlank(parts.length > 0 ? parts[0] : null, draft.getFamily());
        String productType = firstNonBlank(parts.length > 1 ? parts[1] : null, draft.getProductType());
        String productSubType = firstNonBlank(parts.length > 2 ? parts[2] : null, draft.getProductSubType());
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
            if (StringUtils.hasText(draft.getProductFullType())) {
                addUrl(urls, appendQuery(suggestUrl, "query", draft.getProductFullType()));
                ProductFullTypeParts parts = productFullTypeParts(draft);
                addSuggestQuery(urls, suggestUrl, humanizeCode(parts.productSubType));
                addSuggestQuery(urls, suggestUrl, humanizeCode(parts.productType));
                addSuggestQuery(urls, suggestUrl, humanizeCode(parts.family));
            } else if (draft.getIdProductFullType() != null) {
                addUrl(urls, appendQuery(suggestUrl, "id_product_fulltype", String.valueOf(draft.getIdProductFullType())));
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
            return sameText(expectedFullType, actualFullType, false);
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

    private void putIfHasMeaningfulText(ObjectNode target, String field, String value) {
        if (hasMeaningfulText(value)) {
            target.put(field, value.trim());
        }
    }

    private boolean hasMeaningfulText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String plainText = value
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", " ")
                .trim();
        return StringUtils.hasText(plainText);
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
