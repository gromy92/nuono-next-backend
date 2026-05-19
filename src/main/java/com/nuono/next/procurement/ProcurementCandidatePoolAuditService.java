package com.nuono.next.procurement;

import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.CANDIDATE_SOURCE_LIMIT;

import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.CandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolLockRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
class ProcurementCandidatePoolAuditService {

    private final ProcurementRequirementConfirmationMapper mapper;
    private final ProcurementCandidatePoolIdGenerator idGenerator;
    private final ProcurementJsonSupport jsonSupport;

    ProcurementCandidatePoolAuditService(
            ProcurementRequirementConfirmationMapper mapper,
            ProcurementCandidatePoolIdGenerator idGenerator,
            ProcurementJsonSupport jsonSupport
    ) {
        this.mapper = mapper;
        this.idGenerator = idGenerator;
        this.jsonSupport = jsonSupport;
    }

    Long createSnapshot(
            PoolLockRow pool,
            String snapshotType,
            String poolStatus,
            String remark,
            Map<String, Object> extra,
            Long createdBy
    ) {
        List<PoolItemRow> currentItems = mapper.listCurrentPoolItems(pool.getPoolId());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("poolId", pool.getPoolId());
        snapshot.put("poolNo", pool.getPoolNo());
        snapshot.put("ownerUserId", pool.getOwnerUserId());
        snapshot.put("demandItemId", pool.getDemandItemId());
        snapshot.put("poolStatus", poolStatus);
        snapshot.put("itemCount", currentItems.size());
        snapshot.put("items", toSnapshotItems(currentItems));
        if (extra != null && !extra.isEmpty()) {
            snapshot.put("extra", extra);
        }

        Long snapshotId = idGenerator.nextSnapshotId();
        Integer maxVersion = mapper.selectMaxSnapshotVersion(pool.getPoolId(), snapshotType);
        mapper.insertCandidatePoolSnapshot(
                snapshotId,
                pool.getPoolId(),
                pool.getOwnerUserId(),
                pool.getDemandItemId(),
                snapshotType,
                defaultInt(maxVersion) + 1,
                poolStatus,
                currentItems.size(),
                jsonSupport.toJson(snapshot, "采购待选池快照序列化失败。"),
                remark,
                createdBy
        );
        return snapshotId;
    }

    void appendLog(
            PoolLockRow pool,
            PoolItemLockRow item,
            String operationType,
            ProcurementCandidatePoolWriteContext context,
            String beforeStatus,
            String afterStatus,
            Long snapshotId,
            String reason,
            Map<String, Object> detail
    ) {
        mapper.insertPoolOperationLog(
                idGenerator.nextLogId(),
                pool.getPoolId(),
                item == null ? null : item.getPoolItemId(),
                pool.getOwnerUserId(),
                pool.getDemandItemId(),
                item == null ? null : item.getCandidateId(),
                item == null ? null : ProcurementOfferIdExtractor.extract(resolveCandidateUrl(pool.getDemandItemId(), item.getCandidateId())),
                operationType,
                context.operatorUserId,
                context.operatorRole,
                beforeStatus,
                afterStatus,
                snapshotId,
                reason,
                detail == null ? null : jsonSupport.toJson(detail, "采购待选池日志序列化失败。")
        );
    }

    private List<Map<String, Object>> toSnapshotItems(List<PoolItemRow> rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (PoolItemRow row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("poolItemId", row.getPoolItemId());
            item.put("candidateId", row.getCandidateId());
            item.put("offerId", ProcurementOfferIdExtractor.extract(row.getCandidateUrl()));
            item.put("sourceRankNo", row.getSourceRankNo());
            item.put("poolRankNo", row.getPoolRankNo());
            item.put("status", row.getStatus());
            item.put("title", row.getTitle());
            item.put("supplierName", row.getSupplierName());
            item.put("priceText", row.getPriceText());
            item.put("moqText", row.getMoqText());
            item.put("deliveryTimelineText", row.getDeliveryTimelineText());
            item.put("quotePriceText", row.getQuotePriceText());
            item.put("quoteMoqText", row.getQuoteMoqText());
            item.put("quoteDeliveryText", row.getQuoteDeliveryText());
            item.put("replySummary", row.getReplySummary());
            item.put("riskNote", row.getRiskNote());
            item.put("inquiryTaskId", row.getInquiryTaskId());
            item.put("inquiryTaskStatus", row.getInquiryTaskStatus());
            items.add(item);
        }
        return items;
    }

    private String resolveCandidateUrl(Long demandItemId, Long candidateId) {
        if (candidateId == null) {
            return null;
        }
        for (CandidateRow candidate : mapper.listTopCandidates(demandItemId, CANDIDATE_SOURCE_LIMIT)) {
            if (candidateId.equals(candidate.getCandidateId())) {
                return candidate.getCandidateUrl();
            }
        }
        return null;
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
