package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.OperationHandler;
import java.util.List;
import java.util.Objects;

/** Thin Adapter from a DP-specific OperationHandler to runtime job registration. */
public final class OperationHandlerDataPullJob implements DataPullJob {

    private final OperationCode operationCode;
    private final String providerChannel;
    private final String initialStep;
    private final DataPullScopeProvider scopeProvider;
    private final OperationHandler<DataPullTask> handler;

    public OperationHandlerDataPullJob(
            OperationCode operationCode,
            String providerChannel,
            String initialStep,
            DataPullScopeProvider scopeProvider,
            OperationHandler<DataPullTask> handler
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.providerChannel = requireText(providerChannel, "providerChannel");
        this.initialStep = requireText(initialStep, "initialStep");
        this.scopeProvider = Objects.requireNonNull(scopeProvider, "scopeProvider");
        this.handler = Objects.requireNonNull(handler, "handler");
        if (handler.operationCode() != operationCode) {
            throw new IllegalArgumentException("handler operation does not match job operation");
        }
    }

    @Override
    public OperationCode operationCode() {
        return operationCode;
    }

    @Override
    public String providerChannel() {
        return providerChannel;
    }

    @Override
    public String initialStep() {
        return initialStep;
    }

    @Override
    public List<DataPullScope> listScopes() {
        return List.copyOf(Objects.requireNonNull(scopeProvider.listScopes(), "scopes"));
    }

    @Override
    public AdvanceResult advance(ExecutionContext context) {
        return handler.advance(Objects.requireNonNull(context, "context").getTask());
    }

    private static String requireText(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(name + " must be a stable non-blank value");
        }
        return nonNull;
    }
}
