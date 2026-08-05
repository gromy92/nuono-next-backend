package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class TestDataPullJob implements DataPullJob {

    private final OperationCode operationCode;
    private final String providerChannel;
    private final List<DataPullScope> scopes;
    private final Function<ExecutionContext, AdvanceResult> advance;

    TestDataPullJob(
            OperationCode operationCode,
            String providerChannel,
            List<DataPullScope> scopes,
            Function<ExecutionContext, AdvanceResult> advance
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.providerChannel = Objects.requireNonNull(providerChannel, "providerChannel");
        this.scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
        this.advance = Objects.requireNonNull(advance, "advance");
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
        return "FETCH";
    }

    @Override
    public List<DataPullScope> listScopes() {
        return scopes;
    }

    @Override
    public AdvanceResult advance(ExecutionContext context) {
        return advance.apply(context);
    }
}
