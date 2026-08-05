package com.nuono.next.noonpull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.advertising.AdvertisingCampaignRef;
import com.nuono.next.datapull.advertising.AdvertisingCampaignEnumerationAuthority;
import com.nuono.next.datapull.advertising.AdvertisingDashboard;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Validates and maps one complete Ad Manager dashboard response. */
final class NoonAdvertisingDashboardParser {
    private static final int RAW_PAYLOAD_MAX_UTF8_BYTES = 1_000_000;

    private final ObjectMapper objectMapper;
    private final NoonAdvertisingMetricParser metrics;

    NoonAdvertisingDashboardParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.metrics = new NoonAdvertisingMetricParser();
    }

    AdvertisingDashboard parse(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw contract("ADS_DASHBOARD_CONTAINER_INVALID");
        }
        rejectProviderError(response);
        AdvertisingCampaignEnumerationAuthority authority = authority(response);
        JsonNode campaignsNode = response.get("campaigns");
        JsonNode metricsNode = response.path("current").get("campaignMetrics");
        if (campaignsNode == null || !campaignsNode.isArray()
                || metricsNode == null || !metricsNode.isObject()) {
            throw contract("ADS_DASHBOARD_CONTAINER_INVALID");
        }
        if (authority.getDeclaredCampaignCount() != campaignsNode.size()) {
            throw contract("ADS_CAMPAIGN_COUNT_MISMATCH");
        }
        Map<String, JsonNode> metricsByCode = new LinkedHashMap<>();
        metricsNode.fields().forEachRemaining(
                entry -> metricsByCode.put(entry.getKey(), entry.getValue())
        );
        List<NoonAdvertisingCampaignFact> facts = new ArrayList<>();
        List<AdvertisingCampaignRef> active = new ArrayList<>();
        Map<String, Boolean> activeByIdentity = new LinkedHashMap<>();
        int businessSkipped = 0;
        for (JsonNode campaign : campaignsNode) {
            if (campaign == null || !campaign.isObject()) {
                throw contract("ADS_CAMPAIGN_CONTAINER_INVALID");
            }
            String code = requiredText(campaign, "campaignCode", "ADS_CAMPAIGN_CODE_MISSING");
            boolean activeStatus = NoonAdvertisingCampaignStatus.requireKnown(requiredFirstText(
                    campaign,
                    "ADS_CAMPAIGN_STATUS_MISSING",
                    "effectiveStatus",
                    "status"
            ));
            Boolean firstStatus = activeByIdentity.putIfAbsent(code, activeStatus);
            if (firstStatus != null && firstStatus != activeStatus) {
                throw contract("ADS_CAMPAIGN_STATUS_DRIFT");
            }
            boolean firstIdentity = firstStatus == null;
            try {
                NoonAdvertisingCampaignFact fact = campaignFact(
                        campaign,
                        metricsByCode.get(code)
                );
                facts.add(fact);
                if (firstIdentity && activeStatus) {
                    active.add(new AdvertisingCampaignRef(code, fact.getCampaignName()));
                }
            } catch (NoonAdvertisingContractException rowFailure) {
                if (!isBusinessRowFailure(rowFailure)) {
                    throw rowFailure;
                }
                businessSkipped = Math.incrementExact(businessSkipped);
                if (firstIdentity && activeStatus) {
                    active.add(new AdvertisingCampaignRef(code, firstText(
                            campaign,
                            "name",
                            "campaignName"
                    )));
                }
            }
        }
        return new AdvertisingDashboard(facts, active, authority, businessSkipped);
    }

    private AdvertisingCampaignEnumerationAuthority authority(JsonNode response) {
        JsonNode node = response.get("campaignCollectionAuthority");
        if (node == null || !node.isObject()) {
            throw contract("ADS_CAMPAIGN_AUTHORITY_MISSING");
        }
        JsonNode count = node.get("declaredCampaignCount");
        JsonNode complete = node.get("complete");
        if (count == null || !count.isIntegralNumber() || !count.canConvertToLong()
                || complete == null || !complete.isBoolean()) {
            throw contract("ADS_CAMPAIGN_AUTHORITY_INVALID");
        }
        if (!complete.booleanValue()) {
            throw contract("ADS_CAMPAIGN_ENUMERATION_INCOMPLETE");
        }
        String generation = authorityText(node, "generationToken");
        String asOf = authorityText(node, "asOfUtc");
        try {
            return AdvertisingCampaignEnumerationAuthority.fromProviderFields(
                    generation,
                    OffsetDateTime.parse(asOf).withOffsetSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime(),
                    count.longValue(),
                    true
            );
        } catch (DateTimeParseException | IllegalArgumentException invalid) {
            throw contract("ADS_CAMPAIGN_AUTHORITY_INVALID");
        }
    }

    private String authorityText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw contract("ADS_CAMPAIGN_AUTHORITY_INVALID");
        }
        String text = value.textValue();
        if (!StringUtils.hasText(text) || !text.equals(text.trim())) {
            throw contract("ADS_CAMPAIGN_AUTHORITY_INVALID");
        }
        return text;
    }

    private NoonAdvertisingCampaignFact campaignFact(JsonNode campaign, JsonNode metricNode) {
        JsonNode safeMetrics = metricNode == null || metricNode.isNull()
                ? objectMapper.createObjectNode()
                : metricNode;
        if (!safeMetrics.isObject()) {
            throw contract("ADS_CAMPAIGN_METRICS_INVALID");
        }
        NoonAdvertisingCampaignFact fact = new NoonAdvertisingCampaignFact();
        fact.setCampaignCode(metrics.boundedText(
                requiredText(campaign, "campaignCode", "ADS_CAMPAIGN_CODE_MISSING"), 120
        ));
        fact.setCampaignName(metrics.boundedText(
                firstText(campaign, "name", "campaignName"), 500
        ));
        fact.setCampaignStatus(metrics.boundedText(requiredFirstText(
                campaign,
                "ADS_CAMPAIGN_STATUS_MISSING",
                "effectiveStatus",
                "status"
        ), 80));
        fact.setQcStatus(metrics.boundedText(
                firstText(campaign, "qcStatus", "qc_status"), 80
        ));
        fact.setAdgroupCode(metrics.boundedText(text(campaign, "adgroupCode"), 120));
        fact.setCampaignStartDate(metrics.optionalDate(firstText(campaign, "startDate", "startTime")));
        fact.setCampaignEndDate(metrics.optionalDate(firstText(campaign, "endDate", "endTime")));
        fact.setViews(metrics.nonNegativeLong(text(safeMetrics, "views")));
        fact.setClicks(metrics.nonNegativeLong(text(safeMetrics, "clicks")));
        fact.setOrdersCount(metrics.nonNegativeLong(text(safeMetrics, "orders")));
        fact.setAssistedOrders(metrics.nonNegativeLong(text(safeMetrics, "assistedOrders")));
        fact.setAtcCount(metrics.nonNegativeLong(text(safeMetrics, "atc")));
        fact.setSpendAmount(metrics.decimal(text(safeMetrics, "spends")));
        fact.setAdRevenue(metrics.decimal(text(safeMetrics, "revenue")));
        fact.setCtrPercentage(metrics.percentFraction(text(safeMetrics, "ctr")));
        fact.setRoas(metrics.decimal(text(safeMetrics, "roas")));
        fact.setCpc(metrics.decimal(text(safeMetrics, "cpc")));
        fact.setCps(metrics.decimal(text(safeMetrics, "cps")));
        fact.setCvrPercentage(metrics.percentFraction(text(safeMetrics, "cvr")));
        fact.setRawPayloadJson(metrics.boundedRawPayload(
                rawJson(campaign), RAW_PAYLOAD_MAX_UTF8_BYTES
        ));
        return fact;
    }

    private void rejectProviderError(JsonNode response) {
        JsonNode error = response.get("error");
        if (error == null || error.isNull() || error.isMissingNode()) {
            return;
        }
        String value = error.isTextual() ? error.asText("") : error.toString();
        String normalized = value.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "captcha", "risk", "rate limit", "too many", "ip_channel")) {
            throw new NoonAdvertisingRiskException();
        }
        if (containsAny(normalized, "unauthorized", "invalid session", "login", "signin")) {
            throw new NoonAuthenticationRequiredException("Noon Ads authorization is required");
        }
        throw contract("ADS_PROVIDER_ERROR_RESPONSE");
    }

    private String requiredText(JsonNode node, String field, String code) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) {
            throw contract(code);
        }
        return value;
    }

    private String requiredFirstText(JsonNode node, String code, String... fields) {
        String value = firstText(node, fields);
        if (!StringUtils.hasText(value)) {
            throw contract(code);
        }
        return value;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private String rawJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException encodingFailure) {
            throw new NoonAdvertisingContractException(
                    "ADS_RAW_PAYLOAD_ENCODING_FAILED",
                    encodingFailure
            );
        }
    }

    private boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value != null && value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBusinessRowFailure(NoonAdvertisingContractException failure) {
        String code = failure.getSanitizedCode();
        return "ADS_CAMPAIGN_METRICS_INVALID".equals(code)
                || "ADS_CAMPAIGN_DATE_INVALID".equals(code)
                || "ADS_NEGATIVE_COUNT".equals(code)
                || "ADS_COUNT_INVALID".equals(code)
                || "ADS_NUMBER_INVALID".equals(code)
                || "ADS_NUMBER_OUT_OF_RANGE".equals(code)
                || "ADS_FIELD_TOO_LONG".equals(code)
                || "ADS_FIELD_INVALID".equals(code)
                || "ADS_CAMPAIGN_DATE_OUT_OF_RANGE".equals(code);
    }

    private NoonAdvertisingContractException contract(String code) {
        return new NoonAdvertisingContractException(code);
    }
}
