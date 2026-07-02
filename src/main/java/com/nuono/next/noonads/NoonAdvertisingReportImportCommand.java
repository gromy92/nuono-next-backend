package com.nuono.next.noonads;

import java.time.LocalDate;
import java.util.List;
import org.springframework.util.StringUtils;

public class NoonAdvertisingReportImportCommand {
    private final Long ownerUserId;
    private final Long requestedBy;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String sourceName;
    private final String sourceDigestSha256;
    private final String notes;
    private final List<NoonAdvertisingCampaignFact> campaignRows;
    private final List<NoonAdvertisingQueryFact> queryRows;

    public NoonAdvertisingReportImportCommand(
            Long ownerUserId,
            Long requestedBy,
            String projectCode,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String sourceName,
            String sourceDigestSha256,
            String notes,
            List<NoonAdvertisingCampaignFact> campaignRows,
            List<NoonAdvertisingQueryFact> queryRows
    ) {
        this.ownerUserId = ownerUserId;
        this.requestedBy = requestedBy;
        this.projectCode = normalize(projectCode);
        this.storeCode = normalize(storeCode);
        this.siteCode = normalize(siteCode);
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.sourceName = normalize(sourceName);
        this.sourceDigestSha256 = normalize(sourceDigestSha256);
        this.notes = StringUtils.hasText(notes) ? notes.trim() : null;
        this.campaignRows = campaignRows == null ? List.of() : List.copyOf(campaignRows);
        this.queryRows = queryRows == null ? List.of() : List.copyOf(queryRows);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public Long getRequestedBy() { return requestedBy; }
    public String getProjectCode() { return projectCode; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public String getSourceName() { return sourceName; }
    public String getSourceDigestSha256() { return sourceDigestSha256; }
    public String getNotes() { return notes; }
    public List<NoonAdvertisingCampaignFact> getCampaignRows() { return campaignRows; }
    public List<NoonAdvertisingQueryFact> getQueryRows() { return queryRows; }
}
