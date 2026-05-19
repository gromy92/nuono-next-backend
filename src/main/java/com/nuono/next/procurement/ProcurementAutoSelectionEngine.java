package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProcurementAutoSelectionEngine {

    public AutoSelectionResult generate(DemandItemView demandItem) {
        int selectedImageCount = countSelectedImages(demandItem);
        String searchMode = selectedImageCount > 1 ? "IMAGE_MULTI" : selectedImageCount == 1 ? "IMAGE_SINGLE" : "KEYWORD";
        String searchPath = selectedImageCount > 0 ? "IMAGE_ID_LIST_DIRECT" : "TITLE_KEYWORD_FALLBACK";

        List<GeneratedCandidate> candidates = new ArrayList<>();
        candidates.add(buildCandidate(demandItem, CandidateProfile.STRONG, 1));
        candidates.add(buildCandidate(demandItem, CandidateProfile.MEDIUM, 2));
        candidates.add(buildCandidate(demandItem, CandidateProfile.WEAK, 3));

        int recommendedCount = 0;
        for (GeneratedCandidate candidate : candidates) {
            if ("recommended".equals(candidate.getLevel())) {
                recommendedCount += 1;
            }
        }

        String taskStatus = selectedImageCount > 0 ? "SUCCESS" : "PARTIAL_SUCCESS";
        String message = selectedImageCount > 0
                ? String.format("已按当前采购要求重新完成自动选品，回收 %d 条候选，建议优先查看前 %d 条高匹配结果。", candidates.size(), Math.max(1, recommendedCount))
                : String.format("已按标题和采购要求完成兜底筛选，回收 %d 条候选。后续补主图后可再跑一轮更稳的图搜。", candidates.size());

        return new AutoSelectionResult(
                taskStatus,
                100,
                searchMode,
                selectedImageCount,
                searchPath,
                candidates.size(),
                recommendedCount,
                message,
                candidates
        );
    }

    public GeneratedCandidate evaluateExtractedCandidate(DemandItemView demandItem, CandidateView extractedCandidate, int rankNo) {
        CandidateFields fields = new CandidateFields();
        PriceRange priceRange = parsePriceRange(extractedCandidate.getPriceText(), demandItem);
        fields.setPriceMin(priceRange.getMinPrice());
        fields.setPriceMax(priceRange.getMaxPrice());
        fields.setMoq(resolveMoq(extractedCandidate.getMoqText(), demandItem));
        fields.setSupplierName(defaultText(extractedCandidate.getSupplierName(), "1688供应商待识别"));
        fields.setLocationText(defaultText(extractedCandidate.getLocationText(), "发货地待确认"));
        fields.setMaterialText(extractedCandidate.getMaterialText());
        fields.setPowerModeText(extractedCandidate.getPowerModeText());
        fields.setSizeText(extractedCandidate.getSizeText());
        fields.setPackageText(extractedCandidate.getPackageText());
        fields.setDeliveryTimelineText(extractedCandidate.getDeliveryTimelineText());

        CandidateScore score = scoreExtractedCandidate(demandItem, fields, extractedCandidate);
        GeneratedCandidate candidate = new GeneratedCandidate();
        candidate.setRankNo(rankNo);
        candidate.setLevel(score.getLevel());
        candidate.setTotalScore(score.getTotalScore());
        candidate.setFitScore(score.getFitScore());
        candidate.setSpecScore(score.getSpecScore());
        candidate.setPriceScore(score.getPriceScore());
        candidate.setSupplierScore(score.getSupplierScore());
        candidate.setLogisticsScore(score.getLogisticsScore());
        candidate.setCandidatePlatform(defaultText(extractedCandidate.getCandidatePlatform(), "1688"));
        candidate.setCandidateUrl(extractedCandidate.getCandidateUrl());
        candidate.setTitle(defaultText(extractedCandidate.getTitle(), "1688 候选待确认"));
        candidate.setSupplierName(fields.getSupplierName());
        candidate.setPriceText(extractedCandidate.getPriceText());
        candidate.setMoqText(extractedCandidate.getMoqText());
        candidate.setLocationText(fields.getLocationText());
        candidate.setMaterialText(fields.getMaterialText());
        candidate.setPowerModeText(fields.getPowerModeText());
        candidate.setSizeText(fields.getSizeText());
        candidate.setPackageText(fields.getPackageText());
        candidate.setDeliveryTimelineText(fields.getDeliveryTimelineText());
        candidate.setResultCardText(extractedCandidate.getResultCardText());
        candidate.setDetailHighlightText(extractedCandidate.getDetailHighlightText());
        candidate.setAttributeSnapshotText(extractedCandidate.getAttributeSnapshotText());
        candidate.setShippingSnapshotText(extractedCandidate.getShippingSnapshotText());
        candidate.setPackageSnapshotText(extractedCandidate.getPackageSnapshotText());
        candidate.setMainImageUrl(extractedCandidate.getMainImageUrl());
        candidate.setDetailImageUrl(extractedCandidate.getDetailImageUrl());
        candidate.setDeliveryImageUrl(extractedCandidate.getDeliveryImageUrl());
        candidate.setNextAction(defaultNextAction(score.getLevel()));
        candidate.setBadges(score.getBadges());
        candidate.setReasons(score.getReasons());
        candidate.setWarnings(score.getWarnings());
        return candidate;
    }

    private GeneratedCandidate buildCandidate(DemandItemView demandItem, CandidateProfile profile, int rankNo) {
        CandidateFields fields = buildFields(demandItem, profile);
        CandidateScore score = scoreCandidate(demandItem, fields, profile);

        GeneratedCandidate candidate = new GeneratedCandidate();
        candidate.setRankNo(rankNo);
        candidate.setLevel(score.getLevel());
        candidate.setTotalScore(score.getTotalScore());
        candidate.setFitScore(score.getFitScore());
        candidate.setSpecScore(score.getSpecScore());
        candidate.setPriceScore(score.getPriceScore());
        candidate.setSupplierScore(score.getSupplierScore());
        candidate.setLogisticsScore(score.getLogisticsScore());
        candidate.setCandidatePlatform("1688");
        candidate.setTitle(buildTitle(demandItem, profile));
        candidate.setSupplierName(fields.getSupplierName());
        candidate.setPriceText(formatPriceText(fields.getPriceMin(), fields.getPriceMax()));
        candidate.setMoqText(formatMoq(fields.getMoq()));
        candidate.setLocationText(fields.getLocationText());
        candidate.setMaterialText(fields.getMaterialText());
        candidate.setPowerModeText(fields.getPowerModeText());
        candidate.setSizeText(fields.getSizeText());
        candidate.setPackageText(fields.getPackageText());
        candidate.setDeliveryTimelineText(fields.getDeliveryTimelineText());
        candidate.setResultCardText(buildResultCardText(candidate, fields));
        candidate.setDetailHighlightText(buildDetailHighlightText(fields));
        candidate.setAttributeSnapshotText(buildAttributeSnapshotText(fields));
        candidate.setShippingSnapshotText(buildShippingSnapshotText(fields));
        candidate.setPackageSnapshotText(buildPackageSnapshotText(fields));
        candidate.setMainImageUrl(firstNonBlank(demandItem.getSourceImageUrl(), demandItem.getSourceDetailImageUrl()));
        candidate.setDetailImageUrl(firstNonBlank(demandItem.getSourceDetailImageUrl(), demandItem.getSourceImageUrl()));
        candidate.setDeliveryImageUrl(firstNonBlank(demandItem.getSourcePackageImageUrl(), demandItem.getSourceDetailImageUrl()));
        candidate.setNextAction(profile.getNextAction());
        candidate.setCandidateUrl(buildCandidateUrl(demandItem.getId(), rankNo));
        candidate.setBadges(score.getBadges());
        candidate.setReasons(score.getReasons());
        candidate.setWarnings(score.getWarnings());
        return candidate;
    }

    private String buildResultCardText(GeneratedCandidate candidate, CandidateFields fields) {
        return String.join(
                "；",
                Arrays.asList(
                        "结果卡片",
                        normalizeWhitespace(candidate.getTitle()),
                        defaultText(candidate.getSupplierName(), "1688供应商待识别"),
                        "价格 " + defaultText(candidate.getPriceText(), "-"),
                        "起订量 " + defaultText(candidate.getMoqText(), "-"),
                        "发货地 " + defaultText(candidate.getLocationText(), "-"),
                        "发货时效 " + defaultText(fields.getDeliveryTimelineText(), "-")
                )
        );
    }

    private String buildDetailHighlightText(CandidateFields fields) {
        return String.join(
                "；",
                Arrays.asList(
                        "详情卖点",
                        defaultText(fields.getMaterialText(), "材质待确认"),
                        defaultText(fields.getPowerModeText(), "供电方式待确认"),
                        defaultText(fields.getSizeText(), "尺寸待确认"),
                        defaultText(fields.getPackageText(), "包装待确认")
                )
        );
    }

    private String buildAttributeSnapshotText(CandidateFields fields) {
        return String.join(
                "；",
                Arrays.asList(
                        "属性快照",
                        "材质 " + defaultText(fields.getMaterialText(), "待确认"),
                        "供电方式 " + defaultText(fields.getPowerModeText(), "待确认"),
                        "尺寸 " + defaultText(fields.getSizeText(), "待确认"),
                        "包装 " + defaultText(fields.getPackageText(), "待确认")
                )
        );
    }

    private String buildShippingSnapshotText(CandidateFields fields) {
        return String.join(
                "；",
                Arrays.asList(
                        "物流说明",
                        "发货承诺 " + defaultText(fields.getDeliveryTimelineText(), "待确认"),
                        "支持常规打样和批量发货"
                )
        );
    }

    private String buildPackageSnapshotText(CandidateFields fields) {
        return String.join(
                "；",
                Arrays.asList(
                        "包装说明",
                        defaultText(fields.getPackageText(), "包装待确认"),
                        "包装清单可二次确认"
                )
        );
    }

    private CandidateFields buildFields(DemandItemView demandItem, CandidateProfile profile) {
        BigDecimal minPrice = defaultMinPrice(demandItem.getTargetPriceMin(), demandItem.getTargetPriceMax());
        BigDecimal maxPrice = defaultMaxPrice(demandItem.getTargetPriceMin(), demandItem.getTargetPriceMax());
        BigDecimal span = maxPrice.subtract(minPrice);

        CandidateFields fields = new CandidateFields();
        fields.setMaterialText(resolveMaterial(demandItem.getTargetMaterial(), profile));
        fields.setPowerModeText(resolvePowerMode(demandItem.getTargetPowerMode(), profile));
        fields.setSizeText(resolveSize(demandItem.getTargetSizeText(), profile));
        fields.setPackageText(resolvePackage(demandItem.getTargetPackageType(), profile));
        fields.setDeliveryTimelineText(resolveDelivery(demandItem.getDeliveryExpectation(), profile));
        fields.setSupplierName(resolveSupplierName(demandItem, profile));
        fields.setLocationText(resolveLocation(demandItem, profile));

        if (profile == CandidateProfile.STRONG) {
            fields.setPriceMin(minPrice.add(span.multiply(BigDecimal.valueOf(0.08))).setScale(1, RoundingMode.HALF_UP));
            fields.setPriceMax(minPrice.add(span.multiply(BigDecimal.valueOf(0.58))).setScale(1, RoundingMode.HALF_UP));
            fields.setMoq(scaleQuantity(demandItem.getTargetQuantity(), 0.75, 120));
        } else if (profile == CandidateProfile.MEDIUM) {
            fields.setPriceMin(minPrice.subtract(span.multiply(BigDecimal.valueOf(0.10))).max(BigDecimal.valueOf(1.5)).setScale(1, RoundingMode.HALF_UP));
            fields.setPriceMax(maxPrice.add(span.multiply(BigDecimal.valueOf(0.18))).setScale(1, RoundingMode.HALF_UP));
            fields.setMoq(scaleQuantity(demandItem.getTargetQuantity(), 0.95, 180));
        } else {
            fields.setPriceMin(maxPrice.add(span.multiply(BigDecimal.valueOf(0.28))).setScale(1, RoundingMode.HALF_UP));
            fields.setPriceMax(maxPrice.add(span.multiply(BigDecimal.valueOf(0.80))).setScale(1, RoundingMode.HALF_UP));
            fields.setMoq(scaleQuantity(demandItem.getTargetQuantity(), 1.45, 300));
        }

        if (fields.getPriceMax().compareTo(fields.getPriceMin()) < 0) {
            fields.setPriceMax(fields.getPriceMin().add(BigDecimal.valueOf(1.2)));
        }
        return fields;
    }

    private CandidateScore scoreCandidate(DemandItemView demandItem, CandidateFields fields, CandidateProfile profile) {
        CandidateScore score = new CandidateScore();
        score.setPriceScore(scorePrice(demandItem.getTargetPriceMin(), demandItem.getTargetPriceMax(), fields.getPriceMin(), fields.getPriceMax()));
        score.setSupplierScore(scoreSupplier(profile));
        score.setLogisticsScore(scoreDelivery(demandItem.getDeliveryExpectation(), fields.getDeliveryTimelineText()));

        MatchCounter materialCounter = applyFieldMatch(
                "材质",
                compareField(demandItem.getTargetMaterial(), fields.getMaterialText(), FieldType.MATERIAL),
                score
        );
        MatchCounter powerCounter = applyFieldMatch(
                "供电方式",
                compareField(demandItem.getTargetPowerMode(), fields.getPowerModeText(), FieldType.POWER),
                score
        );
        MatchCounter sizeCounter = applyFieldMatch(
                "尺寸",
                compareField(demandItem.getTargetSizeText(), fields.getSizeText(), FieldType.SIZE),
                score
        );
        MatchCounter packageCounter = applyFieldMatch(
                "包装",
                compareField(demandItem.getTargetPackageType(), fields.getPackageText(), FieldType.PACKAGE),
                score
        );
        MatchCounter deliveryCounter = applyFieldMatch(
                "交期",
                compareField(demandItem.getDeliveryExpectation(), fields.getDeliveryTimelineText(), FieldType.DELIVERY),
                score
        );

        int specScore = materialCounter.getSpecPoints()
                + powerCounter.getSpecPoints()
                + sizeCounter.getSpecPoints()
                + packageCounter.getSpecPoints();
        score.setSpecScore(Math.max(4, Math.min(25, specScore)));

        int fitScore = 18
                + materialCounter.getFitPoints()
                + powerCounter.getFitPoints()
                + sizeCounter.getFitPoints()
                + packageCounter.getFitPoints()
                + deliveryCounter.getFitPoints();
        if (profile == CandidateProfile.STRONG) {
            fitScore += 8;
        } else if (profile == CandidateProfile.MEDIUM) {
            fitScore += 4;
        }
        score.setFitScore(Math.max(10, Math.min(40, fitScore)));

        if (score.getPriceScore() >= 14) {
            score.getReasons().add("价格带落在目标区间");
        } else if (score.getPriceScore() >= 9) {
            score.getReasons().add("价格接近目标区间");
        } else {
            score.getWarnings().add("价格需要继续复核");
        }

        if (score.getLogisticsScore() >= 12) {
            score.getReasons().add("交期可覆盖当前采购节奏");
        } else if (score.getLogisticsScore() >= 9) {
            score.getWarnings().add("交期还需要带着问题去询价");
        } else {
            score.getWarnings().add("交期偏慢，建议暂缓推进");
        }

        if (profile == CandidateProfile.STRONG) {
            score.getBadges().addAll(Arrays.asList("实力商家", "图搜优先"));
            score.getReasons().add("供应商能力较强");
        } else if (profile == CandidateProfile.MEDIUM) {
            score.getBadges().add("可继续比对");
            score.getReasons().add("可作为意向采购备选");
        } else {
            score.getBadges().add("外观相近");
            score.getWarnings().add("主体方向接近，但配置仍偏弱");
        }

        deduplicate(score.getBadges());
        deduplicate(score.getReasons());
        deduplicate(score.getWarnings());

        int totalScore = score.getFitScore()
                + score.getSpecScore()
                + score.getPriceScore()
                + score.getSupplierScore()
                + score.getLogisticsScore();
        score.setTotalScore(totalScore);
        if (totalScore >= 78) {
            score.setLevel("recommended");
        } else if (totalScore >= 58) {
            score.setLevel("review");
        } else {
            score.setLevel("reject");
        }
        return score;
    }

    private CandidateScore scoreExtractedCandidate(DemandItemView demandItem, CandidateFields fields, CandidateView extractedCandidate) {
        CandidateScore score = new CandidateScore();
        score.setPriceScore(scorePrice(demandItem.getTargetPriceMin(), demandItem.getTargetPriceMax(), fields.getPriceMin(), fields.getPriceMax()));
        score.setSupplierScore(scoreSupplier(extractedCandidate));
        score.setLogisticsScore(scoreDelivery(demandItem.getDeliveryExpectation(), fields.getDeliveryTimelineText()));

        MatchCounter materialCounter = applyFieldMatch(
                "材质",
                compareField(demandItem.getTargetMaterial(), fields.getMaterialText(), FieldType.MATERIAL),
                score
        );
        MatchCounter powerCounter = applyFieldMatch(
                "供电方式",
                compareField(demandItem.getTargetPowerMode(), fields.getPowerModeText(), FieldType.POWER),
                score
        );
        MatchCounter sizeCounter = applyFieldMatch(
                "尺寸",
                compareField(demandItem.getTargetSizeText(), fields.getSizeText(), FieldType.SIZE),
                score
        );
        MatchCounter packageCounter = applyFieldMatch(
                "包装",
                compareField(demandItem.getTargetPackageType(), fields.getPackageText(), FieldType.PACKAGE),
                score
        );
        MatchCounter deliveryCounter = applyFieldMatch(
                "交期",
                compareField(demandItem.getDeliveryExpectation(), fields.getDeliveryTimelineText(), FieldType.DELIVERY),
                score
        );

        int specScore = materialCounter.getSpecPoints()
                + powerCounter.getSpecPoints()
                + sizeCounter.getSpecPoints()
                + packageCounter.getSpecPoints();
        score.setSpecScore(Math.max(4, Math.min(25, specScore)));
        score.setFitScore(Math.max(
                10,
                Math.min(
                        40,
                        16
                                + materialCounter.getFitPoints()
                                + powerCounter.getFitPoints()
                                + sizeCounter.getFitPoints()
                                + packageCounter.getFitPoints()
                                + deliveryCounter.getFitPoints()
                )
        ));

        if (StringUtils.hasText(extractedCandidate.getMainImageUrl())) {
            score.getBadges().add("图搜优先");
            score.getReasons().add("结果卡含主图，可继续核验");
        }
        if (StringUtils.hasText(extractedCandidate.getCandidateUrl())) {
            score.getReasons().add("候选链接完整，便于回看原始结果");
        }

        if (score.getPriceScore() >= 14) {
            score.getReasons().add("价格带落在目标区间");
        } else if (score.getPriceScore() >= 9) {
            score.getReasons().add("价格接近目标区间");
        } else {
            score.getWarnings().add("价格偏离目标区间");
        }

        if (score.getSupplierScore() >= 14) {
            score.getBadges().add("实力商家");
            score.getReasons().add("供应商能力较强");
        } else if (score.getSupplierScore() >= 10) {
            score.getReasons().add("供应商能力可继续观察");
        } else {
            score.getWarnings().add("供应商能力线索偏弱");
        }

        if (score.getLogisticsScore() >= 12) {
            score.getReasons().add("交期可覆盖当前采购节奏");
        } else if (score.getLogisticsScore() >= 9) {
            score.getWarnings().add("交期还需要进一步确认");
        } else {
            score.getWarnings().add("交期偏慢，需谨慎推进");
        }

        mergePipeSegments(score.getBadges(), extractedCandidate.getBadgesText(), 4);
        mergePipeSegments(score.getReasons(), extractedCandidate.getReasonsText(), 6);
        mergePipeSegments(score.getWarnings(), extractedCandidate.getWarningsText(), 6);

        deduplicate(score.getReasons());
        deduplicate(score.getWarnings());
        deduplicate(score.getBadges());

        int totalScore = score.getFitScore()
                + score.getSpecScore()
                + score.getPriceScore()
                + score.getSupplierScore()
                + score.getLogisticsScore();
        score.setTotalScore(Math.max(12, Math.min(100, totalScore)));
        if (score.getTotalScore() >= 78) {
            score.setLevel("recommended");
            score.getBadges().add("高推荐");
        } else if (score.getTotalScore() >= 58) {
            score.setLevel("review");
            score.getBadges().add("待复核");
        } else {
            score.setLevel("reject");
            score.getBadges().add("淘汰");
        }
        deduplicate(score.getBadges());
        return score;
    }

    private MatchCounter applyFieldMatch(String label, FieldMatch match, CandidateScore score) {
        MatchCounter counter = new MatchCounter();
        if (match == FieldMatch.EXACT) {
            score.getReasons().add(label + "符合采购要求");
            counter.specPoints = 4;
            counter.fitPoints = 3;
            counter.exactMatchCount = 1;
        } else if (match == FieldMatch.PARTIAL) {
            score.getReasons().add(label + "方向接近采购要求");
            score.getWarnings().add(label + "仍需人工进一步确认");
            counter.specPoints = 2;
            counter.fitPoints = 1;
            counter.partialMatchCount = 1;
        } else if (match == FieldMatch.MISMATCH) {
            score.getWarnings().add(label + "与采购要求存在偏差");
        }
        return counter;
    }

    private FieldMatch compareField(String targetValue, String candidateValue, FieldType fieldType) {
        if (!StringUtils.hasText(targetValue) || !StringUtils.hasText(candidateValue)) {
            return FieldMatch.UNKNOWN;
        }
        String normalizedTarget = normalize(targetValue);
        String normalizedCandidate = normalize(candidateValue);
        if (normalizedTarget.equals(normalizedCandidate)
                || normalizedTarget.contains(normalizedCandidate)
                || normalizedCandidate.contains(normalizedTarget)) {
            return FieldMatch.EXACT;
        }

        List<String> keywords = keywordSet(fieldType);
        for (String keyword : keywords) {
            if (normalizedTarget.contains(keyword) && normalizedCandidate.contains(keyword)) {
                return FieldMatch.PARTIAL;
            }
        }

        if (fieldType == FieldType.DELIVERY) {
            Integer targetDays = extractFirstNumber(targetValue);
            Integer candidateDays = extractFirstNumber(candidateValue);
            if (targetDays != null && candidateDays != null) {
                if (candidateDays <= targetDays) {
                    return FieldMatch.EXACT;
                }
                if (candidateDays <= targetDays + 3) {
                    return FieldMatch.PARTIAL;
                }
            }
        }
        return FieldMatch.MISMATCH;
    }

    private List<String> keywordSet(FieldType fieldType) {
        if (fieldType == FieldType.MATERIAL) {
            return Arrays.asList("abs", "陶瓷", "树脂", "金属", "塑料", "电镀");
        }
        if (fieldType == FieldType.POWER) {
            return Arrays.asList("充电", "usb", "插电", "无电", "蜡烛", "炭");
        }
        if (fieldType == FieldType.SIZE) {
            return Arrays.asList("便携", "手持", "迷你", "桌面", "落地", "cm");
        }
        if (fieldType == FieldType.PACKAGE) {
            return Arrays.asList("礼盒", "彩盒", "普通盒", "箱", "袋", "opp");
        }
        return Arrays.asList("48小时", "72小时", "天");
    }

    private int scorePrice(BigDecimal targetMin, BigDecimal targetMax, BigDecimal candidateMin, BigDecimal candidateMax) {
        if (candidateMin == null || candidateMax == null) {
            return 4;
        }
        BigDecimal effectiveMin = defaultMinPrice(targetMin, targetMax);
        BigDecimal effectiveMax = defaultMaxPrice(targetMin, targetMax);
        if (candidateMax.compareTo(effectiveMin) >= 0 && candidateMin.compareTo(effectiveMax) <= 0) {
            return 14;
        }
        BigDecimal nearLow = effectiveMin.subtract(BigDecimal.valueOf(2));
        BigDecimal nearHigh = effectiveMax.add(BigDecimal.valueOf(2));
        if (candidateMax.compareTo(nearLow) >= 0 && candidateMin.compareTo(nearHigh) <= 0) {
            return 9;
        }
        return 4;
    }

    private int scoreSupplier(CandidateProfile profile) {
        if (profile == CandidateProfile.STRONG) {
            return 14;
        }
        if (profile == CandidateProfile.MEDIUM) {
            return 10;
        }
        return 6;
    }

    private int scoreSupplier(CandidateView extractedCandidate) {
        String supplierSignals = String.join(
                " ",
                defaultText(extractedCandidate.getSupplierName(), ""),
                defaultText(extractedCandidate.getBadgesText(), ""),
                defaultText(extractedCandidate.getReasonsText(), ""),
                defaultText(extractedCandidate.getResultCardText(), "")
        );
        if (containsAny(supplierSignals, "超级工厂", "源头工厂", "深度验厂")) {
            return 14;
        }
        if (containsAny(supplierSignals, "实力商家", "诚信通")) {
            return 10;
        }
        return 6;
    }

    private int scoreDelivery(String targetValue, String candidateValue) {
        FieldMatch match = compareField(targetValue, candidateValue, FieldType.DELIVERY);
        if (match == FieldMatch.EXACT) {
            return 12;
        }
        if (match == FieldMatch.PARTIAL) {
            return 9;
        }
        if (match == FieldMatch.UNKNOWN) {
            return 6;
        }
        return 4;
    }

    private PriceRange parsePriceRange(String priceText, DemandItemView demandItem) {
        if (!StringUtils.hasText(priceText)) {
            return new PriceRange(null, null);
        }
        String normalized = priceText.replaceAll("[^0-9.\\-~至]", " ").trim();
        String[] parts = normalized.split("[-~至\\s]+");
        List<BigDecimal> values = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            try {
                values.add(new BigDecimal(part));
            } catch (NumberFormatException exception) {
                // ignore invalid segment
            }
        }
        if (values.isEmpty()) {
            return new PriceRange(null, null);
        }
        BigDecimal min = values.get(0);
        BigDecimal max = values.size() > 1 ? values.get(1) : values.get(0);
        if (max.compareTo(min) < 0) {
            max = min;
        }
        return new PriceRange(min, max);
    }

    private int resolveMoq(String moqText, DemandItemView demandItem) {
        Integer parsed = extractFirstNumber(moqText);
        if (parsed != null && parsed > 0) {
            return parsed;
        }
        return scaleQuantity(demandItem.getTargetQuantity(), 1, 200);
    }

    private void mergePipeSegments(List<String> target, String rawValue, int maxCount) {
        if (!StringUtils.hasText(rawValue)) {
            return;
        }
        String[] parts = rawValue.split("\\|");
        for (String part : parts) {
            String normalized = normalizeWhitespace(part);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            target.add(normalized);
            if (target.size() >= maxCount) {
                break;
            }
        }
    }

    private void deduplicate(List<String> values) {
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                deduplicated.add(value.trim());
            }
        }
        values.clear();
        values.addAll(deduplicated);
    }

    private String defaultNextAction(String level) {
        if ("recommended".equals(level)) {
            return "PREPARE_INQUIRY";
        }
        if ("review".equals(level)) {
            return "CONTINUE_COMPARE";
        }
        return "HOLD";
    }

    private String buildTitle(DemandItemView demandItem, CandidateProfile profile) {
        String baseTitle = normalizeWhitespace(demandItem.getSourceTitle());
        if (!StringUtils.hasText(baseTitle)) {
            baseTitle = "同类货源";
        }
        if (baseTitle.length() > 24) {
            baseTitle = baseTitle.substring(0, 24).trim();
        }
        return baseTitle + profile.getTitleSuffix();
    }

    private String buildCandidateUrl(Long demandItemId, int rankNo) {
        long base = demandItemId == null ? 900000000000L : 900000000000L + demandItemId * 100 + rankNo;
        return "https://detail.1688.com/offer/" + base + ".html";
    }

    private String resolveMaterial(String targetValue, CandidateProfile profile) {
        if (profile == CandidateProfile.STRONG) {
            return defaultText(targetValue, "陶瓷");
        }
        if (profile == CandidateProfile.MEDIUM) {
            if (containsAny(targetValue, "礼盒", "轻奢")) {
                return "ABS+电镀外壳";
            }
            if (containsAny(targetValue, "陶瓷")) {
                return "釉面陶瓷";
            }
            if (containsAny(targetValue, "abs")) {
                return "ABS";
            }
            return defaultText(targetValue, "陶瓷/树脂待确认");
        }
        if (containsAny(targetValue, "陶瓷")) {
            return "树脂";
        }
        if (containsAny(targetValue, "abs")) {
            return "塑料";
        }
        return "混合材质";
    }

    private String resolvePowerMode(String targetValue, CandidateProfile profile) {
        if (profile == CandidateProfile.STRONG) {
            return defaultText(targetValue, "充电款");
        }
        if (profile == CandidateProfile.MEDIUM) {
            if (containsAny(targetValue, "充电", "usb")) {
                return "USB充电款";
            }
            if (containsAny(targetValue, "插电")) {
                return "插电款";
            }
            if (containsAny(targetValue, "无电")) {
                return "无电";
            }
            return defaultText(targetValue, "充电款");
        }
        if (containsAny(targetValue, "充电", "usb")) {
            return "插电款";
        }
        if (containsAny(targetValue, "插电")) {
            return "蜡烛/炭";
        }
        return "插电款";
    }

    private String resolveSize(String targetValue, CandidateProfile profile) {
        if (profile == CandidateProfile.STRONG) {
            return defaultText(targetValue, "便携小型");
        }
        if (profile == CandidateProfile.MEDIUM) {
            if (containsAny(targetValue, "手持", "便携", "迷你")) {
                return "便携小型";
            }
            if (containsAny(targetValue, "桌面")) {
                return "桌面款";
            }
            return defaultText(targetValue, "桌面款");
        }
        if (containsAny(targetValue, "手持", "便携", "迷你")) {
            return "桌面款";
        }
        if (containsAny(targetValue, "桌面")) {
            return "落地款";
        }
        return "大尺寸";
    }

    private String resolvePackage(String targetValue, CandidateProfile profile) {
        if (profile == CandidateProfile.STRONG) {
            return defaultText(targetValue, "礼盒装");
        }
        if (profile == CandidateProfile.MEDIUM) {
            if (containsAny(targetValue, "礼盒", "轻奢")) {
                return "彩盒装";
            }
            if (containsAny(targetValue, "彩盒")) {
                return "普通盒";
            }
            return defaultText(targetValue, "普通盒");
        }
        if (containsAny(targetValue, "礼盒", "轻奢", "彩盒")) {
            return "OPP袋";
        }
        return "普通袋装";
    }

    private String resolveDelivery(String targetValue, CandidateProfile profile) {
        if (profile == CandidateProfile.STRONG) {
            return defaultText(targetValue, "7天内");
        }
        if (profile == CandidateProfile.MEDIUM) {
            Integer days = extractFirstNumber(targetValue);
            if (days != null) {
                return (days + 2) + "-" + (days + 4) + "天";
            }
            return "10天内";
        }
        Integer days = extractFirstNumber(targetValue);
        if (days != null) {
            return (days + 5) + "-" + (days + 8) + "天";
        }
        return "12-15天";
    }

    private String resolveSupplierName(DemandItemView demandItem, CandidateProfile profile) {
        if (profile == CandidateProfile.STRONG) {
            return containsAny(demandItem.getTargetPowerMode(), "充电", "usb")
                    ? "义乌香器源头工厂"
                    : "德化香器供应链";
        }
        if (profile == CandidateProfile.MEDIUM) {
            return "深圳家居器具商行";
        }
        return "广州散货档口";
    }

    private String resolveLocation(DemandItemView demandItem, CandidateProfile profile) {
        if (profile == CandidateProfile.STRONG) {
            return containsAny(demandItem.getTargetMaterial(), "陶瓷") ? "福建德化" : "浙江义乌";
        }
        if (profile == CandidateProfile.MEDIUM) {
            return "广东深圳";
        }
        return "广东广州";
    }

    private BigDecimal defaultMinPrice(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null) {
            return minPrice;
        }
        if (maxPrice != null) {
            return maxPrice.subtract(BigDecimal.valueOf(6)).max(BigDecimal.valueOf(3));
        }
        return BigDecimal.valueOf(10);
    }

    private BigDecimal defaultMaxPrice(BigDecimal minPrice, BigDecimal maxPrice) {
        if (maxPrice != null) {
            return maxPrice.max(defaultMinPrice(minPrice, maxPrice));
        }
        return defaultMinPrice(minPrice, maxPrice).add(BigDecimal.valueOf(8));
    }

    private int scaleQuantity(Integer targetQuantity, double factor, int fallbackValue) {
        if (targetQuantity == null || targetQuantity <= 0) {
            return fallbackValue;
        }
        int scaled = (int) Math.round(targetQuantity * factor / 10.0) * 10;
        return Math.max(50, scaled);
    }

    private int countSelectedImages(DemandItemView demandItem) {
        int count = 0;
        if (StringUtils.hasText(demandItem.getSourceImageUrl())) {
            count += 1;
        }
        if (StringUtils.hasText(demandItem.getSourceDetailImageUrl())) {
            count += 1;
        }
        if (StringUtils.hasText(demandItem.getSourcePackageImageUrl())) {
            count += 1;
        }
        return count;
    }

    private String formatPriceText(BigDecimal minPrice, BigDecimal maxPrice) {
        return minPrice.setScale(1, RoundingMode.HALF_UP).toPlainString()
                + "-"
                + maxPrice.setScale(1, RoundingMode.HALF_UP).toPlainString()
                + " 元";
    }

    private String formatMoq(int moq) {
        return moq + " 件起";
    }

    private String defaultText(String preferredValue, String fallbackValue) {
        return StringUtils.hasText(preferredValue) ? preferredValue.trim() : fallbackValue;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String normalizeWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean containsAny(String value, String... keywords) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String firstValue, String secondValue) {
        if (StringUtils.hasText(firstValue)) {
            return firstValue.trim();
        }
        if (StringUtils.hasText(secondValue)) {
            return secondValue.trim();
        }
        return null;
    }

    private Integer extractFirstNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9]", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] parts = normalized.split("\\s+");
        if (!StringUtils.hasText(parts[0])) {
            return null;
        }
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static final class AutoSelectionResult {
        private final String taskStatus;
        private final int progressPercent;
        private final String searchMode;
        private final int selectedImageCount;
        private final String searchPath;
        private final int resultCount;
        private final int recommendedCount;
        private final String message;
        private final List<GeneratedCandidate> candidates;

        public AutoSelectionResult(
                String taskStatus,
                int progressPercent,
                String searchMode,
                int selectedImageCount,
                String searchPath,
                int resultCount,
                int recommendedCount,
                String message,
                List<GeneratedCandidate> candidates
        ) {
            this.taskStatus = taskStatus;
            this.progressPercent = progressPercent;
            this.searchMode = searchMode;
            this.selectedImageCount = selectedImageCount;
            this.searchPath = searchPath;
            this.resultCount = resultCount;
            this.recommendedCount = recommendedCount;
            this.message = message;
            this.candidates = candidates;
        }

        public String getTaskStatus() {
            return taskStatus;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        public String getSearchMode() {
            return searchMode;
        }

        public int getSelectedImageCount() {
            return selectedImageCount;
        }

        public String getSearchPath() {
            return searchPath;
        }

        public int getResultCount() {
            return resultCount;
        }

        public int getRecommendedCount() {
            return recommendedCount;
        }

        public String getMessage() {
            return message;
        }

        public List<GeneratedCandidate> getCandidates() {
            return candidates;
        }
    }

    public static final class GeneratedCandidate {
        private int rankNo;
        private String level;
        private int totalScore;
        private int fitScore;
        private int specScore;
        private int priceScore;
        private int supplierScore;
        private int logisticsScore;
        private String candidatePlatform;
        private String candidateUrl;
        private String title;
        private String supplierName;
        private String priceText;
        private String moqText;
        private String locationText;
        private String materialText;
        private String powerModeText;
        private String sizeText;
        private String packageText;
        private String deliveryTimelineText;
        private String resultCardText;
        private String detailHighlightText;
        private String attributeSnapshotText;
        private String shippingSnapshotText;
        private String packageSnapshotText;
        private String mainImageUrl;
        private String detailImageUrl;
        private String deliveryImageUrl;
        private String nextAction;
        private List<String> badges = new ArrayList<>();
        private List<String> reasons = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public int getRankNo() {
            return rankNo;
        }

        public void setRankNo(int rankNo) {
            this.rankNo = rankNo;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public int getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(int totalScore) {
            this.totalScore = totalScore;
        }

        public int getFitScore() {
            return fitScore;
        }

        public void setFitScore(int fitScore) {
            this.fitScore = fitScore;
        }

        public int getSpecScore() {
            return specScore;
        }

        public void setSpecScore(int specScore) {
            this.specScore = specScore;
        }

        public int getPriceScore() {
            return priceScore;
        }

        public void setPriceScore(int priceScore) {
            this.priceScore = priceScore;
        }

        public int getSupplierScore() {
            return supplierScore;
        }

        public void setSupplierScore(int supplierScore) {
            this.supplierScore = supplierScore;
        }

        public int getLogisticsScore() {
            return logisticsScore;
        }

        public void setLogisticsScore(int logisticsScore) {
            this.logisticsScore = logisticsScore;
        }

        public String getCandidatePlatform() {
            return candidatePlatform;
        }

        public void setCandidatePlatform(String candidatePlatform) {
            this.candidatePlatform = candidatePlatform;
        }

        public String getCandidateUrl() {
            return candidateUrl;
        }

        public void setCandidateUrl(String candidateUrl) {
            this.candidateUrl = candidateUrl;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getPriceText() {
            return priceText;
        }

        public void setPriceText(String priceText) {
            this.priceText = priceText;
        }

        public String getMoqText() {
            return moqText;
        }

        public void setMoqText(String moqText) {
            this.moqText = moqText;
        }

        public String getLocationText() {
            return locationText;
        }

        public void setLocationText(String locationText) {
            this.locationText = locationText;
        }

        public String getMaterialText() {
            return materialText;
        }

        public void setMaterialText(String materialText) {
            this.materialText = materialText;
        }

        public String getPowerModeText() {
            return powerModeText;
        }

        public void setPowerModeText(String powerModeText) {
            this.powerModeText = powerModeText;
        }

        public String getSizeText() {
            return sizeText;
        }

        public void setSizeText(String sizeText) {
            this.sizeText = sizeText;
        }

        public String getPackageText() {
            return packageText;
        }

        public void setPackageText(String packageText) {
            this.packageText = packageText;
        }

        public String getDeliveryTimelineText() {
            return deliveryTimelineText;
        }

        public void setDeliveryTimelineText(String deliveryTimelineText) {
            this.deliveryTimelineText = deliveryTimelineText;
        }

        public String getResultCardText() {
            return resultCardText;
        }

        public void setResultCardText(String resultCardText) {
            this.resultCardText = resultCardText;
        }

        public String getDetailHighlightText() {
            return detailHighlightText;
        }

        public void setDetailHighlightText(String detailHighlightText) {
            this.detailHighlightText = detailHighlightText;
        }

        public String getAttributeSnapshotText() {
            return attributeSnapshotText;
        }

        public void setAttributeSnapshotText(String attributeSnapshotText) {
            this.attributeSnapshotText = attributeSnapshotText;
        }

        public String getShippingSnapshotText() {
            return shippingSnapshotText;
        }

        public void setShippingSnapshotText(String shippingSnapshotText) {
            this.shippingSnapshotText = shippingSnapshotText;
        }

        public String getPackageSnapshotText() {
            return packageSnapshotText;
        }

        public void setPackageSnapshotText(String packageSnapshotText) {
            this.packageSnapshotText = packageSnapshotText;
        }

        public String getMainImageUrl() {
            return mainImageUrl;
        }

        public void setMainImageUrl(String mainImageUrl) {
            this.mainImageUrl = mainImageUrl;
        }

        public String getDetailImageUrl() {
            return detailImageUrl;
        }

        public void setDetailImageUrl(String detailImageUrl) {
            this.detailImageUrl = detailImageUrl;
        }

        public String getDeliveryImageUrl() {
            return deliveryImageUrl;
        }

        public void setDeliveryImageUrl(String deliveryImageUrl) {
            this.deliveryImageUrl = deliveryImageUrl;
        }

        public String getNextAction() {
            return nextAction;
        }

        public void setNextAction(String nextAction) {
            this.nextAction = nextAction;
        }

        public List<String> getBadges() {
            return badges;
        }

        public void setBadges(List<String> badges) {
            this.badges = badges;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public void setReasons(List<String> reasons) {
            this.reasons = reasons;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }
    }

    private static final class PriceRange {
        private final BigDecimal minPrice;
        private final BigDecimal maxPrice;

        private PriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
        }

        public BigDecimal getMinPrice() {
            return minPrice;
        }

        public BigDecimal getMaxPrice() {
            return maxPrice;
        }
    }

    private static final class CandidateFields {
        private BigDecimal priceMin;
        private BigDecimal priceMax;
        private int moq;
        private String supplierName;
        private String locationText;
        private String materialText;
        private String powerModeText;
        private String sizeText;
        private String packageText;
        private String deliveryTimelineText;

        public BigDecimal getPriceMin() {
            return priceMin;
        }

        public void setPriceMin(BigDecimal priceMin) {
            this.priceMin = priceMin;
        }

        public BigDecimal getPriceMax() {
            return priceMax;
        }

        public void setPriceMax(BigDecimal priceMax) {
            this.priceMax = priceMax;
        }

        public int getMoq() {
            return moq;
        }

        public void setMoq(int moq) {
            this.moq = moq;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getLocationText() {
            return locationText;
        }

        public void setLocationText(String locationText) {
            this.locationText = locationText;
        }

        public String getMaterialText() {
            return materialText;
        }

        public void setMaterialText(String materialText) {
            this.materialText = materialText;
        }

        public String getPowerModeText() {
            return powerModeText;
        }

        public void setPowerModeText(String powerModeText) {
            this.powerModeText = powerModeText;
        }

        public String getSizeText() {
            return sizeText;
        }

        public void setSizeText(String sizeText) {
            this.sizeText = sizeText;
        }

        public String getPackageText() {
            return packageText;
        }

        public void setPackageText(String packageText) {
            this.packageText = packageText;
        }

        public String getDeliveryTimelineText() {
            return deliveryTimelineText;
        }

        public void setDeliveryTimelineText(String deliveryTimelineText) {
            this.deliveryTimelineText = deliveryTimelineText;
        }
    }

    private static final class CandidateScore {
        private String level;
        private int totalScore;
        private int fitScore;
        private int specScore;
        private int priceScore;
        private int supplierScore;
        private int logisticsScore;
        private final List<String> badges = new ArrayList<>();
        private final List<String> reasons = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public int getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(int totalScore) {
            this.totalScore = totalScore;
        }

        public int getFitScore() {
            return fitScore;
        }

        public void setFitScore(int fitScore) {
            this.fitScore = fitScore;
        }

        public int getSpecScore() {
            return specScore;
        }

        public void setSpecScore(int specScore) {
            this.specScore = specScore;
        }

        public int getPriceScore() {
            return priceScore;
        }

        public void setPriceScore(int priceScore) {
            this.priceScore = priceScore;
        }

        public int getSupplierScore() {
            return supplierScore;
        }

        public void setSupplierScore(int supplierScore) {
            this.supplierScore = supplierScore;
        }

        public int getLogisticsScore() {
            return logisticsScore;
        }

        public void setLogisticsScore(int logisticsScore) {
            this.logisticsScore = logisticsScore;
        }

        public List<String> getBadges() {
            return badges;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    private static final class MatchCounter {
        private int specPoints;
        private int fitPoints;
        private int exactMatchCount;
        private int partialMatchCount;

        public int getSpecPoints() {
            return specPoints;
        }

        public int getFitPoints() {
            return fitPoints;
        }

        public int getExactMatchCount() {
            return exactMatchCount;
        }

        public int getPartialMatchCount() {
            return partialMatchCount;
        }
    }

    private enum CandidateProfile {
        STRONG(" 同款优先工厂版", "PREPARE_INQUIRY"),
        MEDIUM(" 相近备选款", "CONTINUE_COMPARE"),
        WEAK(" 外观相似低配款", "HOLD");

        private final String titleSuffix;
        private final String nextAction;

        CandidateProfile(String titleSuffix, String nextAction) {
            this.titleSuffix = titleSuffix;
            this.nextAction = nextAction;
        }

        public String getTitleSuffix() {
            return titleSuffix;
        }

        public String getNextAction() {
            return nextAction;
        }
    }

    private enum FieldType {
        MATERIAL,
        POWER,
        SIZE,
        PACKAGE,
        DELIVERY
    }

    private enum FieldMatch {
        EXACT,
        PARTIAL,
        MISMATCH,
        UNKNOWN
    }
}
