package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateGroupView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProcurementCandidateGroupingAdvisor {

    private static final Pattern TITLE_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[a-z0-9]{2,}");
    private static final Set<String> TITLE_STOP_WORDS = Set.of(
            "同款",
            "优先",
            "工厂",
            "工厂版",
            "工厂款",
            "备选",
            "相近",
            "相似",
            "低配",
            "新款",
            "升级",
            "轻奢",
            "便携",
            "桌面",
            "家居",
            "礼品",
            "礼盒",
            "彩盒",
            "现货",
            "厂家",
            "批发",
            "款",
            "版",
            "套装"
    );

    public List<CandidateGroupView> buildGroups(DemandItemView demandItem, List<CandidateView> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }

        List<GroupBucket> buckets = new ArrayList<>();
        int nextGroupIndex = 1;
        for (CandidateView candidate : sortCandidates(candidates)) {
            CandidateFingerprint fingerprint = fingerprint(candidate);
            GroupBucket matchedBucket = null;
            double bestScore = 0D;
            for (GroupBucket bucket : buckets) {
                double score = bucket.matchScore(fingerprint);
                if (score > bestScore) {
                    matchedBucket = bucket;
                    bestScore = score;
                }
            }

            if (matchedBucket == null || bestScore < 0.60D) {
                matchedBucket = new GroupBucket("group-" + demandItem.getId() + "-" + nextGroupIndex, fingerprint, candidate);
                buckets.add(matchedBucket);
                nextGroupIndex += 1;
            } else {
                matchedBucket.addCandidate(candidate, fingerprint);
            }
        }

        buckets.sort(Comparator
                .comparing(GroupBucket::bestScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GroupBucket::candidateCount, Comparator.reverseOrder())
                .thenComparing(GroupBucket::primaryRankNo, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        List<CandidateGroupView> groups = new ArrayList<>();
        int rank = 1;
        for (GroupBucket bucket : buckets) {
            CandidateGroupView group = bucket.toView(rank);
            groups.add(group);
            for (CandidateView candidate : bucket.candidates) {
                candidate.setGroupKey(group.getGroupKey());
                candidate.setGroupLabel(group.getGroupLabel());
                candidate.setGroupType(group.getGroupType());
                candidate.setGroupRank(rank);
            }
            rank += 1;
        }
        return groups;
    }

    private List<CandidateView> sortCandidates(List<CandidateView> candidates) {
        return candidates.stream()
                .sorted(Comparator
                        .comparing((CandidateView candidate) -> Boolean.TRUE.equals(candidate.getSelected()) ? 0 : 1)
                        .thenComparing(CandidateView::getTotalScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CandidateView::getRankNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CandidateView::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .collect(Collectors.toList());
    }

    private CandidateFingerprint fingerprint(CandidateView candidate) {
        return new CandidateFingerprint(
                normalizeUrl(candidate.getCandidateUrl()),
                normalizeUrl(candidate.getMainImageUrl()),
                normalizeSupplier(candidate.getSupplierName()),
                extractTitleTokens(candidate.getTitle()),
                normalizeCategory(candidate.getStandardizedPowerModeText(), candidate.getPowerModeText()),
                normalizeCategory(candidate.getStandardizedMaterialText(), candidate.getMaterialText()),
                normalizeCategory(candidate.getStandardizedSizeText(), candidate.getSizeText()),
                normalizeCategory(candidate.getStandardizedPackageText(), candidate.getPackageText())
        );
    }

    private String normalizeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        return normalized.replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeSupplier(String value) {
        String normalized = lower(value)
                .replace("源头工厂", "")
                .replace("供应链", "")
                .replace("工作室", "")
                .replace("商行", "")
                .replace("档口", "")
                .replace("工厂", "")
                .replace("厂家", "")
                .replace("旗舰", "")
                .replace("企业", "")
                .replace("店", "")
                .trim();
        return normalizeSpaces(normalized);
    }

    private List<String> extractTitleTokens(String title) {
        if (!StringUtils.hasText(title)) {
            return new ArrayList<>();
        }
        String normalized = lower(title)
                .replace("usb", "充电")
                .replace("type-c", "充电")
                .replace("typec", "充电")
                .replace("portable", "便携");
        Matcher matcher = TITLE_TOKEN_PATTERN.matcher(normalized);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (TITLE_STOP_WORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
            if (tokens.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(tokens);
    }

    private String normalizeCategory(String primaryValue, String fallbackValue) {
        String candidate = firstNonBlank(primaryValue, fallbackValue);
        if (!StringUtils.hasText(candidate)) {
            return "";
        }
        return normalizeSpaces(lower(candidate));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeSpaces(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static final class GroupBucket {

        private final String key;
        private final List<CandidateView> candidates = new ArrayList<>();
        private final List<CandidateFingerprint> fingerprints = new ArrayList<>();
        private CandidateView primary;

        private GroupBucket(String key, CandidateFingerprint fingerprint, CandidateView candidate) {
            this.key = key;
            this.primary = candidate;
            addCandidate(candidate, fingerprint);
        }

        private void addCandidate(CandidateView candidate, CandidateFingerprint fingerprint) {
            candidates.add(candidate);
            fingerprints.add(fingerprint);
            if (primary == null) {
                primary = candidate;
                return;
            }
            Integer currentScore = primary.getTotalScore();
            Integer nextScore = candidate.getTotalScore();
            if ((nextScore != null && currentScore == null)
                    || (nextScore != null && currentScore != null && nextScore > currentScore)
                    || (Boolean.TRUE.equals(candidate.getSelected()) && !Boolean.TRUE.equals(primary.getSelected()))) {
                primary = candidate;
            }
        }

        private double matchScore(CandidateFingerprint fingerprint) {
            double best = 0D;
            for (CandidateFingerprint current : fingerprints) {
                best = Math.max(best, current.similarityTo(fingerprint));
            }
            return best;
        }

        private Integer bestScore() {
            return primary == null ? null : primary.getTotalScore();
        }

        private int candidateCount() {
            return candidates.size();
        }

        private Integer primaryRankNo() {
            return primary == null ? null : primary.getRankNo();
        }

        private CandidateGroupView toView(int rank) {
            CandidateGroupView view = new CandidateGroupView();
            view.setGroupKey(key);
            view.setGroupType(resolveGroupType());
            view.setGroupLabel(buildLabel());
            view.setSummary(buildSummary());
            view.setRepresentativeTitle(primary == null ? "" : primary.getTitle());
            view.setRepresentativeSupplierName(primary == null ? "" : primary.getSupplierName());
            view.setMainImageUrl(primary == null ? "" : primary.getMainImageUrl());
            view.setCandidateCount(candidates.size());
            view.setSupplierCount((int) candidates.stream()
                    .map(CandidateView::getSupplierName)
                    .filter(StringUtils::hasText)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .count());
            view.setBestScore(bestScore());
            view.setBestCandidateId(primary == null ? null : primary.getId());
            view.setCandidateIds(candidates.stream()
                    .map(CandidateView::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList()));
            view.setTags(buildTags(rank));
            return view;
        }

        private String resolveGroupType() {
            if (fingerprints.stream().map(CandidateFingerprint::canonicalUrl).filter(StringUtils::hasText).distinct().count() == 1
                    && fingerprints.stream().anyMatch(item -> StringUtils.hasText(item.canonicalUrl()))) {
                return "SAME_OFFER";
            }
            if (fingerprints.stream().map(CandidateFingerprint::imageKey).filter(StringUtils::hasText).distinct().count() == 1
                    && fingerprints.stream().anyMatch(item -> StringUtils.hasText(item.imageKey()))) {
                return "SAME_VISUAL";
            }
            if (fingerprints.stream().map(CandidateFingerprint::supplierKey).filter(StringUtils::hasText).distinct().count() == 1
                    && candidateCount() > 1) {
                return "SUPPLIER_SERIES";
            }
            return candidateCount() > 1 ? "SIMILAR_SPEC" : "SINGLE";
        }

        private String buildLabel() {
            String type = resolveGroupType();
            String supplier = primary == null ? "" : primary.getSupplierName();
            if ("SAME_OFFER".equals(type) || "SAME_VISUAL".equals(type)) {
                return "疑似同款组";
            }
            if ("SUPPLIER_SERIES".equals(type) && StringUtils.hasText(supplier)) {
                return supplier + " 系列";
            }
            if ("SIMILAR_SPEC".equals(type)) {
                return "相似规格组";
            }
            return "独立候选";
        }

        private String buildSummary() {
            String supplier = primary == null ? "" : primary.getSupplierName();
            String title = primary == null ? "" : primary.getTitle();
            if (candidateCount() <= 1) {
                return StringUtils.hasText(supplier)
                        ? "当前这条候选暂时没有明显重复项，可直接进入询价判断。"
                        : "当前这条候选暂时没有明显重复项，可直接进入下一步判断。";
            }
            if (StringUtils.hasText(supplier)) {
                return String.format("共 %d 条候选，优先从 %s 这组里筛主推款。", candidateCount(), supplier);
            }
            return String.format("共 %d 条候选围绕 %s 聚集，适合先做组内去重。", candidateCount(), defaultText(title, "当前商品"));
        }

        private List<String> buildTags(int rank) {
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            tags.add("第 " + rank + " 组");
            tags.add("候选 " + candidateCount() + " 条");
            long supplierCount = candidates.stream()
                    .map(CandidateView::getSupplierName)
                    .filter(StringUtils::hasText)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .count();
            if (supplierCount > 0) {
                tags.add("供应商 " + supplierCount + " 家");
            }
            if (primary != null && primary.getTotalScore() != null) {
                tags.add("最高分 " + primary.getTotalScore());
            }
            if (primary != null && StringUtils.hasText(primary.getStandardizedPowerModeText())) {
                tags.add(primary.getStandardizedPowerModeText());
            }
            if (primary != null && StringUtils.hasText(primary.getStandardizedMaterialText())) {
                tags.add(primary.getStandardizedMaterialText());
            }
            return new ArrayList<>(tags);
        }

        private String defaultText(String value, String fallback) {
            return StringUtils.hasText(value) ? value.trim() : fallback;
        }
    }

    private static final class CandidateFingerprint {

        private final String canonicalUrl;
        private final String imageKey;
        private final String supplierKey;
        private final List<String> titleTokens;
        private final String powerKey;
        private final String materialKey;
        private final String sizeKey;
        private final String packageKey;

        private CandidateFingerprint(
                String canonicalUrl,
                String imageKey,
                String supplierKey,
                List<String> titleTokens,
                String powerKey,
                String materialKey,
                String sizeKey,
                String packageKey
        ) {
            this.canonicalUrl = canonicalUrl;
            this.imageKey = imageKey;
            this.supplierKey = supplierKey;
            this.titleTokens = titleTokens;
            this.powerKey = powerKey;
            this.materialKey = materialKey;
            this.sizeKey = sizeKey;
            this.packageKey = packageKey;
        }

        private double similarityTo(CandidateFingerprint other) {
            if (other == null) {
                return 0D;
            }
            if (StringUtils.hasText(canonicalUrl) && canonicalUrl.equals(other.canonicalUrl)) {
                return 1D;
            }
            if (StringUtils.hasText(imageKey) && imageKey.equals(other.imageKey)) {
                return 0.96D;
            }

            double score = 0D;
            double titleOverlap = overlap(titleTokens, other.titleTokens);
            if (titleOverlap >= 0.60D) {
                score += 0.42D;
            } else if (titleOverlap >= 0.35D) {
                score += 0.28D;
            } else if (titleOverlap >= 0.20D) {
                score += 0.15D;
            }

            if (StringUtils.hasText(supplierKey) && supplierKey.equals(other.supplierKey)) {
                score += 0.20D;
            }
            if (StringUtils.hasText(powerKey) && powerKey.equals(other.powerKey)) {
                score += 0.15D;
            }
            if (StringUtils.hasText(materialKey) && materialKey.equals(other.materialKey)) {
                score += 0.10D;
            }
            if (StringUtils.hasText(sizeKey) && sizeKey.equals(other.sizeKey)) {
                score += 0.08D;
            }
            if (StringUtils.hasText(packageKey) && packageKey.equals(other.packageKey)) {
                score += 0.05D;
            }
            return score;
        }

        private double overlap(List<String> left, List<String> right) {
            if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
                return 0D;
            }
            Set<String> intersection = new LinkedHashSet<>(left);
            intersection.retainAll(right);
            Set<String> union = new LinkedHashSet<>(left);
            union.addAll(right);
            int unionSize = union.size();
            if (unionSize <= 0) {
                return 0D;
            }
            return (double) intersection.size() / unionSize;
        }

        private String canonicalUrl() {
            return canonicalUrl;
        }

        private String imageKey() {
            return imageKey;
        }

        private String supplierKey() {
            return supplierKey;
        }
    }
}
