package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseAsnSyncThrottleMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.noonauth.NoonAuthRecoveryTriggerPolicy;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonPullFailureType;
import com.nuono.next.officialwarehouse.OfficialWarehouseAsnListSyncSupport.NoonAsnListRow;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnListSyncThrottleRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.web.ApiProblemException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

final class OfficialWarehouseAsnListRemoteExecutor {
    private static final int PER_PAGE = 50;
    private static final int MAX_PAGES = 50;
    private static final int COOLDOWN_MINUTES = 60;
    private static final DateTimeFormatter RETRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OfficialWarehouseMapper mapper;
    private final OfficialWarehouseNoonInboundClient client;
    private final ObjectMapper objectMapper;
    private final NoonPullFailurePolicy failurePolicy;
    private OfficialWarehouseAsnSyncThrottleMapper throttleMapper;

    OfficialWarehouseAsnListRemoteExecutor(
            OfficialWarehouseMapper mapper,
            OfficialWarehouseNoonInboundClient client,
            ObjectMapper objectMapper,
            NoonPullFailurePolicy failurePolicy
    ) {
        this.mapper = mapper;
        this.client = client;
        this.objectMapper = objectMapper;
        this.failurePolicy = failurePolicy;
    }

    void setThrottleMapper(OfficialWarehouseAsnSyncThrottleMapper throttleMapper) {
        this.throttleMapper = throttleMapper;
    }

    AsnListSyncView execute(
            NoonSession session,
            NoonSalesReportBinding binding,
            Long ownerUserId,
            StoreSiteRecord site,
            Long operatorUserId,
            RowWriter rowWriter
    ) {
        String claimToken = claim(ownerUserId, site, operatorUserId);
        try {
            AsnListSyncView result = new AsnListSyncView();
            int page = 1;
            int totalPages = 1;
            while (page <= totalPages && page <= MAX_PAGES) {
                JsonNode data = fetchPage(session, binding, page, totalPages).path("data");
                JsonNode rows = data.path("rows");
                if (rows.isArray()) {
                    result.fetched += rows.size();
                    for (JsonNode rowNode : rows) {
                        NoonAsnListRow row = OfficialWarehouseAsnListSyncSupport.parseRow(rowNode);
                        rowWriter.write(result, ownerUserId, site, binding, session, row, operatorUserId);
                    }
                }
                Integer parsedTotalPages = intValue(data.path("pagination"), "totalPages");
                if (parsedTotalPages != null && parsedTotalPages > 0) {
                    totalPages = parsedTotalPages;
                }
                result.pages = page;
                Boolean hasNextPage = booleanValue(data.path("pagination"), "hasNextPage");
                if (Boolean.FALSE.equals(hasNextPage) && page >= totalPages) {
                    break;
                }
                page++;
            }
            return result;
        } catch (RuntimeException failure) {
            if (shouldRelease(failure)) {
                release(ownerUserId, site, claimToken);
            }
            throw failure;
        }
    }

    String claim(Long ownerUserId, StoreSiteRecord site, Long operatorUserId) {
        String claimToken = UUID.randomUUID().toString();
        mapper.claimOfficialWarehouseAsnListSync(
                ownerUserId, site.storeCode, site.siteCode, claimToken, operatorUserId
        );
        AsnListSyncThrottleRecord throttle = mapper.selectOfficialWarehouseAsnListSyncThrottle(
                ownerUserId, site.storeCode, site.siteCode
        );
        if (throttle != null && claimToken.equals(throttle.claimToken)) {
            return claimToken;
        }
        LocalDateTime lastStartedAt = throttle == null || throttle.lastStartedAt == null
                ? LocalDateTime.now()
                : throttle.lastStartedAt;
        LocalDateTime nextAllowedAt = lastStartedAt.plusMinutes(COOLDOWN_MINUTES);
        long retryAfterSeconds = Math.max(1L, Duration.between(LocalDateTime.now(), nextAllowedAt).getSeconds());
        long retryAfterMinutes = Math.max(1L, (retryAfterSeconds + 59L) / 60L);
        throw new ApiProblemException(
                HttpStatus.TOO_MANY_REQUESTS,
                "OFFICIAL_WAREHOUSE_ASN_SYNC_RATE_LIMITED",
                "RATE_LIMITED",
                "SYNC_ASN_LIST",
                "ASN 列表每小时最多同步一次，请在 " + retryAfterMinutes + " 分钟后重试。",
                true,
                false,
                null,
                Map.of(
                        "cooldownMinutes", COOLDOWN_MINUTES,
                        "retryAfterSeconds", retryAfterSeconds,
                        "nextAllowedAt", nextAllowedAt.format(RETRY_FORMAT)
                ),
                null
        );
    }

    private JsonNode fetchPage(
            NoonSession session,
            NoonSalesReportBinding binding,
            int page,
            int totalPages
    ) {
        ObjectNode body = OfficialWarehouseAsnListSyncSupport.buildListRequest(
                objectMapper, binding.getPartnerId(), page, PER_PAGE, totalPages
        );
        return client.syncAsnList(
                session,
                binding,
                NoonCallContext.appointment(
                        "OFFICIAL_WAREHOUSE_ASN_SYNC",
                        binding.getStoreCode() + "/" + binding.getSiteCode(),
                        "ASN_LIST"
                ),
                body
        );
    }

    private boolean shouldRelease(Throwable failure) {
        String details = failureDetails(failure);
        if (!NoonAuthenticationFailureClassifier.hasPermanentAuthenticationFailureEvidence(failure)
                && (NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure)
                || NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry(details))) {
            return true;
        }
        NoonPullFailureType type = failurePolicy.classify(details);
        return type == NoonPullFailureType.PROVIDER_UNAVAILABLE || type == NoonPullFailureType.TIMEOUT;
    }

    private void release(Long ownerUserId, StoreSiteRecord site, String claimToken) {
        if (throttleMapper == null) {
            return;
        }
        try {
            throttleMapper.release(ownerUserId, site.storeCode, site.siteCode, claimToken);
        } catch (RuntimeException ignored) {
            // Best effort: the original provider failure remains authoritative.
        }
    }

    private String failureDetails(Throwable failure) {
        StringBuilder details = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                details.append(' ').append(current.getMessage());
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return details.toString().trim();
    }

    private Integer intValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private Boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isBoolean() ? value.asBoolean() : null;
    }

    @FunctionalInterface
    interface RowWriter {
        void write(
                AsnListSyncView result,
                Long ownerUserId,
                StoreSiteRecord site,
                NoonSalesReportBinding binding,
                NoonSession session,
                NoonAsnListRow row,
                Long operatorUserId
        );
    }
}
