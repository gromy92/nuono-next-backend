package com.nuono.next.noonpull.datapull;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotPageProvider;
import com.nuono.next.datapull.snapshot.SnapshotPageRequest;
import com.nuono.next.noonpull.NoonInterfacePullPage;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonProductInterfaceSmokeProvider;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** One-call DP-04 Partner offer-list adapter with exact scope and page validation. */
public final class Dp04ProductSnapshotProvider
        implements SnapshotPageProvider<Dp04ProductSnapshotItem> {

    private static final String PROVIDER_CHANNEL = "NOON_PARTNER_PRODUCT_LIST";

    private final NoonProductInterfaceSmokeProvider provider;
    private final NoonPullStoreBindingResolver bindingResolver;

    public Dp04ProductSnapshotProvider(
            NoonProductInterfaceSmokeProvider provider,
            NoonPullStoreBindingResolver bindingResolver
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
    }

    @Override
    public ProviderOutcome<SnapshotPage<Dp04ProductSnapshotItem>> fetchPage(
            SnapshotPageRequest request
    ) {
        try {
            SnapshotPageRequest scope = requireRequest(request);
            NoonInterfacePullRequest pullRequest = NoonInterfacePullRequest.builder()
                    .ownerUserId(scope.getOwnerUserId())
                    .storeCode(scope.getStoreCode())
                    .siteCode(scope.getSiteCode())
                    .dataDomain(NoonPullDataDomain.PRODUCT)
                    .requestName("dp04-complete-product-snapshot")
                    .targetIdentity(scope.getScopeKey())
                    .resumePage(scope.getPageNo())
                    .build();
            requireSameScope(scope, bindingResolver.resolve(pullRequest));
            NoonInterfacePullPage page = Objects.requireNonNull(
                    provider.fetchPage(pullRequest, scope.getPageNo()),
                    "DP-04 provider page"
            );
            return ProviderOutcome.success(toSnapshotPage(scope, page));
        } catch (RuntimeException failure) {
            return NoonSnapshotProviderFailureClassifier.classify(failure, "DP04_PRODUCT_LIST");
        }
    }

    private SnapshotPageRequest requireRequest(SnapshotPageRequest request) {
        SnapshotPageRequest value = Objects.requireNonNull(request, "request");
        if (value.getOperationCode() != OperationCode.DP04
                || !PROVIDER_CHANNEL.equals(value.getProviderChannel())
                || value.getLogicalStoreId() == null
                || !hasText(value.getProjectCode())
                || !hasText(value.getStoreCode())
                || !hasText(value.getSiteCode())
                || !same(value.getProjectCode(), value.getAccountKey())) {
            throw new IllegalArgumentException("DP-04 snapshot scope mismatch");
        }
        return value;
    }

    private void requireSameScope(SnapshotPageRequest request, NoonPullStoreBinding binding) {
        if (binding == null
                || !Objects.equals(binding.getOwnerUserId(), request.getOwnerUserId())
                || !same(binding.getProjectCode(), request.getProjectCode())
                || !same(binding.getStoreCode(), request.getStoreCode())
                || !sameIgnoreCase(binding.getSiteCode(), request.getSiteCode())) {
            throw new IllegalArgumentException("DP-04 resolved binding scope mismatch");
        }
    }

    private SnapshotPage<Dp04ProductSnapshotItem> toSnapshotPage(
            SnapshotPageRequest request,
            NoonInterfacePullPage page
    ) {
        if (page.getPageNumber() != request.getPageNo()
                || page.getPageSize() < 1
                || page.getTotalItems() < 0
                || page.getItems() == null
                || page.getRequestCount() != 1) {
            throw new IllegalArgumentException("DP-04 page contract is invalid");
        }
        SnapshotCollectionAuthority authority = authority(page);
        List<Map<String, Object>> rawItems = page.getItems();
        if (page.getTotalItems() == 0
                && (!rawItems.isEmpty() || page.isHasNextPage())) {
            throw new IllegalArgumentException("DP-04 empty-page metadata conflicts");
        }
        if (page.getTotalItems() > 0 && rawItems.isEmpty()) {
            throw new IllegalArgumentException("DP-04 non-empty snapshot returned an empty page");
        }
        if (rawItems.size() > page.getTotalItems()) {
            throw new IllegalArgumentException("DP-04 page exceeds declared total");
        }

        long offset = (long) (request.getPageNo() - 1) * page.getPageSize();
        int expectedRows = offset >= page.getTotalItems()
                ? 0
                : (int) Math.min(page.getPageSize(), (long) page.getTotalItems() - offset);
        if (rawItems.size() != expectedRows) {
            throw new IllegalArgumentException("DP-04 page row count is incomplete");
        }

        int totalPages = page.getTotalItems() == 0
                ? 1
                : (int) (((long) page.getTotalItems() + page.getPageSize() - 1L)
                / page.getPageSize());
        if (request.getPageNo() > totalPages
                || page.isHasNextPage() != (request.getPageNo() < totalPages)) {
            throw new IllegalArgumentException("DP-04 pagination metadata conflicts");
        }

        List<Dp04ProductSnapshotItem> classified = new ArrayList<>();
        for (int ordinal = 0; ordinal < rawItems.size(); ordinal++) {
            classified.add(Dp04ProductSnapshotItem.fromProvider(
                    rawItems.get(ordinal),
                    request.getPageNo(),
                    ordinal
            ));
        }
        boolean lastPage = !page.isHasNextPage();
        int pageNo = request.getPageNo();
        if (authority == null) {
            return SnapshotPage.twoPassRequired(
                    pageNo, lastPage ? null : Math.addExact(pageNo, 1),
                    lastPage, totalPages, classified, rawItems.size(), 0
            );
        }
        return new SnapshotPage<>(
                pageNo, lastPage ? null : Math.addExact(pageNo, 1),
                lastPage, totalPages, classified, authority, rawItems.size(), 0
        );
    }

    private SnapshotCollectionAuthority authority(NoonInterfacePullPage page) {
        if (!hasText(page.getProviderGenerationToken())) {
            return null;
        }
        try {
            return SnapshotCollectionAuthority.fromProviderToken(
                    SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                    page.getProviderGenerationToken(),
                    null,
                    page.getTotalItems()
            );
        } catch (IllegalArgumentException invalidAuthority) {
            throw new IllegalArgumentException(
                    "DP-04 provider collection authority contract is invalid",
                    invalidAuthority
            );
        }
    }

    private boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private boolean sameIgnoreCase(String left, String right) {
        return normalize(left).toUpperCase(Locale.ROOT)
                .equals(normalize(right).toUpperCase(Locale.ROOT));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return !normalize(value).isEmpty();
    }
}
