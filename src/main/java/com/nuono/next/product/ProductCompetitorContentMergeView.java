package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductCompetitorContentMergeView {

    private boolean ready;
    private String source;
    private String msg;
    private String message;
    private List<String> warnings = new ArrayList<>();
    private Map<String, Map<String, String>> data = new LinkedHashMap<>();

    public static ProductCompetitorContentMergeView of(String draftText, String source, List<String> warnings) {
        ProductCompetitorContentMergeView view = new ProductCompetitorContentMergeView();
        view.setReady(true);
        view.setSource(source);
        view.setWarnings(warnings);
        Map<String, String> draft = new LinkedHashMap<>();
        draft.put("text", draftText);
        view.getData().put("draft", draft);
        return view;
    }

    public static ProductCompetitorContentMergeView unavailable(String source, String message, List<String> warnings) {
        ProductCompetitorContentMergeView view = new ProductCompetitorContentMergeView();
        view.setReady(false);
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

    public Map<String, Map<String, String>> getData() {
        return data;
    }

    public void setData(Map<String, Map<String, String>> data) {
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }
}
