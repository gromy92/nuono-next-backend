package com.nuono.next.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "nuono.ai")
public class AiProperties {

    private boolean enabled;
    private OpenAi openai = new OpenAi();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAi openai) {
        this.openai = openai == null ? new OpenAi() : openai;
    }

    public boolean isOpenAiConfigured() {
        return enabled && StringUtils.hasText(openai.getApiKey());
    }

    public static class OpenAi {

        private String apiKey;
        private String baseUrl = "https://aicodelink.top/v1";
        private String responsesPath = "/responses";
        private String defaultTextModel = "gpt-5.5";
        private String reasoningEffort = "high";
        private int timeoutSeconds = 180;
        private boolean storeResponses;
        private boolean includeMetadata;
        private Integer maxOutputTokens = 1200;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getResponsesPath() {
            return responsesPath;
        }

        public void setResponsesPath(String responsesPath) {
            this.responsesPath = responsesPath;
        }

        public String getDefaultTextModel() {
            return defaultTextModel;
        }

        public void setDefaultTextModel(String defaultTextModel) {
            this.defaultTextModel = defaultTextModel;
        }

        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isStoreResponses() {
            return storeResponses;
        }

        public void setStoreResponses(boolean storeResponses) {
            this.storeResponses = storeResponses;
        }

        public boolean isIncludeMetadata() {
            return includeMetadata;
        }

        public void setIncludeMetadata(boolean includeMetadata) {
            this.includeMetadata = includeMetadata;
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }
    }
}
