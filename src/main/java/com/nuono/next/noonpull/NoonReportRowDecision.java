package com.nuono.next.noonpull;

import java.util.Objects;

/** Typed outcome for one row inside an already validated report container. */
public final class NoonReportRowDecision<T> {
    public enum Kind {
        ACCEPT,
        BUSINESS_SKIP,
        CONTAINER_CONTRACT_ERROR
    }

    private final Kind kind;
    private final T accepted;

    private NoonReportRowDecision(Kind kind, T accepted) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.accepted = accepted;
    }

    public static <T> NoonReportRowDecision<T> accept(T value) {
        return new NoonReportRowDecision<>(Kind.ACCEPT, Objects.requireNonNull(value, "value"));
    }

    public static <T> NoonReportRowDecision<T> businessSkip() {
        return new NoonReportRowDecision<>(Kind.BUSINESS_SKIP, null);
    }

    public static <T> NoonReportRowDecision<T> containerContractError() {
        return new NoonReportRowDecision<>(Kind.CONTAINER_CONTRACT_ERROR, null);
    }

    public Kind getKind() {
        return kind;
    }

    public T getAccepted() {
        if (kind != Kind.ACCEPT) {
            throw new IllegalStateException("only an accepted row carries a fact");
        }
        return accepted;
    }
}
