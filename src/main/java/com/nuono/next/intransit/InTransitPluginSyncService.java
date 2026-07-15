package com.nuono.next.intransit;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SavePackageCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.NodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanOrder;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanOrderBox;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncLine;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncNode;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncPackage;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncSourceBatchExpectation;
import com.nuono.next.intransit.InTransitPluginSyncRecords.EtBoxSyncPlanBoxView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.EtBoxSyncPlanView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.EtBoxSyncStateRow;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncBatchPreviewView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncCommitView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncIssueView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncPreviewView;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class InTransitPluginSyncService {

    private static final Set<String> SUPPORTED_SOURCE_SYSTEMS = Set.of("CHIC", "ET", "YITONG", "YITE");
    private static final int PLUGIN_SYNC_BATCH_LOCK_TIMEOUT_SECONDS = 10;

    private final InTransitGoodsMapper mapper;
    private final InTransitBatchService batchService;
    private final InTransitGoodsAccessScopeService accessScopeService;

    public InTransitPluginSyncService(
            InTransitGoodsMapper mapper,
            InTransitBatchService batchService,
            InTransitGoodsAccessScopeService accessScopeService
    ) {
        this.mapper = mapper;
        this.batchService = batchService;
        this.accessScopeService = accessScopeService;
    }

    public PluginSyncPreviewView preview(PluginSyncCommand command) {
        return analyze(activeCommand(command));
    }

    public EtBoxSyncPlanView planEtBoxSync(EtBoxSyncPlanCommand command) {
        EtBoxSyncPlanCommand resolved = command == null ? new EtBoxSyncPlanCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        InTransitForwarderCatalog.CanonicalForwarder forwarder = resolveForwarder(
                resolved.getSourceSystem(),
                resolved.getForwarderName()
        );
        String sourceSystem = normalizeCode(resolved.getSourceSystem());
        if (!"ET".equals(sourceSystem) && !"YITONG".equals(sourceSystem)) {
            throw new IllegalArgumentException("易通箱子同步计划只支持 ET/YITONG 来源。");
        }

        EtBoxSyncPlanView view = new EtBoxSyncPlanView();
        view.setSourceSystem("ET");
        view.setForwarderName(forwarder.name());
        view.setForceFullSync(resolved.isForceFullSync());
        view.setOrderCount(resolved.getShipOrders().size());
        List<EtBoxSyncPlanBoxView> boxViews = new ArrayList<>();

        for (EtBoxSyncPlanOrder order : resolved.getShipOrders()) {
            String shipOrderId = clean(order.getShipOrderId());
            BatchRow batch = StringUtils.hasText(shipOrderId)
                    ? mapper.selectBatchByReferenceNo(ownerUserId, shipOrderId)
                    : null;
            for (EtBoxSyncPlanOrderBox box : order.getBoxes()) {
                String boxId = clean(box.getBoxId());
                String clientBoxId = clean(box.getClientBoxId());
                EtBoxSyncPlanBoxView boxView = new EtBoxSyncPlanBoxView();
                boxView.setShipOrderId(shipOrderId);
                boxView.setBoxId(boxId);
                boxView.setClientBoxId(clientBoxId);

                if (resolved.isForceFullSync()) {
                    setEtPlanAction(boxView, "FETCH_ALL", "force_full_sync");
                } else if (batch == null) {
                    setEtPlanAction(boxView, "FETCH_ALL", "new_box");
                } else {
                    EtBoxSyncStateRow state = mapper.selectEtBoxSyncState(ownerUserId, batch.getId(), boxId, clientBoxId);
                    setEtPlanAction(boxView, resolveEtPlanAction(state), resolveEtPlanReason(state));
                }
                addEtPlanBox(view, boxViews, boxView);
            }
        }
        view.setBoxes(boxViews);
        return view;
    }

    @Transactional
    public PluginSyncCommitView commit(PluginSyncCommand command) {
        PluginSyncCommand resolved = activeCommand(command);
        PluginSyncPreviewView preview = analyze(resolved);
        if (!preview.isCommittable()) {
            throw new IllegalStateException("插件同步预览存在错误，不能落库。");
        }
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long operatorUserId = resolved.getOperatorUserId();
        InTransitForwarderCatalog.CanonicalForwarder forwarder = resolveForwarder(resolved);
        Map<String, PluginSyncSourceBatchExpectation> sourceExpectationMap = sourceExpectationByBatch(
                resolved.getSourceBatchExpectations()
        );

        for (PluginSyncBatch batch : resolved.getBatches()) {
            String batchNo = clean(batch.getBatchNo());
            if (!StringUtils.hasText(batchNo)) {
                continue;
            }
            commitBatchWithLock(resolved, forwarder, sourceExpectationMap, ownerUserId, operatorUserId, batch, batchNo);
        }
        return InTransitPluginSyncRecords.committedFrom(preview);
    }

    private void commitBatchWithLock(
            PluginSyncCommand resolved,
            InTransitForwarderCatalog.CanonicalForwarder forwarder,
            Map<String, PluginSyncSourceBatchExpectation> sourceExpectationMap,
            Long ownerUserId,
            Long operatorUserId,
            PluginSyncBatch batch,
            String batchNo
    ) {
        String lockName = pluginSyncBatchLockName(ownerUserId, batchNo);
        Integer acquired = mapper.acquirePluginSyncBatchLock(lockName, PLUGIN_SYNC_BATCH_LOCK_TIMEOUT_SECONDS);
        if (!Integer.valueOf(1).equals(acquired)) {
            throw new IllegalStateException("批次正在同步中，请稍后重试：" + batchNo);
        }
        boolean releaseAfterTransaction = registerPluginSyncBatchLockRelease(lockName);
        try {
            commitBatch(resolved, forwarder, sourceExpectationMap, ownerUserId, operatorUserId, batch, batchNo);
        } finally {
            if (!releaseAfterTransaction) {
                releasePluginSyncBatchLock(lockName);
            }
        }
    }

    private void commitBatch(
            PluginSyncCommand resolved,
            InTransitForwarderCatalog.CanonicalForwarder forwarder,
            Map<String, PluginSyncSourceBatchExpectation> sourceExpectationMap,
            Long ownerUserId,
            Long operatorUserId,
            PluginSyncBatch batch,
            String batchNo
    ) {
        BatchRow existingBatch = mapper.selectBatchByReferenceNo(ownerUserId, batchNo);
        SaveBatchCommand batchCommand = toBatchCommand(resolved, batch, existingBatch, forwarder);
        accessScopeService.requireWritableBatchScope(resolved.getAccessContext(), batchCommand);
        BatchView savedBatch = batchService.saveBatch(batchCommand);
        List<String> syncedBoxNos = syncedBoxNos(batch);
        List<String> syncedLineKeys = syncedLineKeys(ownerUserId, batch);
        if (shouldReconcileSyncedDetails(
                batch,
                sourceExpectationMap.get(normalizeCode(batchNo)),
                syncedBoxNos,
                syncedLineKeys
        )) {
            batchService.reconcileSyncedDetails(
                    ownerUserId,
                    operatorUserId,
                    savedBatch.getBatchId(),
                    syncedBoxNos,
                    syncedLineKeys
            );
        }

        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            String boxNo = clean(itemPackage.getBoxNo());
            if (!StringUtils.hasText(boxNo)) {
                continue;
            }
            SavePackageCommand packageCommand = toPackageCommand(resolved, savedBatch.getBatchId(), itemPackage);
            batchService.savePackage(packageCommand);
        }

        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            String boxNo = clean(itemPackage.getBoxNo());
            if (!StringUtils.hasText(boxNo)) {
                continue;
            }
            for (PluginSyncLine line : itemPackage.getLines()) {
                String barcode = sourceBarcode(line);
                if (!StringUtils.hasText(barcode)) {
                    continue;
                }
                String psku = requirePartnerSkuFromBarcode(ownerUserId, barcode);
                LineRow existingLine = selectExistingLineForResolvedPsku(
                        ownerUserId,
                        savedBatch.getBatchId(),
                        boxNo,
                        psku,
                        barcode
                );
                SaveLineCommand lineCommand = toLineCommand(
                        resolved,
                        savedBatch.getBatchId(),
                        batch,
                        itemPackage,
                        line,
                        barcode,
                        psku,
                        existingLine
                );
                accessScopeService.requireWritableLineScope(resolved.getAccessContext(), lineCommand);
                batchService.saveLine(lineCommand);
            }
        }

        boolean shouldRefreshLatestNodeProjection = false;
        for (PluginSyncNode node : batch.getNodes()) {
            ParsedNode parsedNode = parseNode(resolved.getSourceSystem(), batchNo, node);
            if (parsedNode == null) {
                continue;
            }
            NodeRow existingNode = mapper.selectNodeByStatusDescriptionAndHappenedAt(
                    ownerUserId,
                    savedBatch.getBatchId(),
                    parsedNode.nodeStatus,
                    parsedNode.nodeHappenedAt,
                    parsedNode.description
            );
            if (existingNode != null) {
                if (InTransitNodeStatus.isTerminalForBatchProjection(existingNode.getNodeStatus(), existingNode.getDescription())) {
                    shouldRefreshLatestNodeProjection = true;
                }
                continue;
            }
            SaveNodeCommand nodeCommand = new SaveNodeCommand();
            nodeCommand.setOwnerUserId(ownerUserId);
            nodeCommand.setOperatorUserId(operatorUserId);
            nodeCommand.setBatchId(savedBatch.getBatchId());
            nodeCommand.setNodeStatus(parsedNode.nodeStatus);
            nodeCommand.setNodeHappenedAt(parsedNode.nodeHappenedAt);
            nodeCommand.setDescription(parsedNode.description);
            nodeCommand.setOperatorName(clean(node.getOperatorName()));
            batchService.saveNode(nodeCommand);
        }
        if (shouldRefreshLatestNodeProjection) {
            batchService.refreshBatchLatestNodeProjection(ownerUserId, savedBatch.getBatchId());
        }
    }

    private boolean registerPluginSyncBatchLockRelease(String lockName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        // MySQL user locks must stay held until the enclosing transaction has committed or rolled back.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            private boolean released;

            @Override
            public void afterCommit() {
                releaseOnce();
            }

            @Override
            public void afterCompletion(int status) {
                releaseOnce();
            }

            private void releaseOnce() {
                if (released) {
                    return;
                }
                releasePluginSyncBatchLock(lockName);
                released = true;
            }
        });
        return true;
    }

    private void releasePluginSyncBatchLock(String lockName) {
        try {
            mapper.releasePluginSyncBatchLock(lockName);
        } catch (RuntimeException ignored) {
            // Losing the connection also releases MySQL user locks; do not mask the real sync result.
        }
    }

    private PluginSyncCommand activeCommand(PluginSyncCommand command) {
        PluginSyncCommand source = command == null ? new PluginSyncCommand() : command;
        PluginSyncCommand result = new PluginSyncCommand();
        result.setOwnerUserId(source.getOwnerUserId());
        result.setOperatorUserId(source.getOperatorUserId());
        result.setAccessContext(source.getAccessContext());
        result.setSourceSystem(source.getSourceSystem());
        result.setForwarderName(source.getForwarderName());

        List<PluginSyncBatch> activeBatches = new ArrayList<>();
        Set<String> activeBatchKeys = new LinkedHashSet<>();
        for (PluginSyncBatch batch : source.getBatches()) {
            if (isCancelledBatch(batch)) {
                continue;
            }
            activeBatches.add(batch);
            String batchNo = clean(batch.getBatchNo());
            if (StringUtils.hasText(batchNo)) {
                activeBatchKeys.add(normalizeCode(batchNo));
            }
        }
        result.setBatches(activeBatches);
        result.setSourceBatchExpectations(activeSourceBatchExpectations(
                source.getSourceBatchExpectations(),
                activeBatchKeys
        ));
        return result;
    }

    private PluginSyncPreviewView analyze(PluginSyncCommand command) {
        PluginSyncCommand resolved = command == null ? new PluginSyncCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        InTransitForwarderCatalog.CanonicalForwarder forwarder = null;
        List<PluginSyncIssueView> issues = new ArrayList<>();
        try {
            forwarder = resolveForwarder(resolved);
        } catch (IllegalArgumentException exception) {
            issues.add(PluginSyncIssueView.error(null, null, null, "forwarderName", exception.getMessage()));
        }
        if (!StringUtils.hasText(resolved.getSourceSystem())) {
            issues.add(PluginSyncIssueView.error(null, null, null, "sourceSystem", "插件来源系统不能为空。"));
        } else if (!SUPPORTED_SOURCE_SYSTEMS.contains(normalizeCode(resolved.getSourceSystem()))) {
            issues.add(PluginSyncIssueView.error(null, null, null, "sourceSystem", "插件来源系统只支持 CHIC、ET、YITONG、YITE。"));
        }
        if (resolved.getBatches().isEmpty()) {
            issues.add(PluginSyncIssueView.error(null, null, null, "batches", "插件同步批次不能为空。"));
        }

        PluginSyncPreviewView view = new PluginSyncPreviewView();
        view.setSourceSystem(normalizeCode(resolved.getSourceSystem()));
        view.setForwarderName(forwarder == null ? clean(resolved.getForwarderName()) : forwarder.name());
        List<PluginSyncBatchPreviewView> batchViews = new ArrayList<>();
        int packageCount = 0;
        int lineCount = 0;
        int nodeCount = 0;
        int newBatchCount = 0;
        int updateBatchCount = 0;
        int newLineCount = 0;
        int updateLineCount = 0;
        int newNodeCount = 0;
        int skippedNodeCount = 0;
        Set<String> lineKeys = new LinkedHashSet<>();
        Set<String> nodeKeys = new LinkedHashSet<>();
        Set<String> batchKeys = new LinkedHashSet<>();
        Map<String, PluginSyncSourceBatchExpectation> sourceExpectationMap = buildSourceExpectationMap(
                resolved.getSourceBatchExpectations(),
                issues
        );

        for (PluginSyncBatch batch : resolved.getBatches()) {
            String batchNo = clean(batch.getBatchNo());
            if (!StringUtils.hasText(batchNo)) {
                issues.add(PluginSyncIssueView.error(null, null, null, "batchNo", "批次号不能为空。"));
                continue;
            }
            if (!batchKeys.add(normalizeCode(batchNo))) {
                issues.add(PluginSyncIssueView.error(batchNo, null, null, "batchNo", "同一 payload 内批次号不能重复。"));
            }
            BatchRow existingBatch = mapper.selectBatchByReferenceNo(ownerUserId, batchNo);
            if (existingBatch == null) {
                newBatchCount += 1;
            } else {
                updateBatchCount += 1;
            }
            validateBatch(resolved, batch, existingBatch, issues);
            PluginSyncBatchPreviewView batchView = new PluginSyncBatchPreviewView();
            batchView.setBatchNo(batchNo);
            batchView.setBatchId(existingBatch == null ? null : existingBatch.getId());
            batchView.setAction(existingBatch == null ? "create" : "update");
            batchView.setPackageCount(batch.getPackages().size());
            packageCount += batch.getPackages().size();
            warnWhenPackageDetailsAreMissing(batchNo, batch, issues);
            warnWhenPackageChargeableWeightIsMissing(ownerUserId, batchNo, batch, existingBatch, issues);

            int batchShippedQuantity = 0;
            Set<String> boxKeys = new LinkedHashSet<>();
            for (PluginSyncPackage itemPackage : batch.getPackages()) {
                String boxNo = clean(itemPackage.getBoxNo());
                if (!StringUtils.hasText(boxNo)) {
                    issues.add(PluginSyncIssueView.error(batchNo, null, null, "boxNo", "箱号不能为空。"));
                    continue;
                }
                if (sameIdentifier(batchNo, boxNo)) {
                    issues.add(PluginSyncIssueView.error(
                            batchNo,
                            boxNo,
                            null,
                            "boxNo",
                            "箱号不能等于批次号，正确箱号应类似 " + batchNo + "-1。"
                    ));
                }
                if (!boxKeys.add(normalizeCode(boxNo))) {
                    issues.add(PluginSyncIssueView.error(batchNo, boxNo, null, "boxNo", "同一批次内箱号不能重复。"));
                }
                for (PluginSyncLine line : itemPackage.getLines()) {
                    String barcode = sourceBarcode(line);
                    lineCount += 1;
                    batchView.setLineCount(batchView.getLineCount() + 1);
                    validateLine(batchNo, boxNo, resolved, batch, line, issues);
                    if (line.getShippedQuantity() != null && line.getShippedQuantity() >= 0) {
                        batchShippedQuantity += line.getShippedQuantity();
                    }
                    if (!StringUtils.hasText(barcode) || hasBarcodeConflict(line)) {
                        continue;
                    }
                    String psku = resolvePartnerSkuFromBarcode(ownerUserId, batchNo, boxNo, barcode, issues);
                    if (!StringUtils.hasText(psku)) {
                        continue;
                    }
                    String lineKey = batchNo + "\n" + boxNo + "\n" + psku;
                    if (!lineKeys.add(lineKey)) {
                        issues.add(PluginSyncIssueView.error(batchNo, boxNo, psku, "barcode", "同一批次同一箱号内 barcode 解析后的 PSKU 重复。"));
                        continue;
                    }
                    LineRow existingLine = existingBatch == null
                            ? null
                            : selectExistingLineForResolvedPsku(ownerUserId, existingBatch.getId(), boxNo, psku, barcode);
                    if (existingLine == null) {
                        newLineCount += 1;
                    } else {
                        updateLineCount += 1;
                    }
                }
            }
            validateSourceExpectation(batchNo, batch.getPackages().size(), batchShippedQuantity, sourceExpectationMap, issues);

            for (PluginSyncNode node : batch.getNodes()) {
                nodeCount += 1;
                batchView.setNodeCount(batchView.getNodeCount() + 1);
                ParsedNode parsed = parseNode(resolved.getSourceSystem(), batchNo, node, issues);
                if (parsed == null) {
                    continue;
                }
                String nodeKey = batchNo + "\n" + parsed.nodeStatus + "\n" + parsed.nodeHappenedAt + "\n" + clean(parsed.description);
                if (!nodeKeys.add(nodeKey)) {
                    skippedNodeCount += 1;
                    continue;
                }
                NodeRow existingNode = existingBatch == null
                        ? null
                        : mapper.selectNodeByStatusDescriptionAndHappenedAt(
                                ownerUserId,
                                existingBatch.getId(),
                                parsed.nodeStatus,
                                parsed.nodeHappenedAt,
                                parsed.description
                        );
                if (existingNode == null) {
                    newNodeCount += 1;
                } else {
                    skippedNodeCount += 1;
                }
            }
            batchViews.add(batchView);
        }

        view.setBatchCount(resolved.getBatches().size());
        view.setPackageCount(packageCount);
        view.setLineCount(lineCount);
        view.setNodeCount(nodeCount);
        view.setNewBatchCount(newBatchCount);
        view.setUpdateBatchCount(updateBatchCount);
        view.setNewLineCount(newLineCount);
        view.setUpdateLineCount(updateLineCount);
        view.setNewNodeCount(newNodeCount);
        view.setSkippedNodeCount(skippedNodeCount);
        view.setIssues(issues);
        view.setBatches(batchViews);
        view.setCommittable(issues.stream().noneMatch(issue -> "error".equals(issue.getLevel())));
        return view;
    }

    private List<String> syncedBoxNos(PluginSyncBatch batch) {
        Set<String> result = new LinkedHashSet<>();
        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            String boxNo = clean(itemPackage.getBoxNo());
            if (StringUtils.hasText(boxNo) && !itemPackage.getLines().isEmpty()) {
                result.add(boxNo);
            }
        }
        return new ArrayList<>(result);
    }

    private List<String> syncedLineKeys(Long ownerUserId, PluginSyncBatch batch) {
        Set<String> result = new LinkedHashSet<>();
        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            String boxNo = clean(itemPackage.getBoxNo());
            if (!StringUtils.hasText(boxNo)) {
                continue;
            }
            for (PluginSyncLine line : itemPackage.getLines()) {
                String barcode = sourceBarcode(line);
                String psku = StringUtils.hasText(barcode)
                        ? requirePartnerSkuFromBarcode(ownerUserId, barcode)
                        : null;
                if (StringUtils.hasText(psku)) {
                    result.add(boxNo + "\n" + psku);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private boolean shouldReconcileSyncedDetails(
            PluginSyncBatch batch,
            PluginSyncSourceBatchExpectation expectation,
            List<String> syncedBoxNos,
            List<String> syncedLineKeys
    ) {
        if (syncedBoxNos.isEmpty() || syncedLineKeys.isEmpty()) {
            return false;
        }
        if (expectation == null || expectation.getBoxNum() == null || expectation.getTotalQuantity() == null) {
            return false;
        }
        return expectation.getBoxNum() == batch.getPackages().size()
                && expectation.getTotalQuantity() == batchShippedQuantity(batch);
    }

    private int batchShippedQuantity(PluginSyncBatch batch) {
        int result = 0;
        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            for (PluginSyncLine line : itemPackage.getLines()) {
                if (line.getShippedQuantity() != null && line.getShippedQuantity() >= 0) {
                    result += line.getShippedQuantity();
                }
            }
        }
        return result;
    }

    private Map<String, PluginSyncSourceBatchExpectation> sourceExpectationByBatch(
            List<PluginSyncSourceBatchExpectation> expectations
    ) {
        Map<String, PluginSyncSourceBatchExpectation> result = new LinkedHashMap<>();
        for (PluginSyncSourceBatchExpectation expectation : expectations) {
            String batchNo = clean(expectation.getBatchNo());
            if (StringUtils.hasText(batchNo)) {
                result.put(normalizeCode(batchNo), expectation);
            }
        }
        return result;
    }

    private Map<String, PluginSyncSourceBatchExpectation> buildSourceExpectationMap(
            List<PluginSyncSourceBatchExpectation> expectations,
            List<PluginSyncIssueView> issues
    ) {
        Map<String, PluginSyncSourceBatchExpectation> result = new LinkedHashMap<>();
        for (PluginSyncSourceBatchExpectation expectation : expectations) {
            String batchNo = clean(expectation.getBatchNo());
            if (!StringUtils.hasText(batchNo)) {
                issues.add(PluginSyncIssueView.error(
                        null,
                        null,
                        null,
                        "sourceBatchExpectations.batchNo",
                        "来源列表期望的批次号不能为空。"
                ));
                continue;
            }
            String key = normalizeCode(batchNo);
            if (result.containsKey(key)) {
                issues.add(PluginSyncIssueView.error(
                        batchNo,
                        null,
                        null,
                        "sourceBatchExpectations.batchNo",
                        "来源列表期望内批次号不能重复。"
                ));
                continue;
            }
            result.put(key, expectation);
        }
        return result;
    }

    private List<PluginSyncSourceBatchExpectation> activeSourceBatchExpectations(
            List<PluginSyncSourceBatchExpectation> expectations,
            Set<String> activeBatchKeys
    ) {
        Map<String, PluginSyncSourceBatchExpectation> firstByBatch = new LinkedHashMap<>();
        Map<String, Integer> countsByBatch = new LinkedHashMap<>();
        for (PluginSyncSourceBatchExpectation expectation : expectations) {
            String batchNo = clean(expectation.getBatchNo());
            if (!StringUtils.hasText(batchNo)) {
                continue;
            }
            String key = normalizeCode(batchNo);
            if (!activeBatchKeys.contains(key)) {
                continue;
            }
            firstByBatch.putIfAbsent(key, expectation);
            countsByBatch.put(key, countsByBatch.getOrDefault(key, 0) + 1);
        }

        List<PluginSyncSourceBatchExpectation> result = new ArrayList<>();
        for (Map.Entry<String, PluginSyncSourceBatchExpectation> entry : firstByBatch.entrySet()) {
            if (countsByBatch.getOrDefault(entry.getKey(), 0) == 1) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    private void validateSourceExpectation(
            String batchNo,
            int packageCount,
            int shippedQuantity,
            Map<String, PluginSyncSourceBatchExpectation> expectationMap,
            List<PluginSyncIssueView> issues
    ) {
        PluginSyncSourceBatchExpectation expectation = expectationMap.get(normalizeCode(batchNo));
        if (expectation == null) {
            return;
        }
        if (expectation.getBoxNum() != null && expectation.getBoxNum() != packageCount) {
            issues.add(PluginSyncIssueView.error(
                    batchNo,
                    null,
                    null,
                    "sourceBatchExpectations.boxNum",
                    "批次箱数 " + packageCount + " 与来源列表箱数 " + expectation.getBoxNum() + " 不一致。"
            ));
        }
        if (expectation.getTotalQuantity() != null && expectation.getTotalQuantity() != shippedQuantity) {
            issues.add(PluginSyncIssueView.error(
                    batchNo,
                    null,
                    null,
                    "sourceBatchExpectations.totalQuantity",
                    "批次商品数量 " + shippedQuantity + " 与来源列表商品总数 " + expectation.getTotalQuantity() + " 不一致。"
            ));
        }
    }

    private void warnWhenPackageDetailsAreMissing(
            String batchNo,
            PluginSyncBatch batch,
            List<PluginSyncIssueView> issues
    ) {
        if (batch.getPackages().isEmpty()) {
            issues.add(PluginSyncIssueView.warning(
                    batchNo,
                    null,
                    null,
                    "packages",
                    "本次未采集到箱子，提交后只会更新批次层，不会写入箱子或 SKU 明细。"
            ));
            return;
        }
        boolean hasSkuLines = false;
        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            if (!itemPackage.getLines().isEmpty()) {
                hasSkuLines = true;
                break;
            }
        }
        if (!hasSkuLines) {
            issues.add(PluginSyncIssueView.warning(
                    batchNo,
                    null,
                    null,
                    "packages.lines",
                    "本次未采集到 SKU 明细，提交后会更新批次和箱子信息，不会写入商品明细。"
            ));
        }
    }

    private void warnWhenPackageChargeableWeightIsMissing(
            Long ownerUserId,
            String batchNo,
            PluginSyncBatch batch,
            BatchRow existingBatch,
            List<PluginSyncIssueView> issues
    ) {
        if (!hasPackageWithGoodsLinesMissingChargeable(ownerUserId, batch, existingBatch)) {
            return;
        }
        issues.add(PluginSyncIssueView.warning(
                batchNo,
                null,
                null,
                "package.chargeableWeightKg",
                "本次采集到 SKU 明细，但箱子缺计费重；提交后该批次只能保存为草稿，请补齐箱规/计费重后再进入跟踪。"
        ));
    }

    private boolean hasPackageWithGoodsLinesMissingChargeable(
            Long ownerUserId,
            PluginSyncBatch batch,
            BatchRow existingBatch
    ) {
        if (batch == null) {
            return false;
        }
        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            if (itemPackage.getLines().isEmpty()) {
                continue;
            }
            if (resolveChargeableWeight(itemPackage) != null) {
                continue;
            }
            if (existingBatch != null && existingPackageHasChargeableWeight(ownerUserId, existingBatch, itemPackage)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean existingPackageHasChargeableWeight(
            Long ownerUserId,
            BatchRow existingBatch,
            PluginSyncPackage itemPackage
    ) {
        String boxNo = clean(itemPackage.getBoxNo());
        String externalBoxNo = clean(itemPackage.getExternalBoxNo());
        PackageRow existingPackage = null;
        if (StringUtils.hasText(boxNo)) {
            existingPackage = mapper.selectPackageByBoxNo(ownerUserId, existingBatch.getId(), boxNo);
        }
        if (existingPackage == null && StringUtils.hasText(externalBoxNo)) {
            existingPackage = mapper.selectPackageByExternalBoxNoForMerge(ownerUserId, existingBatch.getId(), externalBoxNo);
        }
        return existingPackage != null
                && resolveChargeableWeight(
                        existingPackage.getChargeableWeightKg(),
                        existingPackage.getWeightKg(),
                        existingPackage.getVolumeWeightKg()
                ) != null;
    }

    private RouteDefaults routeDefaults(PluginSyncCommand command, PluginSyncBatch batch) {
        String sourceSystem = normalizeCode(command.getSourceSystem());
        String batchNo = normalizeCode(batch == null ? null : batch.getBatchNo());
        if ("CHIC".equals(sourceSystem) && batchNo != null && batchNo.startsWith("XGGEKSA")) {
            return new RouteDefaults(InTransitTransportMode.AIR.code(), InTransitDestination.RUH.code(), "FBN-RUH");
        }
        if ("CHIC".equals(sourceSystem) && batchNo != null && batchNo.startsWith("XGGEUAE")) {
            return new RouteDefaults(InTransitTransportMode.AIR.code(), InTransitDestination.DB.code(), "FBN-DXB");
        }
        if ("YITE".equals(sourceSystem) && batchNo != null && batchNo.startsWith("YT")) {
            return new RouteDefaults(InTransitTransportMode.SEA.code(), InTransitDestination.RUH.code(), "FBN-RUH");
        }
        return RouteDefaults.empty();
    }

    private String resolveTransportModeForSave(
            PluginSyncBatch batch,
            BatchRow existingBatch,
            RouteDefaults routeDefaults
    ) {
        String resolved = firstText(
                StringUtils.hasText(batch.getTransportMode()) ? resolveTransportMode(batch.getTransportMode()) : null,
                existingBatch == null ? null : existingBatch.getTransportMode()
        );
        if (!StringUtils.hasText(batch.getTransportMode())) {
            resolved = firstText(resolved, routeDefaults.transportMode);
        }
        return resolved;
    }

    private String resolveDestinationForSave(
            PluginSyncBatch batch,
            BatchRow existingBatch,
            RouteDefaults routeDefaults
    ) {
        String resolved = firstText(
                resolveDestination(batch.getBatchNo(), batch.getDestination(), batch.getTargetWarehouseName()),
                existingBatch == null ? null : existingBatch.getTargetStoreCode()
        );
        if (!StringUtils.hasText(batch.getDestination()) && !StringUtils.hasText(batch.getTargetWarehouseName())) {
            resolved = firstText(resolved, routeDefaults.targetStoreCode);
        }
        return resolved;
    }

    private String resolveWarehouseForSave(
            PluginSyncBatch batch,
            BatchRow existingBatch,
            RouteDefaults routeDefaults
    ) {
        String resolved = firstText(
                batch.getTargetWarehouseName(),
                existingBatch == null ? null : existingBatch.getTargetWarehouseName()
        );
        if (!StringUtils.hasText(batch.getTargetWarehouseName())) {
            resolved = firstText(resolved, routeDefaults.targetWarehouseName);
        }
        return resolved;
    }

    private SaveBatchCommand toBatchCommand(
            PluginSyncCommand command,
            PluginSyncBatch batch,
            BatchRow existingBatch,
            InTransitForwarderCatalog.CanonicalForwarder forwarder
    ) {
        SaveBatchCommand result = new SaveBatchCommand();
        result.setOwnerUserId(command.getOwnerUserId());
        result.setOperatorUserId(command.getOperatorUserId());
        result.setBatchId(existingBatch == null ? null : existingBatch.getId());
        result.setRawForwarderName(forwarder.name());
        RouteDefaults routeDefaults = routeDefaults(command, batch);
        String transportMode = resolveTransportModeForSave(batch, existingBatch, routeDefaults);
        result.setTransportMode(transportMode);
        String targetStoreCode = resolveDestinationForSave(batch, existingBatch, routeDefaults);
        result.setTargetStoreCode(targetStoreCode);
        result.setTargetSiteCode(firstText(
                siteCodeFromDestination(targetStoreCode),
                existingBatch == null ? null : existingBatch.getTargetSiteCode()
        ));
        result.setTargetWarehouseName(resolveWarehouseForSave(batch, existingBatch, routeDefaults));
        result.setDepartureDate(batch.getDepartureDate() == null && existingBatch != null ? existingBatch.getDepartureDate() : batch.getDepartureDate());
        result.setTrackingNo(firstText(batch.getTrackingNo(), existingBatch == null ? null : existingBatch.getTrackingNo()));
        result.setContainerNo(firstText(batch.getContainerNo(), existingBatch == null ? null : existingBatch.getContainerNo()));
        result.setBatchReferenceNo(clean(batch.getBatchNo()));
        result.setExternalShipmentNo(firstText(batch.getExternalShipmentNo(), existingBatch == null ? null : existingBatch.getExternalShipmentNo()));
        result.setSourceCreatedAt(firstDateTime(batch.getSourceCreatedAt(), existingBatch == null ? null : existingBatch.getSourceCreatedAt()));
        result.setEstimatedDepartureAt(firstDateTime(batch.getEstimatedDepartureAt(), existingBatch == null ? null : existingBatch.getEstimatedDepartureAt()));
        applyEstimatedArrivalForPluginSync(result, batch, existingBatch, transportMode);
        result.setDeliveryAppointmentText(firstText(batch.getDeliveryAppointmentText(), existingBatch == null ? null : existingBatch.getDeliveryAppointmentText()));
        String batchStatus = firstText(
                resolveBatchStatus(command.getSourceSystem(), batch),
                existingBatch == null ? InTransitBatchStatus.DRAFT.code() : existingBatch.getBatchStatus()
        );
        boolean missingPackageChargeableWeight = hasPackageWithGoodsLinesMissingChargeable(
                command.getOwnerUserId(),
                batch,
                existingBatch
        );
        result.setBatchStatus(resolveBatchStatusForSave(result, batchStatus, missingPackageChargeableWeight));
        return result;
    }

    private void applyEstimatedArrivalForPluginSync(
            SaveBatchCommand command,
            PluginSyncBatch batch,
            BatchRow existingBatch,
            String transportMode
    ) {
        if (existingBatch != null
                && InTransitEstimatedArrivalSource.MANUAL.code().equals(existingBatch.getEstimatedArrivalSource())) {
            command.setEtaDate(existingBatch.getEtaDate());
            command.setEstimatedArrivalAt(existingBatch.getEstimatedArrivalAt());
            command.setEstimatedArrivalSource(existingBatch.getEstimatedArrivalSource());
            command.setEstimatedArrivalSourceDetail(existingBatch.getEstimatedArrivalSourceDetail());
            command.setEstimatedArrivalUpdatedAt(existingBatch.getEstimatedArrivalUpdatedAt());
            command.setEstimatedArrivalUpdatedBy(existingBatch.getEstimatedArrivalUpdatedBy());
            return;
        }
        LocalDate officialEtaDate = batch.getOfficialEtaDate();
        LocalDateTime parsedEstimatedArrivalAt = firstDateTime(batch.getEstimatedArrivalAt(), null);
        if (InTransitTransportMode.SEA.code().equals(transportMode)) {
            LocalDateTime estimatedArrivalAt = parsedEstimatedArrivalAt == null && officialEtaDate != null
                    ? officialEtaDate.atStartOfDay()
                    : parsedEstimatedArrivalAt;
            LocalDate etaDate = firstDate(
                    officialEtaDate,
                    estimatedArrivalAt == null ? null : estimatedArrivalAt.toLocalDate(),
                    existingBatch == null ? null : existingBatch.getEtaDate()
            );
            command.setEtaDate(etaDate);
            command.setEstimatedArrivalAt(firstValue(
                    estimatedArrivalAt,
                    existingBatch == null ? null : existingBatch.getEstimatedArrivalAt()
            ));
            if (estimatedArrivalAt != null || officialEtaDate != null) {
                command.setEstimatedArrivalSource(InTransitEstimatedArrivalSource.OFFICIAL.code());
                command.setEstimatedArrivalSourceDetail("海运官方预计到达时间");
            } else if (existingBatch != null) {
                command.setEstimatedArrivalSource(existingBatch.getEstimatedArrivalSource());
                command.setEstimatedArrivalSourceDetail(existingBatch.getEstimatedArrivalSourceDetail());
                command.setEstimatedArrivalUpdatedAt(existingBatch.getEstimatedArrivalUpdatedAt());
                command.setEstimatedArrivalUpdatedBy(existingBatch.getEstimatedArrivalUpdatedBy());
            }
            return;
        }
        command.setEtaDate(officialEtaDate == null && existingBatch != null ? existingBatch.getEtaDate() : officialEtaDate);
        command.setEstimatedArrivalAt(firstValue(
                parsedEstimatedArrivalAt,
                existingBatch == null ? null : existingBatch.getEstimatedArrivalAt()
        ));
        if (parsedEstimatedArrivalAt != null) {
            command.setEstimatedArrivalSource(InTransitEstimatedArrivalSource.PLUGIN_REPORTED.code());
            command.setEstimatedArrivalSourceDetail("插件采集预计到达时间");
        } else if (existingBatch != null) {
            command.setEstimatedArrivalSource(existingBatch.getEstimatedArrivalSource());
            command.setEstimatedArrivalSourceDetail(existingBatch.getEstimatedArrivalSourceDetail());
            command.setEstimatedArrivalUpdatedAt(existingBatch.getEstimatedArrivalUpdatedAt());
            command.setEstimatedArrivalUpdatedBy(existingBatch.getEstimatedArrivalUpdatedBy());
        }
    }

    private String resolveBatchStatusForSave(
            SaveBatchCommand command,
            String batchStatus,
            boolean missingPackageChargeableWeight
    ) {
        String resolvedStatus = firstText(batchStatus, InTransitBatchStatus.DRAFT.code());
        if (InTransitBatchStatus.DRAFT.code().equals(resolvedStatus)) {
            return resolvedStatus;
        }
        if (missingPackageChargeableWeight) {
            return InTransitBatchStatus.DRAFT.code();
        }
        if (!StringUtils.hasText(command.getRawForwarderName())
                || !StringUtils.hasText(command.getTransportMode())
                || !StringUtils.hasText(command.getTargetStoreCode())
                || !StringUtils.hasText(command.getTargetWarehouseName())) {
            return InTransitBatchStatus.DRAFT.code();
        }
        return resolvedStatus;
    }

    private SaveLineCommand toLineCommand(
            PluginSyncCommand command,
            Long batchId,
            PluginSyncBatch batch,
            PluginSyncPackage itemPackage,
            PluginSyncLine line,
            String barcode,
            String psku,
            LineRow existingLine
    ) {
        SaveLineCommand result = new SaveLineCommand();
        result.setOwnerUserId(command.getOwnerUserId());
        result.setOperatorUserId(command.getOperatorUserId());
        result.setBatchId(batchId);
        result.setLineId(existingLine == null ? null : existingLine.getId());
        result.setBoxNo(clean(itemPackage.getBoxNo()));
        result.setExternalBoxNo(clean(itemPackage.getExternalBoxNo()));
        result.setPackageTrackingNo(clean(itemPackage.getTrackingNo()));
        result.setPackageWeightKg(itemPackage.getWeightKg());
        result.setPackageLengthCm(itemPackage.getLengthCm());
        result.setPackageWidthCm(itemPackage.getWidthCm());
        result.setPackageHeightCm(itemPackage.getHeightCm());
        result.setPackageVolumeCbm(itemPackage.getVolumeCbm());
        result.setPackageVolumeWeightKg(itemPackage.getVolumeWeightKg());
        result.setPackageChargeableWeightKg(resolveChargeableWeight(itemPackage));
        result.setMeasuredWeightKg(itemPackage.getMeasuredWeightKg());
        result.setMeasuredLengthCm(itemPackage.getMeasuredLengthCm());
        result.setMeasuredWidthCm(itemPackage.getMeasuredWidthCm());
        result.setMeasuredHeightCm(itemPackage.getMeasuredHeightCm());
        result.setMeasuredVolumeCbm(itemPackage.getMeasuredVolumeCbm());
        result.setPackageStatus(clean(itemPackage.getPackageStatus()));
        result.setLogisticsStatus(clean(itemPackage.getLogisticsStatus()));
        result.setPackageSnapshotAuthoritative(true);
        LineScope lineScope = lineScope(line);
        result.setSku(clean(barcode));
        result.setMsku(clean(line.getMsku()));
        result.setPsku(clean(psku));
        result.setProductName(clean(line.getProductName()));
        result.setStoreCode(lineScope.storeCode);
        result.setSiteCode(lineScope.siteCode);
        result.setShippedQuantity(line.getShippedQuantity());
        result.setReceivedQuantity(line.getReceivedQuantity());
        result.setCartonCount(line.getCartonCount() == null ? 1 : line.getCartonCount());
        result.setUnitsPerCarton(line.getUnitsPerCarton() == null ? line.getShippedQuantity() : line.getUnitsPerCarton());
        result.setCartonWeightKg(line.getCartonWeightKg());
        result.setCartonVolumeCbm(line.getCartonVolumeCbm());
        return result;
    }

    private String siteCodeFromDestination(String destinationCode) {
        if ("RUH".equals(normalizeCode(destinationCode))) {
            return "SA";
        }
        if ("DB".equals(normalizeCode(destinationCode))) {
            return "AE";
        }
        return null;
    }

    private LineScope lineScope(PluginSyncLine line) {
        String storeCode = clean(line == null ? null : line.getStoreCode());
        if (!isNuonoStoreCode(storeCode)) {
            return LineScope.empty();
        }
        String siteCode = firstText(normalizeStoreSiteCode(line.getSiteCode()), inferSiteCodeFromStoreCode(storeCode));
        if (!StringUtils.hasText(siteCode)) {
            return LineScope.empty();
        }
        return new LineScope(storeCode.toUpperCase(Locale.ROOT), siteCode);
    }

    private boolean isNuonoStoreCode(String storeCode) {
        return StringUtils.hasText(storeCode) && storeCode.trim().toUpperCase(Locale.ROOT).matches("STR[A-Z0-9-]+");
    }

    private String normalizeStoreSiteCode(String siteCode) {
        String normalized = normalizeCode(siteCode);
        if ("SA".equals(normalized) || "AE".equals(normalized) || "EG".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String inferSiteCodeFromStoreCode(String storeCode) {
        if (!StringUtils.hasText(storeCode)) {
            return null;
        }
        String normalized = storeCode.trim().toUpperCase(Locale.ROOT);
        int marker = normalized.lastIndexOf("-N");
        if (marker < 0 || marker + 4 != normalized.length()) {
            return null;
        }
        return normalizeStoreSiteCode(normalized.substring(marker + 2));
    }

    private SavePackageCommand toPackageCommand(
            PluginSyncCommand command,
            Long batchId,
            PluginSyncPackage itemPackage
    ) {
        SavePackageCommand result = new SavePackageCommand();
        result.setOwnerUserId(command.getOwnerUserId());
        result.setOperatorUserId(command.getOperatorUserId());
        result.setBatchId(batchId);
        result.setBoxNo(clean(itemPackage.getBoxNo()));
        result.setExternalBoxNo(clean(itemPackage.getExternalBoxNo()));
        result.setPackageTrackingNo(clean(itemPackage.getTrackingNo()));
        result.setPackageWeightKg(itemPackage.getWeightKg());
        result.setPackageLengthCm(itemPackage.getLengthCm());
        result.setPackageWidthCm(itemPackage.getWidthCm());
        result.setPackageHeightCm(itemPackage.getHeightCm());
        result.setPackageVolumeCbm(itemPackage.getVolumeCbm());
        result.setPackageVolumeWeightKg(itemPackage.getVolumeWeightKg());
        result.setPackageChargeableWeightKg(resolveChargeableWeight(itemPackage));
        result.setMeasuredWeightKg(itemPackage.getMeasuredWeightKg());
        result.setMeasuredLengthCm(itemPackage.getMeasuredLengthCm());
        result.setMeasuredWidthCm(itemPackage.getMeasuredWidthCm());
        result.setMeasuredHeightCm(itemPackage.getMeasuredHeightCm());
        result.setMeasuredVolumeCbm(itemPackage.getMeasuredVolumeCbm());
        result.setPackageStatus(clean(itemPackage.getPackageStatus()));
        result.setLogisticsStatus(clean(itemPackage.getLogisticsStatus()));
        result.setPackageSnapshotAuthoritative(itemPackage.getLines().isEmpty());
        return result;
    }

    private void validateBatch(
            PluginSyncCommand command,
            PluginSyncBatch batch,
            BatchRow existingBatch,
            List<PluginSyncIssueView> issues
    ) {
        String batchNo = clean(batch.getBatchNo());
        RouteDefaults routeDefaults = routeDefaults(command, batch);
        String resolvedTransportMode = resolveTransportModeForSave(batch, existingBatch, routeDefaults);
        String resolvedDestination = resolveDestinationForSave(batch, existingBatch, routeDefaults);
        String resolvedWarehouse = resolveWarehouseForSave(batch, existingBatch, routeDefaults);
        if (StringUtils.hasText(batch.getTransportMode())) {
            if (!StringUtils.hasText(resolveTransportMode(batch.getTransportMode()))) {
                issues.add(PluginSyncIssueView.warning(
                        batchNo,
                        null,
                        null,
                        "transportMode",
                        "运输方式无法识别，提交时将按缺失运输方式保存。原始值：" + clean(batch.getTransportMode())
                ));
            }
        } else if (!StringUtils.hasText(resolvedTransportMode)) {
            issues.add(PluginSyncIssueView.warning(batchNo, null, null, "transportMode", "运输方式为空，将按草稿批次保存。"));
        }
        if (StringUtils.hasText(batch.getDestination())) {
            if (!StringUtils.hasText(resolveDestination(batchNo, batch.getDestination(), batch.getTargetWarehouseName()))) {
                issues.add(PluginSyncIssueView.warning(
                        batchNo,
                        null,
                        null,
                        "destination",
                        "目的地无法识别，提交时将按缺失目的地保存。原始值：" + clean(batch.getDestination())
                ));
            }
        } else if (!StringUtils.hasText(resolvedDestination)) {
            issues.add(PluginSyncIssueView.warning(batchNo, null, null, "destination", "目的地为空，后端会尝试从批次号或仓库推断。"));
        }
        if (!StringUtils.hasText(resolvedWarehouse)) {
            issues.add(PluginSyncIssueView.warning(batchNo, null, null, "targetWarehouseName", "目的仓为空，将按草稿批次保存。"));
        }
        String sourceBatchStatus = sourceBatchStatusText(batch);
        if (StringUtils.hasText(sourceBatchStatus) && !StringUtils.hasText(resolveBatchStatus(command.getSourceSystem(), batch))) {
            issues.add(PluginSyncIssueView.warning(
                    batchNo,
                    null,
                    null,
                    "batchStatus",
                    "批次状态无法识别，提交时将按草稿或既有状态保存。原始值：" + clean(sourceBatchStatus)
            ));
        }
        validateBatchDateTime(batchNo, "sourceCreatedAt", batch.getSourceCreatedAt(), issues);
        validateBatchDateTime(batchNo, "estimatedDepartureAt", batch.getEstimatedDepartureAt(), issues);
        validateBatchDateTime(batchNo, "estimatedArrivalAt", batch.getEstimatedArrivalAt(), issues);
    }

    private void validateLine(
            String batchNo,
            String boxNo,
            PluginSyncCommand command,
            PluginSyncBatch batch,
            PluginSyncLine line,
            List<PluginSyncIssueView> issues
    ) {
        String barcode = sourceBarcode(line);
        if (hasBarcodeConflict(line)) {
            issues.add(PluginSyncIssueView.error(
                    batchNo,
                    boxNo,
                    barcode,
                    "barcode",
                    "物流商品 barcode 与旧字段 sku 不一致，禁止猜测匹配商品。"
            ));
        }
        if (!StringUtils.hasText(barcode)) {
            String message = line != null && StringUtils.hasText(line.getPsku())
                    ? "物流商品 barcode 不能为空；禁止使用 psku 匹配商品。"
                    : "物流商品 barcode 不能为空。";
            issues.add(PluginSyncIssueView.error(batchNo, boxNo, null, "barcode", message));
        }
        if (line.getShippedQuantity() != null && line.getShippedQuantity() < 0) {
            issues.add(PluginSyncIssueView.error(batchNo, boxNo, barcode, "shippedQuantity", "发货数量不能为负数。"));
        }
        if (line.getReceivedQuantity() != null && line.getReceivedQuantity() < 0) {
            issues.add(PluginSyncIssueView.error(batchNo, boxNo, barcode, "receivedQuantity", "已入仓数量不能为负数。"));
        }
        if (line.getShippedQuantity() != null
                && line.getReceivedQuantity() != null
                && line.getReceivedQuantity() > line.getShippedQuantity()) {
            issues.add(PluginSyncIssueView.error(batchNo, boxNo, barcode, "receivedQuantity", "已入仓数量不能大于发货数量。"));
        }
    }

    private String sourceBarcode(PluginSyncLine line) {
        return line == null ? null : firstText(line.getBarcode(), line.getSku());
    }

    private boolean hasBarcodeConflict(PluginSyncLine line) {
        return line != null
                && StringUtils.hasText(line.getBarcode())
                && StringUtils.hasText(line.getSku())
                && !sameIdentifier(line.getBarcode(), line.getSku());
    }

    private String resolvePartnerSkuFromBarcode(
            Long ownerUserId,
            String batchNo,
            String boxNo,
            String barcode,
            List<PluginSyncIssueView> issues
    ) {
        String partnerSku = mapper.selectPartnerSkuByBarcode(ownerUserId, clean(barcode));
        if (!StringUtils.hasText(partnerSku)) {
            issues.add(PluginSyncIssueView.error(
                    batchNo,
                    boxNo,
                    barcode,
                    "barcode",
                    "物流商品 barcode 未匹配到系统商品：" + clean(barcode)
            ));
        }
        return partnerSku;
    }

    private String requirePartnerSkuFromBarcode(Long ownerUserId, String barcode) {
        String partnerSku = mapper.selectPartnerSkuByBarcode(ownerUserId, clean(barcode));
        if (!StringUtils.hasText(partnerSku)) {
            throw new IllegalStateException("物流商品 barcode 未匹配到系统商品：" + clean(barcode));
        }
        return partnerSku;
    }

    private LineRow selectExistingLineForResolvedPsku(
            Long ownerUserId,
            Long batchId,
            String boxNo,
            String resolvedPsku,
            String barcode
    ) {
        LineRow existingLine = mapper.selectLineByBoxNoAndPsku(ownerUserId, batchId, boxNo, resolvedPsku);
        if (existingLine != null || sameIdentifier(resolvedPsku, barcode)) {
            return existingLine;
        }
        return mapper.selectLineByBoxNoAndPsku(ownerUserId, batchId, boxNo, barcode);
    }

    private ParsedNode parseNode(String sourceSystem, String batchNo, PluginSyncNode node) {
        return parseNode(sourceSystem, batchNo, node, null);
    }

    private ParsedNode parseNode(String sourceSystem, String batchNo, PluginSyncNode node, List<PluginSyncIssueView> issues) {
        if (node == null) {
            return null;
        }
        if (isPlaceholderDateTime(node.getNodeTime())) {
            return null;
        }
        String description = clean(node.getDescription());
        String nodeStatus = resolveNodeStatus(sourceSystem, node.getNodeStatus(), description);
        if (!StringUtils.hasText(nodeStatus)) {
            if (issues != null) {
                issues.add(PluginSyncIssueView.warning(
                        batchNo,
                        null,
                        null,
                        "nodeStatus",
                        "物流节点状态无法识别，已跳过该节点。原始值：" + clean(node.getNodeStatus())
                ));
            }
        }
        LocalDateTime happenedAt = null;
        try {
            happenedAt = parseNodeTime(node.getNodeTime());
        } catch (IllegalArgumentException exception) {
            if (issues != null) {
                issues.add(PluginSyncIssueView.warning(
                        batchNo,
                        null,
                        null,
                        "nodeTime",
                        exception.getMessage() + " 已跳过该节点。原始值：" + clean(node.getNodeTime())
                ));
            }
        }
        if (!StringUtils.hasText(nodeStatus) || happenedAt == null) {
            return null;
        }
        return new ParsedNode(InTransitNodeStatus.normalizeForPersistence(nodeStatus, description), happenedAt, description);
    }

    private InTransitForwarderCatalog.CanonicalForwarder resolveForwarder(PluginSyncCommand command) {
        return resolveForwarder(command.getSourceSystem(), command.getForwarderName());
    }

    private InTransitForwarderCatalog.CanonicalForwarder resolveForwarder(String sourceSystemValue, String forwarderName) {
        String sourceSystem = normalizeCode(sourceSystemValue);
        String fallbackName = null;
        if ("CHIC".equals(sourceSystem)) {
            fallbackName = "启客";
        } else if ("ET".equals(sourceSystem) || "YITONG".equals(sourceSystem)) {
            fallbackName = "易通";
        } else if ("YITE".equals(sourceSystem)) {
            fallbackName = "义特";
        }
        return InTransitForwarderCatalog.require(sourceSystem, firstText(forwarderName, fallbackName));
    }

    private void addEtPlanBox(EtBoxSyncPlanView view, List<EtBoxSyncPlanBoxView> boxes, EtBoxSyncPlanBoxView box) {
        boxes.add(box);
        view.setBoxCount(view.getBoxCount() + 1);
        if ("SKIP".equals(box.getAction())) {
            view.setSkipCount(view.getSkipCount() + 1);
        } else if ("FETCH_BOX_SPEC".equals(box.getAction())) {
            view.setFetchBoxSpecCount(view.getFetchBoxSpecCount() + 1);
        } else if ("FETCH_BOX_LINES".equals(box.getAction())) {
            view.setFetchBoxLinesCount(view.getFetchBoxLinesCount() + 1);
        } else if ("FETCH_ALL".equals(box.getAction())) {
            view.setFetchAllCount(view.getFetchAllCount() + 1);
        }
    }

    private void setEtPlanAction(EtBoxSyncPlanBoxView box, String action, String reason) {
        box.setAction(action);
        box.setReason(reason);
    }

    private String resolveEtPlanAction(EtBoxSyncStateRow state) {
        if (state == null || !state.isPackageExists()) {
            return "FETCH_ALL";
        }
        boolean hasSpec = state.isPackageSpecComplete();
        boolean hasLines = state.getLineCount() > 0;
        if (hasSpec && hasLines) {
            return "SKIP";
        }
        if (!hasSpec && hasLines) {
            return "FETCH_BOX_SPEC";
        }
        if (hasSpec) {
            return "FETCH_BOX_LINES";
        }
        return "FETCH_ALL";
    }

    private String resolveEtPlanReason(EtBoxSyncStateRow state) {
        if (state == null || !state.isPackageExists()) {
            return "new_box";
        }
        boolean hasSpec = state.isPackageSpecComplete();
        boolean hasLines = state.getLineCount() > 0;
        if (hasSpec && hasLines) {
            return "known_complete";
        }
        if (!hasSpec && hasLines) {
            return "missing_package";
        }
        if (hasSpec) {
            return "missing_lines";
        }
        return "missing_package_and_lines";
    }

    private LocalDateTime parseNodeTime(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("物流节点时间不能为空。");
        }
        String text = value.trim();
        if (isPlaceholderDateTime(text)) {
            throw new IllegalArgumentException("物流节点时间不能为空。");
        }
        try {
            return LocalDateTime.parse(text.contains("T") ? text : text.replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("物流节点时间格式应为 yyyy-MM-dd HH:mm:ss。");
        }
    }

    private void validateBatchDateTime(
            String batchNo,
            String fieldName,
            String value,
            List<PluginSyncIssueView> issues
    ) {
        if (!StringUtils.hasText(value) || isPlaceholderDateTime(value)) {
            return;
        }
        try {
            parseFlexibleDateTime(value);
        } catch (IllegalArgumentException exception) {
            issues.add(PluginSyncIssueView.warning(
                    batchNo,
                    null,
                    null,
                    fieldName,
                    exception.getMessage() + " 提交时将跳过该时间。原始值：" + clean(value)
            ));
        }
    }

    private LocalDateTime firstDateTime(String value, LocalDateTime existing) {
        if (!StringUtils.hasText(value) || isPlaceholderDateTime(value)) {
            return existing;
        }
        try {
            return parseFlexibleDateTime(value);
        } catch (IllegalArgumentException exception) {
            return existing;
        }
    }

    private LocalDateTime parseFlexibleDateTime(String value) {
        String text = value.trim();
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay();
            }
            return LocalDateTime.parse(text.contains("T") ? text : text.replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("批次时间格式应为 yyyy-MM-dd HH:mm:ss。");
        }
    }

    private boolean isPlaceholderDateTime(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith("1970-01-01");
    }

    private Long requireOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少老板账号范围。");
        }
        return ownerUserId;
    }

    private String pluginSyncBatchLockName(Long ownerUserId, String batchNo) {
        String lockKey = ownerUserId + ":" + normalizeCode(batchNo);
        return "itps:" + sha256Hex(lockKey).substring(0, 40);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                int unsigned = item & 0xff;
                if (unsigned < 16) {
                    result.append('0');
                }
                result.append(Integer.toHexString(unsigned));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用。", exception);
        }
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private boolean sameIdentifier(String left, String right) {
        String normalizedLeft = normalizeCode(left);
        String normalizedRight = normalizeCode(right);
        return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
    }

    private String resolveTransportMode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.contains("海运")) {
            return InTransitTransportMode.SEA.code();
        }
        if (trimmed.contains("空运")) {
            return InTransitTransportMode.AIR.code();
        }
        if ("海运".equals(trimmed)) {
            return InTransitTransportMode.SEA.code();
        }
        if ("空运".equals(trimmed)) {
            return InTransitTransportMode.AIR.code();
        }
        try {
            return InTransitTransportMode.require(trimmed).code();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String resolveDestination(String batchNo, String destination, String targetWarehouseName) {
        if (StringUtils.hasText(destination)) {
            try {
                return InTransitDestination.require(destination).code();
            } catch (IllegalArgumentException exception) {
                InTransitDestination inferred = InTransitDestination.infer(batchNo, targetWarehouseName, destination);
                return inferred == null ? null : inferred.code();
            }
        }
        InTransitDestination inferred = InTransitDestination.infer(batchNo, targetWarehouseName);
        return inferred == null ? null : inferred.code();
    }

    private String resolveBatchStatus(String sourceSystem, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (InTransitNodeStatus.isWarehouseArrivalMarker(value)) {
            return InTransitBatchStatus.WAREHOUSE_RECEIVED.code();
        }
        if (isForwarderWarehouseActiveTrackingStatus(sourceSystem, value)) {
            return InTransitBatchStatus.IN_TRANSIT.code();
        }
        try {
            return InTransitBatchStatus.require(value).code();
        } catch (IllegalArgumentException exception) {
            String text = value.trim().toUpperCase(Locale.ROOT);
            if (text.contains("取消") || text.contains("CANCEL")) {
                return InTransitBatchStatus.CANCELLED.code();
            }
            if (text.contains("异常") || text.contains("问题") || text.contains("EXCEPTION") || text.contains("ERROR")) {
                return InTransitBatchStatus.EXCEPTION.code();
            }
            if (text.contains("入仓") || text.contains("签收") || text.contains("妥投") || text.contains("DELIVERED") || text.contains("RECEIVED")) {
                return InTransitBatchStatus.WAREHOUSE_RECEIVED.code();
            }
            if (text.contains("派送") || text.contains("派件") || text.contains("OUT FOR DELIVERY")) {
                return InTransitBatchStatus.DELIVERING.code();
            }
            if (text.contains("清关") || text.contains("CUSTOMS")) {
                return InTransitBatchStatus.CUSTOMS_CLEARANCE.code();
            }
            if (text.contains("转运") || text.contains("运输中") || text.contains("在途") || text.contains("IN TRANSIT")) {
                return InTransitBatchStatus.IN_TRANSIT.code();
            }
            if (text.contains("已收货") || text.contains("PICKED") || text.contains("PICKUP")) {
                return InTransitBatchStatus.SHIPPED.code();
            }
            if (text.contains("已下单") || text.contains("READY") || text.contains("CREATE")) {
                return InTransitBatchStatus.PENDING_SHIPMENT.code();
            }
            return null;
        }
    }

    private String sourceBatchStatusText(PluginSyncBatch batch) {
        return firstText(firstText(batch.getSourceStatus(), batch.getRawStatus()), firstText(batch.getStatus(), batch.getBatchStatus()));
    }

    private String resolveBatchStatus(String sourceSystem, PluginSyncBatch batch) {
        if (batch == null) {
            return null;
        }
        for (String value : batchStatusCandidates(batch)) {
            String resolved = resolveBatchStatus(sourceSystem, value);
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    private List<String> batchStatusCandidates(PluginSyncBatch batch) {
        List<String> result = new ArrayList<>();
        addTextCandidate(result, batch.getSourceStatus());
        addTextCandidate(result, batch.getRawStatus());
        addTextCandidate(result, batch.getStatus());
        addTextCandidate(result, batch.getBatchStatus());
        return result;
    }

    private void addTextCandidate(List<String> result, String value) {
        String text = clean(value);
        if (StringUtils.hasText(text) && !result.contains(text)) {
            result.add(text);
        }
    }

    private BigDecimal resolveChargeableWeight(PluginSyncPackage itemPackage) {
        return resolveChargeableWeight(
                itemPackage.getChargeableWeightKg(),
                itemPackage.getWeightKg(),
                itemPackage.getVolumeWeightKg()
        );
    }

    private BigDecimal resolveChargeableWeight(
            BigDecimal explicitChargeableWeightKg,
            BigDecimal weightKg,
            BigDecimal volumeWeightKg
    ) {
        if (explicitChargeableWeightKg != null) {
            return explicitChargeableWeightKg;
        }
        if (weightKg == null) {
            return volumeWeightKg;
        }
        if (volumeWeightKg == null) {
            return weightKg;
        }
        return weightKg.compareTo(volumeWeightKg) >= 0 ? weightKg : volumeWeightKg;
    }

    private static final class RouteDefaults {
        private static final RouteDefaults EMPTY = new RouteDefaults(null, null, null);

        private final String transportMode;
        private final String targetStoreCode;
        private final String targetWarehouseName;

        private RouteDefaults(String transportMode, String targetStoreCode, String targetWarehouseName) {
            this.transportMode = transportMode;
            this.targetStoreCode = targetStoreCode;
            this.targetWarehouseName = targetWarehouseName;
        }

        private static RouteDefaults empty() {
            return EMPTY;
        }
    }

    private static final class LineScope {
        private final String storeCode;
        private final String siteCode;

        private LineScope(String storeCode, String siteCode) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }

        private static LineScope empty() {
            return new LineScope(null, null);
        }
    }

    private boolean isCancelledBatch(PluginSyncBatch batch) {
        if (batch == null) {
            return false;
        }
        return isCancelledStatus(batch.getBatchStatus())
                || isCancelledStatus(batch.getSourceStatus())
                || isCancelledStatus(batch.getRawStatus())
                || isCancelledStatus(batch.getStatus());
    }

    private boolean isCancelledStatus(String value) {
        String text = clean(value);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        return "13".equals(normalized)
                || "CANCELLED".equals(normalized)
                || "CANCELED".equals(normalized)
                || normalized.contains("CANCEL")
                || text.contains("取消");
    }

    private String resolveNodeStatus(String sourceSystem, String value, String description) {
        if (!StringUtils.hasText(value)) {
            return InTransitNodeStatus.isWarehouseArrivalMarker(description)
                    ? InTransitNodeStatus.WAREHOUSE_RECEIVED.code()
                    : null;
        }
        if (InTransitNodeStatus.isWarehouseArrivalMarker(value)
                || InTransitNodeStatus.isWarehouseArrivalMarker(description)) {
            return InTransitNodeStatus.WAREHOUSE_RECEIVED.code();
        }
        if (isForwarderWarehouseActiveTrackingStatus(sourceSystem, value, description)) {
            return InTransitNodeStatus.IN_TRANSIT.code();
        }
        try {
            return InTransitNodeStatus.require(value).code();
        } catch (IllegalArgumentException exception) {
            String text = value.trim().toUpperCase(Locale.ROOT);
            if (text.contains("取消") || text.contains("CANCEL")) {
                return InTransitNodeStatus.CANCELLED.code();
            }
            if (text.contains("异常") || text.contains("失败") || text.contains("EXCEPTION") || text.contains("ERROR")) {
                return InTransitNodeStatus.EXCEPTION.code();
            }
            if (text.contains("入仓") || text.contains("签收") || text.contains("妥投") || text.contains("WAREHOUSE") || text.contains("RECEIVED") || text.contains("DELIVERED")) {
                return InTransitNodeStatus.WAREHOUSE_RECEIVED.code();
            }
            if (text.contains("派送") || text.contains("派件") || text.contains("DELIVERING") || text.contains("OUT FOR DELIVERY")) {
                return InTransitNodeStatus.DELIVERING.code();
            }
            if (text.contains("放行") || text.contains("RELEASED")) {
                return InTransitNodeStatus.CUSTOMS_RELEASED.code();
            }
            if (text.contains("清关") || text.contains("CUSTOMS")) {
                return InTransitNodeStatus.CUSTOMS_CLEARANCE.code();
            }
            if (text.contains("到港") || text.contains("抵达") || text.contains("ARRIVED") || text.contains("ARRIVAL")) {
                return InTransitNodeStatus.ARRIVED_PORT.code();
            }
            if (text.contains("转运") || text.contains("预配航期") || text.contains("ETD") || text.contains("IN TRANSIT")) {
                return InTransitNodeStatus.IN_TRANSIT.code();
            }
            if (text.contains("发往海外") || text.contains("发往") || text.contains("起飞") || text.contains("离港") || text.contains("发出") || text.contains("SHIPED") || text.contains("SHIPPED") || text.contains("DEPART") || text.contains("ON THE WAY")) {
                return InTransitNodeStatus.DEPARTED_ORIGIN.code();
            }
            if (text.contains("国内收货") || text.contains("收货") || text.contains("揽收") || text.contains("交货代") || text.contains("HAND") || text.contains("PICKED") || text.contains("PICKUP")) {
                return InTransitNodeStatus.HANDED_TO_FORWARDER.code();
            }
            if (text.contains("已下单") || text.contains("创建") || text.contains("CREATED") || text.contains("SHIPMENT.CREATE")) {
                return InTransitNodeStatus.CREATED.code();
            }
            return null;
        }
    }

    private boolean isForwarderWarehouseActiveTrackingStatus(String sourceSystem, String... values) {
        String normalizedSourceSystem = normalizeCode(sourceSystem);
        boolean etWarehouseCodeMeansForwarderWarehouse =
                "ET".equals(normalizedSourceSystem) || "YITONG".equals(normalizedSourceSystem);
        for (String value : values) {
            if (etWarehouseCodeMeansForwarderWarehouse && isWarehouseReceivedCode(value)) {
                return true;
            }
            if (isForwarderOverseasWarehouseText(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWarehouseReceivedCode(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String text = value.trim().toUpperCase(Locale.ROOT);
        return InTransitBatchStatus.WAREHOUSE_RECEIVED.code().equals(text.toLowerCase(Locale.ROOT))
                || InTransitNodeStatus.WAREHOUSE_RECEIVED.code().equals(text.toLowerCase(Locale.ROOT));
    }

    private boolean isForwarderOverseasWarehouseText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String text = value.trim().toUpperCase(Locale.ROOT);
        boolean forwarderWarehouse = text.contains("海外仓") || text.contains("ET仓") || text.contains("ETRUH");
        if (!forwarderWarehouse) {
            return false;
        }
        return text.contains("入仓")
                || text.contains("入库")
                || text.contains("入海外仓")
                || text.contains("到达")
                || text.contains("抵达")
                || text.contains("到仓")
                || text.contains("签收")
                || text.contains("提回")
                || text.contains("待拆柜")
                || text.contains("待派送")
                || text.contains("可约仓")
                || text.contains("WAREHOUSE")
                || text.contains("RECEIVED")
                || text.contains("DELIVERED")
                || text.contains("ARRIVED");
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : clean(second);
    }

    private static LocalDate firstDate(LocalDate first, LocalDate second, LocalDate third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private static <T> T firstValue(T first, T second) {
        return first == null ? second : first;
    }

    private static final class ParsedNode {
        private final String nodeStatus;
        private final LocalDateTime nodeHappenedAt;
        private final String description;

        private ParsedNode(String nodeStatus, LocalDateTime nodeHappenedAt, String description) {
            this.nodeStatus = nodeStatus;
            this.nodeHappenedAt = nodeHappenedAt;
            this.description = description;
        }
    }
}
