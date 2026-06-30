package com.nuono.next.outboundfee;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class OfficialOutboundFeeLatestCalculationQuery {

    private Long ownerUserId;
    private String storeCode;
    private String site;
    private String specSourceType;
    private List<String> skuIds = new ArrayList<>();

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getSpecSourceType() {
        return specSourceType;
    }

    public void setSpecSourceType(String specSourceType) {
        this.specSourceType = StringUtils.hasText(specSourceType) ? specSourceType.trim() : null;
    }

    public List<String> getSkuIds() {
        return skuIds;
    }

    public void setSkuIds(List<String> skuIds) {
        this.skuIds = normalizeSkuIds(skuIds);
    }

    private List<String> normalizeSkuIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }
}
