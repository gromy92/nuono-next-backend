package com.nuono.next.procurement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class AliAiBulkInquiryCreatePageProbeParser {

    private static final int MAX_TEXT_PREVIEW_LENGTH = 500;
    private static final Pattern HTML_ELEMENT_PATTERN = Pattern.compile(
            "(?is)<(button|a|textarea|select)\\b([^>]*)>(.*?)</\\1>|<(input)\\b([^>]*)/?\\s*>"
    );
    private static final Pattern HTML_ATTRIBUTE_PATTERN = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))"
    );

    AliAiBulkInquiryCreatePageProbeView parseSnapshot(
            AliAiBulkInquiryCreatePageSnapshot snapshot,
            String source
    ) {
        AliAiBulkInquiryCreatePageSnapshot safeSnapshot = snapshot == null
                ? new AliAiBulkInquiryCreatePageSnapshot()
                : snapshot;
        if (!safeSnapshot.isOk()) {
            return failureView(safeSnapshot, source);
        }
        return buildView(
                safeSnapshot.getUrl(),
                safeSnapshot.getTitle(),
                safeSnapshot.getText(),
                safeSnapshot.getElements(),
                source
        );
    }

    AliAiBulkInquiryCreatePageProbeView parseSampleHtml(String sampleHtml, String pageUrl) {
        String normalizedHtml = sampleHtml == null ? "" : sampleHtml;
        String text = stripTags(normalizedHtml);
        List<AliAiBulkInquiryElementProbe> elements = extractElements(normalizedHtml);
        return buildView(pageUrl, "sample-html", text, elements, "sample-html");
    }

    private AliAiBulkInquiryCreatePageProbeView failureView(
            AliAiBulkInquiryCreatePageSnapshot snapshot,
            String source
    ) {
        AliAiBulkInquiryCreatePageProbeView view = new AliAiBulkInquiryCreatePageProbeView();
        view.setReady(true);
        view.setReadable(false);
        view.setSource(source);
        view.setPageUrl(snapshot.getUrl());
        view.setPageTitle(snapshot.getTitle());
        view.setFailureCode(snapshot.getFailureCode());
        view.setMessage(firstNonBlank(snapshot.getFailureMessage(), "1688 智能询盘创建页暂不可读。"));
        view.setElementCount(0);
        view.setButtonCount(0);
        view.setInputCount(0);
        view.setTextareaCount(0);
        view.setSubmitCandidateCount(0);
        view.setOfferInputCandidateCount(0);
        view.setMessageInputCandidateCount(0);
        view.setQuantityInputCandidateCount(0);
        return view;
    }

    private AliAiBulkInquiryCreatePageProbeView buildView(
            String pageUrl,
            String pageTitle,
            String text,
            List<AliAiBulkInquiryElementProbe> rawElements,
            String source
    ) {
        List<AliAiBulkInquiryElementProbe> elements = categorizeElements(rawElements);
        int buttonCount = 0;
        int inputCount = 0;
        int textareaCount = 0;
        int submitCandidateCount = 0;
        int offerInputCandidateCount = 0;
        int messageInputCandidateCount = 0;
        int quantityInputCandidateCount = 0;

        for (AliAiBulkInquiryElementProbe element : elements) {
            if (isButtonLike(element)) {
                buttonCount++;
            }
            if ("input".equalsIgnoreCase(element.getTagName())) {
                inputCount++;
            }
            if ("textarea".equalsIgnoreCase(element.getTagName())) {
                textareaCount++;
            }
            if ("SUBMIT_ACTION".equals(element.getCategory())) {
                submitCandidateCount++;
            } else if ("OFFER_INPUT".equals(element.getCategory())) {
                offerInputCandidateCount++;
            } else if ("MESSAGE_INPUT".equals(element.getCategory())) {
                messageInputCandidateCount++;
            } else if ("QUANTITY_INPUT".equals(element.getCategory())) {
                quantityInputCandidateCount++;
            }
        }

        boolean createPageLikely = submitCandidateCount > 0
                && (offerInputCandidateCount > 0 || messageInputCandidateCount > 0);

        AliAiBulkInquiryCreatePageProbeView view = new AliAiBulkInquiryCreatePageProbeView();
        view.setReady(true);
        view.setReadable(true);
        view.setSource(source);
        view.setPageUrl(pageUrl);
        view.setPageTitle(pageTitle);
        view.setLoginRequired(isLoginRequired(text, pageTitle, pageUrl));
        view.setCreatePageLikely(createPageLikely);
        view.setElementCount(elements.size());
        view.setButtonCount(buttonCount);
        view.setInputCount(inputCount);
        view.setTextareaCount(textareaCount);
        view.setSubmitCandidateCount(submitCandidateCount);
        view.setOfferInputCandidateCount(offerInputCandidateCount);
        view.setMessageInputCandidateCount(messageInputCandidateCount);
        view.setQuantityInputCandidateCount(quantityInputCandidateCount);
        view.setTextPreview(limit(compact(text), MAX_TEXT_PREVIEW_LENGTH));
        view.setElements(elements);
        view.setMessage(createPageLikely
                ? "已识别到可能的 1688 智能询盘创建页控件。"
                : "已读取页面，但未完整识别到创建询盘所需控件。");
        return view;
    }

    private List<AliAiBulkInquiryElementProbe> categorizeElements(List<AliAiBulkInquiryElementProbe> rawElements) {
        List<AliAiBulkInquiryElementProbe> elements = rawElements == null ? new ArrayList<>() : rawElements;
        for (AliAiBulkInquiryElementProbe element : elements) {
            String identity = identityOf(element);
            if (containsAny(identity, "发起询价", "立即询价", "提交", "发布询盘", "批量询盘", "开始询价", "确认", "创建询盘", "一键询价")) {
                element.setCategory("SUBMIT_ACTION");
                element.setRiskLevel("HIGH");
            } else if (containsAny(identity, "商品链接", "产品链接", "offer", "宝贝链接", "1688链接", "货品链接", "批量商品", "链接粘贴")) {
                element.setCategory("OFFER_INPUT");
                element.setRiskLevel("LOW");
            } else if (containsAny(identity, "询价内容", "话术", "留言", "备注", "采购需求", "最低价", "发货时间", "报价")) {
                element.setCategory("MESSAGE_INPUT");
                element.setRiskLevel("LOW");
            } else if (containsAny(identity, "数量", "采购数量", "件数", "起订", "moq")) {
                element.setCategory("QUANTITY_INPUT");
                element.setRiskLevel("LOW");
            } else if (isUploadElement(element, identity)) {
                element.setCategory("UPLOAD_INPUT");
                element.setRiskLevel("MEDIUM");
            } else {
                element.setCategory("OTHER_CONTROL");
                element.setRiskLevel("LOW");
            }
        }
        return elements;
    }

    private List<AliAiBulkInquiryElementProbe> extractElements(String html) {
        List<AliAiBulkInquiryElementProbe> elements = new ArrayList<>();
        Matcher matcher = HTML_ELEMENT_PATTERN.matcher(html);
        while (matcher.find() && elements.size() < 160) {
            String tagName = firstNonBlank(matcher.group(1), matcher.group(4));
            String attributes = firstNonBlank(matcher.group(2), matcher.group(5));
            String body = matcher.group(3);
            Map<String, String> attrs = parseAttributes(attributes);
            AliAiBulkInquiryElementProbe element = new AliAiBulkInquiryElementProbe();
            element.setTagName(normalizeLower(tagName));
            element.setType(attrs.get("type"));
            element.setText(limit(stripTags(firstNonBlank(body, attrs.get("value"))), 120));
            element.setPlaceholder(limit(attrs.get("placeholder"), 120));
            element.setName(limit(attrs.get("name"), 80));
            element.setElementId(limit(attrs.get("id"), 80));
            element.setClassName(limit(attrs.get("class"), 120));
            element.setAriaLabel(limit(attrs.get("aria-label"), 120));
            element.setTitle(limit(attrs.get("title"), 120));
            element.setRole(limit(attrs.get("role"), 80));
            element.setVisible(true);
            elements.add(element);
        }
        return elements;
    }

    private Map<String, String> parseAttributes(String rawAttributes) {
        Map<String, String> attributes = new HashMap<>();
        if (!StringUtils.hasText(rawAttributes)) {
            return attributes;
        }
        Matcher matcher = HTML_ATTRIBUTE_PATTERN.matcher(rawAttributes);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = firstNonBlank(matcher.group(3), matcher.group(4), matcher.group(5));
            attributes.put(key, compact(value));
        }
        return attributes;
    }

    private boolean isButtonLike(AliAiBulkInquiryElementProbe element) {
        String tagName = normalizeLower(element.getTagName());
        String type = normalizeLower(element.getType());
        String role = normalizeLower(element.getRole());
        return "button".equals(tagName)
                || "button".equals(role)
                || ("input".equals(tagName) && ("button".equals(type) || "submit".equals(type)));
    }

    private boolean isUploadElement(AliAiBulkInquiryElementProbe element, String identity) {
        return ("input".equalsIgnoreCase(element.getTagName()) && "file".equalsIgnoreCase(element.getType()))
                || containsAny(identity, "上传", "导入");
    }

    private boolean isLoginRequired(String text, String title, String url) {
        String identity = compact(firstNonBlank(text, "") + " " + firstNonBlank(title, "") + " " + firstNonBlank(url, ""))
                .toLowerCase(Locale.ROOT);
        return containsAny(identity, "登录", "请登录", "login", "signin", "sign in");
    }

    private String identityOf(AliAiBulkInquiryElementProbe element) {
        if (element == null) {
            return "";
        }
        return compact(firstNonBlank(element.getTagName(), "") + " "
                + firstNonBlank(element.getType(), "") + " "
                + firstNonBlank(element.getText(), "") + " "
                + firstNonBlank(element.getPlaceholder(), "") + " "
                + firstNonBlank(element.getName(), "") + " "
                + firstNonBlank(element.getElementId(), "") + " "
                + firstNonBlank(element.getClassName(), "") + " "
                + firstNonBlank(element.getAriaLabel(), "") + " "
                + firstNonBlank(element.getTitle(), "") + " "
                + firstNonBlank(element.getRole(), "")).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String stripTags(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }
        return compact(html.replaceAll("(?is)<script\\b.*?</script>", " ")
                .replaceAll("(?is)<style\\b.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " "));
    }

    private String normalizeLower(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
