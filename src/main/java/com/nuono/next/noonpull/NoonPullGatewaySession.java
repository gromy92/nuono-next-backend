package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonBinaryDownloadSink;
import java.util.Map;

public interface NoonPullGatewaySession {
    JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders);

    default byte[] postBytes(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("POST text/bytes response is not supported by this Noon session.");
    }

    /** One binary POST attempt; DP state owns auth refresh and transient retry. */
    default byte[] postBytesOnce(
            String url,
            JsonNode body,
            boolean withProject,
            Map<String, String> extraHeaders
    ) {
        return postBytes(url, body, withProject, extraHeaders);
    }

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

    default JsonNode postWriteJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
        return postJson(url, body, withProject, extraHeaders);
    }

    /** One POST attempt without transport-layer replay, for reads and externally non-idempotent writes. */
    default JsonNode postJsonOnce(
            String url,
            JsonNode body,
            boolean withProject,
            Map<String, String> extraHeaders
    ) {
        return postWriteJson(url, body, withProject, extraHeaders);
    }

    byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders);

    /** One transport attempt; auth refresh and transient replay belong to the DP state machine. */
    default byte[] getBytesOnce(
            String url,
            boolean withProject,
            Map<String, String> extraHeaders
    ) {
        return getBytes(url, withProject, extraHeaders);
    }

    /** One transport attempt streamed into a bounded synchronous sink. */
    default void getBytesOnce(
            String url,
            boolean withProject,
            Map<String, String> extraHeaders,
            NoonBinaryDownloadSink sink
    ) {
        try {
            byte[] content = getBytesOnce(url, withProject, extraHeaders);
            sink.accept(content, 0, content.length);
            sink.complete();
        } catch (RuntimeException failure) {
            sink.abort(failure);
            throw failure;
        }
    }
}
