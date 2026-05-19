package com.nuono.next.procurement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class AliAiBulkInquiryResultParser {

    private static final Pattern EXPLICIT_COUNT_PATTERN = Pattern.compile("(\\d+)\\s*(?:个|家|条)?\\s*([^\\d\\s]{0,8})");
    private static final Pattern URL_INQUIRY_ID_PATTERN = Pattern.compile("(?:inquiryId|inquiry_id|taskId|task_id|id)=([A-Za-z0-9_-]+)");

    AliAiBulkInquiryResultView parse(String rawText, String resultUrl, String pageTitle, String externalInquiryId, String source) {
        AliAiBulkInquiryResultView view = new AliAiBulkInquiryResultView();
        view.setReady(true);
        view.setSource(source);
        view.setResultUrl(normalize(resultUrl));
        view.setPageTitle(normalize(pageTitle));
        view.setExternalInquiryId(firstNonBlank(externalInquiryId, extractInquiryId(resultUrl)));
        view.setReplySource("ALI_AI_RESULT");

        String normalizedText = compact(rawText);
        view.setRawTextDigest(sha256(normalizedText));
        view.setTextPreview(limit(normalizedText, 500));

        if (!StringUtils.hasText(normalizedText)) {
            view.setReadable(false);
            view.setExternalResultStatus("FAILED");
            view.setReplyParseStatus("NOT_AVAILABLE");
            view.setReplyParseError("1688 智能询盘结果页没有可读取文本。");
            view.setMessage("没有读取到 1688 智能询盘结果文本。");
            return view;
        }

        String lower = normalizedText.toLowerCase(Locale.ROOT);
        boolean loginRequired = containsAny(lower, "login", "登录", "请登录", "淘宝账号登录");
        view.setLoginRequired(loginRequired);
        if (loginRequired) {
            view.setReadable(false);
            view.setExternalResultStatus("FAILED");
            view.setReplyParseStatus("NOT_AVAILABLE");
            view.setReplyParseError("1688 页面要求登录，当前无法只读回读询盘结果。");
            view.setMessage("1688 智能询盘结果页要求登录。");
            return view;
        }

        int priceMentions = countMentions(normalizedText, "报价", "价格", "最低价", "单价", "¥", "￥", "元");
        int moqMentions = countMentions(normalizedText, "起订", "起订量", "moq", "MOQ", "最小起订", "件起");
        int deliveryMentions = countMentions(normalizedText, "发货", "交期", "货期", "几天", "天内", "现货");
        int repliedCount = resolveCount(normalizedText, "已回复");
        int noReplyCount = resolveCount(normalizedText, "未回复", "无回复", "暂无回复");
        int supplierCount = Math.max(repliedCount + noReplyCount, resolveCount(normalizedText, "供应商", "商家", "厂家"));

        view.setReadable(true);
        view.setPriceMentionCount(priceMentions);
        view.setMoqMentionCount(moqMentions);
        view.setDeliveryMentionCount(deliveryMentions);
        view.setRepliedCount(repliedCount);
        view.setNoReplyCount(noReplyCount);
        view.setSupplierCount(supplierCount);
        view.setExternalResultStatus(resolveExternalStatus(normalizedText, repliedCount, priceMentions, deliveryMentions));
        view.setReplyParseStatus(resolveParseStatus(view.getExternalResultStatus(), priceMentions, moqMentions, deliveryMentions));
        if ("FAILED".equals(view.getReplyParseStatus())) {
            view.setReplyParseError("页面可读取，但未识别到报价、MOQ 或交期等可结构化字段。");
        }
        view.setMessage("已完成 1688 智能询盘结果只读解析。");
        return view;
    }

    private String resolveExternalStatus(String text, int repliedCount, int priceMentions, int deliveryMentions) {
        if (containsAny(text, "失败", "异常", "错误", "已取消")) {
            return "FAILED";
        }
        if (containsAny(text, "暂无回复", "无回复", "未回复") && repliedCount <= 0 && priceMentions <= 0) {
            return "NO_REPLY";
        }
        if (repliedCount > 0 || priceMentions > 0 || deliveryMentions > 0 || containsAny(text, "已回复", "商家回复")) {
            return "REPLIED";
        }
        if (containsAny(text, "进行中", "询价中", "等待", "处理中")) {
            return "RUNNING";
        }
        return "CREATED";
    }

    private String resolveParseStatus(String externalStatus, int priceMentions, int moqMentions, int deliveryMentions) {
        if ("REPLIED".equals(externalStatus)) {
            if (priceMentions > 0 && moqMentions > 0 && deliveryMentions > 0) {
                return "SUCCESS";
            }
            if (priceMentions > 0 || moqMentions > 0 || deliveryMentions > 0) {
                return "PARTIAL";
            }
            return "FAILED";
        }
        if ("RUNNING".equals(externalStatus) || "CREATED".equals(externalStatus)) {
            return "PENDING";
        }
        return "NOT_AVAILABLE";
    }

    private int resolveCount(String text, String... labels) {
        int best = 0;
        if (!StringUtils.hasText(text) || labels == null) {
            return best;
        }
        for (String label : labels) {
            if (!StringUtils.hasText(label)) {
                continue;
            }
            Matcher beforeMatcher = Pattern.compile(Pattern.quote(label) + "\\s*(\\d+)\\s*(?:个|家|条)?").matcher(text);
            while (beforeMatcher.find()) {
                best = Math.max(best, parseInt(beforeMatcher.group(1)));
            }
        }
        Matcher matcher = EXPLICIT_COUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            int count = parseInt(matcher.group(1));
            String nearText = matcher.group(2);
            if (containsAny(nearText, labels)) {
                best = Math.max(best, count);
            }
        }
        return best;
    }

    private int countMentions(String text, String... tokens) {
        int count = 0;
        for (String token : tokens) {
            int index = 0;
            while (StringUtils.hasText(token) && index >= 0 && index < text.length()) {
                index = text.indexOf(token, index);
                if (index >= 0) {
                    count++;
                    index += token.length();
                }
            }
        }
        return count;
    }

    private String extractInquiryId(String resultUrl) {
        if (!StringUtils.hasText(resultUrl)) {
            return null;
        }
        Matcher matcher = URL_INQUIRY_ID_PATTERN.matcher(resultUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean containsAny(String text, String... tokens) {
        if (!StringUtils.hasText(text) || tokens == null) {
            return false;
        }
        for (String token : tokens) {
            if (StringUtils.hasText(token) && text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private int parseInt(String rawValue) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String compact(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String sha256(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte part : hash) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256。", exception);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
