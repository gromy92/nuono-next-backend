package com.nuono.next.noonpull.datapull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nuono.next.datapull.report.ReportProviderCapabilities;
import com.nuono.next.datapull.report.ReportProviderCapabilitySource;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotPageProvider;
import com.nuono.next.datapull.snapshot.SnapshotPageRequest;
import com.nuono.next.noonpull.NoonInterfacePullPage;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonOrderLineFact;
import com.nuono.next.noonpull.NoonOrderReportRowClassifier;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.noonpull.NoonReportPullRequest;
import com.nuono.next.noonpull.NoonReportRowDecision;
import com.nuono.next.noonpull.NoonSalesPageQueryProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DP02 exact-window page adapter; it never creates or polls a mutable latest export. */
public final class Dp02OrderPageProvider
        implements SnapshotPageProvider<NoonOrderLineFact>, ReportProviderCapabilitySource {
    public static final String CHANNEL = "NOON_SALES_DASHBOARD_ORDER_LIST";
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?:^|:)date-range:(\\d{4}-\\d{2}-\\d{2})\\.\\.(\\d{4}-\\d{2}-\\d{2})$"
    );

    private final NoonSalesPageQueryProvider provider;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonOrderReportRowClassifier classifier;
    private final ObjectMapper canonicalMapper;

    public Dp02OrderPageProvider(
            NoonSalesPageQueryProvider provider,
            NoonPullStoreBindingResolver bindingResolver,
            NoonOrderReportRowClassifier classifier,
            ObjectMapper objectMapper
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.canonicalMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    public ProviderOutcome<SnapshotPage<NoonOrderLineFact>> fetchPage(
            SnapshotPageRequest request
    ) {
        try {
            SnapshotPageRequest scope = requireRequest(request);
            DateWindow window = window(scope.getBusinessWindowKey());
            NoonInterfacePullRequest pullRequest = NoonInterfacePullRequest.builder()
                    .ownerUserId(scope.getOwnerUserId())
                    .storeCode(scope.getStoreCode())
                    .siteCode(scope.getSiteCode())
                    .dataDomain(NoonPullDataDomain.ORDER)
                    .requestName("dp02-exact-order-page")
                    .targetIdentity(scope.getBusinessWindowKey())
                    .dateFrom(window.from)
                    .dateTo(window.to)
                    .resumePage(scope.getPageNo())
                    .build();
            requireSameScope(scope, bindingResolver.resolve(pullRequest));
            NoonInterfacePullPage page = Objects.requireNonNull(
                    provider.fetchPage(pullRequest, scope.getPageNo()),
                    "DP02 provider page"
            );
            NoonSnapshotPageContract pageContract = NoonSnapshotPageContract.requireExact(
                    scope, page, "DP02"
            );
            return classify(scope, window, pageContract);
        } catch (RuntimeException failure) {
            return NoonSnapshotProviderFailureClassifier.classify(
                    failure, "DP02_ORDER_PAGE"
            );
        }
    }

    private ProviderOutcome<SnapshotPage<NoonOrderLineFact>> classify(
            SnapshotPageRequest scope,
            DateWindow window,
            NoonSnapshotPageContract pageContract
    ) {
        List<Map<String, Object>> rows = pageContract.getRawItems();
        String sourceBatchId = "dp02-page-" + scope.getTaskId();
        NoonReportPullRequest reportRequest = NoonReportPullRequest.builder()
                .ownerUserId(scope.getOwnerUserId())
                .storeCode(scope.getStoreCode())
                .siteCode(scope.getSiteCode())
                .dataDomain(NoonPullDataDomain.ORDER)
                .reportType("sales_dashboard_sales_list")
                .dateFrom(window.from)
                .dateTo(window.to)
                .build();
        List<NoonReportRowDecision<NoonOrderLineFact>> decisions =
                classifier.classifyPageRows(reportRequest, sourceBatchId, rows);
        if (decisions.size() != rows.size()) {
            return ProviderOutcome.contractError("DP02_PAGE_CLASSIFICATION_EXTENT_DRIFT");
        }
        List<NoonOrderLineFact> facts = new ArrayList<>(rows.size());
        List<String> skipFingerprints = new ArrayList<>();
        for (int index = 0; index < decisions.size(); index++) {
            NoonReportRowDecision<NoonOrderLineFact> decision = decisions.get(index);
            if (decision.getKind() == NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR) {
                return ProviderOutcome.contractError("DP02_PAGE_ROW_OUTSIDE_CONTAINER");
            }
            if (decision.getKind() == NoonReportRowDecision.Kind.ACCEPT) {
                facts.add(decision.getAccepted());
            } else {
                skipFingerprints.add(rowFingerprint(rows.get(index)));
            }
        }
        return ProviderOutcome.success(pageContract.twoPass(facts, skipFingerprints));
    }

    @Override
    public ReportProviderCapabilities reportProviderCapabilities() {
        return new ReportProviderCapabilities(
                OperationCode.DP02,
                ReportProviderCapabilities.CreateReadbackEvidence
                        .DIRECT_EXACT_WINDOW_PAGE_QUERY,
                ReportProviderCapabilities.EmptyProofEvidence
                        .AUTHORITATIVE_TOTAL_FOR_EXACT_WINDOW,
                ReportProviderCapabilities.ArtifactCompletenessEvidence
                        .EXACT_PAGE_EXTENT_WITH_TWO_PASS_VALIDATION
        );
    }

    private SnapshotPageRequest requireRequest(SnapshotPageRequest request) {
        SnapshotPageRequest value = Objects.requireNonNull(request, "request");
        if (value.getOperationCode() != OperationCode.DP02
                || !CHANNEL.equals(value.getProviderChannel())
                || value.getLogicalStoreId() == null
                || !hasText(value.getProjectCode())
                || !hasText(value.getStoreCode())
                || !hasText(value.getSiteCode())
                || !same(value.getProjectCode(), value.getAccountKey())) {
            throw new IllegalArgumentException("DP02 page scope mismatch");
        }
        return value;
    }

    private void requireSameScope(SnapshotPageRequest request, NoonPullStoreBinding binding) {
        if (binding == null
                || !Objects.equals(binding.getOwnerUserId(), request.getOwnerUserId())
                || !same(binding.getProjectCode(), request.getProjectCode())
                || !same(binding.getStoreCode(), request.getStoreCode())
                || !sameIgnoreCase(binding.getSiteCode(), request.getSiteCode())) {
            throw new IllegalArgumentException("DP02 resolved binding scope mismatch");
        }
    }

    private DateWindow window(String businessWindowKey) {
        Matcher matcher = DATE_RANGE.matcher(
                Objects.requireNonNull(businessWindowKey, "businessWindowKey")
        );
        if (!matcher.find()) {
            throw new IllegalArgumentException("DP02 business window is invalid");
        }
        LocalDate from = LocalDate.parse(matcher.group(1));
        LocalDate to = LocalDate.parse(matcher.group(2));
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("DP02 business window is reversed");
        }
        return new DateWindow(from, to);
    }

    private String rowFingerprint(Map<String, Object> row) {
        try {
            return sha256(canonicalMapper.writeValueAsString(
                    Objects.requireNonNull(row, "row")
            ));
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP02 row fingerprint is invalid", failure);
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

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 must be available", unavailable);
        }
    }

    private static final class DateWindow {
        private final LocalDate from;
        private final LocalDate to;

        private DateWindow(LocalDate from, LocalDate to) {
            this.from = from;
            this.to = to;
        }
    }
}
