package com.nuono.next.outboundfee;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialOutboundFeeMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class OfficialOutboundFeeCalculationService {

    private static final String STATUS_CALCULATED = "CALCULATED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SOURCE_TYPE_NOON_OFFICIAL = "noon_official";

    private final OfficialOutboundFeeProductContextReader productContextReader;
    private final OfficialOutboundFeeCalculator calculator;
    private final OfficialOutboundFeeMapper mapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public OfficialOutboundFeeCalculationService(
            OfficialOutboundFeeProductContextReader productContextReader,
            OfficialOutboundFeeCalculator calculator,
            OfficialOutboundFeeMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.productContextReader = productContextReader;
        this.calculator = calculator;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    OfficialOutboundFeeCalculationService(
            OfficialOutboundFeeProductContextReader productContextReader,
            OfficialOutboundFeeCalculator calculator
    ) {
        this.productContextReader = productContextReader;
        this.calculator = calculator;
        this.mapper = null;
        this.objectMapper = new ObjectMapper();
    }

    public OfficialOutboundFeeCalculationView calculateByEffectiveSpec(OfficialOutboundFeeCalculationCommand command) {
        validateCommand(command);
        OfficialOutboundFeeProductContext productContext = productContextReader.findContext(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSite(),
                command.getSkuId()
        ).orElse(null);
        if (productContext == null || productContext.getVariantId() == null) {
            command.setResolvedVariantId(null);
            OfficialOutboundFeeCalculationView view = failure(command, null, "SKU_NOT_FOUND", "当前 SKU 在请求店铺下不存在，无法计算官方 FBN 出仓费。", null);
            persistCalculationFact(command, view);
            return view;
        }
        command.setResolvedVariantId(productContext.getVariantId());
        OfficialOutboundFeeProductSpecRecord effectiveSpec = productContext.getEffectiveSpec();
        if (effectiveSpec == null || effectiveSpec.getEffectiveSourceId() == null) {
            OfficialOutboundFeeCalculationView view = failure(command, null, "MISSING_EFFECTIVE_SPEC", "当前 SKU 未选择 1688 或仓管经营生效规格，无法计算官方 FBN 出仓费。", productContext.getSalePrice());
            persistCalculationFact(command, view);
            return view;
        }
        String country = resolveCountry(command.getSite());
        String currency = resolveCurrency(command.getSite());
        BigDecimal salePrice = resolveSalePrice(command, productContext);
        OfficialOutboundFeeCalculationResult result = calculator.calculate(new OfficialOutboundFeeCalculationRequest(
                country,
                "NOON",
                "FBN",
                currency,
                salePrice,
                effectiveSpec.getProductWeightG(),
                effectiveSpec.getProductLengthCm(),
                effectiveSpec.getProductWidthCm(),
                effectiveSpec.getProductHeightCm(),
                command.getCalculationDate() == null ? LocalDate.now() : command.getCalculationDate()
        ));
        OfficialOutboundFeeCalculationView view = baseView(command, effectiveSpec, country, currency, salePrice);
        if (result.isSuccess()) {
            view.setReady(true);
            view.setStatus(STATUS_CALCULATED);
            view.setMessage("已按当前商品经营生效规格和文档管理生效规则计算官方 FBN 出仓费。");
            view.setFeeAmount(result.getFinalFeeAmount());
            view.setCurrency(result.getCurrency());
            view.setMatchedClassificationName(result.getMatchedClassificationName());
            view.setMatchedSlabNaturalKey(result.getMatchedSlabNaturalKey());
            view.setSourceVersionId(result.getSourceVersionId());
            view.setEvidence(result.getEvidence());
            persistCalculationFact(command, view);
            return view;
        }
        view.setReady(false);
        view.setStatus(STATUS_FAILED);
        view.setFailureCode(result.getFailure() == null ? null : result.getFailure().name());
        view.setMessage(failureMessage(view.getFailureCode()));
        persistCalculationFact(command, view);
        return view;
    }

    public OfficialOutboundFeeCalculationView calculateByNoonOfficialSpec(OfficialOutboundFeeCalculationCommand command) {
        validateCommand(command);
        OfficialOutboundFeeProductContext productContext = productContextReader.findContextBySpecSource(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSite(),
                command.getSkuId(),
                SOURCE_TYPE_NOON_OFFICIAL
        ).orElse(null);
        if (productContext == null || productContext.getVariantId() == null) {
            command.setResolvedVariantId(null);
            OfficialOutboundFeeCalculationView view = failure(command, null, "SKU_NOT_FOUND", "当前 SKU 在请求店铺下不存在，无法计算官方 FBN 出仓费。", null);
            applyTaxFields(view);
            persistCalculationFact(command, view);
            return view;
        }
        command.setResolvedVariantId(productContext.getVariantId());
        OfficialOutboundFeeProductSpecRecord noonOfficialSpec = productContext.getEffectiveSpec();
        BigDecimal salePrice = resolveSalePrice(command, productContext);
        if (noonOfficialSpec == null || noonOfficialSpec.getEffectiveSourceId() == null) {
            OfficialOutboundFeeCalculationView view = failure(command, null, "MISSING_NOON_OFFICIAL_SPEC", "当前 SKU 缺少 Noon 官方尺寸，无法按 Noon 官方尺寸计算出仓费。", salePrice);
            applyTaxFields(view);
            persistCalculationFact(command, view);
            return view;
        }
        String country = resolveCountry(command.getSite());
        String currency = resolveCurrency(command.getSite());
        OfficialOutboundFeeCalculationResult result = calculator.calculate(new OfficialOutboundFeeCalculationRequest(
                country,
                "NOON",
                "FBN",
                currency,
                salePrice,
                noonOfficialSpec.getProductWeightG(),
                noonOfficialSpec.getProductLengthCm(),
                noonOfficialSpec.getProductWidthCm(),
                noonOfficialSpec.getProductHeightCm(),
                command.getCalculationDate() == null ? LocalDate.now() : command.getCalculationDate()
        ));
        OfficialOutboundFeeCalculationView view = baseView(command, noonOfficialSpec, country, currency, salePrice);
        if (result.isSuccess()) {
            view.setReady(true);
            view.setStatus(STATUS_CALCULATED);
            view.setMessage("已按 Noon 官方尺寸和文档管理生效规则计算官方 FBN 出仓费。");
            view.setFeeAmount(result.getFinalFeeAmount());
            view.setCurrency(result.getCurrency());
            view.setMatchedClassificationName(result.getMatchedClassificationName());
            view.setMatchedSlabNaturalKey(result.getMatchedSlabNaturalKey());
            view.setSourceVersionId(result.getSourceVersionId());
            view.setEvidence(result.getEvidence());
            applyTaxFields(view);
            persistCalculationFact(command, view);
            return view;
        }
        view.setReady(false);
        view.setStatus(STATUS_FAILED);
        view.setFailureCode(result.getFailure() == null ? null : result.getFailure().name());
        view.setMessage(failureMessage(view.getFailureCode()));
        applyTaxFields(view);
        persistCalculationFact(command, view);
        return view;
    }

    public List<OfficialOutboundFeeCalculationView> latestCalculations(OfficialOutboundFeeLatestCalculationQuery query) {
        validateLatestCalculationQuery(query);
        if (mapper == null || query.getSkuIds().isEmpty()) {
            return List.of();
        }
        List<OfficialOutboundFeeCalculationView> views = mapper.selectLatestCalculationViewsBySkuIds(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSite(),
                query.getSkuIds(),
                query.getSpecSourceType()
        );
        views.forEach(this::applyTaxFields);
        return views;
    }

    private void validateCommand(OfficialOutboundFeeCalculationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少官方出仓费计算参数。");
        }
        if (command.getOwnerUserId() == null || command.getOwnerUserId() <= 0) {
            throw new IllegalArgumentException("老板上下文不能为空。");
        }
        if (!StringUtils.hasText(command.getStoreCode())) {
            throw new IllegalArgumentException("店铺不能为空。");
        }
        if (!command.hasSkuId()) {
            throw new IllegalArgumentException("SKU 不能为空。");
        }
        if (!StringUtils.hasText(command.getSite())) {
            throw new IllegalArgumentException("站点不能为空。");
        }
    }

    private void validateLatestCalculationQuery(OfficialOutboundFeeLatestCalculationQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("缺少官方出仓费历史计算查询参数。");
        }
        if (query.getOwnerUserId() == null || query.getOwnerUserId() <= 0) {
            throw new IllegalArgumentException("老板上下文不能为空。");
        }
        if (!StringUtils.hasText(query.getStoreCode())) {
            throw new IllegalArgumentException("店铺不能为空。");
        }
        if (!StringUtils.hasText(query.getSite())) {
            throw new IllegalArgumentException("站点不能为空。");
        }
    }

    private OfficialOutboundFeeCalculationView failure(
            OfficialOutboundFeeCalculationCommand command,
            OfficialOutboundFeeProductSpecRecord spec,
            String failureCode,
            String message,
            BigDecimal salePrice
    ) {
        OfficialOutboundFeeCalculationView view = baseView(command, spec, resolveCountry(command.getSite()), resolveCurrency(command.getSite()), salePrice);
        view.setReady(false);
        view.setStatus(STATUS_FAILED);
        view.setFailureCode(failureCode);
        view.setMessage(message);
        return view;
    }

    private OfficialOutboundFeeCalculationView baseView(
            OfficialOutboundFeeCalculationCommand command,
            OfficialOutboundFeeProductSpecRecord spec,
            String country,
            String currency,
            BigDecimal salePrice
    ) {
        OfficialOutboundFeeCalculationView view = new OfficialOutboundFeeCalculationView();
        view.setOwnerUserId(command.getOwnerUserId());
        view.setStoreCode(command.getStoreCode());
        view.setSkuId(command.getSkuId());
        view.setVariantId(command.getResolvedVariantId());
        view.setSite(command.getSite());
        view.setCountry(country);
        view.setMarketCurrency(currency);
        view.setSalePrice(salePrice);
        if (spec != null) {
            view.setEffectiveSourceId(spec.getEffectiveSourceId());
            view.setSpecSourceType(spec.getEffectiveSourceType());
            view.setPartnerSku(spec.getPartnerSku());
            view.setChildSku(spec.getChildSku());
            view.setLengthCm(spec.getProductLengthCm());
            view.setWidthCm(spec.getProductWidthCm());
            view.setHeightCm(spec.getProductHeightCm());
            view.setWeightGrams(spec.getProductWeightG());
        }
        return view;
    }

    private BigDecimal resolveSalePrice(
            OfficialOutboundFeeCalculationCommand command,
            OfficialOutboundFeeProductContext productContext
    ) {
        if (command.getSalePrice() != null) {
            return command.getSalePrice();
        }
        return productContext.getSalePrice();
    }

    private String resolveCountry(String site) {
        String normalized = normalizeSite(site);
        if ("SA".equals(normalized) || "KSA".equals(normalized)) {
            return "KSA";
        }
        if ("AE".equals(normalized) || "UAE".equals(normalized)) {
            return "UAE";
        }
        return normalized;
    }

    private String resolveCurrency(String site) {
        String normalized = normalizeSite(site);
        if ("SA".equals(normalized) || "KSA".equals(normalized)) {
            return "SAR";
        }
        if ("AE".equals(normalized) || "UAE".equals(normalized)) {
            return "AED";
        }
        return null;
    }

    private String normalizeSite(String site) {
        return site == null ? "" : site.trim().toUpperCase(Locale.ROOT);
    }

    private String failureMessage(String failureCode) {
        if ("MISSING_DIMENSIONS".equals(failureCode)) {
            return "当前商品经营生效规格缺少长宽高，无法匹配官方出仓费规格分级。";
        }
        if ("MISSING_WEIGHT".equals(failureCode)) {
            return "当前商品经营生效规格缺少重量，无法匹配官方出仓费重量段。";
        }
        if ("MISSING_SALE_PRICE".equals(failureCode)) {
            return "请求未传售价，且当前 SKU 站点报价表缺少售价，无法判断标准费用或高 ASP 费用。";
        }
        if ("SKU_NOT_FOUND".equals(failureCode)) {
            return "当前 SKU 在请求店铺下不存在，无法计算官方 FBN 出仓费。";
        }
        if ("MISSING_NOON_OFFICIAL_SPEC".equals(failureCode)) {
            return "当前 SKU 缺少 Noon 官方尺寸，无法按 Noon 官方尺寸计算出仓费。";
        }
        if ("POLICY_NOT_FOUND".equals(failureCode)) {
            return "当前站点没有可用的文档管理生效官方出仓费计算策略。";
        }
        if ("CLASSIFICATION_NOT_FOUND".equals(failureCode)) {
            return "当前商品经营生效规格超出文档管理生效官方规格分级范围。";
        }
        if ("SLAB_NOT_FOUND".equals(failureCode)) {
            return "当前商品计费重量没有命中文档管理生效官方费用重量段。";
        }
        if ("CURRENCY_MISMATCH".equals(failureCode)) {
            return "售价币种与文档管理生效官方出仓费规则币种不一致。";
        }
        return "官方 FBN 出仓费暂时无法计算。";
    }

    private void persistCalculationFact(
            OfficialOutboundFeeCalculationCommand command,
            OfficialOutboundFeeCalculationView view
    ) {
        applyTaxFields(view);
        if (mapper == null) {
            return;
        }
        if (view.getVariantId() == null) {
            return;
        }
        Long id = mapper.nextCalculationFactId();
        OfficialOutboundFeeCalculationFact fact = new OfficialOutboundFeeCalculationFact();
        fact.setOwnerUserId(view.getOwnerUserId());
        fact.setStoreCode(view.getStoreCode());
        fact.setSite(view.getSite());
        fact.setCountry(view.getCountry());
        fact.setPlatform(view.getPlatform());
        fact.setFulfillmentType(view.getFulfillmentType());
        fact.setVariantId(view.getVariantId());
        fact.setSkuId(view.getSkuId());
        fact.setPartnerSku(view.getPartnerSku());
        fact.setChildSku(view.getChildSku());
        fact.setEffectiveSourceId(view.getEffectiveSourceId());
        fact.setEffectiveSourceType(view.getSpecSourceType());
        fact.setProductLengthCm(view.getLengthCm());
        fact.setProductWidthCm(view.getWidthCm());
        fact.setProductHeightCm(view.getHeightCm());
        fact.setProductWeightG(view.getWeightGrams());
        fact.setSalePrice(view.getSalePrice());
        fact.setMarketCurrency(view.getMarketCurrency());
        fact.setCalculationDate(command.getCalculationDate() == null ? LocalDate.now() : command.getCalculationDate());
        fact.setFeeAmount(view.getFeeAmount());
        fact.setCurrency(view.getCurrency());
        fact.setTaxMultiplier(view.getTaxMultiplier());
        fact.setTaxIncludedFeeAmount(view.getTaxIncludedFeeAmount());
        fact.setMatchedClassificationName(view.getMatchedClassificationName());
        fact.setMatchedSlabNaturalKey(view.getMatchedSlabNaturalKey());
        fact.setSourceVersionId(view.getSourceVersionId());
        fact.setEvidenceJson(evidenceJson(view));
        fact.setStatus(view.getStatus());
        fact.setFailureCode(view.getFailureCode());
        fact.setMessage(view.getMessage());
        mapper.insertCalculationFact(id, fact, operatorUserId(command));
        view.setCalculationFactId(id);
    }

    private void applyTaxFields(OfficialOutboundFeeCalculationView view) {
        if (view == null) {
            return;
        }
        view.setReady(STATUS_CALCULATED.equals(view.getStatus()));
        BigDecimal multiplier = view.getTaxMultiplier() == null ? taxMultiplier(view.getSite()) : view.getTaxMultiplier();
        view.setTaxMultiplier(multiplier);
        if (!STATUS_CALCULATED.equals(view.getStatus())) {
            view.setTaxIncludedFeeAmount(null);
            return;
        }
        if (view.getTaxIncludedFeeAmount() == null && view.getFeeAmount() != null && multiplier != null) {
            view.setTaxIncludedFeeAmount(view.getFeeAmount().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
        }
    }

    private BigDecimal taxMultiplier(String site) {
        String normalized = normalizeSite(site);
        if ("SA".equals(normalized) || "KSA".equals(normalized)) {
            return new BigDecimal("1.15");
        }
        if ("AE".equals(normalized) || "UAE".equals(normalized)) {
            return new BigDecimal("1.05");
        }
        return null;
    }

    private String evidenceJson(OfficialOutboundFeeCalculationView view) {
        try {
            return objectMapper.writeValueAsString(view.getEvidence());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("官方 FBN 出仓费计算证据序列化失败。", exception);
        }
    }

    private Long operatorUserId(OfficialOutboundFeeCalculationCommand command) {
        return command.getOperatorUserId() == null || command.getOperatorUserId() <= 0
                ? command.getOwnerUserId()
                : command.getOperatorUserId();
    }
}
