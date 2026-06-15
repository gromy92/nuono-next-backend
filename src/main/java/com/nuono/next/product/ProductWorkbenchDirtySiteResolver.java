package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
class ProductWorkbenchDirtySiteResolver {

    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter NOON_OFFER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ObjectMapper objectMapper;

    ProductWorkbenchDirtySiteResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    List<String> resolveDirtySiteCodes(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline
    ) {
        LinkedHashSet<String> dirtySiteCodes = new LinkedHashSet<>();
        if (draft == null) {
            return new ArrayList<>();
        }
        Map<String, Map<String, Object>> draftOffers = siteOfferMap(draft.getSiteOffers());
        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(baseline != null ? baseline.getSiteOffers() : null);
        LinkedHashSet<String> siteCodes = new LinkedHashSet<>();
        siteCodes.addAll(draftOffers.keySet());
        siteCodes.addAll(baselineOffers.keySet());
        for (String siteCode : siteCodes) {
            Map<String, Object> draftOffer = draftOffers.get(siteCode);
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (!objectMapper.valueToTree(siteOfferComparable(
                    draftOffer != null ? draftOffer : new LinkedHashMap<>()
            )).equals(objectMapper.valueToTree(siteOfferComparable(
                    baselineOffer != null ? baselineOffer : new LinkedHashMap<>()
            )))) {
                dirtySiteCodes.add(siteCode);
            }
        }
        return new ArrayList<>(dirtySiteCodes);
    }

    private Map<String, Map<String, Object>> siteOfferMap(List<Map<String, Object>> siteOffers) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (siteOffers == null) {
            return map;
        }
        for (Map<String, Object> siteOffer : siteOffers) {
            map.put(siteOfferCode(siteOffer), new LinkedHashMap<>(siteOffer));
        }
        return map;
    }

    private Map<String, Object> siteOfferComparable(Map<String, Object> siteOffer) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        putIfNotNull(comparable, "storeCode", siteOffer.get("storeCode"));
        putIfNotNull(comparable, "site", siteOffer.get("site"));
        putComparableDecimalIfPresent(comparable, "price", siteOffer.get("price"));
        putComparableDecimalIfPresent(comparable, "salePrice", siteOffer.get("salePrice"));
        putIfNotNull(comparable, "saleStart", normalizeOfferDateForNoon(siteOffer.get("saleStart")));
        putIfNotNull(comparable, "saleEnd", normalizeOfferDateForNoon(siteOffer.get("saleEnd")));
        putComparableDecimalIfPresent(comparable, "priceMin", siteOffer.get("priceMin"));
        putComparableDecimalIfPresent(comparable, "priceMax", siteOffer.get("priceMax"));
        putComparableBooleanIfPresent(comparable, "isActive", siteOffer.get("isActive"));
        putComparableDecimalIfPresent(comparable, "idWarranty", siteOffer.get("idWarranty"));
        putIfNotNull(comparable, "offerNote", siteOffer.get("offerNote"));
        return comparable;
    }

    private String siteOfferCode(Map<String, Object> siteOffer) {
        return textValue(siteOffer.get("storeCode"));
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = textValue(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "active".equalsIgnoreCase(text);
    }

    private String normalizeOfferDateForNoon(Object value) {
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text, FETCH_TIME_FORMATTER).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(text).format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putComparableDecimalIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        BigDecimal decimal = asBigDecimal(value);
        if (decimal == null) {
            target.put(key, value);
            return;
        }
        target.put(key, decimal.stripTrailingZeros().toPlainString());
    }

    private void putComparableBooleanIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        target.put(key, truthy(value));
    }
}
