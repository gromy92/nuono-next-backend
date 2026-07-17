package com.nuono.next.procurementorder;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class ProcurementPurchaseOrderSourcingRequirement {

    private static final int MAX_TEXT_LENGTH = 120;
    private static final int MAX_ALI1688_SPEC_TEXT_LENGTH = 200;

    private final String specText;
    private final String sizeText;
    private final String colorText;

    private ProcurementPurchaseOrderSourcingRequirement(String specText, String sizeText, String colorText) {
        this.specText = specText;
        this.sizeText = sizeText;
        this.colorText = colorText;
    }

    public static ProcurementPurchaseOrderSourcingRequirement of(String specText, String sizeText, String colorText) {
        return new ProcurementPurchaseOrderSourcingRequirement(
                clean(specText),
                clean(sizeText),
                clean(colorText)
        );
    }

    public String getSpecText() {
        return specText;
    }

    public String getSizeText() {
        return sizeText;
    }

    public String getColorText() {
        return colorText;
    }

    public List<String> toSpecHints() {
        List<String> hints = new ArrayList<>();
        addHint(hints, "规格", specText);
        addHint(hints, "尺寸", sizeText);
        addHint(hints, "颜色", colorText);
        return hints;
    }

    public boolean hasAnyText() {
        return StringUtils.hasText(specText)
                || StringUtils.hasText(sizeText)
                || StringUtils.hasText(colorText);
    }

    public String toAli1688SpecText() {
        List<String> parts = new ArrayList<>();
        addPart(parts, specText);
        addPart(parts, sizeText);
        addPart(parts, colorText);
        if (parts.isEmpty()) {
            return null;
        }
        String text = String.join(" / ", parts);
        return text.length() <= MAX_ALI1688_SPEC_TEXT_LENGTH
                ? text
                : text.substring(0, MAX_ALI1688_SPEC_TEXT_LENGTH);
    }

    private static void addHint(List<String> hints, String label, String value) {
        if (StringUtils.hasText(value)) {
            hints.add(label + ": " + value);
        }
    }

    private static void addPart(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private static String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_TEXT_LENGTH ? normalized : normalized.substring(0, MAX_TEXT_LENGTH);
    }
}
