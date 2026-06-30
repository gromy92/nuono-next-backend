package com.nuono.next.commission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialCommissionMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OfficialCommissionCalculationService {
    private static final String PLATFORM = "NOON";
    private static final String FULFILLMENT_TYPE = "FBN";

    private final OfficialCommissionMapper mapper;
    private final OfficialCommissionCalculator calculator;
    private final ObjectMapper objectMapper;

    public OfficialCommissionCalculationService(OfficialCommissionMapper mapper, OfficialCommissionCalculator calculator, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.calculator = calculator;
        this.objectMapper = objectMapper;
    }

    public OfficialCommissionCalculationView calculateByProduct(OfficialCommissionCalculationCommand command) {
        validateCommand(command);
        String site = normalizeSite(command.getSite());
        String country = countryFromSite(site);
        LocalDate calculationDate = command.getCalculationDate() == null ? LocalDate.now() : command.getCalculationDate();
        OfficialCommissionProductContext context = mapper.selectProductContext(
                command.getOwnerUserId(),
                command.getStoreCode(),
                site,
                command.getSkuId()
        );
        if (context == null) {
            return productNotFound(command, site, country);
        }
        context.setSite(site);
        BigDecimal salePrice = command.getSalePrice() == null ? context.getStoredSalePrice() : command.getSalePrice();
        String marketCurrency = StringUtils.hasText(context.getMarketCurrency()) ? context.getMarketCurrency() : currencyFromSite(site);
        List<OfficialCommissionRule> rules = mapper.selectActiveCommissionRules(country, PLATFORM, FULFILLMENT_TYPE, calculationDate);
        OfficialCommissionCalculationView view = calculator.calculate(context, rules, country, PLATFORM, FULFILLMENT_TYPE, salePrice, marketCurrency, calculationDate);
        view.setOwnerUserId(command.getOwnerUserId());
        view.setStoreCode(command.getStoreCode());
        view.setSite(site);
        view.setCountry(country);
        view.setPlatform(PLATFORM);
        view.setFulfillmentType(FULFILLMENT_TYPE);
        persist(command, view, calculationDate);
        return view;
    }

    public List<OfficialCommissionCalculationView> calculateBatch(OfficialCommissionBatchCalculationCommand command) {
        if (command == null || !StringUtils.hasText(command.getStoreCode()) || !command.hasItems()) {
            throw new IllegalArgumentException("storeCode and items are required.");
        }
        List<OfficialCommissionCalculationView> results = new ArrayList<>();
        for (OfficialCommissionCalculationCommand item : command.getItems()) {
            item.setOwnerUserId(command.getOwnerUserId());
            item.setStoreCode(command.getStoreCode());
            item.setSite(StringUtils.hasText(item.getSite()) ? item.getSite() : command.getSite());
            item.setCalculationDate(item.getCalculationDate() == null ? command.getCalculationDate() : item.getCalculationDate());
            item.setOperatorUserId(command.getOperatorUserId());
            try {
                results.add(calculateByProduct(item));
            } catch (RuntimeException exception) {
                results.add(batchItemFailure(item, exception));
            }
        }
        return results;
    }

    public List<OfficialCommissionCalculationView> latestCalculations(OfficialCommissionLatestCalculationQuery query) {
        if (query == null || query.getOwnerUserId() == null || !StringUtils.hasText(query.getStoreCode()) || !StringUtils.hasText(query.getSite())) {
            throw new IllegalArgumentException("ownerUserId, storeCode and site are required.");
        }
        if (query.getSkuIds() == null || query.getSkuIds().isEmpty()) {
            return List.of();
        }
        List<OfficialCommissionCalculationView> views = mapper.selectLatestCalculationViewsBySkuIds(
                query.getOwnerUserId(),
                query.getStoreCode().trim(),
                normalizeSite(query.getSite()),
                query.getSkuIds()
        );
        for (OfficialCommissionCalculationView view : views) {
            view.setReady("CALCULATED".equals(view.getStatus()));
        }
        return views;
    }

    private void validateCommand(OfficialCommissionCalculationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (command.getOwnerUserId() == null || !StringUtils.hasText(command.getStoreCode()) || !command.hasSkuId()) {
            throw new IllegalArgumentException("ownerUserId, storeCode and skuId are required.");
        }
    }

    private OfficialCommissionCalculationView batchItemFailure(OfficialCommissionCalculationCommand command, RuntimeException exception) {
        String site = normalizeSite(command.getSite());
        OfficialCommissionCalculationView view = new OfficialCommissionCalculationView();
        view.setReady(false);
        view.setStatus("FAILED");
        view.setFailureCode("BATCH_ITEM_FAILED");
        view.setMessage(exception.getMessage() == null ? "当前 SKU 佣金计算失败。" : exception.getMessage());
        view.setOwnerUserId(command.getOwnerUserId());
        view.setStoreCode(command.getStoreCode());
        view.setSkuId(command.getSkuId());
        view.setSite(site);
        view.setCountry(countryFromSite(site));
        view.setPlatform(PLATFORM);
        view.setFulfillmentType(FULFILLMENT_TYPE);
        view.setSalePrice(command.getSalePrice());
        view.setMarketCurrency(currencyFromSite(site));
        return view;
    }

    private OfficialCommissionCalculationView productNotFound(OfficialCommissionCalculationCommand command, String site, String country) {
        OfficialCommissionCalculationView view = new OfficialCommissionCalculationView();
        view.setReady(false);
        view.setStatus("FAILED");
        view.setFailureCode("PRODUCT_NOT_FOUND");
        view.setMessage("当前店铺站点下没有找到该 SKU，无法计算佣金。");
        view.setOwnerUserId(command.getOwnerUserId());
        view.setStoreCode(command.getStoreCode());
        view.setSkuId(command.getSkuId());
        view.setSite(site);
        view.setCountry(country);
        view.setPlatform(PLATFORM);
        view.setFulfillmentType(FULFILLMENT_TYPE);
        view.setSalePrice(command.getSalePrice());
        view.setMarketCurrency(currencyFromSite(site));
        return view;
    }

    private void persist(OfficialCommissionCalculationCommand command, OfficialCommissionCalculationView view, LocalDate calculationDate) {
        if (view.getVariantId() == null) {
            return;
        }
        view.setEvidenceJson(toJson(view.getEvidence()));
        Long id = mapper.nextCalculationFactId();
        mapper.insertCalculationFact(id, view, calculationDate, operatorUserId(command));
        view.setCalculationFactId(id);
    }

    private Long operatorUserId(OfficialCommissionCalculationCommand command) {
        return command.getOperatorUserId() == null ? command.getOwnerUserId() : command.getOperatorUserId();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String normalizeSite(String site) {
        String normalized = site == null ? "" : site.trim().toUpperCase(Locale.ROOT);
        if ("AE".equals(normalized) || "UAE".equals(normalized)) {
            return "AE";
        }
        return "SA";
    }

    private String countryFromSite(String site) {
        return "AE".equals(normalizeSite(site)) ? "UAE" : "KSA";
    }

    private String currencyFromSite(String site) {
        return "AE".equals(normalizeSite(site)) ? "AED" : "SAR";
    }
}
