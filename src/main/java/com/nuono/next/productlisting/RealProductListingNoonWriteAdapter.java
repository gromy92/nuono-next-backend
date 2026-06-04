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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(prefix = "nuono.product-listing.real-write", name = "enabled", havingValue = "true")
public class RealProductListingNoonWriteAdapter implements ProductListingNoonWriteAdapter {

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final ProductListingRealWriteProperties properties;

    public RealProductListingNoonWriteAdapter(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            ProductListingRealWriteProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        this.properties = properties == null ? new ProductListingRealWriteProperties() : properties;
    }

    @Override
    public ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        try {
            ProductListingDraftCommand draft = requireDraft(request);
            NoonPullStoreBinding binding = bindingResolver.resolve(interfaceRequest(request));
            NoonPullGatewaySession session = sessionFactory.login(binding);
            Map<String, String> headers = writeHeaders(binding);
            ProductListingRealWriteProperties.Endpoints endpoints = properties.getEndpoints();

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

            postStep(steps, "upsert_zsku", session, endpoints.getUpsertZskuUrl(), upsertZskuBody(draft, skuParent), headers);
            postStep(steps, "upsert_offer", session, endpoints.getUpsertOfferUrl(), upsertOfferBody(draft, binding, pskuCode), headers);
            postStep(steps, "upsert_price", session, endpoints.getUpsertPriceUrl(), upsertPriceBody(request, draft, binding, pskuCode), headers);
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

    private Map<String, String> writeHeaders(NoonPullStoreBinding binding) {
        String siteCode = upper(binding.getSiteCode());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-project", binding.getProjectCode());
        headers.put("x-locale", "en-" + siteCode);
        headers.put("Country-Code", siteCode);
        headers.put("Id-Partner", binding.getPartnerId());
        return headers;
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
        try {
            JsonNode response = session.postWriteJson(url, body, true, headers);
            step.setStatus("succeeded");
            steps.add(step);
            return response == null ? objectMapper.createObjectNode() : response;
        } catch (RuntimeException exception) {
            step.setStatus("failed");
            step.setFailureCode("noon_write_failed");
            step.setFailureMessage(exception.getMessage());
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

    private ObjectNode upsertZskuBody(ProductListingDraftCommand draft, String skuParent) {
        ProductFullTypeParts parts = productFullTypeParts(draft);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("skuParent", skuParent);
        root.putArray("variants");
        root.put("lang", "en");
        ObjectNode attributes = root.putObject("attributes");
        attributes.put("brand", firstNonBlank(draft.getProductBrand(), draft.getProductBrandCode(), "Generic"));
        attributes.put("family", parts.family);
        attributes.put("product_type", parts.productType);
        attributes.put("product_subtype", parts.productSubType);
        attributes.put("item_condition", "new");
        attributes.put("update_fulltype", "True");
        if (StringUtils.hasText(draft.getProductTitleEn())) {
            attributes.put("title_en", draft.getProductTitleEn());
        }
        if (StringUtils.hasText(draft.getProductTitleAr())) {
            attributes.put("title_ar", draft.getProductTitleAr());
        }
        return root;
    }

    private ObjectNode upsertOfferBody(ProductListingDraftCommand draft, NoonPullStoreBinding binding, String pskuCode) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode psku = root.putArray("pskus").addObject();
        psku.put("pskuCode", pskuCode);
        psku.put("country", upper(binding.getSiteCode()));
        psku.put("isActive", 1);
        if (draft.getIdWarranty() != null) {
            psku.put("idWarranty", draft.getIdWarranty());
        }
        psku.put("pricingMethod", "manual");
        psku.put("offerNote", "");
        if (draft.getPrice() != null) {
            psku.put("price", draft.getPrice());
        }
        if (Boolean.TRUE.equals(draft.getFbp())
                && StringUtils.hasText(draft.getWarehouseId())
                && draft.getQuantity() != null) {
            ObjectNode stock = psku.putArray("stocks").addObject();
            stock.put("idWarehouse", draft.getWarehouseId());
            stock.put("whCode", draft.getWarehouseId());
            stock.put("quantity", String.valueOf(draft.getQuantity()));
            stock.put("stockUpdated", true);
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

    private String requiredText(JsonNode node, String pointer, String label) {
        String value = node == null ? null : node.at(pointer).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Noon create_product response missing " + label + ".");
        }
        return value;
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

    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
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
    }
}
