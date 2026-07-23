package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

abstract class WarehouseShippingLineCostSupport extends WarehouseDispatchServiceState {

    protected WarehouseShippingLineCostSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

    protected abstract CargoCategoryEstimate inferCargoCategory(List<String> sensitiveReasons);

    protected abstract ForwarderRouteQuoteRecord selectRouteQuote(
            List<ForwarderRouteQuoteRecord> quotes,
            CargoCategoryEstimate cargoCategory,
            PendingShippingLine line
    );

    protected abstract Map<String, Object> readJsonObject(String value);

    protected abstract String writeJson(Object value);

    protected final class PendingShippingLine {
        protected final ShippingBatchSourceRecord firstSource;
        protected final String actualTransportMode;
        protected final String targetForwarderCode;
        protected final String targetForwarderName;
        protected final String routeCode;
        protected final String routeName;
        protected final String warningJson;
        protected final List<PendingShippingSource> sources = new ArrayList<>();
        protected BigDecimal actualWeightKg;
        protected BigDecimal volumeCbm;
        protected BigDecimal chargeableWeightKg;
        protected BigDecimal estimatedAmount;
        protected String currency;
        protected Boolean minimumNotMet = false;
        protected String cargoCategoryCode;
        protected String cargoCategoryName;
        protected String quoteCargoCategoryCode;
        protected String quoteCargoCategoryName;
        protected Boolean cargoCategoryReviewRequired = false;
        protected Boolean quoteMissingForCargoCategory = false;

        protected PendingShippingLine(
                ShippingBatchSourceRecord firstSource,
                String actualTransportMode,
                ShippingForwarderAssignment assignment,
                String warningJson
        ) {
            this.firstSource = firstSource;
            this.actualTransportMode = actualTransportMode;
            this.targetForwarderCode = assignment.targetForwarderCode;
            this.targetForwarderName = assignment.targetForwarderName;
            this.routeCode = assignment.routeCode;
            this.routeName = assignment.routeName;
            this.warningJson = warningJson;
        }

        protected int quantity() {
            return sources.stream().mapToInt(source -> source.quantity).sum();
        }

        protected void evaluate(List<ForwarderRouteQuoteRecord> quotes) {
            actualWeightKg = totalActualWeightKg();
            volumeCbm = totalVolumeCbm();
            CargoCategoryEstimate cargoCategory = inferCargoCategory(sensitiveReasons());
            cargoCategoryCode = cargoCategory.code;
            cargoCategoryName = cargoCategory.name;
            cargoCategoryReviewRequired = cargoCategory.reviewRequired;
            ForwarderRouteQuoteRecord quote = selectRouteQuote(quotes, cargoCategory, this);
            if (quote == null) {
                quoteMissingForCargoCategory = true;
            } else {
                quoteCargoCategoryCode = quote.cargoCategoryCode;
                quoteCargoCategoryName = quote.cargoCategoryName;
            }
            if (TRANSPORT_AIR.equals(actualTransportMode) && actualWeightKg != null && volumeCbm != null) {
                BigDecimal divisor = quote == null || quote.volumeDivisor == null || quote.volumeDivisor.signum() <= 0
                        ? DEFAULT_AIR_VOLUME_DIVISOR
                        : quote.volumeDivisor;
                BigDecimal volumeWeightKg = volumeCbm.multiply(CUBIC_CM_PER_CBM).divide(divisor, 6, RoundingMode.HALF_UP);
                chargeableWeightKg = actualWeightKg.max(volumeWeightKg).setScale(3, RoundingMode.HALF_UP);
            } else if (TRANSPORT_SEA.equals(actualTransportMode) && actualWeightKg != null) {
                chargeableWeightKg = actualWeightKg.setScale(3, RoundingMode.HALF_UP);
            }
            if (quote == null || quote.minUnitPrice == null || !StringUtils.hasText(quote.billingUnit)) {
                return;
            }
            currency = quote.currency;
            BigDecimal billableQuantity = billableQuantity(quote);
            if (billableQuantity == null) {
                return;
            }
            BigDecimal minBillableUnit = effectiveMinBillableUnit(quote);
            if (minBillableUnit != null && minBillableUnit.signum() > 0
                    && billableQuantity.compareTo(minBillableUnit) < 0) {
                minimumNotMet = true;
                billableQuantity = minBillableUnit;
            }
            BigDecimal amount = billableQuantity.multiply(quote.minUnitPrice);
            if (quote.minCharge != null && quote.minCharge.signum() > 0 && amount.compareTo(quote.minCharge) < 0) {
                amount = quote.minCharge;
            }
            estimatedAmount = amount.setScale(4, RoundingMode.HALF_UP);
        }

        protected BigDecimal effectiveMinBillableUnit(ForwarderRouteQuoteRecord quote) {
            if (quote.minBillableUnit != null && quote.minBillableUnit.signum() > 0) {
                return quote.minBillableUnit;
            }
            String billingUnit = quote.billingUnit == null ? "" : quote.billingUnit.trim().toUpperCase(Locale.ROOT);
            if ("YT-SAU-SEA-FBN-RUH".equals(routeCode) && "CBM".equals(billingUnit)) {
                return DEFAULT_YT_SEA_MIN_CBM;
            }
            return null;
        }

        protected BigDecimal billableQuantity(ForwarderRouteQuoteRecord quote) {
            String billingUnit = quote.billingUnit == null ? "" : quote.billingUnit.trim().toUpperCase(Locale.ROOT);
            if ("CBM".equals(billingUnit)) {
                return volumeCbm == null ? null : volumeCbm.setScale(4, RoundingMode.HALF_UP);
            }
            if ("KG".equals(billingUnit)) {
                return chargeableWeightKg == null ? null : chargeableWeightKg.setScale(3, RoundingMode.HALF_UP);
            }
            return null;
        }

        protected BigDecimal totalActualWeightKg() {
            BigDecimal total = BigDecimal.ZERO;
            for (PendingShippingSource source : sources) {
                if (source.source.productWeightG == null) {
                    return null;
                }
                total = total.add(source.source.productWeightG.multiply(BigDecimal.valueOf(source.quantity)));
            }
            return total.divide(GRAMS_PER_KG, 3, RoundingMode.HALF_UP);
        }

        protected BigDecimal totalVolumeCbm() {
            BigDecimal total = BigDecimal.ZERO;
            for (PendingShippingSource source : sources) {
                if (source.source.productLengthCm == null || source.source.productWidthCm == null || source.source.productHeightCm == null) {
                    return null;
                }
                total = total.add(source.source.productLengthCm
                        .multiply(source.source.productWidthCm)
                        .multiply(source.source.productHeightCm)
                        .multiply(BigDecimal.valueOf(source.quantity)));
            }
            return total.divide(CUBIC_CM_PER_CBM, 4, RoundingMode.HALF_UP);
        }

        protected List<String> sensitiveReasons() {
            return sources.stream()
                    .flatMap(source -> source.sensitiveReasons().stream())
                    .distinct()
                    .collect(Collectors.toList());
        }

        protected Map<String, Object> costSnapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("partnerSku", firstSource.partnerSku);
            snapshot.put("targetForwarderCode", targetForwarderCode);
            snapshot.put("targetForwarderName", targetForwarderName);
            snapshot.put("routeCode", routeCode);
            snapshot.put("routeName", routeName);
            snapshot.put("transportMode", actualTransportMode);
            snapshot.put("quantity", quantity());
            snapshot.put("actualWeightKg", actualWeightKg);
            snapshot.put("volumeCbm", volumeCbm);
            snapshot.put("chargeableWeightKg", chargeableWeightKg);
            snapshot.put("estimatedAmount", estimatedAmount);
            snapshot.put("currency", currency);
            snapshot.put("minimumNotMet", minimumNotMet);
            snapshot.put("cargoCategoryCode", cargoCategoryCode);
            snapshot.put("cargoCategoryName", cargoCategoryName);
            snapshot.put("quoteCargoCategoryCode", quoteCargoCategoryCode);
            snapshot.put("quoteCargoCategoryName", quoteCargoCategoryName);
            snapshot.put("cargoCategoryReviewRequired", cargoCategoryReviewRequired);
            snapshot.put("quoteMissingForCargoCategory", quoteMissingForCargoCategory);
            snapshot.put("sensitiveReasons", sensitiveReasons());
            return snapshot;
        }

        protected String augmentedWarningJson() {
            Map<String, Object> snapshot = new LinkedHashMap<>(readJsonObject(warningJson));
            snapshot.put("cargoCategoryCode", cargoCategoryCode);
            snapshot.put("cargoCategoryName", cargoCategoryName);
            snapshot.put("quoteCargoCategoryCode", quoteCargoCategoryCode);
            snapshot.put("quoteCargoCategoryName", quoteCargoCategoryName);
            snapshot.put("cargoCategoryReviewRequired", cargoCategoryReviewRequired);
            snapshot.put("quoteMissingForCargoCategory", quoteMissingForCargoCategory);
            return writeJson(snapshot);
        }

        protected ShippingSuggestionLineRecord toRecord(
                Long optionId,
                Long batchId,
                Long ownerUserId,
                Long lineId
        ) {
            ShippingSuggestionLineRecord record = new ShippingSuggestionLineRecord();
            record.id = lineId;
            record.optionId = optionId;
            record.batchId = batchId;
            record.ownerUserId = ownerUserId;
            record.productMasterId = firstSource.productMasterId;
            record.productVariantId = firstSource.productVariantId;
            record.partnerSku = firstSource.partnerSku;
            record.skuParent = firstSource.skuParent;
            record.titleCache = firstSource.titleCache;
            record.imageUrlCache = firstSource.imageUrlCache;
            record.siteCode = firstSource.siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = firstSource.fulfillmentType;
            record.sourcePartyName = firstSource.sourcePartyName;
            record.specStatus = firstSource.specStatus;
            record.targetForwarderCode = targetForwarderCode;
            record.targetForwarderName = targetForwarderName;
            record.routeCode = routeCode;
            record.routeName = routeName;
            record.actualWeightKg = actualWeightKg;
            record.volumeCbm = volumeCbm;
            record.chargeableWeightKg = chargeableWeightKg;
            record.estimatedAmount = estimatedAmount;
            record.currency = currency;
            record.quantity = quantity();
            record.sourceCount = sources.size();
            record.warningJson = augmentedWarningJson();
            return record;
        }
    }
}
