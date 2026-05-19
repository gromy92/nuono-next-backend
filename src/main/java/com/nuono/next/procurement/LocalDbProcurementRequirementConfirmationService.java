package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationDetailView.DemandDetailView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationDetailView.FinalCandidateView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationDetailView.OperationLogView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationDetailView.PoolItemView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationDetailView.PoolView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationDetailView.SummaryView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationListView.CandidateCollectionTaskView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationListView.CandidateSummaryView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationListView.DemandListItemView;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.CandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandDetailRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandListRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.FinalCandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.OperationLogRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolRow;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProcurementRequirementConfirmationService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int BACKUP_SOURCE_LIMIT = 10;
    private static final int OPERATION_LOG_LIMIT = 50;
    private static final Pattern OFFER_ID_PATH_PATTERN = Pattern.compile("/offer/(\\d+)\\.html");
    private static final Pattern OFFER_ID_QUERY_PATTERN = Pattern.compile("(?:offerId|offer_id|id)=(\\d+)");
    private static final List<String> REQUIRED_FEATURE_TABLES = List.of(
            "procurement_candidate_pool",
            "procurement_candidate_pool_item",
            "procurement_candidate_pool_snapshot",
            "procurement_final_candidate",
            "procurement_pool_operation_log"
    );

    private final ProcurementRequirementConfirmationMapper procurementRequirementConfirmationMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;

    public LocalDbProcurementRequirementConfirmationService(
            ProcurementRequirementConfirmationMapper procurementRequirementConfirmationMapper,
            LocalDbBootstrapStatusService localDbBootstrapStatusService
    ) {
        this.procurementRequirementConfirmationMapper = procurementRequirementConfirmationMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
    }

    @Transactional(readOnly = true)
    public ProcurementRequirementConfirmationListView listDemands(
            Long ownerUserId,
            String status,
            String keyword,
            Integer page,
            Integer pageSize
    ) {
        ProcurementRequirementConfirmationListView view = new ProcurementRequirementConfirmationListView();
        view.setMode("local-db");
        view.setPage(normalizePage(page));
        view.setPageSize(normalizePageSize(pageSize));

        List<String> missingTables = findMissingTables();
        if (!missingTables.isEmpty()) {
            view.setReady(false);
            view.setMissingFeatureTables(missingTables);
            view.setTotal(0);
            view.setMessage("采购需求确认表结构尚未初始化，请先执行 012_procurement_candidate_pool_v1.sql。");
            return view;
        }

        int offset = (view.getPage() - 1) * view.getPageSize();
        String normalizedStatus = normalize(status);
        String normalizedKeyword = normalize(keyword);
        Integer total = procurementRequirementConfirmationMapper.countDemandRows(
                ownerUserId,
                normalizedStatus,
                normalizedKeyword
        );
        List<DemandListRow> rows = procurementRequirementConfirmationMapper.listDemandRows(
                ownerUserId,
                normalizedStatus,
                normalizedKeyword,
                offset,
                view.getPageSize()
        );

        List<DemandListItemView> items = new ArrayList<>();
        for (DemandListRow row : rows) {
            DemandListItemView item = toListItem(row);
            item.setPreviewCandidate(resolvePreviewCandidate(row.getDemandItemId(), row.getPoolId()));
            items.add(item);
        }

        view.setReady(true);
        view.setTotal(total == null ? 0 : total);
        view.setItems(items);
        view.setMessage(items.isEmpty() ? "当前没有符合条件的采购需求。" : "采购需求确认列表已读取。");
        return view;
    }

    @Transactional(readOnly = true)
    public ProcurementRequirementConfirmationDetailView getDemandDetail(Long demandItemId, Long ownerUserId) {
        if (demandItemId == null) {
            throw new IllegalArgumentException("缺少采购需求 ID。");
        }

        ProcurementRequirementConfirmationDetailView view = new ProcurementRequirementConfirmationDetailView();
        view.setMode("local-db");

        List<String> missingTables = findMissingTables();
        if (!missingTables.isEmpty()) {
            view.setReady(false);
            view.setMissingFeatureTables(missingTables);
            view.setMessage("采购需求确认表结构尚未初始化，请先执行 012_procurement_candidate_pool_v1.sql。");
            return view;
        }

        DemandDetailRow demandRow = procurementRequirementConfirmationMapper.selectDemandDetail(demandItemId, ownerUserId);
        if (demandRow == null) {
            throw new IllegalArgumentException("采购需求不存在或当前账号无权查看。");
        }

        PoolRow poolRow = procurementRequirementConfirmationMapper.selectCurrentPool(demandItemId);
        PoolView pool = toPoolView(poolRow);
        List<PoolItemView> poolItems = new ArrayList<>();
        Set<Long> currentCandidateIds = new LinkedHashSet<>();
        if (poolRow != null && poolRow.getPoolId() != null) {
            for (PoolItemRow itemRow : procurementRequirementConfirmationMapper.listCurrentPoolItems(poolRow.getPoolId())) {
                PoolItemView poolItem = toPoolItemView(itemRow);
                poolItems.add(poolItem);
                if (poolItem.getCandidateId() != null) {
                    currentCandidateIds.add(poolItem.getCandidateId());
                }
            }
        }
        pool.setItems(poolItems);
        pool.setPoolCount(poolItems.size());

        List<CandidateSummaryView> backupCandidates = procurementRequirementConfirmationMapper
                .listTopCandidates(demandItemId, BACKUP_SOURCE_LIMIT)
                .stream()
                .filter(candidate -> !currentCandidateIds.contains(candidate.getCandidateId()))
                .map(this::toCandidateSummary)
                .collect(Collectors.toList());

        List<FinalCandidateView> finalCandidates = procurementRequirementConfirmationMapper
                .listFinalCandidates(demandItemId)
                .stream()
                .map(this::toFinalCandidateView)
                .collect(Collectors.toList());

        List<OperationLogView> operationLogs = procurementRequirementConfirmationMapper
                .listOperationLogs(demandItemId, OPERATION_LOG_LIMIT)
                .stream()
                .map(this::toOperationLogView)
                .collect(Collectors.toList());

        view.setDemand(toDemandDetailView(demandRow));
        view.setPool(pool);
        view.setBackupCandidates(backupCandidates);
        view.setFinalCandidates(finalCandidates);
        view.setSummary(toSummaryView(poolRow));
        view.setOperationLogs(operationLogs);
        view.setReady(true);
        view.setMessage("采购需求确认详情已读取。");
        return view;
    }

    private List<String> findMissingTables() {
        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();
        if (!inspection.isReady()) {
            return inspection.getMissingTables();
        }

        List<String> existingTables = procurementRequirementConfirmationMapper.listExistingFeatureTables(REQUIRED_FEATURE_TABLES);
        return REQUIRED_FEATURE_TABLES.stream()
                .filter(tableName -> !existingTables.contains(tableName))
                .collect(Collectors.toList());
    }

    private CandidateSummaryView resolvePreviewCandidate(Long demandItemId, Long poolId) {
        if (poolId != null) {
            List<PoolItemRow> poolItems = procurementRequirementConfirmationMapper.listCurrentPoolItems(poolId);
            if (!poolItems.isEmpty()) {
                return toCandidateSummary(poolItems.get(0));
            }
        }

        List<CandidateRow> candidates = procurementRequirementConfirmationMapper.listTopCandidates(demandItemId, 1);
        if (candidates.isEmpty()) {
            return null;
        }
        return toCandidateSummary(candidates.get(0));
    }

    private DemandListItemView toListItem(DemandListRow row) {
        DemandListItemView item = new DemandListItemView();
        item.setDemandItemId(row.getDemandItemId());
        item.setOrderId(row.getOrderId());
        item.setOwnerUserId(row.getOwnerUserId());
        item.setOrderNo(row.getOrderNo());
        item.setOrderTitle(row.getOrderTitle());
        item.setDemandTitle(row.getDemandTitle());
        item.setDemandStatus(row.getDemandStatus());
        item.setSourcePlatform(row.getSourcePlatform());
        item.setSourceUrl(row.getSourceUrl());
        item.setSourceTitle(row.getSourceTitle());
        item.setSourceImageUrl(row.getSourceImageUrl());
        item.setSourceDetailImageUrl(row.getSourceDetailImageUrl());
        item.setSourcePackageImageUrl(row.getSourcePackageImageUrl());
        item.setTargetPriceMin(row.getTargetPriceMin());
        item.setTargetPriceMax(row.getTargetPriceMax());
        item.setTargetQuantity(row.getTargetQuantity());
        item.setTargetSite(row.getTargetSite());
        item.setSpecialRequirement(row.getSpecialRequirement());
        item.setTargetMaterial(row.getTargetMaterial());
        item.setTargetPowerMode(row.getTargetPowerMode());
        item.setTargetSizeText(row.getTargetSizeText());
        item.setTargetPackageType(row.getTargetPackageType());
        item.setDeliveryExpectation(row.getDeliveryExpectation());
        item.setAssignedBuyerId(row.getAssignedBuyerId());
        item.setAssignedBuyerName(row.getAssignedBuyerName());
        item.setPoolId(row.getPoolId());
        item.setPoolNo(row.getPoolNo());
        item.setPoolStatus(row.getPoolStatus());
        item.setPoolCount(defaultInt(row.getPoolCount()));
        item.setMaxPoolSize(defaultInt(row.getMaxPoolSize(), 5));
        item.setFinalCandidateCount(defaultInt(row.getFinalCandidateCount()));
        item.setCandidateCount(defaultInt(row.getCandidateCount()));
        item.setCandidateCollectionTask(toCandidateCollectionTaskView(row));
        item.setUpdatedAt(row.getUpdatedAt());
        return item;
    }

    private CandidateCollectionTaskView toCandidateCollectionTaskView(DemandListRow row) {
        if (row.getMatchTaskId() == null) {
            return null;
        }
        CandidateCollectionTaskView task = new CandidateCollectionTaskView();
        task.setId(row.getMatchTaskId());
        task.setStatus(row.getMatchTaskStatus());
        task.setProgressPercent(row.getMatchTaskProgressPercent());
        task.setSearchMode(row.getMatchTaskSearchMode());
        task.setSelectedImageCount(row.getMatchTaskSelectedImageCount());
        task.setSearchPath(row.getMatchTaskSearchPath());
        task.setResultCount(row.getMatchTaskResultCount());
        task.setRecommendedCount(row.getMatchTaskRecommendedCount());
        task.setMessage(row.getMatchTaskMessage());
        task.setStartedAt(row.getMatchTaskStartedAt());
        task.setFinishedAt(row.getMatchTaskFinishedAt());
        return task;
    }

    private DemandDetailView toDemandDetailView(DemandDetailRow row) {
        DemandDetailView demand = new DemandDetailView();
        demand.setDemandItemId(row.getDemandItemId());
        demand.setOrderId(row.getOrderId());
        demand.setOwnerUserId(row.getOwnerUserId());
        demand.setOrderNo(row.getOrderNo());
        demand.setOrderTitle(row.getOrderTitle());
        demand.setLineNo(row.getLineNo());
        demand.setSourcePlatform(row.getSourcePlatform());
        demand.setSourceUrl(row.getSourceUrl());
        demand.setSourceTitle(row.getSourceTitle());
        demand.setSourceImageUrl(row.getSourceImageUrl());
        demand.setSourceDetailImageUrl(row.getSourceDetailImageUrl());
        demand.setSourcePackageImageUrl(row.getSourcePackageImageUrl());
        demand.setTargetPriceMin(row.getTargetPriceMin());
        demand.setTargetPriceMax(row.getTargetPriceMax());
        demand.setTargetQuantity(row.getTargetQuantity());
        demand.setTargetSite(row.getTargetSite());
        demand.setSpecialRequirement(row.getSpecialRequirement());
        demand.setTargetMaterial(row.getTargetMaterial());
        demand.setTargetPowerMode(row.getTargetPowerMode());
        demand.setTargetSizeText(row.getTargetSizeText());
        demand.setTargetPackageType(row.getTargetPackageType());
        demand.setDeliveryExpectation(row.getDeliveryExpectation());
        demand.setStatus(row.getDemandStatus());
        demand.setAssignedBuyerId(row.getAssignedBuyerId());
        demand.setAssignedBuyerName(row.getAssignedBuyerName());
        demand.setCurrentPoolId(row.getCurrentPoolId());
        demand.setCreatedAt(row.getCreatedAt());
        demand.setUpdatedAt(row.getUpdatedAt());
        return demand;
    }

    private PoolView toPoolView(PoolRow row) {
        PoolView pool = new PoolView();
        if (row == null) {
            pool.setStatus("POOL_NOT_CREATED");
            pool.setPoolCount(0);
            pool.setMaxPoolSize(5);
            pool.setCandidateSourceLimit(BACKUP_SOURCE_LIMIT);
            return pool;
        }
        pool.setPoolId(row.getPoolId());
        pool.setPoolNo(row.getPoolNo());
        pool.setStatus(row.getStatus());
        pool.setPoolCount(defaultInt(row.getPoolCount()));
        pool.setMaxPoolSize(defaultInt(row.getMaxPoolSize(), 5));
        pool.setCandidateSourceLimit(defaultInt(row.getCandidateSourceLimit(), BACKUP_SOURCE_LIMIT));
        pool.setCurrentSnapshotId(row.getCurrentSnapshotId());
        pool.setAutoCreatedAt(row.getAutoCreatedAt());
        pool.setInquiryStartedAt(row.getInquiryStartedAt());
        pool.setInquiryFinishedAt(row.getInquiryFinishedAt());
        pool.setFinalConfirmedAt(row.getFinalConfirmedAt());
        pool.setSummaryReadyAt(row.getSummaryReadyAt());
        pool.setSummaryText(row.getSummaryText());
        pool.setSummaryInputSnapshotId(row.getSummaryInputSnapshotId());
        return pool;
    }

    private PoolItemView toPoolItemView(PoolItemRow row) {
        PoolItemView item = new PoolItemView();
        item.setPoolItemId(row.getPoolItemId());
        item.setCandidateId(row.getCandidateId());
        item.setSourceRankNo(row.getSourceRankNo());
        item.setPoolRankNo(row.getPoolRankNo());
        item.setStatus(row.getStatus());
        item.setJoinSource(row.getJoinSource());
        item.setInquiryTaskId(row.getInquiryTaskId());
        item.setJoinedAt(row.getJoinedAt());
        item.setFirstSentAt(row.getFirstSentAt());
        item.setNoReplyDeadlineAt(row.getNoReplyDeadlineAt());
        item.setLastFollowUpAt(row.getLastFollowUpAt());
        item.setLastReplyAt(row.getLastReplyAt());
        item.setClosedAt(row.getClosedAt());
        item.setRemovedAt(row.getRemovedAt());
        item.setRemovedBy(row.getRemovedBy());
        item.setRemoveReason(row.getRemoveReason());
        item.setQuotePriceText(row.getQuotePriceText());
        item.setQuoteMoqText(row.getQuoteMoqText());
        item.setQuoteDeliveryText(row.getQuoteDeliveryText());
        item.setReplySummary(row.getReplySummary());
        item.setRiskNote(row.getRiskNote());
        item.setInquiryTaskStatus(row.getInquiryTaskStatus());
        item.setInquiryExecutionStage(row.getInquiryExecutionStage());
        item.setPlannedChannel(row.getPlannedChannel());
        item.setActiveChannel(row.getActiveChannel());
        item.setChannelFallbackReason(row.getChannelFallbackReason());
        item.setExternalInquiryId(row.getExternalInquiryId());
        item.setExternalInquiryUrl(row.getExternalInquiryUrl());
        item.setExternalResultStatus(row.getExternalResultStatus());
        item.setReplySource(row.getReplySource());
        item.setReplyParseStatus(row.getReplyParseStatus());
        item.setReplyParseError(row.getReplyParseError());
        item.setCandidate(toCandidateSummary(row));
        return item;
    }

    private FinalCandidateView toFinalCandidateView(FinalCandidateRow row) {
        FinalCandidateView item = new FinalCandidateView();
        item.setId(row.getId());
        item.setPoolItemId(row.getPoolItemId());
        item.setCandidateId(row.getCandidateId());
        item.setFinalPickType(row.getFinalPickType());
        item.setSnapshotId(row.getSnapshotId());
        item.setDecisionNote(row.getDecisionNote());
        item.setConfirmedBy(row.getConfirmedBy());
        item.setConfirmedAt(row.getConfirmedAt());
        item.setCandidate(toCandidateSummary(row));
        return item;
    }

    private SummaryView toSummaryView(PoolRow row) {
        SummaryView summary = new SummaryView();
        if (row != null) {
            summary.setSummaryText(row.getSummaryText());
            summary.setSnapshotId(row.getSummaryInputSnapshotId());
        }
        return summary;
    }

    private OperationLogView toOperationLogView(OperationLogRow row) {
        OperationLogView item = new OperationLogView();
        item.setId(row.getId());
        item.setPoolId(row.getPoolId());
        item.setPoolItemId(row.getPoolItemId());
        item.setCandidateId(row.getCandidateId());
        item.setOfferId(row.getOfferId());
        item.setOperationType(row.getOperationType());
        item.setOperatorUserId(row.getOperatorUserId());
        item.setOperatorRole(row.getOperatorRole());
        item.setBeforeStatus(row.getBeforeStatus());
        item.setAfterStatus(row.getAfterStatus());
        item.setSnapshotId(row.getSnapshotId());
        item.setOperationReason(row.getOperationReason());
        item.setDetailJson(row.getDetailJson());
        item.setCreatedAt(row.getCreatedAt());
        return item;
    }

    private CandidateSummaryView toCandidateSummary(CandidateRow row) {
        CandidateSummaryView item = new CandidateSummaryView();
        item.setCandidateId(row.getCandidateId());
        item.setRankNo(row.getRankNo());
        item.setTotalScore(row.getTotalScore());
        item.setFitScore(row.getFitScore());
        item.setSpecScore(row.getSpecScore());
        item.setPriceScore(row.getPriceScore());
        item.setSupplierScore(row.getSupplierScore());
        item.setLogisticsScore(row.getLogisticsScore());
        item.setOfferId(extractOfferId(row.getCandidateUrl()));
        item.setTitle(row.getTitle());
        item.setSupplierName(row.getSupplierName());
        item.setCandidateUrl(row.getCandidateUrl());
        item.setMainImageUrl(row.getMainImageUrl());
        item.setDetailImageUrl(row.getDetailImageUrl());
        item.setDeliveryImageUrl(row.getDeliveryImageUrl());
        item.setPriceText(row.getPriceText());
        item.setMoqText(row.getMoqText());
        item.setLocationText(row.getLocationText());
        item.setMaterialText(row.getMaterialText());
        item.setPowerModeText(row.getPowerModeText());
        item.setSizeText(row.getSizeText());
        item.setPackageText(row.getPackageText());
        item.setDeliveryTimelineText(row.getDeliveryTimelineText());
        item.setResultCardText(row.getResultCardText());
        item.setDetailHighlightText(row.getDetailHighlightText());
        item.setAttributeSnapshotText(row.getAttributeSnapshotText());
        item.setShippingSnapshotText(row.getShippingSnapshotText());
        item.setPackageSnapshotText(row.getPackageSnapshotText());
        item.setBadgesText(row.getBadgesText());
        item.setReasonsText(row.getReasonsText());
        item.setWarningsText(row.getWarningsText());
        return item;
    }

    private String extractOfferId(String candidateUrl) {
        if (!StringUtils.hasText(candidateUrl)) {
            return null;
        }
        Matcher pathMatcher = OFFER_ID_PATH_PATTERN.matcher(candidateUrl);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        Matcher queryMatcher = OFFER_ID_QUERY_PATTERN.matcher(candidateUrl);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        return null;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Integer defaultInt(Integer value) {
        return defaultInt(value, 0);
    }

    private Integer defaultInt(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }
}
