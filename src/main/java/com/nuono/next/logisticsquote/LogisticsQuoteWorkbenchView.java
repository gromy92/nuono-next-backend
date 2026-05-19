package com.nuono.next.logisticsquote;

import java.util.ArrayList;
import java.util.List;

public class LogisticsQuoteWorkbenchView {

    private String mode;

    private boolean ready;

    private String message;

    private SummaryView summary = new SummaryView();

    private Long selectedBundleId;

    private List<BundleListItemView> bundles = new ArrayList<>();

    private BundleDetailView selectedBundle;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SummaryView getSummary() {
        return summary;
    }

    public void setSummary(SummaryView summary) {
        this.summary = summary;
    }

    public Long getSelectedBundleId() {
        return selectedBundleId;
    }

    public void setSelectedBundleId(Long selectedBundleId) {
        this.selectedBundleId = selectedBundleId;
    }

    public List<BundleListItemView> getBundles() {
        return bundles;
    }

    public void setBundles(List<BundleListItemView> bundles) {
        this.bundles = bundles;
    }

    public BundleDetailView getSelectedBundle() {
        return selectedBundle;
    }

    public void setSelectedBundle(BundleDetailView selectedBundle) {
        this.selectedBundle = selectedBundle;
    }

    public static class SummaryView {

        private int totalForwarders;

        private int totalBundles;

        private int publishedVersions;

        private int totalRules;

        public int getTotalForwarders() {
            return totalForwarders;
        }

        public void setTotalForwarders(int totalForwarders) {
            this.totalForwarders = totalForwarders;
        }

        public int getTotalBundles() {
            return totalBundles;
        }

        public void setTotalBundles(int totalBundles) {
            this.totalBundles = totalBundles;
        }

        public int getPublishedVersions() {
            return publishedVersions;
        }

        public void setPublishedVersions(int publishedVersions) {
            this.publishedVersions = publishedVersions;
        }

        public int getTotalRules() {
            return totalRules;
        }

        public void setTotalRules(int totalRules) {
            this.totalRules = totalRules;
        }
    }

    public static class BundleListItemView {

        private Long id;

        private String bundleName;

        private String forwarderName;

        private String analysisStatus;

        private String latestVersionNo;

        private String latestVersionStatus;

        private String recommendationLevel;

        private int fileCount;

        private int noteCount;

        private String updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getBundleName() {
            return bundleName;
        }

        public void setBundleName(String bundleName) {
            this.bundleName = bundleName;
        }

        public String getForwarderName() {
            return forwarderName;
        }

        public void setForwarderName(String forwarderName) {
            this.forwarderName = forwarderName;
        }

        public String getAnalysisStatus() {
            return analysisStatus;
        }

        public void setAnalysisStatus(String analysisStatus) {
            this.analysisStatus = analysisStatus;
        }

        public String getLatestVersionNo() {
            return latestVersionNo;
        }

        public void setLatestVersionNo(String latestVersionNo) {
            this.latestVersionNo = latestVersionNo;
        }

        public String getLatestVersionStatus() {
            return latestVersionStatus;
        }

        public void setLatestVersionStatus(String latestVersionStatus) {
            this.latestVersionStatus = latestVersionStatus;
        }

        public String getRecommendationLevel() {
            return recommendationLevel;
        }

        public void setRecommendationLevel(String recommendationLevel) {
            this.recommendationLevel = recommendationLevel;
        }

        public int getFileCount() {
            return fileCount;
        }

        public void setFileCount(int fileCount) {
            this.fileCount = fileCount;
        }

        public int getNoteCount() {
            return noteCount;
        }

        public void setNoteCount(int noteCount) {
            this.noteCount = noteCount;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class BundleDetailView {

        private Long id;

        private String bundleName;

        private String analysisStatus;

        private String analysisSummary;

        private String sourceReadbackHint;

        private Long selectedNoteId;

        private Long selectedFileId;

        private ForwarderView forwarder = new ForwarderView();

        private QuoteVersionView quoteVersion = new QuoteVersionView();

        private List<SourceFileView> files = new ArrayList<>();

        private List<SourceNoteView> notes = new ArrayList<>();

        private List<ServiceView> services = new ArrayList<>();

        private List<RuleView> rules = new ArrayList<>();

        private List<RestrictionView> restrictions = new ArrayList<>();

        private List<EvidenceView> evidences = new ArrayList<>();

        private ReputationSnapshotView reputationSnapshot = new ReputationSnapshotView();

        private List<ReputationSignalView> reputationSignals = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getBundleName() {
            return bundleName;
        }

        public void setBundleName(String bundleName) {
            this.bundleName = bundleName;
        }

        public String getAnalysisStatus() {
            return analysisStatus;
        }

        public void setAnalysisStatus(String analysisStatus) {
            this.analysisStatus = analysisStatus;
        }

        public String getAnalysisSummary() {
            return analysisSummary;
        }

        public void setAnalysisSummary(String analysisSummary) {
            this.analysisSummary = analysisSummary;
        }

        public String getSourceReadbackHint() {
            return sourceReadbackHint;
        }

        public void setSourceReadbackHint(String sourceReadbackHint) {
            this.sourceReadbackHint = sourceReadbackHint;
        }

        public Long getSelectedNoteId() {
            return selectedNoteId;
        }

        public void setSelectedNoteId(Long selectedNoteId) {
            this.selectedNoteId = selectedNoteId;
        }

        public Long getSelectedFileId() {
            return selectedFileId;
        }

        public void setSelectedFileId(Long selectedFileId) {
            this.selectedFileId = selectedFileId;
        }

        public ForwarderView getForwarder() {
            return forwarder;
        }

        public void setForwarder(ForwarderView forwarder) {
            this.forwarder = forwarder;
        }

        public QuoteVersionView getQuoteVersion() {
            return quoteVersion;
        }

        public void setQuoteVersion(QuoteVersionView quoteVersion) {
            this.quoteVersion = quoteVersion;
        }

        public List<SourceFileView> getFiles() {
            return files;
        }

        public void setFiles(List<SourceFileView> files) {
            this.files = files;
        }

        public List<SourceNoteView> getNotes() {
            return notes;
        }

        public void setNotes(List<SourceNoteView> notes) {
            this.notes = notes;
        }

        public List<ServiceView> getServices() {
            return services;
        }

        public void setServices(List<ServiceView> services) {
            this.services = services;
        }

        public List<RuleView> getRules() {
            return rules;
        }

        public void setRules(List<RuleView> rules) {
            this.rules = rules;
        }

        public List<RestrictionView> getRestrictions() {
            return restrictions;
        }

        public void setRestrictions(List<RestrictionView> restrictions) {
            this.restrictions = restrictions;
        }

        public List<EvidenceView> getEvidences() {
            return evidences;
        }

        public void setEvidences(List<EvidenceView> evidences) {
            this.evidences = evidences;
        }

        public ReputationSnapshotView getReputationSnapshot() {
            return reputationSnapshot;
        }

        public void setReputationSnapshot(ReputationSnapshotView reputationSnapshot) {
            this.reputationSnapshot = reputationSnapshot;
        }

        public List<ReputationSignalView> getReputationSignals() {
            return reputationSignals;
        }

        public void setReputationSignals(List<ReputationSignalView> reputationSignals) {
            this.reputationSignals = reputationSignals;
        }
    }

    public static class ForwarderView {

        private Long id;

        private String name;

        private String alias;

        private String companyName;

        private String notes;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    public static class QuoteVersionView {

        private Long id;

        private String versionNo;

        private String status;

        private String summary;

        private String effectiveFrom;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getVersionNo() {
            return versionNo;
        }

        public void setVersionNo(String versionNo) {
            this.versionNo = versionNo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getEffectiveFrom() {
            return effectiveFrom;
        }

        public void setEffectiveFrom(String effectiveFrom) {
            this.effectiveFrom = effectiveFrom;
        }
    }

    public static class SourceFileView {

        private Long id;

        private String fileName;

        private String fileType;

        private String filePath;

        private String sourceLabel;

        private Boolean archived;

        private String archiveUrl;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }

        public void setSourceLabel(String sourceLabel) {
            this.sourceLabel = sourceLabel;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Boolean getArchived() {
            return archived;
        }

        public void setArchived(Boolean archived) {
            this.archived = archived;
        }

        public String getArchiveUrl() {
            return archiveUrl;
        }

        public void setArchiveUrl(String archiveUrl) {
            this.archiveUrl = archiveUrl;
        }
    }

    public static class SourceNoteView {

        private Long id;

        private String noteType;

        private String sourceChannel;

        private String content;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getNoteType() {
            return noteType;
        }

        public void setNoteType(String noteType) {
            this.noteType = noteType;
        }

        public String getSourceChannel() {
            return sourceChannel;
        }

        public void setSourceChannel(String sourceChannel) {
            this.sourceChannel = sourceChannel;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class ServiceView {

        private String serviceName;

        private String countryCode;

        private String routeCode;

        private String transportMode;

        private String businessType;

        private String serviceScope;

        private String transitTimeText;

        private String remarks;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
        }

        public String getRouteCode() {
            return routeCode;
        }

        public void setRouteCode(String routeCode) {
            this.routeCode = routeCode;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        public String getBusinessType() {
            return businessType;
        }

        public void setBusinessType(String businessType) {
            this.businessType = businessType;
        }

        public String getServiceScope() {
            return serviceScope;
        }

        public void setServiceScope(String serviceScope) {
            this.serviceScope = serviceScope;
        }

        public String getTransitTimeText() {
            return transitTimeText;
        }

        public void setTransitTimeText(String transitTimeText) {
            this.transitTimeText = transitTimeText;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }
    }

    public static class RuleView {

        private String serviceName;

        private String ruleName;

        private String ruleType;

        private String cargoCategory;

        private String billingUnit;

        private String currency;

        private Double unitPrice;

        private String calcBasis;

        private String summary;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getCargoCategory() {
            return cargoCategory;
        }

        public void setCargoCategory(String cargoCategory) {
            this.cargoCategory = cargoCategory;
        }

        public String getBillingUnit() {
            return billingUnit;
        }

        public void setBillingUnit(String billingUnit) {
            this.billingUnit = billingUnit;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public Double getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(Double unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getCalcBasis() {
            return calcBasis;
        }

        public void setCalcBasis(String calcBasis) {
            this.calcBasis = calcBasis;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }

    public static class RestrictionView {

        private String serviceName;

        private String restrictionType;

        private String operator;

        private String value;

        private String unit;

        private String severity;

        private String description;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getRestrictionType() {
            return restrictionType;
        }

        public void setRestrictionType(String restrictionType) {
            this.restrictionType = restrictionType;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class EvidenceView {

        private String targetType;

        private String targetName;

        private String sourceType;

        private String sourceName;

        private String locator;

        private String evidenceText;

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }

        public String getTargetName() {
            return targetName;
        }

        public void setTargetName(String targetName) {
            this.targetName = targetName;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName(String sourceName) {
            this.sourceName = sourceName;
        }

        public String getLocator() {
            return locator;
        }

        public void setLocator(String locator) {
            this.locator = locator;
        }

        public String getEvidenceText() {
            return evidenceText;
        }

        public void setEvidenceText(String evidenceText) {
            this.evidenceText = evidenceText;
        }
    }

    public static class ReputationSnapshotView {

        private Integer overallScore;

        private Integer complianceScore;

        private Integer timelinessScore;

        private Integer priceTransparencyScore;

        private Integer claimsScore;

        private Integer serviceScore;

        private String recommendationLevel;

        private String recentRiskSummary;

        private String analysisSummary;

        public Integer getOverallScore() {
            return overallScore;
        }

        public void setOverallScore(Integer overallScore) {
            this.overallScore = overallScore;
        }

        public Integer getComplianceScore() {
            return complianceScore;
        }

        public void setComplianceScore(Integer complianceScore) {
            this.complianceScore = complianceScore;
        }

        public Integer getTimelinessScore() {
            return timelinessScore;
        }

        public void setTimelinessScore(Integer timelinessScore) {
            this.timelinessScore = timelinessScore;
        }

        public Integer getPriceTransparencyScore() {
            return priceTransparencyScore;
        }

        public void setPriceTransparencyScore(Integer priceTransparencyScore) {
            this.priceTransparencyScore = priceTransparencyScore;
        }

        public Integer getClaimsScore() {
            return claimsScore;
        }

        public void setClaimsScore(Integer claimsScore) {
            this.claimsScore = claimsScore;
        }

        public Integer getServiceScore() {
            return serviceScore;
        }

        public void setServiceScore(Integer serviceScore) {
            this.serviceScore = serviceScore;
        }

        public String getRecommendationLevel() {
            return recommendationLevel;
        }

        public void setRecommendationLevel(String recommendationLevel) {
            this.recommendationLevel = recommendationLevel;
        }

        public String getRecentRiskSummary() {
            return recentRiskSummary;
        }

        public void setRecentRiskSummary(String recentRiskSummary) {
            this.recentRiskSummary = recentRiskSummary;
        }

        public String getAnalysisSummary() {
            return analysisSummary;
        }

        public void setAnalysisSummary(String analysisSummary) {
            this.analysisSummary = analysisSummary;
        }
    }

    public static class ReputationSignalView {

        private String signalType;

        private String polarity;

        private String severity;

        private String sourceType;

        private String topic;

        private String evidenceText;

        public String getSignalType() {
            return signalType;
        }

        public void setSignalType(String signalType) {
            this.signalType = signalType;
        }

        public String getPolarity() {
            return polarity;
        }

        public void setPolarity(String polarity) {
            this.polarity = polarity;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getEvidenceText() {
            return evidenceText;
        }

        public void setEvidenceText(String evidenceText) {
            this.evidenceText = evidenceText;
        }
    }
}
