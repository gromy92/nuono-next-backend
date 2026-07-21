package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionPluginIngestCommand extends ProductSelectionSourceCollectionCommand {

    private String extractorVersion;
    private String pageLanguage;
    private List<ProductSelectionCompetitorCategoryLink> categoryLinks = new ArrayList<>();
    private List<PluginWarning> warnings = new ArrayList<>();

    public String getExtractorVersion() {
        return extractorVersion;
    }

    public void setExtractorVersion(String extractorVersion) {
        this.extractorVersion = extractorVersion;
    }

    public String getPageLanguage() {
        return pageLanguage;
    }

    public void setPageLanguage(String pageLanguage) {
        this.pageLanguage = pageLanguage;
    }

    public List<ProductSelectionCompetitorCategoryLink> getCategoryLinks() {
        return categoryLinks;
    }

    public void setCategoryLinks(List<ProductSelectionCompetitorCategoryLink> categoryLinks) {
        this.categoryLinks = categoryLinks == null ? new ArrayList<>() : new ArrayList<>(categoryLinks);
    }

    public List<PluginWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<PluginWarning> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public static class PluginWarning {

        private String code;
        private String field;
        private String message;

        public PluginWarning() {
        }

        public PluginWarning(String code, String field, String message) {
            this.code = code;
            this.field = field;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
