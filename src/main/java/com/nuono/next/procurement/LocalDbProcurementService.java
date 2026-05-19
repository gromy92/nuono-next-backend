package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.procurement.ProcurementAutoSelectionEngine.AutoSelectionResult;
import com.nuono.next.procurement.ProcurementAutoSelectionEngine.GeneratedCandidate;
import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.OrderView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.SummaryView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.TaskView;
import com.nuono.next.procurement.ProcurementManualCandidateBackfillCommand.ManualCandidateInput;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProcurementService {

    private static final Set<String> ALLOWED_NEXT_ACTIONS = Set.of(
            "CONTINUE_COMPARE",
            "PREPARE_INQUIRY",
            "INTENT",
            "HOLD"
    );

    private final ProcurementMapper procurementMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;
    private final ProcurementStructuredFieldParser procurementStructuredFieldParser;
    private final ProcurementDecisionSupportAdvisor procurementDecisionSupportAdvisor;
    private final ProcurementCandidateGroupingAdvisor procurementCandidateGroupingAdvisor;
    private final ProcurementInquiryPreparationAdvisor procurementInquiryPreparationAdvisor;
    private final ProcurementAutoSelectionEngine procurementAutoSelectionEngine;
    private final Procurement1688SearchPageExtractor procurement1688SearchPageExtractor;

    public LocalDbProcurementService(
            ProcurementMapper procurementMapper,
            LocalDbBootstrapStatusService localDbBootstrapStatusService,
            ProcurementStructuredFieldParser procurementStructuredFieldParser,
            ProcurementDecisionSupportAdvisor procurementDecisionSupportAdvisor,
            ProcurementCandidateGroupingAdvisor procurementCandidateGroupingAdvisor,
            ProcurementInquiryPreparationAdvisor procurementInquiryPreparationAdvisor,
            ProcurementAutoSelectionEngine procurementAutoSelectionEngine,
            Procurement1688SearchPageExtractor procurement1688SearchPageExtractor
    ) {
        this.procurementMapper = procurementMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
        this.procurementStructuredFieldParser = procurementStructuredFieldParser;
        this.procurementDecisionSupportAdvisor = procurementDecisionSupportAdvisor;
        this.procurementCandidateGroupingAdvisor = procurementCandidateGroupingAdvisor;
        this.procurementInquiryPreparationAdvisor = procurementInquiryPreparationAdvisor;
        this.procurementAutoSelectionEngine = procurementAutoSelectionEngine;
        this.procurement1688SearchPageExtractor = procurement1688SearchPageExtractor;
    }

    public ProcurementCandidatePoolView buildCandidatePool(Long ownerUserId, String orderNo) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取采购候选池。");
        }

        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();
        ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
        view.setMode("local-db");
        view.setReady(inspection.isReady());
        view.setMissingCoreTables(inspection.getMissingTables());

        if (!inspection.isReady()) {
            view.setMessage("本地库已启用，但第一批核心表还没有补齐，采购候选池暂时不能读取。");
            return view;
        }

        OrderView order = procurementMapper.selectLatestOrder(ownerUserId, normalize(orderNo));
        if (order == null) {
            view.setReady(true);
            view.setMessage("当前老板名下还没有采购需求单样本。");
            view.setSummary(new SummaryView());
            return view;
        }

        List<DemandItemView> demandItems = procurementMapper.listDemandItems(order.getId());
        List<TaskView> tasks = procurementMapper.listTasks(order.getId());
        List<CandidateView> candidates = procurementMapper.listCandidates(order.getId());

        Map<Long, TaskView> latestTaskByDemandItemId = tasks.stream()
                .collect(Collectors.toMap(
                        TaskView::getDemandItemId,
                        task -> task,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, List<CandidateView>> candidatesByDemandItemId = new LinkedHashMap<>();
        for (CandidateView candidate : candidates) {
            candidate.setBadges(splitPipeText(candidate.getBadgesText()));
            candidate.setReasons(splitPipeText(candidate.getReasonsText()));
            candidate.setWarnings(splitPipeText(candidate.getWarningsText()));
            procurementStructuredFieldParser.enrichCandidate(candidate);
            candidatesByDemandItemId.computeIfAbsent(candidate.getDemandItemId(), key -> new ArrayList<>()).add(candidate);
        }

        for (DemandItemView demandItem : demandItems) {
            procurementStructuredFieldParser.enrichDemandItem(demandItem);
            demandItem.setTask(latestTaskByDemandItemId.get(demandItem.getId()));
            List<CandidateView> demandCandidates = candidatesByDemandItemId.getOrDefault(demandItem.getId(), new ArrayList<>());
            for (CandidateView candidate : demandCandidates) {
                procurementDecisionSupportAdvisor.enrichCandidateDecisionSupport(demandItem, candidate);
                procurementInquiryPreparationAdvisor.enrichCandidateInquiryPreparation(demandItem, candidate);
            }
            demandItem.setCandidates(demandCandidates);
            demandItem.setCandidateGroups(procurementCandidateGroupingAdvisor.buildGroups(demandItem, demandCandidates));
        }

        view.setOrder(order);
        view.setDemandItems(demandItems);
        view.setSelectedDemandItemId(resolveSelectedDemandItemId(demandItems));
        view.setSummary(buildSummary(demandItems));
        view.setReady(true);
        view.setMessage("采购候选池样本已准备好，可以继续验证异步筛选与意向采购决策。");
        return view;
    }

    @Transactional
    public ProcurementCandidatePoolView selectCandidate(ProcurementDecisionCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能提交采购决策。");
        }
        if (command.getDemandItemId() == null || command.getCandidateId() == null) {
            throw new IllegalArgumentException("请先选择要确认的候选商品。");
        }

        Integer ownedCount = procurementMapper.countOwnedCandidate(
                command.getOwnerUserId(),
                command.getDemandItemId(),
                command.getCandidateId()
        );
        if (ownedCount == null || ownedCount <= 0) {
            throw new IllegalArgumentException("当前候选商品不在老板名下的采购单里，不能直接设为意向采购。");
        }

        procurementMapper.clearSelectedCandidates(command.getDemandItemId(), command.getOwnerUserId());
        procurementMapper.selectCandidate(command.getDemandItemId(), command.getCandidateId(), command.getOwnerUserId());
        procurementMapper.markDemandItemDecided(command.getDemandItemId(), command.getCandidateId(), command.getOwnerUserId());

        ProcurementCandidatePoolView refreshedView = buildCandidatePool(command.getOwnerUserId(), command.getOrderNo());
        if (refreshedView.getOrder() != null && refreshedView.getOrder().getId() != null) {
            procurementMapper.syncOrderDecisionSummary(refreshedView.getOrder().getId(), command.getOwnerUserId());
            refreshedView = buildCandidatePool(command.getOwnerUserId(), command.getOrderNo());
        }
        refreshedView.setSelectedDemandItemId(command.getDemandItemId());
        refreshedView.setMessage("已把候选商品标记为意向采购，可继续处理下一条需求。");
        return refreshedView;
    }

    @Transactional
    public ProcurementCandidatePoolView saveCandidateReview(ProcurementCandidateReviewCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能保存人工判断。");
        }
        if (command.getDemandItemId() == null || command.getCandidateId() == null) {
            throw new IllegalArgumentException("请先选择要记录判断的候选商品。");
        }

        Integer ownedCount = procurementMapper.countOwnedCandidate(
                command.getOwnerUserId(),
                command.getDemandItemId(),
                command.getCandidateId()
        );
        if (ownedCount == null || ownedCount <= 0) {
            throw new IllegalArgumentException("当前候选商品不在老板名下的采购单里，不能直接保存判断。");
        }

        String nextAction = normalize(command.getNextAction());
        if (nextAction != null) {
            nextAction = nextAction.toUpperCase(Locale.ROOT);
            if (!ALLOWED_NEXT_ACTIONS.contains(nextAction)) {
                throw new IllegalArgumentException("当前下一步动作不在允许范围内，请刷新页面后重试。");
            }
        }

        int updatedRows = procurementMapper.updateCandidateReview(
                command.getDemandItemId(),
                command.getCandidateId(),
                normalize(command.getManualReviewNote()),
                normalize(command.getInquirySummary()),
                nextAction,
                command.getOwnerUserId()
        );
        if (updatedRows <= 0) {
            throw new IllegalStateException("人工判断保存失败，请刷新后重试。");
        }

        ProcurementCandidatePoolView refreshedView = buildCandidatePool(command.getOwnerUserId(), command.getOrderNo());
        refreshedView.setMessage("已保存当前候选的人工判断，可继续比对、询价或设为意向采购。");
        return refreshedView;
    }

    public ProcurementExtractionPreviewView previewExtraction(ProcurementExtractionPreviewCommand command) {
        if (command == null || !hasAnyExtractionInput(command)) {
            throw new IllegalArgumentException("请至少提供一段 1688 结果原文，再执行字段抽取预览。");
        }

        CandidateView candidate = new CandidateView();
        candidate.setTitle(normalize(command.getTitle()));
        candidate.setSupplierName(normalize(command.getSupplierName()));
        candidate.setPriceText(normalize(command.getPriceText()));
        candidate.setMoqText(normalize(command.getMoqText()));
        candidate.setLocationText(normalize(command.getLocationText()));
        candidate.setResultCardText(normalize(command.getResultCardText()));
        candidate.setDetailHighlightText(normalize(command.getDetailHighlightText()));
        candidate.setAttributeSnapshotText(normalize(command.getAttributeSnapshotText()));
        candidate.setShippingSnapshotText(normalize(command.getShippingSnapshotText()));
        candidate.setPackageSnapshotText(normalize(command.getPackageSnapshotText()));
        candidate.setBadgesText(normalize(command.getBadgesText()));
        candidate.setReasonsText(normalize(command.getReasonsText()));
        candidate.setWarningsText(normalize(command.getWarningsText()));

        procurementStructuredFieldParser.enrichCandidate(candidate);

        ProcurementExtractionPreviewView view = new ProcurementExtractionPreviewView();
        view.setReady(true);
        view.setTitle(candidate.getTitle());
        view.setSupplierName(candidate.getSupplierName());
        view.setMaterialText(candidate.getMaterialText());
        view.setPowerModeText(candidate.getPowerModeText());
        view.setSizeText(candidate.getSizeText());
        view.setPackageText(candidate.getPackageText());
        view.setDeliveryTimelineText(candidate.getDeliveryTimelineText());
        view.setStructuredFieldSource(candidate.getStructuredFieldSource());
        view.setExtractionEvidences(candidate.getExtractionEvidences());

        if (candidate.getExtractionEvidences().isEmpty()) {
            view.setMessage("已完成抽取预览，但当前原文里还没有识别到有效的结构化采购字段。");
        } else {
            view.setMessage("已完成 1688 结果字段抽取预览，可以继续把这批原文接入真实候选入库链路。");
        }
        return view;
    }

    public ProcurementSearchPagePreviewView previewSearchPageExtraction(ProcurementSearchPagePreviewCommand command) {
        if (command == null || !StringUtils.hasText(command.getHtml())) {
            throw new IllegalArgumentException("请先粘贴 1688 搜索结果页 HTML，再执行页面抽取。");
        }
        return procurement1688SearchPageExtractor.preview(command.getHtml(), command.getMaxCandidates());
    }

    @Transactional
    public ProcurementCandidatePoolView importSearchPageCandidates(ProcurementImportSearchPageCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能导入搜索页候选。");
        }
        if (command.getDemandItemId() == null) {
            throw new IllegalArgumentException("请先选择要导入候选的采购需求。");
        }
        if (!StringUtils.hasText(command.getHtml())) {
            throw new IllegalArgumentException("请先粘贴 1688 搜索结果页 HTML。");
        }

        DemandItemView demandItem = procurementMapper.selectOwnedDemandItem(command.getOwnerUserId(), command.getDemandItemId());
        if (demandItem == null) {
            throw new IllegalArgumentException("当前采购需求不在老板名下，不能直接导入候选。");
        }
        procurementStructuredFieldParser.enrichDemandItem(demandItem);

        ProcurementSearchPagePreviewView preview = procurement1688SearchPageExtractor.preview(command.getHtml(), command.getMaxCandidates());
        if (preview.getCandidates().isEmpty()) {
            throw new IllegalArgumentException("当前 HTML 里没有识别出可导入的 1688 候选卡。");
        }

        procurementMapper.archiveCandidatesByDemandItem(command.getDemandItemId(), command.getOwnerUserId());
        procurementMapper.markDemandItemScreening(command.getDemandItemId(), command.getOwnerUserId());

        List<GeneratedCandidate> generatedCandidates = new ArrayList<>();
        int rankNo = 1;
        for (CandidateView candidate : preview.getCandidates()) {
            generatedCandidates.add(procurementAutoSelectionEngine.evaluateExtractedCandidate(demandItem, candidate, rankNo));
            rankNo += 1;
        }

        int recommendedCount = 0;
        for (GeneratedCandidate candidate : generatedCandidates) {
            if ("recommended".equals(candidate.getLevel())) {
                recommendedCount += 1;
            }
        }

        Long taskId = procurementMapper.nextTaskId();
        LocalDateTime now = LocalDateTime.now();
        String taskStatus = recommendedCount > 0 ? "SUCCESS" : "PARTIAL_SUCCESS";
        procurementMapper.insertMatchTask(
                taskId,
                command.getDemandItemId(),
                taskStatus,
                100,
                "SEARCH_PAGE_HTML",
                0,
                "PASTED_1688_SEARCH_PAGE",
                generatedCandidates.size(),
                recommendedCount,
                String.format(
                        "已从 1688 搜索页导入 %d 条候选，建议优先复核前 %d 条高匹配结果。",
                        generatedCandidates.size(),
                        Math.max(1, recommendedCount)
                ),
                now,
                now,
                command.getOwnerUserId(),
                command.getOwnerUserId()
        );

        persistGeneratedCandidates(
                command.getDemandItemId(),
                command.getOwnerUserId(),
                taskId,
                generatedCandidates
        );
        procurementMapper.updateDemandItemStatus(
                command.getDemandItemId(),
                recommendedCount > 0 ? "REVIEWING" : "SCREENING",
                command.getOwnerUserId()
        );

        ProcurementCandidatePoolView refreshedView = buildCandidatePool(command.getOwnerUserId(), command.getOrderNo());
        refreshedView.setSelectedDemandItemId(command.getDemandItemId());
        refreshedView.setMessage(
                String.format(
                        "已把 %d 条搜索页候选导入当前采购需求，可直接进入候选池决策。",
                        generatedCandidates.size()
                )
        );
        return refreshedView;
    }

    @Transactional
    public ProcurementCandidatePoolView backfillManualCandidates(ProcurementManualCandidateBackfillCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能回填候选。");
        }
        if (command.getDemandItemId() == null) {
            throw new IllegalArgumentException("请先选择要回填候选的采购需求。");
        }
        if (command.getCandidates() == null || command.getCandidates().isEmpty()) {
            throw new IllegalArgumentException("请先填写至少一条 1688 候选。");
        }

        DemandItemView demandItem = procurementMapper.selectOwnedDemandItem(command.getOwnerUserId(), command.getDemandItemId());
        if (demandItem == null) {
            throw new IllegalArgumentException("当前采购需求不在老板名下，不能直接回填候选。");
        }
        procurementStructuredFieldParser.enrichDemandItem(demandItem);

        List<GeneratedCandidate> generatedCandidates = new ArrayList<>();
        int nextRankNo = procurementMapper.selectMaxRankNoByDemandItem(command.getDemandItemId()) + 1;
        for (ManualCandidateInput item : command.getCandidates()) {
            if (!hasManualCandidateCoreFields(item)) {
                continue;
            }
            CandidateView manualCandidate = buildManualCandidate(item);
            procurementStructuredFieldParser.enrichCandidate(manualCandidate);
            generatedCandidates.add(procurementAutoSelectionEngine.evaluateExtractedCandidate(demandItem, manualCandidate, nextRankNo));
            nextRankNo += 1;
        }

        if (generatedCandidates.isEmpty()) {
            throw new IllegalArgumentException("请至少填写一条有效的 1688 候选链接和商品标题。");
        }

        int recommendedCount = 0;
        for (GeneratedCandidate candidate : generatedCandidates) {
            if ("recommended".equals(candidate.getLevel())) {
                recommendedCount += 1;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Long taskId = procurementMapper.nextTaskId();
        procurementMapper.insertMatchTask(
                taskId,
                command.getDemandItemId(),
                recommendedCount > 0 ? "SUCCESS" : "PARTIAL_SUCCESS",
                100,
                "MANUAL_1688_BACKFILL",
                0,
                "OPERATOR_PAGE_PICK",
                generatedCandidates.size(),
                recommendedCount,
                String.format(
                        "已从 1688 页面手工回填 %d 条候选，建议优先复核前 %d 条高匹配结果。",
                        generatedCandidates.size(),
                        Math.max(1, recommendedCount)
                ),
                now,
                now,
                command.getOwnerUserId(),
                command.getOwnerUserId()
        );

        persistGeneratedCandidates(
                command.getDemandItemId(),
                command.getOwnerUserId(),
                taskId,
                generatedCandidates
        );
        procurementMapper.updateDemandItemStatus(
                command.getDemandItemId(),
                recommendedCount > 0 ? "REVIEWING" : "SCREENING",
                command.getOwnerUserId()
        );

        ProcurementCandidatePoolView refreshedView = buildCandidatePool(command.getOwnerUserId(), command.getOrderNo());
        refreshedView.setSelectedDemandItemId(command.getDemandItemId());
        refreshedView.setMessage(
                String.format(
                        "已向当前采购需求追加 %d 条 1688 候选，可继续在候选池里对比和筛选。",
                        generatedCandidates.size()
                )
        );
        return refreshedView;
    }

    @Transactional
    public ProcurementCandidatePoolView runAutoSelection(ProcurementAutoSelectionCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能运行自动选品。");
        }
        if (command.getDemandItemId() == null) {
            throw new IllegalArgumentException("请先选择要执行自动选品的采购需求。");
        }

        DemandItemView demandItem = procurementMapper.selectOwnedDemandItem(command.getOwnerUserId(), command.getDemandItemId());
        if (demandItem == null) {
            throw new IllegalArgumentException("当前采购需求不在老板名下，不能直接运行自动选品。");
        }

        procurementStructuredFieldParser.enrichDemandItem(demandItem);
        procurementMapper.archiveCandidatesByDemandItem(command.getDemandItemId(), command.getOwnerUserId());
        procurementMapper.markDemandItemScreening(command.getDemandItemId(), command.getOwnerUserId());

        AutoSelectionResult result = procurementAutoSelectionEngine.generate(demandItem);
        LocalDateTime now = LocalDateTime.now();
        Long taskId = procurementMapper.nextTaskId();
        procurementMapper.insertMatchTask(
                taskId,
                command.getDemandItemId(),
                result.getTaskStatus(),
                result.getProgressPercent(),
                result.getSearchMode(),
                result.getSelectedImageCount(),
                result.getSearchPath(),
                result.getResultCount(),
                result.getRecommendedCount(),
                result.getMessage(),
                now,
                now,
                command.getOwnerUserId(),
                command.getOwnerUserId()
        );

        persistGeneratedCandidates(
                command.getDemandItemId(),
                command.getOwnerUserId(),
                taskId,
                result.getCandidates()
        );
        procurementMapper.updateDemandItemStatus(
                command.getDemandItemId(),
                result.getRecommendedCount() > 0 ? "REVIEWING" : "SCREENING",
                command.getOwnerUserId()
        );

        ProcurementCandidatePoolView refreshedView = buildCandidatePool(command.getOwnerUserId(), command.getOrderNo());
        refreshedView.setSelectedDemandItemId(command.getDemandItemId());
        refreshedView.setMessage("已按当前采购要求重新执行自动选品，候选池已刷新。");
        return refreshedView;
    }

    private void persistGeneratedCandidates(
            Long demandItemId,
            Long ownerUserId,
            Long taskId,
            List<GeneratedCandidate> candidates
    ) {
        Long nextCandidateId = procurementMapper.nextCandidateId();
        for (GeneratedCandidate candidate : candidates) {
            procurementMapper.insertCandidate(
                    nextCandidateId,
                    demandItemId,
                    taskId,
                    candidate.getRankNo(),
                    candidate.getLevel(),
                    candidate.getTotalScore(),
                    candidate.getFitScore(),
                    candidate.getSpecScore(),
                    candidate.getPriceScore(),
                    candidate.getSupplierScore(),
                    candidate.getLogisticsScore(),
                    candidate.getCandidatePlatform(),
                    candidate.getCandidateUrl(),
                    candidate.getTitle(),
                    candidate.getSupplierName(),
                    candidate.getPriceText(),
                    candidate.getMoqText(),
                    candidate.getLocationText(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    candidate.getResultCardText(),
                    candidate.getDetailHighlightText(),
                    candidate.getAttributeSnapshotText(),
                    candidate.getShippingSnapshotText(),
                    candidate.getPackageSnapshotText(),
                    candidate.getMainImageUrl(),
                    candidate.getDetailImageUrl(),
                    candidate.getDeliveryImageUrl(),
                    candidate.getNextAction(),
                    joinPipeText(candidate.getBadges()),
                    joinPipeText(candidate.getReasons()),
                    joinPipeText(candidate.getWarnings()),
                    ownerUserId,
                    ownerUserId
            );
            nextCandidateId += 1;
        }
    }

    private boolean hasManualCandidateCoreFields(ManualCandidateInput item) {
        if (item == null) {
            return false;
        }
        return StringUtils.hasText(item.getCandidateUrl()) && StringUtils.hasText(item.getTitle());
    }

    private CandidateView buildManualCandidate(ManualCandidateInput item) {
        CandidateView candidate = new CandidateView();
        candidate.setCandidatePlatform("1688");
        candidate.setCandidateUrl(normalize(item.getCandidateUrl()));
        candidate.setTitle(normalize(item.getTitle()));
        candidate.setSupplierName(normalize(item.getSupplierName()));
        candidate.setPriceText(normalize(item.getPriceText()));
        candidate.setMoqText(normalize(item.getMoqText()));
        candidate.setLocationText(normalize(item.getLocationText()));
        candidate.setResultCardText(firstNonBlank(
                normalize(item.getResultCardText()),
                buildManualResultCardText(item)
        ));
        candidate.setDetailHighlightText(firstNonBlank(
                normalize(item.getDetailHighlightText()),
                normalize(item.getResultCardText()),
                buildManualDetailHighlightText(item)
        ));
        candidate.setAttributeSnapshotText(firstNonBlank(
                normalize(item.getAttributeSnapshotText()),
                buildManualAttributeSnapshotText(item)
        ));
        candidate.setShippingSnapshotText(firstNonBlank(
                normalize(item.getShippingSnapshotText()),
                buildManualShippingSnapshotText(item)
        ));
        candidate.setPackageSnapshotText(firstNonBlank(
                normalize(item.getPackageSnapshotText()),
                buildManualPackageSnapshotText(item)
        ));
        candidate.setMainImageUrl(normalize(item.getMainImageUrl()));
        return candidate;
    }

    private String buildManualResultCardText(ManualCandidateInput item) {
        return joinNonBlankParts(
                "结果卡片",
                normalize(item.getTitle()),
                normalize(item.getSupplierName()),
                StringUtils.hasText(item.getPriceText()) ? "价格 " + normalize(item.getPriceText()) : null,
                StringUtils.hasText(item.getMoqText()) ? "起订量 " + normalize(item.getMoqText()) : null,
                StringUtils.hasText(item.getLocationText()) ? "发货地 " + normalize(item.getLocationText()) : null
        );
    }

    private String buildManualDetailHighlightText(ManualCandidateInput item) {
        return joinNonBlankParts(
                "详情卖点",
                normalize(item.getTitle()),
                normalize(item.getResultCardText())
        );
    }

    private String buildManualAttributeSnapshotText(ManualCandidateInput item) {
        return joinNonBlankParts(
                "属性快照",
                normalize(item.getAttributeSnapshotText()),
                normalize(item.getDetailHighlightText())
        );
    }

    private String buildManualShippingSnapshotText(ManualCandidateInput item) {
        return joinNonBlankParts(
                "物流说明",
                normalize(item.getShippingSnapshotText()),
                StringUtils.hasText(item.getLocationText()) ? "发货地 " + normalize(item.getLocationText()) : null
        );
    }

    private String buildManualPackageSnapshotText(ManualCandidateInput item) {
        return joinNonBlankParts(
                "包装说明",
                normalize(item.getPackageSnapshotText()),
                normalize(item.getAttributeSnapshotText())
        );
    }

    private String joinNonBlankParts(String... parts) {
        List<String> values = new ArrayList<>();
        if (parts == null) {
            return null;
        }
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                values.add(part.trim());
            }
        }
        return values.isEmpty() ? null : String.join("；", values);
    }

    private boolean hasAnyExtractionInput(ProcurementExtractionPreviewCommand command) {
        return StringUtils.hasText(command.getTitle())
                || StringUtils.hasText(command.getSupplierName())
                || StringUtils.hasText(command.getPriceText())
                || StringUtils.hasText(command.getMoqText())
                || StringUtils.hasText(command.getLocationText())
                || StringUtils.hasText(command.getResultCardText())
                || StringUtils.hasText(command.getDetailHighlightText())
                || StringUtils.hasText(command.getAttributeSnapshotText())
                || StringUtils.hasText(command.getShippingSnapshotText())
                || StringUtils.hasText(command.getPackageSnapshotText())
                || StringUtils.hasText(command.getBadgesText())
                || StringUtils.hasText(command.getReasonsText())
                || StringUtils.hasText(command.getWarningsText());
    }

    private SummaryView buildSummary(List<DemandItemView> demandItems) {
        SummaryView summaryView = new SummaryView();
        summaryView.setTotalItems(demandItems.size());

        int runningTasks = 0;
        int successTasks = 0;
        int failedTasks = 0;
        int recommendedCandidates = 0;
        int reviewCandidates = 0;
        int selectedCandidates = 0;

        for (DemandItemView demandItem : demandItems) {
            TaskView task = demandItem.getTask();
            if (task != null) {
                String normalizedTaskStatus = upper(task.getStatus());
                if ("RUNNING".equals(normalizedTaskStatus) || "QUEUED".equals(normalizedTaskStatus)) {
                    runningTasks += 1;
                } else if ("FAILED".equals(normalizedTaskStatus)) {
                    failedTasks += 1;
                } else if ("SUCCESS".equals(normalizedTaskStatus) || "PARTIAL_SUCCESS".equals(normalizedTaskStatus)) {
                    successTasks += 1;
                }
            }

            for (CandidateView candidate : demandItem.getCandidates()) {
                String normalizedLevel = lower(candidate.getLevel());
                if ("recommended".equals(normalizedLevel)) {
                    recommendedCandidates += 1;
                } else if ("review".equals(normalizedLevel)) {
                    reviewCandidates += 1;
                }
                if (Boolean.TRUE.equals(candidate.getSelected())) {
                    selectedCandidates += 1;
                }
            }
        }

        summaryView.setRunningTasks(runningTasks);
        summaryView.setSuccessTasks(successTasks);
        summaryView.setFailedTasks(failedTasks);
        summaryView.setRecommendedCandidates(recommendedCandidates);
        summaryView.setReviewCandidates(reviewCandidates);
        summaryView.setSelectedCandidates(selectedCandidates);
        return summaryView;
    }

    private Long resolveSelectedDemandItemId(List<DemandItemView> demandItems) {
        if (demandItems.isEmpty()) {
            return null;
        }

        for (DemandItemView item : demandItems) {
            if (item.getSelectedCandidateId() != null) {
                return item.getId();
            }
        }

        for (DemandItemView item : demandItems) {
            if (!Objects.equals("DECIDED", upper(item.getStatus()))) {
                return item.getId();
            }
        }

        return demandItems.get(0).getId();
    }

    private List<String> splitPipeText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return new ArrayList<>();
        }

        return List.of(rawValue.split("\\|")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    private String lower(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private String joinPipeText(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("|"));
    }

}
