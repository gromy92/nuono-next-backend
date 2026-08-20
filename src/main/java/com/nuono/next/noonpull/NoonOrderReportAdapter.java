package com.nuono.next.noonpull;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Legacy direct-write adapter. The unified DP Runtime uses the classifier directly. */
@Service
public class NoonOrderReportAdapter {
    private final NoonOrderFactWriter factWriter;
    private final ObjectProvider<NoonOrderFactWriter> factWriterProvider;
    private final NoonOrderReportRowClassifier classifier;

    public NoonOrderReportAdapter(NoonOrderFactWriter factWriter, Clock clock) {
        this.factWriter = factWriter;
        this.factWriterProvider = null;
        this.classifier = new NoonOrderReportRowClassifier(clock);
    }

    @Autowired
    public NoonOrderReportAdapter(
            ObjectProvider<NoonOrderFactWriter> factWriterProvider,
            NoonOrderReportRowClassifier classifier
    ) {
        this.factWriter = null;
        this.factWriterProvider = factWriterProvider;
        this.classifier = classifier;
    }

    public NoonReportProcessResult process(NoonReportDownloadedFile file) {
        if (file == null || file.getRequest() == null) {
            return NoonReportProcessResult.mappingFailed(1, "missing_report_request");
        }
        List<String[]> records;
        try {
            records = NoonReportCsvRecords.parseRectangular(file.getContent());
        } catch (IllegalArgumentException invalidCsv) {
            return NoonReportProcessResult.mappingFailed(1, "invalid_csv");
        }
        if (records.isEmpty() || classifier.isBlank(records.get(0))) {
            return classifier.emptyOrNotReady();
        }
        return writeClassifiedRows(file, records.get(0), records.subList(1, records.size()));
    }

    private NoonReportProcessResult writeClassifiedRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows
    ) {
        int businessSkips = 0;
        Map<String, NoonOrderLineFact> facts = new LinkedHashMap<>();
        List<NoonReportRowDecision<NoonOrderLineFact>> decisions;
        try {
            decisions = classifier.classifyRows(file, header, rows);
        } catch (IllegalArgumentException invalidHeader) {
            return NoonReportProcessResult.mappingFailed(1, invalidHeader.getMessage());
        }
        for (NoonReportRowDecision<NoonOrderLineFact> decision : decisions) {
            if (decision.getKind() == NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR) {
                return NoonReportProcessResult.mappingFailed(1, "row_outside_container");
            }
            if (decision.getKind() == NoonReportRowDecision.Kind.BUSINESS_SKIP) {
                businessSkips++;
                continue;
            }
            NoonOrderLineFact fact = decision.getAccepted();
            if (facts.putIfAbsent(classifier.stableIdentity(fact), fact) != null) {
                businessSkips++;
            }
        }
        if (facts.isEmpty() && businessSkips == 0) {
            return classifier.emptyOrNotReady();
        }
        if (!facts.isEmpty()) {
            factWriter().upsertLines(List.copyOf(facts.values()));
        }
        return businessSkips == 0
                ? NoonReportProcessResult.succeeded(facts.size(), 0)
                : NoonReportProcessResult.succeededWithBusinessSkips(
                        facts.size(), businessSkips);
    }

    private NoonOrderFactWriter factWriter() {
        if (factWriter != null) {
            return factWriter;
        }
        NoonOrderFactWriter writer = factWriterProvider == null
                ? null
                : factWriterProvider.getIfAvailable();
        if (writer == null) {
            throw new IllegalStateException("Noon order fact writer is not available.");
        }
        return writer;
    }
}
