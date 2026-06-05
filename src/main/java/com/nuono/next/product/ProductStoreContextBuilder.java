package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductStoreContextBuilder {

    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Clock clock;

    ProductStoreContextBuilder() {
        this(Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    ProductStoreContextBuilder(Clock clock) {
        this.clock = clock == null ? Clock.system(ZoneId.of("Asia/Shanghai")) : clock;
    }

    Map<String, Object> build(
            StoreSyncOwnerContext owner,
            StoreSyncStoreRecord store,
            String noonUser,
            JsonNode whoamiNode,
            String projectCode,
            String referenceStoreCode,
            String referenceSite,
            int projectSiteCount
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        putIfNotNull(context, "ownerUserId", owner == null ? null : owner.getId());
        putIfNotBlank(context, "ownerName", resolveOwnerName(owner));
        putIfNotBlank(context, "accountNo", owner == null ? null : owner.getAccountNo());
        putIfNotBlank(context, "projectName", store == null ? null : store.getProjectName());
        putIfNotBlank(context, "projectCode", projectCode);
        putIfNotBlank(context, "storeCode", referenceStoreCode);
        putIfNotBlank(context, "site", referenceSite);
        putIfNotNull(context, "projectSiteCount", projectSiteCount);
        putIfNotBlank(context, "noonUser", noonUser);
        putIfNotBlank(context, "whoamiEmail", text(whoamiNode, "email"));
        putIfNotBlank(context, "whoamiRole", text(whoamiNode, "idp_role"));
        putIfNotBlank(context, "fetchedAt", ZonedDateTime.now(clock).format(FETCH_TIME_FORMATTER));
        return context;
    }

    private String resolveOwnerName(StoreSyncOwnerContext owner) {
        if (owner == null) {
            return "当前老板";
        }
        if (StringUtils.hasText(owner.getRealName())) {
            return owner.getRealName();
        }
        if (StringUtils.hasText(owner.getAccountNo())) {
            return owner.getAccountNo();
        }
        return "当前老板";
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.isValueNode() ? valueNode.asText() : valueNode.toString();
        return StringUtils.hasText(value) ? value : null;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
