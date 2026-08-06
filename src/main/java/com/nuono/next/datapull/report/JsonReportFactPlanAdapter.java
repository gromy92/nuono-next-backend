package com.nuono.next.datapull.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportRowDecision;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Deep generic implementation shared by Sales, Order and Finance row contracts. */
public final class JsonReportFactPlanAdapter<T> implements ReportFactPlanAdapter {
    private static final int FACT_PAYLOAD_MAX_UTF8_BYTES = 1_000_000;
    @FunctionalInterface
    public interface Classifier<T> {
        List<NoonReportRowDecision<T>> classify(
                NoonReportDownloadedFile file,
                String[] header,
                List<String[]> rows
        );
    }

    private final Consumer<String[]> headerValidator;
    private final Classifier<T> classifier;
    private final Function<T, String> identity;
    private final ObjectMapper objectMapper;

    public JsonReportFactPlanAdapter(
            Consumer<String[]> headerValidator,
            Classifier<T> classifier,
            Function<T, String> identity,
            ObjectMapper objectMapper
    ) {
        this.headerValidator = Objects.requireNonNull(headerValidator, "headerValidator");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void requireHeader(String[] header) {
        headerValidator.accept(header);
    }

    @Override
    public List<ReportPlannedRow> planRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows,
            long firstRowNumber
    ) {
        List<NoonReportRowDecision<T>> decisions = Objects.requireNonNull(
                classifier.classify(file, header, rows),
                "report row decisions"
        );
        if (decisions.size() != rows.size()) {
            throw new IllegalStateException("report classifier lost source-row accounting");
        }
        List<ReportPlannedRow> planned = new ArrayList<>(decisions.size());
        for (int index = 0; index < decisions.size(); index++) {
            long rowNumber = firstRowNumber + index;
            NoonReportRowDecision<T> decision = Objects.requireNonNull(
                    decisions.get(index),
                    "report row decision"
            );
            if (decision.getKind() == NoonReportRowDecision.Kind.BUSINESS_SKIP) {
                planned.add(ReportPlannedRow.businessSkip(rowNumber));
            } else if (decision.getKind()
                    == NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR) {
                planned.add(ReportPlannedRow.containerError(rowNumber));
            } else {
                T fact = decision.getAccepted();
                String payload = json(fact);
                planned.add(payload.getBytes(StandardCharsets.UTF_8).length
                        > FACT_PAYLOAD_MAX_UTF8_BYTES
                        ? ReportPlannedRow.containerError(rowNumber)
                        : ReportPlannedRow.accepted(
                                rowNumber,
                                Objects.requireNonNull(
                                        identity.apply(fact), "report fact identity"
                                ),
                                payload
                        ));
            }
        }
        return List.copyOf(planned);
    }

    private String json(T fact) {
        try {
            return objectMapper.writeValueAsString(fact);
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException(
                    "report fact staging payload serialization failed",
                    serializationFailure
            );
        }
    }
}
