package com.nuono.next.procurement.aliorder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/** Executes credential-bearing requests without RestTemplate DEBUG body or URL logging. */
final class Ali1688SensitiveHttpClient {
    private Ali1688SensitiveHttpClient() {
    }

    static ResponseEntity<String> get(RestTemplate client, URI uri) {
        return exchange(client, uri, HttpMethod.GET, null, true);
    }

    static ResponseEntity<String> postForm(
            RestTemplate client,
            String url,
            Map<String, String> fields,
            boolean throwOnError
    ) {
        byte[] body = fields.entrySet().stream()
                .map(entry -> formPart(entry.getKey()) + "=" + formPart(entry.getValue()))
                .collect(Collectors.joining("&"))
                .getBytes(StandardCharsets.UTF_8);
        return exchange(client, URI.create(url), HttpMethod.POST, body, throwOnError);
    }

    private static ResponseEntity<String> exchange(
            RestTemplate client,
            URI uri,
            HttpMethod method,
            byte[] body,
            boolean throwOnError
    ) {
        ClientHttpResponse response = null;
        try {
            ClientHttpRequest request = client.getRequestFactory().createRequest(uri, method);
            if (body != null) {
                request.getHeaders().setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                request.getHeaders().setContentLength(body.length);
                request.getBody().write(body);
            }
            response = request.execute();
            if (throwOnError && client.getErrorHandler().hasError(response)) {
                client.getErrorHandler().handleError(response);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(response.getHeaders());
            String responseBody = StreamUtils.copyToString(
                    response.getBody(), StandardCharsets.UTF_8
            );
            return ResponseEntity.status(response.getRawStatusCode())
                    .headers(headers)
                    .body(responseBody);
        } catch (IOException transportFailure) {
            throw new ResourceAccessException("1688 sensitive HTTP exchange failed", transportFailure);
        } finally {
            if (response != null) response.close();
        }
    }

    private static String formPart(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
