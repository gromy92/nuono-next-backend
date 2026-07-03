package com.nuono.next.productlogisticscost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostExceptionRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostExceptionView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.BatchCategoryAssignmentResult;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.BatchCategoryAssignmentCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.CategoryAssignmentItem;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualCurrentQuoteCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualRateCardCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ProductMatchRow;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ExtendWith(MockitoExtension.class)
class ProductLogisticsCostControllerTest {

    @Mock
    private ProductLogisticsCostLedgerService service;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private ProductLogisticsCostMapper mapper;

    private ProductLogisticsCostController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductLogisticsCostController(service, businessAccessResolver);
    }

    @Test
    void shouldExposeReadRoutesUnderProductLogisticsCosts() throws NoSuchMethodException {
        RequestMapping baseMapping = ProductLogisticsCostController.class.getAnnotation(RequestMapping.class);
        assertThat(baseMapping.value()).containsExactly("/api/product-logistics-costs");

        Method currentCosts = ProductLogisticsCostController.class.getMethod(
                "currentCosts",
                Long.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                HttpServletRequest.class
        );
        assertThat(currentCosts.getAnnotation(GetMapping.class).value()).containsExactly("/current");

        Method history = ProductLogisticsCostController.class.getMethod(
                "history",
                Long.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                HttpServletRequest.class
        );
        assertThat(history.getAnnotation(GetMapping.class).value()).containsExactly("/history");

        Method exceptions = ProductLogisticsCostController.class.getMethod(
                "openExceptions",
                Integer.class,
                HttpServletRequest.class
        );
        assertThat(exceptions.getAnnotation(GetMapping.class).value()).containsExactly("/exceptions");

        Method manualCurrentQuote = ProductLogisticsCostController.class.getMethod(
                "manualCurrentQuote",
                ManualCurrentQuoteCommand.class,
                HttpServletRequest.class
        );
        assertThat(manualCurrentQuote.getAnnotation(PostMapping.class).value()).containsExactly("/current/manual");

        Method batchAssignCategories = ProductLogisticsCostController.class.getMethod(
                "batchAssignCategories",
                BatchCategoryAssignmentCommand.class,
                HttpServletRequest.class
        );
        assertThat(batchAssignCategories.getAnnotation(PostMapping.class).value()).containsExactly("/current/categories/batch");

        Method rateCards = ProductLogisticsCostController.class.getMethod(
                "rateCards",
                String.class,
                String.class,
                String.class,
                HttpServletRequest.class
        );
        assertThat(rateCards.getAnnotation(GetMapping.class).value()).containsExactly("/rate-cards");

        Method manualRateCard = ProductLogisticsCostController.class.getMethod(
                "manualRateCard",
                ManualRateCardCommand.class,
                HttpServletRequest.class
        );
        assertThat(manualRateCard.getAnnotation(PostMapping.class).value()).containsExactly("/rate-cards/manual");
    }

    @Test
    void shouldReadCurrentCostsUsingBackendOwnerContextAndForwardFilters() {
        BusinessAccessContext context = context();
        CurrentCostView view = new CurrentCostView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(service.currentCosts(
                10002L,
                88001L,
                "STR69486-NSA",
                77001L,
                " SGGRB148 ",
                " sa ",
                " yitong ",
                " sea ",
                25
        )).thenReturn(view);

        CurrentCostView result = controller.currentCosts(
                88001L,
                "STR69486-NSA",
                77001L,
                " SGGRB148 ",
                " sa ",
                " yitong ",
                " sea ",
                25,
                request
        );

        assertSame(view, result);
        verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }

    @Test
    void shouldReadHistoryUsingBackendOwnerContextAndForwardFilters() {
        BusinessAccessContext context = context();
        CostHistoryView view = new CostHistoryView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(service.history(
                10002L,
                88001L,
                "STR69486-NSA",
                77001L,
                " SGGRB148 ",
                " SA ",
                " YITONG ",
                " SEA ",
                75
        )).thenReturn(view);

        CostHistoryView result = controller.history(
                88001L,
                "STR69486-NSA",
                77001L,
                " SGGRB148 ",
                " SA ",
                " YITONG ",
                " SEA ",
                75,
                request
        );

        assertSame(view, result);
        verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }

    @Test
    void shouldReadOpenExceptionsUsingBackendOwnerContextAndForwardLimit() {
        BusinessAccessContext context = context();
        CostExceptionView view = new CostExceptionView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(service.openExceptions(10002L, 20)).thenReturn(view);

        CostExceptionView result = controller.openExceptions(20, request);

        assertSame(view, result);
        verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }

    @Test
    void shouldBatchAssignCategoryUsingBackendOwnerContext() {
        BusinessAccessContext context = context();
        BatchCategoryAssignmentCommand command = new BatchCategoryAssignmentCommand();
        BatchCategoryAssignmentResult view = new BatchCategoryAssignmentResult();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(service.batchAssignCategories(10002L, 90001L, command)).thenReturn(view);

        BatchCategoryAssignmentResult result = controller.batchAssignCategories(command, request);

        assertSame(view, result);
        verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }

    @Test
    void shouldNormalizeCurrentCostReadFiltersAndDefaultLimitInService() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        CurrentCostRow row = new CurrentCostRow();
        when(mapper.listCurrentCosts(10002L, 88001L, 77001L, "SGGRB148", "SA", "ET", "SEA", 50))
                .thenReturn(List.of(row));

        CurrentCostView result = ledgerService.currentCosts(
                10002L,
                88001L,
                null,
                77001L,
                " SGGRB148 ",
                " sa ",
                " yitong ",
                " sea ",
                0
        );

        assertThat(result.items).containsExactly(row);
    }

    @Test
    void shouldCapHistoryReadLimitAndCleanBlankFiltersInService() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        CostHistoryRow row = new CostHistoryRow();
        when(mapper.listHistory(10002L, null, null, null, null, null, null, 5000))
                .thenReturn(List.of(row));

        CostHistoryView result = ledgerService.history(
                10002L,
                null,
                null,
                null,
                " ",
                " ",
                " ",
                " ",
                5000
        );

        assertThat(result.items).containsExactly(row);
    }

    @Test
    void shouldResolveStoreCodeBeforeReadingCurrentCosts() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        CurrentCostRow row = new CurrentCostRow();
        when(mapper.selectLogicalStoreIdByStoreCode(10002L, "STR69486-NSA")).thenReturn(50003L);
        when(mapper.listCurrentCosts(10002L, 50003L, null, null, "SA", "YITE", "SEA", 50))
                .thenReturn(List.of(row));

        CurrentCostView result = ledgerService.currentCosts(
                10002L,
                null,
                "STR69486-NSA",
                null,
                null,
                "SA",
                "YITE",
                "SEA",
                null
        );

        assertThat(result.items).containsExactly(row);
    }

    @Test
    void shouldDefaultOpenExceptionReadLimitInService() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        CostExceptionRow row = new CostExceptionRow();
        when(mapper.listOpenExceptions(10002L, 50)).thenReturn(List.of(row));

        CostExceptionView result = ledgerService.openExceptions(10002L, null);

        assertThat(result.items).containsExactly(row);
    }

    @Test
    void shouldReadRateCardsUsingBackendOwnerContextAndForwardRouteFilters() {
        BusinessAccessContext context = context();
        RateCardView view = new RateCardView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(service.rateCards(10002L, " ae ", " yitong ", " sea ")).thenReturn(view);

        RateCardView result = controller.rateCards(" ae ", " yitong ", " sea ", request);

        assertSame(view, result);
        verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }

    @Test
    void shouldNormalizeRouteFiltersWhenReadingRateCards() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        RateCardRow row = new RateCardRow();
        when(mapper.listRateCards(10002L, "AE", "ET", "SEA")).thenReturn(List.of(row));

        RateCardView result = ledgerService.rateCards(10002L, " ae ", " yitong ", " sea ");

        assertThat(result.items).containsExactly(row);
    }

    @Test
    void shouldUpsertManualRouteCategoryRateCard() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        ManualRateCardCommand command = new ManualRateCardCommand();
        command.siteCode = "ae";
        command.forwarderCode = "yitong";
        command.forwarderName = "易通";
        command.transportMode = "sea";
        command.cargoCategoryCode = "B";
        command.cargoCategoryName = "B类别运费";
        command.chargeUnit = "cbm";
        command.unitCostCny = new BigDecimal("1660.00");
        command.sourceReference = "ET易通天下物流报价-0604";
        command.remark = "阿联酋最新海运 B 类";

        when(mapper.nextProductLogisticsRateCardId()).thenReturn(430001L);

        RateCardRow result = ledgerService.manualRateCard(307L, 90004L, command);

        assertThat(result.id).isEqualTo(430001L);
        assertThat(result.ownerUserId).isEqualTo(307L);
        assertThat(result.siteCode).isEqualTo("AE");
        assertThat(result.forwarderCode).isEqualTo("ET");
        assertThat(result.forwarderName).isEqualTo("易通");
        assertThat(result.transportMode).isEqualTo("SEA");
        assertThat(result.feeType).isEqualTo("HEADHAUL");
        assertThat(result.cargoCategoryCode).isEqualTo("B");
        assertThat(result.cargoCategoryName).isEqualTo("B类别运费");
        assertThat(result.chargeUnit).isEqualTo("CBM");
        assertThat(result.unitCostCny).isEqualByComparingTo("1660.00");
        assertThat(result.sourceType).isEqualTo("MANUAL_RATE_CARD");
        org.mockito.ArgumentCaptor<RateCardRow> rateCardCaptor =
                org.mockito.ArgumentCaptor.forClass(RateCardRow.class);
        verify(mapper).upsertRateCard(rateCardCaptor.capture(), eq(90004L));
        assertThat(rateCardCaptor.getValue().evidenceJson).contains("ET易通天下物流报价-0604");
    }

    @Test
    void shouldAppendManualCurrentQuoteHistoryBeforeProjectingCurrentCost() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        ManualCurrentQuoteCommand command = new ManualCurrentQuoteCommand();
        command.storeCode = "STR108065-NAE";
        command.partnerSku = "PAPERSAYSB003";
        command.siteCode = "ae";
        command.forwarderCode = "yitong";
        command.forwarderName = "易通";
        command.transportMode = "sea";
        command.cargoCategoryCode = "B";
        command.cargoCategoryName = "B类别运费";
        command.chargeUnit = "CBM";
        command.unitCostCny = new BigDecimal("1150.00");
        command.remark = "运营补当前价";

        ProductMatchRow product = new ProductMatchRow();
        product.logicalStoreId = 50005L;
        product.productMasterId = 52864L;
        product.productVariantId = 53810L;
        product.partnerSku = "PAPERSAYSB003";
        product.barcode = "PAPERSAYSB003";
        product.siteCode = "AE";

        when(mapper.selectProductMatches(307L, "STR108065-NAE", "PAPERSAYSB003", "AE"))
                .thenReturn(List.of(product));
        when(mapper.nextProductLogisticsCostHistoryId()).thenReturn(410001L);
        when(mapper.nextProductLogisticsCurrentCostId()).thenReturn(420001L);
        when(mapper.insertCostHistory(org.mockito.ArgumentMatchers.any(CostHistoryRow.class), eq(90004L)))
                .thenAnswer(invocation -> {
                    CostHistoryRow row = invocation.getArgument(0);
                    row.id = 410001L;
                    return 1;
                });

        CurrentCostRow result = ledgerService.manualCurrentQuote(307L, 90004L, command);

        assertThat(result.id).isEqualTo(420001L);
        assertThat(result.currentHistoryId).isEqualTo(410001L);
        assertThat(result.sourceType).isEqualTo("MANUAL_CURRENT_QUOTE");
        assertThat(result.cargoCategoryName).isEqualTo("B类别运费");
        assertThat(result.unitCostCny).isEqualByComparingTo("1150.00");
        org.mockito.ArgumentCaptor<CostHistoryRow> historyCaptor =
                org.mockito.ArgumentCaptor.forClass(CostHistoryRow.class);
        org.mockito.ArgumentCaptor<CurrentCostRow> currentCaptor =
                org.mockito.ArgumentCaptor.forClass(CurrentCostRow.class);
        verify(mapper).insertCostHistory(historyCaptor.capture(), eq(90004L));
        verify(mapper).upsertCurrentCost(currentCaptor.capture(), eq(90004L));
        assertThat(historyCaptor.getValue().sourceType).isEqualTo("MANUAL_CURRENT_QUOTE");
        assertThat(historyCaptor.getValue().idempotencyKey).contains("MANUAL_CURRENT_QUOTE:307:50005:PAPERSAYSB003:AE:ET:SEA");
        assertThat(currentCaptor.getValue().currentHistoryId).isEqualTo(410001L);
    }

    @Test
    void shouldPersistResolvedPartnerSkuWhenInputMatchesProductBarcode() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        ManualCurrentQuoteCommand command = new ManualCurrentQuoteCommand();
        command.storeCode = "STR108065-NAE";
        command.partnerSku = "PAPERSAYSB027";
        command.siteCode = "AE";
        command.forwarderCode = "ET";
        command.forwarderName = "易通";
        command.transportMode = "SEA";
        command.cargoCategoryCode = "A";
        command.cargoCategoryName = "A类别运费";
        command.chargeUnit = "CBM";
        command.unitCostCny = new BigDecimal("1200.00");

        ProductMatchRow product = new ProductMatchRow();
        product.logicalStoreId = 50005L;
        product.productMasterId = 53045L;
        product.productVariantId = 53991L;
        product.partnerSku = "PAPERSAYS027";
        product.barcode = "PAPERSAYSB027";
        product.siteCode = "AE";

        when(mapper.selectProductMatches(307L, "STR108065-NAE", "PAPERSAYSB027", "AE"))
                .thenReturn(List.of(product));
        when(mapper.nextProductLogisticsCostHistoryId()).thenReturn(410027L);
        when(mapper.nextProductLogisticsCurrentCostId()).thenReturn(420027L);

        CurrentCostRow result = ledgerService.manualCurrentQuote(307L, 90004L, command);

        org.mockito.ArgumentCaptor<CostHistoryRow> historyCaptor =
                org.mockito.ArgumentCaptor.forClass(CostHistoryRow.class);
        org.mockito.ArgumentCaptor<CurrentCostRow> currentCaptor =
                org.mockito.ArgumentCaptor.forClass(CurrentCostRow.class);
        verify(mapper).insertCostHistory(historyCaptor.capture(), eq(90004L));
        verify(mapper).upsertCurrentCost(currentCaptor.capture(), eq(90004L));

        assertThat(result.partnerSku).isEqualTo("PAPERSAYS027");
        assertThat(historyCaptor.getValue().partnerSku).isEqualTo("PAPERSAYS027");
        assertThat(historyCaptor.getValue().barcode).isEqualTo("PAPERSAYSB027");
        assertThat(historyCaptor.getValue().idempotencyKey).contains("MANUAL_CURRENT_QUOTE:307:50005:PAPERSAYS027:AE:ET:SEA");
        assertThat(currentCaptor.getValue().partnerSku).isEqualTo("PAPERSAYS027");
        assertThat(currentCaptor.getValue().barcode).isEqualTo("PAPERSAYSB027");
    }

    @Test
    void shouldAppendCategoryAssignmentHistoryAndProjectCurrentCostForSelectedProducts() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        BatchCategoryAssignmentCommand command = new BatchCategoryAssignmentCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "sa";
        command.forwarderCode = "chic";
        command.forwarderName = "CHIC";
        command.transportMode = "air";
        command.cargoCategoryCode = "SA_COSMETICS_LIQUID";
        command.cargoCategoryName = "沙特-化妆品及液体";
        command.remark = "批量修正 CHIC 类别";
        CategoryAssignmentItem item = new CategoryAssignmentItem();
        item.partnerSku = "PAPERSAYS041";
        command.items = List.of(item);

        ProductMatchRow product = new ProductMatchRow();
        product.logicalStoreId = 50005L;
        product.productMasterId = 53001L;
        product.productVariantId = 54001L;
        product.partnerSku = "PAPERSAYS041";
        product.barcode = "PAPERSAYS041";
        product.siteCode = "SA";

        CurrentCostRow source = new CurrentCostRow();
        source.id = 380041L;
        source.ownerUserId = 307L;
        source.logicalStoreId = 50005L;
        source.productMasterId = 53001L;
        source.productVariantId = 54001L;
        source.partnerSku = "PAPERSAYS041";
        source.barcode = "PAPERSAYS041";
        source.siteCode = "SA";
        source.forwarderCode = "QIKE";
        source.forwarderName = "CHIC";
        source.transportMode = "AIR";
        source.costType = "CURRENT_QUOTE";
        source.feeType = "HEADHAUL";
        source.chargeUnit = "KG";
        source.unitCostCny = new BigDecimal("82.00");
        source.currencyCode = "CNY";
        source.confidenceLevel = "HIGH";

        when(mapper.selectProductMatches(307L, "STR108065-NSA", "PAPERSAYS041", "SA"))
                .thenReturn(List.of(product));
        when(mapper.selectCurrentCostForCategoryAssignment(307L, 50005L, "PAPERSAYS041", "SA", "QIKE", "AIR"))
                .thenReturn(source);
        when(mapper.nextProductLogisticsCostHistoryId()).thenReturn(410041L);
        when(mapper.nextProductLogisticsCurrentCostId()).thenReturn(420041L);

        BatchCategoryAssignmentResult result = ledgerService.batchAssignCategories(307L, 90004L, command);

        assertThat(result.requestedCount).isEqualTo(1);
        assertThat(result.updatedCount).isEqualTo(1);
        assertThat(result.skippedCount).isZero();
        assertThat(result.items).singleElement().satisfies(row -> {
            assertThat(row.partnerSku).isEqualTo("PAPERSAYS041");
            assertThat(row.status).isEqualTo("UPDATED");
        });
        org.mockito.ArgumentCaptor<CostHistoryRow> historyCaptor =
                org.mockito.ArgumentCaptor.forClass(CostHistoryRow.class);
        org.mockito.ArgumentCaptor<CurrentCostRow> currentCaptor =
                org.mockito.ArgumentCaptor.forClass(CurrentCostRow.class);
        verify(mapper).insertCostHistory(historyCaptor.capture(), eq(90004L));
        verify(mapper).upsertCurrentCost(currentCaptor.capture(), eq(90004L));
        assertThat(historyCaptor.getValue().sourceType).isEqualTo("MANUAL_CATEGORY_ASSIGNMENT");
        assertThat(historyCaptor.getValue().rawFeeName).isEqualTo("批量维护类别");
        assertThat(historyCaptor.getValue().cargoCategoryCode).isEqualTo("SA_COSMETICS_LIQUID");
        assertThat(historyCaptor.getValue().cargoCategoryName).isEqualTo("沙特-化妆品及液体");
        assertThat(historyCaptor.getValue().unitCostCny).isEqualByComparingTo("82.00");
        assertThat(currentCaptor.getValue().currentHistoryId).isEqualTo(410041L);
        assertThat(currentCaptor.getValue().cargoCategoryCode).isEqualTo("SA_COSMETICS_LIQUID");
    }

    @Test
    void shouldCreateCurrentQuoteFromRouteRateCardWhenBatchAssigningCategoryToProductWithoutPrice() {
        ProductLogisticsCostLedgerService ledgerService = ledgerService();
        BatchCategoryAssignmentCommand command = new BatchCategoryAssignmentCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "sa";
        command.forwarderCode = "yite";
        command.forwarderName = "义特";
        command.transportMode = "sea";
        command.cargoCategoryCode = "A";
        command.cargoCategoryName = "A类别运费";
        command.remark = "无数据商品批量维护 A 类";
        CategoryAssignmentItem item = new CategoryAssignmentItem();
        item.partnerSku = "PAPERSAYS008";
        command.items = List.of(item);

        ProductMatchRow product = new ProductMatchRow();
        product.logicalStoreId = 50005L;
        product.productMasterId = 53008L;
        product.productVariantId = 54008L;
        product.partnerSku = "PAPERSAYS008";
        product.barcode = "PAPERSAYS008";
        product.siteCode = "SA";

        RateCardRow rateCard = new RateCardRow();
        rateCard.id = 430015L;
        rateCard.ownerUserId = 307L;
        rateCard.siteCode = "SA";
        rateCard.forwarderCode = "YITE";
        rateCard.forwarderName = "义特";
        rateCard.transportMode = "SEA";
        rateCard.feeType = "HEADHAUL";
        rateCard.cargoCategoryCode = "A";
        rateCard.cargoCategoryName = "A类别运费";
        rateCard.chargeUnit = "CBM";
        rateCard.unitCostCny = new BigDecimal("1390.00");
        rateCard.currencyCode = "CNY";
        rateCard.sourceType = "YITE_CURRENT_QUOTE_FACT";
        rateCard.sourceReference = "product_logistics_rate_card_seed";
        rateCard.effectiveAt = LocalDateTime.of(2026, 7, 1, 0, 0);

        when(mapper.selectProductMatches(307L, "STR108065-NSA", "PAPERSAYS008", "SA"))
                .thenReturn(List.of(product));
        when(mapper.selectRateCardForCategoryAssignment(307L, "SA", "YITE", "SEA", "HEADHAUL", "A"))
                .thenReturn(rateCard);
        when(mapper.nextProductLogisticsCostHistoryId()).thenReturn(410008L);
        when(mapper.nextProductLogisticsCurrentCostId()).thenReturn(420008L);

        BatchCategoryAssignmentResult result = ledgerService.batchAssignCategories(307L, 90004L, command);

        assertThat(result.updatedCount).isEqualTo(1);
        assertThat(result.skippedCount).isZero();
        org.mockito.ArgumentCaptor<CostHistoryRow> historyCaptor =
                org.mockito.ArgumentCaptor.forClass(CostHistoryRow.class);
        org.mockito.ArgumentCaptor<CurrentCostRow> currentCaptor =
                org.mockito.ArgumentCaptor.forClass(CurrentCostRow.class);
        verify(mapper).insertCostHistory(historyCaptor.capture(), eq(90004L));
        verify(mapper).upsertCurrentCost(currentCaptor.capture(), eq(90004L));

        assertThat(historyCaptor.getValue().sourceType).isEqualTo("ROUTE_RATE_CARD_CATEGORY_ASSIGNMENT");
        assertThat(historyCaptor.getValue().rawFeeName).isEqualTo("批量维护类别报价");
        assertThat(historyCaptor.getValue().partnerSku).isEqualTo("PAPERSAYS008");
        assertThat(historyCaptor.getValue().cargoCategoryCode).isEqualTo("A");
        assertThat(historyCaptor.getValue().unitCostCny).isEqualByComparingTo("1390.00");
        assertThat(historyCaptor.getValue().chargeUnit).isEqualTo("CBM");
        assertThat(historyCaptor.getValue().evidenceJson).contains("\"sourceRateCardId\":\"430015\"");
        assertThat(currentCaptor.getValue().sourceType).isEqualTo("ROUTE_RATE_CARD_CATEGORY_ASSIGNMENT");
        assertThat(currentCaptor.getValue().currentHistoryId).isEqualTo(410008L);
        assertThat(currentCaptor.getValue().unitCostCny).isEqualByComparingTo("1390.00");
    }

    private ProductLogisticsCostLedgerService ledgerService() {
        return new ProductLogisticsCostLedgerService(mapper);
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .build();
    }
}
