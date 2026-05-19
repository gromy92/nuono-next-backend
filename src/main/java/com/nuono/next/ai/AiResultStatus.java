package com.nuono.next.ai;

public final class AiResultStatus {

    public static final String SUCCESS = "SUCCESS";
    public static final String AI_DISABLED = "AI_DISABLED";
    public static final String AI_PROVIDER_NOT_CONFIGURED = "AI_PROVIDER_NOT_CONFIGURED";
    public static final String AI_PROVIDER_ERROR = "AI_PROVIDER_ERROR";
    public static final String AI_PROVIDER_EMPTY_RESPONSE = "AI_PROVIDER_EMPTY_RESPONSE";
    public static final String AI_OUTPUT_SCHEMA_INVALID = "AI_OUTPUT_SCHEMA_INVALID";
    public static final String AI_REFUSED = "AI_REFUSED";
    public static final String AI_INVALID_INPUT = "AI_INVALID_INPUT";

    private AiResultStatus() {
    }
}
