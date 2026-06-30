package com.nuono.next.outboundfee;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class OfficialOutboundFeeBatchCalculationCommand {
    private Long ownerUserId;
    private String storeCode;
    private String site;
    private LocalDate calculationDate;
    private Long operatorUserId;
    private List<OfficialOutboundFeeCalculationCommand> items = new ArrayList<>();

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public LocalDate getCalculationDate() { return calculationDate; }
    public void setCalculationDate(LocalDate calculationDate) { this.calculationDate = calculationDate; }
    public List<OfficialOutboundFeeCalculationCommand> getItems() { return items; }
    public void setItems(List<OfficialOutboundFeeCalculationCommand> items) { this.items = normalizeItems(items); }

    @JsonIgnore
    public Long getOperatorUserId() { return operatorUserId; }
    @JsonIgnore
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    @JsonIgnore
    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    private List<OfficialOutboundFeeCalculationCommand> normalizeItems(List<OfficialOutboundFeeCalculationCommand> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<OfficialOutboundFeeCalculationCommand> result = new ArrayList<>();
        for (OfficialOutboundFeeCalculationCommand value : values) {
            if (value != null && StringUtils.hasText(value.getSkuId())) {
                result.add(value);
            }
        }
        return result;
    }
}
