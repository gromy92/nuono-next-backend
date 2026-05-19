package com.nuono.next.procurement;

import java.util.ArrayList;
import java.util.List;

public class ProcurementAutoInquiryWorkbenchView {

    private String mode;

    private boolean ready;

    private String message;

    private DemandSnapshotView demandItem;

    private CandidateSnapshotView candidate;

    private AutoInquiryTaskView latestTask;

    private List<AutoInquiryTaskView> taskHistory = new ArrayList<>();

    private List<AutoInquirySessionView> sessionPool = new ArrayList<>();

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

    public DemandSnapshotView getDemandItem() {
        return demandItem;
    }

    public void setDemandItem(DemandSnapshotView demandItem) {
        this.demandItem = demandItem;
    }

    public CandidateSnapshotView getCandidate() {
        return candidate;
    }

    public void setCandidate(CandidateSnapshotView candidate) {
        this.candidate = candidate;
    }

    public AutoInquiryTaskView getLatestTask() {
        return latestTask;
    }

    public void setLatestTask(AutoInquiryTaskView latestTask) {
        this.latestTask = latestTask;
    }

    public List<AutoInquiryTaskView> getTaskHistory() {
        return taskHistory;
    }

    public void setTaskHistory(List<AutoInquiryTaskView> taskHistory) {
        this.taskHistory = taskHistory;
    }

    public List<AutoInquirySessionView> getSessionPool() {
        return sessionPool;
    }

    public void setSessionPool(List<AutoInquirySessionView> sessionPool) {
        this.sessionPool = sessionPool;
    }

    public static class DemandSnapshotView {

        private Long id;

        private Integer lineNo;

        private String sourcePlatform;

        private String sourceTitle;

        private String sourceUrl;

        private String targetSite;

        private String status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getLineNo() {
            return lineNo;
        }

        public void setLineNo(Integer lineNo) {
            this.lineNo = lineNo;
        }

        public String getSourcePlatform() {
            return sourcePlatform;
        }

        public void setSourcePlatform(String sourcePlatform) {
            this.sourcePlatform = sourcePlatform;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public String getTargetSite() {
            return targetSite;
        }

        public void setTargetSite(String targetSite) {
            this.targetSite = targetSite;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class CandidateSnapshotView {

        private Long id;

        private Long demandItemId;

        private String candidatePlatform;

        private String title;

        private String supplierName;

        private String candidateUrl;

        private String level;

        private String nextAction;

        private String mainImageUrl;

        private String inquiryOpeningLine;

        private String inquirySummaryLine;

        private List<String> inquiryQuestions = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getDemandItemId() {
            return demandItemId;
        }

        public void setDemandItemId(Long demandItemId) {
            this.demandItemId = demandItemId;
        }

        public String getCandidatePlatform() {
            return candidatePlatform;
        }

        public void setCandidatePlatform(String candidatePlatform) {
            this.candidatePlatform = candidatePlatform;
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

        public String getCandidateUrl() {
            return candidateUrl;
        }

        public void setCandidateUrl(String candidateUrl) {
            this.candidateUrl = candidateUrl;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getNextAction() {
            return nextAction;
        }

        public void setNextAction(String nextAction) {
            this.nextAction = nextAction;
        }

        public String getMainImageUrl() {
            return mainImageUrl;
        }

        public void setMainImageUrl(String mainImageUrl) {
            this.mainImageUrl = mainImageUrl;
        }

        public String getInquiryOpeningLine() {
            return inquiryOpeningLine;
        }

        public void setInquiryOpeningLine(String inquiryOpeningLine) {
            this.inquiryOpeningLine = inquiryOpeningLine;
        }

        public String getInquirySummaryLine() {
            return inquirySummaryLine;
        }

        public void setInquirySummaryLine(String inquirySummaryLine) {
            this.inquirySummaryLine = inquirySummaryLine;
        }

        public List<String> getInquiryQuestions() {
            return inquiryQuestions;
        }

        public void setInquiryQuestions(List<String> inquiryQuestions) {
            this.inquiryQuestions = inquiryQuestions;
        }
    }

    public static class AutoInquiryTaskView {

        private Long id;

        private Long ownerUserId;

        private Long demandItemId;

        private Long candidateId;

        private Long poolId;

        private Long poolItemId;

        private Long sessionId;

        private String platform;

        private String status;

        private String statusLabel;

        private String executionStage;

        private String executionStageLabel;

        private Integer attemptNo;

        private Integer maxAttempts;

        private String targetOfferId;

        private String targetSupplierIdentity;

        private String targetEntryUrl;

        private String targetLocatorText;

        private String inputPreviewText;

        private String inputPayloadText;

        private String inputPayloadHash;

        private String inputLocator;

        private String plannedChannel;

        private String activeChannel;

        private String channelFallbackReason;

        private String externalInquiryId;

        private String externalInquiryUrl;

        private String externalResultStatus;

        private String replySource;

        private String replyParseStatus;

        private String replyParseError;

        private String sendChannel;

        private String sendEvidence;

        private String threadCheckpoint;

        private String lastMessageDigest;

        private String failureCode;

        private String failureMessage;

        private String handoffReason;

        private String message;

        private String startedAt;

        private String sentAt;

        private String confirmedAt;

        private String finishedAt;

        private String createdAt;

        private String updatedAt;

        private List<AutoInquiryEventView> events = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getDemandItemId() {
            return demandItemId;
        }

        public void setDemandItemId(Long demandItemId) {
            this.demandItemId = demandItemId;
        }

        public Long getCandidateId() {
            return candidateId;
        }

        public void setCandidateId(Long candidateId) {
            this.candidateId = candidateId;
        }

        public Long getPoolId() {
            return poolId;
        }

        public void setPoolId(Long poolId) {
            this.poolId = poolId;
        }

        public Long getPoolItemId() {
            return poolItemId;
        }

        public void setPoolItemId(Long poolItemId) {
            this.poolItemId = poolItemId;
        }

        public Long getSessionId() {
            return sessionId;
        }

        public void setSessionId(Long sessionId) {
            this.sessionId = sessionId;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public void setStatusLabel(String statusLabel) {
            this.statusLabel = statusLabel;
        }

        public String getExecutionStage() {
            return executionStage;
        }

        public void setExecutionStage(String executionStage) {
            this.executionStage = executionStage;
        }

        public String getExecutionStageLabel() {
            return executionStageLabel;
        }

        public void setExecutionStageLabel(String executionStageLabel) {
            this.executionStageLabel = executionStageLabel;
        }

        public Integer getAttemptNo() {
            return attemptNo;
        }

        public void setAttemptNo(Integer attemptNo) {
            this.attemptNo = attemptNo;
        }

        public Integer getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(Integer maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public String getTargetOfferId() {
            return targetOfferId;
        }

        public void setTargetOfferId(String targetOfferId) {
            this.targetOfferId = targetOfferId;
        }

        public String getTargetSupplierIdentity() {
            return targetSupplierIdentity;
        }

        public void setTargetSupplierIdentity(String targetSupplierIdentity) {
            this.targetSupplierIdentity = targetSupplierIdentity;
        }

        public String getTargetEntryUrl() {
            return targetEntryUrl;
        }

        public void setTargetEntryUrl(String targetEntryUrl) {
            this.targetEntryUrl = targetEntryUrl;
        }

        public String getTargetLocatorText() {
            return targetLocatorText;
        }

        public void setTargetLocatorText(String targetLocatorText) {
            this.targetLocatorText = targetLocatorText;
        }

        public String getInputPreviewText() {
            return inputPreviewText;
        }

        public void setInputPreviewText(String inputPreviewText) {
            this.inputPreviewText = inputPreviewText;
        }

        public String getInputPayloadText() {
            return inputPayloadText;
        }

        public void setInputPayloadText(String inputPayloadText) {
            this.inputPayloadText = inputPayloadText;
        }

        public String getInputPayloadHash() {
            return inputPayloadHash;
        }

        public void setInputPayloadHash(String inputPayloadHash) {
            this.inputPayloadHash = inputPayloadHash;
        }

        public String getInputLocator() {
            return inputLocator;
        }

        public void setInputLocator(String inputLocator) {
            this.inputLocator = inputLocator;
        }

        public String getPlannedChannel() {
            return plannedChannel;
        }

        public void setPlannedChannel(String plannedChannel) {
            this.plannedChannel = plannedChannel;
        }

        public String getActiveChannel() {
            return activeChannel;
        }

        public void setActiveChannel(String activeChannel) {
            this.activeChannel = activeChannel;
        }

        public String getChannelFallbackReason() {
            return channelFallbackReason;
        }

        public void setChannelFallbackReason(String channelFallbackReason) {
            this.channelFallbackReason = channelFallbackReason;
        }

        public String getExternalInquiryId() {
            return externalInquiryId;
        }

        public void setExternalInquiryId(String externalInquiryId) {
            this.externalInquiryId = externalInquiryId;
        }

        public String getExternalInquiryUrl() {
            return externalInquiryUrl;
        }

        public void setExternalInquiryUrl(String externalInquiryUrl) {
            this.externalInquiryUrl = externalInquiryUrl;
        }

        public String getExternalResultStatus() {
            return externalResultStatus;
        }

        public void setExternalResultStatus(String externalResultStatus) {
            this.externalResultStatus = externalResultStatus;
        }

        public String getReplySource() {
            return replySource;
        }

        public void setReplySource(String replySource) {
            this.replySource = replySource;
        }

        public String getReplyParseStatus() {
            return replyParseStatus;
        }

        public void setReplyParseStatus(String replyParseStatus) {
            this.replyParseStatus = replyParseStatus;
        }

        public String getReplyParseError() {
            return replyParseError;
        }

        public void setReplyParseError(String replyParseError) {
            this.replyParseError = replyParseError;
        }

        public String getSendChannel() {
            return sendChannel;
        }

        public void setSendChannel(String sendChannel) {
            this.sendChannel = sendChannel;
        }

        public String getSendEvidence() {
            return sendEvidence;
        }

        public void setSendEvidence(String sendEvidence) {
            this.sendEvidence = sendEvidence;
        }

        public String getThreadCheckpoint() {
            return threadCheckpoint;
        }

        public void setThreadCheckpoint(String threadCheckpoint) {
            this.threadCheckpoint = threadCheckpoint;
        }

        public String getLastMessageDigest() {
            return lastMessageDigest;
        }

        public void setLastMessageDigest(String lastMessageDigest) {
            this.lastMessageDigest = lastMessageDigest;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        public String getHandoffReason() {
            return handoffReason;
        }

        public void setHandoffReason(String handoffReason) {
            this.handoffReason = handoffReason;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(String startedAt) {
            this.startedAt = startedAt;
        }

        public String getSentAt() {
            return sentAt;
        }

        public void setSentAt(String sentAt) {
            this.sentAt = sentAt;
        }

        public String getConfirmedAt() {
            return confirmedAt;
        }

        public void setConfirmedAt(String confirmedAt) {
            this.confirmedAt = confirmedAt;
        }

        public String getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(String finishedAt) {
            this.finishedAt = finishedAt;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }

        public List<AutoInquiryEventView> getEvents() {
            return events;
        }

        public void setEvents(List<AutoInquiryEventView> events) {
            this.events = events;
        }
    }

    public static class AutoInquirySessionView {

        private Long id;

        private String platform;

        private String sessionKey;

        private String accountLabel;

        private String status;

        private String statusLabel;

        private String riskCode;

        private Long leasedTaskId;

        private String profilePath;

        private String browserEndpoint;

        private String note;

        private String lastCheckedAt;

        private String leaseUpdatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getSessionKey() {
            return sessionKey;
        }

        public void setSessionKey(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        public String getAccountLabel() {
            return accountLabel;
        }

        public void setAccountLabel(String accountLabel) {
            this.accountLabel = accountLabel;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public void setStatusLabel(String statusLabel) {
            this.statusLabel = statusLabel;
        }

        public String getRiskCode() {
            return riskCode;
        }

        public void setRiskCode(String riskCode) {
            this.riskCode = riskCode;
        }

        public Long getLeasedTaskId() {
            return leasedTaskId;
        }

        public void setLeasedTaskId(Long leasedTaskId) {
            this.leasedTaskId = leasedTaskId;
        }

        public String getProfilePath() {
            return profilePath;
        }

        public void setProfilePath(String profilePath) {
            this.profilePath = profilePath;
        }

        public String getBrowserEndpoint() {
            return browserEndpoint;
        }

        public void setBrowserEndpoint(String browserEndpoint) {
            this.browserEndpoint = browserEndpoint;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public String getLastCheckedAt() {
            return lastCheckedAt;
        }

        public void setLastCheckedAt(String lastCheckedAt) {
            this.lastCheckedAt = lastCheckedAt;
        }

        public String getLeaseUpdatedAt() {
            return leaseUpdatedAt;
        }

        public void setLeaseUpdatedAt(String leaseUpdatedAt) {
            this.leaseUpdatedAt = leaseUpdatedAt;
        }
    }

    public static class AutoInquiryEventView {

        private Long id;

        private Long taskId;

        private String eventType;

        private String statusBefore;

        private String statusAfter;

        private String executionStage;

        private String eventMessage;

        private String eventPayload;

        private String createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getStatusBefore() {
            return statusBefore;
        }

        public void setStatusBefore(String statusBefore) {
            this.statusBefore = statusBefore;
        }

        public String getStatusAfter() {
            return statusAfter;
        }

        public void setStatusAfter(String statusAfter) {
            this.statusAfter = statusAfter;
        }

        public String getExecutionStage() {
            return executionStage;
        }

        public void setExecutionStage(String executionStage) {
            this.executionStage = executionStage;
        }

        public String getEventMessage() {
            return eventMessage;
        }

        public void setEventMessage(String eventMessage) {
            this.eventMessage = eventMessage;
        }

        public String getEventPayload() {
            return eventPayload;
        }

        public void setEventPayload(String eventPayload) {
            this.eventPayload = eventPayload;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}
