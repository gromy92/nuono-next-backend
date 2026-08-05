package com.nuono.next.procurement.aliorder.datapull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Persists pre-validation fingerprints and owns payload/decode integrity checks. */
final class Ali1688Dp10StageAssembler {
    static final int MAX_STAGE_PAYLOAD_BYTES = 16_777_215 - 65_535;

    private final Ali1688Dp10StageMapper mapper;
    private final Ali1688Dp10OrderPayloadCodec codec;
    private final int maxPayloadBytes;

    Ali1688Dp10StageAssembler(Ali1688Dp10StageMapper mapper, ObjectMapper objectMapper) {
        this(mapper, objectMapper, MAX_STAGE_PAYLOAD_BYTES);
    }

    Ali1688Dp10StageAssembler(
            Ali1688Dp10StageMapper mapper,
            ObjectMapper objectMapper,
            int maxPayloadBytes
    ) {
        if (maxPayloadBytes <= 0 || maxPayloadBytes > 16_777_215) {
            throw new IllegalArgumentException("DP-10 stage payload byte limit is invalid");
        }
        this.mapper = mapper;
        this.codec = new Ali1688Dp10OrderPayloadCodec(objectMapper);
        this.maxPayloadBytes = maxPayloadBytes;
    }

    Ali1688Dp10StagePageRow pageRow(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688Dp10ValidatedPage page,
            List<Ali1688Dp10StageItemRow> items
    ) {
        Ali1688Dp10StagePageRow row = new Ali1688Dp10StagePageRow();
        row.setTaskId(task.getId());
        row.setGenerationNo(generationNo);
        row.setScanPass(scanPass);
        row.setPartitionName(page.getPartition().name());
        row.setPageNo(page.getPageNo());
        row.setActiveFenceEpoch(task.getFenceEpoch());
        row.setPageSize(page.getPageSize());
        row.setTotalRecord(page.getTotalRecord());
        row.setExpectedPages(page.getExpectedPages());
        row.setRawRowCount(page.getRawRowCount());
        row.setState("LISTED");
        StringBuilder proof = new StringBuilder();
        appendEnvelope(proof, row);
        items.forEach(item -> appendItemProof(proof, item));
        row.setPageFingerprint(Ali1688Dp10Digest.sha256(proof.toString()));
        return row;
    }

    List<Ali1688Dp10StageItemRow> itemRows(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688Dp10ValidatedPage page
    ) {
        List<Ali1688Dp10StageItemRow> rows = new ArrayList<>();
        for (Ali1688Dp10ListEntry entry : page.getEntries()) {
            Ali1688Dp10StageItemRow row = baseItemRow(
                    task, generationNo, scanPass, page, entry);
            applyPayload(row, entry.getState(), entry.getSanitizedCode(),
                    entry.getOrder());
            rows.add(row);
        }
        return rows;
    }

    void applyDetailPayload(
            Ali1688Dp10StageItemRow row,
            Ali1688Dp10ItemState state,
            String sanitizedCode,
            Ali1688HistoricalOrderProvider.OrderSnapshot order
    ) {
        applyPayload(row, state, sanitizedCode, order);
    }

    Ali1688Dp10StagedPage assemble(Ali1688Dp10StagePageRow page) {
        List<Ali1688Dp10StageItemRow> rows = mapper.selectItems(
                page.getTaskId(), page.getGenerationNo(), page.getScanPass(),
                page.getPartitionName(), page.getPageNo());
        if (rows == null || !Objects.equals(page.getRawRowCount(), rows.size())) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_ITEM_COUNT_INVALID");
        }
        return new Ali1688Dp10StagedPage(
                page.getGenerationNo(), page.getScanPass(),
                Ali1688HistoricalOrderProvider.Partition.valueOf(page.getPartitionName()),
                page.getPageNo(), page.getPageSize(), page.getTotalRecord(),
                page.getExpectedPages(), Ali1688Dp10StagedPage.State.valueOf(page.getState()),
                decodeRows(rows));
    }

    Ali1688HistoricalOrderProvider.OrderSnapshot decodeComplete(Ali1688Dp10StageItemRow row) {
        if (!Ali1688Dp10ItemState.COMPLETE.name().equals(row.getState())) {
            throw new IllegalArgumentException("DP10 staged row is not complete");
        }
        Ali1688HistoricalOrderProvider.OrderSnapshot order = decode(row);
        if (order == null || !Objects.equals(row.getProviderOrderNo(), order.getProviderOrderNo())) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_PAYLOAD_IDENTITY_INVALID");
        }
        return order;
    }

    void verifyRawFingerprint(
            Ali1688Dp10StagePageRow page,
            List<Ali1688Dp10StageItemRow> items
    ) {
        StringBuilder proof = new StringBuilder();
        appendEnvelope(proof, page);
        items.forEach(item -> appendItemProof(proof, item));
        if (!Objects.equals(page.getPageFingerprint(),
                Ali1688Dp10Digest.sha256(proof.toString()))) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_PAGE_FINGERPRINT_INVALID");
        }
    }

    String encode(Ali1688HistoricalOrderProvider.OrderSnapshot order) {
        return codec.encode(order);
    }

    String fingerprint(String payload) {
        return codec.fingerprint(payload);
    }

    private Ali1688Dp10StageItemRow baseItemRow(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688Dp10ValidatedPage page,
            Ali1688Dp10ListEntry entry
    ) {
        Ali1688Dp10StageItemRow row = new Ali1688Dp10StageItemRow();
        row.setTaskId(task.getId());
        row.setGenerationNo(generationNo);
        row.setScanPass(scanPass);
        row.setPartitionName(page.getPartition().name());
        row.setPageNo(page.getPageNo());
        row.setItemOrdinal(entry.getOrdinal());
        if (entry.getOrder() != null) {
            if (!"DP10_ORDER_IDENTITY_INVALID".equals(entry.getSanitizedCode())
                    && !"DP10_ORDER_IDENTITY_MISSING".equals(entry.getSanitizedCode())) {
                row.setProviderOrderNo(entry.getOrder().getProviderOrderNo());
            }
            if (!"DP10_ORDER_MODIFIED_AT_INVALID".equals(entry.getSanitizedCode())
                    && !"DP10_ORDER_MODIFIED_AT_MISSING".equals(entry.getSanitizedCode())) {
                row.setProviderModifiedAt(utc(entry.getOrder().getProviderModifiedAt()));
            }
        }
        row.setState(entry.getState().name());
        row.setValidationCode(entry.getSanitizedCode());
        row.setVerificationState(scanPass == 2 ? "PENDING" : "NOT_APPLICABLE");
        row.setApplyState(scanPass == 2 ? "BLOCKED" : "NOT_APPLICABLE");
        row.setApplyItemCursor(0);
        row.setListContentFingerprint(entry.getRawFingerprint());
        return row;
    }

    private void applyPayload(
            Ali1688Dp10StageItemRow row,
            Ali1688Dp10ItemState state,
            String sanitizedCode,
            Ali1688HistoricalOrderProvider.OrderSnapshot order
    ) {
        Ali1688Dp10OrderPayloadCodec.EncodedPayload encoded =
                codec.encodeBounded(order, maxPayloadBytes);
        if (encoded.isTooLarge()) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_PAYLOAD_TOO_LARGE");
        }
        row.setState(state.name());
        row.setValidationCode(sanitizedCode);
        row.setPayload(encoded.getPayload());
        row.setContentFingerprint(encoded.getFingerprint());
    }

    private List<Ali1688Dp10StagedOrder> decodeRows(List<Ali1688Dp10StageItemRow> rows) {
        List<Ali1688Dp10StagedOrder> orders = new ArrayList<>();
        int ordinal = 0;
        for (Ali1688Dp10StageItemRow row : rows) {
            if (!Objects.equals(row.getItemOrdinal(), ordinal++)) {
                throw new Ali1688Dp10PageContractException("DP10_STAGE_ITEM_ORDER_INVALID");
            }
            orders.add(new Ali1688Dp10StagedOrder(
                    row.getItemOrdinal(), row.getProviderOrderNo(),
                    Ali1688Dp10ItemState.valueOf(row.getState()), row.getValidationCode(),
                    decode(row)));
        }
        return orders;
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot decode(Ali1688Dp10StageItemRow row) {
        return codec.decode(row.getPayload(), row.getContentFingerprint());
    }

    private void appendEnvelope(StringBuilder proof, Ali1688Dp10StagePageRow row) {
        append(proof, row.getGenerationNo());
        append(proof, row.getScanPass());
        append(proof, row.getPartitionName());
        append(proof, row.getPageNo());
        append(proof, row.getPageSize());
        append(proof, row.getTotalRecord());
        append(proof, row.getExpectedPages());
        append(proof, row.getRawRowCount());
    }

    private void appendItemProof(StringBuilder proof, Ali1688Dp10StageItemRow item) {
        append(proof, item.getItemOrdinal());
        append(proof, item.getProviderOrderNo());
        append(proof, item.getProviderModifiedAt());
        append(proof, item.getListContentFingerprint());
    }

    private void append(StringBuilder proof, Object value) {
        String text = String.valueOf(value);
        proof.append(text.length()).append(':').append(text);
    }

    private LocalDateTime utc(java.time.Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
