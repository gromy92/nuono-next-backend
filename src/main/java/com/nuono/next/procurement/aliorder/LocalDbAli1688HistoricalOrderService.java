package com.nuono.next.procurement.aliorder;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalDbAli1688HistoricalOrderService {

    static final String DEV_PROVIDER_CODE = "ALI1688_DEV";
    static final String LOCAL_EXCEL_PROVIDER_CODE = "ALI1688_EXCEL_LOCAL";
    static final String EXCEL_UPLOAD_PROVIDER_CODE = "ALI1688_EXCEL_UPLOAD";
    static final String DEV_ACCOUNT_LABEL = "1688 开发授权账号";
    static final String ORDER_READ_SCOPE = "读取 1688 历史订单，不会付款或创建订单。";
    static final String EXCEL_UPLOAD_SCOPE = "用户上传 1688 历史订单 Excel，只写只读历史订单事实。";
    static final String ASSIGNMENT_TARGET_STORE_SITE = "STORE_SITE";
    static final String ASSIGNMENT_TARGET_CONSUMABLE = "CONSUMABLE";
    static final long MAX_EXCEL_IMPORT_FILE_SIZE_BYTES = 20L * 1024L * 1024L;

    private final Ali1688HistoricalOrderMapper mapper;
    private final Ali1688HistoricalOrderProvider provider;

    @Autowired
    public LocalDbAli1688HistoricalOrderService(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688HistoricalOrderProvider provider
    ) {
        this.mapper = mapper;
        this.provider = provider;
    }

    public LocalDbAli1688HistoricalOrderService(Ali1688HistoricalOrderMapper mapper) {
        this(mapper, new FakeAli1688HistoricalOrderProvider());
    }

    public Ali1688HistoricalOrderWorkbenchView buildWorkbench(BusinessAccessContext context) {
        return buildWorkbench(context, Ali1688HistoricalOrderQuery.defaultQuery());
    }

    public Ali1688HistoricalOrderWorkbenchView buildWorkbench(
            BusinessAccessContext context,
            Ali1688HistoricalOrderQuery query
    ) {
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderQuery resolvedQuery = query == null
                ? Ali1688HistoricalOrderQuery.defaultQuery()
                : query;
        validateRequestedStore(context, resolvedQuery);
        Ali1688HistoricalOrderAuthorizationRow authorization = ownerUserId == null
                ? null
                : mapper.selectCurrentAuthorization(ownerUserId);
        if (authorization != null) {
            return buildAuthorizedWorkbench(context, authorization, resolvedQuery);
        }
        return Ali1688HistoricalOrderWorkbenchView.noAuthorization(context, resolvedQuery);
    }

    public Ali1688HistoricalOrderWorkbenchView createDevAuthorization(BusinessAccessContext context) {
        Long ownerUserId = ownerUserId(context);
        Long operatorUserId = operatorUserId(context);
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(mapper.nextAuthorizationId());
        row.setOwnerUserId(ownerUserId);
        row.setProviderCode(DEV_PROVIDER_CODE);
        row.setProviderAccountId("dev-1688-" + ownerUserId);
        row.setAccountLabel(DEV_ACCOUNT_LABEL);
        row.setStatus("authorized");
        row.setScopeSummary(ORDER_READ_SCOPE);
        row.setExpiresAt(LocalDateTime.now().plusDays(30));
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);
        mapper.insertAuthorization(row);
        mapper.insertOwnerWideStoreBinding(
                mapper.nextOrderStoreBindingId(),
                ownerUserId,
                row.getId(),
                operatorUserId
        );
        return buildAuthorizedWorkbench(context, row, Ali1688HistoricalOrderQuery.defaultQuery());
    }

    public Ali1688HistoricalOrderExcelImportView.SourceView createExcelUploadSource(
            BusinessAccessContext context,
            Ali1688HistoricalOrderExcelImportView.SourceCreateRequest request
    ) {
        Ali1688HistoricalOrderExcelImportView.SourceCreateRequest resolvedRequest = request == null
                ? new Ali1688HistoricalOrderExcelImportView.SourceCreateRequest()
                : request;
        Ali1688HistoricalOrderQuery scopeQuery = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                resolvedRequest.getStoreCode(),
                resolvedRequest.getSiteCode(),
                null,
                null
        );
        if (!hasText(scopeQuery.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 导入必须绑定当前店铺范围。");
        }
        validateRequestedStore(context, scopeQuery);

        Long ownerUserId = ownerUserId(context);
        Long operatorUserId = operatorUserId(context);
        String accountLabel = hasText(resolvedRequest.getAccountLabel())
                ? resolvedRequest.getAccountLabel().trim()
                : "1688 Excel 导入";
        String providerAccountId = "excel-upload:" + ownerUserId + ":" + accountLabel;
        Ali1688HistoricalOrderAuthorizationRow row = mapper.selectAuthorizationByProviderAccount(
                ownerUserId,
                EXCEL_UPLOAD_PROVIDER_CODE,
                providerAccountId
        );
        if (row == null) {
            row = new Ali1688HistoricalOrderAuthorizationRow();
            row.setId(mapper.nextAuthorizationId());
            row.setOwnerUserId(ownerUserId);
            row.setProviderCode(EXCEL_UPLOAD_PROVIDER_CODE);
            row.setProviderAccountId(providerAccountId);
            row.setAccountLabel(accountLabel);
            row.setStatus("authorized");
            row.setScopeSummary(EXCEL_UPLOAD_SCOPE);
            row.setCreatedBy(operatorUserId);
            row.setUpdatedBy(operatorUserId);
            mapper.insertAuthorization(row);
        }
        mapper.insertExplicitStoreBinding(
                mapper.nextOrderStoreBindingId(),
                ownerUserId,
                row.getId(),
                scopeQuery.getStoreCode(),
                scopeQuery.getSiteCode(),
                operatorUserId,
                "Excel 上传来源绑定到当前店铺范围。"
        );
        return Ali1688HistoricalOrderExcelImportView.SourceView.excelUpload(
                row.getId(),
                row.getAccountLabel(),
                scopeQuery.getStoreCode(),
                scopeQuery.getSiteCode()
        );
    }

    public List<Ali1688HistoricalOrderExcelImportView.SourceView> listExcelUploadSources(
            BusinessAccessContext context,
            String storeCode,
            String siteCode
    ) {
        Ali1688HistoricalOrderQuery scopeQuery = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                storeCode,
                siteCode,
                null,
                null
        );
        validateRequestedStore(context, scopeQuery);
        if (!hasText(scopeQuery.getStoreCode())) {
            return List.of();
        }
        return emptyList(mapper.listExcelUploadAuthorizations(
                ownerUserId(context),
                EXCEL_UPLOAD_PROVIDER_CODE,
                scopeQuery.getStoreCode(),
                scopeQuery.getSiteCode()
        )).stream()
                .map(row -> Ali1688HistoricalOrderExcelImportView.SourceView.fromAuthorization(
                        row,
                        scopeQuery.getStoreCode(),
                        scopeQuery.getSiteCode()
                ))
                .collect(Collectors.toList());
    }

    public List<Ali1688HistoricalOrderExcelImportView.BatchView> listExcelImportBatches(
            BusinessAccessContext context,
            String storeCode,
            String siteCode
    ) {
        Ali1688HistoricalOrderQuery scopeQuery = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                storeCode,
                siteCode,
                null,
                null
        );
        validateRequestedStore(context, scopeQuery);
        if (!hasText(scopeQuery.getStoreCode())) {
            return List.of();
        }
        List<Long> visibleAuthorizationIds = visibleAuthorizationIds(ownerUserId(context), scopeQuery, null);
        if (visibleAuthorizationIds.isEmpty()) {
            return List.of();
        }
        return emptyList(mapper.listExcelImportBatches(
                ownerUserId(context),
                visibleAuthorizationIds,
                scopeQuery.getStoreCode(),
                scopeQuery.getSiteCode(),
                20
        )).stream()
                .map(Ali1688HistoricalOrderExcelImportView.BatchView::from)
                .collect(Collectors.toList());
    }

    public Ali1688HistoricalOrderExcelImportView.BatchDetailView excelImportBatchDetail(
            BusinessAccessContext context,
            Long batchId,
            String storeCode,
            String siteCode
    ) {
        if (batchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要查看的 Excel 导入批次。");
        }
        Ali1688HistoricalOrderQuery scopeQuery = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                storeCode,
                siteCode,
                null,
                null
        );
        validateRequestedStore(context, scopeQuery);
        if (!hasText(scopeQuery.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择当前店铺范围。");
        }
        List<Long> visibleAuthorizationIds = visibleAuthorizationIds(ownerUserId(context), scopeQuery, null);
        if (visibleAuthorizationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Excel 导入批次不存在。");
        }
        Ali1688HistoricalOrderExcelImportBatchRow batch = mapper.selectExcelImportBatchForDetail(
                ownerUserId(context),
                batchId,
                visibleAuthorizationIds,
                scopeQuery.getStoreCode(),
                scopeQuery.getSiteCode()
        );
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Excel 导入批次不存在。");
        }
        return Ali1688HistoricalOrderExcelImportView.BatchDetailView.from(batch);
    }

    public Ali1688HistoricalOrderExcelImportView.PreviewView previewExcelImport(
            BusinessAccessContext context,
            Ali1688HistoricalOrderExcelImportView.PreviewRequest request,
            MultipartFile file
    ) {
        Ali1688HistoricalOrderExcelImportView.PreviewRequest resolvedRequest = request == null
                ? new Ali1688HistoricalOrderExcelImportView.PreviewRequest()
                : request;
        Ali1688HistoricalOrderQuery scopeQuery = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                resolvedRequest.getStoreCode(),
                resolvedRequest.getSiteCode(),
                null,
                null
        );
        if (!hasText(scopeQuery.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 预览必须选择当前店铺范围。");
        }
        validateRequestedStore(context, scopeQuery);
        if (resolvedRequest.getAuthorizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 预览必须选择 1688 来源账号。");
        }
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderAuthorizationRow source = mapper.selectAuthorizationById(
                ownerUserId,
                resolvedRequest.getAuthorizationId()
        );
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "1688 Excel 来源账号不存在。");
        }
        List<Long> visibleAuthorizationIds = visibleAuthorizationIds(ownerUserId, scopeQuery, source);
        if (!visibleAuthorizationIds.contains(source.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前店铺未绑定该 1688 Excel 来源账号。");
        }
        byte[] fileBytes = readFileBytes(file);
        String fileName = hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : file.getName();
        Ali1688HistoricalOrderExcelParseResult parseResult = new Ali1688HistoricalOrderExcelParser().parse(
                new ByteArrayInputStream(fileBytes),
                fileName
        );
        Ali1688HistoricalOrderExcelImportBatchRow batch = toPreviewBatch(
                context,
                source,
                scopeQuery,
                fileName,
                fileBytes,
                parseResult
        );
        mapper.insertExcelImportBatch(batch);
        for (Ali1688HistoricalOrderExcelParseResult.Row parsedRow : parseResult.getRows()) {
            mapper.insertExcelImportRow(toExcelImportRow(context, source, batch, parsedRow));
        }
        return Ali1688HistoricalOrderExcelImportView.PreviewView.fromBatch(batch, source, parseResult);
    }

    public Ali1688HistoricalOrderExcelImportView.CommitView commitExcelImport(
            BusinessAccessContext context,
            Ali1688HistoricalOrderExcelImportView.CommitRequest request
    ) {
        if (request == null || request.getBatchId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要确认导入的 Excel 预览批次。");
        }
        Ali1688HistoricalOrderQuery scopeQuery = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                request.getStoreCode(),
                request.getSiteCode(),
                null,
                null
        );
        validateRequestedStore(context, scopeQuery);
        Ali1688HistoricalOrderExcelImportBatchRow batch =
                mapper.selectExcelImportBatch(ownerUserId(context), request.getBatchId());
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Excel 预览批次不存在。");
        }
        if (!"preview_ready".equals(batch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有预览通过的 Excel 批次可以确认导入。");
        }
        if (hasText(scopeQuery.getStoreCode()) && !scopeQuery.getStoreCode().equals(batch.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前店铺与 Excel 预览批次不一致。");
        }
        Ali1688HistoricalOrderAuthorizationRow source =
                mapper.selectAuthorizationById(ownerUserId(context), batch.getAuthorizationId());
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Excel 来源账号不存在。");
        }
        List<Long> visibleAuthorizationIds = visibleAuthorizationIds(
                ownerUserId(context),
                Ali1688HistoricalOrderQuery.fromRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        batch.getStoreCode(),
                        batch.getSiteCode(),
                        null,
                        null
                ),
                source
        );
        if (!visibleAuthorizationIds.contains(source.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前店铺未绑定该 Excel 来源账号。");
        }
        List<Ali1688HistoricalOrderExcelImportRow> rows =
                emptyList(mapper.listExcelImportRows(ownerUserId(context), batch.getId()));
        Ali1688HistoricalOrderExcelImportView.CommitCountsView counts =
                new Ali1688HistoricalOrderExcelImportView.CommitCountsView();
        Map<String, Long> orderIdsByOrderNo = new LinkedHashMap<>();
        for (Ali1688HistoricalOrderExcelImportRow row : rows) {
            Long orderId = orderIdsByOrderNo.get(row.getOrderNo());
            if (orderId == null) {
                String orderNaturalKey = orderNaturalKey(source, row);
                Long existingOrderId = existingIdOrNull(
                        mapper.selectOrderIdByNaturalKey(ownerUserId(context), orderNaturalKey)
                );
                orderId = existingOrderId == null ? mapper.nextOrderId() : existingOrderId;
                orderIdsByOrderNo.put(row.getOrderNo(), orderId);
                String existingSnapshot = existingOrderId == null
                        ? null
                        : mapper.selectOrderRawSnapshotByNaturalKey(ownerUserId(context), orderNaturalKey);
                if (existingOrderId != null && sameSnapshot(existingSnapshot, row.getRawSnapshotJson())) {
                    counts.setSkippedOrderCount(counts.getSkippedOrderCount() + 1);
                } else {
                    mapper.upsertOrder(toOrderFact(context, source, orderId, row));
                    if (existingOrderId == null) {
                        counts.setInsertedOrderCount(counts.getInsertedOrderCount() + 1);
                    } else {
                        counts.setUpdatedOrderCount(counts.getUpdatedOrderCount() + 1);
                    }
                }
            }
            String itemNaturalKey = itemNaturalKey(source, row);
            Long existingItemId = existingIdOrNull(
                    mapper.selectOrderItemIdByNaturalKey(ownerUserId(context), itemNaturalKey)
            );
            Long itemId = existingItemId == null ? mapper.nextOrderItemId() : existingItemId;
            String existingItemSnapshot = existingItemId == null
                    ? null
                    : mapper.selectOrderItemRawSnapshotByNaturalKey(ownerUserId(context), itemNaturalKey);
            if (existingItemId != null && sameSnapshot(existingItemSnapshot, row.getRawSnapshotJson())) {
                counts.setSkippedItemCount(counts.getSkippedItemCount() + 1);
            } else {
                mapper.upsertOrderItem(toItemFact(itemId, orderId, source, row, itemNaturalKey));
                if (existingItemId == null) {
                    counts.setInsertedItemCount(counts.getInsertedItemCount() + 1);
                } else {
                    counts.setUpdatedItemCount(counts.getUpdatedItemCount() + 1);
                }
            }
            if (hasText(row.getLogisticsCompany()) || hasText(row.getTrackingNo())) {
                String logisticsNaturalKey = logisticsNaturalKey(source, row, itemId);
                Long existingLogisticsId = existingIdOrNull(
                        mapper.selectOrderLogisticsIdByNaturalKey(ownerUserId(context), logisticsNaturalKey)
                );
                String existingLogisticsSnapshot = existingLogisticsId == null
                        ? null
                        : mapper.selectOrderLogisticsRawSnapshotByNaturalKey(ownerUserId(context), logisticsNaturalKey);
                if (existingLogisticsId != null && sameSnapshot(existingLogisticsSnapshot, row.getRawSnapshotJson())) {
                    counts.setSkippedLogisticsCount(counts.getSkippedLogisticsCount() + 1);
                } else {
                    Long logisticsId = existingLogisticsId == null ? mapper.nextOrderLogisticsId() : existingLogisticsId;
                    mapper.upsertOrderLogistics(toLogisticsFact(logisticsId, orderId, itemId, source, row, logisticsNaturalKey));
                    if (existingLogisticsId == null) {
                        counts.setInsertedLogisticsCount(counts.getInsertedLogisticsCount() + 1);
                    } else {
                        counts.setUpdatedLogisticsCount(counts.getUpdatedLogisticsCount() + 1);
                    }
                }
            }
        }
        mapper.markExcelImportBatchCommitted(
                batch.getId(),
                ownerUserId(context),
                counts.getInsertedOrderCount() + counts.getUpdatedOrderCount() + counts.getSkippedOrderCount(),
                counts.getInsertedItemCount() + counts.getUpdatedItemCount() + counts.getSkippedItemCount(),
                counts.getInsertedLogisticsCount() + counts.getUpdatedLogisticsCount() + counts.getSkippedLogisticsCount(),
                operatorUserId(context)
        );
        return Ali1688HistoricalOrderExcelImportView.CommitView.committed(batch.getId(), counts);
    }

    private Ali1688HistoricalOrderExcelImportRow toExcelImportRow(
            BusinessAccessContext context,
            Ali1688HistoricalOrderAuthorizationRow source,
            Ali1688HistoricalOrderExcelImportBatchRow batch,
            Ali1688HistoricalOrderExcelParseResult.Row parsedRow
    ) {
        Ali1688HistoricalOrderExcelImportRow row = new Ali1688HistoricalOrderExcelImportRow();
        row.setId(mapper.nextExcelImportRowId());
        row.setBatchId(batch.getId());
        row.setOwnerUserId(ownerUserId(context));
        row.setAuthorizationId(source.getId());
        row.setRowNumber(parsedRow.getRowNumber());
        row.setContinuationRow(parsedRow.isContinuationRow());
        row.setOrderNo(parsedRow.getOrderNo());
        row.setBuyerCompanyName(parsedRow.getBuyerCompanyName());
        row.setBuyerMemberName(parsedRow.getBuyerMemberName());
        row.setSupplierName(parsedRow.getSupplierName());
        row.setSellerMemberName(parsedRow.getSellerMemberName());
        row.setGoodsTotalText(parsedRow.getGoodsTotalText());
        row.setFreightText(parsedRow.getFreightText());
        row.setAdjustmentText(parsedRow.getAdjustmentText());
        row.setPaidAmountText(parsedRow.getPaidAmountText());
        row.setOrderStatus(parsedRow.getOrderStatus());
        row.setOrderTime(parsedRow.getOrderTime());
        row.setPaidAt(parsedRow.getPaidAt());
        row.setShipperName(parsedRow.getShipperName());
        row.setReceiverName(parsedRow.getReceiverName());
        row.setReceiverPostalCode(parsedRow.getReceiverPostalCode());
        row.setReceiverTelephone(parsedRow.getReceiverTelephone());
        row.setReceiverMobile(parsedRow.getReceiverMobile());
        row.setReceiverAddress(parsedRow.getReceiverAddress());
        row.setBuyerRemark(parsedRow.getBuyerRemark());
        row.setTitle(parsedRow.getTitle());
        row.setOfferId(parsedRow.getOfferId());
        row.setSkuId(parsedRow.getSkuId());
        row.setProductCode(parsedRow.getProductCode());
        row.setModelText(parsedRow.getModelText());
        row.setSingleProductCode(parsedRow.getSingleProductCode());
        row.setQuantityText(parsedRow.getQuantityText());
        row.setUnit(parsedRow.getUnit());
        row.setUnitPriceText(parsedRow.getUnitPriceText());
        row.setLogisticsCompany(parsedRow.getLogisticsCompany());
        row.setTrackingNo(parsedRow.getTrackingNo());
        row.setSourceBatchNo(parsedRow.getSourceBatchNo());
        row.setDownstreamChannel(parsedRow.getDownstreamChannel());
        row.setDownstreamOrderNo(parsedRow.getDownstreamOrderNo());
        row.setInitiatorLoginName(parsedRow.getInitiatorLoginName());
        row.setRawSnapshotJson("{\"source\":\"excel_upload\",\"rowNumber\":" + parsedRow.getRowNumber() + "}");
        return row;
    }

    private Ali1688HistoricalOrderRow toOrderFact(
            BusinessAccessContext context,
            Ali1688HistoricalOrderAuthorizationRow source,
            Long orderId,
            Ali1688HistoricalOrderExcelImportRow row
    ) {
        Ali1688HistoricalOrderRow order = new Ali1688HistoricalOrderRow();
        order.setId(orderId);
        order.setOwnerUserId(ownerUserId(context));
        order.setAuthorizationId(source.getId());
        order.setOrderNaturalKey(source.getId() + ":" + row.getOrderNo());
        order.setProviderOrderNo(row.getOrderNo());
        order.setOrderTime(row.getOrderTime());
        order.setPaidAt(row.getPaidAt());
        order.setBuyerCompanyName(row.getBuyerCompanyName());
        order.setBuyerMemberName(row.getBuyerMemberName());
        order.setSupplierName(row.getSupplierName());
        order.setSellerMemberName(row.getSellerMemberName());
        order.setGoodsTotalText(row.getGoodsTotalText());
        order.setFreightText(row.getFreightText());
        order.setAdjustmentText(row.getAdjustmentText());
        order.setPaidAmountText(row.getPaidAmountText());
        order.setAmountText(row.getPaidAmountText());
        order.setAmountValue(parseMoney(row.getPaidAmountText()));
        order.setCurrency("CNY");
        order.setOrderStatus(row.getOrderStatus());
        order.setShipperName(row.getShipperName());
        order.setReceiverName(row.getReceiverName());
        order.setReceiverPostalCode(row.getReceiverPostalCode());
        order.setReceiverTelephone(row.getReceiverTelephone());
        order.setReceiverMobile(row.getReceiverMobile());
        order.setReceiverPhone(row.getReceiverMobile());
        order.setReceiverAddress(row.getReceiverAddress());
        order.setBuyerRemark(row.getBuyerRemark());
        order.setInitiatorLoginName(row.getInitiatorLoginName());
        order.setSourceBatchNo(row.getSourceBatchNo());
        order.setDownstreamOrderNo(row.getDownstreamOrderNo());
        order.setRawSnapshotJson(row.getRawSnapshotJson());
        return order;
    }

    private Ali1688HistoricalOrderItemRow toItemFact(
            Long itemId,
            Long orderId,
            Ali1688HistoricalOrderAuthorizationRow source,
            Ali1688HistoricalOrderExcelImportRow row,
            String itemNaturalKey
    ) {
        Ali1688HistoricalOrderItemRow item = new Ali1688HistoricalOrderItemRow();
        item.setId(itemId);
        item.setOrderId(orderId);
        item.setItemNaturalKey(itemNaturalKey);
        item.setOfferId(row.getOfferId());
        item.setSkuId(row.getSkuId());
        item.setTitle(row.getTitle());
        item.setModelText(row.getModelText());
        item.setProductCode(row.getProductCode());
        item.setSingleProductCode(row.getSingleProductCode());
        item.setQuantity(parseInteger(row.getQuantityText()));
        item.setUnit(row.getUnit());
        item.setUnitPriceText(row.getUnitPriceText());
        item.setRawSnapshotJson(row.getRawSnapshotJson());
        return item;
    }

    private Ali1688HistoricalOrderLogisticsRow toLogisticsFact(
            Long logisticsId,
            Long orderId,
            Long itemId,
            Ali1688HistoricalOrderAuthorizationRow source,
            Ali1688HistoricalOrderExcelImportRow row,
            String logisticsNaturalKey
    ) {
        Ali1688HistoricalOrderLogisticsRow logistics = new Ali1688HistoricalOrderLogisticsRow();
        logistics.setId(logisticsId);
        logistics.setOrderId(orderId);
        logistics.setItemId(itemId);
        logistics.setLogisticsNaturalKey(logisticsNaturalKey);
        logistics.setLogisticsCompany(row.getLogisticsCompany());
        logistics.setTrackingNo(row.getTrackingNo());
        logistics.setRawSnapshotJson(row.getRawSnapshotJson());
        return logistics;
    }

    private String orderNaturalKey(Ali1688HistoricalOrderAuthorizationRow source, Ali1688HistoricalOrderExcelImportRow row) {
        return source.getId() + ":" + row.getOrderNo();
    }

    private String itemNaturalKey(Ali1688HistoricalOrderAuthorizationRow source, Ali1688HistoricalOrderExcelImportRow row) {
        return source.getId() + ":" + row.getOrderNo() + ":" + nullToEmpty(row.getOfferId())
                + ":" + nullToEmpty(row.getSkuId()) + ":" + nullToEmpty(row.getProductCode())
                + ":" + nullToEmpty(row.getModelText()) + ":" + nullToEmpty(row.getSingleProductCode())
                + ":" + row.getRowNumber();
    }

    private String logisticsNaturalKey(
            Ali1688HistoricalOrderAuthorizationRow source,
            Ali1688HistoricalOrderExcelImportRow row,
            Long itemId
    ) {
        return source.getId() + ":" + row.getOrderNo() + ":" + itemId
                + ":" + nullToEmpty(row.getLogisticsCompany()) + ":" + nullToEmpty(row.getTrackingNo());
    }

    private boolean sameSnapshot(String existingSnapshot, String newSnapshot) {
        return existingSnapshot != null && existingSnapshot.equals(newSnapshot);
    }

    private Long existingIdOrNull(Long existingId) {
        return existingId == null || existingId <= 0 ? null : existingId;
    }

    private Ali1688HistoricalOrderExcelImportBatchRow toPreviewBatch(
            BusinessAccessContext context,
            Ali1688HistoricalOrderAuthorizationRow source,
            Ali1688HistoricalOrderQuery scopeQuery,
            String fileName,
            byte[] fileBytes,
            Ali1688HistoricalOrderExcelParseResult parseResult
    ) {
        Ali1688HistoricalOrderExcelParseResult.Summary summary = parseResult.getSummary();
        Ali1688HistoricalOrderExcelImportBatchRow batch = new Ali1688HistoricalOrderExcelImportBatchRow();
        batch.setId(mapper.nextExcelImportBatchId());
        batch.setOwnerUserId(ownerUserId(context));
        batch.setAuthorizationId(source.getId());
        batch.setStoreCode(scopeQuery.getStoreCode());
        batch.setSiteCode(scopeQuery.getSiteCode());
        batch.setFileName(fileName);
        batch.setFileSize((long) fileBytes.length);
        batch.setFileHash(sha256Hex(fileBytes));
        boolean validationPassed = parseResult.getHeaderValidation().isValid() && parseResult.getRowErrors().isEmpty();
        batch.setStatus(validationPassed ? "preview_ready" : "validation_failed");
        batch.setHeaderVersion("ali1688_historical_order_export_v1");
        batch.setOrderHeaderRowCount(summary.getOrderHeaderRowCount());
        batch.setProductLineCount(summary.getProductLineCount());
        batch.setLogisticsLineCount(summary.getLogisticsLineCount());
        batch.setValidRowCount(summary.getValidRowCount());
        batch.setDuplicateCandidateCount(summary.getDuplicateCandidateCount());
        batch.setErrorCount(parseResult.getRowErrors().size());
        batch.setWarningCount(parseResult.getRowWarnings().size());
        if (!validationPassed) {
            batch.setFailureCode(previewFailureCode(parseResult));
            batch.setFailureMessage(previewFailureMessage(parseResult));
        }
        batch.setErrorSummaryJson("{\"rowErrors\":" + parseResult.getRowErrors().size()
                + ",\"rowWarnings\":" + parseResult.getRowWarnings().size() + "}");
        batch.setCreatedBy(operatorUserId(context));
        batch.setUpdatedBy(operatorUserId(context));
        return batch;
    }

    private byte[] readFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new Ali1688HistoricalOrderExcelImportException(
                    HttpStatus.BAD_REQUEST,
                    Ali1688HistoricalOrderExcelImportFailureCode.EMPTY_WORKBOOK,
                    "请上传 1688 历史订单 Excel 文件。"
            );
        }
        String fileName = hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : file.getName();
        if (!hasText(fileName) || !fileName.toLowerCase().endsWith(".xlsx")) {
            throw new Ali1688HistoricalOrderExcelImportException(
                    HttpStatus.BAD_REQUEST,
                    Ali1688HistoricalOrderExcelImportFailureCode.UNSUPPORTED_FILE_TYPE,
                    "仅支持上传 1688 历史订单 .xlsx 文件。"
            );
        }
        if (file.getSize() > MAX_EXCEL_IMPORT_FILE_SIZE_BYTES) {
            throw new Ali1688HistoricalOrderExcelImportException(
                    HttpStatus.BAD_REQUEST,
                    Ali1688HistoricalOrderExcelImportFailureCode.FILE_TOO_LARGE,
                    "Excel 文件过大，请拆分后再上传。"
            );
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new Ali1688HistoricalOrderExcelImportException(
                    HttpStatus.BAD_REQUEST,
                    Ali1688HistoricalOrderExcelImportFailureCode.DAMAGED_WORKBOOK,
                    "Excel 文件读取失败，请重新上传。"
            );
        }
    }

    private String previewFailureCode(Ali1688HistoricalOrderExcelParseResult parseResult) {
        if (!parseResult.getHeaderValidation().isValid()) {
            return Ali1688HistoricalOrderExcelImportFailureCode.HEADER_MISMATCH.getCode();
        }
        if (!parseResult.getRowErrors().isEmpty()) {
            return parseResult.getRowErrors().get(0).getCode();
        }
        return null;
    }

    private String previewFailureMessage(Ali1688HistoricalOrderExcelParseResult parseResult) {
        if (!parseResult.getHeaderValidation().isValid()) {
            return parseResult.getHeaderValidation().getMessage();
        }
        if (!parseResult.getRowErrors().isEmpty()) {
            return parseResult.getRowErrors().get(0).getMessage();
        }
        return null;
    }

    private String sha256Hex(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fileBytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", ex);
        }
    }

    private Ali1688HistoricalOrderSyncTaskRow createInitialBackfillTask(
            BusinessAccessContext context,
            Long ownerUserId,
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        return createSyncTask(context, ownerUserId, authorization, "initial_backfill");
    }

    private Ali1688HistoricalOrderSyncTaskRow createSyncTask(
            BusinessAccessContext context,
            Long ownerUserId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            String taskType
    ) {
        Ali1688HistoricalOrderSyncTaskRow task = new Ali1688HistoricalOrderSyncTaskRow();
        task.setId(mapper.nextSyncTaskId());
        task.setOwnerUserId(ownerUserId);
        task.setAuthorizationId(authorization.getId());
        task.setTaskType(taskType);
        task.setStatus("running");
        task.setProcessedCount(0);
        task.setImportedCount(0);
        task.setFailedCount(0);
        task.setProgressPercent(0);
        task.setCheckpointJson(checkpointJson(null));
        task.setCreatedBy(operatorUserId(context));
        task.setUpdatedBy(operatorUserId(context));
        mapper.insertSyncTask(task);
        return task;
    }

    public Ali1688HistoricalOrderWorkbenchView runInitialBackfill(BusinessAccessContext context) {
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderAuthorizationRow authorization = ownerUserId == null
                ? null
                : mapper.selectCurrentAuthorization(ownerUserId);
        if (authorization == null) {
            return Ali1688HistoricalOrderWorkbenchView.noAuthorization(context);
        }

        Ali1688HistoricalOrderSyncTaskRow task = mapper.selectLatestResumableTask(ownerUserId, authorization.getId());
        if (task == null) {
            task = createInitialBackfillTask(context, ownerUserId, authorization);
        }

        return executeProviderSync(context, ownerUserId, authorization, task);
    }

    public Ali1688HistoricalOrderWorkbenchView runManualRefresh(BusinessAccessContext context) {
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderAuthorizationRow authorization = ownerUserId == null
                ? null
                : mapper.selectCurrentAuthorization(ownerUserId);
        if (authorization == null) {
            return Ali1688HistoricalOrderWorkbenchView.noAuthorization(context);
        }
        if (LOCAL_EXCEL_PROVIDER_CODE.equals(authorization.getProviderCode())) {
            return buildAuthorizedWorkbench(context, authorization, Ali1688HistoricalOrderQuery.defaultQuery());
        }
        Ali1688HistoricalOrderSyncTaskRow task = createSyncTask(
                context,
                ownerUserId,
                authorization,
                "manual_refresh"
        );
        return executeProviderSync(context, ownerUserId, authorization, task);
    }

    private Ali1688HistoricalOrderWorkbenchView executeProviderSync(
            BusinessAccessContext context,
            Long ownerUserId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderSyncTaskRow task
    ) {
        int processedCount = defaultInt(task.getProcessedCount());
        int importedItemCount = defaultInt(task.getImportedCount());
        int failedCount = defaultInt(task.getFailedCount());
        String cursor = cursorFromCheckpoint(task.getCheckpointJson());

        while (true) {
            Ali1688HistoricalOrderProvider.Page page = provider.fetchPage(authorization, cursor);
            for (Ali1688HistoricalOrderProvider.OrderSnapshot orderSnapshot : page.getOrders()) {
                processedCount++;
                Long orderId = mapper.nextOrderId();
                Ali1688HistoricalOrderRow order = toOrderRow(ownerUserId, authorization, orderId, orderSnapshot);
                mapper.upsertOrder(order);

                int lineNo = 1;
                for (Ali1688HistoricalOrderProvider.OrderItemSnapshot itemSnapshot : orderSnapshot.getItems()) {
                    Ali1688HistoricalOrderItemRow item = toItemRow(
                            mapper.nextOrderItemId(),
                            orderId,
                            authorization,
                            orderSnapshot,
                            itemSnapshot,
                            lineNo
                    );
                    mapper.upsertOrderItem(item);
                    if (hasText(itemSnapshot.getLogisticsCompany()) || hasText(itemSnapshot.getTrackingNo())) {
                        Ali1688HistoricalOrderLogisticsRow logistics = toLogisticsRow(
                                mapper.nextOrderLogisticsId(),
                                orderId,
                                item.getId(),
                                authorization,
                                orderSnapshot,
                                itemSnapshot,
                                lineNo
                        );
                        mapper.upsertOrderLogistics(logistics);
                    }
                    importedItemCount++;
                    lineNo++;
                }
            }

            String checkpointJson = checkpointJson(page.getNextCursor());
            if (page.hasFailure()) {
                failedCount++;
                Ali1688HistoricalOrderFailureCode failureCode =
                        Ali1688HistoricalOrderFailureCode.fromCode(page.getFailureCode());
                boolean retryable = page.isRetryableFailure() || failureCode.isRetryable();
                boolean requiresManualAction = failureCode.isRequiresManualAction();
                if (processedCount == 0 && importedItemCount == 0) {
                    mapper.markSyncTaskFailed(
                            task.getId(),
                            processedCount,
                            importedItemCount,
                            failedCount,
                            failureCode.getCode(),
                            page.getFailureMessage(),
                            checkpointJson,
                            retryable,
                            requiresManualAction
                    );
                    return buildWorkbench(context);
                }
                mapper.markSyncTaskPartialSuccess(
                        task.getId(),
                        processedCount,
                        importedItemCount,
                        failedCount,
                        failureCode.getCode(),
                        page.getFailureMessage(),
                        checkpointJson,
                        retryable,
                        requiresManualAction
                );
                return buildWorkbench(context);
            }
            if (!page.isHasMore()) {
                mapper.markSyncTaskSuccess(task.getId(), processedCount, importedItemCount, failedCount, checkpointJson);
                return buildWorkbench(context);
            }
            mapper.updateSyncTaskCheckpoint(
                    task.getId(),
                    checkpointJson,
                    page.getProgressPercent(),
                    processedCount,
                    importedItemCount,
                    failedCount
            );
            cursor = page.getNextCursor();
        }
    }

    public Ali1688HistoricalOrderWorkbenchView revokeAuthorization(
            BusinessAccessContext context,
            Long authorizationId
    ) {
        mapper.revokeAuthorization(authorizationId, ownerUserId(context), operatorUserId(context));
        return Ali1688HistoricalOrderWorkbenchView.noAuthorization(context);
    }

    public Ali1688HistoricalOrderWorkbenchView.OrderDetailView orderDetail(
            BusinessAccessContext context,
            Long orderId
    ) {
        return orderDetail(context, orderId, Ali1688HistoricalOrderQuery.defaultQuery());
    }

    public Ali1688HistoricalOrderWorkbenchView.OrderDetailView orderDetail(
            BusinessAccessContext context,
            Long orderId,
            Ali1688HistoricalOrderQuery query
    ) {
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderAuthorizationRow authorization = ownerUserId == null
                ? null
                : mapper.selectCurrentAuthorization(ownerUserId);
        if (authorization == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "1688 历史订单授权不存在。");
        }
        Ali1688HistoricalOrderQuery resolvedQuery = query == null
                ? Ali1688HistoricalOrderQuery.defaultQuery()
                : query;
        validateRequestedStore(context, resolvedQuery);
        List<Long> visibleAuthorizationIds = visibleAuthorizationIds(ownerUserId, resolvedQuery, authorization);
        if (visibleAuthorizationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前店铺还没有绑定 1688 历史订单授权。");
        }
        Ali1688HistoricalOrderRow order = mapper.selectOrderById(ownerUserId, visibleAuthorizationIds, orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "1688 历史订单不存在。");
        }
        List<Long> orderIds = List.of(order.getId());
        List<Ali1688HistoricalOrderItemRow> itemRows = emptyList(mapper.listOrderItems(ownerUserId, orderIds));
        Map<Long, List<Ali1688HistoricalOrderLogisticsRow>> logisticsByItemId =
                logisticsByItemId(ownerUserId, orderIds);
        Map<Long, Ali1688HistoricalOrderItemAssignmentSummaryRow> assignmentSummaryByItemId =
                assignmentSummaryByItemId(ownerUserId, itemRows);
        List<Ali1688HistoricalOrderWorkbenchView.OrderItemView> items = itemRows.stream()
                .map(row -> Ali1688HistoricalOrderWorkbenchView.OrderItemView.fromRow(
                        row,
                        firstLogistics(logisticsByItemId.get(row.getId())),
                        assignmentSummaryByItemId.get(row.getId())
                ))
                .collect(Collectors.toList());
        return Ali1688HistoricalOrderWorkbenchView.OrderDetailView.fromRow(
                order,
                items,
                Ali1688HistoricalOrderSensitiveFieldPolicy.apply(context, order)
        );
    }

    @Transactional
    public Ali1688HistoricalOrderAssignmentView.AssignResult assignProductLines(
            BusinessAccessContext context,
            Ali1688HistoricalOrderAssignmentView.AssignRequest request
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要分配的货品行。");
        }
        String targetType = normalizeAssignmentTargetType(request.getTargetType());
        boolean consumableAssignment = ASSIGNMENT_TARGET_CONSUMABLE.equals(targetType);
        Ali1688HistoricalOrderQuery targetScope = null;
        if (!consumableAssignment) {
            targetScope = Ali1688HistoricalOrderQuery.fromRequest(
                    null,
                    null,
                    null,
                    null,
                    null,
                    request.getTargetStoreCode(),
                    request.getTargetSiteCode(),
                    null,
                    null
            );
            if (!hasText(targetScope.getStoreCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择目标店铺。");
            }
            validateRequestedStore(context, targetScope);
        }
        List<Ali1688HistoricalOrderAssignmentView.AssignLineRequest> lines = emptyList(request.getLines());
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要分配的货品行。");
        }

        Map<Long, Integer> requestedQuantityByItemId = new LinkedHashMap<>();
        for (Ali1688HistoricalOrderAssignmentView.AssignLineRequest line : lines) {
            if (line == null || line.getItemId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "货品行不能为空。");
            }
            if (!consumableAssignment && (line.getQuantity() == null || line.getQuantity() <= 0)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分配数量必须大于 0。");
            }
            requestedQuantityByItemId.merge(line.getItemId(), consumableAssignment ? 0 : line.getQuantity(), Integer::sum);
        }

        Long ownerUserId = ownerUserId(context);
        Map<Long, Ali1688HistoricalOrderItemAssignmentSummaryRow> assignedByItemId =
                emptyList(mapper.listOrderItemAssignmentSummaries(ownerUserId, new ArrayList<>(requestedQuantityByItemId.keySet())))
                        .stream()
                        .filter(row -> row.getItemId() != null)
                        .collect(Collectors.toMap(
                                Ali1688HistoricalOrderItemAssignmentSummaryRow::getItemId,
                                row -> row,
                                (left, right) -> right,
                                LinkedHashMap::new
                        ));
        Map<Long, Ali1688HistoricalOrderItemRow> itemById = new LinkedHashMap<>();
        for (Long itemId : requestedQuantityByItemId.keySet()) {
            Ali1688HistoricalOrderItemRow item = mapper.selectOrderItemForAssignment(ownerUserId, itemId);
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "货品行不存在或无权限分配。");
            }
            if (item.getQuantity() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "货品行数量未返回，不能分配。");
            }
            int alreadyAssigned = assignedByItemId.containsKey(itemId)
                    ? defaultInt(assignedByItemId.get(itemId).getAssignedQuantity())
                    : 0;
            Ali1688HistoricalOrderItemAssignmentSummaryRow assignmentSummary = assignedByItemId.get(itemId);
            if (!consumableAssignment && assignmentSummary != null
                    && defaultInt(assignmentSummary.getConsumableAssignmentCount()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "货品行已标记为耗材，请先撤回耗材分配。");
            }
            if (consumableAssignment && assignmentSummary != null
                    && defaultInt(assignmentSummary.getStoreSiteAssignmentCount()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "货品行已分配到店铺，请先撤回店铺分配。");
            }
            int remainingQuantity = item.getQuantity() - alreadyAssigned;
            int requestedQuantity = consumableAssignment ? item.getQuantity() : requestedQuantityByItemId.get(itemId);
            if (requestedQuantity > remainingQuantity) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分配数量不能超过剩余数量。");
            }
            requestedQuantityByItemId.put(itemId, requestedQuantity);
            itemById.put(itemId, item);
        }

        int totalAssignedQuantity = 0;
        for (Map.Entry<Long, Integer> entry : requestedQuantityByItemId.entrySet()) {
            Ali1688HistoricalOrderItemRow item = itemById.get(entry.getKey());
            Ali1688HistoricalOrderItemAssignmentRow assignment = new Ali1688HistoricalOrderItemAssignmentRow();
            assignment.setId(mapper.nextOrderItemAssignmentId());
            assignment.setOwnerUserId(ownerUserId);
            assignment.setAuthorizationId(item.getAuthorizationId());
            assignment.setOrderId(item.getOrderId());
            assignment.setItemId(item.getId());
            assignment.setTargetType(targetType);
            assignment.setTargetStoreCode(consumableAssignment ? null : targetScope.getStoreCode());
            assignment.setTargetSiteCode(consumableAssignment ? null : hasText(targetScope.getSiteCode()) ? targetScope.getSiteCode() : "*");
            assignment.setAssignedQuantity(entry.getValue());
            assignment.setStatus("active");
            assignment.setRemark(consumableAssignment ? "1688 历史订单货品行标记为耗材。" : "1688 历史订单货品行分配。");
            assignment.setCreatedBy(operatorUserId(context));
            assignment.setUpdatedBy(operatorUserId(context));
            mapper.insertOrderItemAssignment(assignment);
            totalAssignedQuantity += entry.getValue();
        }

        return Ali1688HistoricalOrderAssignmentView.AssignResult.assigned(
                requestedQuantityByItemId.size(),
                totalAssignedQuantity
        );
    }

    public List<Ali1688HistoricalOrderAssignmentView.RecordView> listProductLineAssignments(
            BusinessAccessContext context,
            Long itemId
    ) {
        if (itemId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要查看的货品行。");
        }
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderItemRow item = mapper.selectOrderItemForAssignment(ownerUserId, itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "货品行不存在或无权限查看。");
        }
        return emptyList(mapper.listOrderItemAssignments(ownerUserId, itemId))
                .stream()
                .filter(row -> canAccessAssignmentTarget(context, row))
                .map(Ali1688HistoricalOrderAssignmentView.RecordView::fromRow)
                .collect(Collectors.toList());
    }

    @Transactional
    public Ali1688HistoricalOrderAssignmentView.AssignResult adjustAssignmentQuantity(
            BusinessAccessContext context,
            Long assignmentId,
            Ali1688HistoricalOrderAssignmentView.AdjustRequest request
    ) {
        if (assignmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要调整的分配记录。");
        }
        if (request == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分配数量必须大于 0。");
        }
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                mapper.selectOrderItemAssignmentById(ownerUserId, assignmentId);
        validateMutableAssignment(context, assignment);
        if (isConsumableAssignment(assignment)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "耗材使用整条货品行数量，请撤回后重新分配。");
        }
        Ali1688HistoricalOrderItemRow item = mapper.selectOrderItemForAssignment(ownerUserId, assignment.getItemId());
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "货品行不存在或无权限分配。");
        }
        if (item.getQuantity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "货品行数量未返回，不能调整分配。");
        }
        int otherAssignedQuantity = defaultInt(mapper.sumAssignedQuantityExcludingAssignment(
                ownerUserId,
                assignment.getItemId(),
                assignmentId
        ));
        if (otherAssignedQuantity + request.getQuantity() > item.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分配数量不能超过货品行原始数量。");
        }
        mapper.updateOrderItemAssignmentQuantity(
                assignmentId,
                ownerUserId,
                request.getQuantity(),
                operatorUserId(context)
        );
        return Ali1688HistoricalOrderAssignmentView.AssignResult.assigned(1, request.getQuantity());
    }

    @Transactional
    public Ali1688HistoricalOrderAssignmentView.AssignResult revokeAssignment(
            BusinessAccessContext context,
            Long assignmentId
    ) {
        if (assignmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要撤回的分配记录。");
        }
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                mapper.selectOrderItemAssignmentById(ownerUserId, assignmentId);
        validateMutableAssignment(context, assignment);
        mapper.revokeOrderItemAssignment(assignmentId, ownerUserId, operatorUserId(context));
        return Ali1688HistoricalOrderAssignmentView.AssignResult.assigned(1, 0);
    }

    @Transactional
    public Ali1688HistoricalOrderProductLinkView.LinkResult linkProductLine(
            BusinessAccessContext context,
            Ali1688HistoricalOrderProductLinkView.LinkRequest request
    ) {
        if (request == null || request.getAssignmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要关联商品的分配记录。");
        }
        String skuParent = trimToNull(request.getSkuParent());
        if (!hasText(skuParent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要关联的商品 SKU。");
        }
        requireProductLinkWriteAccess(context);
        Long ownerUserId = ownerUserId(context);
        Long operatorUserId = operatorUserId(context);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                mapper.selectOrderItemAssignmentById(ownerUserId, request.getAssignmentId());
        validateMutableAssignment(context, assignment);
        if (isConsumableAssignment(assignment)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "耗材分配不能关联店铺商品。");
        }
        if (!hasText(assignment.getTargetStoreCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先把货品行分配到店铺后再关联商品。");
        }
        validateProductSkuInAssignmentTarget(ownerUserId, assignment, skuParent);

        Ali1688HistoricalOrderProductLinkRow link = new Ali1688HistoricalOrderProductLinkRow();
        link.setId(mapper.nextOrderItemProductLinkId());
        link.setOwnerUserId(ownerUserId);
        link.setAuthorizationId(assignment.getAuthorizationId());
        link.setOrderId(assignment.getOrderId());
        link.setItemId(assignment.getItemId());
        link.setAssignmentId(assignment.getId());
        link.setTargetStoreCode(assignment.getTargetStoreCode());
        link.setTargetSiteCode(assignment.getTargetSiteCode());
        link.setSkuParent(skuParent);
        link.setPartnerSku(trimToNull(request.getPartnerSku()));
        link.setPskuCode(trimToNull(request.getPskuCode()));
        link.setProductTitle(trimToNull(request.getProductTitle()));
        link.setProductImageUrl(trimToNull(request.getProductImageUrl()));
        link.setStatus("active");
        link.setCreatedBy(operatorUserId);
        link.setUpdatedBy(operatorUserId);

        Ali1688HistoricalOrderProductLinkRow activeLink =
                mapper.selectActiveOrderItemProductLinkByAssignment(ownerUserId, assignment.getId());
        if (sameProductLink(activeLink, link)) {
            return Ali1688HistoricalOrderProductLinkView.LinkResult.linked(activeLink);
        }
        if (activeLink != null) {
            mapper.updateOrderItemProductLinkStatus(activeLink.getId(), ownerUserId, "replaced", operatorUserId);
        }
        mapper.insertOrderItemProductLink(link);
        mapper.insertOrderItemProductLinkAudit(productLinkAudit(
                mapper.nextOrderItemProductLinkAuditId(),
                ownerUserId,
                assignment.getId(),
                activeLink,
                link,
                activeLink == null ? "link" : "relink",
                operatorUserId
        ));
        return Ali1688HistoricalOrderProductLinkView.LinkResult.linked(link);
    }

    @Transactional
    public Ali1688HistoricalOrderProductLinkView.LinkResult unlinkProductLine(
            BusinessAccessContext context,
            Long assignmentId
    ) {
        if (assignmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要解除关联的分配记录。");
        }
        requireProductLinkWriteAccess(context);
        Long ownerUserId = ownerUserId(context);
        Long operatorUserId = operatorUserId(context);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                mapper.selectOrderItemAssignmentById(ownerUserId, assignmentId);
        validateMutableAssignment(context, assignment);
        Ali1688HistoricalOrderProductLinkRow activeLink =
                mapper.selectActiveOrderItemProductLinkByAssignment(ownerUserId, assignmentId);
        if (activeLink == null) {
            return Ali1688HistoricalOrderProductLinkView.LinkResult.unlinked(assignmentId);
        }
        mapper.updateOrderItemProductLinkStatus(activeLink.getId(), ownerUserId, "unlinked", operatorUserId);
        mapper.insertOrderItemProductLinkAudit(productLinkAudit(
                mapper.nextOrderItemProductLinkAuditId(),
                ownerUserId,
                assignmentId,
                activeLink,
                null,
                "unlink",
                operatorUserId
        ));
        return Ali1688HistoricalOrderProductLinkView.LinkResult.unlinked(assignmentId);
    }

    public List<Ali1688HistoricalOrderProductLinkView.AuditView> listProductLinkAudits(
            BusinessAccessContext context,
            Long assignmentId
    ) {
        if (assignmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要查看的分配记录。");
        }
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                mapper.selectOrderItemAssignmentById(ownerUserId, assignmentId);
        validateMutableAssignment(context, assignment);
        return emptyList(mapper.listOrderItemProductLinkAudits(ownerUserId, assignmentId))
                .stream()
                .map(Ali1688HistoricalOrderProductLinkView.AuditView::fromRow)
                .collect(Collectors.toList());
    }

    public List<Ali1688HistoricalOrderProductLinkView.CandidateView> listProductLinkCandidates(
            BusinessAccessContext context,
            Long assignmentId,
            String linkStatus,
            String keyword
    ) {
        if (assignmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要关联商品的分配记录。");
        }
        requireProductLinkWriteAccess(context);
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                mapper.selectOrderItemAssignmentById(ownerUserId, assignmentId);
        validateMutableAssignment(context, assignment);
        if (isConsumableAssignment(assignment) || !hasText(assignment.getTargetStoreCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先把货品行分配到店铺后再关联商品。");
        }
        String resolvedLinkStatus = normalizeProductLinkCandidateStatus(linkStatus);
        if (!hasText(resolvedLinkStatus)) {
            Ali1688HistoricalOrderProductLinkRow activeLink =
                    mapper.selectActiveOrderItemProductLinkByAssignment(ownerUserId, assignmentId);
            resolvedLinkStatus = activeLink == null ? "unlinked" : "linked";
        }
        return emptyList(mapper.listOrderItemProductLinkCandidates(
                        ownerUserId,
                        assignment.getTargetStoreCode(),
                        assignment.getTargetSiteCode(),
                        resolvedLinkStatus,
                        trimToNull(keyword)
                ))
                .stream()
                .map(Ali1688HistoricalOrderProductLinkView.CandidateView::fromRow)
                .collect(Collectors.toList());
    }

    public Ali1688SkuPurchaseHistoryView listSkuPurchaseHistory(
            BusinessAccessContext context,
            Ali1688SkuPurchaseHistoryQuery query
    ) {
        Ali1688SkuPurchaseHistoryQuery resolvedQuery = query == null
                ? Ali1688SkuPurchaseHistoryQuery.fromRequest(null, null, null, null, null)
                : query;
        if (!hasText(resolvedQuery.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要查看的店铺。");
        }
        validateRequestedStore(context, Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                resolvedQuery.getStoreCode(),
                resolvedQuery.getSiteCode(),
                null,
                null
        ));
        int unlinkedAssignedLineCount = mapper.countUnlinkedAssignedStoreSiteLines(
                ownerUserId(context),
                resolvedQuery.getStoreCode(),
                resolvedQuery.getSiteCode(),
                resolvedQuery.getKeyword(),
                resolvedQuery.getPurchaseTimeFrom(),
                resolvedQuery.getPurchaseTimeTo()
        );
        boolean unlinkedOnly = "unlinked".equalsIgnoreCase(resolvedQuery.getLinkStatus());
        boolean linkedOnly = "linked".equalsIgnoreCase(resolvedQuery.getLinkStatus());
        Map<String, SkuPurchaseHistoryAccumulator> bySku = new LinkedHashMap<>();

        List<Ali1688SkuPurchaseHistoryProductRow> productRows = emptyList(mapper.listSkuPurchaseHistoryProducts(
                ownerUserId(context),
                resolvedQuery.getStoreCode(),
                resolvedQuery.getSiteCode(),
                resolvedQuery.getKeyword()
        ));
        List<Ali1688SkuPurchaseHistoryRow> rows = emptyList(mapper.listSkuPurchaseHistoryRows(
                ownerUserId(context),
                resolvedQuery.getStoreCode(),
                resolvedQuery.getSiteCode(),
                resolvedQuery.getKeyword(),
                resolvedQuery.getPurchaseTimeFrom(),
                resolvedQuery.getPurchaseTimeTo()
        ));
        for (Ali1688SkuPurchaseHistoryProductRow productRow : productRows) {
            if (productRow == null || !hasText(productRow.getSkuParent())) {
                continue;
            }
            bySku.putIfAbsent(
                    purchaseHistoryGroupKey(productRow),
                    new SkuPurchaseHistoryAccumulator(productRow)
            );
        }
        Map<String, Boolean> seenAssignments = new LinkedHashMap<>();
        for (Ali1688SkuPurchaseHistoryRow row : rows) {
            if (row == null || !hasText(row.getSkuParent())) {
                continue;
            }
            String assignmentKey = purchaseHistoryAssignmentKey(row);
            if (seenAssignments.putIfAbsent(assignmentKey, Boolean.TRUE) != null) {
                continue;
            }
            String groupKey = purchaseHistoryGroupKey(row);
            bySku.computeIfAbsent(groupKey, key -> new SkuPurchaseHistoryAccumulator(row, "linked"))
                    .add(row, calculatePurchaseCost(row));
        }
        List<Ali1688SkuPurchaseHistoryView.ItemView> items = bySku.values()
                .stream()
                .map(SkuPurchaseHistoryAccumulator::toView)
                .filter(item -> !linkedOnly || "linked".equalsIgnoreCase(item.getLinkStatus()))
                .filter(item -> !unlinkedOnly || "unlinked".equalsIgnoreCase(item.getLinkStatus()))
                .collect(Collectors.toList());
        items.sort((left, right) -> nullToEmpty(right.getRecentPurchaseTime())
                .compareTo(nullToEmpty(left.getRecentPurchaseTime())));
        int total = items.size();
        int page = Math.max(1, resolvedQuery.getPage());
        int pageSize = Math.max(1, resolvedQuery.getPageSize());
        int fromIndex = Math.min(total, (page - 1) * pageSize);
        int toIndex = Math.min(total, fromIndex + pageSize);
        Ali1688SkuPurchaseHistoryView view = Ali1688SkuPurchaseHistoryView.of(
                new ArrayList<>(items.subList(fromIndex, toIndex)),
                page,
                pageSize,
                total
        );
        view.setUnlinkedAssignedLineCount(unlinkedAssignedLineCount);
        return view;
    }

    @Transactional
    public Ali1688HistoricalOrderCleanupView.DeleteOrderResult deleteHistoricalOrder(
            BusinessAccessContext context,
            Long orderId,
            Ali1688HistoricalOrderCleanupView.DeleteOrderRequest request
    ) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要删除的历史订单。");
        }
        Ali1688HistoricalOrderCleanupView.DeleteOrderRequest resolvedRequest = request == null
                ? new Ali1688HistoricalOrderCleanupView.DeleteOrderRequest()
                : request;
        Ali1688HistoricalOrderQuery scopeQuery = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                resolvedRequest.getStoreCode(),
                resolvedRequest.getSiteCode(),
                null,
                null
        );
        validateRequestedStore(context, scopeQuery);
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderAuthorizationRow authorization = ownerUserId == null
                ? null
                : mapper.selectCurrentAuthorization(ownerUserId);
        if (authorization == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "1688 历史订单授权不存在。");
        }
        List<Long> visibleAuthorizationIds = visibleAuthorizationIds(ownerUserId, scopeQuery, authorization);
        if (visibleAuthorizationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前店铺还没有绑定 1688 历史订单授权。");
        }
        Ali1688HistoricalOrderRow order = mapper.selectOrderById(ownerUserId, visibleAuthorizationIds, orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "1688 历史订单不存在。");
        }
        if (mapper.countActiveOrderAssignments(ownerUserId, orderId) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单已有有效分配记录，请先撤回分配。");
        }
        String deleteReason = hasText(resolvedRequest.getReason())
                ? resolvedRequest.getReason().trim()
                : "不属于任何店铺";
        mapper.softDeleteOrderHeader(orderId, ownerUserId, operatorUserId(context), deleteReason);
        mapper.softDeleteOrderItems(orderId);
        mapper.softDeleteOrderLogistics(orderId);
        return Ali1688HistoricalOrderCleanupView.DeleteOrderResult.deleted(orderId, deleteReason);
    }

    private Ali1688HistoricalOrderWorkbenchView buildAuthorizedWorkbench(
            BusinessAccessContext context,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderQuery query
    ) {
        Long ownerUserId = ownerUserId(context);
        Ali1688HistoricalOrderQuery resolvedQuery = query == null
                ? Ali1688HistoricalOrderQuery.defaultQuery()
                : query;
        validateRequestedStore(context, resolvedQuery);
        List<Long> visibleAuthorizationIds = visibleAuthorizationIds(ownerUserId, resolvedQuery, authorization);
        Ali1688HistoricalOrderSyncTaskRow latestTask = mapper.selectLatestTask(ownerUserId, authorization.getId());
        if (visibleAuthorizationIds.isEmpty()) {
            return Ali1688HistoricalOrderWorkbenchView.authorizedWithOrders(
                    context,
                    authorization,
                    List.of(),
                    0,
                    0,
                    latestTask,
                    resolvedQuery,
                    Ali1688HistoricalOrderWorkbenchView.StoreScopeView.unbound(resolvedQuery)
            );
        }
        Ali1688HistoricalOrderAuthorizationRow primaryAuthorization =
                mapper.selectAuthorizationById(ownerUserId, visibleAuthorizationIds.get(0));
        if (primaryAuthorization == null) {
            primaryAuthorization = authorization;
        }
        List<Ali1688HistoricalOrderRow> orders = mapper.listOrders(ownerUserId, visibleAuthorizationIds, resolvedQuery);
        List<Long> orderIds = orders.stream()
                .map(Ali1688HistoricalOrderRow::getId)
                .collect(Collectors.toList());
        Map<Long, List<Ali1688HistoricalOrderLogisticsRow>> logisticsByItemId =
                orderIds.isEmpty() ? Map.of() : logisticsByItemId(ownerUserId, orderIds);
        List<Ali1688HistoricalOrderItemRow> itemRows = orderIds.isEmpty()
                ? List.of()
                : emptyList(mapper.listOrderItems(ownerUserId, orderIds));
        Map<Long, Ali1688HistoricalOrderItemAssignmentSummaryRow> assignmentSummaryByItemId =
                assignmentSummaryByItemId(ownerUserId, itemRows);
        Map<Long, List<Ali1688HistoricalOrderItemAssignmentRow>> activeAssignmentsByItemId =
                activeAssignmentsByItemId(ownerUserId, itemRows);
        Map<Long, Ali1688HistoricalOrderProductLinkRow> productLinkByAssignmentId =
                productLinkByAssignmentId(ownerUserId, activeAssignmentsByItemId);
        Map<Long, List<Ali1688HistoricalOrderWorkbenchView.OrderItemView>> itemsByOrderId = orderIds.isEmpty()
                ? Map.of()
                : itemRows
                        .stream()
                        .collect(Collectors.groupingBy(
                                Ali1688HistoricalOrderItemRow::getOrderId,
                                Collectors.mapping(
                                        row -> Ali1688HistoricalOrderWorkbenchView.OrderItemView.fromRow(
                                                row,
                                                firstLogistics(logisticsByItemId.get(row.getId())),
                                                assignmentSummaryByItemId.get(row.getId())
                                        ),
                                        Collectors.toList()
                                )
                        ));
        List<Ali1688HistoricalOrderWorkbenchView.OrderRowView> orderViews = new ArrayList<>();
        for (Ali1688HistoricalOrderRow order : orders) {
            List<Ali1688HistoricalOrderWorkbenchView.OrderItemView> itemViews =
                    itemsByOrderId.getOrDefault(order.getId(), List.of());
            if (itemViews.isEmpty()) {
                orderViews.add(Ali1688HistoricalOrderWorkbenchView.OrderRowView.fromRow(order, List.of()));
                continue;
            }
            for (Ali1688HistoricalOrderWorkbenchView.OrderItemView itemView : itemViews) {
                Long itemId = parseLong(itemView.getId());
                for (Ali1688HistoricalOrderWorkbenchView.OrderItemView displayItem : splitItemForWorkbench(
                        itemView,
                        activeAssignmentsByItemId.get(itemId),
                        productLinkByAssignmentId
                )) {
                    Ali1688HistoricalOrderWorkbenchView.OrderRowView displayOrder =
                            Ali1688HistoricalOrderWorkbenchView.OrderRowView.fromRow(order, List.of(displayItem));
                    applyProratedOrderAmounts(displayOrder, displayItem);
                    orderViews.add(displayOrder);
                }
            }
        }
        return Ali1688HistoricalOrderWorkbenchView.authorizedWithOrders(
                context,
                primaryAuthorization,
                orderViews,
                mapper.countOrders(ownerUserId, visibleAuthorizationIds, resolvedQuery),
                mapper.countOrderItems(ownerUserId, visibleAuthorizationIds),
                latestTask,
                resolvedQuery,
                Ali1688HistoricalOrderWorkbenchView.StoreScopeView.bound(resolvedQuery, visibleAuthorizationIds)
        );
    }

    private List<Long> visibleAuthorizationIds(
            Long ownerUserId,
            Ali1688HistoricalOrderQuery query,
            Ali1688HistoricalOrderAuthorizationRow fallbackAuthorization
    ) {
        if (ownerUserId == null) {
            return List.of();
        }
        if (query == null || !hasText(query.getStoreCode())) {
            return fallbackAuthorization == null || fallbackAuthorization.getId() == null
                    ? List.of()
                    : List.of(fallbackAuthorization.getId());
        }
        return emptyList(mapper.listVisibleAuthorizationIds(
                ownerUserId,
                query.getStoreCode(),
                query.getSiteCode()
        ));
    }

    private void validateRequestedStore(BusinessAccessContext context, Ali1688HistoricalOrderQuery query) {
        if (query == null || !hasText(query.getStoreCode())) {
            return;
        }
        if (context == null || !context.canAccessStore(query.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能查看该店铺的 1688 历史订单。");
        }
        Long scopedOwnerUserId = context.resolveOwnerUserIdForStore(query.getStoreCode());
        if (scopedOwnerUserId != null && ownerUserId(context) != null && !scopedOwnerUserId.equals(ownerUserId(context))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前店铺不属于当前老板，不能查看 1688 历史订单。");
        }
    }

    private boolean canAccessAssignmentTarget(
            BusinessAccessContext context,
            Ali1688HistoricalOrderItemAssignmentRow assignment
    ) {
        if (assignment == null || !hasText(assignment.getTargetStoreCode())) {
            return true;
        }
        try {
            validateRequestedStore(context, Ali1688HistoricalOrderQuery.fromRequest(
                    null,
                    null,
                    null,
                    null,
                    null,
                    assignment.getTargetStoreCode(),
                    assignment.getTargetSiteCode(),
                    null,
                    null
            ));
            return true;
        } catch (ResponseStatusException ex) {
            if (ex.getStatus() == HttpStatus.FORBIDDEN) {
                return false;
            }
            throw ex;
        }
    }

    private void validateMutableAssignment(
            BusinessAccessContext context,
            Ali1688HistoricalOrderItemAssignmentRow assignment
    ) {
        if (assignment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分配记录不存在或无权限操作。");
        }
        validateRequestedStore(context, Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                assignment.getTargetStoreCode(),
                assignment.getTargetSiteCode(),
                null,
                null
        ));
        if (!"active".equals(assignment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能调整或撤回有效分配记录。");
        }
    }

    private Ali1688HistoricalOrderRow toOrderRow(
            Long ownerUserId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Long orderId,
            Ali1688HistoricalOrderProvider.OrderSnapshot snapshot
    ) {
        Ali1688HistoricalOrderRow row = new Ali1688HistoricalOrderRow();
        row.setId(orderId);
        row.setOwnerUserId(ownerUserId);
        row.setAuthorizationId(authorization.getId());
        row.setOrderNaturalKey(authorization.getId() + ":" + snapshot.getProviderOrderNo());
        row.setProviderOrderNo(snapshot.getProviderOrderNo());
        row.setOrderTime(snapshot.getOrderTime());
        row.setPaidAt(snapshot.getPaidAt());
        row.setBuyerCompanyName(snapshot.getBuyerCompanyName());
        row.setBuyerMemberName(snapshot.getBuyerMemberName());
        row.setSupplierName(snapshot.getSupplierName());
        row.setSellerMemberName(snapshot.getSellerMemberName());
        row.setGoodsTotalText(snapshot.getGoodsTotalText());
        row.setFreightText(snapshot.getFreightText());
        row.setAdjustmentText(snapshot.getAdjustmentText());
        row.setPaidAmountText(snapshot.getPaidAmountText());
        row.setAmountText(snapshot.getAmountText());
        row.setAmountValue(parseMoney(snapshot.getAmountText()));
        row.setCurrency(snapshot.getCurrency());
        row.setOrderStatus(snapshot.getOrderStatus());
        row.setLogisticsStatus(snapshot.getLogisticsStatus());
        row.setShipperName(snapshot.getShipperName());
        row.setOriginalUrl(snapshot.getOriginalUrl());
        row.setReceiverName(snapshot.getReceiverName());
        row.setReceiverPostalCode(snapshot.getReceiverPostalCode());
        row.setReceiverTelephone(snapshot.getReceiverTelephone());
        row.setReceiverMobile(snapshot.getReceiverMobile());
        row.setReceiverPhone(snapshot.getReceiverPhone());
        row.setReceiverAddress(snapshot.getReceiverAddress());
        row.setBuyerRemark(snapshot.getBuyerRemark());
        row.setSupplierContact(snapshot.getSupplierContact());
        row.setInitiatorLoginName(snapshot.getInitiatorLoginName());
        row.setSourceBatchNo(snapshot.getSourceBatchNo());
        row.setDownstreamOrderNo(snapshot.getDownstreamOrderNo());
        row.setRawSnapshotJson(snapshot.getRawSnapshotJson());
        return row;
    }

    private Ali1688HistoricalOrderItemRow toItemRow(
            Long itemId,
            Long orderId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            Ali1688HistoricalOrderProvider.OrderItemSnapshot snapshot,
            int lineNo
    ) {
        Ali1688HistoricalOrderItemRow row = new Ali1688HistoricalOrderItemRow();
        row.setId(itemId);
        row.setOrderId(orderId);
        row.setItemNaturalKey(String.join(
                ":",
                String.valueOf(authorization.getId()),
                order.getProviderOrderNo(),
                nullToEmpty(snapshot.getOfferId()),
                nullToEmpty(snapshot.getSkuText()),
                String.valueOf(lineNo)
        ));
        row.setOfferId(snapshot.getOfferId());
        row.setSkuId(snapshot.getSkuId());
        row.setTitle(snapshot.getTitle());
        row.setSkuText(snapshot.getSkuText());
        row.setModelText(snapshot.getModelText());
        row.setProductCode(snapshot.getProductCode());
        row.setSingleProductCode(snapshot.getSingleProductCode());
        row.setQuantity(snapshot.getQuantity());
        row.setUnit(snapshot.getUnit());
        row.setUnitPriceText(snapshot.getUnitPriceText());
        row.setAmountText(snapshot.getAmountText());
        row.setImageUrl(snapshot.getImageUrl());
        row.setRawSnapshotJson(snapshot.getRawSnapshotJson());
        return row;
    }

    private Ali1688HistoricalOrderLogisticsRow toLogisticsRow(
            Long logisticsId,
            Long orderId,
            Long itemId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            Ali1688HistoricalOrderProvider.OrderItemSnapshot snapshot,
            int lineNo
    ) {
        if (!hasText(snapshot.getLogisticsCompany()) && !hasText(snapshot.getTrackingNo())) {
            return null;
        }
        String logisticsKeyTail = hasText(snapshot.getTrackingNo())
                ? snapshot.getTrackingNo()
                : "line-" + lineNo;
        Ali1688HistoricalOrderLogisticsRow row = new Ali1688HistoricalOrderLogisticsRow();
        row.setId(logisticsId);
        row.setOrderId(orderId);
        row.setItemId(itemId);
        row.setLogisticsNaturalKey(String.join(
                ":",
                String.valueOf(authorization.getId()),
                order.getProviderOrderNo(),
                String.valueOf(itemId),
                logisticsKeyTail
        ));
        row.setLogisticsCompany(snapshot.getLogisticsCompany());
        row.setTrackingNo(snapshot.getTrackingNo());
        row.setRawSnapshotJson(snapshot.getRawSnapshotJson());
        return row;
    }

    private Map<Long, List<Ali1688HistoricalOrderLogisticsRow>> logisticsByItemId(
            Long ownerUserId,
            List<Long> orderIds
    ) {
        return emptyList(mapper.listOrderLogistics(ownerUserId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(Ali1688HistoricalOrderLogisticsRow::getItemId));
    }

    private Map<Long, Ali1688HistoricalOrderItemAssignmentSummaryRow> assignmentSummaryByItemId(
            Long ownerUserId,
            List<Ali1688HistoricalOrderItemRow> itemRows
    ) {
        List<Long> itemIds = emptyList(itemRows).stream()
                .map(Ali1688HistoricalOrderItemRow::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return emptyList(mapper.listOrderItemAssignmentSummaries(ownerUserId, itemIds))
                .stream()
                .filter(row -> row.getItemId() != null)
                .collect(Collectors.toMap(
                        Ali1688HistoricalOrderItemAssignmentSummaryRow::getItemId,
                        row -> row,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, List<Ali1688HistoricalOrderItemAssignmentRow>> activeAssignmentsByItemId(
            Long ownerUserId,
            List<Ali1688HistoricalOrderItemRow> itemRows
    ) {
        List<Long> itemIds = emptyList(itemRows).stream()
                .map(Ali1688HistoricalOrderItemRow::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return emptyList(mapper.listActiveOrderItemAssignments(ownerUserId, itemIds))
                .stream()
                .filter(row -> row.getItemId() != null)
                .collect(Collectors.groupingBy(
                        Ali1688HistoricalOrderItemAssignmentRow::getItemId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<Long, Ali1688HistoricalOrderProductLinkRow> productLinkByAssignmentId(
            Long ownerUserId,
            Map<Long, List<Ali1688HistoricalOrderItemAssignmentRow>> activeAssignmentsByItemId
    ) {
        if (activeAssignmentsByItemId == null || activeAssignmentsByItemId.isEmpty()) {
            return Map.of();
        }
        List<Long> assignmentIds = activeAssignmentsByItemId.values().stream()
                .flatMap(List::stream)
                .map(Ali1688HistoricalOrderItemAssignmentRow::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        return emptyList(mapper.listActiveOrderItemProductLinks(ownerUserId, assignmentIds))
                .stream()
                .filter(row -> row.getAssignmentId() != null)
                .collect(Collectors.toMap(
                        Ali1688HistoricalOrderProductLinkRow::getAssignmentId,
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Ali1688HistoricalOrderLogisticsRow firstLogistics(List<Ali1688HistoricalOrderLogisticsRow> rows) {
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private List<Ali1688HistoricalOrderWorkbenchView.OrderItemView> splitItemForWorkbench(
            Ali1688HistoricalOrderWorkbenchView.OrderItemView item,
            List<Ali1688HistoricalOrderItemAssignmentRow> assignments,
            Map<Long, Ali1688HistoricalOrderProductLinkRow> productLinkByAssignmentId
    ) {
        List<Ali1688HistoricalOrderItemAssignmentRow> activeAssignments = emptyList(assignments).stream()
                .filter(row -> row.getId() != null)
                .filter(row -> "active".equals(row.getStatus()))
                .collect(Collectors.toList());
        if (activeAssignments.isEmpty()) {
            return splitItemForWorkbench(item);
        }
        Integer originalQuantity = item == null ? null : item.getOriginalQuantity();
        if (item == null || originalQuantity == null || originalQuantity <= 0) {
            return item == null ? List.of() : List.of(item);
        }
        List<Ali1688HistoricalOrderWorkbenchView.OrderItemView> splitItems = new ArrayList<>();
        int assignedQuantity = 0;
        for (Ali1688HistoricalOrderItemAssignmentRow assignment : activeAssignments) {
            int segmentQuantity = Math.max(0, defaultInt(assignment.getAssignedQuantity()));
            if (segmentQuantity <= 0) {
                continue;
            }
            assignedQuantity += segmentQuantity;
            Ali1688HistoricalOrderWorkbenchView.OrderItemView assignedItem = copyItemView(item);
            assignedItem.setQuantity(segmentQuantity);
            assignedItem.setAssignedQuantity(segmentQuantity);
            assignedItem.setRemainingQuantity(0);
            assignedItem.setAssignmentStatus("assigned");
            assignedItem.setAssignmentStatusLabel("已分配");
            assignedItem.setAssignmentBreakdownText(assignmentDisplayText(assignment, segmentQuantity));
            assignedItem.setAssignmentId(assignment.getId());
            assignedItem.setAssignmentTargetType(assignment.getTargetType());
            assignedItem.setAssignmentTargetStoreCode(assignment.getTargetStoreCode());
            assignedItem.setAssignmentTargetSiteCode(assignment.getTargetSiteCode());
            assignedItem.setProductLink(Ali1688HistoricalOrderProductLinkView.LinkedProductView.fromRow(
                    productLinkByAssignmentId.get(assignment.getId())
            ));
            assignedItem.setAmountText(prorateMoneyText(item.getAmountText(), segmentQuantity, originalQuantity));
            splitItems.add(assignedItem);
        }
        int remainingQuantity = Math.max(0, originalQuantity - assignedQuantity);
        if (remainingQuantity > 0) {
            Ali1688HistoricalOrderWorkbenchView.OrderItemView unassignedItem = copyItemView(item);
            unassignedItem.setQuantity(remainingQuantity);
            unassignedItem.setAssignedQuantity(0);
            unassignedItem.setRemainingQuantity(remainingQuantity);
            unassignedItem.setAssignmentStatus("unassigned");
            unassignedItem.setAssignmentStatusLabel("未分配");
            unassignedItem.setAssignmentBreakdownText(null);
            unassignedItem.setAssignmentId(null);
            unassignedItem.setAssignmentTargetType(null);
            unassignedItem.setAssignmentTargetStoreCode(null);
            unassignedItem.setAssignmentTargetSiteCode(null);
            unassignedItem.setProductLink(null);
            unassignedItem.setAmountText(prorateMoneyText(item.getAmountText(), remainingQuantity, originalQuantity));
            splitItems.add(unassignedItem);
        }
        return splitItems.isEmpty() ? List.of(item) : splitItems;
    }

    private List<Ali1688HistoricalOrderWorkbenchView.OrderItemView> splitItemForWorkbench(
            Ali1688HistoricalOrderWorkbenchView.OrderItemView item
    ) {
        if (item == null) {
            return List.of();
        }
        Integer originalQuantity = item.getOriginalQuantity();
        if (originalQuantity == null || originalQuantity <= 0 || !hasText(item.getAssignmentBreakdownText())) {
            return List.of(item);
        }
        List<AssignmentDisplaySegment> segments = parseAssignmentDisplaySegments(item.getAssignmentBreakdownText());
        if (segments.isEmpty()) {
            return List.of(item);
        }
        List<Ali1688HistoricalOrderWorkbenchView.OrderItemView> splitItems = new ArrayList<>();
        int assignedQuantity = 0;
        for (AssignmentDisplaySegment segment : segments) {
            if (segment.quantity <= 0) {
                continue;
            }
            assignedQuantity += segment.quantity;
            Ali1688HistoricalOrderWorkbenchView.OrderItemView assignedItem = copyItemView(item);
            assignedItem.setQuantity(segment.quantity);
            assignedItem.setAssignedQuantity(segment.quantity);
            assignedItem.setRemainingQuantity(0);
            assignedItem.setAssignmentStatus("assigned");
            assignedItem.setAssignmentStatusLabel("已分配");
            assignedItem.setAssignmentBreakdownText(segment.displayText);
            assignedItem.setAmountText(prorateMoneyText(item.getAmountText(), segment.quantity, originalQuantity));
            splitItems.add(assignedItem);
        }
        int remainingQuantity = Math.max(0, originalQuantity - assignedQuantity);
        if (remainingQuantity > 0) {
            Ali1688HistoricalOrderWorkbenchView.OrderItemView unassignedItem = copyItemView(item);
            unassignedItem.setQuantity(remainingQuantity);
            unassignedItem.setAssignedQuantity(0);
            unassignedItem.setRemainingQuantity(remainingQuantity);
            unassignedItem.setAssignmentStatus("unassigned");
            unassignedItem.setAssignmentStatusLabel("未分配");
            unassignedItem.setAssignmentBreakdownText(null);
            unassignedItem.setAmountText(prorateMoneyText(item.getAmountText(), remainingQuantity, originalQuantity));
            splitItems.add(unassignedItem);
        }
        return splitItems.isEmpty() ? List.of(item) : splitItems;
    }

    private Ali1688HistoricalOrderWorkbenchView.OrderItemView copyItemView(
            Ali1688HistoricalOrderWorkbenchView.OrderItemView source
    ) {
        Ali1688HistoricalOrderWorkbenchView.OrderItemView copy =
                new Ali1688HistoricalOrderWorkbenchView.OrderItemView();
        copy.setId(source.getId());
        copy.setOfferId(source.getOfferId());
        copy.setSkuId(source.getSkuId());
        copy.setTitle(source.getTitle());
        copy.setSkuText(source.getSkuText());
        copy.setModelText(source.getModelText());
        copy.setProductCode(source.getProductCode());
        copy.setSingleProductCode(source.getSingleProductCode());
        copy.setQuantity(source.getQuantity());
        copy.setOriginalQuantity(source.getOriginalQuantity());
        copy.setAssignedQuantity(source.getAssignedQuantity());
        copy.setRemainingQuantity(source.getRemainingQuantity());
        copy.setAssignmentStatus(source.getAssignmentStatus());
        copy.setAssignmentStatusLabel(source.getAssignmentStatusLabel());
        copy.setAssignmentBreakdownText(source.getAssignmentBreakdownText());
        copy.setAssignmentId(source.getAssignmentId());
        copy.setAssignmentTargetType(source.getAssignmentTargetType());
        copy.setAssignmentTargetStoreCode(source.getAssignmentTargetStoreCode());
        copy.setAssignmentTargetSiteCode(source.getAssignmentTargetSiteCode());
        copy.setProductLink(source.getProductLink());
        copy.setUnit(source.getUnit());
        copy.setUnitPriceText(source.getUnitPriceText());
        copy.setAmountText(source.getAmountText());
        copy.setImageUrl(source.getImageUrl());
        copy.setLogisticsCompany(source.getLogisticsCompany());
        copy.setTrackingNo(source.getTrackingNo());
        copy.setMissingFields(source.getMissingFields());
        return copy;
    }

    private String assignmentDisplayText(
            Ali1688HistoricalOrderItemAssignmentRow assignment,
            int quantity
    ) {
        if (assignment == null || ASSIGNMENT_TARGET_CONSUMABLE.equals(assignment.getTargetType())) {
            return "耗材 " + quantity;
        }
        String storeCode = hasText(assignment.getTargetStoreCode())
                ? assignment.getTargetStoreCode().trim()
                : "未指定店铺";
        if (!hasText(assignment.getTargetSiteCode()) || "*".equals(assignment.getTargetSiteCode())) {
            return storeCode + " " + quantity;
        }
        return storeCode + " " + assignment.getTargetSiteCode().trim() + " " + quantity;
    }

    private List<AssignmentDisplaySegment> parseAssignmentDisplaySegments(String assignmentBreakdownText) {
        if (!hasText(assignmentBreakdownText)) {
            return List.of();
        }
        List<AssignmentDisplaySegment> segments = new ArrayList<>();
        for (String part : assignmentBreakdownText.split("/")) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int lastSpace = trimmed.lastIndexOf(' ');
            if (lastSpace <= 0 || lastSpace >= trimmed.length() - 1) {
                continue;
            }
            String label = trimmed.substring(0, lastSpace).trim();
            Integer quantity = parsePositiveInteger(trimmed.substring(lastSpace + 1).trim());
            if (!hasText(label) || quantity == null) {
                continue;
            }
            segments.add(new AssignmentDisplaySegment(label + " " + quantity, quantity));
        }
        return segments;
    }

    private Integer parsePositiveInteger(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            int parsed = new BigDecimal(value.trim()).intValue();
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void applyProratedOrderAmounts(
            Ali1688HistoricalOrderWorkbenchView.OrderRowView order,
            Ali1688HistoricalOrderWorkbenchView.OrderItemView item
    ) {
        Integer originalQuantity = item.getOriginalQuantity();
        Integer displayQuantity = item.getQuantity();
        if (originalQuantity == null || displayQuantity == null
                || originalQuantity <= 0 || displayQuantity.equals(originalQuantity)) {
            return;
        }
        order.setGoodsTotalText(prorateMoneyText(order.getGoodsTotalText(), displayQuantity, originalQuantity));
        order.setFreightText(prorateMoneyText(order.getFreightText(), displayQuantity, originalQuantity));
        order.setAdjustmentText(prorateMoneyText(order.getAdjustmentText(), displayQuantity, originalQuantity));
        order.setPaidAmountText(prorateMoneyText(order.getPaidAmountText(), displayQuantity, originalQuantity));
        order.setAmountText(prorateMoneyText(order.getAmountText(), displayQuantity, originalQuantity));
    }

    private String prorateMoneyText(String value, Integer quantity, Integer originalQuantity) {
        if (!hasText(value) || quantity == null || originalQuantity == null || originalQuantity <= 0
                || quantity.equals(originalQuantity)) {
            return value;
        }
        BigDecimal amount = parseMoney(value);
        if (amount == null) {
            return value;
        }
        BigDecimal prorated = amount
                .multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(originalQuantity), 2, RoundingMode.HALF_UP);
        return formatMoneyLike(value, prorated);
    }

    private String formatMoneyLike(String originalText, BigDecimal amount) {
        if (amount == null) {
            return originalText;
        }
        String currency = originalText != null && originalText.contains("¥") ? "¥" : "";
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        String number = normalized.abs().toPlainString();
        return normalized.signum() < 0 ? "-" + currency + number : currency + number;
    }

    private <T> List<T> emptyList(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private static class AssignmentDisplaySegment {
        private final String displayText;
        private final int quantity;

        private AssignmentDisplaySegment(String displayText, int quantity) {
            this.displayText = displayText;
            this.quantity = quantity;
        }
    }

    private static class PurchaseCostCalculation {
        private final BigDecimal allocatedCost;
        private final BigDecimal unitPrice;
        private final String amountBasis;
        private final String priceQuality;

        private PurchaseCostCalculation(
                BigDecimal allocatedCost,
                BigDecimal unitPrice,
                String amountBasis,
                String priceQuality
        ) {
            this.allocatedCost = allocatedCost;
            this.unitPrice = unitPrice;
            this.amountBasis = amountBasis;
            this.priceQuality = priceQuality;
        }

        private static PurchaseCostCalculation ready(
                BigDecimal allocatedCost,
                BigDecimal unitPrice,
                String amountBasis
        ) {
            return new PurchaseCostCalculation(allocatedCost, unitPrice, amountBasis, "ready");
        }

        private static PurchaseCostCalculation missing(String priceQuality) {
            return new PurchaseCostCalculation(null, null, "missing", priceQuality);
        }
    }

    private static class SkuPurchaseHistoryAccumulator {
        private final String storeCode;
        private final String siteCode;
        private String linkStatus;
        private final Long assignmentId;
        private final Long orderId;
        private final Long itemId;
        private final String orderNo;
        private final String orderTime;
        private final String supplierName;
        private final String skuParent;
        private final String partnerSku;
        private final String pskuCode;
        private final String productTitle;
        private final String productTitleCn;
        private final String productImageUrl;
        private final String sourceOfferId;
        private final String sourceSkuId;
        private final String sourceProductCode;
        private final String sourceSingleProductCode;
        private final List<Ali1688SkuPurchaseHistoryView.HistoryView> history = new ArrayList<>();
        private final Map<String, Boolean> amountBasisSet = new LinkedHashMap<>();
        private final Map<String, Boolean> qualityFlagSet = new LinkedHashMap<>();
        private int purchaseCount;
        private int totalQuantity;
        private int pricedQuantity;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal lowestUnitPrice;
        private BigDecimal highestUnitPrice;
        private BigDecimal recentUnitPrice;
        private String recentPurchaseTime;

        private SkuPurchaseHistoryAccumulator(Ali1688SkuPurchaseHistoryRow seed, String linkStatus) {
            this.storeCode = seed.getStoreCode();
            this.siteCode = seed.getSiteCode();
            this.linkStatus = linkStatus;
            this.assignmentId = seed.getAssignmentId();
            this.orderId = seed.getOrderId();
            this.itemId = seed.getItemId();
            this.orderNo = seed.getOrderNo();
            this.orderTime = seed.getOrderTime();
            this.supplierName = seed.getSupplierName();
            this.skuParent = seed.getSkuParent();
            this.partnerSku = seed.getPartnerSku();
            this.pskuCode = seed.getPskuCode();
            this.productTitle = seed.getProductTitle();
            this.productTitleCn = null;
            this.productImageUrl = seed.getProductImageUrl();
            this.sourceOfferId = seed.getSourceOfferId();
            this.sourceSkuId = seed.getSourceSkuId();
            this.sourceProductCode = seed.getSourceProductCode();
            this.sourceSingleProductCode = seed.getSourceSingleProductCode();
        }

        private SkuPurchaseHistoryAccumulator(Ali1688SkuPurchaseHistoryProductRow seed) {
            this.storeCode = seed.getStoreCode();
            this.siteCode = seed.getSiteCode();
            this.linkStatus = "unlinked";
            this.assignmentId = null;
            this.orderId = null;
            this.itemId = null;
            this.orderNo = null;
            this.orderTime = null;
            this.supplierName = null;
            this.skuParent = seed.getSkuParent();
            this.partnerSku = seed.getPartnerSku();
            this.pskuCode = seed.getPskuCode();
            this.productTitle = seed.getProductTitle();
            this.productTitleCn = seed.getProductTitleCn();
            this.productImageUrl = seed.getProductImageUrl();
            this.sourceOfferId = null;
            this.sourceSkuId = null;
            this.sourceProductCode = null;
            this.sourceSingleProductCode = null;
        }

        private void add(Ali1688SkuPurchaseHistoryRow row, PurchaseCostCalculation cost) {
            linkStatus = "linked";
            purchaseCount++;
            Integer assignedQuantity = row.getAssignedQuantity();
            if (assignedQuantity != null && assignedQuantity > 0) {
                totalQuantity += assignedQuantity;
            }
            Ali1688SkuPurchaseHistoryView.HistoryView historyView =
                    new Ali1688SkuPurchaseHistoryView.HistoryView();
            historyView.setOrderId(row.getOrderId());
            historyView.setItemId(row.getItemId());
            historyView.setAssignmentId(row.getAssignmentId());
            historyView.setOrderNo(row.getOrderNo());
            historyView.setOrderTime(row.getOrderTime());
            historyView.setSupplierName(row.getSupplierName());
            historyView.setAssignedQuantity(row.getAssignedQuantity());
            historyView.setAmountBasis(cost.amountBasis);
            historyView.setPriceQuality(cost.priceQuality);
            if (cost.allocatedCost != null && cost.unitPrice != null && assignedQuantity != null && assignedQuantity > 0) {
                BigDecimal allocatedCost = money(cost.allocatedCost);
                BigDecimal unitPrice = money(cost.unitPrice);
                historyView.setAllocatedCost(allocatedCost);
                historyView.setUnitPrice(unitPrice);
                totalCost = totalCost.add(allocatedCost);
                pricedQuantity += assignedQuantity;
                if (lowestUnitPrice == null || unitPrice.compareTo(lowestUnitPrice) < 0) {
                    lowestUnitPrice = unitPrice;
                }
                if (highestUnitPrice == null || unitPrice.compareTo(highestUnitPrice) > 0) {
                    highestUnitPrice = unitPrice;
                }
                amountBasisSet.put(cost.amountBasis, Boolean.TRUE);
                if (recentPurchaseTime == null || nullToEmpty(row.getOrderTime()).compareTo(recentPurchaseTime) > 0) {
                    recentPurchaseTime = row.getOrderTime();
                    recentUnitPrice = unitPrice;
                }
            } else {
                qualityFlagSet.put(cost.priceQuality, Boolean.TRUE);
            }
            history.add(historyView);
        }

        private Ali1688SkuPurchaseHistoryView.ItemView toView() {
            history.sort((left, right) -> nullToEmpty(right.getOrderTime()).compareTo(nullToEmpty(left.getOrderTime())));
            Ali1688SkuPurchaseHistoryView.ItemView view = new Ali1688SkuPurchaseHistoryView.ItemView();
            view.setStoreCode(storeCode);
            view.setSiteCode(siteCode);
            view.setLinkStatus(linkStatus);
            view.setAssignmentId(assignmentId);
            view.setOrderId(orderId);
            view.setItemId(itemId);
            view.setOrderNo(orderNo);
            view.setOrderTime(orderTime);
            view.setSupplierName(supplierName);
            view.setSkuParent(skuParent);
            view.setPartnerSku(partnerSku);
            view.setPskuCode(pskuCode);
            view.setProductTitle(productTitle);
            view.setProductTitleCn(productTitleCn);
            view.setProductImageUrl(productImageUrl);
            view.setSourceOfferId(sourceOfferId);
            view.setSourceSkuId(sourceSkuId);
            view.setSourceProductCode(sourceProductCode);
            view.setSourceSingleProductCode(sourceSingleProductCode);
            view.setPurchaseCount(purchaseCount);
            view.setTotalQuantity(totalQuantity);
            view.setTotalCost(money(totalCost));
            view.setAverageUnitPrice(pricedQuantity > 0
                    ? money(totalCost.divide(BigDecimal.valueOf(pricedQuantity), 8, RoundingMode.HALF_UP))
                    : null);
            view.setRecentUnitPrice(recentUnitPrice);
            view.setRecentPurchaseTime(recentPurchaseTime);
            view.setLowestUnitPrice(lowestUnitPrice);
            view.setHighestUnitPrice(highestUnitPrice);
            view.setAmountBasis(resolveAmountBasis(amountBasisSet));
            view.setDataQualityFlags(new ArrayList<>(qualityFlagSet.keySet()));
            view.setHistory(history);
            return view;
        }

        private static BigDecimal money(BigDecimal amount) {
            return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }

        private static String resolveAmountBasis(Map<String, Boolean> amountBasisSet) {
            if (amountBasisSet.isEmpty()) {
                return "missing";
            }
            if (amountBasisSet.size() == 1) {
                return amountBasisSet.keySet().iterator().next();
            }
            return "mixed";
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank()) {
            return null;
        }
        return new BigDecimal(normalized);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim()).intValue();
    }

    private Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeAssignmentTargetType(String targetType) {
        if (!hasText(targetType)) {
            return ASSIGNMENT_TARGET_STORE_SITE;
        }
        String normalized = targetType.trim().toUpperCase();
        if (ASSIGNMENT_TARGET_CONSUMABLE.equals(normalized)) {
            return ASSIGNMENT_TARGET_CONSUMABLE;
        }
        return ASSIGNMENT_TARGET_STORE_SITE;
    }

    private String normalizeProductLinkCandidateStatus(String linkStatus) {
        if (!hasText(linkStatus)) {
            return null;
        }
        String normalized = linkStatus.trim().toLowerCase();
        if ("linked".equals(normalized) || "unlinked".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private boolean isConsumableAssignment(Ali1688HistoricalOrderItemAssignmentRow assignment) {
        return assignment != null && ASSIGNMENT_TARGET_CONSUMABLE.equals(assignment.getTargetType());
    }

    private void requireProductLinkWriteAccess(BusinessAccessContext context) {
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色只能查看商品关联，不能修改。");
        }
        if (context.isBossAccount()) {
            return;
        }
        if (!context.isOperatorAccount()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色只能查看商品关联，不能修改。");
        }
        String roleName = trimToNull(context.getRoleName());
        if ("运营".equals(roleName) || "运营主管".equals(roleName) || "运营管理".equals(roleName)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色只能查看商品关联，不能修改。");
    }

    private void validateProductSkuInAssignmentTarget(
            Long ownerUserId,
            Ali1688HistoricalOrderItemAssignmentRow assignment,
            String skuParent
    ) {
        int matchedSkuCount = mapper.countProductSkuInAssignmentTarget(
                ownerUserId,
                assignment.getTargetStoreCode(),
                assignment.getTargetSiteCode(),
                skuParent
        );
        if (matchedSkuCount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联商品必须属于当前分配目标店铺站点。");
        }
    }

    private boolean sameProductLink(
            Ali1688HistoricalOrderProductLinkRow current,
            Ali1688HistoricalOrderProductLinkRow requested
    ) {
        return current != null
                && requested != null
                && normalizedEquals(current.getSkuParent(), requested.getSkuParent());
    }

    private boolean normalizedEquals(String left, String right) {
        String normalizedLeft = hasText(left) ? left.trim() : "";
        String normalizedRight = hasText(right) ? right.trim() : "";
        return normalizedLeft.equals(normalizedRight);
    }

    private Ali1688HistoricalOrderProductLinkAuditRow productLinkAudit(
            Long id,
            Long ownerUserId,
            Long assignmentId,
            Ali1688HistoricalOrderProductLinkRow oldLink,
            Ali1688HistoricalOrderProductLinkRow newLink,
            String actionType,
            Long operatorUserId
    ) {
        Ali1688HistoricalOrderProductLinkAuditRow audit =
                new Ali1688HistoricalOrderProductLinkAuditRow();
        audit.setId(id);
        audit.setOwnerUserId(ownerUserId);
        audit.setAssignmentId(assignmentId);
        audit.setActionType(actionType);
        audit.setOldLinkId(oldLink == null ? null : oldLink.getId());
        audit.setOldSkuParent(oldLink == null ? null : oldLink.getSkuParent());
        audit.setOldPartnerSku(oldLink == null ? null : oldLink.getPartnerSku());
        audit.setOldPskuCode(oldLink == null ? null : oldLink.getPskuCode());
        audit.setOldProductTitle(oldLink == null ? null : oldLink.getProductTitle());
        audit.setNewLinkId(newLink == null ? null : newLink.getId());
        audit.setNewSkuParent(newLink == null ? null : newLink.getSkuParent());
        audit.setNewPartnerSku(newLink == null ? null : newLink.getPartnerSku());
        audit.setNewPskuCode(newLink == null ? null : newLink.getPskuCode());
        audit.setNewProductTitle(newLink == null ? null : newLink.getProductTitle());
        audit.setCreatedBy(operatorUserId);
        return audit;
    }

    private String purchaseHistoryGroupKey(Ali1688SkuPurchaseHistoryRow row) {
        return nullToEmpty(row.getStoreCode())
                + "|"
                + nullToEmpty(row.getSiteCode())
                + "|"
                + nullToEmpty(row.getSkuParent());
    }

    private String purchaseHistoryGroupKey(Ali1688SkuPurchaseHistoryProductRow row) {
        return nullToEmpty(row.getStoreCode())
                + "|"
                + nullToEmpty(row.getSiteCode())
                + "|"
                + nullToEmpty(row.getSkuParent());
    }

    private String purchaseHistoryAssignmentKey(Ali1688SkuPurchaseHistoryRow row) {
        if (row.getAssignmentId() != null) {
            return "assignment:" + row.getAssignmentId();
        }
        return "link:" + row.getProductLinkId();
    }

    private PurchaseCostCalculation calculatePurchaseCost(Ali1688SkuPurchaseHistoryRow row) {
        Integer assignedQuantity = row.getAssignedQuantity();
        if (assignedQuantity == null || assignedQuantity <= 0) {
            return PurchaseCostCalculation.missing("missing_quantity");
        }
        BigDecimal itemAmount = parseMoney(row.getItemAmountText());
        Integer itemQuantity = row.getItemQuantity();
        if (itemAmount == null || itemQuantity == null || itemQuantity <= 0) {
            return PurchaseCostCalculation.missing("missing_price_basis");
        }
        BigDecimal assignedRatio = BigDecimal.valueOf(assignedQuantity)
                .divide(BigDecimal.valueOf(itemQuantity), 8, RoundingMode.HALF_UP);
        BigDecimal paidAmount = parseMoney(row.getPaidAmountText());
        BigDecimal goodsTotal = parseMoney(row.getGoodsTotalText());
        BigDecimal allocatedCost;
        String amountBasis;
        if (paidAmount != null && goodsTotal != null && goodsTotal.compareTo(BigDecimal.ZERO) > 0) {
            allocatedCost = paidAmount
                    .multiply(itemAmount)
                    .divide(goodsTotal, 8, RoundingMode.HALF_UP)
                    .multiply(assignedRatio);
            amountBasis = "paid_amount_allocated";
        } else {
            allocatedCost = itemAmount.multiply(assignedRatio);
            amountBasis = "item_amount_allocated";
        }
        BigDecimal unitPrice = allocatedCost
                .divide(BigDecimal.valueOf(assignedQuantity), 8, RoundingMode.HALF_UP);
        return PurchaseCostCalculation.ready(allocatedCost, unitPrice, amountBasis);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String checkpointJson(String nextCursor) {
        if (nextCursor == null || nextCursor.isBlank()) {
            return "{\"nextCursor\":null}";
        }
        return "{\"nextCursor\":\"" + nextCursor.replace("\"", "\\\"") + "\"}";
    }

    private String cursorFromCheckpoint(String checkpointJson) {
        if (checkpointJson == null || checkpointJson.isBlank() || checkpointJson.contains("\"nextCursor\":null")) {
            return null;
        }
        String marker = "\"nextCursor\":\"";
        int start = checkpointJson.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int valueEnd = checkpointJson.indexOf('"', valueStart);
        return valueEnd < 0 ? null : checkpointJson.substring(valueStart, valueEnd);
    }

    private Long ownerUserId(BusinessAccessContext context) {
        if (context == null) {
            return null;
        }
        return context.getBusinessOwnerUserId() == null
                ? context.getSessionUserId()
                : context.getBusinessOwnerUserId();
    }

    private Long operatorUserId(BusinessAccessContext context) {
        return context == null ? null : context.getSessionUserId();
    }
}
