package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProcurementInquiryPreparationAdvisor {

    public void enrichCandidateInquiryPreparation(DemandItemView demandItem, CandidateView candidate) {
        if (demandItem == null || candidate == null) {
            return;
        }

        candidate.setInquiryOpeningLine(buildOpeningLine(demandItem, candidate));
        candidate.setInquirySummaryLine(buildSummaryLine(demandItem, candidate));
        candidate.setInquiryQuestions(buildInquiryQuestions(demandItem, candidate));
        candidate.setQuoteChecklist(buildQuoteChecklist(demandItem, candidate));
        candidate.setSampleChecklist(buildSampleChecklist(demandItem, candidate));
    }

    private String buildOpeningLine(DemandItemView demandItem, CandidateView candidate) {
        String title = defaultText(demandItem.getSourceTitle(), candidate.getTitle(), "这款商品");
        return String.format("您好，我们在看 %s，想确认是否可以按当前要求打样和报价。", title);
    }

    private String buildSummaryLine(DemandItemView demandItem, CandidateView candidate) {
        List<String> parts = new ArrayList<>();
        parts.add("目标站点 " + defaultText(demandItem.getTargetSite(), "待定"));
        parts.add("目标价 " + formatPriceRange(demandItem.getTargetPriceMin(), demandItem.getTargetPriceMax()));
        parts.add("目标量 " + defaultText(quantityText(demandItem.getTargetQuantity()), "待定"));
        parts.add("候选报价 " + defaultText(candidate.getStandardizedPriceText(), candidate.getPriceText(), "待确认"));
        parts.add("起订量 " + defaultText(candidate.getStandardizedMoqText(), candidate.getMoqText(), "待确认"));
        parts.add("交期 " + defaultText(candidate.getStandardizedDeliveryText(), candidate.getDeliveryTimelineText(), "待确认"));
        return String.join("；", parts);
    }

    private List<String> buildInquiryQuestions(DemandItemView demandItem, CandidateView candidate) {
        LinkedHashSet<String> questions = new LinkedHashSet<>();
        if (candidate.getPendingQuestions() != null) {
            for (String item : candidate.getPendingQuestions()) {
                if (StringUtils.hasText(item)) {
                    questions.add(item.trim());
                }
                if (questions.size() >= 6) {
                    break;
                }
            }
        }
        questions.add("确认是否支持稳定供货，以及常规补单周期。");
        if (StringUtils.hasText(candidate.getSupplierName())) {
            questions.add("确认是否为工厂直供、是否支持打样和定制。");
        }
        return limit(questions, 6);
    }

    private List<String> buildQuoteChecklist(DemandItemView demandItem, CandidateView candidate) {
        LinkedHashSet<String> checklist = new LinkedHashSet<>();
        checklist.add("请按当前起订量和更高一档数量分别报价，方便比较阶梯价。");
        checklist.add("报价请拆开说明：裸品、包装、配件、说明书、打样费。");
        checklist.add("请同步说明是否含税、运费口径和打样可退规则。");
        if (StringUtils.hasText(demandItem.getTargetPackageType())) {
            checklist.add(String.format("请单独列出 %s 的增量成本。", demandItem.getTargetPackageType()));
        }
        if (containsAny(candidate.getStandardizedPowerModeText(), "充电")) {
            checklist.add("请补充电池容量、续航时间、充电接口和充电线是否包含。");
        }
        if (containsAny(candidate.getStandardizedPowerModeText(), "插电")) {
            checklist.add("请说明插头规格、电压和是否支持沙特站销售。");
        }
        if (StringUtils.hasText(demandItem.getDeliveryExpectation())) {
            checklist.add("请同时给出打样交期和大货交期。");
        }
        return limit(checklist, 6);
    }

    private List<String> buildSampleChecklist(DemandItemView demandItem, CandidateView candidate) {
        LinkedHashSet<String> checklist = new LinkedHashSet<>();
        checklist.add("样品先看外观比例、开孔、按键位置和做工。");
        checklist.add("对照原商品核验颜色、材质质感和整体体量。");
        if (containsAny(candidate.getStandardizedMaterialText(), "陶瓷")) {
            checklist.add("重点看陶瓷胆是否可拆洗、厚度是否稳定、耐高温表现如何。");
        }
        if (containsAny(candidate.getStandardizedPowerModeText(), "充电")) {
            checklist.add("实测充电口、电池续航、加热稳定性和安全性。");
        }
        if (containsAny(candidate.getStandardizedPowerModeText(), "插电")) {
            checklist.add("实测插头、电压、连续通电稳定性和温控表现。");
        }
        if (StringUtils.hasText(demandItem.getTargetPackageType()) || StringUtils.hasText(candidate.getStandardizedPackageText())) {
            checklist.add("检查礼盒/彩盒尺寸、印刷、说明书和包装清单是否完整。");
        }
        return limit(checklist, 5);
    }

    private List<String> limit(LinkedHashSet<String> source, int maxSize) {
        List<String> result = new ArrayList<>();
        for (String item : source) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            result.add(item.trim());
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    private String formatPriceRange(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return "待定";
        }
        if (min != null && max != null) {
            return strip(min) + " - " + strip(max) + " 元";
        }
        return strip(min != null ? min : max) + " 元";
    }

    private String strip(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String quantityText(Integer quantity) {
        return quantity == null ? "" : quantity + " 件";
    }

    private boolean containsAny(String value, String keyword) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String defaultText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
