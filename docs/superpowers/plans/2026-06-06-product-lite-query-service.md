# Product Lite Query Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable local product light-query service for title-based product lookup without returning stock, price, offer, spec, variant, or `skuParent` fields.

**Architecture:** Introduce a narrow `ProductLiteQueryService` in the product module backed by a dedicated `ProductLiteMapper`. The mapper reads `product_master` plus optional local draft/baseline title JSON only, so store scope and title search are centralized without joining SKU, site offer, price, stock, or spec tables. Existing SKU/offer-dependent flows stay on their current queries until their contracts are redesigned.

**Tech Stack:** Spring Boot, MyBatis annotation mapper, JUnit 5, Mockito.

---

### Task 1: SQL Contract

**Files:**
- Create: `src/test/java/com/nuono/next/product/ProductLiteMapperSqlTest.java`
- Create: `src/main/java/com/nuono/next/infrastructure/mapper/ProductLiteMapper.java`
- Create: `src/main/java/com/nuono/next/product/ProductLiteRecord.java`

- [ ] **Step 1: Write the failing SQL contract test**

```java
@Test
void searchSqlUsesOnlyMasterDraftAndSnapshotTables() throws Exception {
    Method method = ProductLiteMapper.class.getMethod(
            "search",
            Long.class,
            String.class,
            String.class,
            String.class,
            int.class
    );
    String sql = String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

    assertThat(sql)
            .contains("FROM logical_store ls")
            .contains("JOIN logical_store_site lss")
            .contains("JOIN product_master pm")
            .contains("LEFT JOIN product_master_draft pmd")
            .contains("LEFT JOIN product_master_snapshot pms")
            .contains("pm.title_cache")
            .contains("$.content.titleEn")
            .contains("$.content.titleCn")
            .doesNotContain("product_variant")
            .doesNotContain("product_site_offer")
            .doesNotContain("sku_parent AS")
            .doesNotContain("child_sku")
            .doesNotContain("offer_code")
            .doesNotContain("sale_price")
            .doesNotContain("stock")
            .doesNotContain("variant_id")
            .doesNotContain("effective_source");

    new XMLLanguageDriver().createSqlSource(new Configuration(), String.join("\n", method.getAnnotation(Select.class).value()), Object.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ProductLiteMapperSqlTest test`
Expected: FAIL because `ProductLiteMapper` does not exist.

- [ ] **Step 3: Add minimal mapper and record**

Create `ProductLiteRecord` with only: `productMasterId`, `storeCode`, `siteCode`, `title`, `titleCn`, `titleEn`, `brand`, `imageUrl`, `productFulltype`, `sourceType`.

Create `ProductLiteMapper#search(ownerUserId, storeCode, siteCode, titleKeyword, limit)` using `logical_store`, `logical_store_site`, `product_master`, `product_master_draft`, and latest baseline `product_master_snapshot`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ProductLiteMapperSqlTest test`
Expected: PASS.

### Task 2: Service Contract

**Files:**
- Create: `src/test/java/com/nuono/next/product/ProductLiteQueryServiceTest.java`
- Create: `src/main/java/com/nuono/next/product/ProductLiteQueryService.java`
- Create: `src/main/java/com/nuono/next/product/ProductLiteQuery.java`
- Create: `src/main/java/com/nuono/next/product/ProductLiteView.java`

- [ ] **Step 1: Write failing service tests**

Test that the service trims store/site/title keyword, uppercases store/site, caps `limit` to 50, defaults invalid `limit` to 20, resolves owner from `BusinessAccessContext#resolveOwnerUserIdForStore`, and maps only light view fields.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ProductLiteQueryServiceTest test`
Expected: FAIL because the service classes do not exist.

- [ ] **Step 3: Add minimal service implementation**

Implement `search(BusinessAccessContext context, ProductLiteQuery query)` and throw `ResponseStatusException` with `BAD_REQUEST` or `FORBIDDEN` for missing store or invalid scope.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ProductLiteQueryServiceTest test`
Expected: PASS.

### Task 3: HTTP API

**Files:**
- Create: `src/test/java/com/nuono/next/product/ProductLiteControllerTest.java`
- Create: `src/main/java/com/nuono/next/product/ProductLiteController.java`

- [ ] **Step 1: Write failing controller tests**

Test route mapping `/api/products/lite`, PRODUCT_MASTER store access resolution, and request parameter mapping for `storeCode`, `siteCode`, `titleKeyword`, and `limit`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ProductLiteControllerTest test`
Expected: FAIL because controller does not exist.

- [ ] **Step 3: Add controller**

Implement `GET /api/products/lite` returning `List<ProductLiteView>`, using `BusinessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, storeCode)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ProductLiteControllerTest test`
Expected: PASS.

### Task 4: Safe Replacement Review

**Files:**
- Inspect: `src/main/java/com/nuono/next/competitoranalysis/CompetitorAnalysisService.java`
- Inspect: `src/main/java/com/nuono/next/infrastructure/mapper/Ali1688HistoricalOrderMapper.java`
- Inspect: `src/main/java/com/nuono/next/infrastructure/mapper/NoonFinanceTransactionMapper.java`
- Inspect: `src/main/java/com/nuono/next/infrastructure/mapper/SalesDataMapper.java`

- [ ] **Step 1: Classify current product joins**

Keep queries that require SKU, variant, offer, sales, finance, group, or link state on their current mapper because they do not match the light-query contract.

- [ ] **Step 2: Record replacement decision**

Add a short decision to `docs/current-focus.md` or final notes: first version creates the reusable service and API; no existing SKU/offer-dependent call is replaced because that would change behavior.

### Task 5: Verification

**Files:**
- Compile all changed Java sources.

- [ ] **Step 1: Run focused tests**

Run: `mvn -q -Dtest=ProductLiteMapperSqlTest,ProductLiteQueryServiceTest,ProductLiteControllerTest test`
Expected: PASS.

- [ ] **Step 2: Run compile**

Run: `mvn -q -DskipTests compile`
Expected: PASS.

- [ ] **Step 3: Commit**

Run:
```bash
git status --short
git add docs/superpowers/plans/2026-06-06-product-lite-query-service.md src/main/java/com/nuono/next/product src/main/java/com/nuono/next/infrastructure/mapper src/test/java/com/nuono/next/product
git commit -m "feat: add product lite query service"
```
