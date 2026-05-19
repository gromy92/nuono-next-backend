package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductContentTranslateView {

    private boolean ready;
    private String source;
    private String message;
    private List<String> warnings = new ArrayList<>();
    private Map<String, Object> data = new LinkedHashMap<>();

    public static ProductContentTranslateView of(String translatedText, String source, List<String> warnings) {
        ProductContentTranslateView view = new ProductContentTranslateView();
        view.setReady(true);
        view.setSource(source);
        view.setWarnings(warnings);
        view.setMessage(warnings == null || warnings.isEmpty()
                ? "已完成翻译。"
                : "已生成翻译草稿，请人工复核。");
        Map<String, Object> translation = new LinkedHashMap<>();
        translation.put("text", translatedText);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("translation", translation);
        view.setData(data);
        return view;
    }

    public static ProductContentTranslateView unavailable(String source, String message, List<String> warnings) {
        ProductContentTranslateView view = new ProductContentTranslateView();
        view.setReady(false);
        view.setSource(source);
        view.setWarnings(warnings);
        view.setMessage(message);
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
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : data;
    }
}
