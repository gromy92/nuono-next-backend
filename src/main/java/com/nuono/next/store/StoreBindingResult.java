package com.nuono.next.store;

import com.nuono.next.noon.NoonSessionGateway;
import java.util.ArrayList;
import java.util.List;

public class StoreBindingResult {

    private boolean success;

    private String message;

    private List<NoonSessionGateway.MerchantProject> projectList = new ArrayList<>();

    public static StoreBindingResult succeeded(String message) {
        StoreBindingResult result = new StoreBindingResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    public static StoreBindingResult projectSelectionRequired(
            List<NoonSessionGateway.MerchantProject> projectList,
            String message
    ) {
        StoreBindingResult result = new StoreBindingResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.setProjectList(projectList);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<NoonSessionGateway.MerchantProject> getProjectList() {
        return projectList;
    }

    public void setProjectList(List<NoonSessionGateway.MerchantProject> projectList) {
        this.projectList = projectList == null ? new ArrayList<>() : new ArrayList<>(projectList);
    }
}
