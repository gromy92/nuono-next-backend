package com.nuono.next.noonpull;

import com.nuono.next.product.NoonProductListFieldSupport;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.product.ProductProjectionPersistenceService;
import com.nuono.next.product.ProductSourceTypeSupport;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonProductListPullAdapter {
    private final NoonProductProjectionWriter projectionWriter;

    public NoonProductListPullAdapter(NoonProductProjectionWriter projectionWriter) {
        this.projectionWriter = projectionWriter;
    }

    public NoonProductListApplyResult apply(NoonProductListApplyCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Noon product list apply command is required.");
        }
        List<ProductProjectionPersistenceService.ProductMasterSeed> seeds = new ArrayList<>();
        for (Map<String, Object> item : command.getItems()) {
            ProductProjectionPersistenceService.ProductMasterSeed seed = toProductSeed(command, item);
            if (StringUtils.hasText(seed.getSkuParent())) {
                seeds.add(seed);
            }
        }
        NoonProductProjectionWriteCommand writeCommand = new NoonProductProjectionWriteCommand();
        writeCommand.setOwnerUserId(command.getOwnerUserId());
        writeCommand.setProjectCode(command.getProjectCode());
        writeCommand.setProjectName(command.getProjectName());
        writeCommand.setReferenceStoreCode(command.getStoreCode());
        writeCommand.setSourceBatchId(command.getSourceBatchId());
        writeCommand.setPreserveDrafts(true);
        writeCommand.setPublishFlowTriggered(false);
        writeCommand.setCompleteSiteScope(false);
        writeCommand.setSiteSeeds(List.of(new ProductProjectionPersistenceService.SiteSeed(
                command.getStoreCode(),
                command.getSiteCode(),
                "LOCAL_READY",
                true
        )));
        writeCommand.setProductSeeds(seeds);
        projectionWriter.write(writeCommand);
        return new NoonProductListApplyResult(seeds.size());
    }

    @SuppressWarnings("unchecked")
    private ProductProjectionPersistenceService.ProductMasterSeed toProductSeed(
            NoonProductListApplyCommand command,
            Map<String, Object> item
    ) {
        Map<String, Object> content = item.get("content") instanceof Map<?, ?>
                ? new LinkedHashMap<>((Map<String, Object>) item.get("content"))
                : new LinkedHashMap<>();
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                new ProductProjectionPersistenceService.ProductMasterSeed();
        String skuParent = firstNonBlank(
                text(item, "csku_parent"),
                text(item, "zsku_parent"),
                text(item, "sku_parent"),
                text(item, "skuParent"),
                text(item, "catalog_sku")
        );
        String childSku = firstNonBlank(
                text(item, "sku"),
                text(item, "catalog_sku"),
                text(item, "zsku_child"),
                text(item, "child_sku")
        );
        seed.setSkuParent(skuParent);
        seed.setProductSourceType(ProductSourceTypeSupport.resolve(null, childSku, skuParent));
        seed.setChildSku(childSku);
        seed.setPartnerSku(text(item, "partner_sku"));
        seed.setPskuCode(NoonProductListFieldSupport.pskuCode(item));
        seed.setOfferCode(text(item, "offer_code"));
        seed.setReferenceStoreCode(command.getStoreCode());
        seed.setTitleCache(firstNonBlank(text(content, "title"), text(item, "title")));
        seed.setBrandCache(firstNonBlank(text(content, "brand"), text(item, "brand_code"), text(item, "brand")));
        seed.setCoverImageUrl(resolveImageUrl(firstNonBlank(text(content, "image"), text(item, "image"))));
        seed.setProductFulltypeCache(text(item, "product_fulltype"));
        seed.setBarcode(firstNonBlank(text(item, "barcode"), text(item, "gtin"), text(item, "ean"), text(item, "upc")));
        seed.setOriginalPrice(firstNonBlank(text(item, "base_price"), text(item, "original_price")));
        seed.setSalePrice(text(item, "sale_price"));
        seed.setFinalPrice(firstNonBlank(text(item, "price"), seed.getSalePrice(), seed.getOriginalPrice()));
        seed.setFinalPriceSource(seed.getFinalPrice() == null ? null : "offer_list");
        seed.setLiveStatus(firstNonBlank(text(item, "live_status"), text(item, "seller_status")));
        seed.setStatusCode(text(item, "status_code"));
        seed.setIsActive(resolveActive(seed.getLiveStatus()));
        seed.setFbnStock(intValue(item.get("fbn_stock")));
        seed.setSupermallStock(intValue(item.get("supermall_stock")));
        seed.setFbpStock(intValue(item.get("fbp_stock")));
        seed.setSyncStatus("synced");
        seed.setLastSyncedAt(LocalDateTime.now().toString());
        ProductProjectionPersistenceService.SiteOfferSeed siteOffer =
                ProductProjectionPersistenceService.SiteOfferSeed.fromRepresentative(seed);
        siteOffer.setCurrency(text(item, "currency"));
        seed.addSiteOffer(siteOffer);
        return seed;
    }

    private String text(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        String value = String.valueOf(map.get(key)).trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean resolveActive(String liveStatus) {
        if (!StringUtils.hasText(liveStatus)) {
            return false;
        }
        String normalized = liveStatus.trim().toLowerCase(Locale.ROOT);
        return "active".equals(normalized)
                || "true".equals(normalized)
                || "1".equals(normalized)
                || "yes".equals(normalized)
                || "enabled".equals(normalized);
    }

    private String resolveImageUrl(String image) {
        if (!StringUtils.hasText(image)) {
            return image;
        }
        if (isHttpOrProtocolRelativeUrl(image)) {
            return ProductImageUrlSupport.normalize(image);
        }
        String path = image.replaceFirst("^/+", "");
        String prefix = path.regionMatches(true, 0, "p/", 0, 2)
                ? "https://f.nooncdn.com/"
                : "https://f.nooncdn.com/p/";
        return ProductImageUrlSupport.normalize(prefix + path);
    }

    private boolean isHttpOrProtocolRelativeUrl(String value) {
        return value.startsWith("//")
                || value.regionMatches(true, 0, "http://", 0, "http://".length())
                || value.regionMatches(true, 0, "https://", 0, "https://".length());
    }
}
