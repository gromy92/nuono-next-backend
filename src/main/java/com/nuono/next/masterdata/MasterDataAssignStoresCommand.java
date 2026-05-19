package com.nuono.next.masterdata;

import java.util.ArrayList;
import java.util.List;

public class MasterDataAssignStoresCommand {

    private Long operatorUserId;

    private List<String> storeCodes = new ArrayList<>();

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public List<String> getStoreCodes() {
        return storeCodes;
    }

    public void setStoreCodes(List<String> storeCodes) {
        this.storeCodes = storeCodes == null ? new ArrayList<>() : storeCodes;
    }
}
