package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonlog.NoonHttpCallLogView;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CorrectAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnLineCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.UpsertAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseAsnListSyncSupport.NoonAsnListRow;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineRecord;
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
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnShippingBatchLinkView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AppointmentAvailabilityView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AppointmentView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ProductCandidateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.RoutingWarehouseView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ShippingBatchCandidateView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.productselection.NoonImageUrlNormalizer;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import com.nuono.next.sales.NoonSalesReportRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbOfficialWarehouseService implements OfficialWarehouseAsnNumberSyncer {

    private static final BigDecimal CUBIC_FEET_DIVISOR = new BigDecimal("28316.846592");
    private static final int DEFAULT_APPOINTMENT_RETRY_MINUTES = 3;
    private static final int DEFAULT_SEAL_CHECK_ATTEMPTS = 8;
    private static final long DEFAULT_SEAL_CHECK_INTERVAL_MS = 1500L;
    private static final int DEFAULT_ASN_LIST_SYNC_PER_PAGE = 50;
    private static final int DEFAULT_ASN_LIST_SYNC_MAX_PAGES = 50;

    private final OfficialWarehouseMapper mapper;
    private final NoonSessionGateway noonSessionGateway;
    private final NoonSalesReportBindingResolver bindingResolver;
    private final NoonHttpCallLogService noonHttpCallLogService;
    private final OfficialWarehouseNoonInboundClient noonInboundClient;
    private final ObjectMapper objectMapper;
    private final OfficialWarehouseAppointmentRunner appointmentRunner;

    @Value("${nuono.official-warehouse.appointment.scheduler.enabled:false}")
    private boolean appointmentSchedulerEnabled;

    @Value("${nuono.official-warehouse.appointment.scheduler.max-items-per-tick:20}")
    private int appointmentSchedulerMaxItems;

    @Value("${nuono.official-warehouse.appointment.scheduler.retry-minutes:3}")
    private int appointmentRetryMinutes;

    @Value("${nuono.official-warehouse.appointment.scheduler.system-operator-user-id:0}")
    private long appointmentSystemOperatorUserId;

    public LocalDbOfficialWarehouseService(
            OfficialWarehouseMapper mapper,
            NoonSessionGateway noonSessionGateway,
            NoonSalesReportBindingResolver bindingResolver,
            NoonHttpCallLogService noonHttpCallLogService,
            OfficialWarehouseNoonInboundClient noonInboundClient,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.noonSessionGateway = noonSessionGateway;
        this.bindingResolver = bindingResolver;
        this.noonHttpCallLogService = noonHttpCallLogService;
        this.noonInboundClient = noonInboundClient;
        this.objectMapper = objectMapper;
        this.appointmentRunner = new OfficialWarehouseAppointmentRunner(Clock.systemDefaultZone());
    }

    public List<AsnView> listAsns(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword
    ) {
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        Collection<String> storeCodes = trimToNull(storeCode) == null ? access.getStoreCodes() : List.of(storeCode);
        return mapper.listAsns(
                        ownerUserId,
                        storeCodes,
                        trimToNull(storeCode),
                        normalizeSite(siteCode),
                        keywordLike(keyword),
                        200
                )
                .stream()
                .map(record -> toAsnView(record, true))
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
        NoonSession session = noonSessionGateway.login(
                ownerUserId,
                binding.getNoonUser(),
                binding.getNoonPassword(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );

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
                    syncNoonAsnListRow(result, ownerUserId, site, binding, remoteRow, access.getSessionUserId());
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
        NoonSession session = noonSessionGateway.login(
                ownerUserId,
                binding.getNoonUser(),
                binding.getNoonPassword(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );

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
                    syncNoonAsnListRow(result, ownerUserId, site, binding, remoteRow, access.getSessionUserId());
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
        view.noonUser = resolveNoonUser(record);
        return view;
    }

    public List<ProductCandidateView> listProductCandidates(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword
    ) {
        return listProductCandidates(access, storeCode, siteCode, keyword, List.of());
    }

    public List<ProductCandidateView> listProductCandidates(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword,
            Collection<String> shippingBatchIds
    ) {
        String normalizedStoreCode = requireText(storeCode, "请选择店铺。");
        String normalizedSiteCode = normalizeSite(requireText(siteCode, "请选择站点。"));
        Long ownerUserId = requireOwnerUserId(access, normalizedStoreCode);
        StoreSiteRecord site = requireStoreSite(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        List<Long> selectedBatchIds = normalizeShippingBatchIds(shippingBatchIds);
        if (!selectedBatchIds.isEmpty()) {
            return listProductCandidatesFromShippingBatches(ownerUserId, site, keyword, selectedBatchIds);
        }
        return mapper.listProductCandidates(
                        ownerUserId,
                        site.storeCode,
                        site.siteCode,
                        keywordLike(keyword),
                        List.of(),
                        200
                )
                .stream()
                .map(this::toProductCandidateView)
                .collect(Collectors.toList());
    }

    private List<ProductCandidateView> listProductCandidatesFromShippingBatches(
            Long ownerUserId,
            StoreSiteRecord site,
            String keyword,
            List<Long> selectedBatchIds
    ) {
        List<ShippingBatchSourceAllocationRecord> allocations = mapper.listShippingBatchSourceAllocations(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                selectedBatchIds,
                List.of()
        );
        if (allocations.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> quantityByVariantId = new LinkedHashMap<>();
        for (ShippingBatchSourceAllocationRecord allocation : allocations) {
            if (allocation.productVariantId == null) {
                continue;
            }
            int quantity = Math.max(0, allocation.quantity == null ? 0 : allocation.quantity);
            if (quantity <= 0) {
                continue;
            }
            quantityByVariantId.merge(allocation.productVariantId, quantity, Integer::sum);
        }
        if (quantityByVariantId.isEmpty()) {
            return List.of();
        }
        return mapper.listProductCandidates(
                        ownerUserId,
                        site.storeCode,
                        site.siteCode,
                        keywordLike(keyword),
                        quantityByVariantId.keySet(),
                        Math.max(quantityByVariantId.size(), 1)
                )
                .stream()
                .map(row -> {
                    ProductCandidateView view = toProductCandidateView(row);
                    view.batchAvailableQuantity = quantityByVariantId.getOrDefault(row.productVariantId, 0);
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

        Map<Long, Integer> quantityByVariantId = new LinkedHashMap<>();
        for (CreateAsnLineCommand lineCommand : lineCommands) {
            if (lineCommand == null || lineCommand.productVariantId == null) {
                throw new IllegalArgumentException("商品行缺少商品变体 ID。");
            }
            int quantity = lineCommand.quantity == null ? 0 : lineCommand.quantity;
            if (quantity <= 0) {
                throw new IllegalArgumentException("商品数量必须大于 0。");
            }
            quantityByVariantId.merge(lineCommand.productVariantId, quantity, Integer::sum);
        }

        List<ProductCandidateRecord> candidateRows = mapper.listProductCandidates(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                null,
                quantityByVariantId.keySet(),
                Math.max(quantityByVariantId.size(), 1)
        );
        Map<Long, ProductCandidateRecord> candidatesByVariantId = candidateRows.stream()
                .collect(Collectors.toMap(row -> row.productVariantId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Set<Long> missingVariantIds = new LinkedHashSet<>(quantityByVariantId.keySet());
        missingVariantIds.removeAll(candidatesByVariantId.keySet());
        if (!missingVariantIds.isEmpty()) {
            throw new IllegalArgumentException("部分商品缺少站点 PSKU 或不属于当前店铺：" + missingVariantIds);
        }

        List<AsnLineInsertRecord> lineRows = new ArrayList<>();
        int totalQuantity = 0;
        for (Map.Entry<Long, Integer> entry : quantityByVariantId.entrySet()) {
            ProductCandidateRecord candidate = candidatesByVariantId.get(entry.getKey());
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
            lineRow.imageUrlCache = NoonImageUrlNormalizer.normalize(candidate.imageUrlCache);
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
            NoonSession session = noonSessionGateway.login(
                    ownerUserId,
                    binding.getNoonUser(),
                    binding.getNoonPassword(),
                    binding.getPersistedCookie(),
                    binding.getProjectCode(),
                    binding.getStoreCode()
            );

            NoonCallContext asnCallContext = NoonCallContext.asn(asnId, localAsnNo);
            JsonNode createResponse = noonInboundClient.createAsn(session, binding, asnCallContext, totalQuantity);
            JsonNode createData = createResponse.path("data");
            String asnNr = requireText(text(createData, "asn_nr"), "Noon 创建 ASN 响应缺少 asn_nr。");
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
            return getAsn(access, String.valueOf(asnId));
        } catch (IllegalArgumentException exception) {
            mapper.markAsnFailed(asnId, "VALIDATION", "VALIDATION", exception.getMessage(), access.getSessionUserId());
            mapper.markPendingLinesFailed(asnId, exception.getMessage(), access.getSessionUserId());
            throw exception;
        } catch (Exception exception) {
            String message = shrinkMessage(exception);
            mapper.markAsnFailed(asnId, resolveFailureStage(), exception.getClass().getSimpleName(), message, access.getSessionUserId());
            mapper.markPendingLinesFailed(asnId, message, access.getSessionUserId());
            throw new IllegalStateException("Noon 官方仓 ASN 创建失败：" + message, exception);
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

        List<Long> variantIds = lineRows.stream()
                .map(row -> row.productVariantId)
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.toList());
        List<ShippingBatchSourceAllocationRecord> allocations = mapper.listShippingBatchSourceAllocations(
                ownerUserId,
                site.storeCode,
                site.siteCode,
                selectedBatchIds,
                variantIds
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

        Map<Long, List<ShippingBatchSourceAllocationRecord>> allocationsByVariantId = new LinkedHashMap<>();
        Map<Long, Integer> remainingBySourceId = new LinkedHashMap<>();
        for (ShippingBatchSourceAllocationRecord allocation : allocations) {
            Long sourceId = allocationSourceId(allocation);
            if (allocation.productVariantId == null || sourceId == null) {
                continue;
            }
            int quantity = Math.max(0, allocation.quantity == null ? 0 : allocation.quantity);
            if (quantity <= 0) {
                continue;
            }
            allocationsByVariantId
                    .computeIfAbsent(allocation.productVariantId, ignored -> new ArrayList<>())
                    .add(allocation);
            remainingBySourceId.put(sourceId, quantity);
        }

        List<AsnShippingBatchLinkInsertRecord> links = new ArrayList<>();
        for (AsnLineInsertRecord lineRow : lineRows) {
            int requiredQuantity = Math.max(0, lineRow.quantity == null ? 0 : lineRow.quantity);
            int availableQuantity = allocationsByVariantId
                    .getOrDefault(lineRow.productVariantId, List.of())
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
            for (ShippingBatchSourceAllocationRecord allocation : allocationsByVariantId.getOrDefault(lineRow.productVariantId, List.of())) {
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

    private Long allocationSourceId(ShippingBatchSourceAllocationRecord allocation) {
        if (allocation == null) {
            return null;
        }
        return allocation.inTransitGoodsLineId == null ? allocation.shippingBatchSourceId : allocation.inTransitGoodsLineId;
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
            NoonAsnListRow remoteRow,
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
        insert.warehouseFrom = firstNonBlank(remoteRow.warehouseFrom, remoteRow.warehouseFromCode, "NOON_SYNC");
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
        NoonSession session = noonSessionGateway.login(
                ownerUserId,
                binding.getNoonUser(),
                binding.getNoonPassword(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );
        return appointmentRunner.queryAvailability(
                        task,
                        noonInboundClient.appointmentClient(
                                session,
                                binding,
                                NoonCallContext.appointment(
                                        "OFFICIAL_WAREHOUSE_APPOINTMENT_AVAILABILITY",
                                        asn.id,
                                        asn.noonAsnNr
                                )
                        )
                )
                .stream()
                .map(this::toAppointmentAvailabilityView)
                .collect(Collectors.toList());
    }

    public List<String> listAppointmentWarehouseFromOptions(
            BusinessAccessContext access,
            String asnId
    ) {
        AsnView asn = getAsn(access, asnId);
        Long ownerUserId = requireOwnerUserId(access, asn.storeCode);
        AsnRecord asnRecord = mapper.selectAsn(ownerUserId, parseLongId(asn.id, "官方仓 ASN 不存在。"));
        NoonSalesReportBinding binding = resolveBinding(ownerUserId, asnRecord.logicalStoreId, asn.storeCode, asn.siteCode);
        NoonSession session = noonSessionGateway.login(
                ownerUserId,
                binding.getNoonUser(),
                binding.getNoonPassword(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );
        LinkedHashSet<String> warehouses = new LinkedHashSet<>(
                noonInboundClient.listPartnerWarehouses(
                        session,
                        binding,
                        NoonCallContext.asn(asnRecord.id, asnRecord.localAsnNo)
                )
        );
        if (asn.appointment != null && StringUtils.hasText(asn.appointment.warehouseFrom)) {
            warehouses.add(asn.appointment.warehouseFrom.trim());
        }
        return new ArrayList<>(warehouses);
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
        AppointmentInsertRecord row = new AppointmentInsertRecord();
        row.asnId = parsedAsnId;
        row.ownerUserId = ownerUserId;
        row.logicalStoreId = mapper.selectAsn(ownerUserId, parsedAsnId).logicalStoreId;
        row.storeCode = asn.storeCode;
        row.storeName = asn.storeName;
        row.siteCode = asn.siteCode;
        row.projectCode = asn.projectCode;
        row.partnerId = asn.partnerId;
        row.localAsnNo = asn.localAsnNo;
        row.noonAsnNr = requireText(asn.noonAsnNr, "ASN 缺少 Noon ASN 编号。");
        row.totalUnits = asn.totalQuantity == null ? 0 : asn.totalQuantity;
        row.warehouseFrom = requireText(command.warehouseFrom, "请填写出发仓库。");
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

        AppointmentRecord existing = mapper.selectLatestAppointmentByAsn(ownerUserId, parsedAsnId);
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
            initialDelayString = "${nuono.official-warehouse.appointment.scheduler.initial-delay-ms:30000}",
            fixedDelayString = "${nuono.official-warehouse.appointment.scheduler.fixed-delay-ms:60000}"
    )
    public void runAppointmentScheduler() {
        if (!appointmentSchedulerEnabled) {
            return;
        }
        List<AppointmentRecord> dueAppointments = mapper.listDueAppointments(Math.max(1, appointmentSchedulerMaxItems));
        for (AppointmentRecord appointment : dueAppointments) {
            try {
                runAppointmentRecord(appointment, schedulerOperatorUserId(appointment), true);
            } catch (Exception ignored) {
                // Individual appointment failures are persisted in runAppointmentRecord.
            }
        }
    }

    private AppointmentView runAppointmentRecord(AppointmentRecord appointment, Long operatorUserId, boolean allowRetry) {
        Long operatorId = operatorUserId == null ? appointment.ownerUserId : operatorUserId;
        AppointmentTask task = null;
        mapper.markAppointmentRunning(appointment.id, operatorId);
        try {
            task = toAppointmentTask(appointment);
            NoonSalesReportBinding binding = resolveBinding(appointment);
            NoonSession session = noonSessionGateway.login(
                    appointment.ownerUserId,
                    binding.getNoonUser(),
                    binding.getNoonPassword(),
                    binding.getPersistedCookie(),
                    binding.getProjectCode(),
                    binding.getStoreCode()
            );
            RunResult result = appointmentRunner.runOnce(
                    task,
                    noonInboundClient.appointmentClient(
                            session,
                            binding,
                            NoonCallContext.appointment(
                                    "OFFICIAL_WAREHOUSE_APPOINTMENT",
                                    String.valueOf(appointment.id),
                                    appointment.noonAsnNr
                            )
                    )
            );
            persistResolvedWarehouseFrom(appointment, task, operatorId);
            if ("SCHEDULED".equals(result.status)) {
                mapper.markAppointmentScheduled(
                        appointment.id,
                        result.appointmentDate,
                        result.slotId,
                        result.appointmentTime,
                        operatorId
                );
            } else if (allowRetry && shouldRetryAppointment(appointment, result.failureType)) {
                mapper.markAppointmentPendingRetry(
                        appointment.id,
                        safeRetryMinutes(),
                        "SCHEDULE",
                        result.failureType,
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
            persistResolvedWarehouseFrom(appointment, task, operatorId);
            String message = shrinkMessage(exception);
            if (allowRetry && shouldRetryAppointment(appointment, "NOON_CALL")) {
                mapper.markAppointmentPendingRetry(
                        appointment.id,
                        safeRetryMinutes(),
                        "NOON_CALL",
                        exception.getClass().getSimpleName(),
                        message,
                        operatorId
                );
            } else {
                mapper.markAppointmentFailed(
                        appointment.id,
                        "NOON_CALL",
                        exception.getClass().getSimpleName(),
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
        AppointmentTask task = null;
        mapper.markAppointmentRunning(appointment.id, operatorId);
        try {
            task = toAppointmentTask(appointment);
            NoonSalesReportBinding binding = resolveBinding(appointment);
            NoonSession session = noonSessionGateway.login(
                    appointment.ownerUserId,
                    binding.getNoonUser(),
                    binding.getNoonPassword(),
                    binding.getPersistedCookie(),
                    binding.getProjectCode(),
                    binding.getStoreCode()
            );
            RunResult result = appointmentRunner.scheduleSelectedSlot(
                    task,
                    noonInboundClient.appointmentClient(
                            session,
                            binding,
                            NoonCallContext.appointment(
                                    "OFFICIAL_WAREHOUSE_APPOINTMENT",
                                    String.valueOf(appointment.id),
                                    appointment.noonAsnNr
                            )
                    ),
                    appointmentDate,
                    new SlotCapacity(slotId, appointmentTime)
            );
            persistResolvedWarehouseFrom(appointment, task, operatorId);
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
            persistResolvedWarehouseFrom(appointment, task, operatorId);
            mapper.markAppointmentFailed(
                    appointment.id,
                    "NOON_CALL",
                    exception.getClass().getSimpleName(),
                    shrinkMessage(exception),
                    operatorId
            );
        }
        return toAppointmentView(requireAppointment(appointment.ownerUserId, appointment.id));
    }

    private void persistResolvedWarehouseFrom(AppointmentRecord appointment, AppointmentTask task, Long operatorUserId) {
        if (appointment == null || task == null || !StringUtils.hasText(task.warehouseFrom)) {
            return;
        }
        String resolved = task.warehouseFrom.trim();
        if (!resolved.equals(appointment.warehouseFrom)) {
            mapper.updateAppointmentWarehouseFrom(appointment.ownerUserId, appointment.id, resolved, operatorUserId);
            appointment.warehouseFrom = resolved;
        }
    }

    private boolean shouldRetryAppointment(AppointmentRecord appointment, String failureType) {
        if (failureType != null && failureType.startsWith("NOON_ASN_")) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return appointment.apEndDateValue == null || !today.isAfter(appointment.apEndDateValue);
    }

    private int safeRetryMinutes() {
        return appointmentRetryMinutes <= 0 ? DEFAULT_APPOINTMENT_RETRY_MINUTES : appointmentRetryMinutes;
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
        task.warehouseFrom = appointment.warehouseFrom;
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
        task.warehouseFrom = requireText(command.warehouseFrom, "请填写出发仓库。");
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
            String warehouseFrom = requireText(detail.warehouseFrom, "Noon ASN 详情缺少出发仓库，不能 sealed ASN。");
            noonInboundClient.setWarehouses(session, binding, context, asnNr, normalizedWarehouseTo, warehouseFrom);
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
        view.imageUrl = NoonImageUrlNormalizer.normalize(row.imageUrlCache);
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
        view.warehouseFrom = row.warehouseFrom;
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
        view.warehouseFrom = slot.warehouseFrom;
        view.warehouseFromCode = slot.warehouseFromCode;
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
        view.imageUrl = NoonImageUrlNormalizer.normalize(row.imageUrlCache);
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
            view.missingTags.add("缺官方尺寸");
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
