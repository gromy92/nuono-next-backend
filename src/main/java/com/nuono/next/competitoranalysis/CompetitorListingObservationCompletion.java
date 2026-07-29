package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorListingObservationMapper;
import com.nuono.next.noon.NoonShanghaiBusinessTime;

final class CompetitorListingObservationCompletion {
    private final CompetitorListingObservationMapper mapper;

    CompetitorListingObservationCompletion(
            CompetitorListingObservationMapper mapper
    ) {
        this.mapper = mapper;
    }

    void found(
            Long observationId,
            String leaseToken,
            NoonProductDetail detail,
            Long actorUserId
    ) {
        CompetitorListingObservationCommand command =
                CompetitorListingObservationSupport.completionCommand(
                        observationId,
                        leaseToken,
                        detail,
                        actorUserId
                );
        command.setCanonicalUrl(
                CompetitorListingObservationSupport.normalizeText(
                        detail.getDetailUrl()
                )
        );
        command.setTitleEn(
                CompetitorListingObservationSupport.normalizeText(
                        detail.getTitleEn()
                )
        );
        command.setTitleAr(
                CompetitorListingObservationSupport.normalizeText(
                        detail.getTitleAr()
                )
        );
        command.setImageUrl(
                CompetitorListingObservationSupport.normalizeText(
                        CompetitorListingObservationSupport.firstNonBlank(
                                detail.getMainImageUrlNormalized(),
                                detail.getMainImageUrlRaw()
                        )
                )
        );
        command.setPriceAmount(detail.getPriceAmount());
        command.setCurrencyCode(
                CompetitorListingObservationSupport.normalizeText(
                        detail.getCurrencyCode()
                )
        );
        command.setTagsJson(
                CompetitorListingObservationSupport.normalizeText(
                        CompetitorListingObservationSupport.firstNonBlank(
                                detail.getBadgesJson(),
                                detail.getLogisticsTagsJson()
                        )
                )
        );
        if (mapper.completeExactFound(command) != 1) {
            throw new IllegalStateException(
                    "竞品列表观察写入租约已失效。"
            );
        }
    }

    void failure(
            Long observationId,
            String leaseToken,
            RuntimeException error,
            Long actorUserId,
            boolean notFound
    ) {
        CompetitorListingObservationCommand command =
                CompetitorListingObservationSupport.completionCommand(
                        observationId,
                        leaseToken,
                        null,
                        actorUserId
                );
        if (error instanceof NoonSearchProviderException) {
            NoonSearchProviderException provider =
                    (NoonSearchProviderException) error;
            command.setLastErrorCode(provider.getErrorCode());
            command.setProviderHttpStatus(
                    provider.getProviderHttpStatus()
            );
            command.setSourceUrl(
                    CompetitorListingObservationSupport.normalizeText(
                            provider.getSourceUrl()
                    )
            );
            command.setResponseHash(
                    CompetitorListingObservationSupport.normalizeText(
                            provider.getResponseHash()
                    )
            );
        } else {
            command.setLastErrorCode("LIST_REFRESH_FAILED");
        }
        command.setLastErrorMessage(
                CompetitorListingObservationSupport.shrink(
                        error == null ? null : error.getMessage(),
                        1024
                )
        );
        command.setCapturedAt(NoonShanghaiBusinessTime.now());
        if (notFound) {
            mapper.completeExactNotFound(command);
        } else {
            mapper.completeExactFailure(command);
        }
    }
}
