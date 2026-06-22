package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface NoonPullGatewaySession {
    JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders);

    JsonNode postWriteJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders);

    default JsonNode postMultipartFile(
            String url,
            String fieldName,
            String fileName,
            String contentType,
            byte[] content,
            boolean withProject,
            Map<String, String> extraHeaders
    ) {
        throw new UnsupportedOperationException("Noon multipart file upload is not supported by this session.");
    }

    byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders);
}
