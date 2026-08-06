package com.nuono.next.noonpull.datapull;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** One staged DP-04 row: writable facts, presence-only evidence, or an absence-safety veto. */
public final class Dp04ProductSnapshotItem {

    private static final int IDENTITY_MAX = 100;

    private final RowKind rowKind;
    private final Map<String, Object> projectionPayload;
    private final String presencePartnerSku;
    private final String stableIdentity;

    private Dp04ProductSnapshotItem(
            RowKind rowKind,
            Map<String, Object> projectionPayload,
            String presencePartnerSku,
            String stableIdentity,
            boolean rejectUnknownFields
    ) {
        Map<String, Object> sanitized = Dp04ProductPayloadContract.sanitize(
                projectionPayload,
                rejectUnknownFields
        );
        requireStoredState(rowKind, sanitized, presencePartnerSku, stableIdentity);
        this.rowKind = rowKind;
        this.projectionPayload = Collections.unmodifiableMap(sanitized);
        this.presencePartnerSku = normalizeText(presencePartnerSku);
        this.stableIdentity = stableIdentity;
    }

    /** Classifies every provider row so a skipped fact cannot disappear from absence proof. */
    public static Dp04ProductSnapshotItem fromProvider(
            Map<String, Object> payload,
            int pageNo,
            int rowOrdinal
    ) {
        if (pageNo < 1 || rowOrdinal < 0) {
            throw new IllegalArgumentException("DP-04 provider row position is invalid");
        }
        String partnerSku = providerPartnerSku(payload);
        if (!fitsIdentity(partnerSku)) {
            return new Dp04ProductSnapshotItem(
                    RowKind.ABSENCE_UNSAFE,
                    Map.of(),
                    null,
                    "unidentified-row:" + pageNo + ':' + rowOrdinal,
                    true
            );
        }
        String stableIdentity = normalizeIdentity(partnerSku);
        Map<String, Object> sanitized;
        try {
            sanitized = Dp04ProductPayloadContract.sanitize(payload, false);
            Dp04ProductPayloadContract.requireNumericSyntax(sanitized);
        } catch (IllegalArgumentException invalidBusinessField) {
            return new Dp04ProductSnapshotItem(
                    RowKind.PRESENCE_ONLY,
                    Map.of(),
                    partnerSku,
                    stableIdentity,
                    true
            );
        }
        if (!fitsIdentity(Dp04ProductPayloadContract.productIdentity(sanitized))
                || !Dp04ProductPayloadContract.fitsTargetColumns(sanitized)) {
            return new Dp04ProductSnapshotItem(
                    RowKind.PRESENCE_ONLY, Map.of(), partnerSku, stableIdentity, true
            );
        }
        return new Dp04ProductSnapshotItem(
                RowKind.PROJECTION,
                sanitized,
                partnerSku,
                stableIdentity,
                true
        );
    }

    static Dp04ProductSnapshotItem fromStoredPayload(
            String rowKind,
            String stableIdentity,
            String presencePartnerSku,
            Map<String, Object> payload
    ) {
        return new Dp04ProductSnapshotItem(
                RowKind.valueOf(rowKind),
                payload,
                presencePartnerSku,
                stableIdentity,
                true
        );
    }

    public String getStableIdentity() {
        return stableIdentity;
    }

    public boolean isWritableProjection() {
        return rowKind == RowKind.PROJECTION;
    }

    public boolean isAbsenceReconciliationSafe() {
        return rowKind != RowKind.ABSENCE_UNSAFE;
    }

    public String getPresencePartnerSku() {
        return presencePartnerSku;
    }

    public Map<String, Object> toProjectionPayload() {
        if (!isWritableProjection()) {
            throw new IllegalStateException("DP-04 skipped row has no projection payload");
        }
        return Dp04ProductPayloadContract.deepCopy(projectionPayload);
    }

    String getRowKind() {
        return rowKind.name();
    }

    Map<String, Object> getStagedProjectionPayload() {
        return Dp04ProductPayloadContract.deepCopy(projectionPayload);
    }

    private static String normalizeIdentity(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!fitsIdentity(normalized)) {
            throw new IllegalArgumentException("DP-04 stable identity is invalid");
        }
        return "partner-sku:" + normalized.length() + ':' + normalized;
    }

    private static String providerPartnerSku(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object raw = payload.get("partner_sku");
        return raw instanceof CharSequence ? normalizeText(raw.toString()) : null;
    }

    private static boolean fitsIdentity(String value) {
        return StringUtils.hasText(value)
                && value.length() <= IDENTITY_MAX
                && value.indexOf('\0') < 0;
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void requireStoredState(
            RowKind rowKind,
            Map<String, Object> payload,
            String presencePartnerSku,
            String stableIdentity
    ) {
        Objects.requireNonNull(rowKind, "DP-04 row kind");
        if (rowKind == RowKind.ABSENCE_UNSAFE) {
            if (!payload.isEmpty() || presencePartnerSku != null || stableIdentity == null
                    || !stableIdentity.matches("unidentified-row:[1-9][0-9]*:[0-9]+")) {
                throw new IllegalArgumentException("DP-04 unidentified row state is invalid");
            }
            return;
        }
        String partnerSku = normalizeText(presencePartnerSku);
        if (!fitsIdentity(partnerSku) || !normalizeIdentity(partnerSku).equals(stableIdentity)) {
            throw new IllegalArgumentException("DP-04 presence identity is invalid");
        }
        if (rowKind == RowKind.PRESENCE_ONLY && !payload.isEmpty()) {
            throw new IllegalArgumentException("DP-04 presence-only row cannot contain facts");
        }
        if (rowKind == RowKind.PROJECTION
                && (!partnerSku.equals(Dp04ProductPayloadContract.text(payload.get("partner_sku")))
                || !fitsIdentity(Dp04ProductPayloadContract.productIdentity(payload))
                || !Dp04ProductPayloadContract.fitsTargetColumns(payload))) {
            throw new IllegalArgumentException("DP-04 projection row is invalid");
        }
    }

    private enum RowKind {
        PROJECTION,
        PRESENCE_ONLY,
        ABSENCE_UNSAFE
    }
}
