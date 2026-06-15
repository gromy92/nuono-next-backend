package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompetitorKeywordRefreshTransactionRunner {
    private static final String DEFAULT_PROVIDER_FAILURE = "COMPETITOR_PROVIDER_FAILED";
    private static final String DEFAULT_PROVIDER_FAILURE_MESSAGE = "关键词刷新失败。";
    private static final int MAX_PROVIDER_STATUS_LENGTH = 32;
    private static final int MAX_SOURCE_URL_LENGTH = 1000;
    private static final int MAX_PARSER_VERSION_LENGTH = 80;
    private static final int MAX_ERROR_CODE_LENGTH = 128;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final CompetitorAnalysisMapper mapper;
    private final CompetitorKeywordRefreshRunner runner;

    public CompetitorKeywordRefreshTransactionRunner(
            CompetitorAnalysisMapper mapper,
            CompetitorKeywordRefreshRunner runner
    ) {
        this.mapper = mapper;
        this.runner = runner == null ? new NoopCompetitorKeywordRefreshRunner() : runner;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompetitorKeywordRefreshResult runKeyword(
            Long searchRunId,
            CompetitorWatchProductRow watchProduct,
            CompetitorKeywordRow keyword,
            Long actorUserId
    ) {
        Long keywordRunId = mapper.nextKeywordRunId();
        try {
            CompetitorKeywordRefreshOutcome outcome = runner.refresh(CompetitorKeywordRefreshContext.builder()
                    .searchRunId(searchRunId)
                    .keywordRunId(keywordRunId)
                    .watchProduct(watchProduct)
                    .keyword(keyword)
                    .actorUserId(actorUserId)
                    .build());
            if (outcome == null) {
                outcome = CompetitorKeywordRefreshOutcome.success(0);
            }
            mapper.insertKeywordRun(buildKeywordRun(keywordRunId, searchRunId, keyword, outcome, actorUserId));
            if (outcome.isSuccess()) {
                mapper.markKeywordProviderSucceeded(
                        keyword.getId(),
                        firstNonBlank(outcome.getProviderStatus(), "SUCCESS"),
                        actorUserId
                );
                return CompetitorKeywordRefreshResult.success(
                        nullToZero(outcome.getCandidateUpsertedCount()),
                        nullToZero(outcome.getRankFactWrittenCount())
                );
            }
            String errorCode = truncate(
                    firstNonBlank(outcome.getErrorCode(), DEFAULT_PROVIDER_FAILURE),
                    MAX_ERROR_CODE_LENGTH
            );
            String errorMessage = truncate(
                    firstNonBlank(outcome.getErrorMessage(), DEFAULT_PROVIDER_FAILURE_MESSAGE),
                    MAX_ERROR_MESSAGE_LENGTH
            );
            mapper.markKeywordProviderFailed(keyword.getId(), errorCode, errorMessage, actorUserId);
            return CompetitorKeywordRefreshResult.failure(errorCode, errorMessage);
        } catch (RuntimeException exception) {
            String message = truncate(
                    firstNonBlank(exception.getMessage(), DEFAULT_PROVIDER_FAILURE_MESSAGE),
                    MAX_ERROR_MESSAGE_LENGTH
            );
            String errorCode = exception instanceof NoonSearchProviderException
                    ? firstNonBlank(((NoonSearchProviderException) exception).getErrorCode(), DEFAULT_PROVIDER_FAILURE)
                    : DEFAULT_PROVIDER_FAILURE;
            errorCode = truncate(errorCode, MAX_ERROR_CODE_LENGTH);
            CompetitorKeywordRefreshOutcome failed = CompetitorKeywordRefreshOutcome.failure(
                    errorCode,
                    message
            );
            if (exception instanceof NoonSearchProviderException) {
                NoonSearchProviderException providerException = (NoonSearchProviderException) exception;
                failed.setSourceUrl(providerException.getSourceUrl());
                failed.setProviderHttpStatus(providerException.getProviderHttpStatus());
                failed.setResponseHash(providerException.getResponseHash());
            }
            mapper.insertKeywordRun(buildKeywordRun(keywordRunId, searchRunId, keyword, failed, actorUserId));
            mapper.markKeywordProviderFailed(keyword.getId(), errorCode, message, actorUserId);
            return CompetitorKeywordRefreshResult.failure(errorCode, message);
        }
    }

    private CompetitorKeywordRunInsertCommand buildKeywordRun(
            Long keywordRunId,
            Long searchRunId,
            CompetitorKeywordRow keyword,
            CompetitorKeywordRefreshOutcome outcome,
            Long actorUserId
    ) {
        CompetitorKeywordRunInsertCommand command = new CompetitorKeywordRunInsertCommand();
        command.setId(keywordRunId);
        command.setSearchRunId(searchRunId);
        command.setKeywordId(keyword.getId());
        command.setKeywordSnapshot(keyword.getKeyword());
        command.setLocaleSnapshot(keyword.getLocale());
        command.setProviderStatus(truncate(firstNonBlank(outcome.getProviderStatus(), "FAILED"), MAX_PROVIDER_STATUS_LENGTH));
        command.setResultCount(nullToZero(outcome.getResultCount()));
        command.setRequestedResultLimit(outcome.getRequestedResultLimit());
        command.setSourceUrl(truncate(outcome.getSourceUrl(), MAX_SOURCE_URL_LENGTH));
        command.setParserVersion(truncate(outcome.getParserVersion(), MAX_PARSER_VERSION_LENGTH));
        command.setProviderHttpStatus(outcome.getProviderHttpStatus());
        command.setResponseHash(truncate(outcome.getResponseHash(), MAX_ERROR_CODE_LENGTH));
        command.setCapturedAt(outcome.getCapturedAt());
        command.setErrorCode(truncate(outcome.getErrorCode(), MAX_ERROR_CODE_LENGTH));
        command.setErrorMessage(truncate(outcome.getErrorMessage(), MAX_ERROR_MESSAGE_LENGTH));
        command.setActorUserId(actorUserId);
        return command;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
