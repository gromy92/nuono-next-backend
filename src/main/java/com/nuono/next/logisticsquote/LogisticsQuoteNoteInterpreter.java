package com.nuono.next.logisticsquote;

import com.nuono.next.logisticsquote.LogisticsQuoteNotePreviewView.RestrictionPreviewView;
import com.nuono.next.logisticsquote.LogisticsQuoteNotePreviewView.RulePreviewView;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogisticsQuoteNoteInterpreter {

    private static final Pattern CBM_SURCHARGE_PATTERN = Pattern.compile(
            "(单品[^\\n，。；]*?)(?:加|附加|另加)\\s*(\\d+(?:\\.\\d+)?)\\s*/\\s*(?:方|CBM)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern KG_SURCHARGE_PATTERN = Pattern.compile(
            "(?:加|附加|另加)\\s*(\\d+(?:\\.\\d+)?)\\s*/\\s*(?:公斤|kg)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MIN_CHARGE_PATTERN = Pattern.compile(
            "最低(?:消费|收费)\\s*(\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MAX_BOX_WEIGHT_PATTERN = Pattern.compile(
            "单箱(?:重量)?[^\\d]{0,6}(\\d+(?:\\.\\d+)?)\\s*(?:公斤|KG|kg)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MAX_SIDE_LENGTH_PATTERN = Pattern.compile(
            "单边[^\\d]{0,6}(\\d+(?:\\.\\d+)?)\\s*(?:CM|cm|厘米).*单询",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MIN_CBM_FOR_DECLARATION_PATTERN = Pattern.compile(
            "低于\\s*(\\d+(?:\\.\\d+)?)\\s*(?:个)?方.*不接报关",
            Pattern.CASE_INSENSITIVE
    );

    public LogisticsQuoteNotePreviewView interpret(String rawNote) {
        LogisticsQuoteNotePreviewView view = new LogisticsQuoteNotePreviewView();
        String note = normalize(rawNote);
        if (!StringUtils.hasText(note)) {
            throw new IllegalArgumentException("请先输入一段补充文案，再执行规则预览。");
        }

        view.setReady(true);
        view.setNormalizedNote(note);

        extractCbmSurcharge(note, view);
        extractKgSurcharge(note, view);
        extractMinCharge(note, view);
        extractMaxBoxWeight(note, view);
        extractMaxSideLength(note, view);
        extractMinCbmForDeclaration(note, view);

        if (view.getRulePreviews().isEmpty() && view.getRestrictionPreviews().isEmpty()) {
            view.setMessage("当前文案里还没有识别到稳定的结构化规则，先保留为来源备注更稳。");
            view.getWarnings().add("建议补充计费单位、金额或限制阈值，方便后续直接转正式规则。");
            return view;
        }

        view.setMessage("已根据补充文案生成结构化规则预览，确认后可进入正式报价版本。");
        return view;
    }

    private void extractCbmSurcharge(String note, LogisticsQuoteNotePreviewView view) {
        Matcher matcher = CBM_SURCHARGE_PATTERN.matcher(note);
        while (matcher.find()) {
            RulePreviewView rule = new RulePreviewView();
            rule.setRuleName("补充文案附加费");
            rule.setRuleType("MANUAL_NOTE_DERIVED");
            rule.setBillingUnit("CBM");
            rule.setUnitPrice(parseDouble(matcher.group(2)));
            rule.setTriggerCondition(normalize(matcher.group(1)));
            rule.setSummary(String.format(Locale.ROOT, "%s 时，每方额外加 %.2f。", normalize(matcher.group(1)), rule.getUnitPrice()));
            view.getRulePreviews().add(rule);
        }
    }

    private void extractKgSurcharge(String note, LogisticsQuoteNotePreviewView view) {
        Matcher matcher = KG_SURCHARGE_PATTERN.matcher(note);
        while (matcher.find()) {
            RulePreviewView rule = new RulePreviewView();
            rule.setRuleName("补充文案附加费");
            rule.setRuleType("MANUAL_NOTE_DERIVED");
            rule.setBillingUnit("KG");
            rule.setUnitPrice(parseDouble(matcher.group(1)));
            rule.setTriggerCondition("补充文案未明确额外触发条件");
            rule.setSummary(String.format(Locale.ROOT, "按公斤额外加 %.2f。", rule.getUnitPrice()));
            view.getRulePreviews().add(rule);
        }
    }

    private void extractMinCharge(String note, LogisticsQuoteNotePreviewView view) {
        Matcher matcher = MIN_CHARGE_PATTERN.matcher(note);
        while (matcher.find()) {
            RulePreviewView rule = new RulePreviewView();
            rule.setRuleName("最低收费");
            rule.setRuleType("MIN_CHARGE");
            rule.setBillingUnit("FIXED");
            rule.setUnitPrice(parseDouble(matcher.group(1)));
            rule.setTriggerCondition("单票最低收费");
            rule.setSummary(String.format(Locale.ROOT, "最低收费 %.2f。", rule.getUnitPrice()));
            view.getRulePreviews().add(rule);
        }
    }

    private void extractMaxBoxWeight(String note, LogisticsQuoteNotePreviewView view) {
        Matcher matcher = MAX_BOX_WEIGHT_PATTERN.matcher(note);
        while (matcher.find()) {
            RestrictionPreviewView restriction = new RestrictionPreviewView();
            restriction.setRestrictionType("MAX_BOX_WEIGHT");
            restriction.setOperator("<=");
            restriction.setValue(matcher.group(1));
            restriction.setUnit("KG");
            restriction.setSeverity("HARD");
            restriction.setDescription(String.format(Locale.ROOT, "单箱重量不应超过 %s KG。", matcher.group(1)));
            view.getRestrictionPreviews().add(restriction);
        }
    }

    private void extractMaxSideLength(String note, LogisticsQuoteNotePreviewView view) {
        Matcher matcher = MAX_SIDE_LENGTH_PATTERN.matcher(note);
        while (matcher.find()) {
            RestrictionPreviewView restriction = new RestrictionPreviewView();
            restriction.setRestrictionType("INQUIRY_REQUIRED");
            restriction.setOperator("TEXT");
            restriction.setValue(matcher.group(1));
            restriction.setUnit("CM");
            restriction.setSeverity("SOFT");
            restriction.setDescription(String.format(Locale.ROOT, "单边超过 %s CM 时需单询。", matcher.group(1)));
            view.getRestrictionPreviews().add(restriction);
        }
    }

    private void extractMinCbmForDeclaration(String note, LogisticsQuoteNotePreviewView view) {
        Matcher matcher = MIN_CBM_FOR_DECLARATION_PATTERN.matcher(note);
        while (matcher.find()) {
            RestrictionPreviewView restriction = new RestrictionPreviewView();
            restriction.setRestrictionType("CUSTOMS_DECLARATION_LIMIT");
            restriction.setOperator(">=");
            restriction.setValue(matcher.group(1));
            restriction.setUnit("CBM");
            restriction.setSeverity("HARD");
            restriction.setDescription(String.format(Locale.ROOT, "低于 %s 方时不接报关件。", matcher.group(1)));
            view.getRestrictionPreviews().add(restriction);
        }
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.replace('\u3000', ' ')
                .replaceAll("[\\t\\r\\n]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private Double parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Double.parseDouble(value);
    }
}
