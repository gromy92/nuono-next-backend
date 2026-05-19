package com.nuono.next.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Slf4jAiInvocationLogSink implements AiInvocationLogSink {

    private static final Logger logger = LoggerFactory.getLogger(Slf4jAiInvocationLogSink.class);

    @Override
    public void log(AiInvocationLogEntry entry) {
        AiUsage usage = entry.getUsage();
        logger.info(
                "ai_invocation feature={} operation={} operatorUserId={} status={} provider={} model={} responseId={} durationMs={} inputTokens={} outputTokens={} totalTokens={} promptDigest={} errorCode={}",
                entry.getFeatureCode(),
                entry.getOperationCode(),
                entry.getOperatorUserId(),
                entry.getStatus(),
                entry.getProvider(),
                entry.getModel(),
                entry.getResponseId(),
                entry.getDurationMillis(),
                usage == null ? null : usage.getInputTokens(),
                usage == null ? null : usage.getOutputTokens(),
                usage == null ? null : usage.getTotalTokens(),
                entry.getPromptDigest(),
                entry.getErrorCode()
        );
    }
}
