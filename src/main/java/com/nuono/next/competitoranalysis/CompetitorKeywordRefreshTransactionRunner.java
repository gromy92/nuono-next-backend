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
            String errorCode = firstNonBlank(outcome.getErrorCode(), DEFAULT_PROVIDER_FAILURE);
            String errorMessage = firstNonBlank(outcome.getErrorMessage(), DEFAULT_PROVIDER_FAILURE_MESSAGE);
            mapper.markKeywordProviderFailed(keyword.getId(), errorCode, errorMessage, actorUserId);
            return CompetitorKeywordRefreshResult.failure(errorCode, errorMessage);
        } catch (RuntimeException exception) {
            String message = firstNonBlank(exception.getMessage(), DEFAULT_PROVIDER_FAILURE_MESSAGE);
            String errorCode = exception instanceof NoonSearchProviderException
                    ? firstNonBlank(((NoonSearchProviderException) exception).getErrorCode(), DEFAULT_PROVIDER_FAILURE)
                    : DEFAULT_PROVIDER_FAILURE;
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
        command.setProviderStatus(firstNonBlank(outcome.getProviderStatus(), "FAILED"));
        command.setResultCount(nullToZero(outcome.getResultCount()));
        command.setSourceUrl(normalize(outcome.getSourceUrl()));
        command.setParserVersion(normalize(outcome.getParserVersion()));
        command.setProviderHttpStatus(outcome.getProviderHttpStatus());
        command.setResponseHash(normalize(outcome.getResponseHash()));
        command.setCapturedAt(outcome.getCapturedAt());
        command.setErrorCode(normalize(outcome.getErrorCode()));
        command.setErrorMessage(normalize(outcome.getErrorMessage()));
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
}
