package com.nuono.next.sales;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class ProductLifecycleTransitionService {

    private final ProductLifecycleStateRepository repository;

    public ProductLifecycleTransitionService(ProductLifecycleStateRepository repository) {
        this.repository = repository;
    }

    public ProductLifecycleCurrentState apply(ProductLifecycleTransitionCommand command) {
        ProductLifecycleCurrentState previous = repository.findCurrentState(command.getQuery());
        ProductLifecycleCurrentState next;
        if (command.isStockoutDistorted()) {
            next = applyStockoutHold(command, previous);
            repository.saveCurrentState(next);
            return next;
        }
        if (previous != null && "data_insufficient".equals(command.getCandidate().getCode())) {
            next = holdPreviousForDataGap(command, previous);
            repository.saveCurrentState(next);
            return next;
        }
        next = candidateState(command, previous);
        repository.saveCurrentState(next);
        if (previous != null && !previous.getLifecycleCode().equals(command.getCandidate().getCode())) {
            repository.saveHistory(history(command, previous));
        }
        return next;
    }

    private ProductLifecycleCurrentState applyStockoutHold(
            ProductLifecycleTransitionCommand command,
            ProductLifecycleCurrentState previous
    ) {
        if (previous == null) {
            return state(
                    command,
                    null,
                    "data_insufficient",
                    "数据不足",
                    "stockout_no_previous_state",
                    "首次计算遇到库存异常或断货失真，且没有上一阶段可保持。",
                    holdEvidence("stockout_no_previous_state", command)
            );
        }
        return state(
                command,
                previous.getId(),
                previous.getLifecycleCode(),
                previous.getLifecycleLabel(),
                "stockout_hold",
                "库存异常或断货可能压低销量，暂时保持上一生命周期阶段。",
                holdEvidence("stockout_hold", command)
        );
    }

    private ProductLifecycleCurrentState holdPreviousForDataGap(
            ProductLifecycleTransitionCommand command,
            ProductLifecycleCurrentState previous
    ) {
        return state(
                command,
                previous.getId(),
                previous.getLifecycleCode(),
                previous.getLifecycleLabel(),
                "data_insufficient_hold",
                "本次计算数据不足，暂时保持上一生命周期阶段。",
                holdEvidence("data_insufficient_hold", command)
        );
    }

    private ProductLifecycleCurrentState candidateState(
            ProductLifecycleTransitionCommand command,
            ProductLifecycleCurrentState previous
    ) {
        ProductLifecycleResult candidate = command.getCandidate();
        return state(
                command,
                previous == null ? null : previous.getId(),
                candidate.getCode(),
                candidate.getLabel(),
                candidate.getQualityState(),
                candidate.getExplanation(),
                candidate.getEvidenceJson()
        );
    }

    private ProductLifecycleCurrentState state(
            ProductLifecycleTransitionCommand command,
            Long id,
            String lifecycleCode,
            String lifecycleLabel,
            String qualityState,
            String explanation,
            String evidenceJson
    ) {
        ProductLifecycleStateQuery query = command.getQuery();
        return new ProductLifecycleCurrentState(
                id,
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getPartnerSku(),
                query.getSku(),
                lifecycleCode,
                lifecycleLabel,
                command.getCandidate().getRuleVersion(),
                command.getAnalysisDate(),
                command.getListingDate(),
                command.getListingDateSource(),
                qualityState,
                explanation,
                evidenceJson,
                command.getJobId(),
                LocalDateTime.now()
        );
    }

    private ProductLifecycleHistoryRecord history(
            ProductLifecycleTransitionCommand command,
            ProductLifecycleCurrentState previous
    ) {
        ProductLifecycleStateQuery query = command.getQuery();
        ProductLifecycleResult candidate = command.getCandidate();
        return new ProductLifecycleHistoryRecord(
                null,
                previous.getId(),
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getPartnerSku(),
                query.getSku(),
                previous.getLifecycleCode(),
                previous.getLifecycleLabel(),
                candidate.getCode(),
                candidate.getLabel(),
                candidate.getRuleVersion(),
                command.getAnalysisDate(),
                "lifecycle_transition",
                candidate.getEvidenceJson(),
                LocalDateTime.now()
        );
    }

    private String holdEvidence(String reason, ProductLifecycleTransitionCommand command) {
        ProductLifecycleResult candidate = command.getCandidate();
        return "{"
                + "\"reason\":\"" + reason + "\","
                + "\"candidateLifecycleCode\":\"" + candidate.getCode() + "\","
                + "\"candidateQualityState\":\"" + candidate.getQualityState() + "\","
                + "\"candidateEvidence\":" + jsonObject(candidate.getEvidenceJson())
                + "}";
    }

    private String jsonObject(String value) {
        if (value == null || value.isBlank()) {
            return "null";
        }
        return value;
    }
}
