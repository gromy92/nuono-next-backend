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
public class NoonSalesReportAdapter {
    private final NoonSalesFactWriter factWriter;
    private final ObjectProvider<NoonSalesFactWriter> factWriterProvider;
    private final NoonSalesReportRowClassifier classifier;

    public NoonSalesReportAdapter(NoonSalesFactWriter factWriter) {
        this(factWriter, null);
    }

    NoonSalesReportAdapter(NoonSalesFactWriter factWriter, Clock ignoredClock) {
        this.factWriter = factWriter;
        this.factWriterProvider = null;
        this.classifier = new NoonSalesReportRowClassifier();
    }

    @Autowired
    public NoonSalesReportAdapter(
            ObjectProvider<NoonSalesFactWriter> factWriterProvider,
            NoonSalesReportRowClassifier classifier
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
            return classifier.emptyOrNotReady("missing");
        }
        int businessSkips = 0;
        Map<String, NoonSalesDailyFact> facts = new LinkedHashMap<>();
        List<String[]> rows = records.subList(1, records.size());
        List<NoonReportRowDecision<NoonSalesDailyFact>> decisions;
        try {
            decisions = classifier.classifyRows(file, records.get(0), rows);
        } catch (IllegalArgumentException invalidHeader) {
            return NoonReportProcessResult.mappingFailed(1, "invalid_header");
        }
        for (NoonReportRowDecision<NoonSalesDailyFact> decision : decisions) {
            if (decision.getKind() == NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR) {
                return NoonReportProcessResult.mappingFailed(1, "row_outside_container");
            }
            if (decision.getKind() == NoonReportRowDecision.Kind.BUSINESS_SKIP) {
                businessSkips++;
                continue;
            }
            NoonSalesDailyFact fact = decision.getAccepted();
            if (facts.putIfAbsent(classifier.stableIdentity(fact), fact) != null) {
                businessSkips++;
            }
        }
        if (facts.isEmpty() && businessSkips == 0) {
            return classifier.emptyOrNotReady("valid");
        }
        if (!facts.isEmpty()) {
            factWriter().upsertAll(List.copyOf(facts.values()));
        }
        return businessSkips == 0
                ? NoonReportProcessResult.succeeded(facts.size(), 0)
                : NoonReportProcessResult.succeededWithBusinessSkips(
                        facts.size(), businessSkips);
    }

    private NoonSalesFactWriter factWriter() {
        if (factWriter != null) {
            return factWriter;
        }
        NoonSalesFactWriter writer = factWriterProvider == null
                ? null
                : factWriterProvider.getIfAvailable();
        if (writer == null) {
            throw new IllegalStateException("Noon sales fact writer is not available.");
        }
        return writer;
    }
}
