package com.nuono.next.intransit.autosync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nuono.next.infrastructure.mapper.InTransitFreightCostMapper;
import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightBillCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightComponentCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightBillRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FreightBillSyncPreviewService {
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final InTransitGoodsMapper batchMapper;
    private final InTransitFreightCostMapper freightCostMapper;
    private final ObjectMapper objectMapper;

    public FreightBillSyncPreviewService(
            InTransitGoodsMapper batchMapper,
            InTransitFreightCostMapper freightCostMapper,
            ObjectMapper objectMapper
    ) {
        this.batchMapper = batchMapper;
        this.freightCostMapper = freightCostMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper.copy();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public FreightBillSyncPreview preview(ActualFreightSyncCommand command, FreightBillFetchResult fetchResult) {
        ActualFreightSyncCommand resolved = command == null ? new ActualFreightSyncCommand() : command;
        FreightBillSyncPreview preview = new FreightBillSyncPreview();
        preview.setRevisionDigest(fetchResult == null ? null : fetchResult.getRevisionDigest());
        List<FreightBillSyncPreview.Issue> issues = new ArrayList<>();
        if (fetchResult == null || !fetchResult.isSnapshotComplete()) {
            issues.add(issue("INCOMPLETE_SNAPSHOT", null, null, "三方返回不是完整费用快照，禁止覆盖已有费用。"));
        }
        if (fetchResult != null && fetchResult.getIssues() != null) {
            for (String sourceIssue : fetchResult.getIssues()) {
                issues.add(issue("PROVIDER_PAYLOAD_INCOMPLETE", null, null, sourceIssue));
            }
        }

        Long ownerUserId = resolved.getOwnerUserId();
        String sourceSystem = normalizeSource(resolved.getSourceSystem());
        if (ownerUserId == null || ownerUserId <= 0) {
            issues.add(issue("OWNER_REQUIRED", null, null, "费用预览缺少业务 owner。"));
        }
        if (!"CHIC".equals(sourceSystem) && !"YITE".equals(sourceSystem)) {
            issues.add(issue("SOURCE_NOT_PHASE_ONE", null, null, "一期费用自动拉取只允许 CHIC、YITE。"));
        }

        List<ActualFreightBillCommand> changedBills = new ArrayList<>();
        Set<String> naturalKeys = new HashSet<>();
        int componentCount = 0;
        int createCount = 0;
        int updateCount = 0;
        int unchangedCount = 0;
        for (ActualFreightBillCommand bill : resolved.getBills()) {
            if (bill == null) {
                issues.add(issue("NULL_BILL", null, null, "费用快照包含空账单。"));
                continue;
            }
            String billNo = clean(bill.getBillNo());
            String batchReferenceNo = clean(bill.getBatchReferenceNo());
            if (!StringUtils.hasText(billNo) || !StringUtils.hasText(batchReferenceNo)) {
                issues.add(issue("BILL_KEY_REQUIRED", billNo, batchReferenceNo, "账单号、批次号不能为空。"));
                continue;
            }
            String naturalKey = sourceSystem + "|" + billNo + "|" + batchReferenceNo;
            if (!naturalKeys.add(naturalKey)) {
                issues.add(issue("DUPLICATE_BILL_KEY", billNo, batchReferenceNo, "费用快照存在重复账单业务键。"));
                continue;
            }
            if (bill.getComponents() == null || bill.getComponents().isEmpty()) {
                issues.add(issue("COMPONENTS_REQUIRED", billNo, batchReferenceNo, "完整账单必须包含费用明细。"));
                continue;
            }
            componentCount += bill.getComponents().size();
            BigDecimal componentTotal = BigDecimal.ZERO;
            boolean amountMissing = false;
            for (ActualFreightComponentCommand component : bill.getComponents()) {
                if (component == null || component.getCnyAmount() == null) {
                    amountMissing = true;
                    continue;
                }
                componentTotal = componentTotal.add(component.getCnyAmount());
            }
            if (amountMissing || bill.getCnyTotalAmount() == null) {
                issues.add(issue("AMOUNT_REQUIRED", billNo, batchReferenceNo, "账单或费用明细缺少人民币金额。"));
                continue;
            }
            if (componentTotal.subtract(bill.getCnyTotalAmount()).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
                issues.add(issue("AMOUNT_NOT_CLOSED", billNo, batchReferenceNo, "账单合计与费用明细合计不闭合。"));
                continue;
            }
            BatchRow batch = ownerUserId == null ? null : batchMapper.selectBatchByReferenceNo(ownerUserId, batchReferenceNo);
            if (batch == null) {
                issues.add(issue("BATCH_NOT_FOUND", billNo, batchReferenceNo, "找不到本地在途批次，禁止写入。"));
                continue;
            }
            ActualFreightBillRow existing = freightCostMapper.selectActualBillBySource(ownerUserId, sourceSystem, billNo, batch.getId());
            if (existing == null) {
                createCount += 1;
                changedBills.add(bill);
            } else if (samePayload(existing.getRawPayloadJson(), bill)) {
                unchangedCount += 1;
            } else {
                updateCount += 1;
                changedBills.add(bill);
            }
        }

        ActualFreightSyncCommand changedCommand = new ActualFreightSyncCommand();
        changedCommand.setOwnerUserId(resolved.getOwnerUserId());
        changedCommand.setOperatorUserId(resolved.getOperatorUserId());
        changedCommand.setAccessContext(resolved.getAccessContext());
        changedCommand.setSourceSystem(sourceSystem);
        changedCommand.setBills(changedBills);
        preview.setChangedCommand(changedCommand);
        preview.setBillCount(resolved.getBills().size());
        preview.setComponentCount(componentCount);
        preview.setCreateCount(createCount);
        preview.setUpdateCount(updateCount);
        preview.setUnchangedCount(unchangedCount);
        preview.setIssues(issues);
        preview.setCommittable(issues.isEmpty());
        return preview;
    }

    private boolean samePayload(String existingPayload, ActualFreightBillCommand candidate) {
        if (!StringUtils.hasText(existingPayload)) {
            return false;
        }
        try {
            String candidatePayload = objectMapper.writeValueAsString(candidate);
            return objectMapper.readTree(existingPayload).equals(objectMapper.readTree(candidatePayload));
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private static FreightBillSyncPreview.Issue issue(String code, String billNo, String batchReferenceNo, String message) {
        return new FreightBillSyncPreview.Issue(code, billNo, batchReferenceNo, message);
    }

    private static String normalizeSource(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
