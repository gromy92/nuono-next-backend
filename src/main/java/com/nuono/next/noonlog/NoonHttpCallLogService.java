package com.nuono.next.noonlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.NoonHttpCallLogMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class NoonHttpCallLogService {

    private static final int MAX_SUMMARY_LENGTH = 20000;

    private final NoonHttpCallLogMapper mapper;
    private final ObjectMapper objectMapper;

    public NoonHttpCallLogService(NoonHttpCallLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(
            HttpRequest request,
            Integer responseStatusCode,
            String responseBody,
            Long elapsedMs,
            String status,
            String failureType,
            String errorMessage
    ) {
        if (request == null) {
            return;
        }
        NoonHttpCallLogContext context = NoonHttpCallLogContextHolder.current();
        URI uri = request.uri();
        NoonHttpCallLogRecord row = new NoonHttpCallLogRecord();
        row.id = mapper.nextLogId();
        row.occurredAt = LocalDateTime.now();
        row.sourceModule = firstNonBlank(context == null ? null : context.sourceModule, "NOON_GATEWAY");
        row.operation = firstNonBlank(context == null ? null : context.operation, "HTTP_CALL");
        row.ownerUserId = context == null ? null : context.ownerUserId;
        row.storeCode = context == null ? null : trim(context.storeCode);
        row.siteCode = context == null ? null : trim(context.siteCode);
        row.projectCode = context == null ? null : trim(context.projectCode);
        row.partnerId = context == null ? null : trim(context.partnerId);
        row.businessType = context == null ? null : trim(context.businessType);
        row.businessId = context == null ? null : trim(context.businessId);
        row.businessRef = context == null ? null : trim(context.businessRef);
        row.httpMethod = trim(request.method());
        row.host = uri == null ? null : trim(uri.getHost());
        row.path = uri == null ? null : truncate(uri.getPath(), 500);
        row.queryHash = uri == null ? null : sha256(uri.getRawQuery());
        row.requestSummaryJson = summarize(context == null ? null : context.requestSummaryJson);
        row.requestHash = sha256(context == null ? null : context.requestSummaryJson);
        row.responseStatusCode = responseStatusCode;
        row.responseSummaryJson = summarize(responseBody);
        row.responseHash = sha256(responseBody);
        row.elapsedMs = elapsedMs;
        row.status = firstNonBlank(status, "UNKNOWN");
        row.failureType = trim(failureType);
        row.errorMessage = truncate(errorMessage, 1000);
        mapper.insert(row);
    }

    public List<NoonHttpCallLogView> listRecent(String businessType, String businessId, String businessRef, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return mapper.listRecent(trim(businessType), trim(businessId), trim(businessRef), safeLimit)
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    private NoonHttpCallLogView toView(NoonHttpCallLogRecord row) {
        NoonHttpCallLogView view = new NoonHttpCallLogView();
        view.id = row.id == null ? null : String.valueOf(row.id);
        view.occurredAt = row.occurredAt == null ? null : row.occurredAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        view.sourceModule = row.sourceModule;
        view.operation = row.operation;
        view.storeCode = row.storeCode;
        view.siteCode = row.siteCode;
        view.projectCode = row.projectCode;
        view.partnerId = row.partnerId;
        view.businessType = row.businessType;
        view.businessId = row.businessId;
        view.businessRef = row.businessRef;
        view.httpMethod = row.httpMethod;
        view.host = row.host;
        view.path = row.path;
        view.responseStatusCode = row.responseStatusCode;
        view.elapsedMs = row.elapsedMs;
        view.status = row.status;
        view.failureType = row.failureType;
        view.errorMessage = row.errorMessage;
        view.requestSummaryJson = row.requestSummaryJson;
        view.responseSummaryJson = row.responseSummaryJson;
        return view;
    }

    private String summarize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_SUMMARY_LENGTH) {
            return trimmed;
        }
        try {
            return objectMapper.writeValueAsString(new SummaryPreview(trimmed.length(), trimmed.substring(0, MAX_SUMMARY_LENGTH)));
        } catch (Exception exception) {
            return trimmed.substring(0, MAX_SUMMARY_LENGTH);
        }
    }

    private static String sha256(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = alphabet[value >>> 4];
            output[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(output);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trim(value);
            if (StringUtils.hasText(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String truncate(String value, int maxLength) {
        String trimmed = trim(value);
        if (trimmed == null || trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private static final class SummaryPreview {
        public final boolean truncated = true;
        public final int originalLength;
        public final String preview;

        private SummaryPreview(int originalLength, String preview) {
            this.originalLength = originalLength;
            this.preview = preview;
        }
    }
}
