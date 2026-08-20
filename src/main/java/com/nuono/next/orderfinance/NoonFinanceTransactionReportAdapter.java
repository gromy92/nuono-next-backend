package com.nuono.next.orderfinance;

import com.nuono.next.noonpull.NoonReportCsvRecords;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportProcessResult;
import com.nuono.next.noonpull.NoonReportRowDecision;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Legacy direct-write adapter. The unified DP Runtime uses the classifier directly. */
@Service
public class NoonFinanceTransactionReportAdapter {
    private final NoonFinanceTransactionFactWriter factWriter;
    private final NoonFinanceTransactionReportRowClassifier classifier;

    public NoonFinanceTransactionReportAdapter(NoonFinanceTransactionFactWriter factWriter) {
        this(factWriter, new NoonFinanceTransactionReportRowClassifier());
    }

    @Autowired
    public NoonFinanceTransactionReportAdapter(
            NoonFinanceTransactionFactWriter factWriter,
            NoonFinanceTransactionReportRowClassifier classifier
    ) {
        this.factWriter = factWriter;
        this.classifier = classifier;
    }

    public NoonReportProcessResult process(NoonReportDownloadedFile file) {
        if (file == null || file.getRequest() == null) {
            return NoonReportProcessResult.mappingFailed(1, "missing_report_request");
        }
        List<String[]> rows;
        try {
            rows = NoonReportCsvRecords.parseRectangular(file.getContent());
        } catch (RuntimeException invalidCsv) {
            return NoonReportProcessResult.mappingFailed(1, "invalid_csv");
        }
        if (rows.isEmpty() || rows.get(0).length == 0) {
            return NoonReportProcessResult.emptyReport();
        }
        int businessSkips = 0;
        Map<String, NoonFinanceTransactionFact> facts = new LinkedHashMap<>();
        List<String[]> body = rows.subList(1, rows.size());
        List<NoonReportRowDecision<NoonFinanceTransactionFact>> decisions;
        try {
            decisions = classifier.classifyRows(file, rows.get(0), body);
        } catch (IllegalArgumentException invalidHeader) {
            return NoonReportProcessResult.mappingFailed(1, invalidHeader.getMessage());
        }
        for (NoonReportRowDecision<NoonFinanceTransactionFact> decision : decisions) {
            if (decision.getKind() == NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR) {
                return NoonReportProcessResult.mappingFailed(1, "row_outside_container");
            }
            if (decision.getKind() == NoonReportRowDecision.Kind.BUSINESS_SKIP) {
                businessSkips++;
                continue;
            }
            NoonFinanceTransactionFact fact = decision.getAccepted();
            if (facts.putIfAbsent(classifier.stableIdentity(fact), fact) != null) {
                businessSkips++;
            }
        }
        if (facts.isEmpty() && businessSkips == 0) {
            return NoonReportProcessResult.emptyReport();
        }
        if (!facts.isEmpty()) {
            factWriter.upsertAll(List.copyOf(facts.values()));
        }
        return businessSkips == 0
                ? NoonReportProcessResult.succeeded(facts.size(), 0)
                : NoonReportProcessResult.succeededWithBusinessSkips(
                        facts.size(), businessSkips);
    }
}
