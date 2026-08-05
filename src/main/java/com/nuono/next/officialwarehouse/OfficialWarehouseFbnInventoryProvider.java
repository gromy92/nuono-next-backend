package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
public class OfficialWarehouseFbnInventoryProvider {

    static final String FBN_INVENTORY_URL =
            "https://fbn.noon.partners/_svc/sc-fbn/api/v5/seller-lab/fbn-inventory";
    private final ObjectMapper objectMapper;
    private final OfficialWarehouseFbnInventoryResponseParser responseParser;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;


    public OfficialWarehouseFbnInventoryProvider(
            ObjectMapper objectMapper, NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory
    ) {
        this.objectMapper = objectMapper;
        this.responseParser = new OfficialWarehouseFbnInventoryResponseParser(objectMapper);
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
    }

    public InventoryPage fetchPage(PullRequest request, int page) {
        if (request == null || request.ownerUserId == null || !StringUtils.hasText(request.storeCode)) {
            throw new IllegalArgumentException("缺少官方仓库存同步店铺范围。");
        }
        int safePage = Math.max(1, page);
        NoonPullStoreBinding binding = bindingResolver.resolve(NoonInterfacePullRequest.builder()
                .ownerUserId(request.ownerUserId)
                .storeCode(request.storeCode)
                .siteCode(request.siteCode)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .requestName("official-warehouse-fbn-inventory")
                .targetIdentity("official-warehouse-fbn-inventory:" + request.storeCode)
                .build());
        byte[] responseBytes = sessionFactory.openOneShot(binding).postBytesOnce(
                FBN_INVENTORY_URL,
                requestBody(safePage),
                false,
                fbnHeaders(binding)
        );
        return responseParser.parse(responseBytes, safePage);
    }
    private ObjectNode requestBody(int page) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("inventory_tab_name", "export");
        body.set("filters", objectMapper.createObjectNode());
        ObjectNode pagination = body.putObject("pagination");
        pagination.put("page", page);
        return body;
    }
    private Map<String, String> fbnHeaders(NoonPullStoreBinding binding) {
        String site = siteCode(binding).toLowerCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "text/csv,application/json,*/*");
        headers.put("Country-Code", site);
        headers.put("Id-Partner", binding.getPartnerId());
        headers.put("X-Locale", "en-" + site);
        headers.put("X-Platform", "web");
        headers.put("X-Project", binding.getProjectCode());
        return headers;
    }
    private String siteCode(NoonPullStoreBinding binding) {
        return OfficialWarehouseFbnInventoryFields.firstNonBlank(
                binding.getSiteCode(), "SA"
        ).toUpperCase(Locale.ROOT);
    }
    public static class PullRequest {
        public final Long ownerUserId;
        public final String storeCode;
        public final String siteCode;

        public PullRequest(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }

    public static class InventoryPage {
        public final int page;
        public final boolean hasNextPage;
        public final Boolean hasNextPageEvidence;
        public final Integer totalPages;
        public final boolean completeExport;
        // Null unless the provider response contract itself supplies this evidence.
        public final String providerGenerationToken;
        public final String providerExportToken;
        public final Long declaredCollectionCount;
        public final List<InventoryItem> items;
        public final JsonNode rawResponse;
        private final byte[] rawResponseBytes;

        public InventoryPage(int page, boolean hasNextPage, List<InventoryItem> items, JsonNode rawResponse) {
            this(page, hasNextPage, null, false, items, rawResponse);
        }

        public InventoryPage(
                int page, Boolean hasNextPageEvidence, Integer totalPages,
                boolean completeExport, List<InventoryItem> items, JsonNode rawResponse
        ) {
            this(page, hasNextPageEvidence, totalPages, completeExport,
                    null, null, null, items, rawResponse, null);
        }

        public InventoryPage(
                int page, Boolean hasNextPageEvidence, Integer totalPages, boolean completeExport,
                String providerGenerationToken, String providerExportToken,
                Long declaredCollectionCount, List<InventoryItem> items, JsonNode rawResponse
        ) {
            this(
                    page, hasNextPageEvidence, totalPages, completeExport,
                    providerGenerationToken, providerExportToken, declaredCollectionCount,
                    items, rawResponse, null
            );
        }

        public InventoryPage(
                int page, Boolean hasNextPageEvidence, Integer totalPages, boolean completeExport,
                String providerGenerationToken, String providerExportToken,
                Long declaredCollectionCount, List<InventoryItem> items, JsonNode rawResponse,
                byte[] rawResponseBytes
        ) {
            this.page = page;
            this.hasNextPageEvidence = hasNextPageEvidence;
            this.hasNextPage = hasNextPageEvidence != null
                    ? hasNextPageEvidence
                    : totalPages != null && page < totalPages;
            this.totalPages = totalPages;
            this.completeExport = completeExport;
            this.providerGenerationToken = providerGenerationToken;
            this.providerExportToken = providerExportToken;
            this.declaredCollectionCount = declaredCollectionCount;
            this.items = List.copyOf(items == null ? List.of() : items);
            this.rawResponse = rawResponse;
            this.rawResponseBytes = rawResponseBytes == null ? null : rawResponseBytes.clone();
        }

        public byte[] exactResponseBytes() {
            return rawResponseBytes == null ? null : rawResponseBytes.clone();
        }
    }

    public static class InventoryItem {
        public final String warehouseCode;
        public final Integer quantity;
        public final String inventoryType;
        public final String reasonCode;
        public final String stockBucket;
        public final String barcode;
        public final String pbarcode;
        public final String noonSku;
        public final String partnerSku;
        public final String countryCode;
        public final String classificationCode;
        public final String title;
        public final String brand;
        public final String inventorySnapshotAt;
        public final JsonNode rawPayload;

        private InventoryItem(
                String warehouseCode,
                Integer quantity,
                String inventoryType,
                String reasonCode,
                String stockBucket,
                String barcode,
                String pbarcode,
                String noonSku,
                String partnerSku,
                String countryCode,
                String classificationCode,
                String title,
                String brand,
                String inventorySnapshotAt,
                JsonNode rawPayload
        ) {
            this.warehouseCode = warehouseCode;
            this.quantity = quantity;
            this.inventoryType = inventoryType;
            this.reasonCode = reasonCode;
            this.stockBucket = stockBucket;
            this.barcode = barcode;
            this.pbarcode = pbarcode;
            this.noonSku = noonSku;
            this.partnerSku = partnerSku;
            this.countryCode = countryCode;
            this.classificationCode = classificationCode;
            this.title = title;
            this.brand = brand;
            this.inventorySnapshotAt = inventorySnapshotAt;
            this.rawPayload = rawPayload;
        }

        public static InventoryItem from(JsonNode row) {
            String inventoryType = OfficialWarehouseFbnInventoryFields.text(
                    row, "inventory_type", "inventoryType"
            );
            String reasonCode = OfficialWarehouseFbnInventoryFields.text(
                    row, "reason_code", "reasonCode"
            );
            return new InventoryItem(
                    OfficialWarehouseFbnInventoryFields.text(
                            row, "warehouse_code", "warehouseCode", "warehouse"
                    ),
                    OfficialWarehouseFbnInventoryFields.integer(row, "qty"),
                    inventoryType,
                    reasonCode,
                    OfficialWarehouseFbnInventoryFields.stockBucket(inventoryType, reasonCode),
                    OfficialWarehouseFbnInventoryFields.text(row, "barcode"),
                    OfficialWarehouseFbnInventoryFields.text(row, "pbarcode", "pbarcode_canonical"),
                    OfficialWarehouseFbnInventoryFields.text(row, "sku", "noon_sku", "noonSku"),
                    OfficialWarehouseFbnInventoryFields.text(row, "partner_sku", "partnerSku", "psku"),
                    OfficialWarehouseFbnInventoryFields.text(row, "country_code", "countryCode"),
                    OfficialWarehouseFbnInventoryFields.text(row, "classification_code", "classificationCode"),
                    OfficialWarehouseFbnInventoryFields.text(row, "title", "product_title", "productTitle"),
                    OfficialWarehouseFbnInventoryFields.text(row, "brand"),
                    OfficialWarehouseFbnInventoryFields.normalizeDateTime(
                            OfficialWarehouseFbnInventoryFields.text(
                                    row, "inventory_snapshot_at", "inventorySnapshotAt", "snapshot_at"
                            )
                    ),
                    row
            );
        }
    }
}
