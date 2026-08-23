package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;

/** Canonical Noon asset-upload request and successful-response contract. */
public final class NoonAssetUploadContract {

    public static final String URL =
            "https://catalog.noon.partners/_svc/mp-partner-catalog/catalog/asset/upload";
    public static final String FILE_FIELD = "file";
    private static final URI ENDPOINT = URI.create(URL);

    private NoonAssetUploadContract() {
    }

    public static String host() {
        return ENDPOINT.getHost();
    }

    public static int port() {
        return ENDPOINT.getPort() > 0 ? ENDPOINT.getPort() : 443;
    }

    public static String requireUploadPath(JsonNode response) {
        for (String field : List.of("upload_path", "uploadPath", "path", "url")) {
            String value = response == null ? "" : response.path(field).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        throw new IllegalStateException("Noon 图片上传响应缺少 upload_path。");
    }
}
