package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonOperationException;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonlog.NoonHttpCallLogView;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonPullFailureType;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CorrectAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnLineCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.UpsertAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseAsnListSyncSupport.NoonAsnListRow;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnInboundReceiptRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnListSyncThrottleRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnNoonListSyncRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnShippingBatchLinkInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnShippingBatchLinkRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ProductCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AppointmentTask;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AvailableSlot;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.RunResult;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.SlotCapacity;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnLineView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnInboundDetailView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnInboundLineView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnInboundSummaryView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnShippingBatchLinkView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnValidationView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AppointmentAvailabilityView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AppointmentView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ProductCandidateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.RoutingWarehouseView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ShippingBatchCandidateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.MissingBatchItemView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.MissingBatchView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import com.nuono.next.sales.NoonSalesReportRequest;
import com.nuono.next.web.ApiProblemException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbOfficialWarehouseService implements OfficialWarehouseAsnNumberSyncer {

    private static final BigDecimal CUBIC_FEET_DIVISOR = new BigDecimal("28316.846592");
    private static final int DEFAULT_APPOINTMENT_RETRY_SECONDS = 5;
    private static final int APPOINTMENT_RETRY_CAP_SECONDS = 1800;
    private static final int DEFAULT_SEAL_CHECK_ATTEMPTS = 8;
    private static final long DEFAULT_SEAL_CHECK_INTERVAL_MS = 1500L;
    private static final int DEFAULT_ASN_LIST_SYNC_PER_PAGE = 50;
    private static final int DEFAULT_ASN_LIST_SYNC_MAX_PAGES = 50;
    private static final int ASN_LIST_SYNC_COOLDOWN_MINUTES = 60;
    private static final DateTimeFormatter SYNC_RETRY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String APPOINTMENT_RISK_BACKOFF_STAGE = "NOON_RISK_BACKOFF";
    private static final String APPOINTMENT_RISK_BACKOFF_SOURCE = "OFFICIAL_WAREHOUSE_APPOINTMENT";
    private final OfficialWarehouseMapper mapper;
    private final NoonSessionGateway noonSessionGateway;
    private final NoonSalesReportBindingResolver bindingResolver;
    private final NoonHttpCallLogService noonHttpCallLogService;
    private final OfficialWarehouseNoonInboundClient noonInboundClient;
    private final ObjectMapper objectMapper;
    private final OfficialWarehouseAppointmentRunner appointmentRunner;
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final NoonPullFailurePolicy failurePolicy;
    @Value("${nuono.official-warehouse.appointment.scheduler.enabled:false}")
    private boolean appointmentSchedulerEnabled;
    @Value("${nuono.official-warehouse.appointment.scheduler.max-items-per-tick:20}")
    private int appointmentSchedulerMaxItems;
    @Value("${nuono.official-warehouse.appointment.scheduler.retry-base-seconds:5}")
    private int appointmentRetryBaseSeconds;

    @Value("${nuono.official-warehouse.appointment.scheduler.stale-no-capacity-minutes:15}")
    private int appointmentStaleNoCapacityMinutes;

    @Value("${nuono.official-warehouse.appointment.scheduler.system-operator-user-id:0}")
    private long appointmentSystemOperatorUserId;

    public LocalDbOfficialWarehouseService(
            OfficialWarehouseMapper mapper,
            NoonSessionGateway noonSessionGateway,
            NoonSalesReportBindingResolver bindingResolver,
            NoonHttpCallLogService noonHttpCallLogService,
            OfficialWarehouseNoonInboundClient noonInboundClient,
            ObjectMapper objectMapper,
            NoonRiskBackoffGuard riskBackoffGuard,
            NoonPullFailurePolicy failurePolicy
    ) {
        this.mapper = mapper;
        this.noonSessionGateway = noonSessionGateway;
        this.bindingResolver = bindingResolver;
        this.noonHttpCallLogService = noonHttpCallLogService;
        this.noonInboundClient = noonInboundClient;
        this.objectMapper = objectMapper;
        this.appointmentRunner = new OfficialWarehouseAppointmentRunner(Clock.systemDefaultZone());
        this.riskBackoffGuard = riskBackoffGuard == null ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.failurePolicy = failurePolicy == null ? new NoonPullFailurePolicy() : failurePolicy;
    }

    public List<AsnView> listAsns(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword
    ) {
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        Collection<String> storeCodes = trimToNull(storeCode) == null ? access.getStoreCodes() : List.of(storeCode);
        List<AsnRecord> records = mapper.listAsns(
                        ownerUserId,
                        storeCodes,
                        trimToNull(storeCode),
                        normalizeSite(siteCode),
                        keywordLike(keyword),
                        200
                );
        Map<Long, List<AsnInboundReceiptRecord>> receiptsByAsn = inboundReceiptsByAsn(
                ownerUserId,
                records.stream().map(record -> record.id).collect(Collectors.toList())
        );
        return records.stream()
                .map(record -> {
                    AsnView view = toAsnView(record, true);
                    view.inboundSummary = inboundSummary(record, receiptsByAsn.getOrDefault(record.id, List.of()));
                    return view;
                })
                .collect(Collectors.toList());
    }

    public AsnListSyncView syncNoonAsnList(
            BusinessAccessContext access,
            String storeCode,
            String siteCode
    ) {
        String normalizedStoreCode = requireText(storeCode, "请选择店铺。");
        String normalizedSiteCode = normalizeSite(requireText(siteCode, "请选择站点。"));
        Long ownerUserId = requireOwnerUserId(access, normalizedStoreCode);
        StoreSiteRecord site = requireStoreSite(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        NoonSalesReportBinding binding = resolveBinding(ownerUserId, site.logicalStoreId, site.storeCode, site.siteCode);
        NoonSession session = openNoonSession(ownerUserId, binding);
        claimOfficialWarehouseAsnListSync(ownerUserId, site, access.getSessionUserId());

        AsnListSyncView result = new AsnListSyncView();
        int page = 1;
        int totalPages = 1;
        while (page <= totalPages && page <= DEFAULT_ASN_LIST_SYNC_MAX_PAGES) {
            ObjectNode body = OfficialWarehouseAsnListSyncSupport.buildListRequest(
                    objectMapper,
                    binding.getPartnerId(),
                    page,
                    DEFAULT_ASN_LIST_SYNC_PER_PAGE,
                    totalPages
            );
            JsonNode response = noonInboundClient.syncAsnList(
                    session,
                    binding,
                    NoonCallContext.appointment(
                            "OFFICIAL_WAREHOUSE_ASN_SYNC",
                            binding.getStoreCode() + "/" + binding.getSiteCode(),
                            "ASN_LIST"
                    ),
                    body
            );
            JsonNode data = response.path("data");
            JsonNode rows = data.path("rows");
            if (rows.isArray()) {
                result.fetched += rows.size();
                for (JsonNode rowNode : rows) {
                    NoonAsnListRow remoteRow = OfficialWarehouseAsnListSyncSupport.parseRow(rowNode);
                    syncNoonAsnListRow(result, ownerUserId, site, binding, session, remoteRow, false, access.getSessionUserId());
                }
            }
            JsonNode pagination = data.path("pagination");
            Integer parsedTotalPages = intValue(pagination, "totalPages");
            if (parsedTotalPages != null && parsedTotalPages > 0) {
                totalPages = parsedTotalPages;
            }
            result.pages = page;
            Boolean hasNextPage = booleanValue(pagination, "hasNextPage");
            if (Boolean.FALSE.equals(hasNextPage) && page >= totalPages) {
                break;
            }
            page++;
        }
        return result;
    }

    void claimOfficialWarehouseAsnListSync(Long ownerUserId, StoreSiteRecord site, Long operatorUserId) {
        String claimToken = UUID.randomUUID().toString();
        mapper.claimOfficialWarehouseAsnListSync(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                claimToken,
                operatorUserId
        );
        AsnListSyncThrottleRecord throttle = mapper.selectOfficialWarehouseAsnListSyncThrottle(
                ownerUserId,
                site.storeCode,
                site.siteCode
        );
        if (throttle != null && claimToken.equals(throttle.claimToken)) {
            return;
        }

        LocalDateTime lastStartedAt = throttle == null || throttle.lastStartedAt == null
                ? LocalDateTime.now()
                : throttle.lastStartedAt;
        LocalDateTime nextAllowedAt = lastStartedAt.plusMinutes(ASN_LIST_SYNC_COOLDOWN_MINUTES);
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
                        "cooldownMinutes", ASN_LIST_SYNC_COOLDOWN_MINUTES,
                        "retryAfterSeconds", retryAfterSeconds,
                        "nextAllowedAt", nextAllowedAt.format(SYNC_RETRY_TIME_FORMATTER)
                ),
                null
        );
    }

    @Override
    public AsnListSyncView syncNoonAsnNumbers(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            Collection<String> asnNumbers,
            boolean dryRun
    ) {
        String normalizedStoreCode = requireText(storeCode, "请选择店铺。");
        String normalizedSiteCode = normalizeSite(requireText(siteCode, "请选择站点。"));
        Long ownerUserId = requireOwnerUserId(access, normalizedStoreCode);
        StoreSiteRecord site = requireStoreSite(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        NoonSalesReportBinding binding = resolveBinding(ownerUserId, site.logicalStoreId, site.storeCode, site.siteCode);
        NoonSession session = openNoonSession(ownerUserId, binding);

        AsnListSyncView result = new AsnListSyncView();
        for (String asnNumber : normalizeAsnNumbers(asnNumbers)) {
            ObjectNode body = OfficialWarehouseAsnListSyncSupport.buildSearchRequest(
                    objectMapper,
                    binding.getPartnerId(),
                    asnNumber
            );
            JsonNode response = noonInboundClient.syncAsnList(
                    session,
                    binding,
                    NoonCallContext.appointment(
                            "OFFICIAL_WAREHOUSE_ASN_SYNC",
                            binding.getStoreCode() + "/" + binding.getSiteCode(),
                            "ASN_SEARCH"
                    ),
                    body
            );
            result.pages += 1;
            boolean found = false;
            JsonNode rows = response.path("data").path("rows");
            if (rows.isArray()) {
                for (JsonNode rowNode : rows) {
                    NoonAsnListRow remoteRow = OfficialWarehouseAsnListSyncSupport.parseRow(rowNode);
                    if (!asnNumber.equalsIgnoreCase(trimToNull(remoteRow.asnNr))) {
                        continue;
                    }
                    found = true;
                    result.fetched += 1;
                    if (dryRun) {
                        continue;
                    }
                    syncNoonAsnListRow(result, ownerUserId, site, binding, session, remoteRow, true, access.getSessionUserId());
                }
            }
            if (!found) {
                result.skipped += 1;
            }
        }
        return result;
    }

    public AsnView getAsn(BusinessAccessContext access, String asnId) {
        Long parsedAsnId = parseLongId(asnId, "官方仓 ASN 不存在。");
        Long ownerUserId = requireOwnerUserId(access, null);
        AsnRecord record = mapper.selectAsn(ownerUserId, parsedAsnId);
        if (record == null) {
            throw new IllegalArgumentException("官方仓 ASN 不存在或无权访问。");
        }
        if (!access.canAccessStore(record.storeCode)) {
            throw new IllegalArgumentException("当前账号不能查看该店铺官方仓 ASN。");
        }
        AsnView view = toAsnView(record, true);
        view.inboundSummary = inboundSummary(record, inboundReceipts(ownerUserId, List.of(record.id)));
        view.noonUser = resolveNoonUser(record);
        return view;
    }

    public AsnInboundDetailView getAsnInboundDetail(BusinessAccessContext access, String asnId) {
        Long parsedAsnId = parseLongId(asnId, "官方仓 ASN 不存在。");
        Long ownerUserId = requireOwnerUserId(access, null);
        AsnRecord asn = mapper.selectAsn(ownerUserId, parsedAsnId);
        if (asn == null) {
            throw new IllegalArgumentException("官方仓 ASN 不存在或无权访问。");
        }
        if (!access.canAccessStore(asn.storeCode)) {
            throw new IllegalArgumentException("当前账号不能查看该店铺官方仓 ASN。");
        }

        List<AsnLineRecord> localLines = mapper.listAsnLines(asn.id);
        List<AsnInboundReceiptRecord> receipts = inboundReceipts(ownerUserId, List.of(asn.id));
        AsnInboundDetailView detail = new AsnInboundDetailView();
        detail.asnId = String.valueOf(asn.id);
        detail.localAsnNo = asn.localAsnNo;
        detail.noonAsnNr = asn.noonAsnNr;
        detail.storeCode = asn.storeCode;
        detail.siteCode = asn.siteCode;
        detail.sourceType = asn.sourceType;
        detail.summary = inboundSummary(asn, receipts);

        List<Long> receiptVariantIds = receipts.stream()
                .map(receipt -> receipt.productVariantId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<String> receiptPartnerSkus = receipts.stream()
                .map(receipt -> trimToNull(receipt.partnerSku))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<ProductCandidateRecord> receiptProductCandidates = receiptVariantIds.isEmpty() && receiptPartnerSkus.isEmpty()
                ? List.of()
                : mapper.listProductCandidates(
                        ownerUserId,
                        asn.storeCode,
                        asn.siteCode,
                        null,
                        receiptVariantIds,
                        receiptPartnerSkus,
                        Math.max(1000, receiptVariantIds.size() + receiptPartnerSkus.size())
                );
        Map<Long, ProductCandidateRecord> receiptCandidatesByVariantId = new LinkedHashMap<>();
        Map<String, ProductCandidateRecord> uniqueReceiptCandidatesByPartnerSku = new LinkedHashMap<>();
        Set<String> ambiguousReceiptPartnerSkus = new LinkedHashSet<>();
        for (ProductCandidateRecord candidate : receiptProductCandidates) {
            if (candidate.productVariantId != null) {
                receiptCandidatesByVariantId.putIfAbsent(candidate.productVariantId, candidate);
            }
            registerInboundProductCandidate(
                    uniqueReceiptCandidatesByPartnerSku,
                    ambiguousReceiptPartnerSkus,
                    candidate
            );
        }

        Map<Long, AsnInboundLineView> linesById = new LinkedHashMap<>();
        Map<String, AsnInboundLineView> uniqueLinesByKey = new LinkedHashMap<>();
        Set<String> ambiguousKeys = new LinkedHashSet<>();
        for (AsnLineRecord line : localLines) {
            AsnInboundLineView view = inboundLine(line);
            detail.lines.add(view);
            linesById.put(line.id, view);
            registerInboundLineKeys(uniqueLinesByKey, ambiguousKeys, view, line.childSku);
        }
        Map<String, AsnInboundLineView> reportOnlyLines = new LinkedHashMap<>();
        for (AsnInboundReceiptRecord receipt : receipts) {
            AsnInboundLineView target = receipt.asnLineId == null ? null : linesById.get(receipt.asnLineId);
            boolean matchedByBusinessKey = false;
            if (target == null) {
                target = findInboundLineByBusinessKey(uniqueLinesByKey, ambiguousKeys, receipt);
                matchedByBusinessKey = target != null;
            }
            if (target == null) {
                String reportKey = inboundReportOnlyKey(receipt);
                ProductCandidateRecord productCandidate = receipt.productVariantId == null
                        ? null
                        : receiptCandidatesByVariantId.get(receipt.productVariantId);
                if (productCandidate == null) {
                    String partnerKey = inboundPartnerKey(receipt.partnerSku);
                    if (partnerKey != null && !ambiguousReceiptPartnerSkus.contains(partnerKey)) {
                        productCandidate = uniqueReceiptCandidatesByPartnerSku.get(partnerKey);
                    }
                }
                ProductCandidateRecord resolvedProductCandidate = productCandidate;
                target = reportOnlyLines.computeIfAbsent(
                        reportKey,
                        ignored -> inboundReportOnlyLine(receipt, resolvedProductCandidate)
                );
            }
            accumulateInboundReceipt(target, receipt, matchedByBusinessKey);
        }
        detail.lines.addAll(reportOnlyLines.values());
        for (AsnInboundLineView line : detail.lines) {
            finalizeInboundLine(line);
        }
        detail.summary.unmatchedLineCount = (int) detail.lines.stream().filter(line -> line.reportOnly).count();
        detail.summary.exceptionLineCount = (int) detail.lines.stream()
                .filter(line -> !"NO_RECEIPT".equals(line.inboundStatus) && !"NORMAL".equals(line.inboundStatus))
                .count();
        return detail;
    }

    public List<ProductCandidateView> listProductCandidates(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword
    ) {
        return listProductCandidates(access, storeCode, siteCode, keyword, List.of(), List.of());
    }

    public List<ProductCandidateView> listProductCandidates(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword,
            Collection<String> shippingBatchIds,
            Collection<String> requestedPartnerSkus
    ) {
        String normalizedStoreCode = requireText(storeCode, "请选择店铺。");
        String normalizedSiteCode = normalizeSite(requireText(siteCode, "请选择站点。"));
        Long ownerUserId = requireOwnerUserId(access, normalizedStoreCode);
        StoreSiteRecord site = requireStoreSite(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        List<Long> selectedBatchIds = normalizeShippingBatchIds(shippingBatchIds);
        List<String> normalizedPartnerSkus = normalizePartnerSkus(requestedPartnerSkus);
        if (!selectedBatchIds.isEmpty()) {
            return listProductCandidatesFromShippingBatches(
                    ownerUserId,
                    site,
                    keyword,
                    selectedBatchIds,
                    normalizedPartnerSkus
            );
        }
        return mapper.listProductCandidates(
                        ownerUserId,
                        site.storeCode,
                        site.siteCode,
                        keywordLike(keyword),
                        List.of(),
                        normalizedPartnerSkus,
                        normalizedPartnerSkus.isEmpty() ? 200 : Math.max(normalizedPartnerSkus.size(), 1)
                )
                .stream()
                .map(this::toProductCandidateView)
                .collect(Collectors.toList());
    }

    private List<ProductCandidateView> listProductCandidatesFromShippingBatches(
            Long ownerUserId,
            StoreSiteRecord site,
            String keyword,
            List<Long> selectedBatchIds,
            List<String> requestedPartnerSkus
    ) {
        List<ShippingBatchSourceAllocationRecord> allocations = mapper.listShippingBatchSourceAllocations(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                selectedBatchIds,
                List.of(),
                List.of()
        );
        if (allocations.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> quantityByProductKey = new LinkedHashMap<>();
        Set<Long> legacyVariantIds = new LinkedHashSet<>();
        Set<String> partnerSkus = new LinkedHashSet<>();
        Set<String> requestedPartnerSkuSet = requestedPartnerSkus.stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (ShippingBatchSourceAllocationRecord allocation : allocations) {
            if (!StringUtils.hasText(allocation.partnerSku) && allocation.productVariantId == null) {
                continue;
            }
            int quantity = Math.max(0, allocation.quantity == null ? 0 : allocation.quantity);
            if (quantity <= 0) {
                continue;
            }
            if (!requestedPartnerSkuSet.isEmpty()
                    && (!StringUtils.hasText(allocation.partnerSku)
                    || !requestedPartnerSkuSet.contains(allocation.partnerSku.trim().toUpperCase(Locale.ROOT)))) {
                continue;
            }
            quantityByProductKey.merge(siteProductKey(site.storeCode, site.siteCode, allocation.partnerSku, allocation.productVariantId), quantity, Integer::sum);
            if (StringUtils.hasText(allocation.partnerSku)) {
                partnerSkus.add(allocation.partnerSku.trim());
            } else {
                legacyVariantIds.add(allocation.productVariantId);
            }
        }
        if (quantityByProductKey.isEmpty()) {
            return List.of();
        }
        return mapper.listProductCandidates(
                        ownerUserId,
                        site.storeCode,
                        site.siteCode,
                        keywordLike(keyword),
                        legacyVariantIds,
                        partnerSkus,
                        Math.max(quantityByProductKey.size(), 1)
                )
                .stream()
                .map(row -> {
                    ProductCandidateView view = toProductCandidateView(row);
                    view.batchAvailableQuantity = quantityByProductKey.getOrDefault(siteProductKey(site.storeCode, site.siteCode, row.partnerSku, row.productVariantId), 0);
                    return view;
                })
                .collect(Collectors.toList());
    }

    public List<ShippingBatchCandidateView> listShippingBatchCandidates(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword
    ) {
        String normalizedStoreCode = requireText(storeCode, "请选择店铺。");
        String normalizedSiteCode = normalizeSite(requireText(siteCode, "请选择站点。"));
        Long ownerUserId = requireOwnerUserId(access, normalizedStoreCode);
        StoreSiteRecord site = requireStoreSite(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        return mapper.listShippingBatchCandidates(
                        ownerUserId,
                        site.storeCode,
                        site.siteCode,
                        keywordLike(keyword),
                        100
                )
                .stream()
                .map(this::toShippingBatchCandidateView)
                .collect(Collectors.toList());
    }

    public AsnView createAsn(BusinessAccessContext access, CreateAsnCommand command) {
        AsnValidationView validation = validateAsn(access, command);
        if (!validation.missingBatches.isEmpty()
                && (command.partialBatchConfirmed == null || !command.partialBatchConfirmed)) {
            throw partialBatchConfirmationRequired(validation);
        }
        if (command == null) {
            throw new IllegalArgumentException("缺少官方仓 ASN 创建参数。");
        }
        String storeCode = requireText(command.storeCode, "请选择店铺。");
        String siteCode = normalizeSite(requireText(command.siteCode, "请选择站点。"));
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        StoreSiteRecord site = requireStoreSite(ownerUserId, storeCode, siteCode);
        List<CreateAsnLineCommand> lineCommands = command.lines == null ? List.of() : command.lines;
        if (lineCommands.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个商品。");
        }

        Map<String, Integer> quantityByProductKey = new LinkedHashMap<>();
        Set<Long> legacyVariantIds = new LinkedHashSet<>();
        Set<String> partnerSkus = new LinkedHashSet<>();
        for (CreateAsnLineCommand lineCommand : lineCommands) {
            if (lineCommand == null || (!StringUtils.hasText(lineCommand.partnerSku) && lineCommand.productVariantId == null)) {
                throw new IllegalArgumentException("商品行缺少 PSKU。");
            }
            int quantity = lineCommand.quantity == null ? 0 : lineCommand.quantity;
            if (quantity <= 0) {
                throw new IllegalArgumentException("商品数量必须大于 0。");
            }
            quantityByProductKey.merge(siteProductKey(site.storeCode, site.siteCode, lineCommand.partnerSku, lineCommand.productVariantId), quantity, Integer::sum);
            if (StringUtils.hasText(lineCommand.partnerSku)) {
                partnerSkus.add(lineCommand.partnerSku.trim());
            } else {
                legacyVariantIds.add(lineCommand.productVariantId);
            }
        }

        List<ProductCandidateRecord> candidateRows = mapper.listProductCandidates(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                null,
                legacyVariantIds,
                partnerSkus,
                Math.max(quantityByProductKey.size(), 1)
        );
        Map<String, ProductCandidateRecord> candidatesByProductKey = candidateRows.stream()
                .collect(Collectors.toMap(
                        row -> siteProductKey(site.storeCode, site.siteCode, row.partnerSku, row.productVariantId),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<String> missingProductKeys = new LinkedHashSet<>(quantityByProductKey.keySet());
        missingProductKeys.removeAll(candidatesByProductKey.keySet());
        if (!missingProductKeys.isEmpty()) {
            throw new IllegalArgumentException("部分商品缺少站点 PSKU 或不属于当前店铺：" + missingProductKeys);
        }

        List<AsnLineInsertRecord> lineRows = new ArrayList<>();
        int totalQuantity = 0;
        for (Map.Entry<String, Integer> entry : quantityByProductKey.entrySet()) {
            ProductCandidateRecord candidate = candidatesByProductKey.get(entry.getKey());
            ProductCandidateView candidateView = toProductCandidateView(candidate);
            if (!candidateView.missingTags.isEmpty()) {
                throw new IllegalArgumentException(candidateView.partnerSku + " 缺少官方仓建 ASN 所需数据：" + candidateView.missingTags);
            }
            AsnLineInsertRecord lineRow = new AsnLineInsertRecord();
            lineRow.id = mapper.nextAsnLineId();
            lineRow.ownerUserId = ownerUserId;
            lineRow.storeCode = site.storeCode;
            lineRow.siteCode = site.siteCode;
            lineRow.productMasterId = candidate.productMasterId;
            lineRow.productVariantId = candidate.productVariantId;
            lineRow.productSiteOfferId = candidate.productSiteOfferId;
            lineRow.skuParent = candidate.skuParent;
            lineRow.partnerSku = candidate.partnerSku;
            lineRow.childSku = candidate.childSku;
            lineRow.pskuCode = candidate.pskuCode;
            lineRow.noonSku = candidate.noonSku;
            lineRow.titleCache = candidate.titleCache;
            lineRow.imageUrlCache = ProductImageUrlSupport.normalize(candidate.imageUrlCache);
            lineRow.quantity = entry.getValue();
            lineRow.productLengthCm = candidate.productLengthCm;
            lineRow.productWidthCm = candidate.productWidthCm;
            lineRow.productHeightCm = candidate.productHeightCm;
            lineRow.productWeightG = candidate.productWeightG;
            lineRow.cubicFeet = calculateCubicFeet(candidate.productLengthCm, candidate.productWidthCm, candidate.productHeightCm);
            lineRow.storageTypeCode = firstNonBlank(candidate.storageTypeCode, "standard");
            lineRow.lineStatus = "PENDING";
            lineRow.operatorUserId = access.getSessionUserId();
            lineRows.add(lineRow);
            totalQuantity += entry.getValue();
        }

        Long asnId = mapper.nextAsnId();
        String localAsnNo = "OWA-" + asnId;
        for (AsnLineInsertRecord lineRow : lineRows) {
            lineRow.asnId = asnId;
        }
        List<Long> selectedShippingBatchIds = normalizeShippingBatchIds(command.shippingBatchIds);
        List<AsnShippingBatchLinkInsertRecord> shippingBatchLinks = buildAsnShippingBatchLinks(
                ownerUserId,
                site,
                asnId,
                lineRows,
                selectedShippingBatchIds,
                access.getSessionUserId()
        );
        AsnInsertRecord asnRow = new AsnInsertRecord();
        asnRow.id = asnId;
        asnRow.ownerUserId = ownerUserId;
        asnRow.logicalStoreId = site.logicalStoreId;
        asnRow.storeCode = site.storeCode;
        asnRow.storeName = site.storeName;
        asnRow.siteCode = site.siteCode;
        asnRow.projectCode = site.projectCode;
        asnRow.localAsnNo = localAsnNo;
        asnRow.sourceType = firstNonBlank(command.sourceType, "MANUAL");
        asnRow.status = "DRAFT";
        asnRow.productCount = lineRows.size();
        asnRow.totalQuantity = totalQuantity;
        asnRow.operatorUserId = access.getSessionUserId();
        mapper.insertAsn(asnRow);
        for (AsnLineInsertRecord lineRow : lineRows) {
            mapper.insertAsnLine(lineRow);
        }
        for (AsnShippingBatchLinkInsertRecord linkRow : shippingBatchLinks) {
            mapper.insertAsnShippingBatchLink(linkRow);
        }

        boolean remoteAsnCreated = false;
        String noonAsnNo = null;
        try {
            NoonSalesReportBinding binding = bindingResolver.resolve(new NoonSalesReportRequest(
                    ownerUserId,
                    site.logicalStoreId,
                    site.storeCode,
                    site.siteCode,
                    LocalDate.now(),
                    LocalDate.now()
            ));
            mapper.updateAsnBinding(asnId, binding.getProjectCode(), binding.getPartnerId(), access.getSessionUserId());
            NoonSession session = openNoonSession(ownerUserId, binding);

            NoonCallContext asnCallContext = NoonCallContext.asn(asnId, localAsnNo);
            JsonNode createResponse = noonInboundClient.createAsn(session, binding, asnCallContext, totalQuantity);
            JsonNode createData = createResponse.path("data");
            String asnNr = requireText(text(createData, "asn_nr"), "Noon 创建 ASN 响应缺少 asn_nr。");
            noonAsnNo = asnNr;
            remoteAsnCreated = true;
            Long partnerAsnId = longValue(createData, "id_partner_asn");
            Integer noonTotalQty = intValue(createData, "total_qty");
            mapper.markAsnCreated(
                    asnId,
                    asnNr,
                    partnerAsnId,
                    noonTotalQty == null ? totalQuantity : noonTotalQty,
                    firstNonBlank(text(createData, "asn_status"), text(createData, "id_status")),
                    access.getSessionUserId()
            );

            JsonNode routingResponse = noonInboundClient.routeWarehouse(session, binding, asnCallContext, asnNr, lineRows);
            JsonNode firstWarehouse = firstWarehouse(routingResponse);
            String selectedWarehousePartnerCode = text(firstWarehouse, "partner_code");
            String selectedWarehouseCode = text(firstWarehouse, "code");
            mapper.markRouted(
                    asnId,
                    writeJson(routingResponse),
                    booleanValue(routingResponse, "is_transfer"),
                    selectedWarehousePartnerCode,
                    selectedWarehouseCode,
                    firstNonBlank(selectedWarehousePartnerCode, selectedWarehouseCode),
                    access.getSessionUserId()
            );

            JsonNode linesResponse = noonInboundClient.createLines(session, binding, asnCallContext, asnNr, lineRows);
            mapper.markAllLinesCreated(asnId, access.getSessionUserId());
            applyNoonLineResponse(asnId, linesResponse, access.getSessionUserId());
            sealRemoteAsnAfterLineCreation(
                    session,
                    binding,
                    asnId,
                    localAsnNo,
                    asnNr,
                    asnCallContext,
                    selectedWarehousePartnerCode,
                    access.getSessionUserId()
            );
            mapper.markLinesCreated(asnId, access.getSessionUserId());
            mapper.markProductSiteOfferLogisticsHistoryByAsn(asnId, access.getSessionUserId());
            return getAsn(access, String.valueOf(asnId));
        } catch (IllegalArgumentException exception) {
            failAsnCreation(asnId, remoteAsnCreated, "VALIDATION", "VALIDATION", exception.getMessage(), access.getSessionUserId());
            throw exception;
        } catch (NoonOperationException exception) {
            String message = shrinkMessage(exception);
            failAsnCreation(
                    asnId,
                    remoteAsnCreated,
                    exception.getClassification().getOperation(),
                    exception.getClassification().getCode(),
                    message,
                    access.getSessionUserId()
            );
            throw OfficialWarehouseNoonProblemTranslator.createAsnProblem(
                    exception,
                    asnId,
                    localAsnNo,
                    noonAsnNo,
                    lineRows
            );
        } catch (Exception exception) {
            String message = shrinkMessage(exception);
            failAsnCreation(asnId, remoteAsnCreated, resolveFailureStage(), exception.getClass().getSimpleName(), message, access.getSessionUserId());
            throw new IllegalStateException("Noon 官方仓 ASN 创建失败：" + message, exception);
        }
    }

    public AsnValidationView validateAsn(BusinessAccessContext access, CreateAsnCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少官方仓 ASN 创建参数。");
        }
        String storeCode = requireText(command.storeCode, "请选择店铺。");
        String siteCode = normalizeSite(requireText(command.siteCode, "请选择站点。"));
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        StoreSiteRecord site = requireStoreSite(ownerUserId, storeCode, siteCode);
        List<CreateAsnLineCommand> lineCommands = command.lines == null ? List.of() : command.lines;
        if (lineCommands.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个商品。");
        }

        Map<String, Integer> selectedQuantities = new LinkedHashMap<>();
        Set<Long> variantIds = new LinkedHashSet<>();
        Set<String> partnerSkus = new LinkedHashSet<>();
        for (CreateAsnLineCommand line : lineCommands) {
            if (line == null || (!StringUtils.hasText(line.partnerSku) && line.productVariantId == null)) {
                throw new IllegalArgumentException("商品行缺少 PSKU。");
            }
            int quantity = line.quantity == null ? 0 : line.quantity;
            if (quantity <= 0) {
                throw new IllegalArgumentException("商品数量必须大于 0。");
            }
            String productKey = siteProductKey(site.storeCode, site.siteCode, line.partnerSku, line.productVariantId);
            selectedQuantities.merge(productKey, quantity, Integer::sum);
            if (StringUtils.hasText(line.partnerSku)) {
                partnerSkus.add(line.partnerSku.trim());
            } else {
                variantIds.add(line.productVariantId);
            }
        }

        List<ProductCandidateRecord> selectedCandidates = mapper.listProductCandidates(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                null,
                variantIds,
                partnerSkus,
                Math.max(selectedQuantities.size(), 1)
        );
        Set<String> selectedCandidateKeys = selectedCandidates.stream()
                .map(row -> siteProductKey(site.storeCode, site.siteCode, row.partnerSku, row.productVariantId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> missingSelectedKeys = new LinkedHashSet<>(selectedQuantities.keySet());
        missingSelectedKeys.removeAll(selectedCandidateKeys);
        if (!missingSelectedKeys.isEmpty()) {
            throw new IllegalArgumentException("部分商品缺少站点 PSKU 或不属于当前店铺：" + missingSelectedKeys);
        }

        List<Long> selectedBatchIds = normalizeShippingBatchIds(command.shippingBatchIds);
        AsnValidationView view = new AsnValidationView();
        view.valid = true;
        if (selectedBatchIds.isEmpty()) {
            view.completeBatchSelection = true;
            return view;
        }
        List<ShippingBatchSourceAllocationRecord> allocations = mapper.listShippingBatchSourceAllocations(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                selectedBatchIds,
                List.of(),
                List.of()
        );
        sortShippingBatchAllocations(allocations, selectedBatchIds);
        Set<Long> allVariantIds = allocations.stream()
                .filter(row -> !StringUtils.hasText(row.partnerSku))
                .map(row -> row.productVariantId)
                .filter(value -> value != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allPartnerSkus = allocations.stream()
                .map(row -> trimToNull(row.partnerSku))
                .filter(value -> value != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ProductCandidateRecord> candidateByProductKey = mapper.listProductCandidates(
                        ownerUserId,
                        site.storeCode,
                        site.siteCode,
                        null,
                        allVariantIds,
                        allPartnerSkus,
                        Math.max(allocations.size(), 1)
                ).stream()
                .collect(Collectors.toMap(
                        row -> siteProductKey(site.storeCode, site.siteCode, row.partnerSku, row.productVariantId),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<OfficialWarehouseBatchSelectionValidator.Allocation> scopedAllocations = allocations.stream()
                .filter(row -> row.quantity != null && row.quantity > 0)
                .map(row -> {
                    String productKey = siteProductKey(site.storeCode, site.siteCode, row.partnerSku, row.productVariantId);
                    ProductCandidateRecord candidate = candidateByProductKey.get(productKey);
                    return new OfficialWarehouseBatchSelectionValidator.Allocation(
                            String.valueOf(allocationBatchId(row)),
                            firstNonBlank(row.shippingBatchNo, row.batchReferenceNo, row.trackingNo, row.externalShipmentNo),
                            productKey,
                            firstNonBlank(candidate == null ? null : candidate.titleCache, row.titleCache, row.partnerSku),
                            firstNonBlank(candidate == null ? null : candidate.partnerSku, row.partnerSku),
                            candidate == null ? null : candidate.noonSku,
                            row.quantity
                    );
                })
                .collect(Collectors.toList());
        OfficialWarehouseBatchSelectionValidator.Result result =
                OfficialWarehouseBatchSelectionValidator.validate(scopedAllocations, selectedQuantities);
        view.missingBatches = result.getMissingBatches().stream()
                .map(this::toMissingBatchView)
                .collect(Collectors.toList());
        view.completeBatchSelection = view.missingBatches.isEmpty();
        return view;
    }

    private void failAsnCreation(
            Long asnId,
            boolean remoteAsnCreated,
            String errorStage,
            String failureType,
            String errorMessage,
            Long operatorUserId
    ) {
        mapper.markAsnFailed(asnId, errorStage, failureType, errorMessage, operatorUserId);
        mapper.markPendingLinesFailed(asnId, errorMessage, operatorUserId);
        if (!remoteAsnCreated) {
            mapper.softDeleteAsnShippingBatchLinks(asnId, operatorUserId);
            mapper.softDeleteAsnLines(asnId, operatorUserId);
            mapper.softDeletePreSubmitAsn(asnId, operatorUserId);
        }
    }

    private List<AsnShippingBatchLinkInsertRecord> buildAsnShippingBatchLinks(
            Long ownerUserId,
            StoreSiteRecord site,
            Long asnId,
            List<AsnLineInsertRecord> lineRows,
            List<Long> selectedBatchIds,
            Long operatorUserId
    ) {
        if (selectedBatchIds == null || selectedBatchIds.isEmpty()) {
            return List.of();
        }
        if (lineRows == null || lineRows.isEmpty()) {
            return List.of();
        }
        int pendingProductMatches = mapper.countPendingProductMatchesForBatches(ownerUserId, selectedBatchIds);
        if (pendingProductMatches > 0) {
            throw new IllegalArgumentException("选择的物流批次仍有 " + pendingProductMatches + " 条商品待匹配，请先在在途物流中重新匹配。");
        }
        List<String> partnerSkus = lineRows.stream()
                .map(row -> trimToNull(row.partnerSku))
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.toList());
        List<Long> variantIds = lineRows.stream()
                .filter(row -> !StringUtils.hasText(row.partnerSku))
                .map(row -> row.productVariantId)
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.toList());
        List<ShippingBatchSourceAllocationRecord> allocations = mapper.listShippingBatchSourceAllocations(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                selectedBatchIds,
                variantIds,
                partnerSkus
        );
        if (allocations.isEmpty()) {
            throw new IllegalArgumentException("选择的物流批次没有匹配当前 ASN 商品。");
        }

        Map<Long, Integer> batchOrder = new LinkedHashMap<>();
        for (int index = 0; index < selectedBatchIds.size(); index++) {
            batchOrder.put(selectedBatchIds.get(index), index);
        }
        allocations.sort((left, right) -> {
            int leftOrder = batchOrder.getOrDefault(allocationBatchId(left), Integer.MAX_VALUE);
            int rightOrder = batchOrder.getOrDefault(allocationBatchId(right), Integer.MAX_VALUE);
            if (leftOrder != rightOrder) {
                return Integer.compare(leftOrder, rightOrder);
            }
            long leftSourceId = allocationSourceId(left) == null ? Long.MAX_VALUE : allocationSourceId(left);
            long rightSourceId = allocationSourceId(right) == null ? Long.MAX_VALUE : allocationSourceId(right);
            return Long.compare(leftSourceId, rightSourceId);
        });

        Map<String, List<ShippingBatchSourceAllocationRecord>> allocationsByProductKey = new LinkedHashMap<>();
        Map<Long, Integer> remainingBySourceId = new LinkedHashMap<>();
        for (ShippingBatchSourceAllocationRecord allocation : allocations) {
            Long sourceId = allocationSourceId(allocation);
            if ((!StringUtils.hasText(allocation.partnerSku) && allocation.productVariantId == null) || sourceId == null) {
                continue;
            }
            int quantity = Math.max(0, allocation.quantity == null ? 0 : allocation.quantity);
            if (quantity <= 0) {
                continue;
            }
            allocationsByProductKey
                    .computeIfAbsent(siteProductKey(site.storeCode, site.siteCode, allocation.partnerSku, allocation.productVariantId), ignored -> new ArrayList<>())
                    .add(allocation);
            remainingBySourceId.put(sourceId, quantity);
        }

        List<AsnShippingBatchLinkInsertRecord> links = new ArrayList<>();
        for (AsnLineInsertRecord lineRow : lineRows) {
            int requiredQuantity = Math.max(0, lineRow.quantity == null ? 0 : lineRow.quantity);
            String productKey = siteProductKey(site.storeCode, site.siteCode, lineRow.partnerSku, lineRow.productVariantId);
            int availableQuantity = allocationsByProductKey
                    .getOrDefault(productKey, List.of())
                    .stream()
                    .mapToInt(allocation -> remainingBySourceId.getOrDefault(allocationSourceId(allocation), 0))
                    .sum();
            if (availableQuantity < requiredQuantity) {
                String label = firstNonBlank(
                        lineRow.partnerSku,
                        lineRow.pskuCode,
                        lineRow.skuParent,
                        String.valueOf(lineRow.productVariantId)
                );
                throw new IllegalArgumentException(
                        label + " 选择的物流批次数量不足：需要 " + requiredQuantity + "，批次可用 " + availableQuantity + "。"
                );
            }

            int remainingQuantity = requiredQuantity;
            for (ShippingBatchSourceAllocationRecord allocation : allocationsByProductKey.getOrDefault(productKey, List.of())) {
                if (remainingQuantity <= 0) {
                    break;
                }
                Long sourceId = allocationSourceId(allocation);
                if (sourceId == null) {
                    continue;
                }
                int sourceRemaining = remainingBySourceId.getOrDefault(sourceId, 0);
                if (sourceRemaining <= 0) {
                    continue;
                }
                int linkedQuantity = Math.min(remainingQuantity, sourceRemaining);
                AsnShippingBatchLinkInsertRecord link = new AsnShippingBatchLinkInsertRecord();
                link.id = mapper.nextAsnShippingBatchLinkId();
                link.asnId = asnId;
                link.asnLineId = lineRow.id;
                link.ownerUserId = ownerUserId;
                link.storeCode = site.storeCode;
                link.siteCode = site.siteCode;
                link.shippingBatchId = allocation.shippingBatchId;
                link.shippingBatchNo = allocation.shippingBatchNo;
                link.shippingBatchSourceId = allocation.shippingBatchSourceId;
                link.inTransitBatchId = allocation.inTransitBatchId;
                link.batchReferenceNo = allocation.batchReferenceNo;
                link.trackingNo = allocation.trackingNo;
                link.externalShipmentNo = allocation.externalShipmentNo;
                link.forwarderName = allocation.forwarderName;
                link.transportMode = allocation.transportMode;
                link.latestNodeStatus = allocation.latestNodeStatus;
                link.inTransitGoodsLineId = allocation.inTransitGoodsLineId;
                link.fulfillmentBalanceId = allocation.fulfillmentBalanceId;
                link.purchaseOrderId = allocation.purchaseOrderId;
                link.purchaseOrderNo = allocation.purchaseOrderNo;
                link.purchaseOrderItemId = allocation.purchaseOrderItemId;
                link.purchaseOrderItemSiteId = allocation.purchaseOrderItemSiteId;
                link.productMasterId = lineRow.productMasterId;
                link.productVariantId = lineRow.productVariantId;
                link.partnerSku = lineRow.partnerSku;
                link.pskuCode = lineRow.pskuCode;
                link.quantity = linkedQuantity;
                link.relationStatus = "LINKED";
                link.relationBasis = allocation.inTransitBatchId == null
                        ? "ASN_CREATE_SELECTED_BATCH"
                        : "ASN_CREATE_SELECTED_IN_TRANSIT_BATCH";
                link.operatorUserId = operatorUserId;
                links.add(link);
                remainingQuantity -= linkedQuantity;
                remainingBySourceId.put(sourceId, sourceRemaining - linkedQuantity);
            }
        }
        return links;
    }

    private Long allocationBatchId(ShippingBatchSourceAllocationRecord allocation) {
        if (allocation == null) {
            return null;
        }
        return allocation.inTransitBatchId == null ? allocation.shippingBatchId : allocation.inTransitBatchId;
    }

    private void sortShippingBatchAllocations(
            List<ShippingBatchSourceAllocationRecord> allocations,
            List<Long> selectedBatchIds
    ) {
        Map<Long, Integer> batchOrder = new LinkedHashMap<>();
        for (int index = 0; index < selectedBatchIds.size(); index++) {
            batchOrder.put(selectedBatchIds.get(index), index);
        }
        allocations.sort((left, right) -> {
            int leftOrder = batchOrder.getOrDefault(allocationBatchId(left), Integer.MAX_VALUE);
            int rightOrder = batchOrder.getOrDefault(allocationBatchId(right), Integer.MAX_VALUE);
            if (leftOrder != rightOrder) {
                return Integer.compare(leftOrder, rightOrder);
            }
            long leftSourceId = allocationSourceId(left) == null ? Long.MAX_VALUE : allocationSourceId(left);
            long rightSourceId = allocationSourceId(right) == null ? Long.MAX_VALUE : allocationSourceId(right);
            return Long.compare(leftSourceId, rightSourceId);
        });
    }

    private MissingBatchView toMissingBatchView(OfficialWarehouseBatchSelectionValidator.MissingBatch batch) {
        MissingBatchView view = new MissingBatchView();
        view.shippingBatchId = batch.getBatchId();
        view.batchNo = batch.getBatchNo();
        view.items = batch.getItems().stream().map(item -> {
            MissingBatchItemView itemView = new MissingBatchItemView();
            itemView.title = item.getTitle();
            itemView.partnerSku = item.getPartnerSku();
            itemView.noonSku = item.getNoonSku();
            itemView.missingQuantity = item.getMissingQuantity();
            return itemView;
        }).collect(Collectors.toList());
        return view;
    }

    private ApiProblemException partialBatchConfirmationRequired(AsnValidationView validation) {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFICIAL_WAREHOUSE_PARTIAL_BATCH_CONFIRM_REQUIRED",
                "CONFIRMATION_REQUIRED",
                "CREATE_ASN",
                "当前选择未覆盖物流批次中的全部待约商品，可能造成漏约。",
                false,
                false,
                null,
                Map.of("missingBatches", validation.missingBatches),
                null
        );
    }

    private Long allocationSourceId(ShippingBatchSourceAllocationRecord allocation) {
        if (allocation == null) {
            return null;
        }
        return allocation.inTransitGoodsLineId == null ? allocation.shippingBatchSourceId : allocation.inTransitGoodsLineId;
    }

    private String siteProductKey(String storeCode, String siteCode, String partnerSku, Long productVariantId) {
        String store = trimToNull(storeCode);
        String site = trimToNull(siteCode);
        String psku = trimToNull(partnerSku);
        if (store != null && site != null && psku != null) {
            return store.toUpperCase(Locale.ROOT) + "|" + site.toUpperCase(Locale.ROOT) + "|psku:" + psku;
        }
        return "variant:" + productVariantId;
    }

    public List<NoonHttpCallLogView> listNoonCalls(BusinessAccessContext access, String asnId) {
        AsnView asn = getAsn(access, asnId);
        return noonHttpCallLogService.listRecent("OFFICIAL_WAREHOUSE_ASN", asn.id, asn.localAsnNo, 50);
    }

    private void syncNoonAsnListRow(
            AsnListSyncView result,
            Long ownerUserId,
            StoreSiteRecord site,
            NoonSalesReportBinding binding,
            NoonSession session,
            NoonAsnListRow remoteRow,
            boolean allowExistingRoutingPrefill,
            Long operatorUserId
    ) {
        Long operatorId = operatorUserId == null ? ownerUserId : operatorUserId;
        if (remoteRow == null || !StringUtils.hasText(remoteRow.asnNr)) {
            result.skipped += 1;
            return;
        }
        if (StringUtils.hasText(remoteRow.countryCode)
                && StringUtils.hasText(site.siteCode)
                && !site.siteCode.equalsIgnoreCase(remoteRow.countryCode)) {
            result.skipped += 1;
            return;
        }

        AsnRecord existing = mapper.selectAsnByNoonAsnNr(ownerUserId, site.storeCode, site.siteCode, remoteRow.asnNr);
        Long asnId;
        String localAsnNo;
        boolean insertedFromNoon = existing == null;
        if (existing == null) {
            asnId = mapper.nextAsnId();
            localAsnNo = "OWA-" + asnId;
            AsnInsertRecord insert = new AsnInsertRecord();
            insert.id = asnId;
            insert.ownerUserId = ownerUserId;
            insert.logicalStoreId = site.logicalStoreId;
            insert.storeCode = site.storeCode;
            insert.storeName = site.storeName;
            insert.siteCode = site.siteCode;
            insert.projectCode = binding.getProjectCode();
            insert.partnerId = binding.getPartnerId();
            insert.localAsnNo = localAsnNo;
            insert.sourceType = "NOON_SYNC";
            insert.status = firstNonBlank(remoteRow.localAsnStatus, "LINES_CREATED");
            insert.productCount = 0;
            insert.totalQuantity = remoteRow.totalQty == null ? 0 : remoteRow.totalQty;
            insert.operatorUserId = operatorId;
            mapper.insertAsn(insert);
            result.created += 1;
        } else {
            asnId = existing.id;
            localAsnNo = existing.localAsnNo;
            result.updated += 1;
        }

        AsnNoonListSyncRecord syncRecord = buildAsnNoonListSyncRecord(
                ownerUserId,
                asnId,
                binding,
                remoteRow,
                operatorId
        );
        boolean missingRoutingSnapshot = existing == null
                || (!StringUtils.hasText(existing.routingResponseJson)
                && !StringUtils.hasText(existing.selectedWarehousePartnerCode)
                && !StringUtils.hasText(existing.selectedWarehouseCode));
        mapper.syncAsnFromNoonList(syncRecord);
        if ("FAILED".equals(syncRecord.status)) {
            result.failed += 1;
        }
        syncAppointmentFromNoonList(
                result,
                ownerUserId,
                site,
                binding,
                asnId,
                localAsnNo,
                remoteRow,
                operatorId
        );
        if (insertedFromNoon || (allowExistingRoutingPrefill && missingRoutingSnapshot)) {
            prefillSyncedAsnRoutingWarehouses(
                    session,
                    binding,
                    asnId,
                    localAsnNo,
                    syncRecord,
                    remoteRow,
                    operatorId
            );
        }
    }

    private void prefillSyncedAsnRoutingWarehouses(
            NoonSession session,
            NoonSalesReportBinding binding,
            Long asnId,
            String localAsnNo,
            AsnNoonListSyncRecord syncRecord,
            NoonAsnListRow remoteRow,
            Long operatorUserId
    ) {
        if (session == null || syncRecord == null || remoteRow == null || !StringUtils.hasText(syncRecord.noonAsnNr)) {
            return;
        }
        if (!"LINES_CREATED".equals(syncRecord.status)) {
            return;
        }
        if (!OfficialWarehouseStatusPolicy.isNoonAsnReadyForAppointmentStatus(remoteRow.remoteStatus)) {
            return;
        }
        if (StringUtils.hasText(remoteRow.warehouseToPartnerCode) || StringUtils.hasText(remoteRow.warehouseToCode)) {
            return;
        }
        NoonCallContext context = NoonCallContext.asn(asnId, localAsnNo);
        try {
            JsonNode detail = noonInboundClient.queryAsnDetailRow(session, binding, context, syncRecord.noonAsnNr);
            List<AsnLineInsertRecord> routingLines = OfficialWarehouseNoonInboundClient.routingLineRowsFromAsnDetail(detail);
            if (routingLines.isEmpty()) {
                return;
            }
            JsonNode routingResponse = noonInboundClient.routeWarehouse(session, binding, context, syncRecord.noonAsnNr, routingLines);
            JsonNode firstWarehouse = firstWarehouse(routingResponse);
            String selectedWarehousePartnerCode = text(firstWarehouse, "partner_code");
            String selectedWarehouseCode = text(firstWarehouse, "code");
            if (!StringUtils.hasText(selectedWarehousePartnerCode) && !StringUtils.hasText(selectedWarehouseCode)) {
                return;
            }
            mapper.updateAsnRoutingSnapshot(
                    asnId,
                    writeJson(routingResponse),
                    booleanValue(routingResponse, "is_transfer"),
                    selectedWarehousePartnerCode,
                    selectedWarehouseCode,
                    firstNonBlank(selectedWarehousePartnerCode, selectedWarehouseCode),
                    operatorUserId
            );
        } catch (RuntimeException exception) {
            // Keep ASN list sync resilient; the Noon call log keeps the routing prefill failure detail.
        }
    }

    private AsnNoonListSyncRecord buildAsnNoonListSyncRecord(
            Long ownerUserId,
            Long asnId,
            NoonSalesReportBinding binding,
            NoonAsnListRow remoteRow,
            Long operatorUserId
    ) {
        AsnNoonListSyncRecord record = new AsnNoonListSyncRecord();
        record.id = asnId;
        record.ownerUserId = ownerUserId;
        record.projectCode = binding.getProjectCode();
        record.partnerId = binding.getPartnerId();
        record.status = firstNonBlank(remoteRow.localAsnStatus, "LINES_CREATED");
        record.noonAsnNr = remoteRow.asnNr;
        record.noonPartnerAsnId = remoteRow.partnerAsnId;
        record.noonTotalQty = remoteRow.totalQty;
        record.noonAsnStatus = remoteRow.remoteStatus;
        record.noonUpdatedAt = remoteRow.noonUpdatedAt;
        record.warehouseToPartnerCode = remoteRow.warehouseToPartnerCode;
        record.warehouseToCode = remoteRow.warehouseToCode;
        record.warehouseName = firstNonBlank(remoteRow.warehouseToPartnerCode, remoteRow.warehouseToCode);
        if ("FAILED".equals(record.status)) {
            String normalizedStatus = firstNonBlank(OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(remoteRow.remoteStatus), "UNKNOWN");
            record.failureType = "NOON_ASN_" + normalizedStatus;
            record.errorMessage = "Noon ASN 列表状态为 " + normalizedStatus;
        }
        record.operatorUserId = operatorUserId;
        return record;
    }

    private void syncAppointmentFromNoonList(
            AsnListSyncView result,
            Long ownerUserId,
            StoreSiteRecord site,
            NoonSalesReportBinding binding,
            Long asnId,
            String localAsnNo,
            NoonAsnListRow remoteRow,
            Long operatorUserId
    ) {
        AppointmentRecord existing = mapper.selectLatestAppointmentByAsn(ownerUserId, asnId);
        if (remoteRow.hasConfirmedAppointment()) {
            if (remoteRow.remoteFailed()) {
                AppointmentRecord target = existing == null
                        ? insertSyncedAppointment(ownerUserId, site, binding, asnId, localAsnNo, remoteRow, "FAILED", operatorUserId)
                        : existing;
                mapper.correctAppointment(
                        ownerUserId,
                        target.id,
                        "FAILED",
                        null,
                        null,
                        null,
                        "NOON_ASN_" + firstNonBlank(OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(remoteRow.remoteStatus), "FAILED"),
                        "SYNC_ASN_LIST",
                        "Noon ASN 列表显示该约仓已失效：" + firstNonBlank(remoteRow.remoteStatus, "unknown"),
                        operatorUserId
                );
                result.corrected += 1;
                return;
            }
            if (existing == null) {
                AppointmentRecord inserted = insertSyncedAppointment(
                        ownerUserId,
                        site,
                        binding,
                        asnId,
                        localAsnNo,
                        remoteRow,
                        "PENDING",
                        operatorUserId
                );
                mapper.markAppointmentScheduled(
                        inserted.id,
                        remoteRow.appointmentDate,
                        null,
                        remoteRow.appointmentTime,
                        operatorUserId
                );
                syncAppointmentGateAndDocks(ownerUserId, inserted.id, remoteRow, operatorUserId);
                result.scheduled += 1;
                return;
            }
            if (!isSameScheduledAppointment(existing, remoteRow)) {
                mapper.correctAppointment(
                        ownerUserId,
                        existing.id,
                        "SCHEDULED",
                        remoteRow.appointmentDate,
                        null,
                        remoteRow.appointmentTime,
                        null,
                        null,
                        null,
                        operatorUserId
                );
                result.scheduled += 1;
            }
            syncAppointmentGateAndDocks(ownerUserId, existing.id, remoteRow, operatorUserId);
            return;
        }

        if (existing != null && "SCHEDULED".equals(existing.status)) {
            mapper.correctAppointment(
                    ownerUserId,
                    existing.id,
                    "FAILED",
                    null,
                    null,
                    null,
                    "SCHEDULE_NOT_CONFIRMED",
                    "SYNC_ASN_LIST",
                    "Noon ASN 列表未显示已约仓。",
                    operatorUserId
            );
            result.corrected += 1;
        } else if (existing != null
                && remoteRow.remoteFailed()
                && ("PENDING".equals(existing.status) || "RUNNING".equals(existing.status) || "FAILED".equals(existing.status))) {
            mapper.correctAppointment(
                    ownerUserId,
                    existing.id,
                    "FAILED",
                    null,
                    null,
                    null,
                    "NOON_ASN_" + firstNonBlank(OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(remoteRow.remoteStatus), "FAILED"),
                    "SYNC_ASN_LIST",
                    "Noon ASN 列表显示该 ASN 已失效：" + firstNonBlank(remoteRow.remoteStatus, "unknown"),
                    operatorUserId
            );
            result.corrected += 1;
        }
    }

    private AppointmentRecord insertSyncedAppointment(
            Long ownerUserId,
            StoreSiteRecord site,
            NoonSalesReportBinding binding,
            Long asnId,
            String localAsnNo,
            NoonAsnListRow remoteRow,
            String status,
            Long operatorUserId
    ) {
        AppointmentInsertRecord insert = new AppointmentInsertRecord();
        insert.id = mapper.nextAppointmentId();
        insert.asnId = asnId;
        insert.ownerUserId = ownerUserId;
        insert.logicalStoreId = site.logicalStoreId;
        insert.storeCode = site.storeCode;
        insert.storeName = site.storeName;
        insert.siteCode = site.siteCode;
        insert.projectCode = binding.getProjectCode();
        insert.partnerId = binding.getPartnerId();
        insert.localAsnNo = localAsnNo;
        insert.noonAsnNr = remoteRow.asnNr;
        insert.totalUnits = remoteRow.totalQty == null ? 0 : remoteRow.totalQty;
        insert.warehouseToPartnerCode = firstNonBlank(remoteRow.warehouseToPartnerCode, remoteRow.warehouseToCode, "NOON_SYNC");
        insert.warehouseToCode = remoteRow.warehouseToCode;
        insert.apStartDate = remoteRow.appointmentDate;
        insert.apEndDate = remoteRow.appointmentDate;
        insert.apTimeRange = remoteRow.appointmentTime;
        insert.availableToday = false;
        insert.status = status;
        insert.gate = remoteRow.gate;
        insert.docks = remoteRow.docks;
        insert.operatorUserId = operatorUserId;
        mapper.insertAppointment(insert);
        return requireAppointment(ownerUserId, insert.id);
    }

    private void syncAppointmentGateAndDocks(
            Long ownerUserId,
            Long appointmentId,
            NoonAsnListRow remoteRow,
            Long operatorUserId
    ) {
        String gate = trimToNull(remoteRow == null ? null : remoteRow.gate);
        String docks = trimToNull(remoteRow == null ? null : remoteRow.docks);
        if (gate == null && docks == null) {
            return;
        }
        mapper.updateAppointmentGateDocks(ownerUserId, appointmentId, gate, docks, operatorUserId);
    }

    private boolean isSameScheduledAppointment(AppointmentRecord existing, NoonAsnListRow remoteRow) {
        if (!"SCHEDULED".equals(existing.status)) {
            return false;
        }
        String appointmentDate = remoteRow.appointmentDate == null ? null : remoteRow.appointmentDate.toString();
        return appointmentDate != null
                && appointmentDate.equals(existing.appointmentDate)
                && firstNonBlank(remoteRow.appointmentTime, "").equals(firstNonBlank(existing.appointmentTime, ""));
    }

    public List<NoonHttpCallLogView> listAppointmentNoonCalls(BusinessAccessContext access, String appointmentId) {
        Long parsedAppointmentId = parseLongId(appointmentId, "约仓记录不存在。");
        Long ownerUserId = requireOwnerUserId(access, null);
        AppointmentRecord appointment = requireAppointment(ownerUserId, parsedAppointmentId);
        if (!access.canAccessStore(appointment.storeCode)) {
            throw new IllegalArgumentException("当前账号不能查看该店铺约仓记录。");
        }
        return noonHttpCallLogService.listRecent(
                "OFFICIAL_WAREHOUSE_APPOINTMENT",
                String.valueOf(appointment.id),
                appointment.noonAsnNr,
                50
        );
    }

    public List<AppointmentView> listAppointments(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String status,
            String keyword
    ) {
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        Collection<String> storeCodes = trimToNull(storeCode) == null ? access.getStoreCodes() : List.of(storeCode);
        return mapper.listAppointments(
                        ownerUserId,
                        storeCodes,
                        trimToNull(storeCode),
                        trimToNull(siteCode) == null ? null : normalizeSite(siteCode),
                        normalizeStatus(status),
                        keywordLike(keyword),
                        200
                )
                .stream()
                .map(this::toAppointmentView)
                .collect(Collectors.toList());
    }

    public AppointmentView upsertAppointment(
            BusinessAccessContext access,
            String asnId,
            UpsertAppointmentCommand command
    ) {
        return toAppointmentView(upsertAppointmentRecord(access, asnId, command, "缺少自动约仓参数。"));
    }

    public AppointmentView submitManualAppointment(
            BusinessAccessContext access,
            String asnId,
            UpsertAppointmentCommand command
    ) {
        AppointmentRecord appointment = upsertAppointmentRecord(access, asnId, command, "缺少手动约仓参数。");
        LocalDate appointmentDate = parseLocalDate(command.appointmentDate, "请选择可用约仓日期。");
        if (command.appointmentSlotId == null || command.appointmentSlotId <= 0) {
            throw new IllegalArgumentException("请选择可用仓位时段。");
        }
        return runSelectedAppointmentRecord(
                appointment,
                access.getSessionUserId(),
                appointmentDate,
                command.appointmentSlotId,
                trimToNull(command.appointmentTime)
        );
    }

    public List<AppointmentAvailabilityView> listAppointmentAvailability(
            BusinessAccessContext access,
            String asnId,
            UpsertAppointmentCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少查仓位参数。");
        }
        AsnView asn = getAsn(access, asnId);
        if (!"LINES_CREATED".equals(asn.status) || !StringUtils.hasText(asn.noonAsnNr)) {
            throw new IllegalArgumentException("ASN 行创建完成后才能查询仓位。");
        }
        Long ownerUserId = requireOwnerUserId(access, asn.storeCode);
        AsnRecord asnRecord = mapper.selectAsn(ownerUserId, parseLongId(asn.id, "官方仓 ASN 不存在。"));
        AppointmentTask task = toAppointmentTask(asn, command);
        NoonSalesReportBinding binding = resolveBinding(ownerUserId, asnRecord.logicalStoreId, asn.storeCode, asn.siteCode);
        NoonSession session = openNoonSession(ownerUserId, binding);
        return appointmentRunner.queryAvailability(
                        task,
                        noonInboundClient.appointmentClient(
                                session,
                                binding,
                                NoonCallContext.appointment(
                                        "OFFICIAL_WAREHOUSE_APPOINTMENT_AVAILABILITY",
                                        asn.id,
                                        asn.noonAsnNr
                                ),
                                confirmedTask -> persistAsnCurrentWarehouse(
                                        ownerUserId,
                                        asnRecord.id,
                                        confirmedTask,
                                        access.getSessionUserId()
                                )
                        )
                )
                .stream()
                .map(this::toAppointmentAvailabilityView)
                .collect(Collectors.toList());
    }

    private AppointmentRecord upsertAppointmentRecord(
            BusinessAccessContext access,
            String asnId,
            UpsertAppointmentCommand command,
            String missingCommandMessage
    ) {
        if (command == null) {
            throw new IllegalArgumentException(missingCommandMessage);
        }
        AsnView asn = getAsn(access, asnId);
        if (!"LINES_CREATED".equals(asn.status) || !StringUtils.hasText(asn.noonAsnNr)) {
            throw new IllegalArgumentException("ASN 行创建完成后才能约仓。");
        }
        Long parsedAsnId = parseLongId(asn.id, "官方仓 ASN 不存在。");
        Long ownerUserId = requireOwnerUserId(access, asn.storeCode);
        AsnRecord asnRecord = mapper.selectAsn(ownerUserId, parsedAsnId);
        if (asnRecord == null) {
            throw new IllegalArgumentException("官方仓 ASN 不存在。");
        }
        AppointmentRecord existing = mapper.selectLatestAppointmentByAsn(ownerUserId, parsedAsnId);
        AppointmentInsertRecord row = new AppointmentInsertRecord();
        row.asnId = parsedAsnId;
        row.ownerUserId = ownerUserId;
        row.logicalStoreId = asnRecord.logicalStoreId;
        row.storeCode = asn.storeCode;
        row.storeName = asn.storeName;
        row.siteCode = asn.siteCode;
        row.projectCode = asn.projectCode;
        row.partnerId = asn.partnerId;
        row.localAsnNo = asn.localAsnNo;
        row.noonAsnNr = requireText(asn.noonAsnNr, "ASN 缺少 Noon ASN 编号。");
        row.totalUnits = asn.totalQuantity == null ? 0 : asn.totalQuantity;
        row.warehouseToPartnerCode = requireText(
                resolveAppointmentWarehouseToPartnerCode(asn.selectedWarehousePartnerCode, command.warehouseToPartnerCode),
                "请选择到达仓库。"
        );
        row.warehouseToCode = resolveAppointmentWarehouseToCode(asn.selectedWarehouseCode, command.warehouseToCode);
        row.apStartDate = parseLocalDate(command.apStartDate, "请选择约仓开始日期。");
        row.apEndDate = parseLocalDate(command.apEndDate, "请选择约仓结束日期。");
        if (row.apEndDate.isBefore(row.apStartDate)) {
            throw new IllegalArgumentException("约仓结束日期不能早于开始日期。");
        }
        row.apTimeRange = trimToNull(command.apTimeRange);
        row.availableToday = Boolean.TRUE.equals(command.availableToday);
        row.status = "PENDING";
        row.operatorUserId = access.getSessionUserId();

        if (existing == null || "CANCELED".equals(existing.status)) {
            row.id = mapper.nextAppointmentId();
            mapper.insertAppointment(row);
        } else {
            row.id = existing.id;
            mapper.updateAppointmentRequest(row);
        }
        return requireAppointment(ownerUserId, row.id);
    }

    public AppointmentView cancelAppointment(BusinessAccessContext access, String appointmentId) {
        Long parsedAppointmentId = parseLongId(appointmentId, "约仓记录不存在。");
        Long ownerUserId = requireOwnerUserId(access, null);
        AppointmentRecord appointment = requireAppointment(ownerUserId, parsedAppointmentId);
        if (!access.canAccessStore(appointment.storeCode)) {
            throw new IllegalArgumentException("当前账号不能操作该店铺约仓记录。");
        }
        mapper.cancelAppointment(ownerUserId, parsedAppointmentId, access.getSessionUserId());
        return toAppointmentView(requireAppointment(ownerUserId, parsedAppointmentId));
    }

    public AppointmentView correctAppointment(
            BusinessAccessContext access,
            String appointmentId,
            CorrectAppointmentCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少约仓订正参数。");
        }
        Long parsedAppointmentId = parseLongId(appointmentId, "约仓记录不存在。");
        Long ownerUserId = requireOwnerUserId(access, null);
        AppointmentRecord appointment = requireAppointment(ownerUserId, parsedAppointmentId);
        if (!access.canAccessStore(appointment.storeCode)) {
            throw new IllegalArgumentException("当前账号不能订正该店铺约仓记录。");
        }
        String status = OfficialWarehouseStatusPolicy.normalizeAppointmentCorrectionStatus(command.status);
        LocalDate appointmentDate = null;
        String appointmentTime = null;
        Integer appointmentSlotId = null;
        String errorStage = null;
        String failureType = null;
        String errorMessage = null;
        if ("SCHEDULED".equals(status)) {
            appointmentDate = parseLocalDate(command.appointmentDate, "订正为约仓成功时必须填写约仓日期。");
            appointmentTime = trimToNull(command.appointmentTime);
            appointmentSlotId = command.appointmentSlotId;
        } else if ("FAILED".equals(status)) {
            errorStage = firstNonBlank(command.errorStage, "MANUAL_CORRECTION");
            failureType = firstNonBlank(command.failureType, "MANUAL_CORRECTION");
            errorMessage = trimToNull(command.errorMessage);
        }
        mapper.correctAppointment(
                ownerUserId,
                parsedAppointmentId,
                status,
                appointmentDate,
                appointmentSlotId,
                appointmentTime,
                failureType,
                errorStage,
                errorMessage,
                access.getSessionUserId()
        );
        return toAppointmentView(requireAppointment(ownerUserId, parsedAppointmentId));
    }

    public AppointmentView runAppointmentOnce(BusinessAccessContext access, String appointmentId) {
        Long parsedAppointmentId = parseLongId(appointmentId, "约仓记录不存在。");
        Long ownerUserId = requireOwnerUserId(access, null);
        AppointmentRecord appointment = requireAppointment(ownerUserId, parsedAppointmentId);
        if (!access.canAccessStore(appointment.storeCode)) {
            throw new IllegalArgumentException("当前账号不能操作该店铺约仓记录。");
        }
        return runAppointmentRecord(appointment, access.getSessionUserId(), true);
    }

    @Scheduled(
            initialDelayString = "${nuono.official-warehouse.appointment.scheduler.initial-delay-ms:5000}",
            fixedDelayString = "${nuono.official-warehouse.appointment.scheduler.fixed-delay-ms:5000}"
    )
    public void runAppointmentScheduler() {
        if (!appointmentSchedulerEnabled) {
            return;
        }
        mapper.markStaleNoCapacityAppointmentsPending(
                Math.max(1, appointmentStaleNoCapacityMinutes),
                appointmentSystemOperatorUserId
        );
        List<AppointmentRecord> dueAppointments = mapper.listDueAppointments(Math.max(1, appointmentSchedulerMaxItems));
        for (AppointmentRecord appointment : dueAppointments) {
            Long operatorId = schedulerOperatorUserId(appointment);
            if (mapper.claimDueAppointmentForRun(appointment.id, operatorId) == 0) {
                continue;
            }
            try {
                runClaimedAppointmentRecord(appointment, operatorId, true);
            } catch (Exception ignored) {
                // Individual appointment failures are persisted in runClaimedAppointmentRecord.
            }
        }
    }

    private AppointmentView runAppointmentRecord(AppointmentRecord appointment, Long operatorUserId, boolean allowRetry) {
        Long operatorId = operatorUserId == null ? appointment.ownerUserId : operatorUserId;
        if (allowRetry && shouldRetryAppointment(appointment, APPOINTMENT_RISK_BACKOFF_STAGE)) {
            NoonRiskBackoffHold activeHold = currentAppointmentRiskBackoff(appointment);
            if (activeHold != null) {
                markAppointmentPendingRiskBackoff(appointment, activeHold, operatorId);
                return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
            }
        }
        if (mapper.markAppointmentRunning(appointment.id, operatorId) == 0) {
            return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
        }
        return runClaimedAppointmentRecord(appointment, operatorId, allowRetry);
    }

    private AppointmentView runClaimedAppointmentRecord(AppointmentRecord appointment, Long operatorId, boolean allowRetry) {
        if (allowRetry && shouldRetryAppointment(appointment, APPOINTMENT_RISK_BACKOFF_STAGE)) {
            NoonRiskBackoffHold activeHold = currentAppointmentRiskBackoff(appointment);
            if (activeHold != null) {
                markAppointmentPendingRiskBackoff(appointment, activeHold, operatorId);
                return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
            }
        }
        try {
            AppointmentTask task = toAppointmentTask(appointment);
            NoonSalesReportBinding binding = resolveBinding(appointment);
            NoonSession session = openNoonSession(appointment.ownerUserId, binding);
            RunResult result = appointmentRunner.runOnce(
                    task,
                    noonInboundClient.appointmentClient(
                            session,
                            binding,
                            NoonCallContext.appointment(
                                    "OFFICIAL_WAREHOUSE_APPOINTMENT",
                                    String.valueOf(appointment.id),
                                    appointment.noonAsnNr
                            ),
                            confirmedTask -> persistAsnCurrentWarehouse(
                                    appointment.ownerUserId,
                                    appointment.asnId,
                                    confirmedTask,
                                    operatorId
                            )
                    )
            );
            if ("SCHEDULED".equals(result.status)) {
                mapper.markAppointmentScheduled(
                        appointment.id,
                        result.appointmentDate,
                        result.slotId,
                        result.appointmentTime,
                        operatorId
                );
            } else if (allowRetry && shouldRetryAppointment(appointment, result.failureType)) {
                String retryFailureType = appointmentRetryFailureType("SCHEDULE", result.failureType, result.errorMessage);
                String retryErrorStage = appointmentRetryErrorStage("SCHEDULE", retryFailureType);
                mapper.markAppointmentPendingRetry(
                        appointment.id,
                        nextAppointmentRetrySeconds(
                                safeRetryBaseSeconds(),
                                appointment,
                                retryErrorStage,
                                retryFailureType,
                                result.errorMessage
                        ),
                        retryErrorStage,
                        retryFailureType,
                        result.errorMessage,
                        operatorId
                );
            } else {
                mapper.markAppointmentFailed(
                        appointment.id,
                        "SCHEDULE",
                        result.failureType,
                        result.errorMessage,
                        operatorId
                );
            }
        } catch (Exception exception) {
            String message = shrinkMessage(exception);
            NoonRiskBackoffHold riskBackoffHold = recordAppointmentRiskBackoffIfNeeded(appointment, message);
            if (riskBackoffHold != null && allowRetry && shouldRetryAppointment(appointment, riskBackoffHold.getRiskType())) {
                markAppointmentPendingRiskBackoff(appointment, riskBackoffHold, operatorId);
                return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
            }
            String retryFailureType = appointmentRetryFailureType(
                    "NOON_CALL",
                    noonFailureType(exception),
                    message
            );
            String retryErrorStage = appointmentRetryErrorStage("NOON_CALL", retryFailureType);
            if (allowRetry
                    && (isNoCapacityFailure(retryFailureType) || isRetryableNoonCallFailure(retryFailureType))
                    && shouldRetryAppointment(appointment, retryFailureType)) {
                mapper.markAppointmentPendingRetry(
                        appointment.id,
                        nextAppointmentRetrySeconds(
                                safeRetryBaseSeconds(),
                                appointment,
                                retryErrorStage,
                                retryFailureType,
                                message
                        ),
                        retryErrorStage,
                        retryFailureType,
                        message,
                        operatorId
                );
            } else {
                mapper.markAppointmentFailed(
                        appointment.id,
                        retryErrorStage,
                        retryFailureType,
                        message,
                        operatorId
                );
            }
        }
        return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
    }

    private AppointmentView runSelectedAppointmentRecord(
            AppointmentRecord appointment,
            Long operatorUserId,
            LocalDate appointmentDate,
            Integer slotId,
            String appointmentTime
    ) {
        Long operatorId = operatorUserId == null ? appointment.ownerUserId : operatorUserId;
        NoonRiskBackoffHold activeHold = currentAppointmentRiskBackoff(appointment);
        if (activeHold != null) {
            mapper.markAppointmentFailed(
                    appointment.id,
                    APPOINTMENT_RISK_BACKOFF_STAGE,
                    activeHold.getRiskType(),
                    appointmentRiskBackoffMessage(activeHold),
                    operatorId
            );
            return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
        }
        if (mapper.markAppointmentRunning(appointment.id, operatorId) == 0) {
            return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
        }
        try {
            AppointmentTask task = toAppointmentTask(appointment);
            NoonSalesReportBinding binding = resolveBinding(appointment);
            NoonSession session = openNoonSession(appointment.ownerUserId, binding);
            RunResult result = appointmentRunner.scheduleSelectedSlot(
                    task,
                    noonInboundClient.appointmentClient(
                            session,
                            binding,
                            NoonCallContext.appointment(
                                    "OFFICIAL_WAREHOUSE_APPOINTMENT",
                                    String.valueOf(appointment.id),
                                    appointment.noonAsnNr
                            ),
                            confirmedTask -> persistAsnCurrentWarehouse(
                                    appointment.ownerUserId,
                                    appointment.asnId,
                                    confirmedTask,
                                    operatorId
                            )
                    ),
                    appointmentDate,
                    new SlotCapacity(slotId, appointmentTime)
            );
            if ("SCHEDULED".equals(result.status)) {
                mapper.markAppointmentScheduled(
                        appointment.id,
                        result.appointmentDate,
                        result.slotId,
                        result.appointmentTime,
                        operatorId
                );
            } else {
                mapper.markAppointmentFailed(
                        appointment.id,
                        "SCHEDULE",
                        result.failureType,
                        result.errorMessage,
                        operatorId
                );
            }
        } catch (Exception exception) {
            String message = shrinkMessage(exception);
            NoonRiskBackoffHold riskBackoffHold = recordAppointmentRiskBackoffIfNeeded(appointment, message);
            if (riskBackoffHold != null) {
                mapper.markAppointmentFailed(
                        appointment.id,
                        APPOINTMENT_RISK_BACKOFF_STAGE,
                        riskBackoffHold.getRiskType(),
                        appointmentRiskBackoffMessage(riskBackoffHold),
                        operatorId
                );
                return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
            }
            mapper.markAppointmentFailed(
                    appointment.id,
                    "NOON_CALL",
                    exception.getClass().getSimpleName(),
                    message,
                    operatorId
            );
        }
        return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
    }

    private NoonRiskBackoffHold currentAppointmentRiskBackoff(AppointmentRecord appointment) {
        return riskBackoffGuard.currentHold(appointmentRiskBackoffScope(appointment)).orElse(null);
    }

    private NoonRiskBackoffHold recordAppointmentRiskBackoffIfNeeded(AppointmentRecord appointment, String rawFailure) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        if (!isAppointmentRiskBackoffFailure(failureType)) {
            return null;
        }
        return riskBackoffGuard.recordRiskSignal(
                appointmentRiskBackoffScope(appointment),
                failureType.code(),
                APPOINTMENT_RISK_BACKOFF_SOURCE,
                appointment == null ? null : appointment.id,
                null,
                appointmentRiskBackoffDiagnostic(appointment, rawFailure)
        );
    }

    private NoonRiskBackoffScope appointmentRiskBackoffScope(AppointmentRecord appointment) {
        if (appointment == null) {
            return NoonRiskBackoffScope.allNoon(null, null, null);
        }
        return NoonRiskBackoffScope.allNoon(appointment.ownerUserId, appointment.storeCode, appointment.siteCode);
    }

    private boolean isAppointmentRiskBackoffFailure(NoonPullFailureType failureType) {
        return failureType == NoonPullFailureType.RATE_LIMITED
                || failureType == NoonPullFailureType.CAPTCHA_REQUIRED
                || failureType == NoonPullFailureType.BLOCKED_BY_RISK_CONTROL;
    }

    private void markAppointmentPendingRiskBackoff(
            AppointmentRecord appointment,
            NoonRiskBackoffHold hold,
            Long operatorUserId
    ) {
        mapper.markAppointmentPendingRetry(
                appointment.id,
                riskBackoffRetrySeconds(hold),
                APPOINTMENT_RISK_BACKOFF_STAGE,
                hold.getRiskType(),
                appointmentRiskBackoffMessage(hold),
                operatorUserId
        );
    }

    private int riskBackoffRetrySeconds(NoonRiskBackoffHold hold) {
        if (hold == null || hold.getBlockedUntil() == null) {
            return safeRetryBaseSeconds();
        }
        long seconds = Duration.between(LocalDateTime.now(Clock.systemUTC()), hold.getBlockedUntil()).getSeconds();
        if (seconds <= 0) {
            return 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }

    private String appointmentRiskBackoffMessage(NoonRiskBackoffHold hold) {
        if (hold == null) {
            return "Noon 风控退避中。";
        }
        String riskType = firstNonBlank(hold.getRiskType(), "risk_backoff");
        String blockedUntil = hold.getBlockedUntil() == null ? null : hold.getBlockedUntil().toString();
        String diagnostic = trimToNull(hold.getDiagnosticSummary());
        String message = "Noon 风控退避中：" + riskType
                + (StringUtils.hasText(blockedUntil) ? "，冷却至 " + blockedUntil : "")
                + (StringUtils.hasText(diagnostic) ? "，原因：" + diagnostic : "");
        return message.length() > 900 ? message.substring(0, 900) : message;
    }

    private String appointmentRiskBackoffDiagnostic(AppointmentRecord appointment, String rawFailure) {
        List<String> parts = new ArrayList<>();
        if (appointment != null) {
            parts.add("appointmentId=" + appointment.id);
            parts.add("asn=" + appointment.noonAsnNr);
            parts.add("store=" + appointment.storeCode);
            parts.add("site=" + appointment.siteCode);
        }
        parts.add("failed=" + rawFailure);
        return String.join(" ", parts);
    }

    private void persistAsnCurrentWarehouse(
            Long ownerUserId,
            Long asnId,
            AppointmentTask task,
            Long operatorUserId
    ) {
        if (ownerUserId == null || asnId == null || task == null || !StringUtils.hasText(task.warehouseTo)) {
            return;
        }
        String warehouseToPartnerCode = task.warehouseTo.trim();
        mapper.updateAsnCurrentWarehouse(
                ownerUserId,
                asnId,
                warehouseToPartnerCode,
                trimToNull(task.warehouseToCode),
                warehouseToPartnerCode,
                operatorUserId == null ? ownerUserId : operatorUserId
        );
    }

    private boolean shouldRetryAppointment(AppointmentRecord appointment, String failureType) {
        if (failureType != null && failureType.startsWith("NOON_ASN_")) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return appointment.apEndDateValue == null || !today.isAfter(appointment.apEndDateValue);
    }

    private int safeRetryBaseSeconds() {
        return appointmentRetryBaseSeconds <= 0 ? DEFAULT_APPOINTMENT_RETRY_SECONDS : appointmentRetryBaseSeconds;
    }

    static int nextAppointmentRetrySeconds(int baseRetrySeconds, AppointmentRecord appointment) {
        return nextAppointmentRetrySeconds(baseRetrySeconds, appointment, "SCHEDULE", "SCHEDULE_APPOINTMENT", null);
    }

    static int nextAppointmentRetrySeconds(
            int baseRetrySeconds,
            AppointmentRecord appointment,
            String errorStage,
            String failureType,
            String errorMessage
    ) {
        if (isNoCapacityFailure(failureType)) {
            return 0;
        }
        int safeBase = baseRetrySeconds <= 0 ? DEFAULT_APPOINTMENT_RETRY_SECONDS : baseRetrySeconds;
        int previousAttemptCount = appointment == null || appointment.attemptCount == null
                ? 0
                : Math.max(0, appointment.attemptCount);
        int failedAttemptsAfterCurrentRun = previousAttemptCount + 1;
        long multiplier = 1L << Math.min(30, failedAttemptsAfterCurrentRun);
        long seconds = (long) safeBase * multiplier;
        return (int) Math.min(seconds, APPOINTMENT_RETRY_CAP_SECONDS);
    }

    static String appointmentRetryFailureType(String errorStage, String failureType, String errorMessage) {
        if ("NOON_NO_CAPACITY".equalsIgnoreCase(trimToNull(failureType))) {
            return "NO_CAPACITY";
        }
        if (isNoonAccessBlocked(errorStage, failureType, errorMessage)) {
            return "NOON_ACCESS_BLOCKED";
        }
        if (isNoonAccessFailure(errorStage, failureType, errorMessage)) {
            return "NOON_ACCESS_FAILURE";
        }
        return failureType;
    }

    static boolean isRetryableNoonCallFailure(String retryFailureType) {
        return isNoonAccessFailureType(retryFailureType);
    }

    private static String appointmentRetryErrorStage(String fallbackStage, String retryFailureType) {
        if (isNoCapacityFailure(retryFailureType)) {
            return "SCHEDULE";
        }
        return isNoonAccessFailureType(retryFailureType) ? "NOON_ACCESS" : fallbackStage;
    }

    private static String noonFailureType(Exception exception) {
        if (exception instanceof NoonOperationException) {
            return ((NoonOperationException) exception).getClassification().getCode();
        }
        return exception == null ? "UNKNOWN" : exception.getClass().getSimpleName();
    }

    private static boolean isNoCapacityFailure(String failureType) {
        return "NO_CAPACITY".equalsIgnoreCase(trimToNull(failureType));
    }

    private static boolean isNoonAccessBlocked(String errorStage, String failureType, String errorMessage) {
        String combined = retryText(errorStage, failureType, errorMessage);
        return combined.contains("http 407")
                || combined.contains("proxy authentication")
                || combined.contains("tunnel failed");
    }

    private static boolean isNoonAccessFailure(String errorStage, String failureType, String errorMessage) {
        if (isNoonAccessBlocked(errorStage, failureType, errorMessage)) {
            return true;
        }
        String combined = retryText(errorStage, failureType, errorMessage);
        return combined.contains("io_exception")
                || combined.contains("connection reset")
                || combined.contains("connection refused")
                || combined.contains("connect timed out")
                || combined.contains("request timed out")
                || combined.contains("read timed out")
                || combined.contains("no route to host")
                || combined.contains("buffer_underflow")
                || combined.contains("header parser received no bytes")
                || combined.contains("with eof")
                || combined.contains("non decrypted")
                || combined.contains("eof reached")
                || combined.contains("unexpected end")
                || combined.contains("connection closed")
                || combined.contains("closed channel")
                || combined.contains("http 408")
                || combined.contains("http 500")
                || combined.contains("http 502")
                || combined.contains("http 503")
                || combined.contains("http 504");
    }

    private static boolean isNoonAccessFailureType(String retryFailureType) {
        return "NOON_ACCESS_BLOCKED".equalsIgnoreCase(trimToNull(retryFailureType))
                || "NOON_ACCESS_FAILURE".equalsIgnoreCase(trimToNull(retryFailureType));
    }

    private static String retryText(String errorStage, String failureType, String errorMessage) {
        return (String.valueOf(errorStage) + " " + String.valueOf(failureType) + " " + String.valueOf(errorMessage))
                .toLowerCase(Locale.ROOT);
    }

    private Long schedulerOperatorUserId(AppointmentRecord appointment) {
        if (appointmentSystemOperatorUserId > 0) {
            return appointmentSystemOperatorUserId;
        }
        return appointment == null ? null : appointment.ownerUserId;
    }

    private AppointmentTask toAppointmentTask(AppointmentRecord appointment) {
        AppointmentTask task = new AppointmentTask();
        task.appointmentId = appointment.id;
        task.asnId = appointment.asnId;
        task.noonAsnNr = appointment.noonAsnNr;
        task.totalUnits = appointment.totalUnits;
        task.warehouseTo = appointment.warehouseToPartnerCode;
        task.warehouseToCode = appointment.warehouseToCode;
        task.apStartDate = appointment.apStartDateValue;
        task.apEndDate = appointment.apEndDateValue;
        task.apTimeRange = appointment.apTimeRange;
        task.availableToday = Boolean.TRUE.equals(appointment.availableToday);
        return task;
    }

    private AppointmentTask toAppointmentTask(AsnView asn, UpsertAppointmentCommand command) {
        AppointmentTask task = new AppointmentTask();
        task.asnId = parseLongId(asn.id, "官方仓 ASN 不存在。");
        task.noonAsnNr = requireText(asn.noonAsnNr, "ASN 缺少 Noon ASN 编号。");
        task.totalUnits = asn.totalQuantity == null ? 0 : asn.totalQuantity;
        task.warehouseTo = requireText(
                resolveAppointmentWarehouseToPartnerCode(asn.selectedWarehousePartnerCode, command.warehouseToPartnerCode),
                "请选择到达仓库。"
        );
        task.warehouseToCode = resolveAppointmentWarehouseToCode(
                asn.selectedWarehouseCode,
                command.warehouseToCode
        );
        task.apStartDate = parseLocalDate(command.apStartDate, "请选择约仓开始日期。");
        task.apEndDate = parseLocalDate(command.apEndDate, "请选择约仓结束日期。");
        if (task.apEndDate.isBefore(task.apStartDate)) {
            throw new IllegalArgumentException("约仓结束日期不能早于开始日期。");
        }
        task.apTimeRange = trimToNull(command.apTimeRange);
        task.availableToday = Boolean.TRUE.equals(command.availableToday);
        return task;
    }

    private NoonSalesReportBinding resolveBinding(AppointmentRecord appointment) {
        return resolveBinding(appointment.ownerUserId, appointment.logicalStoreId, appointment.storeCode, appointment.siteCode);
    }

    private NoonSalesReportBinding resolveBinding(Long ownerUserId, Long logicalStoreId, String storeCode, String siteCode) {
        return bindingResolver.resolve(new NoonSalesReportRequest(
                ownerUserId,
                logicalStoreId,
                storeCode,
                siteCode,
                LocalDate.now(),
                LocalDate.now()
        ));
    }

    private NoonSession openNoonSession(Long ownerUserId, NoonSalesReportBinding binding) {
        return noonSessionGateway.loginWithPersistedCookie(
                ownerUserId,
                binding.getNoonUser(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );
    }

    private String resolveNoonUser(AsnRecord row) {
        if (row == null) {
            return null;
        }
        try {
            NoonSalesReportBinding binding = resolveBinding(row.ownerUserId, row.logicalStoreId, row.storeCode, row.siteCode);
            return binding.getNoonUser();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private AppointmentRecord requireAppointment(Long ownerUserId, Long appointmentId) {
        AppointmentRecord appointment = mapper.selectAppointment(ownerUserId, appointmentId);
        if (appointment == null) {
            throw new IllegalArgumentException("约仓记录不存在或无权访问。");
        }
        return appointment;
    }

    private void sealRemoteAsnAfterLineCreation(
            NoonSession session,
            NoonSalesReportBinding binding,
            Long asnId,
            String localAsnNo,
            String asnNr,
            NoonCallContext context,
            String warehouseTo,
            Long operatorUserId
    ) {
        String normalizedWarehouseTo = requireText(warehouseTo, "Noon 路由仓响应缺少到达仓库，不能 sealed ASN。");
        AsnDetail detail = noonInboundClient.queryAsnDetail(session, binding, context, asnNr);
        mapper.updateAsnNoonStatus(asnId, detail.status, operatorUserId);
        String status = OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(detail.status);
        if (OfficialWarehouseStatusPolicy.isNoonAsnFailureStatus(status)) {
            throw new IllegalStateException("Noon ASN 状态不可 sealed：" + status);
        }
        if (!OfficialWarehouseStatusPolicy.isNoonAsnReadyForAppointmentStatus(status)
                && !OfficialWarehouseStatusPolicy.isNoonAsnScheduledStatus(status)) {
            noonInboundClient.setWarehouses(session, binding, context, asnNr, normalizedWarehouseTo);
            AsnDetail sealDetail = noonInboundClient.sealAsn(session, binding, context, asnNr);
            mapper.updateAsnNoonStatus(asnId, sealDetail.status, operatorUserId);
        }
        AsnDetail sealedDetail = waitRemoteAsnReadyForAppointment(session, binding, asnId, asnNr, context, operatorUserId);
        mapper.updateAsnNoonStatus(asnId, sealedDetail.status, operatorUserId);
    }

    private AsnDetail waitRemoteAsnReadyForAppointment(
            NoonSession session,
            NoonSalesReportBinding binding,
            Long asnId,
            String asnNr,
            NoonCallContext context,
            Long operatorUserId
    ) {
        AsnDetail lastDetail = null;
        for (int attempt = 0; attempt < DEFAULT_SEAL_CHECK_ATTEMPTS; attempt++) {
            lastDetail = noonInboundClient.queryAsnDetail(session, binding, context, asnNr);
            mapper.updateAsnNoonStatus(asnId, lastDetail.status, operatorUserId);
            String status = OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(lastDetail.status);
            if (OfficialWarehouseStatusPolicy.isNoonAsnFailureStatus(status)) {
                throw new IllegalStateException("Noon ASN 状态不可约仓：" + status);
            }
            if (OfficialWarehouseStatusPolicy.isNoonAsnReadyForAppointmentStatus(status)
                    || OfficialWarehouseStatusPolicy.isNoonAsnScheduledStatus(status)) {
                return lastDetail;
            }
            if (attempt + 1 < DEFAULT_SEAL_CHECK_ATTEMPTS) {
                sleepBeforeNextSealCheck();
            }
        }
        String lastStatus = OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(lastDetail == null ? null : lastDetail.status);
        throw new IllegalStateException("Noon 已设置仓库，但 ASN 尚未 sealed，当前状态：" + firstNonBlank(lastStatus, "UNKNOWN"));
    }

    private static void sleepBeforeNextSealCheck() {
        try {
            Thread.sleep(DEFAULT_SEAL_CHECK_INTERVAL_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Noon ASN sealed 被中断。", exception);
        }
    }


    private void applyNoonLineResponse(Long asnId, JsonNode linesResponse, Long operatorUserId) {
        JsonNode data = linesResponse == null ? null : linesResponse.path("data");
        if (data == null || !data.isArray()) {
            return;
        }
        for (JsonNode line : data) {
            mapper.updateLineFromNoon(
                    asnId,
                    text(line, "psku_code"),
                    text(line, "sku"),
                    longValue(line, "id_partner_asn_line"),
                    intValue(line, "id_cluster"),
                    intValue(line, "id_storage_type"),
                    text(line, "cluster_code"),
                    text(line, "asn_status"),
                    text(line, "country_code"),
                    booleanValue(line, "is_labeled"),
                    booleanValue(line, "is_repl_tool_asn"),
                    operatorUserId
            );
        }
    }

    private Map<Long, List<AsnInboundReceiptRecord>> inboundReceiptsByAsn(
            Long ownerUserId,
            List<Long> asnIds
    ) {
        List<AsnInboundReceiptRecord> receipts = inboundReceipts(ownerUserId, asnIds);
        return receipts.stream().collect(Collectors.groupingBy(
                receipt -> receipt.asnId,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private List<AsnInboundReceiptRecord> inboundReceipts(Long ownerUserId, List<Long> asnIds) {
        if (asnIds == null || asnIds.isEmpty()) {
            return List.of();
        }
        List<AsnInboundReceiptRecord> receipts = mapper.listAsnInboundReceipts(ownerUserId, asnIds);
        return receipts == null ? List.of() : receipts;
    }

    private AsnInboundSummaryView inboundSummary(AsnRecord asn, List<AsnInboundReceiptRecord> receipts) {
        AsnInboundSummaryView summary = new AsnInboundSummaryView();
        summary.asnQuantity = inboundQuantity(asn.noonTotalQty == null ? asn.totalQuantity : asn.noonTotalQty);
        summary.reportConnected = receipts != null && !receipts.isEmpty();
        if (receipts == null) {
            return summary;
        }
        for (AsnInboundReceiptRecord receipt : receipts) {
            long expected = inboundQuantity(receipt.qtyExpected);
            long received = inboundQuantity(receipt.receivedQty);
            summary.expectedQuantity += expected;
            summary.receivedQuantity += received;
            summary.qcFailedQuantity += inboundQuantity(receipt.qcFailedQty);
            summary.unidentifiedQuantity += inboundQuantity(receipt.unidentifiedQty);
            summary.shortQuantity += Math.max(expected - received, 0);
            summary.overQuantity += Math.max(received - expected, 0);
            summary.receiptLineCount += 1;
            if (!"NORMAL".equals(normalizeInboundCode(receipt.receiptStatus))) {
                summary.exceptionLineCount += 1;
            }
            summary.latestImportedAt = latestInboundTimestamp(summary.latestImportedAt, receipt.importedAt);
        }
        return summary;
    }

    private AsnInboundLineView inboundLine(AsnLineRecord row) {
        AsnInboundLineView view = new AsnInboundLineView();
        view.asnLineId = String.valueOf(row.id);
        view.productVariantId = row.productVariantId == null ? null : String.valueOf(row.productVariantId);
        view.productSiteOfferId = row.productSiteOfferId == null ? null : String.valueOf(row.productSiteOfferId);
        view.partnerSku = row.partnerSku;
        view.pskuCode = row.pskuCode;
        view.noonSku = row.noonSku;
        view.title = row.titleCache;
        view.imageUrl = ProductImageUrlSupport.normalize(row.imageUrlCache);
        view.asnQuantity = inboundQuantity(row.qty);
        view.reportOnly = false;
        view.inboundStatus = "NO_RECEIPT";
        view.matchStatus = "NO_RECEIPT";
        return view;
    }

    private AsnInboundLineView inboundReportOnlyLine(
            AsnInboundReceiptRecord receipt,
            ProductCandidateRecord productCandidate
    ) {
        AsnInboundLineView view = new AsnInboundLineView();
        Long productVariantId = receipt.productVariantId != null
                ? receipt.productVariantId
                : productCandidate == null ? null : productCandidate.productVariantId;
        Long productSiteOfferId = receipt.productSiteOfferId != null
                ? receipt.productSiteOfferId
                : productCandidate == null ? null : productCandidate.productSiteOfferId;
        view.productVariantId = productVariantId == null ? null : String.valueOf(productVariantId);
        view.productSiteOfferId = productSiteOfferId == null ? null : String.valueOf(productSiteOfferId);
        view.partnerSku = receipt.partnerSku;
        view.pskuCode = receipt.pskuCode;
        view.noonSku = receipt.noonSku;
        if (productCandidate != null) {
            view.title = productCandidate.titleCache;
            view.imageUrl = ProductImageUrlSupport.normalize(productCandidate.imageUrlCache);
        }
        view.reportOnly = true;
        view.matchStatus = "REPORT_ONLY";
        return view;
    }

    private void registerInboundProductCandidate(
            Map<String, ProductCandidateRecord> uniqueCandidatesByPartnerSku,
            Set<String> ambiguousPartnerSkus,
            ProductCandidateRecord candidate
    ) {
        String key = inboundPartnerKey(candidate.partnerSku);
        if (key == null || ambiguousPartnerSkus.contains(key)) {
            return;
        }
        ProductCandidateRecord existing = uniqueCandidatesByPartnerSku.putIfAbsent(key, candidate);
        if (existing != null && !Objects.equals(existing.productVariantId, candidate.productVariantId)) {
            uniqueCandidatesByPartnerSku.remove(key);
            ambiguousPartnerSkus.add(key);
        }
    }

    private void registerInboundLineKeys(
            Map<String, AsnInboundLineView> uniqueLinesByKey,
            Set<String> ambiguousKeys,
            AsnInboundLineView line,
            String childSku
    ) {
        registerInboundLineKey(uniqueLinesByKey, ambiguousKeys, inboundNoonKey(line.noonSku), line);
        registerInboundLineKey(uniqueLinesByKey, ambiguousKeys, inboundNoonKey(line.pskuCode), line);
        registerInboundLineKey(uniqueLinesByKey, ambiguousKeys, inboundNoonKey(childSku), line);
        registerInboundLineKey(uniqueLinesByKey, ambiguousKeys, inboundPartnerKey(line.partnerSku), line);
    }

    private void registerInboundLineKey(
            Map<String, AsnInboundLineView> uniqueLinesByKey,
            Set<String> ambiguousKeys,
            String key,
            AsnInboundLineView line
    ) {
        if (key == null || ambiguousKeys.contains(key)) {
            return;
        }
        AsnInboundLineView existing = uniqueLinesByKey.putIfAbsent(key, line);
        if (existing != null && existing != line) {
            uniqueLinesByKey.remove(key);
            ambiguousKeys.add(key);
        }
    }

    private AsnInboundLineView findInboundLineByBusinessKey(
            Map<String, AsnInboundLineView> uniqueLinesByKey,
            Set<String> ambiguousKeys,
            AsnInboundReceiptRecord receipt
    ) {
        for (String key : new String[] {
                inboundNoonKey(receipt.noonSku),
                inboundNoonKey(receipt.pskuCode),
                inboundPartnerKey(receipt.partnerSku)
        }) {
            if (key != null && !ambiguousKeys.contains(key) && uniqueLinesByKey.containsKey(key)) {
                return uniqueLinesByKey.get(key);
            }
        }
        return null;
    }

    private String inboundReportOnlyKey(AsnInboundReceiptRecord receipt) {
        return firstNonBlank(
                inboundPartnerKey(receipt.partnerSku),
                inboundNoonKey(receipt.noonSku),
                inboundNoonKey(receipt.pskuCode),
                inboundNoonKey(receipt.pbarcodeCanonical),
                receipt.reportRowId == null ? null : "ROW:" + receipt.reportRowId
        );
    }

    private void accumulateInboundReceipt(
            AsnInboundLineView line,
            AsnInboundReceiptRecord receipt,
            boolean matchedByBusinessKey
    ) {
        long expected = inboundQuantity(receipt.qtyExpected);
        long received = inboundQuantity(receipt.receivedQty);
        line.expectedQuantity += expected;
        line.receivedQuantity += received;
        line.qcFailedQuantity += inboundQuantity(receipt.qcFailedQty);
        line.unidentifiedQuantity += inboundQuantity(receipt.unidentifiedQty);
        line.receiptLineCount += 1;
        line.partnerSku = firstNonBlank(line.partnerSku, receipt.partnerSku);
        line.pskuCode = firstNonBlank(line.pskuCode, receipt.pskuCode);
        line.noonSku = firstNonBlank(line.noonSku, receipt.noonSku);
        line.qcFailedReason = firstNonBlank(line.qcFailedReason, receipt.qcFailedReason);
        line.partnerWarehouse = firstNonBlank(line.partnerWarehouse, receipt.partnerWarehouse);
        line.noonWarehouse = firstNonBlank(line.noonWarehouse, receipt.noonWarehouse);
        line.asnCompletedAt = latestInboundTimestamp(line.asnCompletedAt, receipt.asnCompletedAt);
        line.latestImportedAt = latestInboundTimestamp(line.latestImportedAt, receipt.importedAt);
        if (!line.reportOnly) {
            line.matchStatus = matchedByBusinessKey ? "MATCHED_BY_BUSINESS_KEY" : "MATCHED";
        }
    }

    private void finalizeInboundLine(AsnInboundLineView line) {
        line.shortQuantity = Math.max(line.expectedQuantity - line.receivedQuantity, 0);
        line.overQuantity = Math.max(line.receivedQuantity - line.expectedQuantity, 0);
        if (line.receiptLineCount <= 0) {
            line.inboundStatus = "NO_RECEIPT";
        } else if (line.reportOnly) {
            line.inboundStatus = "UNMATCHED";
        } else if (line.unidentifiedQuantity > 0) {
            line.inboundStatus = "UNIDENTIFIED";
        } else if (line.qcFailedQuantity > 0) {
            line.inboundStatus = "QC_FAILED";
        } else if (line.shortQuantity > 0) {
            line.inboundStatus = "SHORT_RECEIVED";
        } else if (line.overQuantity > 0) {
            line.inboundStatus = "OVER_RECEIVED";
        } else {
            line.inboundStatus = "NORMAL";
        }
    }

    private String inboundNoonKey(String value) {
        String normalized = normalizeInboundIdentity(value);
        return normalized == null ? null : "NOON:" + normalized;
    }

    private String inboundPartnerKey(String value) {
        String normalized = normalizeInboundIdentity(value);
        return normalized == null ? null : "PARTNER:" + normalized;
    }

    private String normalizeInboundIdentity(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeInboundCode(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    private String latestInboundTimestamp(String current, String candidate) {
        String normalizedCandidate = trimToNull(candidate);
        if (normalizedCandidate == null) {
            return current;
        }
        String normalizedCurrent = trimToNull(current);
        return normalizedCurrent == null || normalizedCandidate.compareTo(normalizedCurrent) > 0
                ? normalizedCandidate
                : normalizedCurrent;
    }

    private int inboundQuantity(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private AsnView toAsnView(AsnRecord row, boolean withLines) {
        AsnView view = new AsnView();
        view.id = String.valueOf(row.id);
        view.inboundNo = row.localAsnNo;
        view.localAsnNo = row.localAsnNo;
        view.sourceType = row.sourceType;
        view.storeCode = row.storeCode;
        view.storeName = row.storeName;
        view.siteCode = row.siteCode;
        view.projectCode = row.projectCode;
        view.partnerId = row.partnerId;
        view.status = row.status;
        view.asnNo = row.noonAsnNr;
        view.noonAsnNr = row.noonAsnNr;
        view.noonAsnStatus = row.noonAsnStatus;
        view.noonPartnerAsnId = row.noonPartnerAsnId == null ? null : String.valueOf(row.noonPartnerAsnId);
        view.productCount = row.productCount;
        view.totalQuantity = row.totalQuantity;
        view.selectedWarehouseCode = row.selectedWarehouseCode;
        view.selectedWarehousePartnerCode = row.selectedWarehousePartnerCode;
        view.selectedWarehouseName = row.selectedWarehouseName;
        view.routingIsTransfer = row.routingIsTransfer;
        view.errorStage = row.errorStage;
        view.failureType = row.failureType;
        view.errorMessage = row.errorMessage;
        view.submittedAt = row.submittedAt;
        view.finishedAt = row.finishedAt;
        view.createdAt = row.createdAt;
        view.updatedAt = row.updatedAt;
        view.routingWarehouses = parseRoutingWarehouses(row.routingResponseJson);
        AppointmentRecord appointment = mapper.selectLatestAppointmentByAsn(row.ownerUserId, row.id);
        view.appointment = appointment == null ? null : toAppointmentView(appointment);
        if (withLines) {
            List<AsnShippingBatchLinkRecord> linkRecords = mapper.listAsnShippingBatchLinks(row.id);
            view.shippingBatchLinks = linkRecords.stream()
                    .map(this::toAsnShippingBatchLinkView)
                    .collect(Collectors.toList());
            Map<Long, List<AsnShippingBatchLinkRecord>> linksByLineId = linkRecords.stream()
                    .collect(Collectors.groupingBy(
                            link -> link.asnLineId,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            view.lines = mapper.listAsnLines(row.id).stream().map(lineRow -> {
                AsnLineView lineView = toAsnLineView(lineRow);
                lineView.shippingBatchLinks = linksByLineId.getOrDefault(lineRow.id, List.of()).stream()
                        .map(this::toAsnShippingBatchLinkView)
                        .collect(Collectors.toList());
                return lineView;
            }).collect(Collectors.toList());
        }
        return view;
    }

    private AsnLineView toAsnLineView(AsnLineRecord row) {
        AsnLineView view = new AsnLineView();
        view.id = String.valueOf(row.id);
        view.productVariantId = String.valueOf(row.productVariantId);
        view.productSiteOfferId = row.productSiteOfferId == null ? null : String.valueOf(row.productSiteOfferId);
        view.skuParent = row.skuParent;
        view.partnerSku = row.partnerSku;
        view.childSku = row.childSku;
        view.pskuCode = row.pskuCode;
        view.noonSku = row.noonSku;
        view.title = row.titleCache;
        view.titleEn = row.titleEn;
        view.brand = row.brandCache;
        view.imageUrl = ProductImageUrlSupport.normalize(row.imageUrlCache);
        view.quantity = row.qty;
        view.productLengthCm = row.productLengthCm;
        view.productWidthCm = row.productWidthCm;
        view.productHeightCm = row.productHeightCm;
        view.productWeightG = row.productWeightG;
        view.cubicFeet = row.cubicFeet;
        view.storageTypeCode = row.storageTypeCode;
        view.noonPartnerAsnLineId = row.noonPartnerAsnLineId == null ? null : String.valueOf(row.noonPartnerAsnLineId);
        view.noonClusterCode = row.noonClusterCode;
        view.noonAsnStatus = row.noonAsnStatus;
        view.noonCountryCode = row.noonCountryCode;
        view.labeled = row.labeled;
        view.replToolAsn = row.replToolAsn;
        view.lineStatus = row.lineStatus;
        view.errorMessage = row.errorMessage;
        return view;
    }

    private AsnShippingBatchLinkView toAsnShippingBatchLinkView(AsnShippingBatchLinkRecord row) {
        AsnShippingBatchLinkView view = new AsnShippingBatchLinkView();
        view.id = row.id == null ? null : String.valueOf(row.id);
        view.asnId = row.asnId == null ? null : String.valueOf(row.asnId);
        view.asnLineId = row.asnLineId == null ? null : String.valueOf(row.asnLineId);
        view.shippingBatchId = row.shippingBatchId == null ? null : String.valueOf(row.shippingBatchId);
        view.shippingBatchNo = row.shippingBatchNo;
        view.shippingBatchSourceId = row.shippingBatchSourceId == null ? null : String.valueOf(row.shippingBatchSourceId);
        view.inTransitBatchId = row.inTransitBatchId == null ? null : String.valueOf(row.inTransitBatchId);
        view.batchReferenceNo = row.batchReferenceNo;
        view.trackingNo = row.trackingNo;
        view.externalShipmentNo = row.externalShipmentNo;
        view.forwarderName = row.forwarderName;
        view.transportMode = row.transportMode;
        view.latestNodeStatus = row.latestNodeStatus;
        view.inTransitGoodsLineId = row.inTransitGoodsLineId == null ? null : String.valueOf(row.inTransitGoodsLineId);
        view.fulfillmentBalanceId = row.fulfillmentBalanceId == null ? null : String.valueOf(row.fulfillmentBalanceId);
        view.purchaseOrderId = row.purchaseOrderId == null ? null : String.valueOf(row.purchaseOrderId);
        view.purchaseOrderNo = row.purchaseOrderNo;
        view.purchaseOrderItemId = row.purchaseOrderItemId == null ? null : String.valueOf(row.purchaseOrderItemId);
        view.purchaseOrderItemSiteId = row.purchaseOrderItemSiteId == null ? null : String.valueOf(row.purchaseOrderItemSiteId);
        view.productMasterId = row.productMasterId == null ? null : String.valueOf(row.productMasterId);
        view.productVariantId = row.productVariantId == null ? null : String.valueOf(row.productVariantId);
        view.partnerSku = row.partnerSku;
        view.pskuCode = row.pskuCode;
        view.quantity = row.quantity;
        view.relationStatus = row.relationStatus;
        view.relationBasis = row.relationBasis;
        view.createdAt = row.createdAt;
        return view;
    }

    private AppointmentView toAppointmentView(AppointmentRecord row) {
        AppointmentView view = new AppointmentView();
        view.id = String.valueOf(row.id);
        view.asnId = row.asnId == null ? null : String.valueOf(row.asnId);
        view.localAsnNo = row.localAsnNo;
        view.noonAsnNr = row.noonAsnNr;
        view.storeCode = row.storeCode;
        view.siteCode = row.siteCode;
        view.status = row.status;
        view.warehouseToPartnerCode = row.warehouseToPartnerCode;
        view.warehouseToCode = row.warehouseToCode;
        view.apStartDate = row.apStartDate;
        view.apEndDate = row.apEndDate;
        view.apTimeRange = row.apTimeRange;
        view.availableToday = row.availableToday;
        view.appointmentDate = row.appointmentDate;
        view.appointmentSlotId = row.appointmentSlotId;
        view.appointmentTime = row.appointmentTime;
        view.gate = row.gate;
        view.docks = row.docks;
        view.attemptCount = row.attemptCount;
        view.lastAttemptAt = row.lastAttemptAt;
        view.nextAttemptAt = row.nextAttemptAt;
        view.apSuccessTime = row.apSuccessTime;
        view.errorStage = row.errorStage;
        view.failureType = row.failureType;
        view.errorMessage = row.errorMessage;
        view.createdAt = row.createdAt;
        view.updatedAt = row.updatedAt;
        return view;
    }

    private AppointmentAvailabilityView toAppointmentAvailabilityView(AvailableSlot slot) {
        AppointmentAvailabilityView view = new AppointmentAvailabilityView();
        view.date = slot.capacityDate == null ? null : slot.capacityDate.toString();
        view.slotId = slot.slotId;
        view.time = slot.name;
        view.label = java.util.stream.Stream.of(view.date, view.time)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
        return view;
    }

    private ProductCandidateView toProductCandidateView(ProductCandidateRecord row) {
        ProductCandidateView view = new ProductCandidateView();
        view.productVariantId = String.valueOf(row.productVariantId);
        view.productSiteOfferId = row.productSiteOfferId == null ? null : String.valueOf(row.productSiteOfferId);
        view.storeCode = row.storeCode;
        view.storeName = row.storeName;
        view.siteCode = row.siteCode;
        view.skuParent = row.skuParent;
        view.partnerSku = row.partnerSku;
        view.childSku = row.childSku;
        view.pskuCode = row.pskuCode;
        view.noonSku = row.noonSku;
        view.title = row.titleCache;
        view.titleEn = row.titleEn;
        view.brand = row.brandCache;
        view.imageUrl = ProductImageUrlSupport.normalize(row.imageUrlCache);
        view.productLengthCm = row.productLengthCm;
        view.productWidthCm = row.productWidthCm;
        view.productHeightCm = row.productHeightCm;
        view.productWeightG = row.productWeightG;
        view.cubicFeet = calculateCubicFeet(row.productLengthCm, row.productWidthCm, row.productHeightCm);
        view.cartonLengthCm = row.cartonLengthCm;
        view.cartonWidthCm = row.cartonWidthCm;
        view.cartonHeightCm = row.cartonHeightCm;
        view.cartonWeightKg = row.cartonWeightKg;
        view.cartonQuantity = row.cartonQuantity;
        view.storageTypeCode = firstNonBlank(row.storageTypeCode, "standard");
        view.logisticsProfileStatus = firstNonBlank(row.logisticsProfileStatus, "needs_review");
        view.batteryElectricType = firstNonBlank(row.batteryElectricType, "unknown");
        view.magneticType = firstNonBlank(row.magneticType, "unknown");
        view.liquidType = firstNonBlank(row.liquidType, "unknown");
        view.powderType = firstNonBlank(row.powderType, "unknown");
        view.woodenMaterialType = firstNonBlank(row.woodenMaterialType, "unknown");
        view.bladeWeaponType = firstNonBlank(row.bladeWeaponType, "unknown");
        view.manualConfirmRequired = row.manualConfirmRequired == null || row.manualConfirmRequired;
        if (!StringUtils.hasText(row.pskuCode)) {
            view.missingTags.add("缺Noon ASN PSKU");
        }
        if (!StringUtils.hasText(row.noonSku)) {
            view.missingTags.add("缺 Noon SKU");
        }
        if (view.cubicFeet == null) {
            view.missingTags.add("缺尺寸");
        }
        return view;
    }

    private ShippingBatchCandidateView toShippingBatchCandidateView(ShippingBatchCandidateRecord row) {
        ShippingBatchCandidateView view = new ShippingBatchCandidateView();
        view.id = row.id == null ? null : String.valueOf(row.id);
        view.sourceKind = firstNonBlank(row.sourceKind, "IN_TRANSIT_BATCH");
        view.batchNo = row.batchNo;
        view.trackingNo = row.trackingNo;
        view.externalShipmentNo = row.externalShipmentNo;
        view.forwarderName = row.forwarderName;
        view.transportMode = row.transportMode;
        view.status = row.status;
        view.latestNodeStatus = row.latestNodeStatus;
        view.selectedOptionId = row.selectedOptionId == null ? null : String.valueOf(row.selectedOptionId);
        view.totalQuantity = row.totalQuantity;
        view.storeSiteQuantity = row.storeSiteQuantity;
        view.linkedQuantity = row.linkedQuantity;
        view.remainingQuantity = row.remainingQuantity;
        view.scheduledAppointmentQuantity = row.scheduledAppointmentQuantity;
        view.alreadyAppointed = row.alreadyAppointed != null && row.alreadyAppointed;
        view.batchUsedByAsn = row.batchUsedByAsn != null && row.batchUsedByAsn;
        view.batchUsageLabel = firstNonBlank(
                row.batchUsageLabel,
                view.alreadyAppointed ? "已约仓" : view.batchUsedByAsn ? "已建ASN" : "可约仓"
        );
        view.skuCount = row.skuCount;
        view.purchaseOrderCount = row.purchaseOrderCount;
        view.updatedAt = row.updatedAt;
        return view;
    }

    private List<RoutingWarehouseView> parseRoutingWarehouses(String routingResponseJson) {
        if (!StringUtils.hasText(routingResponseJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(routingResponseJson);
            JsonNode data = root.path("data");
            if (!data.isArray() && root.isArray()) {
                data = root;
            }
            if (!data.isArray()) {
                return List.of();
            }
            List<RoutingWarehouseView> result = new ArrayList<>();
            for (JsonNode item : data) {
                RoutingWarehouseView view = new RoutingWarehouseView();
                view.partnerCode = text(item, "partner_code");
                view.code = text(item, "code");
                view.lat = longValue(item, "lat");
                view.lng = longValue(item, "lng");
                result.add(view);
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private JsonNode firstWarehouse(JsonNode routingResponse) {
        JsonNode data = routingResponse == null ? null : routingResponse.path("data");
        if (data != null && data.isArray() && data.size() > 0) {
            return data.get(0);
        }
        return objectMapper.createObjectNode();
    }

    private StoreSiteRecord requireStoreSite(Long ownerUserId, String storeCode, String siteCode) {
        StoreSiteRecord site = mapper.selectStoreSite(ownerUserId, storeCode, siteCode);
        if (site == null) {
            throw new IllegalArgumentException("店铺站点不存在或未同步：" + storeCode + " / " + siteCode);
        }
        return site;
    }

    private Long requireOwnerUserId(BusinessAccessContext access, String storeCode) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        Long ownerUserId = StringUtils.hasText(storeCode) ? access.resolveOwnerUserIdForStore(storeCode) : null;
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("无法识别当前业务老板账号。");
        }
        return ownerUserId;
    }

    private BigDecimal calculateCubicFeet(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        if (!positive(lengthCm) || !positive(widthCm) || !positive(heightCm)) {
            return null;
        }
        return lengthCm.multiply(widthCm)
                .multiply(heightCm)
                .divide(CUBIC_FEET_DIVISOR, 5, RoundingMode.HALF_UP);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String keywordLike(String keyword) {
        String normalized = trimToNull(keyword);
        return normalized == null ? null : "%" + normalized + "%";
    }

    private List<String> normalizeAsnNumbers(Collection<String> asnNumbers) {
        if (asnNumbers == null || asnNumbers.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String asnNumber : asnNumbers) {
            String value = trimToNull(asnNumber);
            if (value != null) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<Long> normalizeShippingBatchIds(Collection<String> shippingBatchIds) {
        if (shippingBatchIds == null || shippingBatchIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (String shippingBatchId : shippingBatchIds) {
            String text = trimToNull(shippingBatchId);
            if (text == null) {
                continue;
            }
            Long value = parseLongOrNull(text);
            if (value == null || value <= 0) {
                throw new IllegalArgumentException("物流批次 ID 不合法：" + text);
            }
            normalized.add(value);
        }
        return new ArrayList<>(normalized);
    }

    private List<String> normalizePartnerSkus(Collection<String> partnerSkus) {
        if (partnerSkus == null || partnerSkus.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : partnerSkus) {
            String text = trimToNull(raw);
            if (text == null) {
                continue;
            }
            for (String token : text.split("[\\s,，]+")) {
                String value = trimToNull(token);
                if (value != null) {
                    normalized.add(value.toUpperCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeSite(String value) {
        String normalized = requireText(value, "请选择站点。");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private Long parseLongId(String value, String message) {
        try {
            return Long.parseLong(requireText(value, message));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private LocalDate parseLocalDate(String value, String message) {
        try {
            return LocalDate.parse(requireText(value, message));
        } catch (Exception exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String resolveFailureStage() {
        return "NOON_CALL";
    }

    private static String shrinkMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (!StringUtils.hasText(message)) {
            message = throwable == null ? "未知错误" : throwable.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() > 900 ? normalized.substring(0, 900) : normalized;
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return trimToNull(value.asText(null));
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static Long longValue(JsonNode node, String fieldName) {
        String text = text(node, fieldName);
        return parseLongOrNull(text);
    }

    private static Long parseLongOrNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer intValue(JsonNode node, String fieldName) {
        Long value = longValue(node, fieldName);
        return value == null ? null : value.intValue();
    }

    private static Boolean booleanValue(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asInt() != 0;
        }
        String text = trimToNull(value.asText(null));
        if (text == null) {
            return null;
        }
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private static String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    static String resolveAppointmentWarehouseToPartnerCode(String selectedWarehousePartnerCode, String requestedWarehouseToPartnerCode) {
        return firstNonBlank(requestedWarehouseToPartnerCode, selectedWarehousePartnerCode);
    }

    static String resolveAppointmentWarehouseToCode(String selectedWarehouseCode, String requestedWarehouseToCode) {
        return firstNonBlank(requestedWarehouseToCode, selectedWarehouseCode);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
