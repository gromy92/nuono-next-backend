package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.infrastructure.mapper.ProductListingKeywordSuggestionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.productkeyword.ProductKeywordEventStatus;
import com.nuono.next.productkeyword.ProductKeywordNormalizer;
import com.nuono.next.productkeyword.ProductKeywordRecord;
import com.nuono.next.productkeyword.ProductKeywordSourceType;
import com.nuono.next.productkeyword.ProductKeywordStatus;
import com.nuono.next.productkeyword.ProductKeywordUsageEventRecord;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductListingKeywordSuggestionService {
    private static final String SOURCE_REF_TYPE = "product_listing_draft";

    private final ProductKeywordMapper keywordMapper;
    private final ProductListingKeywordSuggestionMapper suggestionMapper;
    private final ProductKeywordNormalizer normalizer;

    public ProductListingKeywordSuggestionService(
            ProductKeywordMapper keywordMapper,
            ProductListingKeywordSuggestionMapper suggestionMapper,
            ProductKeywordNormalizer normalizer
    ) {
        this.keywordMapper = keywordMapper;
        this.suggestionMapper = suggestionMapper;
        this.normalizer = normalizer;
    }

    public void sync(
            BusinessAccessContext context,
            ProductListingDraftView draftView,
            ProductListingKeywordSuggestionCommand command
    ) {
        ProductListingDraftCommand draft = requireDraft(context, draftView);
        Long draftId = draftView.getDraftId();
        Long ownerUserId = draftView.getOwnerUserId();
        Long actorUserId = context.getSessionUserId();
        String storeCode = normalizedStore(draftView.getStoreCode());
        String siteCode = siteCode(storeCode);
        Map<String, ProductListingKeywordSuggestionSet.Item> current = ProductListingKeywordSuggestionSet.from(
                command,
                siteCode,
                draftId,
                normalizer
        );
        String partnerSku = StringUtils.hasText(draft.getPsku()) ? draft.getPsku().trim() : null;
        if (!StringUtils.hasText(partnerSku)) {
            // The suggestions remain durable inside the listing draft JSON and
            // are synchronized into the PSKU-scoped keyword ledger on a later
            // save after the operator assigns the PSKU.
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        for (ProductListingKeywordSuggestionSet.Item suggestion : current.values()) {
            ProductKeywordRecord keyword = upsertObservedKeyword(
                    ownerUserId,
                    actorUserId,
                    storeCode,
                    siteCode,
                    partnerSku,
                    draftId,
                    suggestion,
                    now
            );
            keywordMapper.upsertUsageEvent(event(
                    keyword,
                    draftId,
                    suggestion,
                    ProductKeywordEventStatus.SUGGESTED,
                    actorUserId,
                    now
            ));
        }

        for (ProductKeywordUsageEventRecord previous : latestEvents(draftId).values()) {
            if (!ProductKeywordEventStatus.SUGGESTED.name().equals(previous.getEventStatus())
                    || current.containsKey(previous.getSourceRefKey())) {
                continue;
            }
            ProductListingKeywordSuggestionSet.Item removed = new ProductListingKeywordSuggestionSet.Item(
                    previous.getKeyword(),
                    previous.getKeywordNorm(),
                    localeFromRef(previous)
            );
            ProductKeywordRecord keyword = keywordMapper.selectByScopeAndNorm(
                    ownerUserId,
                    storeCode,
                    siteCode,
                    StringUtils.hasText(previous.getPartnerSku()) ? previous.getPartnerSku() : partnerSku,
                    previous.getKeywordNorm()
            );
            if (keyword != null) {
                keywordMapper.upsertUsageEvent(event(
                        keyword,
                        draftId,
                        removed,
                        ProductKeywordEventStatus.REMOVED,
                        actorUserId,
                        now
                ));
            }
        }
    }

    public ProductListingKeywordSuggestionView listForDraft(BusinessAccessContext context, ProductListingDraftView draftView) {
        requireDraft(context, draftView);
        return view(draftView.getDraftId());
    }

    public ProductListingKeywordSuggestionView latestForProductScope(
            BusinessAccessContext context,
            String storeCode,
            String partnerSku
    ) {
        requireContext(context);
        String normalizedStoreCode = normalizedStore(storeCode);
        if (!context.canAccessStore(normalizedStoreCode)) {
            throw new IllegalArgumentException("Store scope is not accessible.");
        }
        ProductListingDraftRecord draft = suggestionMapper.selectLatestDraftByProductScope(
                context.getBusinessOwnerUserId(),
                normalizedStoreCode,
                requireText(partnerSku, "PSKU is required.")
        );
        return draft == null ? ProductListingKeywordSuggestionView.empty() : view(draft.getId());
    }

    private ProductListingKeywordSuggestionView view(Long draftId) {
        ProductListingKeywordSuggestionView view = new ProductListingKeywordSuggestionView();
        view.setDraftId(draftId);
        List<ProductListingKeywordSuggestionView.Item> items = new ArrayList<>();
        for (ProductKeywordUsageEventRecord event : latestEvents(draftId).values()) {
            if (ProductKeywordEventStatus.SUGGESTED.name().equals(event.getEventStatus())) {
                items.add(new ProductListingKeywordSuggestionView.Item(
                        event.getKeyword(),
                        event.getKeywordNorm(),
                        localeFromRef(event)
                ));
            }
        }
        view.setItems(items);
        return view;
    }

    private ProductKeywordRecord upsertObservedKeyword(
            Long ownerUserId,
            Long actorUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            Long draftId,
            ProductListingKeywordSuggestionSet.Item suggestion,
            LocalDateTime now
    ) {
        ProductKeywordRecord existing = keywordMapper.selectByScopeAndNorm(
                ownerUserId,
                storeCode,
                siteCode,
                partnerSku,
                suggestion.keywordNorm
        );
        ProductKeywordRecord record = existing == null ? new ProductKeywordRecord() : existing;
        if (record.getId() == null) {
            record.setId(keywordMapper.nextKeywordId());
            record.setOwnerUserId(ownerUserId);
            record.setStoreCode(storeCode);
            record.setSiteCode(siteCode);
            record.setPartnerSku(partnerSku);
            record.setKeyword(suggestion.keyword);
            record.setKeywordNorm(suggestion.keywordNorm);
            record.setLocale(suggestion.locale);
            record.setStatus(ProductKeywordStatus.OBSERVED.name());
            record.setIntentTagsJson("[\"COMPETITOR_CANDIDATE\"]");
            record.setSourceSummaryJson("{\"listingDraftId\":" + draftId + "}");
            record.setFirstSeenAt(now);
            record.setCreatedBy(actorUserId);
        }
        record.setLastSeenAt(now);
        record.setUpdatedBy(actorUserId);
        keywordMapper.upsertKeyword(record);
        return record;
    }

    private ProductKeywordUsageEventRecord event(
            ProductKeywordRecord keyword,
            Long draftId,
            ProductListingKeywordSuggestionSet.Item suggestion,
            ProductKeywordEventStatus status,
            Long actorUserId,
            LocalDateTime occurredAt
    ) {
        String refKey = ProductListingKeywordSuggestionSet.refKey(draftId, suggestion.locale, suggestion.keywordNorm);
        ProductKeywordUsageEventRecord event = new ProductKeywordUsageEventRecord();
        event.setId(keywordMapper.nextUsageEventId());
        event.setKeywordId(keyword.getId());
        event.setOwnerUserId(keyword.getOwnerUserId());
        event.setStoreCode(keyword.getStoreCode());
        event.setSiteCode(keyword.getSiteCode());
        event.setPartnerSku(keyword.getPartnerSku());
        event.setKeyword(suggestion.keyword);
        event.setKeywordNorm(suggestion.keywordNorm);
        event.setSourceType(ProductKeywordSourceType.LISTING_DRAFT.name());
        event.setSourceRefType(SOURCE_REF_TYPE);
        event.setSourceRefId(draftId);
        event.setSourceRefKey(refKey);
        event.setEventStatus(status.name());
        event.setOccurredAt(occurredAt);
        event.setPayloadJson("{\"draftId\":" + draftId + ",\"locale\":\"" + json(suggestion.locale) + "\"}");
        event.setMetricsJson("{\"candidate\":true}");
        event.setEventNaturalKey(String.join("|", ProductKeywordSourceType.LISTING_DRAFT.name(), SOURCE_REF_TYPE,
                String.valueOf(draftId), suggestion.locale, suggestion.keywordNorm, status.name()));
        event.setCreatedBy(actorUserId);
        event.setUpdatedBy(actorUserId);
        return event;
    }

    private Map<String, ProductKeywordUsageEventRecord> latestEvents(Long draftId) {
        Map<String, ProductKeywordUsageEventRecord> latest = new LinkedHashMap<>();
        for (ProductKeywordUsageEventRecord event : suggestionMapper.listDraftSuggestionEvents(draftId)) {
            if (StringUtils.hasText(event.getSourceRefKey())) {
                latest.putIfAbsent(event.getSourceRefKey(), event);
            }
        }
        return latest;
    }

    private ProductListingDraftCommand requireDraft(BusinessAccessContext context, ProductListingDraftView view) {
        requireContext(context);
        if (view == null || view.getDraftId() == null || view.getDraft() == null) {
            throw new IllegalArgumentException("Product listing draft is required.");
        }
        if (!Objects.equals(context.getBusinessOwnerUserId(), view.getOwnerUserId())
                || !context.canAccessStore(view.getStoreCode())) {
            throw new IllegalArgumentException("Product listing draft scope is not accessible.");
        }
        return view.getDraft();
    }

    private void requireContext(BusinessAccessContext context) {
        if (context == null || context.getBusinessOwnerUserId() == null || context.getSessionUserId() == null) {
            throw new IllegalArgumentException("Business context is required.");
        }
    }

    private String localeFromRef(ProductKeywordUsageEventRecord event) {
        String ref = event.getSourceRefKey();
        if (!StringUtils.hasText(ref)) {
            return event.getSiteCode();
        }
        String[] parts = ref.split(":", 3);
        return parts.length >= 2 ? parts[1] : event.getSiteCode();
    }

    private String normalizedStore(String value) {
        return requireText(value, "Store code is required.").toUpperCase(Locale.ROOT);
    }

    private String siteCode(String storeCode) {
        String upper = normalizedStore(storeCode);
        if (upper.endsWith("-NSA") || upper.endsWith("-SAU")) {
            return "SA";
        }
        if (upper.endsWith("-NAE")) {
            return "AE";
        }
        int separator = upper.lastIndexOf('-');
        return separator >= 0 ? upper.substring(separator + 1).replaceFirst("^N", "") : "*";
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
