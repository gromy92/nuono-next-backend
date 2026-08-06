package com.nuono.next.officialwarehouse.datapull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotPageProvider;
import com.nuono.next.datapull.snapshot.SnapshotPageRequest;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.noonpull.datapull.NoonSnapshotProviderFailureClassifier;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.PullRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One-call DP-07-A inventory page adapter with explicit last-page proof. */
public final class Dp07InventorySnapshotProvider
        implements SnapshotPageProvider<Dp07InventorySnapshotItem> {

    private static final String PROVIDER_CHANNEL = "NOON_FBN_INVENTORY";

    private final OfficialWarehouseFbnInventoryProvider provider;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final ObjectMapper objectMapper;

    public Dp07InventorySnapshotProvider(
            OfficialWarehouseFbnInventoryProvider provider,
            NoonPullStoreBindingResolver bindingResolver,
            ObjectMapper objectMapper
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> fetchPage(
            SnapshotPageRequest request
    ) {
        try {
            SnapshotPageRequest scope = requireRequest(request);
            NoonInterfacePullRequest bindingRequest = NoonInterfacePullRequest.builder()
                    .ownerUserId(scope.getOwnerUserId())
                    .storeCode(scope.getStoreCode())
                    .siteCode(scope.getSiteCode())
                    .dataDomain(NoonPullDataDomain.PRODUCT)
                    .requestName("dp07a-complete-fbn-inventory")
                    .targetIdentity(scope.getScopeKey())
                    .resumePage(scope.getPageNo())
                    .build();
            requireSameScope(scope, bindingResolver.resolve(bindingRequest));
            InventoryPage page = provider.fetchPage(
                    new PullRequest(
                            scope.getOwnerUserId(),
                            scope.getStoreCode(),
                            scope.getSiteCode()
                    ),
                    scope.getPageNo()
            );
            return ProviderOutcome.success(toSnapshotPage(scope, page));
        } catch (Dp07InventoryColumnContract.ContainerContractException contractFailure) {
            return ProviderOutcome.contractError("DP07A_INVENTORY_CONTAINER_CAPACITY_ERROR");
        } catch (Dp07InventorySnapshotItem.ProviderRowContractException contractFailure) {
            return ProviderOutcome.contractError("DP07A_INVENTORY_ROW_CONTRACT_ERROR");
        } catch (RuntimeException failure) {
            return NoonSnapshotProviderFailureClassifier.classify(failure, "DP07A_INVENTORY");
        }
    }

    private SnapshotPageRequest requireRequest(SnapshotPageRequest request) {
        SnapshotPageRequest value = Objects.requireNonNull(request, "request");
        if (value.getOperationCode() != OperationCode.DP07A
                || !PROVIDER_CHANNEL.equals(value.getProviderChannel())
                || value.getLogicalStoreId() == null
                || !hasText(value.getProjectCode())
                || !hasText(value.getStoreCode())
                || !hasText(value.getSiteCode())
                || !same(value.getProjectCode(), value.getAccountKey())) {
            throw new IllegalArgumentException("DP-07-A snapshot scope mismatch");
        }
        return value;
    }

    private void requireSameScope(SnapshotPageRequest request, NoonPullStoreBinding binding) {
        if (binding == null
                || !Objects.equals(binding.getOwnerUserId(), request.getOwnerUserId())
                || !same(binding.getProjectCode(), request.getProjectCode())
                || !same(binding.getStoreCode(), request.getStoreCode())
                || !sameIgnoreCase(binding.getSiteCode(), request.getSiteCode())) {
            throw new IllegalArgumentException("DP-07-A resolved binding scope mismatch");
        }
    }

    private SnapshotPage<Dp07InventorySnapshotItem> toSnapshotPage(
            SnapshotPageRequest request,
            InventoryPage page
    ) {
        InventoryPage value = Objects.requireNonNull(page, "DP-07-A provider page");
        if (value.page != request.getPageNo() || value.items == null || value.rawResponse == null) {
            throw new IllegalArgumentException("DP-07-A page contract is invalid");
        }
        SnapshotCollectionAuthority authority = authority(value);
        if (value.completeExport) {
            if (value.page != 1 || value.hasNextPage
                    || !Boolean.FALSE.equals(value.hasNextPageEvidence)
                    || !Objects.equals(value.totalPages, 1)) {
                throw new IllegalArgumentException("DP-07-A complete export metadata conflicts");
            }
        } else if (value.hasNextPageEvidence == null && value.totalPages == null) {
            throw new IllegalArgumentException("DP-07-A last-page evidence is missing");
        }
        if (value.totalPages != null) {
            if (value.totalPages < value.page
                    || (value.hasNextPage && value.page >= value.totalPages)
                    || (!value.hasNextPage && value.page < value.totalPages)) {
                throw new IllegalArgumentException("DP-07-A pagination metadata conflicts");
            }
        }
        if (value.hasNextPage && value.items.isEmpty()) {
            throw new IllegalArgumentException("DP-07-A non-last page is empty");
        }
        if (value.declaredCollectionCount != null
                && (value.items.size() > value.declaredCollectionCount
                || (value.completeExport
                && value.items.size() != value.declaredCollectionCount))) {
            throw new IllegalArgumentException(
                    "DP-07-A provider collection authority contract conflicts with rows"
            );
        }

        List<Dp07InventorySnapshotItem> accepted = new ArrayList<>();
        List<String> skippedComparisonFingerprints = new ArrayList<>();
        for (InventoryItem item : value.items) {
            java.util.Optional<Dp07InventorySnapshotItem> classified =
                    Dp07InventorySnapshotItem.fromProvider(item, objectMapper);
            if (classified.isPresent()) {
                accepted.add(classified.get());
            } else {
                skippedComparisonFingerprints.add(
                        Dp07BusinessSkippedRowFingerprint.from(item, objectMapper)
                );
            }
        }
        boolean lastPage = !value.hasNextPage;
        Integer totalPages = value.totalPages != null
                ? value.totalPages
                : lastPage ? value.page : null;
        int skipped = value.items.size() - accepted.size();
        if (authority == null) {
            return SnapshotPage.twoPassRequired(
                    value.page, lastPage ? null : Math.addExact(value.page, 1),
                    lastPage, totalPages, accepted, value.items.size(), skipped,
                    skippedComparisonFingerprints
            );
        }
        return new SnapshotPage<>(
                value.page, lastPage ? null : Math.addExact(value.page, 1),
                lastPage, totalPages, accepted, authority, value.items.size(), skipped
        );
    }

    private SnapshotCollectionAuthority authority(InventoryPage page) {
        if (page.completeExport) {
            byte[] responseBytes = page.exactResponseBytes();
            if (responseBytes == null
                    || page.declaredCollectionCount == null
                    || page.declaredCollectionCount != page.items.size()) {
                throw new IllegalArgumentException(
                        "DP-07-A complete response authority contract is missing or invalid"
                );
            }
            return SnapshotCollectionAuthority.fromCompleteResponse(
                    responseBytes, page.declaredCollectionCount
            );
        }
        boolean generation = hasText(page.providerGenerationToken);
        boolean export = hasText(page.providerExportToken);
        if (export || (page.declaredCollectionCount != null
                && page.declaredCollectionCount < 0L)
                || (generation && page.declaredCollectionCount == null)) {
            throw new IllegalArgumentException(
                    "DP-07-A provider collection authority contract is missing or invalid"
            );
        }
        if (!generation) {
            return null;
        }
        try {
            return SnapshotCollectionAuthority.fromProviderToken(
                    SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                    page.providerGenerationToken,
                    null,
                    page.declaredCollectionCount
            );
        } catch (IllegalArgumentException invalidAuthority) {
            throw new IllegalArgumentException(
                    "DP-07-A provider collection authority contract is invalid",
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
