package com.nuono.next.noonpull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.advertising.AdvertisingCampaignObservation;
import com.nuono.next.datapull.advertising.AdvertisingCampaignPage;
import com.nuono.next.datapull.advertising.AdvertisingCampaignRef;
import com.nuono.next.datapull.advertising.AdvertisingStagedFact;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Structurally validates one real /metrics/campaigns page and isolates bad business rows. */
final class NoonAdvertisingCampaignPageParser {
    private static final int RAW_PAYLOAD_MAX_UTF8_BYTES = 1_000_000;

    private final ObjectMapper objectMapper;
    private final NoonAdvertisingMetricParser metrics;

    NoonAdvertisingCampaignPageParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.metrics = new NoonAdvertisingMetricParser();
    }

    AdvertisingCampaignPage parse(JsonNode response, int expectedPageNo) {
        if (response == null || !response.isObject() || expectedPageNo < 1) {
            throw contract("ADS_CAMPAIGN_PAGE_CONTAINER_INVALID");
        }
        rejectProviderError(response);
        JsonNode campaigns = response.get("campaigns");
        JsonNode pagination = response.get("paginationMetadata");
        if (campaigns == null || !campaigns.isArray()
                || pagination == null || !pagination.isObject()) {
            throw contract("ADS_CAMPAIGN_PAGE_CONTAINER_INVALID");
        }
        long declaredCount = nonNegativeLong(
                pagination.get("nbHits"),
                "ADS_CAMPAIGN_PAGE_EXTENT_INVALID"
        );
        long rawTotalPages = nonNegativeLong(
                pagination.get("nbPages"),
                "ADS_CAMPAIGN_PAGE_EXTENT_INVALID"
        );
        int totalPages;
        if (declaredCount == 0L) {
            if (expectedPageNo != 1 || campaigns.size() != 0 || rawTotalPages > 1L) {
                throw contract("ADS_CAMPAIGN_PAGE_EXTENT_INVALID");
            }
            totalPages = 1;
        } else {
            if (rawTotalPages < 1L || rawTotalPages > Integer.MAX_VALUE
                    || expectedPageNo > rawTotalPages || campaigns.size() == 0) {
                throw contract("ADS_CAMPAIGN_PAGE_EXTENT_INVALID");
            }
            totalPages = (int) rawTotalPages;
        }

        List<AdvertisingStagedFact> facts = new ArrayList<>();
        List<AdvertisingCampaignObservation> observations = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (JsonNode campaign : campaigns) {
            try {
                NoonAdvertisingCampaignFact fact = campaignFact(campaign);
                boolean active = NoonAdvertisingCampaignStatus.requireKnown(
                        fact.getCampaignStatus()
                );
                facts.add(AdvertisingStagedFact.campaign(fact));
                observations.add(new AdvertisingCampaignObservation(
                        new AdvertisingCampaignRef(
                                fact.getCampaignCode(),
                                fact.getCampaignName()
                        ),
                        active
                ));
            } catch (NoonAdvertisingContractException | IllegalArgumentException rowFailure) {
                skipped.add(skippedFingerprint(campaign));
            }
        }
        return new AdvertisingCampaignPage(
                expectedPageNo,
                totalPages,
                declaredCount,
                facts,
                observations,
                skipped
        );
    }

    private NoonAdvertisingCampaignFact campaignFact(JsonNode campaign) {
        if (campaign == null || !campaign.isObject()) {
            throw contract("ADS_CAMPAIGN_ROW_INVALID");
        }
        JsonNode metricNode = campaign.path("metrics");
        if (!metricNode.isObject()) {
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
        fact.setCampaignStartDate(metrics.optionalDate(firstText(
                campaign, "startDate", "startTime"
        )));
        fact.setCampaignEndDate(metrics.optionalDate(firstText(
                campaign, "endDate", "endTime"
        )));
        fact.setViews(metrics.nonNegativeLong(text(metricNode, "views")));
        fact.setClicks(metrics.nonNegativeLong(text(metricNode, "clicks")));
        fact.setOrdersCount(metrics.nonNegativeLong(text(metricNode, "orders")));
        fact.setAssistedOrders(metrics.nonNegativeLong(text(metricNode, "assistedOrders")));
        fact.setAtcCount(metrics.nonNegativeLong(text(metricNode, "atc")));
        fact.setSpendAmount(metrics.decimal(text(metricNode, "spends")));
        fact.setAdRevenue(metrics.decimal(text(metricNode, "revenue")));
        fact.setCtrPercentage(metrics.percentFraction(text(metricNode, "ctr")));
        fact.setRoas(metrics.decimal(text(metricNode, "roas")));
        fact.setCpc(metrics.decimal(text(metricNode, "cpc")));
        fact.setCps(metrics.decimal(text(metricNode, "cps")));
        fact.setCvrPercentage(metrics.percentFraction(text(metricNode, "cvr")));
        fact.setRawPayloadJson(metrics.boundedRawPayload(
                rawJson(campaign), RAW_PAYLOAD_MAX_UTF8_BYTES
        ));
        return fact;
    }

    private long nonNegativeLong(JsonNode value, String code) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0L) {
            throw contract(code);
        }
        return value.longValue();
    }

    private String skippedFingerprint(JsonNode campaign) {
        String raw = campaign == null ? "null" : rawJson(campaign);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("dp06-business-skip-v1\u001f" + raw).getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) value.append(String.format("%02x", item & 0xff));
            return value.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    private void rejectProviderError(JsonNode response) {
        JsonNode error = response.get("error");
        if (error == null || error.isNull() || error.isMissingNode()) return;
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
        if (!StringUtils.hasText(value)) throw contract(code);
        return value;
    }

    private String requiredFirstText(JsonNode node, String code, String... fields) {
        String value = firstText(node, fields);
        if (!StringUtils.hasText(value)) throw contract(code);
        return value;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) return "";
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
        for (String marker : markers) if (value != null && value.contains(marker)) return true;
        return false;
    }

    private NoonAdvertisingContractException contract(String code) {
        return new NoonAdvertisingContractException(code);
    }
}
