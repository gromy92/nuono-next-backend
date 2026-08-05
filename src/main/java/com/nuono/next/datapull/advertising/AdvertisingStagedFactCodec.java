package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.snapshot.SnapshotItemDescriptor;
import com.nuono.next.datapull.snapshot.SnapshotPayloadCodec;
import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Fixed-field, versioned staging codec; provider payloads never use Java polymorphic serialization. */
public final class AdvertisingStagedFactCodec
        implements SnapshotItemDescriptor<AdvertisingStagedFact>,
        SnapshotPayloadCodec<AdvertisingStagedFact> {
    private static final String VERSION = "v1";
    private static final String NULL = "~";
    private static final int CAMPAIGN_FIELDS = 25;
    private static final int QUERY_FIELDS = 21;

    @Override
    public String stableIdentity(AdvertisingStagedFact item) {
        AdvertisingStagedFact value = requireItem(item);
        if (value.getKind() == AdvertisingStagedFact.Kind.CAMPAIGN) {
            return "campaign:v1:" + sha256(lengthPrefixed(require(
                    value.getCampaignFact().getCampaignCode(), "campaignCode"
            )));
        }
        NoonAdvertisingQueryFact fact = value.getQueryFact();
        String naturalIdentity =
                lengthPrefixed(require(fact.getCampaignCode(), "campaignCode"))
                + lengthPrefixed(text(fact.getPartnerSku()))
                + lengthPrefixed(text(fact.getAdSkuCode()))
                + lengthPrefixed(require(fact.getQueryText(), "queryText"))
                + lengthPrefixed(text(fact.getQueryKind()));
        return "query:v1:" + sha256(naturalIdentity);
    }

    @Override
    public String stableContentFingerprint(AdvertisingStagedFact item) {
        return sha256(encode(item));
    }

    @Override
    public String encode(AdvertisingStagedFact item) {
        AdvertisingStagedFact value = requireItem(item);
        List<String> fields = value.getKind() == AdvertisingStagedFact.Kind.CAMPAIGN
                ? campaignFields(value.getCampaignFact())
                : queryFields(value.getQueryFact());
        List<String> encoded = new ArrayList<>(fields.size() + 2);
        encoded.add(VERSION);
        encoded.add(value.getKind().name());
        for (String field : fields) {
            encoded.add(encodeText(field));
        }
        return String.join("|", encoded);
    }

    @Override
    public AdvertisingStagedFact decode(String payload) {
        if (payload == null || payload.isEmpty() || !payload.equals(payload.trim())) {
            throw new IllegalArgumentException("advertising stage payload is required");
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 2 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("unsupported advertising stage payload");
        }
        AdvertisingStagedFact.Kind kind;
        try {
            kind = AdvertisingStagedFact.Kind.valueOf(parts[1]);
        } catch (RuntimeException invalidKind) {
            throw new IllegalArgumentException("invalid advertising stage kind", invalidKind);
        }
        int expected = kind == AdvertisingStagedFact.Kind.CAMPAIGN
                ? CAMPAIGN_FIELDS + 2
                : QUERY_FIELDS + 2;
        if (parts.length != expected) {
            throw new IllegalArgumentException("advertising stage field count mismatch");
        }
        List<String> fields = new ArrayList<>(expected - 2);
        for (int index = 2; index < parts.length; index++) {
            fields.add(decodeText(parts[index]));
        }
        return kind == AdvertisingStagedFact.Kind.CAMPAIGN
                ? AdvertisingStagedFact.campaign(decodeCampaign(fields))
                : AdvertisingStagedFact.query(decodeQuery(fields));
    }

    private List<String> campaignFields(NoonAdvertisingCampaignFact fact) {
        NoonAdvertisingCampaignFact value = java.util.Objects.requireNonNull(fact, "campaign fact");
        require(value.getCampaignCode(), "campaignCode");
        return List.of(
                text(value.getCampaignCode()),
                text(value.getCampaignName()),
                text(value.getPrimaryAdSkuCode()),
                text(value.getPrimaryPartnerSku()),
                text(value.getCampaignStatus()),
                text(value.getQcStatus()),
                text(value.getAdgroupCode()),
                date(value.getCampaignStartDate()),
                date(value.getCampaignEndDate()),
                String.valueOf(value.getViews()),
                String.valueOf(value.getClicks()),
                String.valueOf(value.getOrdersCount()),
                String.valueOf(value.getAssistedOrders()),
                String.valueOf(value.getAtcCount()),
                decimal(value.getSpendAmount()),
                decimal(value.getAdRevenue()),
                decimal(value.getCtrPercentage()),
                decimal(value.getRoas()),
                decimal(value.getCpc()),
                decimal(value.getCps()),
                decimal(value.getCvrPercentage()),
                decimal(value.getZeroOrderSpendAmount()),
                decimal(value.getZeroOrderSpendShare()),
                text(value.getRawPayloadJson()),
                ""
        );
    }

    private List<String> queryFields(NoonAdvertisingQueryFact fact) {
        NoonAdvertisingQueryFact value = java.util.Objects.requireNonNull(fact, "query fact");
        require(value.getCampaignCode(), "campaignCode");
        require(value.getQueryText(), "queryText");
        return List.of(
                text(value.getCampaignCode()),
                text(value.getCampaignName()),
                text(value.getAdSkuCode()),
                text(value.getPartnerSku()),
                text(value.getQueryText()),
                text(value.getQueryKind()),
                String.valueOf(value.getViews()),
                String.valueOf(value.getClicks()),
                String.valueOf(value.getOrdersCount()),
                String.valueOf(value.getAssistedOrders()),
                String.valueOf(value.getAtcCount()),
                decimal(value.getSpendAmount()),
                decimal(value.getAdRevenue()),
                decimal(value.getCtrPercentage()),
                decimal(value.getRoas()),
                decimal(value.getCpc()),
                decimal(value.getCps()),
                decimal(value.getCvrPercentage()),
                text(value.getRawPayloadJson()),
                "",
                ""
        );
    }

    private NoonAdvertisingCampaignFact decodeCampaign(List<String> fields) {
        NoonAdvertisingCampaignFact fact = new NoonAdvertisingCampaignFact();
        fact.setCampaignCode(require(fields.get(0), "campaignCode"));
        fact.setCampaignName(fields.get(1));
        fact.setPrimaryAdSkuCode(fields.get(2));
        fact.setPrimaryPartnerSku(fields.get(3));
        fact.setCampaignStatus(fields.get(4));
        fact.setQcStatus(fields.get(5));
        fact.setAdgroupCode(fields.get(6));
        fact.setCampaignStartDate(parseDate(fields.get(7)));
        fact.setCampaignEndDate(parseDate(fields.get(8)));
        fact.setViews(parseLong(fields.get(9)));
        fact.setClicks(parseLong(fields.get(10)));
        fact.setOrdersCount(parseLong(fields.get(11)));
        fact.setAssistedOrders(parseLong(fields.get(12)));
        fact.setAtcCount(parseLong(fields.get(13)));
        fact.setSpendAmount(parseDecimal(fields.get(14)));
        fact.setAdRevenue(parseDecimal(fields.get(15)));
        fact.setCtrPercentage(parseDecimal(fields.get(16)));
        fact.setRoas(parseDecimal(fields.get(17)));
        fact.setCpc(parseDecimal(fields.get(18)));
        fact.setCps(parseDecimal(fields.get(19)));
        fact.setCvrPercentage(parseDecimal(fields.get(20)));
        fact.setZeroOrderSpendAmount(parseDecimal(fields.get(21)));
        fact.setZeroOrderSpendShare(parseDecimal(fields.get(22)));
        fact.setRawPayloadJson(fields.get(23));
        requireReservedEmpty(fields.get(24));
        return fact;
    }

    private NoonAdvertisingQueryFact decodeQuery(List<String> fields) {
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setCampaignCode(require(fields.get(0), "campaignCode"));
        fact.setCampaignName(fields.get(1));
        fact.setAdSkuCode(fields.get(2));
        fact.setPartnerSku(fields.get(3));
        fact.setQueryText(require(fields.get(4), "queryText"));
        fact.setQueryKind(fields.get(5));
        fact.setViews(parseLong(fields.get(6)));
        fact.setClicks(parseLong(fields.get(7)));
        fact.setOrdersCount(parseLong(fields.get(8)));
        fact.setAssistedOrders(parseLong(fields.get(9)));
        fact.setAtcCount(parseLong(fields.get(10)));
        fact.setSpendAmount(parseDecimal(fields.get(11)));
        fact.setAdRevenue(parseDecimal(fields.get(12)));
        fact.setCtrPercentage(parseDecimal(fields.get(13)));
        fact.setRoas(parseDecimal(fields.get(14)));
        fact.setCpc(parseDecimal(fields.get(15)));
        fact.setCps(parseDecimal(fields.get(16)));
        fact.setCvrPercentage(parseDecimal(fields.get(17)));
        fact.setRawPayloadJson(fields.get(18));
        requireReservedEmpty(fields.get(19));
        requireReservedEmpty(fields.get(20));
        return fact;
    }

    private AdvertisingStagedFact requireItem(AdvertisingStagedFact item) {
        return java.util.Objects.requireNonNull(item, "item");
    }

    private String require(String value, String name) {
        return AdvertisingAdvertiser.requireIdentity(value, name);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private String decimal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).toPlainString();
    }

    private long parseLong(String value) {
        long parsed = Long.parseLong(require(value, "long"));
        if (parsed < 0L) {
            throw new IllegalArgumentException("advertising counts must not be negative");
        }
        return parsed;
    }

    private BigDecimal parseDecimal(String value) {
        return new BigDecimal(require(value, "decimal"));
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value);
    }

    private void requireReservedEmpty(String value) {
        if (value == null || !value.isEmpty()) {
            throw new IllegalArgumentException("reserved advertising stage field must be empty");
        }
    }

    private String encodeText(String value) {
        if (value == null) {
            return NULL;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String decodeText(String encoded) {
        if (NULL.equals(encoded)) {
            return null;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (RuntimeException invalidBase64) {
            throw new IllegalArgumentException("invalid advertising stage text", invalidBase64);
        }
    }

    private String lengthPrefixed(String value) {
        return value.length() + ":" + value + "|";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                result.append(String.format("%02x", part & 0xff));
            }
            return result.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
