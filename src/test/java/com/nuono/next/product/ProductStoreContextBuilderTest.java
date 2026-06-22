package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductStoreContextBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildStoreContextWithWhoamiAndFixedFetchedAt() throws Exception {
        ProductStoreContextBuilder builder = new ProductStoreContextBuilder(fixedClock());
        StoreSyncOwnerContext owner = owner(307L, "boss-001", "Alice");
        StoreSyncStoreRecord store = store("xingyao");
        JsonNode whoamiNode = objectMapper.readTree("{\"email\":\"alice@example.com\",\"idp_role\":\"admin\"}");

        Map<String, Object> context = builder.build(
                owner,
                store,
                "alice@noon",
                whoamiNode,
                "PRJ108065",
                "STR245027-NSA",
                "SA",
                2
        );

        assertEquals(307L, context.get("ownerUserId"));
        assertEquals("Alice", context.get("ownerName"));
        assertEquals("boss-001", context.get("accountNo"));
        assertEquals("xingyao", context.get("projectName"));
        assertEquals("PRJ108065", context.get("projectCode"));
        assertEquals("STR245027-NSA", context.get("storeCode"));
        assertEquals("SA", context.get("site"));
        assertEquals(2, context.get("projectSiteCount"));
        assertEquals("alice@noon", context.get("noonUser"));
        assertEquals("alice@example.com", context.get("whoamiEmail"));
        assertEquals("admin", context.get("whoamiRole"));
        assertEquals("2026-06-05 18:20:30", context.get("fetchedAt"));
    }

    @Test
    void shouldFallbackOwnerNameAndSkipBlankValues() {
        ProductStoreContextBuilder builder = new ProductStoreContextBuilder(fixedClock());
        StoreSyncOwnerContext owner = owner(308L, "boss-002", " ");
        StoreSyncStoreRecord store = store(" ");

        Map<String, Object> context = builder.build(
                owner,
                store,
                " ",
                objectMapper.missingNode(),
                " ",
                "STR245027-NAE",
                " ",
                1
        );

        assertEquals("boss-002", context.get("ownerName"));
        assertEquals("boss-002", context.get("accountNo"));
        assertEquals("STR245027-NAE", context.get("storeCode"));
        assertEquals(1, context.get("projectSiteCount"));
        assertEquals("2026-06-05 18:20:30", context.get("fetchedAt"));
        assertFalse(context.containsKey("projectName"));
        assertFalse(context.containsKey("projectCode"));
        assertFalse(context.containsKey("site"));
        assertFalse(context.containsKey("noonUser"));
        assertFalse(context.containsKey("whoamiEmail"));
        assertFalse(context.containsKey("whoamiRole"));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-05T10:20:30Z"), ZoneId.of("Asia/Shanghai"));
    }

    private StoreSyncOwnerContext owner(Long id, String accountNo, String realName) {
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setId(id);
        owner.setAccountNo(accountNo);
        owner.setRealName(realName);
        return owner;
    }

    private StoreSyncStoreRecord store(String projectName) {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectName(projectName);
        return store;
    }
}
