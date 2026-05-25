package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface NoonPullGatewaySession {
    JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders);

    byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders);
}
