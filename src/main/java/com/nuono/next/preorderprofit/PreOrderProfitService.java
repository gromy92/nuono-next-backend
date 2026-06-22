package com.nuono.next.preorderprofit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.PreOrderProfitMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class PreOrderProfitService {
    private static final long CANDIDATE_SEQUENCE_START = 260000L;
    private static final long COMPETITOR_SEQUENCE_START = 270000L;
    private static final long PURCHASE_ORDER_SEQUENCE_START = 280000L;
    private static final long PURCHASE_ORDER_ITEM_SEQUENCE_START = 290000L;

    private final PreOrderProfitMapper mapper;
    private final PreOrderProfitCalculator calculator;
    private final ObjectMapper objectMapper;

    public PreOrderProfitService(
            PreOrderProfitMapper mapper,
            PreOrderProfitCalculator calculator,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.calculator = calculator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PreOrderProfitCandidateView createCandidate(
            BusinessAccessContext context,
            PreOrderProfitCandidateCommand command
    ) {
        PreOrderProfitCandidateRow row = buildCandidateRow(context, command);
        row.setId(mapper.nextId("pre_order_profit_candidate", CANDIDATE_SEQUENCE_START));
        row.setCreatedBy(actorUserId(context));
        row.setUpdatedBy(actorUserId(context));
        mapper.insertCandidate(row);
        return getCandidate(context, row.getId());
    }

    @Transactional
    public PreOrderProfitCandidateView updateCandidate(
            BusinessAccessContext context,
            Long candidateId,
            PreOrderProfitCandidateCommand command
    ) {
        PreOrderProfitCandidateRow existing = requireCandidate(context, candidateId);
        PreOrderProfitCandidateRow row = buildCandidateRow(context, command);
        row.setId(existing.getId());
        row.setCreatedBy(existing.getCreatedBy());
        row.setUpdatedBy(actorUserId(context));
        int updated = mapper.updateCandidate(row);
        if (updated <= 0) {
            throw new IllegalArgumentException("选品池商品不存在或无权修改。");
        }
        return getCandidate(context, candidateId);
    }

    public List<PreOrderProfitCandidateView> listCandidates(
            BusinessAccessContext context,
            String storeCode,
            String siteCode,
            String keyword,
            String calculationStatus,
            String categoryId,
            String logisticsCarrierId
    ) {
        String normalizedStoreCode = requireStoreCode(context, storeCode);
        Long ownerUserId = ownerUserId(context, normalizedStoreCode);
        return mapper.selectCandidates(
                        ownerUserId,
                        normalizedStoreCode,
                        normalizeUpper(siteCode),
                        normalizeText(keyword),
                        normalizeUpper(calculationStatus),
                        normalizeLower(categoryId),
                        normalizeLower(logisticsCarrierId)
                )
                .stream()
                .map(row -> toCandidateView(context, row, false))
                .collect(Collectors.toList());
    }

    public PreOrderProfitCandidateView getCandidate(BusinessAccessContext context, Long candidateId) {
        return toCandidateView(context, requireCandidate(context, candidateId), true);
    }

    @Transactional
    public void deleteCandidate(BusinessAccessContext context, Long candidateId) {
        PreOrderProfitCandidateRow row = requireCandidate(context, candidateId);
        int updated = mapper.softDeleteCandidate(row.getOwnerUserId(), candidateId, actorUserId(context));
        if (updated <= 0) {
            throw new IllegalArgumentException("选品池商品不存在或无权删除。");
        }
    }

    @Transactional
    public PreOrderProfitCompetitorView addCompetitor(
            BusinessAccessContext context,
            Long candidateId,
            PreOrderProfitCompetitorCommand command
    ) {
        PreOrderProfitCandidateRow candidate = requireCandidate(context, candidateId);
        PreOrderProfitCompetitorRow row = buildCompetitorRow(context, candidate, command);
        row.setId(mapper.nextId("pre_order_profit_competitor", COMPETITOR_SEQUENCE_START));
        row.setCreatedBy(actorUserId(context));
        row.setUpdatedBy(actorUserId(context));
        mapper.insertCompetitor(row);
        return toCompetitorView(row);
    }

    @Transactional
    public PreOrderProfitCompetitorView updateCompetitor(
            BusinessAccessContext context,
            Long candidateId,
            Long competitorId,
            PreOrderProfitCompetitorCommand command
    ) {
        PreOrderProfitCandidateRow candidate = requireCandidate(context, candidateId);
        PreOrderProfitCompetitorRow existing = requireCompetitor(context, candidateId, competitorId);
        PreOrderProfitCompetitorRow row = buildCompetitorRow(context, candidate, command);
        row.setId(existing.getId());
        row.setCreatedBy(existing.getCreatedBy());
        row.setUpdatedBy(actorUserId(context));
        int updated = mapper.updateCompetitor(row);
        if (updated <= 0) {
            throw new IllegalArgumentException("竞品记录不存在或无权修改。");
        }
        return toCompetitorView(mapper.selectCompetitorById(candidate.getOwnerUserId(), candidateId, competitorId));
    }

    @Transactional
    public void deleteCompetitor(BusinessAccessContext context, Long candidateId, Long competitorId) {
        PreOrderProfitCandidateRow candidate = requireCandidate(context, candidateId);
        int updated = mapper.softDeleteCompetitor(candidate.getOwnerUserId(), candidateId, competitorId, actorUserId(context));
        if (updated <= 0) {
            throw new IllegalArgumentException("竞品记录不存在或无权删除。");
        }
    }

    @Transactional
    public PreOrderProfitPurchaseOrderView createPurchaseOrder(
            BusinessAccessContext context,
            PreOrderProfitPurchaseOrderCommand command
    ) {
        String storeCode = requireStoreCode(context, command == null ? null : command.getStoreCode());
        PreOrderProfitPurchaseOrderRow row = new PreOrderProfitPurchaseOrderRow();
        row.setId(mapper.nextId("pre_order_profit_purchase_order", PURCHASE_ORDER_SEQUENCE_START));
        row.setOwnerUserId(ownerUserId(context, storeCode));
        row.setStoreCode(storeCode);
        row.setSiteCode(normalizeUpper(command.getSiteCode()));
        row.setName(requireText(command.getName(), "采购单名称不能为空。"));
        row.setNotes(normalizeText(command.getNotes()));
        row.setCreatedBy(actorUserId(context));
        row.setUpdatedBy(actorUserId(context));
        mapper.insertPurchaseOrder(row);
        return toPurchaseOrderView(mapper.selectPurchaseOrderById(row.getOwnerUserId(), row.getId()));
    }

    public List<PreOrderProfitPurchaseOrderView> listPurchaseOrders(
            BusinessAccessContext context,
            String storeCode,
            String siteCode
    ) {
        String normalizedStoreCode = requireStoreCode(context, storeCode);
        return mapper.selectPurchaseOrders(
                        ownerUserId(context, normalizedStoreCode),
                        normalizedStoreCode,
                        normalizeUpper(siteCode)
                )
                .stream()
                .map(this::toPurchaseOrderView)
                .collect(Collectors.toList());
    }

    @Transactional
    public PreOrderProfitPurchaseOrderLinkView addCandidateToPurchaseOrder(
            BusinessAccessContext context,
            Long candidateId,
            Long purchaseOrderId
    ) {
        PreOrderProfitCandidateRow candidate = requireCandidate(context, candidateId);
        PreOrderProfitPurchaseOrderRow order = mapper.selectPurchaseOrderById(candidate.getOwnerUserId(), purchaseOrderId);
        if (order == null || !Objects.equals(order.getStoreCode(), candidate.getStoreCode())) {
            throw new IllegalArgumentException("采购单不存在或不属于当前选品商品店铺。");
        }
        PreOrderProfitPurchaseOrderItemRow existing = mapper.selectPurchaseOrderItem(
                candidate.getOwnerUserId(),
                purchaseOrderId,
                candidateId
        );
        if (existing != null) {
            return linkView(existing, true);
        }
        PreOrderProfitPurchaseOrderItemRow item = new PreOrderProfitPurchaseOrderItemRow();
        item.setId(mapper.nextId("pre_order_profit_purchase_order_item", PURCHASE_ORDER_ITEM_SEQUENCE_START));
        item.setOwnerUserId(candidate.getOwnerUserId());
        item.setPurchaseOrderId(purchaseOrderId);
        item.setCandidateId(candidateId);
        item.setStoreCode(candidate.getStoreCode());
        item.setSiteCode(candidate.getSiteCode());
        item.setCreatedBy(actorUserId(context));
        mapper.insertPurchaseOrderItem(item);
        return linkView(item, false);
    }

    private PreOrderProfitCandidateRow buildCandidateRow(
            BusinessAccessContext context,
            PreOrderProfitCandidateCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少选品池商品参数。");
        }
        String storeCode = requireStoreCode(context, command.getStoreCode());
        String siteCode = normalizeUpper(command.getSiteCode());
        PreOrderProfitCalculationView calculation = calculator.calculate(command);
        PreOrderProfitCandidateRow row = new PreOrderProfitCandidateRow();
        row.setOwnerUserId(ownerUserId(context, storeCode));
        row.setStoreCode(storeCode);
        row.setSiteCode(siteCode);
        row.setTitle(normalizeText(command.getTitle()));
        row.setSkuHint(normalizeText(command.getSkuHint()));
        row.setPurchaseUrl(normalizeText(command.getPurchaseUrl()));
        row.setPurchasePriceRmb(command.getPurchasePriceRmb());
        row.setLengthCm(command.getLengthCm());
        row.setWidthCm(command.getWidthCm());
        row.setHeightCm(command.getHeightCm());
        row.setActualWeightKg(command.getActualWeightKg());
        row.setCategoryId(normalizeLower(command.getCategoryId()));
        row.setCategoryLabel(resolveCategoryLabel(command, siteCode));
        row.setLogisticsCarrierId(normalizeLower(command.getLogisticsCarrierId()));
        row.setLogisticsCarrierLabel(resolveLogisticsLabel(command, siteCode));
        row.setSalePrice(command.getSalePrice());
        row.setTargetMarginRate(command.getTargetMarginRate());
        row.setCandidateStatus(StringUtils.hasText(command.getCandidateStatus())
                ? normalizeUpper(command.getCandidateStatus())
                : "DRAFT");
        row.setNotes(normalizeText(command.getNotes()));
        row.setLatestCalculationStatus(calculation.getStatus());
        row.setLatestCalculationJson(writeCalculationJson(calculation));
        return row;
    }

    private String resolveCategoryLabel(PreOrderProfitCandidateCommand command, String siteCode) {
        if (StringUtils.hasText(command.getCategoryLabel())) {
            return command.getCategoryLabel().trim();
        }
        return calculator.categoryLabel(command.getCategoryId(), siteCode);
    }

    private String resolveLogisticsLabel(PreOrderProfitCandidateCommand command, String siteCode) {
        if (StringUtils.hasText(command.getLogisticsCarrierLabel())) {
            return command.getLogisticsCarrierLabel().trim();
        }
        return calculator.logisticsLabel(command.getLogisticsCarrierId(), siteCode);
    }

    private PreOrderProfitCompetitorRow buildCompetitorRow(
            BusinessAccessContext context,
            PreOrderProfitCandidateRow candidate,
            PreOrderProfitCompetitorCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少竞品参数。");
        }
        PreOrderProfitCompetitorRow row = new PreOrderProfitCompetitorRow();
        row.setCandidateId(candidate.getId());
        row.setOwnerUserId(candidate.getOwnerUserId());
        row.setStoreCode(candidate.getStoreCode());
        row.setTitle(requireText(command.getTitle(), "竞品标题不能为空。"));
        row.setUrl(normalizeText(command.getUrl()));
        row.setPlatform(StringUtils.hasText(command.getPlatform()) ? normalizeUpper(command.getPlatform()) : "NOON");
        row.setSiteCode(StringUtils.hasText(command.getSiteCode()) ? normalizeUpper(command.getSiteCode()) : candidate.getSiteCode());
        row.setPrice(command.getPrice());
        row.setCurrency(normalizeUpper(command.getCurrency()));
        row.setSellerName(normalizeText(command.getSellerName()));
        row.setNotes(normalizeText(command.getNotes()));
        row.setUpdatedBy(actorUserId(context));
        return row;
    }

    private PreOrderProfitCandidateRow requireCandidate(BusinessAccessContext context, Long candidateId) {
        if (candidateId == null) {
            throw new IllegalArgumentException("缺少选品池商品 ID。");
        }
        PreOrderProfitCandidateRow row = mapper.selectCandidateById(ownerUserIdForCandidateLookup(context), candidateId);
        if (row == null) {
            throw new IllegalArgumentException("选品池商品不存在或无权访问。");
        }
        requireStoreCode(context, row.getStoreCode());
        return row;
    }

    private PreOrderProfitCompetitorRow requireCompetitor(
            BusinessAccessContext context,
            Long candidateId,
            Long competitorId
    ) {
        PreOrderProfitCandidateRow candidate = requireCandidate(context, candidateId);
        PreOrderProfitCompetitorRow row = mapper.selectCompetitorById(candidate.getOwnerUserId(), candidateId, competitorId);
        if (row == null) {
            throw new IllegalArgumentException("竞品记录不存在或无权访问。");
        }
        return row;
    }

    private PreOrderProfitCandidateView toCandidateView(
            BusinessAccessContext context,
            PreOrderProfitCandidateRow row,
            boolean includeRelations
    ) {
        PreOrderProfitCandidateView view = new PreOrderProfitCandidateView();
        view.setId(row.getId());
        view.setStoreCode(row.getStoreCode());
        view.setSiteCode(row.getSiteCode());
        view.setTitle(row.getTitle());
        view.setSkuHint(row.getSkuHint());
        view.setPurchaseUrl(row.getPurchaseUrl());
        view.setPurchasePriceRmb(row.getPurchasePriceRmb());
        view.setLengthCm(row.getLengthCm());
        view.setWidthCm(row.getWidthCm());
        view.setHeightCm(row.getHeightCm());
        view.setActualWeightKg(row.getActualWeightKg());
        view.setCategoryId(row.getCategoryId());
        view.setCategoryLabel(row.getCategoryLabel());
        view.setLogisticsCarrierId(row.getLogisticsCarrierId());
        view.setLogisticsCarrierLabel(row.getLogisticsCarrierLabel());
        view.setSalePrice(row.getSalePrice());
        view.setTargetMarginRate(row.getTargetMarginRate());
        view.setCandidateStatus(row.getCandidateStatus());
        view.setNotes(row.getNotes());
        view.setLatestCalculationStatus(row.getLatestCalculationStatus());
        view.setLatestCalculation(readCalculationJson(row.getLatestCalculationJson()));
        view.setCompetitorCount(row.getCompetitorCount() == null ? 0 : row.getCompetitorCount());
        view.setPurchaseOrderCount(row.getPurchaseOrderCount() == null ? 0 : row.getPurchaseOrderCount());
        if (includeRelations) {
            for (PreOrderProfitCompetitorRow competitor : mapper.selectCompetitorsByCandidate(row.getOwnerUserId(), row.getId())) {
                view.getCompetitors().add(toCompetitorView(competitor));
            }
            for (PreOrderProfitPurchaseOrderRow order : mapper.selectPurchaseOrdersByCandidate(row.getOwnerUserId(), row.getId())) {
                view.getPurchaseOrders().add(toPurchaseOrderView(order));
            }
        }
        return view;
    }

    private PreOrderProfitCompetitorView toCompetitorView(PreOrderProfitCompetitorRow row) {
        PreOrderProfitCompetitorView view = new PreOrderProfitCompetitorView();
        view.setId(row.getId());
        view.setCandidateId(row.getCandidateId());
        view.setStoreCode(row.getStoreCode());
        view.setTitle(row.getTitle());
        view.setUrl(row.getUrl());
        view.setPlatform(row.getPlatform());
        view.setSiteCode(row.getSiteCode());
        view.setPrice(row.getPrice());
        view.setCurrency(row.getCurrency());
        view.setSellerName(row.getSellerName());
        view.setNotes(row.getNotes());
        return view;
    }

    private PreOrderProfitPurchaseOrderView toPurchaseOrderView(PreOrderProfitPurchaseOrderRow row) {
        PreOrderProfitPurchaseOrderView view = new PreOrderProfitPurchaseOrderView();
        view.setId(row.getId());
        view.setStoreCode(row.getStoreCode());
        view.setSiteCode(row.getSiteCode());
        view.setName(row.getName());
        view.setNotes(row.getNotes());
        view.setItemCount(row.getItemCount() == null ? 0 : row.getItemCount());
        return view;
    }

    private PreOrderProfitPurchaseOrderLinkView linkView(
            PreOrderProfitPurchaseOrderItemRow row,
            boolean alreadyLinked
    ) {
        PreOrderProfitPurchaseOrderLinkView view = new PreOrderProfitPurchaseOrderLinkView();
        view.setItemId(row.getId());
        view.setPurchaseOrderId(row.getPurchaseOrderId());
        view.setCandidateId(row.getCandidateId());
        view.setAlreadyLinked(alreadyLinked);
        return view;
    }

    private String writeCalculationJson(PreOrderProfitCalculationView calculation) {
        try {
            return objectMapper.writeValueAsString(calculation);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("利润计算结果序列化失败。", exception);
        }
    }

    private PreOrderProfitCalculationView readCalculationJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PreOrderProfitCalculationView.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("利润计算结果读取失败。", exception);
        }
    }

    private Long ownerUserIdForCandidateLookup(BusinessAccessContext context) {
        Long ownerUserId = context.getBusinessOwnerUserId();
        if (ownerUserId != null) {
            return ownerUserId;
        }
        return context.getSessionUserId();
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId != null) {
            return ownerUserId;
        }
        if (context.getBusinessOwnerUserId() != null) {
            return context.getBusinessOwnerUserId();
        }
        return context.getSessionUserId();
    }

    private Long actorUserId(BusinessAccessContext context) {
        return context.getSessionUserId() == null ? context.getBusinessOwnerUserId() : context.getSessionUserId();
    }

    private String requireStoreCode(BusinessAccessContext context, String storeCode) {
        String normalized = requireText(storeCode, "店铺不能为空。").toUpperCase(Locale.ROOT);
        if (!context.canAccessStore(normalized)) {
            throw new IllegalArgumentException("当前账号不能操作该店铺。");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeLower(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
