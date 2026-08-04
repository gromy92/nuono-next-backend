package com.nuono.next.productlogisticscost;

import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.procurementorder.ProductForwarderEligibilityProductScope;
import com.nuono.next.procurementorder.ProductForwarderEligibilityProductService;
import com.nuono.next.procurementorder.WarehouseForwarderEligibilityService;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualCurrentQuoteWithEligibilityCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ProductMatchRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.EligibilityView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.EligibilityListView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.ManualCurrentQuoteWithEligibilityResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductLogisticsCurrentQuoteMaintenanceService {

    private final ProductLogisticsCostMapper mapper;
    private final ProductLogisticsCostLedgerService ledgerService;
    private final ProductForwarderEligibilityProductService eligibilityService;

    public ProductLogisticsCurrentQuoteMaintenanceService(
            ProductLogisticsCostMapper mapper,
            ProductLogisticsCostLedgerService ledgerService,
            ProductForwarderEligibilityProductService eligibilityService
    ) {
        this.mapper = mapper;
        this.ledgerService = ledgerService;
        this.eligibilityService = eligibilityService;
    }

    @Transactional(readOnly = true)
    public EligibilityView currentEligibility(
            Long ownerUserId,
            String storeCode,
            String partnerSku,
            String siteCode,
            String forwarderCode,
            String transportMode
    ) {
        Long requiredOwner = requirePositive(ownerUserId, "业务归属不能为空。");
        String requiredStore = requireText(storeCode, "店铺不能为空。");
        String requiredPartnerSku = requireText(partnerSku, "系统 PSKU 不能为空。");
        String normalizedSite = normalizeCode(requireText(siteCode, "站点不能为空。"));
        ProductMatchRow product = resolveProduct(
                requiredOwner, requiredStore, requiredPartnerSku, normalizedSite
        );
        ProductForwarderEligibilityProductScope scope = scope(
                requiredOwner,
                requiredStore,
                product,
                normalizedSite,
                forwarderCode,
                transportMode
        );
        EligibilityView view = new EligibilityView();
        view.partnerSku = product.partnerSku;
        view.eligibilityStatus = eligibilityService.currentStatus(scope);
        return view;
    }

    @Transactional(readOnly = true)
    public EligibilityListView currentEligibilities(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String forwarderCode,
            String transportMode
    ) {
        Long requiredOwner = requirePositive(ownerUserId, "业务归属不能为空。");
        String requiredStore = requireText(storeCode, "店铺不能为空。");
        Long logicalStoreId = mapper.selectLogicalStoreIdByStoreCode(requiredOwner, requiredStore);
        if (logicalStoreId == null || logicalStoreId <= 0) {
            throw new IllegalArgumentException("未找到当前店铺。");
        }
        Map<String, String> statuses = eligibilityService.currentStatusesForRoute(
                requiredOwner,
                logicalStoreId,
                normalizeCode(requireText(siteCode, "站点不能为空。")),
                normalizeCode(requireText(forwarderCode, "货代不能为空。")),
                normalizeCode(requireText(transportMode, "货运方式不能为空。"))
        );
        EligibilityListView view = new EligibilityListView();
        statuses.forEach((partnerSku, status) -> {
            EligibilityView item = new EligibilityView();
            item.partnerSku = partnerSku;
            item.eligibilityStatus = status;
            view.items.add(item);
        });
        return view;
    }

    @Transactional
    public ManualCurrentQuoteWithEligibilityResult maintainCurrentQuote(
            Long ownerUserId,
            Long operatorUserId,
            ManualCurrentQuoteWithEligibilityCommand command
    ) {
        Long requiredOwner = requirePositive(ownerUserId, "业务归属不能为空。");
        Long requiredOperator = requirePositive(operatorUserId, "操作人不能为空。");
        if (command == null) {
            throw new IllegalArgumentException("人工维护报价不能为空。");
        }
        String storeCode = requireText(command.storeCode, "店铺不能为空。");
        String partnerSku = requireText(command.partnerSku, "系统 PSKU 不能为空。");
        String siteCode = normalizeCode(requireText(command.siteCode, "站点不能为空。"));
        ProductMatchRow product = resolveProduct(requiredOwner, storeCode, partnerSku, siteCode);
        ProductForwarderEligibilityProductScope scope = scope(
                requiredOwner,
                storeCode,
                product,
                siteCode,
                command.forwarderCode,
                command.transportMode
        );
        String status = eligibilityService.updateProductRule(
                scope,
                command.eligibilityStatus,
                requiredOperator
        );

        ManualCurrentQuoteWithEligibilityResult result = new ManualCurrentQuoteWithEligibilityResult();
        result.eligibilityStatus = status;
        if (!WarehouseForwarderEligibilityService.UNSUPPORTED.equals(status)) {
            command.partnerSku = product.partnerSku;
            result.currentCost = ledgerService.manualCurrentQuote(requiredOwner, requiredOperator, command);
        }
        return result;
    }

    private ProductMatchRow resolveProduct(
            Long ownerUserId,
            String storeCode,
            String partnerSku,
            String siteCode
    ) {
        List<ProductMatchRow> matches = mapper.selectProductMatches(
                ownerUserId,
                storeCode,
                partnerSku,
                siteCode
        );
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("未找到当前店铺下的系统 PSKU：" + partnerSku);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("系统 PSKU 匹配到多个当前商品，请先清理商品主档：" + partnerSku);
        }
        ProductMatchRow product = matches.get(0);
        requireText(product.partnerSku, "商品主档系统 PSKU 不能为空。");
        if (product.logicalStoreId == null || product.logicalStoreId <= 0) {
            throw new IllegalArgumentException("商品缺少稳定店铺或 PSKU 身份，不能维护承运状态。");
        }
        return product;
    }

    private ProductForwarderEligibilityProductScope scope(
            Long ownerUserId,
            String storeCode,
            ProductMatchRow product,
            String siteCode,
            String forwarderCode,
            String transportMode
    ) {
        return new ProductForwarderEligibilityProductScope(
                ownerUserId,
                product.logicalStoreId,
                product.productMasterId,
                product.productVariantId,
                storeCode,
                product.partnerSku,
                siteCode,
                normalizeCode(requireText(forwarderCode, "货代不能为空。")),
                normalizeCode(requireText(transportMode, "货运方式不能为空。"))
        );
    }

    private static Long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
