package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.List;
import org.springframework.util.StringUtils;

class ProductSnapshotTemplateFetcher {

    private final ProductAttributeTemplateService productAttributeTemplateService;

    ProductSnapshotTemplateFetcher(ProductAttributeTemplateService productAttributeTemplateService) {
        this.productAttributeTemplateService = productAttributeTemplateService;
    }

    JsonNode fetch(
            NoonSession session,
            String projectCode,
            String storeCode,
            String productFulltype,
            Long operatorUserId,
            List<String> warnings,
            StageRecorder stageRecorder
    ) {
        if (!StringUtils.hasText(productFulltype)) {
            return MissingNode.getInstance();
        }
        long stageStartedAt = System.nanoTime();
        JsonNode templateNode = productAttributeTemplateService.loadTemplate(
                session,
                projectCode,
                storeCode,
                productFulltype,
                operatorUserId,
                warnings
        );
        recordStage(stageRecorder, stageStartedAt);
        return templateNode == null ? MissingNode.getInstance() : templateNode;
    }

    private void recordStage(StageRecorder stageRecorder, long startedAt) {
        if (stageRecorder != null) {
            stageRecorder.record("fulltypeTemplate", startedAt);
        }
    }

    @FunctionalInterface
    interface StageRecorder {
        void record(String stageName, long startedAt);
    }
}
