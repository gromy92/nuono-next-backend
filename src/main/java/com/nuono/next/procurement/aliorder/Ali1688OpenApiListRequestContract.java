package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Builds and proves the exact official buyer-order list request/response pagination contract. */
final class Ali1688OpenApiListRequestContract {
    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final Ali1688OpenApiListContract responseContract;
    private final Ali1688OpenApiJson json;

    Ali1688OpenApiListRequestContract(
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiListContract responseContract,
            Ali1688OpenApiJson json
    ) {
        this.properties = properties;
        this.responseContract = responseContract;
        this.json = json;
    }

    int pageNo(Ali1688HistoricalOrderRequest request) {
        return request.isFixedWindow() ? request.getPageNo() : parsePage(request.getProviderCursor());
    }

    int pageSize(Ali1688HistoricalOrderRequest request) {
        return request.isFixedWindow() ? request.getPageSize() : Math.max(1, properties.getPageSize());
    }

    Map<String, String> parameters(
            String accessToken,
            Ali1688HistoricalOrderRequest request
    ) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("access_token", accessToken);
        parameters.put(parameterName(properties.getPageNumberParameterName(), "page"),
                String.valueOf(pageNo(request)));
        parameters.put(parameterName(properties.getPageSizeParameterName(), "pageSize"),
                String.valueOf(pageSize(request)));
        if (!request.isFixedWindow()) return parameters;
        if (request.getModifiedFrom() != null) {
            parameters.put(parameterName(properties.getModifiedFromParameterName(), "modifyStartTime"),
                    formatModifiedTime(request.getModifiedFrom()));
        }
        parameters.put(parameterName(properties.getModifiedToParameterName(), "modifyEndTime"),
                formatModifiedTime(request.getModifiedTo()));
        parameters.put(parameterName(properties.getHistoryParameterName(), "isHis"),
                String.valueOf(request.getPartition().isHistorical()));
        return parameters;
    }

    Ali1688OpenApiListContract.Pagination pagination(
            JsonNode root,
            Ali1688HistoricalOrderRequest request,
            int rowCount
    ) {
        int pageNo = pageNo(request);
        int pageSize = pageSize(request);
        Ali1688OpenApiListContract.Pagination pagination =
                responseContract.provePagination(root, pageNo, pageSize);
        return pagination.isProven() || request.isFixedWindow()
                ? pagination
                : legacyPagination(root, pageNo, pageSize, rowCount);
    }

    private Ali1688OpenApiListContract.Pagination legacyPagination(
            JsonNode root,
            int pageNo,
            int pageSize,
            int rowCount
    ) {
        String hasMore = json.text(root, "hasMore", "hasNext", "hasNextPage");
        if (!StringUtils.hasText(hasMore)) return Ali1688OpenApiListContract.Pagination.unknown();
        boolean more = "true".equalsIgnoreCase(hasMore) || "1".equals(hasMore);
        if (!more && !"false".equalsIgnoreCase(hasMore) && !"0".equals(hasMore)) {
            return Ali1688OpenApiListContract.Pagination.unknown();
        }
        try {
            long total = more
                    ? Math.addExact(Math.multiplyExact((long) pageNo, pageSize), 1L)
                    : Math.addExact(
                            Math.multiplyExact((long) pageNo - 1L, pageSize),
                            rowCount
                    );
            int pages = Ali1688PaginationMath.expectedPages(total, pageSize);
            return Ali1688OpenApiListContract.Pagination.proven(total, pages, more);
        } catch (ArithmeticException unrepresentablePageCount) {
            return Ali1688OpenApiListContract.Pagination.unknown();
        }
    }

    private String formatModifiedTime(Instant instant) {
        if (instant == null) throw new IllegalStateException("official modified time is required");
        ZoneId zone = ZoneId.of(properties.getProviderZoneId().trim());
        String format = properties.getModifiedFromFormat().trim();
        return "ISO_OFFSET_DATE_TIME".equalsIgnoreCase(format)
                ? DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(zone))
                : DateTimeFormatter.ofPattern(format).withZone(zone).format(instant);
    }

    private String parameterName(String configuredName, String officialName) {
        String name = configuredName == null ? "" : configuredName.trim();
        if (!officialName.equals(name)) {
            throw new IllegalStateException("invalid official parameter: " + officialName);
        }
        return name;
    }

    private int parsePage(String value) {
        if (!StringUtils.hasText(value)) return 1;
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
