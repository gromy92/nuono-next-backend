package com.nuono.next.noonpull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Scripted transport kept outside the behavior test so the scenarios stay readable. */
final class FakeReportProvider implements NoonReportProvider {
    final List<String> calls = new ArrayList<>();
    private final List<NoonReportExportStatus> pollStatuses;
    private final byte[] content;
    private final RuntimeException pollException;

    private FakeReportProvider(
            List<NoonReportExportStatus> pollStatuses,
            String content,
            RuntimeException pollException
    ) {
        this.pollStatuses = new ArrayList<>(pollStatuses);
        this.content = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        this.pollException = pollException;
    }

    static FakeReportProvider ready(String content) {
        return new FakeReportProvider(
                List.of(NoonReportExportStatus.ready("https://download.test/sales.csv")),
                content,
                null
        );
    }

    static FakeReportProvider missingDownloadUrl() {
        return new FakeReportProvider(List.of(NoonReportExportStatus.ready(null)), "", null);
    }

    static FakeReportProvider pending() {
        return new FakeReportProvider(List.of(NoonReportExportStatus.pending()), "", null);
    }

    static FakeReportProvider failedExport() {
        return new FakeReportProvider(
                List.of(NoonReportExportStatus.failed("export failed")),
                "",
                null
        );
    }

    static FakeReportProvider sequence(NoonReportExportStatus... statuses) {
        return new FakeReportProvider(
                Arrays.asList(statuses),
                "date,sku_parent,units_sold,sales_amount,currency\n"
                        + "2026-05-21,Z1,2,39.90,AED\n",
                null
        );
    }

    static FakeReportProvider throwingOnPoll(String message) {
        return new FakeReportProvider(
                List.of(NoonReportExportStatus.pending()),
                "",
                new RuntimeException(message)
        );
    }

    @Override
    public String createExport(NoonReportPullRequest request) {
        calls.add("create");
        return "EXP-1";
    }

    @Override
    public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
        calls.add("poll:" + exportId);
        if (pollException != null) {
            throw pollException;
        }
        if (pollStatuses.isEmpty()) {
            return NoonReportExportStatus.pending();
        }
        if (pollStatuses.size() == 1) {
            return pollStatuses.get(0);
        }
        return pollStatuses.remove(0);
    }

    @Override
    public byte[] download(NoonReportPullRequest request, String downloadUrl) {
        calls.add("download");
        return content;
    }
}
