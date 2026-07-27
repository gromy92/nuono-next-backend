package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.util.StringUtils;

final class ProductRebuildWorkflowStore {
    private final ProductManagementMapper mapper;
    private final ObjectMapper objectMapper;

    ProductRebuildWorkflowStore(
            ProductManagementMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    String claim(
            ProductPublishTaskRecord task,
            LocalDateTime staleBefore,
            Function<String, Map<String, Object>> claimedStateFactory
    ) {
        String claimToken = UUID.randomUUID().toString();
        int claimed = mapper.claimProductRebuildDeleteTaskForListing(
                task.getId(),
                task.getOwnerUserId(),
                staleBefore,
                resultJson(task, claimedStateFactory.apply(claimToken))
        );
        return claimed > 0 ? claimToken : null;
    }

    boolean renew(
            ProductPublishTaskRecord task,
            String claimToken,
            Map<String, Object> claimedState
    ) {
        return mapper.renewProductRebuildListingClaim(
                task.getId(),
                task.getOwnerUserId(),
                claimToken,
                resultJson(task, claimedState)
        ) > 0;
    }

    boolean complete(
            ProductPublishTaskRecord task,
            String claimToken,
            Map<String, Object> rebuildState
    ) {
        return mapper.completeProductRebuildListingClaim(
                task.getId(),
                task.getOwnerUserId(),
                claimToken,
                resultJson(task, rebuildState)
        ) > 0;
    }

    void record(
            ProductPublishTaskRecord task,
            Map<String, Object> rebuildState
    ) {
        mapper.updateProductRebuildDeleteTaskResult(
                task.getId(),
                task.getOwnerUserId(),
                resultJson(task, rebuildState)
        );
    }

    private String resultJson(
            ProductPublishTaskRecord task,
            Map<String, Object> rebuildState
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        JsonNode existing = readJson(task.getResultJson());
        if (existing != null && existing.isObject()) {
            root.setAll((ObjectNode) existing);
        }
        if (!StringUtils.hasText(text(root, "status"))) {
            root.put("status", task.getStatus());
        }
        root.set("rebuild", objectMapper.valueToTree(
                rebuildState == null ? Map.of() : rebuildState
        ));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "商品重建结果 JSON 序列化失败：" + exception.getMessage(),
                    exception
            );
        }
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value != null && value.isValueNode() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }
}
