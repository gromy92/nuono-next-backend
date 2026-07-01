package com.nuono.next.operationsskin;

import java.util.ArrayList;
import java.util.List;

public class OperationsSkinComponentsSaveRequest {
    private String storeCode;
    private List<OperationsSkinComponentView> components = new ArrayList<>();

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public List<OperationsSkinComponentView> getComponents() {
        return components;
    }

    public void setComponents(List<OperationsSkinComponentView> components) {
        this.components = components == null ? new ArrayList<>() : components;
    }
}
