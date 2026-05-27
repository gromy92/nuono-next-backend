package com.nuono.next.logisticsquote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteFactMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MyBatisLogisticsQuoteFactRepositoryTest {

    @Test
    void readsActiveServiceLinesForRecommendationConsumption() {
        CapturingLogisticsQuoteFactMapper mapper = new CapturingLogisticsQuoteFactMapper();
        MyBatisLogisticsQuoteFactRepository repository = new MyBatisLogisticsQuoteFactRepository(mapper);
        repository.insertServiceLine(new LogisticsServiceLineFact(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "KSA",
                null,
                "KSA/Riyadh",
                "sea",
                "warehouse_to_fbn",
                "义特沙特海运双清包税",
                "中国",
                "KSA/Riyadh",
                "weekly",
                25,
                35,
                "2026-05-01",
                LogisticsQuoteFactStatus.ACTIVE.value(),
                lineage(3001L)
        ));

        List<LogisticsServiceLineFact> rows = repository.findActiveServiceLines(new LogisticsServiceLineQuery(
                null,
                "KSA",
                "sea",
                "warehouse_to_fbn",
                "KSA/Riyadh"
        ));

        assertEquals(1, rows.size());
        assertEquals("yite", rows.get(0).getForwarderCode());
        assertEquals("义特物流", rows.get(0).getForwarderName());
        assertEquals("KSA/Riyadh", rows.get(0).getDestinationNode());
        assertEquals("quote.pdf", rows.get(0).getSourceLineage().getSourceFileName());
    }

    @Test
    void readsComparablePriceRulesWithServiceLineAndCargoCategoryContext() {
        CapturingLogisticsQuoteFactMapper mapper = new CapturingLogisticsQuoteFactMapper();
        MyBatisLogisticsQuoteFactRepository repository = new MyBatisLogisticsQuoteFactRepository(mapper);
        LogisticsQuoteComparisonService comparisonService = new LogisticsQuoteComparisonService(repository);
        repository.insertServiceLine(serviceLine("yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh", "yite", "ACTIVE"));
        repository.insertServiceLine(serviceLine("et|KSA|air|warehouse_to_fbn|KSA/Riyadh", "et", "ACTIVE"));
        repository.insertCargoCategory(category(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "普货",
                false,
                "ACTIVE"
        ));
        repository.insertCargoCategory(category(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|battery",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "带电",
                false,
                "ACTIVE"
        ));
        repository.insertPriceRule(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "120",
                "cbm",
                "ACTIVE",
                "NORMAL"
        ));
        repository.insertPriceRule(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|battery|cbm",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|battery",
                "150",
                "cbm",
                "ACTIVE",
                "NORMAL"
        ));
        repository.insertPriceRule(price(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "90",
                "cbm",
                "ACTIVE",
                "NORMAL"
        ));
        repository.insertPriceRule(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm|old",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "100",
                "cbm",
                "SUPERSEDED",
                "NORMAL"
        ));
        repository.insertPriceRule(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm|ask",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                null,
                "cbm",
                "ACTIVE",
                "ASK_QUOTE"
        ));

        LogisticsQuoteComparisonResult result = comparisonService.compareBasePrices(new LogisticsQuoteComparisonQuery(
                "KSA",
                "sea",
                "warehouse_to_fbn",
                "普货",
                "cbm"
        ));

        assertEquals(1, result.getRows().size());
        LogisticsPriceRuleFact row = result.getRows().get(0);
        assertEquals("yite", row.getForwarderCode());
        assertEquals(new BigDecimal("120"), row.getUnitPrice());
        assertEquals("yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh", row.getServiceLineKey());
        assertEquals("yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal", row.getCargoCategoryKey());
        assertEquals(3003L, row.getSourceLineage().getSourceVersionItemId());
        assertEquals("quote.pdf", row.getSourceLineage().getSourceFileName());
    }

    @Test
    void readsActiveCargoCategoriesForRecommendationConsumption() {
        CapturingLogisticsQuoteFactMapper mapper = new CapturingLogisticsQuoteFactMapper();
        MyBatisLogisticsQuoteFactRepository repository = new MyBatisLogisticsQuoteFactRepository(mapper);
        repository.insertCargoCategory(category(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "普货",
                false,
                "ACTIVE"
        ));
        repository.insertCargoCategory(category(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|manual",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "需确认",
                true,
                "ACTIVE"
        ));
        repository.insertCargoCategory(category(
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "et",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "普货",
                false,
                "ACTIVE"
        ));
        repository.insertCargoCategory(category(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|old",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "旧分类",
                false,
                "SUPERSEDED"
        ));

        List<LogisticsCargoCategoryFact> rows = repository.findActiveCargoCategories(
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh"
        );

        assertEquals(2, rows.size());
        assertEquals("yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal", rows.get(0).getNaturalKey());
        assertEquals("普货", rows.get(0).getCategoryName());
        assertEquals("yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|manual", rows.get(1).getNaturalKey());
        assertEquals("需确认", rows.get(1).getCategoryName());
        assertEquals("quote.pdf", rows.get(0).getSourceLineage().getSourceFileName());
    }

    @Test
    void readsActivePriceRulesByServiceLineForShipmentCostEstimation() {
        CapturingLogisticsQuoteFactMapper mapper = new CapturingLogisticsQuoteFactMapper();
        MyBatisLogisticsQuoteFactRepository repository = new MyBatisLogisticsQuoteFactRepository(mapper);
        repository.insertPriceRule(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "120",
                "cbm",
                "ACTIVE",
                "NORMAL"
        ));
        repository.insertPriceRule(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|ask|cbm",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                null,
                "cbm",
                "ACTIVE",
                "ASK_QUOTE"
        ));
        repository.insertPriceRule(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|old|cbm",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "80",
                "cbm",
                "SUPERSEDED",
                "NORMAL"
        ));
        repository.insertPriceRule(price(
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "et",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "110",
                "cbm",
                "ACTIVE",
                "NORMAL"
        ));

        List<LogisticsPriceRuleFact> rows = repository.findPriceRulesByServiceLineKey(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh"
        );

        assertEquals(2, rows.size());
        assertEquals("NORMAL", rows.get(0).getPriceStatus());
        assertEquals(new BigDecimal("120"), rows.get(0).getUnitPrice());
        assertEquals("ASK_QUOTE", rows.get(1).getPriceStatus());
        assertEquals("quote.pdf", rows.get(1).getSourceLineage().getSourceFileName());
    }

    @Test
    void readsActiveRestrictionRulesByServiceLineForRecommendationFiltering() {
        CapturingLogisticsQuoteFactMapper mapper = new CapturingLogisticsQuoteFactMapper();
        MyBatisLogisticsQuoteFactRepository repository = new MyBatisLogisticsQuoteFactRepository(mapper);
        repository.insertRestrictionRule(restriction(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "battery",
                "带电禁运",
                "hard",
                false,
                "ACTIVE"
        ));
        repository.insertRestrictionRule(restriction(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|manual",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "sensitive",
                "敏感货人工确认",
                "warning",
                true,
                "ACTIVE"
        ));
        repository.insertRestrictionRule(restriction(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|old",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "old",
                "旧规则",
                "hard",
                false,
                "SUPERSEDED"
        ));

        List<LogisticsRestrictionRuleFact> rows = repository.findRestrictionRulesByServiceLineKey(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh"
        );

        assertEquals(2, rows.size());
        assertEquals("battery", rows.get(0).getRestrictionType());
        assertEquals("hard", rows.get(0).getSeverity());
        assertEquals("sensitive", rows.get(1).getRestrictionType());
        assertTrue(rows.get(1).isManualConfirmRequired());
        assertEquals("quote.pdf", rows.get(0).getSourceLineage().getSourceFileName());
    }

    private static LogisticsServiceLineFact serviceLine(String naturalKey, String forwarderCode, String status) {
        return new LogisticsServiceLineFact(
                naturalKey,
                forwarderCode,
                forwarderCode,
                "KSA",
                null,
                "KSA/Riyadh",
                naturalKey.contains("|air|") ? "air" : "sea",
                "warehouse_to_fbn",
                forwarderCode,
                "中国",
                "KSA/Riyadh",
                "weekly",
                25,
                35,
                "2026-05-01",
                status,
                lineage(3001L)
        );
    }

    private static LogisticsCargoCategoryFact category(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String categoryName,
            boolean manualConfirmRequired,
            String status
    ) {
        return new LogisticsCargoCategoryFact(
                naturalKey,
                forwarderCode,
                serviceLineKey,
                null,
                categoryName,
                categoryName,
                null,
                null,
                null,
                null,
                null,
                manualConfirmRequired,
                status,
                lineage(3002L)
        );
    }

    private static LogisticsPriceRuleFact price(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String cargoCategoryKey,
            String unitPrice,
            String billingUnit,
            String status,
            String priceStatus
    ) {
        return new LogisticsPriceRuleFact(
                naturalKey,
                forwarderCode,
                serviceLineKey,
                cargoCategoryKey,
                unitPrice == null ? null : new BigDecimal(unitPrice),
                "SAR",
                billingUnit,
                "unit_price",
                null,
                null,
                null,
                null,
                null,
                null,
                priceStatus,
                "2026-05-01",
                status,
                lineage(3003L)
        );
    }

    private static LogisticsQuoteFactSourceLineage lineage(Long sourceVersionItemId) {
        return new LogisticsQuoteFactSourceLineage(
                "file_management",
                20076L,
                40054L,
                70054L,
                sourceVersionItemId,
                "quote.pdf",
                "page 1"
        );
    }

    private static LogisticsRestrictionRuleFact restriction(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String restrictionType,
            String itemText,
            String severity,
            boolean manualConfirmRequired,
            String status
    ) {
        return new LogisticsRestrictionRuleFact(
                naturalKey,
                forwarderCode,
                serviceLineKey,
                restrictionType,
                itemText,
                itemText,
                restrictionType,
                severity,
                manualConfirmRequired,
                status,
                lineage(3004L)
        );
    }

    private static class CapturingLogisticsQuoteFactMapper implements LogisticsQuoteFactMapper {

        private final List<LogisticsServiceLineFact> serviceLines = new ArrayList<>();
        private final List<LogisticsCargoCategoryFact> cargoCategories = new ArrayList<>();
        private final List<LogisticsPriceRuleFact> priceRules = new ArrayList<>();
        private final List<LogisticsRestrictionRuleFact> restrictionRules = new ArrayList<>();

        @Override
        public Long nextFactId(String tableName, long initialValue) {
            return initialValue + 1;
        }

        @Override
        public int countBySourceVersionItemId(String tableName, Long sourceVersionItemId) {
            return 0;
        }

        @Override
        public int countActiveByNaturalKey(String tableName, String naturalKey) {
            return 0;
        }

        @Override
        public int supersedeActiveByNaturalKey(String tableName, String naturalKey) {
            return 0;
        }

        @Override
        public List<Map<String, Object>> selectActiveServiceLineRows(
                String forwarderCode,
                String country,
                String transportMode,
                String serviceScope,
                String destinationNode
        ) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (LogisticsServiceLineFact fact : serviceLines) {
                if (matches(forwarderCode, fact.getForwarderCode())
                        && matches(country, fact.getCountry())
                        && matches(transportMode, fact.getTransportMode())
                        && matches(serviceScope, fact.getServiceScope())
                        && matches(destinationNode, fact.getDestinationNode())
                        && LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus())) {
                    rows.add(serviceLineRow(fact));
                }
            }
            return rows;
        }

        @Override
        public List<Map<String, Object>> selectComparablePriceRuleRows(
                String country,
                String transportMode,
                String serviceScope,
                String cargoCategoryName,
                String billingUnit
        ) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (LogisticsPriceRuleFact price : priceRules) {
                if (!price.isComparable() || !matches(billingUnit, price.getBillingUnit())) {
                    continue;
                }
                LogisticsServiceLineFact serviceLine = serviceLines.stream()
                        .filter(line -> line.getNaturalKey().equals(price.getServiceLineKey()))
                        .filter(line -> line.getForwarderCode().equals(price.getForwarderCode()))
                        .filter(line -> LogisticsQuoteFactStatus.ACTIVE.value().equals(line.getStatus()))
                        .findFirst()
                        .orElse(null);
                if (serviceLine == null
                        || !matches(country, serviceLine.getCountry())
                        || !matches(transportMode, serviceLine.getTransportMode())
                        || !matches(serviceScope, serviceLine.getServiceScope())) {
                    continue;
                }
                LogisticsCargoCategoryFact category = cargoCategories.stream()
                        .filter(value -> value.getNaturalKey().equals(price.getCargoCategoryKey()))
                        .filter(value -> value.getForwarderCode().equals(price.getForwarderCode()))
                        .filter(value -> value.getServiceLineKey().equals(serviceLine.getNaturalKey()))
                        .filter(LogisticsCargoCategoryFact::isComparable)
                        .findFirst()
                        .orElse(null);
                if (category != null && matches(cargoCategoryName, category.getCategoryName())) {
                    rows.add(priceRuleRow(price));
                }
            }
            return rows;
        }

        @Override
        public List<Map<String, Object>> selectActiveCargoCategoryRows(String forwarderCode, String serviceLineKey) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (LogisticsCargoCategoryFact category : cargoCategories) {
                if (matches(forwarderCode, category.getForwarderCode())
                        && matches(serviceLineKey, category.getServiceLineKey())
                        && LogisticsQuoteFactStatus.ACTIVE.value().equals(category.getStatus())) {
                    rows.add(cargoCategoryRow(category));
                }
            }
            return rows;
        }

        @Override
        public List<Map<String, Object>> selectActivePriceRuleRowsByServiceLineKey(String serviceLineKey) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (LogisticsPriceRuleFact price : priceRules) {
                if (matches(serviceLineKey, price.getServiceLineKey())
                        && LogisticsQuoteFactStatus.ACTIVE.value().equals(price.getStatus())) {
                    rows.add(priceRuleRow(price));
                }
            }
            return rows;
        }

        @Override
        public List<Map<String, Object>> selectActiveRestrictionRuleRowsByServiceLineKey(String serviceLineKey) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (LogisticsRestrictionRuleFact restriction : restrictionRules) {
                if (matches(serviceLineKey, restriction.getServiceLineKey())
                        && LogisticsQuoteFactStatus.ACTIVE.value().equals(restriction.getStatus())) {
                    rows.add(restrictionRuleRow(restriction));
                }
            }
            return rows;
        }

        @Override
        public int insertServiceLine(Long id, LogisticsServiceLineFact fact, Long operatorUserId) {
            serviceLines.add(fact);
            return 1;
        }

        @Override
        public int insertCargoCategory(Long id, LogisticsCargoCategoryFact fact, Long operatorUserId) {
            cargoCategories.add(fact);
            return 1;
        }

        @Override
        public int insertPriceRule(Long id, LogisticsPriceRuleFact fact, Long operatorUserId) {
            priceRules.add(fact);
            return 1;
        }

        @Override
        public int insertSurchargeRule(Long id, LogisticsSurchargeRuleFact fact, Long operatorUserId) {
            return 1;
        }

        @Override
        public int insertBillingRule(Long id, LogisticsBillingRuleFact fact, Long operatorUserId) {
            return 1;
        }

        @Override
        public int insertRestrictionRule(Long id, LogisticsRestrictionRuleFact fact, Long operatorUserId) {
            restrictionRules.add(fact);
            return 1;
        }

        @Override
        public int insertWarehouseFeeRule(Long id, LogisticsWarehouseFeeRuleFact fact, Long operatorUserId) {
            return 1;
        }

        private boolean matches(String expected, String actual) {
            return expected == null || expected.isBlank() || expected.equals(actual);
        }

        private Map<String, Object> serviceLineRow(LogisticsServiceLineFact fact) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("naturalKey", fact.getNaturalKey());
            row.put("forwarderCode", fact.getForwarderCode());
            row.put("forwarderName", fact.getForwarderName());
            row.put("country", fact.getCountry());
            row.put("fulfillmentMode", fact.getFulfillmentMode());
            row.put("destinationNode", fact.getDestinationNode());
            row.put("transportMode", fact.getTransportMode());
            row.put("serviceScope", fact.getServiceScope());
            row.put("channelName", fact.getChannelName());
            row.put("originWarehouse", fact.getOriginWarehouse());
            row.put("destinationWarehouse", fact.getDestinationWarehouse());
            row.put("departureFrequency", fact.getDepartureFrequency());
            row.put("estimatedDaysMin", fact.getEstimatedDaysMin());
            row.put("estimatedDaysMax", fact.getEstimatedDaysMax());
            row.put("effectiveFrom", fact.getEffectiveFrom());
            row.put("status", fact.getStatus());
            row.put("sourceType", fact.getSourceLineage().getSourceType());
            row.put("sourceTaskId", fact.getSourceLineage().getSourceTaskId());
            row.put("sourceResultId", fact.getSourceLineage().getSourceResultId());
            row.put("sourceVersionId", fact.getSourceLineage().getSourceVersionId());
            row.put("sourceVersionItemId", fact.getSourceLineage().getSourceVersionItemId());
            row.put("sourceFileName", fact.getSourceLineage().getSourceFileName());
            row.put("sourceLocator", fact.getSourceLineage().getSourceLocator());
            return row;
        }

        private Map<String, Object> priceRuleRow(LogisticsPriceRuleFact fact) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("naturalKey", fact.getNaturalKey());
            row.put("forwarderCode", fact.getForwarderCode());
            row.put("serviceLineKey", fact.getServiceLineKey());
            row.put("cargoCategoryKey", fact.getCargoCategoryKey());
            row.put("unitPrice", fact.getUnitPrice());
            row.put("currency", fact.getCurrency());
            row.put("billingUnit", fact.getBillingUnit());
            row.put("pricingModel", fact.getPricingModel());
            row.put("minimumBillableUnit", fact.getMinimumBillableUnit());
            row.put("minimumBillableUnitType", fact.getMinimumBillableUnitType());
            row.put("minimumCharge", fact.getMinimumCharge());
            row.put("volumeDivisor", fact.getVolumeDivisor());
            row.put("seaWeightRatio", fact.getSeaWeightRatio());
            row.put("roundingRule", fact.getRoundingRule());
            row.put("priceStatus", fact.getPriceStatus());
            row.put("effectiveFrom", fact.getEffectiveFrom());
            row.put("status", fact.getStatus());
            row.put("sourceType", fact.getSourceLineage().getSourceType());
            row.put("sourceTaskId", fact.getSourceLineage().getSourceTaskId());
            row.put("sourceResultId", fact.getSourceLineage().getSourceResultId());
            row.put("sourceVersionId", fact.getSourceLineage().getSourceVersionId());
            row.put("sourceVersionItemId", fact.getSourceLineage().getSourceVersionItemId());
            row.put("sourceFileName", fact.getSourceLineage().getSourceFileName());
            row.put("sourceLocator", fact.getSourceLineage().getSourceLocator());
            return row;
        }

        private Map<String, Object> cargoCategoryRow(LogisticsCargoCategoryFact fact) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("naturalKey", fact.getNaturalKey());
            row.put("forwarderCode", fact.getForwarderCode());
            row.put("serviceLineKey", fact.getServiceLineKey());
            row.put("categoryCode", fact.getCategoryCode());
            row.put("categoryName", fact.getCategoryName());
            row.put("sourceCategoryName", fact.getSourceCategoryName());
            row.put("productExamples", fact.getProductExamples());
            row.put("keywords", fact.getKeywords());
            row.put("electricType", fact.getElectricType());
            row.put("sensitiveTags", fact.getSensitiveTags());
            row.put("packingPolicy", fact.getPackingPolicy());
            row.put("manualConfirmRequired", fact.isManualConfirmRequired());
            row.put("status", fact.getStatus());
            row.put("sourceType", fact.getSourceLineage().getSourceType());
            row.put("sourceTaskId", fact.getSourceLineage().getSourceTaskId());
            row.put("sourceResultId", fact.getSourceLineage().getSourceResultId());
            row.put("sourceVersionId", fact.getSourceLineage().getSourceVersionId());
            row.put("sourceVersionItemId", fact.getSourceLineage().getSourceVersionItemId());
            row.put("sourceFileName", fact.getSourceLineage().getSourceFileName());
            row.put("sourceLocator", fact.getSourceLineage().getSourceLocator());
            return row;
        }

        private Map<String, Object> restrictionRuleRow(LogisticsRestrictionRuleFact fact) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("naturalKey", fact.getNaturalKey());
            row.put("forwarderCode", fact.getForwarderCode());
            row.put("serviceLineKey", fact.getServiceLineKey());
            row.put("restrictionType", fact.getRestrictionType());
            row.put("itemText", fact.getItemText());
            row.put("requirementText", fact.getRequirementText());
            row.put("applicabilityScope", fact.getApplicabilityScope());
            row.put("severity", fact.getSeverity());
            row.put("manualConfirmRequired", fact.isManualConfirmRequired());
            row.put("status", fact.getStatus());
            row.put("sourceType", fact.getSourceLineage().getSourceType());
            row.put("sourceTaskId", fact.getSourceLineage().getSourceTaskId());
            row.put("sourceResultId", fact.getSourceLineage().getSourceResultId());
            row.put("sourceVersionId", fact.getSourceLineage().getSourceVersionId());
            row.put("sourceVersionItemId", fact.getSourceLineage().getSourceVersionItemId());
            row.put("sourceFileName", fact.getSourceLineage().getSourceFileName());
            row.put("sourceLocator", fact.getSourceLineage().getSourceLocator());
            return row;
        }
    }
}
