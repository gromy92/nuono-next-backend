package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductListingAiListingView {

    private boolean ready;
    private String source;
    private String ruleVersion;
    private String msg;
    private String message;
    private List<String> warnings = new ArrayList<>();
    private Map<String, Object> data = new LinkedHashMap<>();

    public static ProductListingAiListingView of(
            String ruleVersion,
            Map<String, Object> data,
            String source,
            List<String> warnings
    ) {
        ProductListingAiListingView view = new ProductListingAiListingView();
        view.setReady(true);
        view.setRuleVersion(ruleVersion);
        view.setData(data);
        view.setSource(source);
        view.setWarnings(warnings);
        return view;
    }

    public static ProductListingAiListingView unavailable(
            String ruleVersion,
            String source,
            String message,
            List<String> warnings
    ) {
        ProductListingAiListingView view = new ProductListingAiListingView();
        view.setReady(false);
        view.setRuleVersion(ruleVersion);
        view.setSource(source);
        view.setMsg(message);
        view.setMessage(message);
        view.setWarnings(warnings);
        return view;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }
}
