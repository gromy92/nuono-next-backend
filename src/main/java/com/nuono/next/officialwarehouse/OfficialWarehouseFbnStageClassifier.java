package com.nuono.next.officialwarehouse;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.report.ReportFactColumnContract;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportPullRequest;
import com.nuono.next.noonpull.NoonReportRowDecision;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportCsvParser.ReceivedRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Container-first DP-07-B row classification, before any deterministic business defect. */
@Component
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class OfficialWarehouseFbnStageClassifier {
    private final OfficialWarehouseFbnReceivedReportCsvParser parser;

    public OfficialWarehouseFbnStageClassifier(
            OfficialWarehouseFbnReceivedReportCsvParser parser
    ) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public void requireHeader(String[] header) {
        parser.requireStageHeader(header);
    }

    public List<NoonReportRowDecision<ReceivedRow>> classify(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows
    ) {
        requireHeader(header);
        List<String> headers = OfficialWarehouseFbnReceivedReportValueParser.normalizedHeaders(
                Arrays.asList(header)
        );
        List<NoonReportRowDecision<ReceivedRow>> decisions = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            String[] record = rows.get(index);
            if (parser.isBlank(record)) {
                decisions.add(NoonReportRowDecision.businessSkip());
                continue;
            }
            Map<String, String> raw = parser.rawFields(headers, Arrays.asList(record));
            if (outsideContainer(file, raw)) {
                decisions.add(NoonReportRowDecision.containerContractError());
                continue;
            }
            try {
                ReceivedRow row = parser.toRow(index + 2, raw);
                decisions.add(row == null || row.asnScheduleDate == null || exceedsFactColumns(row)
                        ? NoonReportRowDecision.businessSkip()
                        : NoonReportRowDecision.accept(row));
            } catch (IllegalArgumentException deterministicDefect) {
                decisions.add(NoonReportRowDecision.businessSkip());
            }
        }
        return List.copyOf(decisions);
    }

    public String identity(ReceivedRow row) {
        return row.businessKey();
    }

    private boolean exceedsFactColumns(ReceivedRow row) {
        try {
            ReportFactColumnContract.text(row.businessKey(), 500);
            ReportFactColumnContract.text(row.partnerSku, 100);
            ReportFactColumnContract.text(row.noonSku, 100);
            ReportFactColumnContract.text(row.pbarcodeCanonical, 120);
            ReportFactColumnContract.text(row.noonAsnNr, 120);
            ReportFactColumnContract.text(row.partnerWarehouse, 100);
            ReportFactColumnContract.text(row.noonWarehouse, 100);
            ReportFactColumnContract.text(row.countryCode, 20);
            ReportFactColumnContract.text(row.qcFailedReason, 1000);
            ReportFactColumnContract.date(LocalDate.parse(row.asnScheduleDate));
            persistableDateTime(row.asnCreatedAt);
            persistableDateTime(row.asnCompletedAt);
            return false;
        } catch (IllegalArgumentException invalidTargetValue) {
            return true;
        }
    }

    private void persistableDateTime(String value) {
        if (value == null) return;
        if (value.length() == 10) {
            ReportFactColumnContract.date(LocalDate.parse(value));
            return;
        }
        ReportFactColumnContract.dateTime(LocalDateTime.parse(value.replace(' ', 'T')));
    }

    private boolean outsideContainer(
            NoonReportDownloadedFile file,
            Map<String, String> raw
    ) {
        NoonReportPullRequest request = file == null ? null : file.getRequest();
        if (request == null || request.getDateFrom() == null || request.getDateTo() == null) {
            return true;
        }
        String requestedSite = normalizeSite(request.getSiteCode());
        if (requestedSite == null) {
            return true;
        }
        String rawCountry = OfficialWarehouseFbnReceivedReportValueParser.text(
                raw, "country_code"
        );
        String contentSite = normalizeSite(rawCountry);
        if (rawCountry != null && contentSite == null) {
            return true;
        }
        if (contentSite != null && !requestedSite.equals(contentSite)) {
            return true;
        }
        String scheduleDate;
        try {
            scheduleDate = OfficialWarehouseFbnReceivedReportValueParser.date(
                    raw, "asn_schedule_date"
            );
        } catch (IllegalArgumentException invalidDate) {
            return false;
        }
        if (scheduleDate == null) {
            return false;
        }
        LocalDate date = LocalDate.parse(scheduleDate);
        try {
            ReportFactColumnContract.date(date);
        } catch (IllegalArgumentException invalidTargetDate) {
            return false;
        }
        return date.isBefore(request.getDateFrom()) || date.isAfter(request.getDateTo());
    }

    private String normalizeSite(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(normalized) || "KSA".equals(normalized)
                || "SAUDI ARABIA".equals(normalized)) {
            return "SA";
        }
        if ("AE".equals(normalized) || "UAE".equals(normalized)
                || "UNITED ARAB EMIRATES".equals(normalized)) {
            return "AE";
        }
        return null;
    }
}
