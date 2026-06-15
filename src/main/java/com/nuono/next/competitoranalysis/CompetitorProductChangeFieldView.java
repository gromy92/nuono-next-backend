package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.util.StringUtils;

public class CompetitorProductChangeFieldView {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String fieldKey;
    private String fieldLabel;
    private String changeType;
    private Object oldValue;
    private Object newValue;
    private String severity;

    static CompetitorProductChangeFieldView fromRow(CompetitorProductChangeEventRow row) {
        CompetitorProductChangeFieldView view = new CompetitorProductChangeFieldView();
        view.setFieldKey(row.getFieldKey());
        view.setFieldLabel(StringUtils.hasText(row.getFieldLabel()) ? row.getFieldLabel() : row.getFieldKey());
        view.setChangeType(normalizeLower(row.getChangeType()));
        view.setOldValue(parseJsonValue(row.getOldValueJson()));
        view.setNewValue(parseJsonValue(row.getNewValueJson()));
        view.setSeverity(normalizeSeverity(row.getSeverity()));
        return view;
    }

    private static Object parseJsonValue(String valueJson) {
        if (!StringUtils.hasText(valueJson)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(valueJson, Object.class);
        } catch (Exception ignored) {
            return valueJson;
        }
    }

    private static String normalizeSeverity(String severity) {
        String normalized = normalizeLower(severity);
        return StringUtils.hasText(normalized) ? normalized : "info";
    }

    private static String normalizeLower(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getFieldLabel() { return fieldLabel; }
    public void setFieldLabel(String fieldLabel) { this.fieldLabel = fieldLabel; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public Object getOldValue() { return oldValue; }
    public void setOldValue(Object oldValue) { this.oldValue = oldValue; }
    public Object getNewValue() { return newValue; }
    public void setNewValue(Object newValue) { this.newValue = newValue; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
