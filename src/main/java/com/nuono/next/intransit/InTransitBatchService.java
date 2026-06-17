package com.nuono.next.intransit;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.DeleteLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.DeleteNodeCommand;
import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SavePackageCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchAggregateRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchLatestNodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchListView;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.LineListView;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.LineView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeListView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.NodeView;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.InTransitForwarderCommands.ResolveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderResolveView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InTransitBatchService {

    private static final int DEFAULT_BATCH_LIST_PAGE_SIZE = 20;
    private static final int MAX_BATCH_LIST_PAGE_SIZE = 100;

    private final InTransitGoodsMapper mapper;
    private final InTransitForwarderService forwarderService;
    private final InTransitOperationAuditService auditService;

    public InTransitBatchService(
            InTransitGoodsMapper mapper,
            InTransitForwarderService forwarderService,
            InTransitOperationAuditService auditService
    ) {
        this.mapper = mapper;
        this.forwarderService = forwarderService;
        this.auditService = auditService;
    }

    public BatchListView listBatches(InTransitBatchQuery query) {
        InTransitBatchQuery resolved = query == null ? new InTransitBatchQuery() : query;
        requireOwnerUserId(resolved.getOwnerUserId());
        if (StringUtils.hasText(resolved.getTransportMode())) {
            resolved.setTransportMode(InTransitTransportMode.require(resolved.getTransportMode()).code());
        }
        if (StringUtils.hasText(resolved.getBatchStatus())) {
            resolved.setBatchStatus(InTransitBatchStatus.require(resolved.getBatchStatus()).code());
        }
        if (StringUtils.hasText(resolved.getTargetStoreCode())) {
            resolved.setTargetStoreCode(InTransitDestination.require(resolved.getTargetStoreCode()).code());
        }
        normalizeBatchListPage(resolved);
        normalizeBatchListSort(resolved);
        int totalCount = mapper.countBatches(resolved);
        BatchListView view = new BatchListView();
        view.setTotalCount(totalCount);
        view.setPage(resolved.getPage());
        view.setPageSize(resolved.getPageSize());
        view.setItems(mapper.listBatches(resolved).stream()
                .map(BatchView::from)
                .collect(Collectors.toList()));
        return view;
    }

    public BatchView getBatch(Long ownerUserId, Long batchId) {
        requireOwnerUserId(ownerUserId);
        if (batchId == null || batchId <= 0) {
            throw new IllegalArgumentException("在途批次不存在。");
        }
        BatchRow row = mapper.selectBatchById(ownerUserId, batchId);
        if (row == null) {
            throw new IllegalArgumentException("在途批次不存在。");
        }
        return BatchView.from(row);
    }

    public BatchView saveBatch(SaveBatchCommand command) {
        SaveBatchCommand resolved = command == null ? new SaveBatchCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long operatorUserId = resolved.getOperatorUserId();
        BatchRow existingBatch = resolved.getBatchId() == null ? null : requireBatch(ownerUserId, resolved.getBatchId());
        String batchStatus = StringUtils.hasText(resolved.getBatchStatus())
                ? InTransitBatchStatus.require(resolved.getBatchStatus()).code()
                : existingBatch == null
                ? InTransitBatchStatus.DRAFT.code()
                : firstText(existingBatch.getBatchStatus(), InTransitBatchStatus.DRAFT.code());
        if (existingBatch != null && !InTransitBatchStatus.DRAFT.code().equals(batchStatus)) {
            mergeExistingBatchFieldsForTracking(resolved, existingBatch);
        }
        String transportMode = StringUtils.hasText(resolved.getTransportMode())
                ? InTransitTransportMode.require(resolved.getTransportMode()).code()
                : null;
        String destinationCode = resolveDestinationCode(resolved);
        resolved.setTargetStoreCode(destinationCode);
        ForwarderResolveView forwarder = resolveForwarder(ownerUserId, resolved);
        List<String> missingFields = missingFields(resolved, forwarder, transportMode, destinationCode);
        if (!InTransitBatchStatus.DRAFT.code().equals(batchStatus) && !missingFields.isEmpty()) {
            throw new IllegalStateException("在途批次进入跟踪前需补齐：" + String.join(",", missingFields) + "。");
        }

        BatchRow row = new BatchRow();
        row.setId(resolved.getBatchId() == null ? mapper.nextBatchId() : resolved.getBatchId());
        row.setOwnerUserId(ownerUserId);
        row.setStandardForwarderId(forwarder == null ? resolved.getStandardForwarderId() : forwarder.getStandardForwarderId());
        row.setStandardForwarderCode(forwarder == null ? null : forwarder.getStandardForwarderCode());
        row.setStandardForwarderName(forwarder == null ? null : forwarder.getStandardForwarderName());
        row.setRawForwarderName(canonicalRawForwarderName(forwarder, resolved));
        row.setNormalizedRawForwarderName(forwarder == null ? null : forwarder.getNormalizedRawForwarderName());
        row.setForwarderQualityStatus(forwarderQualityStatus(forwarder, resolved));
        row.setTransportMode(transportMode);
        row.setBatchStatus(batchStatus);
        row.setTargetStoreCode(destinationCode);
        row.setTargetSiteCode(clean(resolved.getTargetSiteCode()));
        row.setTargetWarehouseName(clean(resolved.getTargetWarehouseName()));
        row.setDepartureDate(resolved.getDepartureDate());
        row.setEtaDate(resolved.getEtaDate());
        row.setTrackingNo(clean(resolved.getTrackingNo()));
        row.setContainerNo(clean(resolved.getContainerNo()));
        row.setBatchReferenceNo(clean(resolved.getBatchReferenceNo()));
        row.setExternalShipmentNo(clean(resolved.getExternalShipmentNo()));
        row.setSourceCreatedAt(resolved.getSourceCreatedAt());
        row.setEstimatedDepartureAt(resolved.getEstimatedDepartureAt());
        row.setEstimatedArrivalAt(resolved.getEstimatedArrivalAt());
        row.setDeliveryAppointmentText(clean(resolved.getDeliveryAppointmentText()));
        row.setMissingFieldsJson(InTransitBatchRecords.toMissingFieldsJson(missingFields));
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);
        boolean created = resolved.getBatchId() == null;
        if (created) {
            mapper.insertBatch(row);
        } else {
            mapper.updateBatch(row);
        }
        audit(
                ownerUserId,
                operatorUserId,
                created ? "batch_created" : "batch_updated",
                "batch",
                row.getId(),
                row.getId(),
                row.getTargetStoreCode(),
                row.getTargetSiteCode(),
                created ? "在途批次已创建。" : "在途批次已更新。",
                detail("batchStatus", row.getBatchStatus(), "transportMode", row.getTransportMode())
        );
        return getBatch(ownerUserId, row.getId());
    }

    private void mergeExistingBatchFieldsForTracking(SaveBatchCommand command, BatchRow existing) {
        if (command.getStandardForwarderId() == null && !StringUtils.hasText(command.getRawForwarderName())) {
            command.setStandardForwarderId(existing.getStandardForwarderId());
            command.setRawForwarderName(existing.getRawForwarderName());
        }
        if (!StringUtils.hasText(command.getTransportMode())) {
            command.setTransportMode(existing.getTransportMode());
        }
        if (!StringUtils.hasText(command.getTargetStoreCode())) {
            command.setTargetStoreCode(existing.getTargetStoreCode());
        }
        if (!StringUtils.hasText(command.getTargetSiteCode())) {
            command.setTargetSiteCode(existing.getTargetSiteCode());
        }
        if (!StringUtils.hasText(command.getTargetWarehouseName())) {
            command.setTargetWarehouseName(existing.getTargetWarehouseName());
        }
        if (command.getDepartureDate() == null) {
            command.setDepartureDate(existing.getDepartureDate());
        }
        if (command.getEtaDate() == null) {
            command.setEtaDate(existing.getEtaDate());
        }
        if (!StringUtils.hasText(command.getTrackingNo())) {
            command.setTrackingNo(existing.getTrackingNo());
        }
        if (!StringUtils.hasText(command.getContainerNo())) {
            command.setContainerNo(existing.getContainerNo());
        }
        if (!StringUtils.hasText(command.getBatchReferenceNo())) {
            command.setBatchReferenceNo(existing.getBatchReferenceNo());
        }
        if (!StringUtils.hasText(command.getExternalShipmentNo())) {
            command.setExternalShipmentNo(existing.getExternalShipmentNo());
        }
        if (command.getSourceCreatedAt() == null) {
            command.setSourceCreatedAt(existing.getSourceCreatedAt());
        }
        if (command.getEstimatedDepartureAt() == null) {
            command.setEstimatedDepartureAt(existing.getEstimatedDepartureAt());
        }
        if (command.getEstimatedArrivalAt() == null) {
            command.setEstimatedArrivalAt(existing.getEstimatedArrivalAt());
        }
        if (!StringUtils.hasText(command.getDeliveryAppointmentText())) {
            command.setDeliveryAppointmentText(existing.getDeliveryAppointmentText());
        }
    }

    public LineListView listLines(Long ownerUserId, Long batchId) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        requireBatch(resolvedOwnerUserId, batchId);
        LineListView view = new LineListView();
        view.setItems(mapper.listLines(resolvedOwnerUserId, batchId).stream()
                .map(LineView::from)
                .collect(Collectors.toList()));
        return view;
    }

    public LineView saveLine(SaveLineCommand command) {
        SaveLineCommand resolved = command == null ? new SaveLineCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long batchId = requirePositiveId(resolved.getBatchId(), "在途批次不存在。");
        Long operatorUserId = resolved.getOperatorUserId();
        BatchRow batch = requireBatch(ownerUserId, batchId);
        if (resolved.getLineId() != null) {
            requireLine(ownerUserId, batchId, resolved.getLineId());
        }
        if (!StringUtils.hasText(resolved.getBoxNo())) {
            throw new IllegalArgumentException("箱号不能为空。");
        }
        if (!StringUtils.hasText(resolved.getPsku())) {
            throw new IllegalArgumentException("PSKU不能为空。");
        }

        Integer shippedQuantity = nonNegativeOrZero(resolved.getShippedQuantity(), "发货数量不能为负数。");
        Integer receivedQuantity = nonNegativeOrZero(resolved.getReceivedQuantity(), "已入仓数量不能为负数。");
        if (receivedQuantity > shippedQuantity) {
            throw new IllegalStateException("已入仓数量不能大于发货数量。");
        }
        assertNonNegative(resolved.getCartonCount(), "箱数不能为负数。");
        assertNonNegative(resolved.getUnitsPerCarton(), "单箱数量不能为负数。");
        assertNonNegative(resolved.getCartonWeightKg(), "单箱重量不能为负数。");
        assertNonNegative(resolved.getCartonVolumeCbm(), "单箱体积不能为负数。");
        assertNonNegative(resolved.getPackageWeightKg(), "箱子重量不能为负数。");
        assertNonNegative(resolved.getPackageLengthCm(), "箱子长度不能为负数。");
        assertNonNegative(resolved.getPackageWidthCm(), "箱子宽度不能为负数。");
        assertNonNegative(resolved.getPackageHeightCm(), "箱子高度不能为负数。");
        assertNonNegative(resolved.getPackageVolumeCbm(), "箱子体积不能为负数。");
        assertNonNegative(resolved.getPackageVolumeWeightKg(), "箱子体积重量不能为负数。");
        assertNonNegative(resolved.getPackageChargeableWeightKg(), "箱子计费重量不能为负数。");
        assertNonNegative(resolved.getMeasuredWeightKg(), "仓库测量箱子重量不能为负数。");
        assertNonNegative(resolved.getMeasuredLengthCm(), "仓库测量箱子长度不能为负数。");
        assertNonNegative(resolved.getMeasuredWidthCm(), "仓库测量箱子宽度不能为负数。");
        assertNonNegative(resolved.getMeasuredHeightCm(), "仓库测量箱子高度不能为负数。");
        assertNonNegative(resolved.getMeasuredVolumeCbm(), "仓库测量箱子体积不能为负数。");
        PackageRow packageRow = resolvePackage(
                ownerUserId,
                batchId,
                clean(resolved.getBoxNo()),
                clean(resolved.getExternalBoxNo()),
                clean(resolved.getPackageTrackingNo()),
                resolved.getPackageWeightKg(),
                resolved.getPackageLengthCm(),
                resolved.getPackageWidthCm(),
                resolved.getPackageHeightCm(),
                resolved.getPackageVolumeCbm(),
                resolved.getPackageVolumeWeightKg(),
                resolved.getPackageChargeableWeightKg(),
                resolved.getMeasuredWeightKg(),
                resolved.getMeasuredLengthCm(),
                resolved.getMeasuredWidthCm(),
                resolved.getMeasuredHeightCm(),
                resolved.getMeasuredVolumeCbm(),
                clean(resolved.getPackageStatus()),
                clean(resolved.getLogisticsStatus()),
                resolved.isPackageSnapshotAuthoritative(),
                operatorUserId
        );

        LineRow row = new LineRow();
        row.setId(resolved.getLineId() == null ? mapper.nextLineId() : resolved.getLineId());
        row.setOwnerUserId(ownerUserId);
        row.setBatchId(batchId);
        row.setPackageId(packageRow == null ? null : packageRow.getId());
        row.setBoxNo(packageRow == null ? null : packageRow.getBoxNo());
        row.setSku(clean(resolved.getSku()));
        row.setMsku(clean(resolved.getMsku()));
        row.setPsku(clean(resolved.getPsku()));
        row.setProductName(clean(resolved.getProductName()));
        row.setStoreCode(clean(resolved.getStoreCode()));
        row.setSiteCode(clean(resolved.getSiteCode()));
        row.setShippedQuantity(shippedQuantity);
        row.setReceivedQuantity(receivedQuantity);
        row.setRemainingQuantity(shippedQuantity - receivedQuantity);
        row.setCartonCount(resolved.getCartonCount());
        row.setUnitsPerCarton(resolved.getUnitsPerCarton());
        row.setCartonWeightKg(resolved.getCartonWeightKg());
        row.setCartonVolumeCbm(resolved.getCartonVolumeCbm());
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);

        boolean created = resolved.getLineId() == null;
        if (created) {
            mapper.insertLine(row);
        } else {
            mapper.updateLine(row);
        }
        refreshBatchAggregate(ownerUserId, batchId);
        audit(
                ownerUserId,
                operatorUserId,
                created ? "line_created" : "line_updated",
                "line",
                row.getId(),
                batchId,
                firstText(row.getStoreCode(), batch.getTargetStoreCode()),
                firstText(row.getSiteCode(), batch.getTargetSiteCode()),
                created ? "在途商品明细已创建。" : "在途商品明细已更新。",
                detail("psku", row.getPsku(), "sourceSku", row.getSku(), "boxNo", row.getBoxNo(), "shippedQuantity", row.getShippedQuantity(), "receivedQuantity", row.getReceivedQuantity())
        );
        return LineView.from(requireLine(ownerUserId, batchId, row.getId()));
    }

    public void savePackage(SavePackageCommand command) {
        SavePackageCommand resolved = command == null ? new SavePackageCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long batchId = requirePositiveId(resolved.getBatchId(), "在途批次不存在。");
        Long operatorUserId = resolved.getOperatorUserId();
        BatchRow batch = requireBatch(ownerUserId, batchId);
        if (!StringUtils.hasText(resolved.getBoxNo())) {
            throw new IllegalArgumentException("箱号不能为空。");
        }
        assertNonNegative(resolved.getPackageWeightKg(), "箱子重量不能为负数。");
        assertNonNegative(resolved.getPackageLengthCm(), "箱子长度不能为负数。");
        assertNonNegative(resolved.getPackageWidthCm(), "箱子宽度不能为负数。");
        assertNonNegative(resolved.getPackageHeightCm(), "箱子高度不能为负数。");
        assertNonNegative(resolved.getPackageVolumeCbm(), "箱子体积不能为负数。");
        assertNonNegative(resolved.getPackageVolumeWeightKg(), "箱子体积重量不能为负数。");
        assertNonNegative(resolved.getPackageChargeableWeightKg(), "箱子计费重量不能为负数。");
        assertNonNegative(resolved.getMeasuredWeightKg(), "仓库测量箱子重量不能为负数。");
        assertNonNegative(resolved.getMeasuredLengthCm(), "仓库测量箱子长度不能为负数。");
        assertNonNegative(resolved.getMeasuredWidthCm(), "仓库测量箱子宽度不能为负数。");
        assertNonNegative(resolved.getMeasuredHeightCm(), "仓库测量箱子高度不能为负数。");
        assertNonNegative(resolved.getMeasuredVolumeCbm(), "仓库测量箱子体积不能为负数。");

        PackageRow packageRow = resolvePackage(
                ownerUserId,
                batchId,
                clean(resolved.getBoxNo()),
                clean(resolved.getExternalBoxNo()),
                clean(resolved.getPackageTrackingNo()),
                resolved.getPackageWeightKg(),
                resolved.getPackageLengthCm(),
                resolved.getPackageWidthCm(),
                resolved.getPackageHeightCm(),
                resolved.getPackageVolumeCbm(),
                resolved.getPackageVolumeWeightKg(),
                resolved.getPackageChargeableWeightKg(),
                resolved.getMeasuredWeightKg(),
                resolved.getMeasuredLengthCm(),
                resolved.getMeasuredWidthCm(),
                resolved.getMeasuredHeightCm(),
                resolved.getMeasuredVolumeCbm(),
                clean(resolved.getPackageStatus()),
                clean(resolved.getLogisticsStatus()),
                resolved.isPackageSnapshotAuthoritative(),
                operatorUserId
        );
        refreshBatchAggregate(ownerUserId, batchId);
        audit(
                ownerUserId,
                operatorUserId,
                "package_snapshot_saved",
                "package",
                packageRow == null ? batchId : packageRow.getId(),
                batchId,
                batch.getTargetStoreCode(),
                batch.getTargetSiteCode(),
                "在途箱子信息已更新。",
                detail("boxNo", resolved.getBoxNo(), "externalBoxNo", resolved.getExternalBoxNo())
        );
    }

    public void reconcileSyncedDetails(
            Long ownerUserId,
            Long operatorUserId,
            Long batchId,
            List<String> syncedBoxNos,
            List<String> syncedLineKeys
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        Long resolvedBatchId = requirePositiveId(batchId, "在途批次不存在。");
        if (syncedBoxNos == null || syncedBoxNos.isEmpty() || syncedLineKeys == null || syncedLineKeys.isEmpty()) {
            return;
        }
        mapper.softDeleteLinesNotInSyncedDetails(
                resolvedOwnerUserId,
                resolvedBatchId,
                syncedBoxNos,
                syncedLineKeys,
                operatorUserId
        );
        mapper.softDeletePackagesNotInSyncedBoxes(
                resolvedOwnerUserId,
                resolvedBatchId,
                syncedBoxNos,
                operatorUserId
        );
        refreshBatchAggregate(resolvedOwnerUserId, resolvedBatchId);
    }

    public LineListView deleteLine(DeleteLineCommand command) {
        DeleteLineCommand resolved = command == null ? new DeleteLineCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long batchId = requirePositiveId(resolved.getBatchId(), "在途批次不存在。");
        Long lineId = requirePositiveId(resolved.getLineId(), "在途商品明细不存在。");
        BatchRow batch = requireBatch(ownerUserId, batchId);
        LineRow line = requireLine(ownerUserId, batchId, lineId);
        mapper.deleteLine(ownerUserId, batchId, lineId, resolved.getOperatorUserId());
        refreshBatchAggregate(ownerUserId, batchId);
        audit(
                ownerUserId,
                resolved.getOperatorUserId(),
                "line_deleted",
                "line",
                lineId,
                batchId,
                firstText(line.getStoreCode(), batch.getTargetStoreCode()),
                firstText(line.getSiteCode(), batch.getTargetSiteCode()),
                "在途商品明细已删除。",
                detail("psku", line.getPsku(), "sourceSku", line.getSku())
        );
        return listLines(ownerUserId, batchId);
    }

    public NodeListView listNodes(Long ownerUserId, Long batchId) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        requireBatch(resolvedOwnerUserId, batchId);
        NodeListView view = new NodeListView();
        view.setItems(mapper.listNodes(resolvedOwnerUserId, batchId).stream()
                .map(NodeView::from)
                .collect(Collectors.toList()));
        return view;
    }

    public NodeView saveNode(SaveNodeCommand command) {
        SaveNodeCommand resolved = command == null ? new SaveNodeCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long batchId = requirePositiveId(resolved.getBatchId(), "在途批次不存在。");
        Long operatorUserId = resolved.getOperatorUserId();
        BatchRow batch = requireBatch(ownerUserId, batchId);
        String nodeStatus = InTransitNodeStatus.require(resolved.getNodeStatus()).code();
        LocalDateTime nodeHappenedAt = resolved.getNodeHappenedAt() == null
                ? LocalDateTime.now()
                : resolved.getNodeHappenedAt();

        if (resolved.getNodeId() != null) {
            requireNode(ownerUserId, batchId, resolved.getNodeId());
            NodeRow row = new NodeRow();
            row.setId(resolved.getNodeId());
            row.setOwnerUserId(ownerUserId);
            row.setBatchId(batchId);
            row.setNodeStatus(nodeStatus);
            row.setNodeHappenedAt(nodeHappenedAt);
            row.setDescription(clean(resolved.getDescription()));
            row.setOperatorName(clean(resolved.getOperatorName()));
            row.setUpdatedBy(operatorUserId);

            mapper.updateNode(row);
            refreshBatchLatestNode(ownerUserId, batchId);
            audit(
                    ownerUserId,
                    operatorUserId,
                    "logistics_node_updated",
                    "logistics_node",
                    row.getId(),
                    batchId,
                    batch.getTargetStoreCode(),
                    batch.getTargetSiteCode(),
                    "物流节点已更新。",
                    detail("nodeStatus", row.getNodeStatus(), "nodeHappenedAt", row.getNodeHappenedAt())
            );
            return NodeView.from(requireNode(ownerUserId, batchId, row.getId()));
        }

        NodeRow row = new NodeRow();
        row.setId(mapper.nextNodeId());
        row.setOwnerUserId(ownerUserId);
        row.setBatchId(batchId);
        row.setNodeStatus(nodeStatus);
        row.setNodeHappenedAt(nodeHappenedAt);
        row.setDescription(clean(resolved.getDescription()));
        row.setOperatorName(clean(resolved.getOperatorName()));
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);

        mapper.insertNode(row);
        refreshBatchLatestNode(ownerUserId, batchId);
        audit(
                ownerUserId,
                operatorUserId,
                "logistics_node_added",
                "logistics_node",
                row.getId(),
                batchId,
                batch.getTargetStoreCode(),
                batch.getTargetSiteCode(),
                "物流节点已追加。",
                detail("nodeStatus", row.getNodeStatus(), "nodeHappenedAt", row.getNodeHappenedAt())
        );
        return NodeView.from(requireNode(ownerUserId, batchId, row.getId()));
    }

    public NodeListView deleteNode(DeleteNodeCommand command) {
        DeleteNodeCommand resolved = command == null ? new DeleteNodeCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long batchId = requirePositiveId(resolved.getBatchId(), "在途批次不存在。");
        Long nodeId = requirePositiveId(resolved.getNodeId(), "物流节点不存在。");
        BatchRow batch = requireBatch(ownerUserId, batchId);
        requireNode(ownerUserId, batchId, nodeId);

        mapper.deleteNode(ownerUserId, batchId, nodeId, resolved.getOperatorUserId());
        refreshBatchLatestNode(ownerUserId, batchId);
        audit(
                ownerUserId,
                resolved.getOperatorUserId(),
                "logistics_node_deleted",
                "logistics_node",
                nodeId,
                batchId,
                batch.getTargetStoreCode(),
                batch.getTargetSiteCode(),
                "物流节点已删除。",
                null
        );
        return listNodes(ownerUserId, batchId);
    }

    private ForwarderResolveView resolveForwarder(Long ownerUserId, SaveBatchCommand command) {
        if (command.getStandardForwarderId() != null && command.getStandardForwarderId() > 0) {
            ForwarderRow row = mapper.selectForwarderById(ownerUserId, command.getStandardForwarderId());
            if (row == null || !"ACTIVE".equals(row.getStatus())) {
                throw new IllegalArgumentException("标准货代不存在。");
            }
            InTransitForwarderCatalog.CanonicalForwarder canonical =
                    InTransitForwarderCatalog.match(row.getForwarderCode(), row.getForwarderName());
            String standardForwarderName = canonical == null ? row.getForwarderName() : canonical.name();
            ForwarderResolveView view = new ForwarderResolveView();
            view.setQualityStatus(InTransitQualityStatus.FORWARDER_MATCHED.code());
            view.setStandardForwarderId(row.getId());
            view.setStandardForwarderCode(row.getForwarderCode());
            view.setStandardForwarderName(standardForwarderName);
            view.setRawForwarderName(standardForwarderName);
            view.setNormalizedRawForwarderName(clean(firstText(command.getRawForwarderName(), standardForwarderName)));
            return view;
        }
        if (!StringUtils.hasText(command.getRawForwarderName())) {
            return null;
        }
        ResolveForwarderCommand resolveCommand = new ResolveForwarderCommand();
        resolveCommand.setOwnerUserId(ownerUserId);
        resolveCommand.setRawForwarderName(command.getRawForwarderName());
        return forwarderService.resolveForwarder(resolveCommand);
    }

    private List<String> missingFields(
            SaveBatchCommand command,
            ForwarderResolveView forwarder,
            String transportMode,
            String destinationCode
    ) {
        List<String> result = new ArrayList<>();
        if (forwarder == null || (!StringUtils.hasText(command.getRawForwarderName()) && forwarder.getStandardForwarderId() == null)) {
            result.add("forwarder");
        }
        if (!StringUtils.hasText(transportMode)) {
            result.add("transportMode");
        }
        if (!StringUtils.hasText(destinationCode)) {
            result.add("targetStoreCode");
        }
        if (!StringUtils.hasText(command.getTargetWarehouseName())) {
            result.add("targetWarehouseName");
        }
        return result;
    }

    private String resolveDestinationCode(SaveBatchCommand command) {
        if (StringUtils.hasText(command.getTargetStoreCode())
                && InTransitDestination.infer(command.getTargetStoreCode()) == null) {
            throw new IllegalArgumentException("目的地只支持 RUH 利雅得或 DB 迪拜。");
        }
        InTransitDestination destination = InTransitDestination.infer(
                command.getTargetStoreCode(),
                command.getTargetSiteCode(),
                command.getTargetWarehouseName(),
                command.getBatchReferenceNo(),
                command.getTrackingNo(),
                command.getContainerNo()
        );
        if (destination != null) {
            return destination.code();
        }
        return null;
    }

    private String canonicalRawForwarderName(ForwarderResolveView forwarder, SaveBatchCommand command) {
        if (forwarder != null && StringUtils.hasText(forwarder.getRawForwarderName())) {
            return forwarder.getRawForwarderName();
        }
        InTransitForwarderCatalog.CanonicalForwarder canonical =
                InTransitForwarderCatalog.match(null, command.getRawForwarderName());
        if (canonical != null) {
            return canonical.name();
        }
        return clean(command.getRawForwarderName());
    }

    private String forwarderQualityStatus(ForwarderResolveView forwarder, SaveBatchCommand command) {
        if (forwarder == null) {
            return StringUtils.hasText(command.getRawForwarderName())
                    ? InTransitQualityStatus.FORWARDER_UNMATCHED.code()
                    : InTransitQualityStatus.FORWARDER_UNMATCHED.code();
        }
        return forwarder.getQualityStatus();
    }

    private static Long requireOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少老板账号范围。");
        }
        return ownerUserId;
    }

    private static void normalizeBatchListPage(InTransitBatchQuery query) {
        int page = query.getPage() == null || query.getPage() <= 0 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null || query.getPageSize() <= 0
                ? firstPositive(query.getLimit(), DEFAULT_BATCH_LIST_PAGE_SIZE)
                : query.getPageSize();
        pageSize = Math.min(pageSize, MAX_BATCH_LIST_PAGE_SIZE);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setLimit(pageSize);
        query.setOffset((page - 1) * pageSize);
    }

    private static int firstPositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static void normalizeBatchListSort(InTransitBatchQuery query) {
        String sortField = clean(query.getSortField());
        if (!"etaDate".equals(sortField)
                && !"latestNodeHappenedAt".equals(sortField)
                && !"gmtUpdated".equals(sortField)
                && !"createdAt".equals(sortField)) {
            query.setSortField("createdAt");
            query.setSortDirection("desc");
        } else {
            query.setSortField(sortField);
            String sortDirection = clean(query.getSortDirection());
            if (!StringUtils.hasText(sortDirection) && "createdAt".equals(sortField)) {
                query.setSortDirection("desc");
            } else {
                query.setSortDirection("desc".equalsIgnoreCase(sortDirection) ? "desc" : "asc");
            }
        }
    }

    private BatchRow requireBatch(Long ownerUserId, Long batchId) {
        Long resolvedBatchId = requirePositiveId(batchId, "在途批次不存在。");
        BatchRow row = mapper.selectBatchById(ownerUserId, resolvedBatchId);
        if (row == null) {
            throw new IllegalArgumentException("在途批次不存在。");
        }
        return row;
    }

    private LineRow requireLine(Long ownerUserId, Long batchId, Long lineId) {
        Long resolvedLineId = requirePositiveId(lineId, "在途商品明细不存在。");
        LineRow row = mapper.selectLineById(ownerUserId, batchId, resolvedLineId);
        if (row == null) {
            throw new IllegalArgumentException("在途商品明细不存在。");
        }
        return row;
    }

    private PackageRow resolvePackage(
            Long ownerUserId,
            Long batchId,
            String boxNo,
            String externalBoxNo,
            String trackingNo,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal volumeCbm,
            BigDecimal volumeWeightKg,
            BigDecimal chargeableWeightKg,
            BigDecimal measuredWeightKg,
            BigDecimal measuredLengthCm,
            BigDecimal measuredWidthCm,
            BigDecimal measuredHeightCm,
            BigDecimal measuredVolumeCbm,
            String packageStatus,
            String logisticsStatus,
            boolean packageSnapshotAuthoritative,
            Long operatorUserId
    ) {
        if (!StringUtils.hasText(boxNo)) {
            return null;
        }
        PackageRow existing = mapper.selectPackageByBoxNo(ownerUserId, batchId, boxNo);
        if (existing == null && sameIdentifier(boxNo, externalBoxNo)) {
            existing = mapper.selectPackageByExternalBoxNoForMerge(ownerUserId, batchId, externalBoxNo);
        }
        if (existing != null) {
            boolean hasPackageDetail = StringUtils.hasText(externalBoxNo)
                    || StringUtils.hasText(trackingNo)
                    || weightKg != null
                    || lengthCm != null
                    || widthCm != null
                    || heightCm != null
                    || volumeCbm != null
                    || volumeWeightKg != null
                    || chargeableWeightKg != null
                    || measuredWeightKg != null
                    || measuredLengthCm != null
                    || measuredWidthCm != null
                    || measuredHeightCm != null
                    || measuredVolumeCbm != null
                    || StringUtils.hasText(packageStatus)
                    || StringUtils.hasText(logisticsStatus)
                    || packageSnapshotAuthoritative;
            if (hasPackageDetail) {
                if (packageSnapshotAuthoritative) {
                    existing.setExternalBoxNo(externalBoxNo);
                    existing.setTrackingNo(trackingNo);
                    existing.setWeightKg(firstValue(weightKg, existing.getWeightKg()));
                    existing.setLengthCm(firstValue(lengthCm, existing.getLengthCm()));
                    existing.setWidthCm(firstValue(widthCm, existing.getWidthCm()));
                    existing.setHeightCm(firstValue(heightCm, existing.getHeightCm()));
                    existing.setVolumeCbm(firstValue(volumeCbm, existing.getVolumeCbm()));
                    existing.setVolumeWeightKg(firstValue(volumeWeightKg, existing.getVolumeWeightKg()));
                    existing.setChargeableWeightKg(firstValue(chargeableWeightKg, existing.getChargeableWeightKg()));
                    existing.setMeasuredWeightKg(firstValue(measuredWeightKg, existing.getMeasuredWeightKg()));
                    existing.setMeasuredLengthCm(firstValue(measuredLengthCm, existing.getMeasuredLengthCm()));
                    existing.setMeasuredWidthCm(firstValue(measuredWidthCm, existing.getMeasuredWidthCm()));
                    existing.setMeasuredHeightCm(firstValue(measuredHeightCm, existing.getMeasuredHeightCm()));
                    existing.setMeasuredVolumeCbm(firstValue(measuredVolumeCbm, existing.getMeasuredVolumeCbm()));
                    existing.setPackageStatus(packageStatus);
                    existing.setLogisticsStatus(logisticsStatus);
                } else {
                    existing.setExternalBoxNo(firstText(externalBoxNo, existing.getExternalBoxNo()));
                    existing.setTrackingNo(firstText(trackingNo, existing.getTrackingNo()));
                    existing.setWeightKg(firstValue(weightKg, existing.getWeightKg()));
                    existing.setLengthCm(firstValue(lengthCm, existing.getLengthCm()));
                    existing.setWidthCm(firstValue(widthCm, existing.getWidthCm()));
                    existing.setHeightCm(firstValue(heightCm, existing.getHeightCm()));
                    existing.setVolumeCbm(firstValue(volumeCbm, existing.getVolumeCbm()));
                    existing.setVolumeWeightKg(firstValue(volumeWeightKg, existing.getVolumeWeightKg()));
                    existing.setChargeableWeightKg(firstValue(chargeableWeightKg, existing.getChargeableWeightKg()));
                    existing.setMeasuredWeightKg(firstValue(measuredWeightKg, existing.getMeasuredWeightKg()));
                    existing.setMeasuredLengthCm(firstValue(measuredLengthCm, existing.getMeasuredLengthCm()));
                    existing.setMeasuredWidthCm(firstValue(measuredWidthCm, existing.getMeasuredWidthCm()));
                    existing.setMeasuredHeightCm(firstValue(measuredHeightCm, existing.getMeasuredHeightCm()));
                    existing.setMeasuredVolumeCbm(firstValue(measuredVolumeCbm, existing.getMeasuredVolumeCbm()));
                    existing.setPackageStatus(firstText(packageStatus, existing.getPackageStatus()));
                    existing.setLogisticsStatus(firstText(logisticsStatus, existing.getLogisticsStatus()));
                }
                existing.setChargeableWeightKg(resolveChargeableWeight(
                        existing.getChargeableWeightKg(),
                        existing.getWeightKg(),
                        existing.getVolumeWeightKg()
                ));
                existing.setUpdatedBy(operatorUserId);
                mapper.updatePackage(existing);
            }
            return existing;
        }
        PackageRow row = new PackageRow();
        row.setId(mapper.nextPackageId());
        row.setOwnerUserId(ownerUserId);
        row.setBatchId(batchId);
        row.setBoxNo(boxNo);
        row.setExternalBoxNo(externalBoxNo);
        row.setTrackingNo(trackingNo);
        row.setWeightKg(weightKg);
        row.setLengthCm(lengthCm);
        row.setWidthCm(widthCm);
        row.setHeightCm(heightCm);
        row.setVolumeCbm(volumeCbm);
        row.setVolumeWeightKg(volumeWeightKg);
        row.setChargeableWeightKg(resolveChargeableWeight(chargeableWeightKg, weightKg, volumeWeightKg));
        row.setMeasuredWeightKg(measuredWeightKg);
        row.setMeasuredLengthCm(measuredLengthCm);
        row.setMeasuredWidthCm(measuredWidthCm);
        row.setMeasuredHeightCm(measuredHeightCm);
        row.setMeasuredVolumeCbm(measuredVolumeCbm);
        row.setPackageStatus(packageStatus);
        row.setLogisticsStatus(logisticsStatus);
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);
        mapper.insertPackage(row);
        return row;
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

    private void refreshBatchAggregate(Long ownerUserId, Long batchId) {
        BatchAggregateRow aggregate = mapper.aggregateLines(ownerUserId, batchId);
        mapper.refreshBatchAggregate(ownerUserId, batchId, aggregate == null ? new BatchAggregateRow() : aggregate);
    }

    private NodeRow requireNode(Long ownerUserId, Long batchId, Long nodeId) {
        Long resolvedNodeId = requirePositiveId(nodeId, "物流节点不存在。");
        NodeRow row = mapper.selectNodeById(ownerUserId, batchId, resolvedNodeId);
        if (row == null) {
            throw new IllegalArgumentException("物流节点不存在。");
        }
        return row;
    }

    private void refreshBatchLatestNode(Long ownerUserId, Long batchId) {
        BatchLatestNodeRow latestNode = mapper.selectLatestNode(ownerUserId, batchId);
        if (latestNode == null) {
            latestNode = new BatchLatestNodeRow();
            latestNode.setDerivedBatchStatus(InTransitBatchStatus.DRAFT.code());
        } else {
            latestNode.setDerivedBatchStatus(deriveBatchStatusFromNode(latestNode.getLatestNodeStatus()));
        }
        mapper.refreshBatchLatestNode(ownerUserId, batchId, latestNode);
    }

    private static String deriveBatchStatusFromNode(String nodeStatus) {
        InTransitNodeStatus status = InTransitNodeStatus.require(nodeStatus);
        switch (status) {
            case EXCEPTION:
                return InTransitBatchStatus.EXCEPTION.code();
            case WAREHOUSE_RECEIVED:
                return InTransitBatchStatus.WAREHOUSE_RECEIVED.code();
            case CANCELLED:
                return InTransitBatchStatus.CANCELLED.code();
            case CUSTOMS_CLEARANCE:
                return InTransitBatchStatus.CUSTOMS_CLEARANCE.code();
            case DELIVERING:
                return InTransitBatchStatus.DELIVERING.code();
            case HANDED_TO_FORWARDER:
                return InTransitBatchStatus.PENDING_SHIPMENT.code();
            case CREATED:
                return InTransitBatchStatus.DRAFT.code();
            case DEPARTED_ORIGIN:
            case IN_TRANSIT:
            case ARRIVED_PORT:
            case CUSTOMS_RELEASED:
            default:
                return InTransitBatchStatus.IN_TRANSIT.code();
        }
    }

    private static Long requirePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static Integer nonNegativeOrZero(Integer value, String message) {
        if (value == null) {
            return 0;
        }
        assertNonNegative(value, message);
        return value;
    }

    private static void assertNonNegative(Integer value, String message) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void assertNonNegative(BigDecimal value, String message) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void audit(
            Long ownerUserId,
            Long operatorUserId,
            String operationType,
            String targetType,
            Long targetId,
            Long batchId,
            String storeCode,
            String siteCode,
            String summary,
            Map<String, Object> detail
    ) {
        InTransitOperationAuditService.AuditCommand command = new InTransitOperationAuditService.AuditCommand();
        command.setOwnerUserId(ownerUserId);
        command.setOperatorUserId(operatorUserId);
        command.setOperationType(operationType);
        command.setTargetType(targetType);
        command.setTargetId(targetId);
        command.setBatchId(batchId);
        command.setStoreCode(storeCode);
        command.setSiteCode(siteCode);
        command.setSummary(summary);
        command.setDetail(detail);
        auditService.record(command);
    }

    private static Map<String, Object> detail(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (pairs == null) {
            return result;
        }
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static <T> T firstValue(T first, T second) {
        return first == null ? second : first;
    }

    private static boolean sameIdentifier(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.trim().equalsIgnoreCase(right.trim());
    }
}
