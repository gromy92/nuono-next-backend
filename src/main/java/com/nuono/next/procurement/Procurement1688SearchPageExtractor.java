package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class Procurement1688SearchPageExtractor {

    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?:[¥￥]\\s*\\d+(?:\\.\\d+)?(?:\\s*[-~至]\\s*\\d+(?:\\.\\d+)?)?)|(?:\\d+(?:\\.\\d+)?(?:\\s*[-~至]\\s*\\d+(?:\\.\\d+)?)?\\s*元)"
    );
    private static final Pattern MOQ_PATTERN = Pattern.compile("\\d+\\s*(?:件|个|套|只|箱|台|包|PCS|pcs)\\s*起?");
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?:广东|浙江|江苏|山东|福建|河北|河南|湖北|湖南|江西|安徽|四川|上海|北京|天津|重庆|广西|深圳|广州|东莞|佛山|中山|汕头|义乌|金华|台州|温州|宁波|绍兴|嘉兴|湖州|苏州|无锡|南通|青岛|临沂|泉州|厦门|福州|成都|武汉|郑州|杭州)[\\p{IsHan}A-Za-z0-9]{0,6}"
    );
    private static final Pattern IMAGE_ATTRIBUTE_PATTERN = Pattern.compile("^(?:data-)?(?:src|lazy-src|data-lazy-src)$", Pattern.CASE_INSENSITIVE);

    private static final List<String> ATTRIBUTE_KEYWORDS = List.of(
            "材质", "abs", "陶瓷", "塑料", "树脂", "金属",
            "供电", "充电", "usb", "插电", "电池",
            "尺寸", "便携", "迷你", "手持", "桌面",
            "包装", "礼盒", "彩盒", "牛皮盒", "袋装", "箱装"
    );
    private static final List<String> SHIPPING_KEYWORDS = List.of(
            "发货", "交期", "小时", "天", "现货", "打样", "排产", "补货", "包邮"
    );
    private static final List<String> PACKAGE_KEYWORDS = List.of(
            "包装", "礼盒", "礼品", "彩盒", "牛皮盒", "袋装", "箱装", "配件", "清单"
    );
    private static final List<String> BADGE_KEYWORDS = List.of(
            "实力商家", "深度验厂", "超级工厂", "诚信通", "48小时发货", "72小时发货", "包邮"
    );
    private static final List<String> SUPPLIER_HINTS = List.of(
            "公司", "工厂", "厂", "商行", "供应链", "制造", "贸易", "旗舰店", "专营店", "店"
    );

    private final ProcurementStructuredFieldParser procurementStructuredFieldParser;

    public Procurement1688SearchPageExtractor(ProcurementStructuredFieldParser procurementStructuredFieldParser) {
        this.procurementStructuredFieldParser = procurementStructuredFieldParser;
    }

    public ProcurementSearchPagePreviewView preview(String html, Integer maxCandidates) {
        if (!StringUtils.hasText(html)) {
            throw new IllegalArgumentException("请先粘贴 1688 搜索结果页 HTML，再执行页面抽取。");
        }

        int effectiveMaxCandidates = maxCandidates == null ? 12 : Math.max(1, Math.min(maxCandidates, 30));
        Document document = Jsoup.parse(html, "https://s.1688.com/");
        ProcurementSearchPagePreviewView view = new ProcurementSearchPagePreviewView();
        view.setReady(true);
        view.setPageTitle(compactText(document.title()));

        Elements offerAnchors = document.select("a[href]");
        List<Element> matchedOfferAnchors = offerAnchors.stream()
                .filter(this::isOfferAnchor)
                .collect(Collectors.toList());
        view.setDetectedOfferCount(matchedOfferAnchors.size());

        Map<String, CandidateView> extractedCandidates = new LinkedHashMap<>();
        for (Element anchor : matchedOfferAnchors) {
            CandidateView candidate = buildCandidate(anchor);
            if (candidate == null) {
                continue;
            }

            String key = StringUtils.hasText(candidate.getCandidateUrl())
                    ? candidate.getCandidateUrl()
                    : "candidate-" + extractedCandidates.size();
            extractedCandidates.putIfAbsent(key, candidate);

            if (extractedCandidates.size() >= effectiveMaxCandidates) {
                break;
            }
        }

        view.setCandidates(new ArrayList<>(extractedCandidates.values()));
        view.setExtractedCount(view.getCandidates().size());
        view.setWarnings(buildWarnings(document, matchedOfferAnchors.size(), view.getCandidates().size()));

        if (view.getCandidates().isEmpty()) {
            view.setMessage("没有从当前 HTML 里识别出稳定的 1688 候选卡。建议粘贴“搜索结果页完整源码”而不是片段。");
        } else {
            view.setMessage("已完成 1688 搜索页抽取预览，可以继续用这些结果验证候选筛选和字段规则。");
        }
        return view;
    }

    private CandidateView buildCandidate(Element anchor) {
        Element container = resolveOfferContainer(anchor);
        if (container == null) {
            return null;
        }

        List<String> containerSegments = collectContainerSegments(container);
        String containerText = String.join("；", containerSegments);
        if (!StringUtils.hasText(containerText) || containerText.length() < 24) {
            return null;
        }

        CandidateView candidate = new CandidateView();
        candidate.setCandidatePlatform("1688");
        candidate.setCandidateUrl(resolveUrl(anchor));
        candidate.setTitle(resolveTitle(anchor, containerSegments));
        candidate.setSupplierName(resolveSupplierName(containerSegments, candidate.getTitle()));
        candidate.setPriceText(resolvePrice(containerSegments));
        candidate.setMoqText(resolvePattern(containerText, MOQ_PATTERN));
        candidate.setLocationText(resolveLocation(containerSegments));
        candidate.setMainImageUrl(resolveImageUrl(container));
        candidate.setBadgesText(joinKeywordHits(containerText, BADGE_KEYWORDS, 4));
        candidate.setReasonsText(buildReasonsText(candidate, containerText));
        candidate.setWarningsText(buildWarningsText(containerText));
        candidate.setResultCardText(buildResultCardText(candidate, containerText));
        candidate.setDetailHighlightText(buildKeywordSnapshot("详情卖点", containerText, ATTRIBUTE_KEYWORDS, 5));
        candidate.setAttributeSnapshotText(buildKeywordSnapshot("属性快照", containerText, ATTRIBUTE_KEYWORDS, 6));
        candidate.setShippingSnapshotText(buildKeywordSnapshot("物流说明", containerText, SHIPPING_KEYWORDS, 4));
        candidate.setPackageSnapshotText(buildKeywordSnapshot("包装说明", containerText, PACKAGE_KEYWORDS, 4));

        if (!hasMinimumCandidateShape(candidate)) {
            return null;
        }

        procurementStructuredFieldParser.enrichCandidate(candidate);
        return candidate;
    }

    private boolean hasMinimumCandidateShape(CandidateView candidate) {
        return StringUtils.hasText(candidate.getTitle())
                || StringUtils.hasText(candidate.getPriceText())
                || StringUtils.hasText(candidate.getSupplierName());
    }

    private boolean isOfferAnchor(Element anchor) {
        String href = resolveUrl(anchor);
        if (!StringUtils.hasText(href)) {
            return false;
        }
        return href.contains("detail.1688.com/offer/")
                || href.contains("/offer/")
                || href.contains("offer_search")
                || href.contains("offerId=");
    }

    private Element resolveOfferContainer(Element anchor) {
        Element current = anchor;
        int depth = 0;
        while (current != null && depth < 7) {
            String text = compactText(current.text());
            int nestedOfferLinks = (int) current.select("a[href]").stream().filter(this::isOfferAnchor).count();
            if (text.length() >= 24 && text.length() <= 600 && nestedOfferLinks <= 1) {
                return current;
            }
            if (text.length() > 1600 || nestedOfferLinks > 6) {
                break;
            }
            current = current.parent();
            depth += 1;
        }
        return anchor.parent();
    }

    private String resolveUrl(Element anchor) {
        String rawHref = compactText(anchor.absUrl("href"));
        if (StringUtils.hasText(rawHref)) {
            return normalizeUrl(rawHref);
        }
        rawHref = compactText(anchor.attr("href"));
        return normalizeUrl(rawHref);
    }

    private String normalizeUrl(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if (value.startsWith("//")) {
            value = "https:" + value;
        } else if (value.startsWith("/")) {
            value = "https://s.1688.com" + value;
        }
        try {
            URI uri = new URI(value);
            if (!StringUtils.hasText(uri.getScheme())) {
                return value;
            }
            return new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            ).toString();
        } catch (URISyntaxException exception) {
            return value;
        }
    }

    private String resolveTitle(Element anchor, List<String> containerSegments) {
        List<String> candidates = new ArrayList<>();
        candidates.add(compactText(anchor.attr("title")));
        candidates.add(compactText(anchor.text()));
        candidates.addAll(containerSegments);

        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            if (candidate.length() < 6) {
                continue;
            }
            if (PRICE_PATTERN.matcher(candidate).find() || MOQ_PATTERN.matcher(candidate).find()) {
                continue;
            }
            if (SUPPLIER_HINTS.stream().anyMatch(candidate::contains)) {
                continue;
            }
            return limitText(candidate);
        }
        return null;
    }

    private String resolveSupplierName(List<String> containerSegments, String title) {
        return containerSegments.stream()
                .map(this::compactText)
                .filter(StringUtils::hasText)
                .filter(value -> !value.equals(title))
                .filter(value -> SUPPLIER_HINTS.stream().anyMatch(value::contains))
                .findFirst()
                .orElse(null);
    }

    private String resolvePrice(List<String> containerSegments) {
        for (String segment : containerSegments) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            Matcher matcher = PRICE_PATTERN.matcher(segment);
            if (matcher.find()) {
                return compactText(matcher.group());
            }
        }
        return null;
    }

    private String resolveLocation(List<String> containerSegments) {
        for (String segment : containerSegments) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            if (SUPPLIER_HINTS.stream().anyMatch(segment::contains)) {
                continue;
            }
            Matcher matcher = LOCATION_PATTERN.matcher(segment);
            if (matcher.find()) {
                return compactText(matcher.group());
            }
        }
        return null;
    }

    private String resolveImageUrl(Element container) {
        for (Element image : container.select("img")) {
            for (org.jsoup.nodes.Attribute attribute : image.attributes()) {
                if (!IMAGE_ATTRIBUTE_PATTERN.matcher(attribute.getKey()).matches()) {
                    continue;
                }
                String value = compactText(attribute.getValue());
                if (StringUtils.hasText(value) && (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//"))) {
                    return normalizeUrl(value);
                }
            }
        }
        return null;
    }

    private String resolvePattern(String text, Pattern pattern) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return compactText(matcher.group());
        }
        return null;
    }

    private String buildResultCardText(CandidateView candidate, String containerText) {
        LinkedHashSet<String> segments = new LinkedHashSet<>();
        segments.add("结果卡片");
        segments.add(limitText(candidate.getTitle()));
        segments.add(limitText(candidate.getSupplierName()));
        segments.add(limitText(candidate.getPriceText()));
        segments.add(limitText(candidate.getMoqText()));
        segments.add(limitText(candidate.getLocationText()));
        splitSegments(containerText).stream().limit(4).forEach(segments::add);
        return segments.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("；"));
    }

    private String buildReasonsText(CandidateView candidate, String containerText) {
        LinkedHashSet<String> segments = new LinkedHashSet<>();
        if (StringUtils.hasText(candidate.getPriceText())) {
            segments.add("价格信息已识别");
        }
        if (StringUtils.hasText(candidate.getSupplierName())) {
            segments.add("供应商主体已识别");
        }
        if (StringUtils.hasText(candidate.getLocationText())) {
            segments.add("发货地已识别");
        }
        segments.addAll(keywordSegments(containerText, ATTRIBUTE_KEYWORDS, 3));
        return segments.stream().filter(StringUtils::hasText).collect(Collectors.joining("|"));
    }

    private String buildWarningsText(String containerText) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        if (!StringUtils.hasText(resolvePattern(containerText, PRICE_PATTERN))) {
            warnings.add("价格仍需二次确认");
        }
        if (!StringUtils.hasText(resolvePattern(containerText, MOQ_PATTERN))) {
            warnings.add("起订量仍需二次确认");
        }
        if (keywordSegments(containerText, PACKAGE_KEYWORDS, 1).isEmpty()) {
            warnings.add("包装线索偏弱");
        }
        if (keywordSegments(containerText, SHIPPING_KEYWORDS, 1).isEmpty()) {
            warnings.add("物流交期线索偏弱");
        }
        return warnings.stream().collect(Collectors.joining("|"));
    }

    private String buildKeywordSnapshot(String label, String text, List<String> keywords, int maxSegments) {
        List<String> segments = keywordSegments(text, keywords, maxSegments);
        if (segments.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(label);
        parts.addAll(segments);
        return String.join("；", parts);
    }

    private List<String> keywordSegments(String text, List<String> keywords, int maxSegments) {
        return splitSegments(text).stream()
                .map(this::compactText)
                .filter(StringUtils::hasText)
                .filter(segment -> containsAny(segment.toLowerCase(Locale.ROOT), keywords))
                .map(this::limitText)
                .distinct()
                .limit(maxSegments)
                .collect(Collectors.toList());
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String joinKeywordHits(String text, List<String> keywords, int maxSegments) {
        return splitSegments(text).stream()
                .map(this::compactText)
                .filter(StringUtils::hasText)
                .filter(segment -> containsAny(segment.toLowerCase(Locale.ROOT), keywords))
                .distinct()
                .limit(maxSegments)
                .collect(Collectors.joining("|"));
    }

    private List<String> buildWarnings(Document document, int detectedOfferCount, int extractedCount) {
        List<String> warnings = new ArrayList<>();
        if (!StringUtils.hasText(document.title())) {
            warnings.add("当前 HTML 没有标题，可能不是完整页面源码。");
        }
        if (detectedOfferCount <= 0) {
            warnings.add("没有识别到 offer 链接，建议确认粘贴的是 1688 搜索结果页源码。");
        } else if (extractedCount < detectedOfferCount / 2) {
            warnings.add("已识别到部分 offer 链接，但不少候选卡结构不稳定，后续需要继续补规则。");
        }
        return warnings;
    }

    private List<String> splitSegments(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }
        String[] pieces = rawText
                .replace('\u00A0', ' ')
                .split("[\\n\\r\\t|；;•]+");
        List<String> segments = new ArrayList<>();
        for (String piece : pieces) {
            String normalized = compactText(piece);
            if (StringUtils.hasText(normalized) && normalized.length() > 1) {
                segments.add(normalized);
            }
        }
        return segments;
    }

    private List<String> collectContainerSegments(Element container) {
        LinkedHashSet<String> segments = new LinkedHashSet<>();
        String ownText = compactText(container.ownText());
        if (StringUtils.hasText(ownText)) {
            segments.add(ownText);
        }
        for (Element descendant : container.select("*")) {
            String title = compactText(descendant.attr("title"));
            if (StringUtils.hasText(title)) {
                segments.add(title);
            }
            String ownDescendantText = compactText(descendant.ownText());
            if (StringUtils.hasText(ownDescendantText)) {
                segments.add(ownDescendantText);
            }
        }
        if (segments.isEmpty()) {
            segments.addAll(splitSegments(container.text()));
        }
        return new ArrayList<>(segments);
    }

    private String compactText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return rawValue
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limitText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= 80 ? value : value.substring(0, 80);
    }
}
