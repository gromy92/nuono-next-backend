package com.nuono.next.procurementorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.AddItemsCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateShippingOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ShippingOrderSegmentScopeCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.SiteQuantityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemSourcingRequirementCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineYiteMaterialCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderSeaRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderWarehouseProcessingFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsBillReconciliationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsCostComponentInsertRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsExpectedBillComponentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsExpectedBillRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsRecommendationInsertRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688HistoryRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688PurchaseBatchRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductArchiveRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductOfferRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderChannelQuoteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderDeclarationAttributeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreScopeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ProductOptionView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistoryItemView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistorySourceView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistoryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688PurchaseBatchView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsCostComponentView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanLineView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsRecommendationView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteChannelOptionView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteForwarderOptionView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteImportErrorView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteImportView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteOptionsView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteReportExportView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteSummaryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderItemView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderShippingSubmitView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.LogisticsBillComponentView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.LogisticsBillView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderLineView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderSegmentView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderSubmitView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.SiteQuantitySummaryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.SiteAllocationView;
import com.nuono.next.productselection.Ali1688CollectionView;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import com.nuono.next.productselection.NoonImageUrlNormalizer;
import com.nuono.next.productselection.ProductSelectionSourceCollectionRow;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProcurementPurchaseOrderService {

    private static final String HIDDEN_SOURCE_TYPE = "purchase-order-product";
    private static final String TRANSPORT_AIR = "AIR";
    private static final String TRANSPORT_EXPRESS = "EXPRESS";
    private static final String TRANSPORT_SEA = "SEA";
    private static final String TRANSPORT_UNSPECIFIED = "UNSPECIFIED";
    private static final String FULFILLMENT_WAREHOUSE_RECEIPT = "WAREHOUSE_RECEIPT";
    private static final String FULFILLMENT_FACTORY_DIRECT = "FACTORY_DIRECT";
    private static final String LOGISTICS_QUOTE_PENDING = "PENDING_QUOTE";
    private static final String LOGISTICS_QUOTE_CONFIRMED = "CONFIRMED";
    private static final String SHIPPING_NOT_SUBMITTED = "NOT_SUBMITTED";
    private static final String SHIPPING_PARTIAL_SUBMITTED = "PARTIAL_SUBMITTED";
    private static final String SHIPPING_SUBMITTED = "SUBMITTED";
    private static final String ORDER_SUBMITTED = "SUBMITTED";
    private static final String LOGISTICS_QUOTE_XLS_CONTENT_TYPE = "application/vnd.ms-excel";
    private static final String LOGISTICS_QUOTE_XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String YITE_FORWARDER_CODE = "YT";
    private static final String YITE_FORWARDER_NAME = "义特物流";
    private static final String YITE_TEMPLATE_TYPE = "YITE_B2B_SINGLE_TICKET";
    private static final String YITE_TEMPLATE_NAME = "义特海外无忧B2B单票导入模板";
    private static final String YITE_FILENAME_PREFIX = "B2B发货审核单";
    private static final String YITE_MATERIAL_ATTRIBUTE_CODE = "YITE_MATERIAL";
    private static final int YITE_PRODUCT_IMAGE_COLUMN = 11;
    private static final int LOGISTICS_PRODUCT_IMAGE_EMBED_PIXELS = 400;
    private static final float YITE_PRODUCT_IMAGE_ROW_HEIGHT_POINTS = 52.5F;
    private static final int YITE_PRODUCT_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
    private static final Duration YITE_PRODUCT_IMAGE_TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient YITE_PRODUCT_IMAGE_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Set<String> YITE_MATERIAL_OPTIONS = Set.of("塑料", "陶瓷", "金属", "纸", "纺织", "木制");
    private static final String ET_FORWARDER_CODE = "ET";
    private static final String ET_FORWARDER_NAME = "易通物流";
    private static final String ET_TEMPLATE_TYPE = "ET_SKU_ONE_STEP_PACKING_IMPORT";
    private static final String ET_TEMPLATE_NAME = "易通SKU一步上传装箱清单导入模板";
    private static final String ET_FILENAME_PREFIX = "sku一步上传装箱清单导入模版";
    private static final int ET_ENGLISH_SHORT_NAME_MAX_LENGTH = 90;
    private static final String[] LOGISTICS_QUOTE_HEADERS = {
            "报价行ID", "采购单ID", "采购商品ID", "采购站点行ID", "采购单号", "采购单名", "站点", "运输方式",
            "商品SKU", "PSKU", "商品名称", "数量", "履约方式", "新品", "报价状态", "提交发货状态",
            "货代编码", "货代名称", "路线编码", "路线名称", "服务编码", "服务名称", "币种", "单价",
            "计费单位", "确认金额", "物流备注"
    };
    private static final int YITE_DETAIL_HEADER_ROW_INDEX = 22;
    private static final int YITE_DETAIL_FIRST_DATA_ROW_INDEX = 23;
    private static final int YITE_HIDDEN_START_COLUMN = 20;
    private static final int YITE_HIDDEN_QUOTE_LINE_ID_COLUMN = YITE_HIDDEN_START_COLUMN;
    private static final int YITE_HIDDEN_PURCHASE_ORDER_ID_COLUMN = YITE_HIDDEN_START_COLUMN + 1;
    private static final int YITE_HIDDEN_PURCHASE_ORDER_ITEM_ID_COLUMN = YITE_HIDDEN_START_COLUMN + 2;
    private static final int YITE_HIDDEN_PURCHASE_ORDER_ITEM_SITE_ID_COLUMN = YITE_HIDDEN_START_COLUMN + 3;
    private static final int YITE_HIDDEN_FORWARDER_CODE_COLUMN = YITE_HIDDEN_START_COLUMN + 4;
    private static final int YITE_HIDDEN_UNIT_PRICE_COLUMN = YITE_HIDDEN_START_COLUMN + 5;
    private static final int YITE_HIDDEN_BILLING_UNIT_COLUMN = YITE_HIDDEN_START_COLUMN + 6;
    private static final int YITE_HIDDEN_ESTIMATED_AMOUNT_COLUMN = YITE_HIDDEN_START_COLUMN + 7;
    private static final int YITE_HIDDEN_REMARK_COLUMN = YITE_HIDDEN_START_COLUMN + 8;
    private static final int YITE_VISIBLE_DECLARED_UNIT_PRICE_COLUMN = 9;
    private static final int YITE_VISIBLE_LATEST_QUOTE_COLUMN = 16;
    private static final String[] YITE_DETAIL_HEADERS = {
            "货箱编号*", "货箱重量(KG)*", "货箱长度(CM)*", "货箱宽度(CM)*", "货箱高度(CM)*",
            "产品SKU*", "产品英文品名*", "产品中文品名*", "产品申报数量*", "产品申报单价*",
            "产品材质*", "产品图片", "产品品牌", "产品型号", "产品海关编码", "历史报价"
    };
    private static final String[] YITE_HIDDEN_HEADERS = {
            "Nuono报价行ID", "Nuono采购单ID", "Nuono采购商品ID", "Nuono采购站点行ID",
            "Nuono货代编码", "货代确认单价", "计费单位", "确认金额", "物流备注"
    };
    private static final int ET_HEADER_ROW_INDEX = 1;
    private static final int ET_FIRST_DATA_ROW_INDEX = 2;
    private static final int ET_VISIBLE_COLUMN_COUNT = 21;
    private static final int ET_PRODUCT_IMAGE_COLUMN = 13;
    private static final int ET_HIDDEN_START_COLUMN = 52;
    private static final int ET_HIDDEN_QUOTE_LINE_ID_COLUMN = ET_HIDDEN_START_COLUMN;
    private static final int ET_HIDDEN_PURCHASE_ORDER_ID_COLUMN = ET_HIDDEN_START_COLUMN + 1;
    private static final int ET_HIDDEN_PURCHASE_ORDER_ITEM_ID_COLUMN = ET_HIDDEN_START_COLUMN + 2;
    private static final int ET_HIDDEN_PURCHASE_ORDER_ITEM_SITE_ID_COLUMN = ET_HIDDEN_START_COLUMN + 3;
    private static final int ET_HIDDEN_FORWARDER_CODE_COLUMN = ET_HIDDEN_START_COLUMN + 4;
    private static final int ET_HIDDEN_FORWARDER_NAME_COLUMN = ET_HIDDEN_START_COLUMN + 5;
    private static final int ET_HIDDEN_ROUTE_CODE_COLUMN = ET_HIDDEN_START_COLUMN + 6;
    private static final int ET_HIDDEN_ROUTE_NAME_COLUMN = ET_HIDDEN_START_COLUMN + 7;
    private static final int ET_HIDDEN_SERVICE_CODE_COLUMN = ET_HIDDEN_START_COLUMN + 8;
    private static final int ET_HIDDEN_SERVICE_NAME_COLUMN = ET_HIDDEN_START_COLUMN + 9;
    private static final int ET_HIDDEN_UNIT_PRICE_COLUMN = ET_HIDDEN_START_COLUMN + 10;
    private static final int ET_HIDDEN_BILLING_UNIT_COLUMN = ET_HIDDEN_START_COLUMN + 11;
    private static final int ET_HIDDEN_ESTIMATED_AMOUNT_COLUMN = ET_HIDDEN_START_COLUMN + 12;
    private static final int ET_HIDDEN_REMARK_COLUMN = ET_HIDDEN_START_COLUMN + 13;
    private static final int ET_DEFAULT_CARTON_LENGTH_CM = 30;
    private static final int ET_DEFAULT_CARTON_WIDTH_CM = 30;
    private static final int ET_DEFAULT_CARTON_HEIGHT_CM = 30;
    private static final int ET_DEFAULT_CARTON_WEIGHT_KG = 10;
    private static final BigDecimal ET_DEFAULT_DECLARE_UNIT_PRICE_USD = BigDecimal.ONE;
    private static final float ET_PRODUCT_IMAGE_ROW_HEIGHT_POINTS = 52.5F;
    private static final String ET_INSTRUCTION_TEXT = "填表说明 1 1 该表格用于产品信息和装箱清单同时导入，适用于所有产品首次发货。（“填表指南”请看sheet2）。所有产品都已发过货（系统已有产品信息）建议用“产品、装箱数据分部导入”模板直接导入装箱清单即可。\n"
            + "1.1 装有相同产品、箱号排序连号（连号用\"—\"表示）箱子可同一行填写；\n"
            + "1.2 不同产品或不连号箱子，分行填写。\n"
            + "2 批发按箱操作商品条码可随意自编（5位以上）；平台2c产品应准确填写，以便后续按sku操作。 \n"
            + "3 应如实填写所有信息，并承担错报责任。 ";
    private static final String[] ET_DETAIL_HEADERS = {
            "箱号", "*长(CM)", "*宽(CM)", "*高(CM)", "*重量(KG)", "*每箱数量",
            "*商家条码", "款号", "*英文简称", "*中文品名", "*申报单价($)", "*实物品牌",
            "平台品牌", "*图片", "*材质", "*是否带电", "*是否带磁", "*是否带蓝牙",
            "*是否带液体", "*是否带粉末", "用途"
    };
    private static final String[] ET_HIDDEN_HEADERS = {
            "Nuono报价行ID", "Nuono采购单ID", "Nuono采购商品ID", "Nuono采购站点行ID",
            "Nuono货代编码", "Nuono货代名称", "Nuono路线编码", "Nuono路线名称",
            "Nuono服务编码", "Nuono服务名称", "货代确认单价", "计费单位", "确认金额", "物流备注"
    };
    private static final BigDecimal CUBIC_CM_PER_CBM = new BigDecimal("1000000");
    private static final BigDecimal GRAMS_PER_KG = new BigDecimal("1000");
    private static final BigDecimal DEFAULT_AIR_VOLUME_DIVISOR = new BigDecimal("6000");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {};
    private static final DateTimeFormatter VIEW_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProcurementPurchaseOrderMapper mapper;
    private final ProductSelectionMapper productSelectionMapper;
    private final LocalDbAli1688CollectionService ali1688CollectionService;
    private final ObjectMapper objectMapper;
    private final PurchaseOrderLogisticsCostCalculator costCalculator = new PurchaseOrderLogisticsCostCalculator();

    public LocalDbProcurementPurchaseOrderService(
            ProcurementPurchaseOrderMapper mapper,
            ProductSelectionMapper productSelectionMapper,
            LocalDbAli1688CollectionService ali1688CollectionService,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.productSelectionMapper = productSelectionMapper;
        this.ali1688CollectionService = ali1688CollectionService;
        this.objectMapper = objectMapper;
    }

    public List<PurchaseOrderView> listOrders(
            BusinessAccessContext access,
            String storeCode,
            String keyword
    ) {
        return listOrders(access, storeCode, keyword, false);
    }

    public List<PurchaseOrderView> listOrders(
            BusinessAccessContext access,
            String storeCode,
            String keyword,
            boolean submittedOnly
    ) {
        return listOrders(access, storeCode, keyword, submittedOnly, false);
    }

    public List<PurchaseOrderView> listOrders(
            BusinessAccessContext access,
            String storeCode,
            String keyword,
            boolean submittedOnly,
            boolean shippingAvailableOnly
    ) {
        if (!StringUtils.hasText(storeCode)) {
            Long ownerUserId = access == null ? null : access.getBusinessOwnerUserId();
            if (ownerUserId == null) {
                throw new IllegalArgumentException("缺少老板范围，无法读取采购单。");
            }
            return mapper.listOrdersByOwner(ownerUserId, access.getStoreCodes(), trim(keyword), submittedOnly, shippingAvailableOnly, 120).stream()
                    .map(this::toOrderView)
                    .collect(Collectors.toList());
        }
        StoreScopeRecord scope = requireStoreScope(access, storeCode);
        return mapper.listOrders(scope.logicalStoreId, trim(keyword), submittedOnly, shippingAvailableOnly, 80).stream()
                .map(this::toOrderView)
                .collect(Collectors.toList());
    }

    public PurchaseOrderView getOrder(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        return toOrderView(order);
    }

    public List<ProductOptionView> listProductOptions(
            BusinessAccessContext access,
            String storeCode,
            String keyword
    ) {
        StoreScopeRecord scope = requireStoreScope(access, storeCode);
        return mapper.listProductOptions(scope.logicalStoreId, trim(keyword), 60).stream()
                .map(this::toProductOptionView)
                .collect(Collectors.toList());
    }

    @Transactional
    public PurchaseOrderView createOrder(BusinessAccessContext access, CreateOrderCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少采购单参数。");
        }
        StoreScopeRecord scope = requireStoreScope(access, command.storeCode);
        List<String> siteCodes = normalizeSiteCodes(
                command.siteCodes == null || command.siteCodes.isEmpty()
                        ? siteCodesFromItems(command.items)
                        : command.siteCodes,
                scope.logicalStoreId
        );
        String title = requiredText(command.title, "请输入采购单名。");
        Long operatorUserId = access.getSessionUserId();
        Long orderId = mapper.nextOrderId();

        PurchaseOrderRecord order = new PurchaseOrderRecord();
        order.id = orderId;
        order.ownerUserId = scope.ownerUserId;
        order.logicalStoreId = scope.logicalStoreId;
        order.orderNo = "PO-" + orderId;
        order.title = title;
        order.remark = trimToNull(command.remark);
        order.status = "DRAFT";
        order.collectionStatus = "NOT_STARTED";
        order.progressPercent = 0;
        order.siteCodesJson = writeStringList(siteCodes);
        order.projectCodeCache = scope.projectCode;
        order.projectNameCache = scope.projectName;
        order.anchorStoreCodeCache = scope.anchorStoreCode;
        order.createdBy = operatorUserId;
        order.updatedBy = operatorUserId;
        mapper.insertOrder(order);
        log(orderId, null, "CREATE_ORDER", operatorUserId, null, "DRAFT", null);

        addItemsInternal(orderId, command.items, operatorUserId);
        mapper.recalculateOrderAggregates(orderId, operatorUserId);
        return toOrderView(requireOrder(orderId));
    }

    @Transactional
    public PurchaseOrderView updateOrder(
            BusinessAccessContext access,
            String orderId,
            UpdateOrderCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少采购单参数。");
        }
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        String title = requiredText(command.title, "请输入采购单名。");
        String remark = trimToNull(command.remark);
        Long operatorUserId = access.getSessionUserId();
        mapper.updateOrderHeader(order.id, title, remark, operatorUserId);
        log(order.id, null, "UPDATE_ORDER", operatorUserId, order.status, order.status, title);
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView submitOrder(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        Long operatorUserId = access.getSessionUserId();
        if (!isOrderSubmitted(order)) {
            int updated = mapper.submitOrder(order.id, operatorUserId);
            if (updated <= 0) {
                throw new IllegalArgumentException("采购单提交失败，请刷新后重试。");
            }
            log(order.id, null, "SUBMIT_ORDER", operatorUserId, order.status, ORDER_SUBMITTED, null);
        }
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView addItems(
            BusinessAccessContext access,
            String orderId,
            AddItemsCommand command
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        if (command == null || command.items == null || command.items.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个商品。");
        }
        addItemsInternal(order.id, command.items, access.getSessionUserId());
        mapper.recalculateOrderAggregates(order.id, access.getSessionUserId());
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView updateItem(
            BusinessAccessContext access,
            String orderId,
            String itemId,
            UpdateItemCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少采购单商品参数。");
        }
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        Long parsedItemId = parseLongId(itemId, "采购单商品不存在或已删除。");
        PurchaseOrderItemRecord item = mapper.selectItemById(parsedItemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        String requestedPsku = trim(command.psku);
        if (StringUtils.hasText(requestedPsku) && !requestedPsku.equalsIgnoreCase(item.partnerSku)) {
            throw new IllegalArgumentException("编辑商品不支持修改 PSKU，请删除后重新添加。");
        }
        List<SiteTransportQuantity> allocations = normalizeSiteTransportQuantities(command);
        if (allocations.isEmpty()) {
            throw new IllegalArgumentException("请填写 " + item.partnerSku + " 的站点和数量。");
        }

        Map<String, StoreSiteRecord> availableStoreSites = storeSitesByCode(order.logicalStoreId);
        LinkedHashSet<String> nextOrderSiteCodes = new LinkedHashSet<>(readStringList(order.siteCodesJson));
        Long operatorUserId = access.getSessionUserId();
        String beforeStatus = dbStatus(item);
        String requestedFulfillmentType = normalizeOptionalFulfillmentType(command.fulfillmentType);
        if (requestedFulfillmentType != null || command.fulfillmentSourceName != null) {
            String nextFulfillmentType = requestedFulfillmentType == null
                    ? normalizeFulfillmentType(item.fulfillmentType)
                    : requestedFulfillmentType;
            String nextFulfillmentSourceName = command.fulfillmentSourceName == null
                    ? trimToNull(item.fulfillmentSourceName)
                    : trimToNull(command.fulfillmentSourceName);
            mapper.updateItemFulfillment(item.id, nextFulfillmentType, nextFulfillmentSourceName, operatorUserId);
            item.fulfillmentType = nextFulfillmentType;
            item.fulfillmentSourceName = nextFulfillmentSourceName;
        }
        mapper.softDeleteItemSitesByItem(parsedItemId, operatorUserId);
        for (SiteTransportQuantity allocation : allocations) {
            String siteCode = normalizeSiteCode(allocation.siteCode);
            if (!availableStoreSites.containsKey(siteCode)) {
                throw new IllegalArgumentException("站点 " + siteCode + " 不属于当前店铺。");
            }
            nextOrderSiteCodes.add(siteCode);
            ProductOfferRecord offer = mapper.selectProductOffer(order.logicalStoreId, item.partnerSku, item.productVariantId, siteCode);
            if (offer == null) {
                throw new IllegalArgumentException(item.partnerSku + " 在站点 " + siteCode + " 没有商品 Offer，不能加入采购单。");
            }
            PurchaseOrderItemSiteRecord site = new PurchaseOrderItemSiteRecord();
            site.id = mapper.nextItemSiteId();
            site.purchaseOrderId = order.id;
            site.purchaseOrderItemId = item.id;
            site.ownerUserId = order.ownerUserId;
            site.logicalStoreId = order.logicalStoreId;
            site.siteId = offer.siteId;
            site.siteCode = offer.siteCode;
            site.productSiteOfferId = offer.productSiteOfferId;
            site.pskuCode = offer.pskuCode;
            site.offerCode = offer.offerCode;
            site.transportMode = allocation.transportMode;
            site.quantity = allocation.quantity;
            site.createdBy = operatorUserId;
            site.updatedBy = operatorUserId;
            mapper.upsertItemSite(site);
        }
        mapper.recalculateItemAggregates(item.id, operatorUserId);
        mapper.recalculateOrderAggregates(order.id, operatorUserId);
        persistOrderSiteCodesIfChanged(order, nextOrderSiteCodes, operatorUserId);
        log(order.id, item.id, "UPDATE_ITEM", operatorUserId, beforeStatus, beforeStatus, item.partnerSku);
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView updateItemSourcingRequirement(
            BusinessAccessContext access,
            String orderId,
            String itemId,
            UpdateItemSourcingRequirementCommand command
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        Long parsedItemId = parseLongId(itemId, "采购单商品不存在或已删除。");
        PurchaseOrderItemRecord item = mapper.selectItemById(parsedItemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        ProcurementPurchaseOrderSourcingRequirement requirement = ProcurementPurchaseOrderSourcingRequirement.of(
                command == null ? null : command.sourcingSpec,
                command == null ? null : command.sourcingSize,
                command == null ? null : command.sourcingColor
        );
        mapper.updateItemSourcingRequirement(parsedItemId, requirement, access.getSessionUserId());
        log(order.id, parsedItemId, "UPDATE_ITEM_SOURCING_REQUIREMENT", access.getSessionUserId(), null, null, null);
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView deleteOrder(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        Long operatorUserId = access.getSessionUserId();
        mapper.softDeleteLinksByOrder(order.id, operatorUserId);
        mapper.softDeleteItemSitesByOrder(order.id, operatorUserId);
        mapper.softDeleteItemsByOrder(order.id, operatorUserId);
        mapper.softDeleteOrder(order.id, operatorUserId);
        log(order.id, null, "DELETE_ORDER", operatorUserId, order.status, "DELETED", null);
        PurchaseOrderView view = new PurchaseOrderView();
        view.id = String.valueOf(order.id);
        view.orderNo = order.orderNo;
        view.title = order.title;
        view.status = "deleted";
        return view;
    }

    @Transactional
    public PurchaseOrderView deleteItem(
            BusinessAccessContext access,
            String orderId,
            String itemId
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        Long parsedItemId = parseLongId(itemId, "采购单商品不存在或已删除。");
        PurchaseOrderItemRecord item = mapper.selectItemById(parsedItemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        Long operatorUserId = access.getSessionUserId();
        mapper.supersedeCurrentAli1688TasksByItem(parsedItemId, operatorUserId);
        mapper.softDeleteLinksByItem(parsedItemId, operatorUserId);
        mapper.softDeleteItemSitesByItem(parsedItemId, operatorUserId);
        mapper.softDeleteItem(parsedItemId, operatorUserId);
        mapper.recalculateOrderAggregates(order.id, operatorUserId);
        log(order.id, parsedItemId, "DELETE_ITEM", operatorUserId, dbStatus(item), "DELETED", item.partnerSku);
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView collectOrder(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        List<PurchaseOrderItemRecord> items = mapper.listItemsByOrder(order.id);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("当前采购单还没有商品，不能发起采集。");
        }
        for (PurchaseOrderItemRecord item : items) {
            collectItemInternal(order, item, access.getSessionUserId());
        }
        mapper.recalculateOrderAggregates(order.id, access.getSessionUserId());
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView collectItem(
            BusinessAccessContext access,
            String orderId,
            String itemId
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        Long parsedItemId = parseLongId(itemId, "采购单商品不存在或已删除。");
        PurchaseOrderItemRecord item = mapper.selectItemById(parsedItemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        collectItemInternal(order, item, access.getSessionUserId());
        mapper.recalculateOrderAggregates(order.id, access.getSessionUserId());
        return toOrderView(requireOrder(order.id));
    }

    public Ali1688CollectionView getItemAli1688(BusinessAccessContext access, String itemId) {
        PurchaseOrderItemRecord item = mapper.selectItemById(parseLongId(itemId, "采购单商品不存在或已删除。"));
        if (item == null) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        PurchaseOrderRecord order = requireOrder(item.purchaseOrderId);
        requireOrderAccess(access, order);
        if (item.sourceCollectionId == null) {
            Ali1688CollectionView view = new Ali1688CollectionView();
            view.status = "not_started";
            view.progressPercent = 0;
            view.searchMode = "主图图搜";
            view.sourcePlatform = "店铺";
            view.sourceTitle = defaultText(item.titleCache, item.partnerSku);
            view.sourceTitleCn = defaultText(item.titleCache, item.partnerSku);
            view.sourceImageUrl = NoonImageUrlNormalizer.normalize(item.imageUrlCache);
            view.candidateCount = 0;
            view.recommendedCount = 0;
            view.message = "该采购单商品尚未发起1688采集。";
            view.canGenerateProcurementOrder = false;
            view.sourceSpecs = purchaseOrderItemSpecs(item);
            return view;
        }
        Ali1688CollectionView view = ali1688CollectionService.getCurrentView(item.sourceCollectionId);
        view.sourceSpecs = mergeSourceSpecs(purchaseOrderItemSpecs(item), view.sourceSpecs);
        return view;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderAli1688HistoryView getOrderAli1688History(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        List<PurchaseOrderItemRecord> items = mapper.listItemsByOrder(order.id);
        List<PurchaseOrderItemSiteRecord> sites = mapper.listItemSitesByOrder(order.id);
        PurchaseOrderAli1688HistoryView view = new PurchaseOrderAli1688HistoryView();
        if (items.isEmpty() || sites.isEmpty()) {
            fillPagination(view);
            return view;
        }

        LinkedHashSet<String> siteCodes = new LinkedHashSet<>();
        LinkedHashSet<String> partnerSkus = new LinkedHashSet<>();
        LinkedHashSet<String> skuParents = new LinkedHashSet<>();
        Map<Long, PurchaseOrderItemRecord> itemsById = items.stream()
                .collect(Collectors.toMap(item -> item.id, item -> item, (left, right) -> left, LinkedHashMap::new));
        for (PurchaseOrderItemRecord item : items) {
            addTrimmed(partnerSkus, item.partnerSku);
            addTrimmed(skuParents, item.skuParent);
        }
        for (PurchaseOrderItemSiteRecord site : sites) {
            String siteCode = normalizeSiteCode(site.siteCode);
            if (StringUtils.hasText(siteCode)) {
                siteCodes.add(siteCode);
            }
            PurchaseOrderItemRecord item = itemsById.get(site.purchaseOrderItemId);
            if (item != null) {
                addTrimmed(partnerSkus, item.partnerSku);
                addTrimmed(skuParents, item.skuParent);
            }
        }
        String projectCode = firstText(order.projectCodeCache, order.anchorStoreCodeCache);
        if (!StringUtils.hasText(projectCode) || siteCodes.isEmpty() || (partnerSkus.isEmpty() && skuParents.isEmpty())) {
            fillPagination(view);
            return view;
        }

        Map<String, Ali1688HistoryAccumulator> bySku = new LinkedHashMap<>();
        for (PurchaseOrderAli1688PurchaseBatchRow row : mapper.listOrderAli1688PurchaseBatches(
                order.ownerUserId,
                projectCode,
                new ArrayList<>(siteCodes),
                new ArrayList<>(partnerSkus),
                new ArrayList<>(skuParents)
        )) {
            bySku.computeIfAbsent(ali1688HistoryGroupKey(row.siteCode, row.partnerSku, row.pskuCode, row.skuParent),
                    ignored -> new Ali1688HistoryAccumulator(row))
                    .add(row);
        }
        for (PurchaseOrderAli1688HistoryRow row : mapper.listOrderAli1688AllocationHistoryRows(
                order.ownerUserId,
                projectCode,
                new ArrayList<>(siteCodes),
                new ArrayList<>(partnerSkus),
                new ArrayList<>(skuParents)
        )) {
            bySku.computeIfAbsent(ali1688HistoryGroupKey(row.siteCode, row.partnerSku, row.pskuCode, row.skuParent),
                    ignored -> new Ali1688HistoryAccumulator(row))
                    .add(row);
        }
        for (PurchaseOrderAli1688HistoryRow row : mapper.listOrderAli1688HistoryRows(
                order.ownerUserId,
                projectCode,
                new ArrayList<>(siteCodes),
                new ArrayList<>(partnerSkus),
                new ArrayList<>(skuParents)
        )) {
            bySku.computeIfAbsent(ali1688HistoryGroupKey(row.siteCode, row.partnerSku, row.pskuCode, row.skuParent),
                    ignored -> new Ali1688HistoryAccumulator(row))
                    .add(row);
        }
        view.items = bySku.values().stream()
                .map(Ali1688HistoryAccumulator::toView)
                .collect(Collectors.toList());
        fillPagination(view);
        return view;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderLogisticsPlanView previewLogisticsPlan(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        return buildLogisticsPlanView(order, access.getSessionUserId(), false);
    }

    @Transactional
    public PurchaseOrderLogisticsPlanView generateLogisticsPlan(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        assertOrderEditable(order);
        return buildLogisticsPlanView(order, access.getSessionUserId(), true);
    }

    @Transactional
    public PurchaseOrderLogisticsQuoteReportExportView exportLogisticsQuoteReport(
            BusinessAccessContext access,
            String orderId,
            String forwarderCode,
            String routeCode
    ) {
        String selectedForwarderCode = requiredText(forwarderCode, "请选择报价货代。");
        String selectedRouteCode = requiredText(routeCode, "请选择货代支持的渠道。");
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        Long operatorUserId = access.getSessionUserId();
        List<PurchaseOrderLogisticsQuoteLineRecord> lines = refreshLogisticsQuoteLines(order, operatorUserId);
        List<LogisticsQuoteExportOption> options = collectLogisticsQuoteExportOptions(lines);
        LogisticsQuoteExportOption selectedOption = requireLogisticsQuoteExportOption(
                options,
                selectedForwarderCode,
                selectedRouteCode
        );
        List<PurchaseOrderLogisticsQuoteLineRecord> exportLines = lines.stream()
                .filter(line -> !LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(line.quoteStatus)))
                .filter(line -> matchesLogisticsQuoteOption(line, selectedOption.candidate))
                .collect(Collectors.toList());
        if (exportLines.isEmpty()) {
            throw new IllegalArgumentException("当前采购单没有匹配该货代渠道的待报价商品。");
        }
        for (PurchaseOrderLogisticsQuoteLineRecord line : exportLines) {
            applyLogisticsQuoteChannel(line, selectedOption.candidate);
            mapper.assignLogisticsQuoteLineChannel(line, operatorUserId);
        }

        PurchaseOrderLogisticsQuoteReportExportView view = new PurchaseOrderLogisticsQuoteReportExportView();
        view.filename = logisticsQuoteReportFilename(order.orderNo, selectedOption);
        view.contentType = logisticsQuoteTemplateContentType(selectedOption.templateType);
        view.rowCount = exportLines.size();
        view.pendingCount = (int) exportLines.stream()
                .filter(line -> LOGISTICS_QUOTE_PENDING.equals(normalizeLogisticsQuoteStatus(line.quoteStatus)))
                .count();
        view.newProductCount = (int) exportLines.stream()
                .filter(line -> Boolean.TRUE.equals(line.isNewProduct))
                .count();
        view.content = buildLogisticsQuoteWorkbook(order, exportLines, selectedOption.templateType);
        List<Long> lineIds = exportLines.stream()
                .map(line -> line.id)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (!lineIds.isEmpty()) {
            mapper.markLogisticsQuoteLinesExported(order.id, lineIds, operatorUserId);
        }
        log(order.id, null, "EXPORT_LOGISTICS_QUOTE_REPORT", operatorUserId, order.status, order.status, view.filename);
        return view;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderLogisticsQuoteOptionsView listLogisticsQuoteOptions(
            BusinessAccessContext access,
            String orderId
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        List<PurchaseOrderLogisticsQuoteLineRecord> lines =
                emptyIfNull(mapper.listLogisticsQuoteCandidatesByOrder(order.id));
        List<LogisticsQuoteExportOption> options = collectLogisticsQuoteExportOptions(lines);
        return toLogisticsQuoteOptionsView(order, lines, options);
    }

    @Transactional
    public PurchaseOrderLogisticsQuoteImportView importLogisticsQuoteReport(
            BusinessAccessContext access,
            String orderId,
            InputStream input,
            String filename
    ) {
        if (input == null) {
            throw new IllegalArgumentException("请上传物流报价回传表。");
        }
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        PurchaseOrderLogisticsQuoteImportView view = new PurchaseOrderLogisticsQuoteImportView();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("报价回传表为空。");
            }
            boolean recognized = false;
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (isEtTemplateSheet(sheet)) {
                    recognized = true;
                    importEtLogisticsQuoteSheet(order, sheet, access.getSessionUserId(), filename, view, Set.of());
                } else if (isYiteTemplateSheet(sheet)) {
                    recognized = true;
                    importYiteLogisticsQuoteSheet(order, sheet, access.getSessionUserId(), filename, view, Set.of());
                } else if (isGenericLogisticsQuoteSheet(sheet)) {
                    recognized = true;
                    importGenericLogisticsQuoteSheet(order, sheet, access.getSessionUserId(), filename, view, Set.of());
                }
            }
            if (!recognized) {
                throw new IllegalArgumentException("未识别报价回传表模板，请使用采购单导出的义特、易通模板或通用报价确认表。");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("报价回传表读取失败，请确认文件格式为 xls 或 xlsx。", exception);
        }
        log(order.id, null, "IMPORT_LOGISTICS_QUOTE_REPORT", access.getSessionUserId(), order.status, order.status,
                filename == null ? "物流报价回传表" : filename);
        return view;
    }

    @Transactional
    public PurchaseOrderShippingSubmitView submitShipping(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        int blockingCount = mapper.countUnconfirmedLogisticsQuoteLines(order.id);
        if (blockingCount > 0) {
            throw new IllegalArgumentException("还有物流报价未确认，不能提交给仓库装箱。");
        }
        Long operatorUserId = access.getSessionUserId();
        int submitted = mapper.submitLogisticsQuoteLinesForShipping(order.id, operatorUserId);
        if (submitted <= 0) {
            throw new IllegalArgumentException("当前采购单没有可提交发货的报价行。");
        }
        log(order.id, null, "SUBMIT_SHIPPING", operatorUserId, order.status, order.status, String.valueOf(submitted));
        PurchaseOrderShippingSubmitView view = new PurchaseOrderShippingSubmitView();
        view.purchaseOrderId = String.valueOf(order.id);
        view.purchaseOrderNo = order.orderNo;
        view.shippingSubmitStatus = SHIPPING_SUBMITTED;
        view.submittedLineCount = submitted;
        return view;
    }

    @Transactional(readOnly = true)
    public List<ShippingOrderView> listShippingOrders(BusinessAccessContext access, String keyword) {
        Long ownerUserId = ownerUserId(access);
        return emptyIfNull(mapper.listShippingOrders(ownerUserId, trim(keyword), 50)).stream()
                .map(order -> toShippingOrderView(order, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LogisticsBillView> listLogisticsBills(BusinessAccessContext access, String keyword) {
        Long ownerUserId = ownerUserId(access);
        return emptyIfNull(mapper.listLogisticsBills(ownerUserId, trim(keyword), 80)).stream()
                .map(row -> toLogisticsBillView(row, List.of()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LogisticsBillView getLogisticsBill(BusinessAccessContext access, String expectedBillId) {
        Long ownerUserId = ownerUserId(access);
        Long parsedExpectedBillId = parseLongId(expectedBillId, "物流账单不存在或已删除。");
        LogisticsExpectedBillRecord bill = mapper.selectLogisticsBillById(ownerUserId, parsedExpectedBillId);
        if (bill == null) {
            throw new IllegalArgumentException("物流账单不存在或已删除。");
        }
        List<LogisticsExpectedBillComponentRecord> components =
                emptyIfNull(mapper.listLogisticsBillComponents(ownerUserId, parsedExpectedBillId));
        return toLogisticsBillView(bill, components);
    }

    @Transactional(readOnly = true)
    public ShippingOrderView getShippingOrder(BusinessAccessContext access, String shippingOrderId) {
        ShippingOrderRecord order = requireShippingOrderAccess(
                access,
                parseLongId(shippingOrderId, "发货单不存在或已删除。")
        );
        return toShippingOrderView(order, true);
    }

    @Transactional
    public ShippingOrderView updateShippingOrder(
            BusinessAccessContext access,
            String shippingOrderId,
            UpdateShippingOrderCommand command
    ) {
        Long parsedShippingOrderId = parseLongId(shippingOrderId, "发货单不存在或已删除。");
        ShippingOrderRecord order = requireShippingOrderAccess(access, parsedShippingOrderId);
        String title = command == null ? null : trim(command.title);
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("请输入发货单名。");
        }
        String remark = command == null ? null : trimToNull(command.remark);
        mapper.updateShippingOrderHeader(order.id, order.ownerUserId, title, remark, access.getSessionUserId());
        return toShippingOrderView(mapper.selectShippingOrderById(order.id), true);
    }

    @Transactional
    public ShippingOrderView updateShippingOrderLineYiteMaterial(
            BusinessAccessContext access,
            String shippingOrderId,
            String shippingOrderLineId,
            UpdateShippingOrderLineYiteMaterialCommand command
    ) {
        Long parsedShippingOrderId = parseLongId(shippingOrderId, "发货单不存在或已删除。");
        Long parsedLineId = parseLongId(shippingOrderLineId, "发货单商品不存在或已删除。");
        ShippingOrderRecord order = requireShippingOrderAccess(access, parsedShippingOrderId);
        String material = normalizeYiteMaterial(command == null ? null : command.yiteMaterial);
        ShippingOrderLineRecord line = mapper.selectShippingOrderLineById(order.id, parsedLineId, order.ownerUserId);
        if (line == null) {
            throw new IllegalArgumentException("发货单商品不存在或已删除。");
        }
        int updated = mapper.updateShippingOrderLineYiteMaterial(
                order.id,
                parsedLineId,
                order.ownerUserId,
                material,
                access.getSessionUserId()
        );
        if (updated <= 0) {
            throw new IllegalArgumentException("发货单商品不存在或已删除。");
        }
        mapper.updateShippingOrderQuoteLineYiteMaterial(
                order.id,
                parsedLineId,
                order.ownerUserId,
                material,
                access.getSessionUserId()
        );
        persistYiteMaterialAttributeFromShippingLine(order, line, material, access.getSessionUserId());
        return toShippingOrderView(mapper.selectShippingOrderById(order.id), true);
    }

    @Transactional
    public ShippingOrderView createShippingOrder(BusinessAccessContext access, CreateShippingOrderCommand command) {
        if (command == null || command.purchaseOrderIds == null || command.purchaseOrderIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要合并的采购单。");
        }
        Long ownerUserId = ownerUserId(access);
        Long operatorUserId = access.getSessionUserId();
        List<PurchaseOrderRecord> orders = new ArrayList<>();
        LinkedHashSet<Long> orderIds = new LinkedHashSet<>();
        for (String rawOrderId : command.purchaseOrderIds) {
            Long orderId = parseLongId(rawOrderId, "采购单不存在或已删除。");
            if (!orderIds.add(orderId)) {
                continue;
            }
            PurchaseOrderRecord order = requireOrderAccess(access, orderId);
            if (!ownerUserId.equals(order.ownerUserId)) {
                throw new IllegalArgumentException("发货单不能跨老板合并采购单。");
            }
            if (!isOrderSubmitted(order)) {
                throw new IllegalArgumentException("提交后的采购单才可加入发货单。");
            }
            orders.add(order);
        }
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("请选择要合并的采购单。");
        }

        List<PurchaseOrderLogisticsQuoteLineRecord> sourceLines = new ArrayList<>();
        for (PurchaseOrderRecord order : orders) {
            sourceLines.addAll(refreshLogisticsQuoteLines(order, operatorUserId));
        }
        List<Long> itemSiteIds = sourceLines.stream()
                .map(line -> line.purchaseOrderItemSiteId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (itemSiteIds.isEmpty()) {
            throw new IllegalArgumentException("所选采购单没有可加入发货单的商品行。");
        }
        List<String> warnings = new ArrayList<>();
        if (mapper.countActiveShippingOrderLinesByItemSites(itemSiteIds) > 0) {
            warnings.add("所选采购单中已有商品行加入过发货单，本次已按整单重新生成发货单。");
        }

        Long shippingOrderId = mapper.nextShippingOrderId();
        String shippingOrderNo = "SO-" + shippingOrderId;
        Map<String, ShippingOrderSegmentRecord> segmentByKey = new LinkedHashMap<>();
        for (PurchaseOrderLogisticsQuoteLineRecord line : sourceLines) {
            String siteCode = defaultText(line.siteCode, "-");
            String transportMode = normalizeTransportMode(line.plannedTransportMode);
            String key = siteCode + "|" + transportMode;
            ShippingOrderSegmentRecord segment = segmentByKey.get(key);
            if (segment == null) {
                segment = new ShippingOrderSegmentRecord();
                segment.id = mapper.nextShippingOrderSegmentId();
                segment.shippingOrderId = shippingOrderId;
                segment.ownerUserId = ownerUserId;
                segment.siteCode = siteCode;
                segment.transportMode = transportMode;
                segment.segmentNo = shippingOrderNo + "-" + siteCode + "-" + transportMode;
                segment.quoteStatus = LOGISTICS_QUOTE_PENDING;
                segment.shippingSubmitStatus = SHIPPING_NOT_SUBMITTED;
                segment.lineCount = 0;
                segment.skuCount = 0;
                segment.totalQuantity = 0;
                segment.missingYiteMaterialCount = 0;
                segmentByKey.put(key, segment);
            }
            segment.lineCount = nonNull(segment.lineCount) + 1;
            segment.totalQuantity = nonNull(segment.totalQuantity) + nonNull(line.quantity);
            if (YITE_FORWARDER_CODE.equalsIgnoreCase(defaultText(line.forwarderCode, ""))
                    && !StringUtils.hasText(line.yiteMaterial)) {
                segment.missingYiteMaterialCount = nonNull(segment.missingYiteMaterialCount) + 1;
            }
        }
        for (ShippingOrderSegmentRecord segment : segmentByKey.values()) {
            segment.skuCount = (int) sourceLines.stream()
                    .filter(line -> defaultText(line.siteCode, "-").equals(segment.siteCode))
                    .filter(line -> normalizeTransportMode(line.plannedTransportMode).equals(segment.transportMode))
                    .map(line -> stableProductKey(line.sourceStoreCode, line.partnerSku, line.productVariantId))
                    .distinct()
                    .count();
        }
        List<ShippingOrderLineRecord> shippingLines = sourceLines.stream()
                .map(line -> toShippingOrderLineRecord(
                        shippingOrderId,
                        segmentByKey.get(defaultText(line.siteCode, "-") + "|" + normalizeTransportMode(line.plannedTransportMode)),
                        line,
                        mapper.nextShippingOrderLineId()
                ))
                .collect(Collectors.toList());
        ShippingOrderRecord shippingOrder = new ShippingOrderRecord();
        shippingOrder.id = shippingOrderId;
        shippingOrder.ownerUserId = ownerUserId;
        shippingOrder.shippingOrderNo = shippingOrderNo;
        shippingOrder.title = StringUtils.hasText(command.title)
                ? command.title.trim()
                : defaultShippingOrderTitle(orders);
        shippingOrder.status = "DRAFT";
        shippingOrder.purchaseOrderCount = orders.size();
        shippingOrder.lineCount = shippingLines.size();
        shippingOrder.skuCount = (int) shippingLines.stream()
                .map(line -> stableProductKey(line.sourceStoreCode, line.partnerSku, line.productVariantId))
                .distinct()
                .count();
        shippingOrder.totalQuantity = shippingLines.stream().mapToInt(line -> nonNull(line.quantity)).sum();
        shippingOrder.storeSummaryJson = writeJson(countBy(shippingLines, line -> defaultText(line.sourceStoreName, line.sourceStoreCode)));
        shippingOrder.siteSummaryJson = writeJson(countBy(shippingLines, line -> defaultText(line.siteCode, "-")));
        shippingOrder.transportSummaryJson = writeJson(countBy(shippingLines, line -> normalizeTransportMode(line.plannedTransportMode)));
        shippingOrder.quoteStatus = LOGISTICS_QUOTE_PENDING;
        shippingOrder.shippingSubmitStatus = SHIPPING_NOT_SUBMITTED;
        shippingOrder.remark = trimToNull(command.remark);
        mapper.insertShippingOrder(shippingOrder, operatorUserId);
        for (ShippingOrderSegmentRecord segment : segmentByKey.values()) {
            mapper.insertShippingOrderSegment(segment, operatorUserId);
        }
        for (ShippingOrderLineRecord line : shippingLines) {
            mapper.insertShippingOrderLine(line, operatorUserId);
        }

        List<PurchaseOrderLogisticsQuoteLineRecord> quoteLines =
                refreshShippingOrderLogisticsQuoteLines(shippingOrder, operatorUserId);
        for (PurchaseOrderLogisticsQuoteLineRecord quoteLine : quoteLines) {
            mapper.updateShippingOrderLineQuoteLine(
                    shippingOrderId,
                    quoteLine.purchaseOrderItemSiteId,
                    quoteLine.id,
                    operatorUserId
            );
        }
        ShippingOrderView view = toShippingOrderView(mapper.selectShippingOrderById(shippingOrderId), true);
        view.warnings.addAll(warnings);
        return view;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderLogisticsQuoteOptionsView listShippingOrderLogisticsQuoteOptions(
            BusinessAccessContext access,
            String shippingOrderId
    ) {
        return listShippingOrderLogisticsQuoteOptions(access, shippingOrderId, null);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderLogisticsQuoteOptionsView listShippingOrderLogisticsQuoteOptions(
            BusinessAccessContext access,
            String shippingOrderId,
            ShippingOrderSegmentScopeCommand command
    ) {
        ShippingOrderRecord order = requireShippingOrderAccess(
                access,
                parseLongId(shippingOrderId, "发货单不存在或已删除。")
        );
        List<Long> segmentIds = parseRequestedSegmentIds(command);
        List<PurchaseOrderLogisticsQuoteLineRecord> lines = segmentIds.isEmpty()
                ? emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrder(order.id))
                : emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrderSegments(order.id, segmentIds));
        List<LogisticsQuoteExportOption> options = collectLogisticsQuoteExportOptions(lines);
        return toLogisticsQuoteOptionsView(shippingOrderAsPurchaseOrder(order), lines, options);
    }

    @Transactional
    public PurchaseOrderLogisticsQuoteReportExportView exportShippingOrderLogisticsQuoteReport(
            BusinessAccessContext access,
            String shippingOrderId,
            String forwarderCode,
            String routeCode
    ) {
        return exportShippingOrderLogisticsQuoteReport(access, shippingOrderId, forwarderCode, routeCode, null);
    }

    @Transactional
    public PurchaseOrderLogisticsQuoteReportExportView exportShippingOrderLogisticsQuoteReport(
            BusinessAccessContext access,
            String shippingOrderId,
            String forwarderCode,
            String routeCode,
            ShippingOrderSegmentScopeCommand command
    ) {
        String selectedForwarderCode = requiredText(forwarderCode, "请选择报价货代。");
        String selectedRouteCode = requiredText(routeCode, "请选择货代支持的渠道。");
        ShippingOrderRecord shippingOrder = requireShippingOrderAccess(
                access,
                parseLongId(shippingOrderId, "发货单不存在或已删除。")
        );
        Long operatorUserId = access.getSessionUserId();
        List<Long> segmentIds = parseRequestedSegmentIds(command);
        List<PurchaseOrderLogisticsQuoteLineRecord> lines =
                refreshShippingOrderLogisticsQuoteLines(shippingOrder, operatorUserId);
        if (!segmentIds.isEmpty()) {
            lines = lines.stream()
                    .filter(line -> line.shippingOrderSegmentId != null && segmentIds.contains(line.shippingOrderSegmentId))
                    .collect(Collectors.toList());
        }
        List<LogisticsQuoteExportOption> options = collectLogisticsQuoteExportOptions(lines);
        LogisticsQuoteExportOption selectedOption = requireLogisticsQuoteExportOption(
                options,
                selectedForwarderCode,
                selectedRouteCode
        );
        List<PurchaseOrderLogisticsQuoteLineRecord> matchingLines =
                logisticsQuoteReportLines(lines, selectedOption.candidate);
        if (matchingLines.isEmpty()) {
            throw new IllegalArgumentException("当前发货单没有匹配该货代渠道的商品。");
        }
        for (PurchaseOrderLogisticsQuoteLineRecord line : matchingLines) {
            applyLogisticsQuoteChannel(line, selectedOption.candidate);
            mapper.assignLogisticsQuoteLineChannel(line, operatorUserId);
        }
        mapper.refreshShippingOrderQuoteState(shippingOrder.id, matchingLines.get(0), operatorUserId);

        List<PurchaseOrderLogisticsQuoteLineRecord> exportLines = matchingLines.stream()
                .filter(line -> !LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(line.quoteStatus)))
                .collect(Collectors.toList());
        List<PurchaseOrderLogisticsQuoteLineRecord> reportLines = matchingLines;

        PurchaseOrderRecord document = shippingOrderAsPurchaseOrder(shippingOrder);
        PurchaseOrderLogisticsQuoteReportExportView view = new PurchaseOrderLogisticsQuoteReportExportView();
        view.filename = logisticsQuoteReportFilename(shippingOrder.shippingOrderNo, selectedOption);
        view.contentType = logisticsQuoteTemplateContentType(selectedOption.templateType);
        view.rowCount = reportLines.size();
        view.pendingCount = (int) reportLines.stream()
                .filter(line -> LOGISTICS_QUOTE_PENDING.equals(normalizeLogisticsQuoteStatus(line.quoteStatus)))
                .count();
        view.newProductCount = (int) reportLines.stream()
                .filter(line -> Boolean.TRUE.equals(line.isNewProduct))
                .count();
        view.content = buildLogisticsQuoteWorkbook(document, reportLines, selectedOption.templateType);
        List<Long> lineIds = exportLines.stream().map(line -> line.id).filter(id -> id != null).collect(Collectors.toList());
        if (!lineIds.isEmpty()) {
            mapper.markShippingOrderLogisticsQuoteLinesExported(shippingOrder.id, lineIds, operatorUserId);
        }
        List<Long> affectedSegmentIds = matchingLines.stream()
                .map(line -> line.shippingOrderSegmentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (!affectedSegmentIds.isEmpty()) {
            mapper.refreshShippingOrderSegmentState(shippingOrder.id, affectedSegmentIds, matchingLines.get(0), operatorUserId);
            mapper.refreshShippingOrderHeaderState(shippingOrder.id, shippingOrder.ownerUserId, operatorUserId);
        }
        return view;
    }

    @Transactional
    public PurchaseOrderLogisticsQuoteImportView importShippingOrderLogisticsQuoteReport(
            BusinessAccessContext access,
            String shippingOrderId,
            InputStream input,
            String filename
    ) {
        return importShippingOrderLogisticsQuoteReport(access, shippingOrderId, input, filename, null);
    }

    @Transactional
    public PurchaseOrderLogisticsQuoteImportView importShippingOrderLogisticsQuoteReport(
            BusinessAccessContext access,
            String shippingOrderId,
            InputStream input,
            String filename,
            ShippingOrderSegmentScopeCommand command
    ) {
        if (input == null) {
            throw new IllegalArgumentException("请上传物流报价回传表。");
        }
        ShippingOrderRecord shippingOrder = requireShippingOrderAccess(
                access,
                parseLongId(shippingOrderId, "发货单不存在或已删除。")
        );
        List<Long> requestedSegmentIds = parseRequestedSegmentIds(command);
        Set<Long> allowedSegmentIds = new LinkedHashSet<>(requestedSegmentIds);
        PurchaseOrderRecord document = shippingOrderAsPurchaseOrder(shippingOrder);
        PurchaseOrderLogisticsQuoteImportView view = new PurchaseOrderLogisticsQuoteImportView();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("报价回传表为空。");
            }
            boolean recognized = false;
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (isEtTemplateSheet(sheet)) {
                    recognized = true;
                    importEtLogisticsQuoteSheet(document, sheet, access.getSessionUserId(), filename, view, allowedSegmentIds);
                } else if (isYiteTemplateSheet(sheet)) {
                    recognized = true;
                    importYiteLogisticsQuoteSheet(document, sheet, access.getSessionUserId(), filename, view, allowedSegmentIds);
                } else if (isGenericLogisticsQuoteSheet(sheet)) {
                    recognized = true;
                    importGenericLogisticsQuoteSheet(document, sheet, access.getSessionUserId(), filename, view, allowedSegmentIds);
                }
            }
            if (!recognized) {
                throw new IllegalArgumentException("未识别报价回传表模板，请使用发货单导出的义特、易通模板或通用报价确认表。");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("报价回传表读取失败，请确认文件格式为 xls 或 xlsx。", exception);
        }
        List<PurchaseOrderLogisticsQuoteLineRecord> lines =
                requestedSegmentIds.isEmpty()
                        ? emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrder(shippingOrder.id))
                        : emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrderSegments(
                                shippingOrder.id,
                                requestedSegmentIds
                        ));
        PurchaseOrderLogisticsQuoteLineRecord sample = lines.stream()
                .filter(line -> line.forwarderCode != null || line.routeCode != null)
                .findFirst()
                .orElse(new PurchaseOrderLogisticsQuoteLineRecord());
        if (requestedSegmentIds.isEmpty()) {
            mapper.refreshShippingOrderQuoteState(shippingOrder.id, sample, access.getSessionUserId());
        } else {
            mapper.refreshShippingOrderSegmentState(shippingOrder.id, requestedSegmentIds, sample, access.getSessionUserId());
            mapper.refreshShippingOrderHeaderState(shippingOrder.id, shippingOrder.ownerUserId, access.getSessionUserId());
        }
        return view;
    }

    @Transactional
    public ShippingOrderSubmitView submitShippingOrder(BusinessAccessContext access, String shippingOrderId) {
        return submitShippingOrder(access, shippingOrderId, null);
    }

    @Transactional
    public ShippingOrderSubmitView submitShippingOrder(
            BusinessAccessContext access,
            String shippingOrderId,
            ShippingOrderSegmentScopeCommand command
    ) {
        ShippingOrderRecord shippingOrder = requireShippingOrderAccess(
                access,
                parseLongId(shippingOrderId, "发货单不存在或已删除。")
        );
        List<Long> segmentIds = resolveShippingOrderSegmentScope(shippingOrder.id, command);
        if (segmentIds.isEmpty()) {
            return submitWholeShippingOrderWithoutSegments(access, shippingOrder);
        }
        List<PurchaseOrderLogisticsQuoteLineRecord> lines =
                emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrderSegments(shippingOrder.id, segmentIds));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("当前子发货单没有可提交发货的报价行。");
        }
        boolean missingYiteMaterial = lines.stream()
                .filter(line -> YITE_FORWARDER_CODE.equalsIgnoreCase(defaultText(line.forwarderCode, "")))
                .anyMatch(line -> !StringUtils.hasText(line.yiteMaterial));
        if (missingYiteMaterial) {
            throw new IllegalArgumentException("义特材质缺失，不能提交发货单。");
        }
        Long operatorUserId = access.getSessionUserId();
        int submitted = mapper.submitLogisticsQuoteLinesForShippingOrderSegments(shippingOrder.id, segmentIds, operatorUserId);
        if (submitted <= 0) {
            throw new IllegalArgumentException("当前子发货单没有可提交发货的报价行。");
        }
        mapper.refreshShippingOrderSegmentState(shippingOrder.id, segmentIds, lines.get(0), operatorUserId);
        mapper.refreshShippingOrderHeaderState(shippingOrder.id, shippingOrder.ownerUserId, operatorUserId);
        ShippingOrderSubmitView view = new ShippingOrderSubmitView();
        view.shippingOrderId = String.valueOf(shippingOrder.id);
        view.shippingOrderNo = shippingOrder.shippingOrderNo;
        ShippingOrderRecord next = mapper.selectShippingOrderById(shippingOrder.id);
        view.shippingSubmitStatus = next == null
                ? SHIPPING_PARTIAL_SUBMITTED
                : normalizeShippingSubmitStatus(next.shippingSubmitStatus);
        view.submittedLineCount = submitted;
        return view;
    }

    private ShippingOrderSubmitView submitWholeShippingOrderWithoutSegments(
            BusinessAccessContext access,
            ShippingOrderRecord shippingOrder
    ) {
        Long operatorUserId = access.getSessionUserId();
        int submitted = mapper.submitLogisticsQuoteLinesForShippingOrder(shippingOrder.id, operatorUserId);
        if (submitted <= 0) {
            throw new IllegalArgumentException("当前发货单没有可提交发货的报价行。");
        }
        mapper.markShippingOrderSubmitted(shippingOrder.id, shippingOrder.ownerUserId, operatorUserId);
        ShippingOrderSubmitView view = new ShippingOrderSubmitView();
        view.shippingOrderId = String.valueOf(shippingOrder.id);
        view.shippingOrderNo = shippingOrder.shippingOrderNo;
        view.shippingSubmitStatus = SHIPPING_SUBMITTED;
        view.submittedLineCount = submitted;
        return view;
    }

    private List<Long> resolveShippingOrderSegmentScope(
            Long shippingOrderId,
            ShippingOrderSegmentScopeCommand command
    ) {
        if (command != null && command.segmentIds != null && !command.segmentIds.isEmpty()) {
            return parseRequestedSegmentIds(command);
        }
        return emptyIfNull(mapper.listShippingOrderSegments(shippingOrderId)).stream()
                .map(segment -> segment.id)
                .filter(id -> id != null)
                .collect(Collectors.toList());
    }

    private List<Long> parseRequestedSegmentIds(ShippingOrderSegmentScopeCommand command) {
        if (command == null || command.segmentIds == null || command.segmentIds.isEmpty()) {
            return List.of();
        }
        return command.segmentIds.stream()
                .map(rawId -> parseLongId(rawId, "子发货单不存在或已删除。"))
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional
    public LogisticsBillView generateShippingOrderExpectedBill(BusinessAccessContext access, String shippingOrderId) {
        return generateShippingOrderExpectedBill(access, shippingOrderId, null);
    }

    @Transactional
    public LogisticsBillView generateShippingOrderExpectedBill(
            BusinessAccessContext access,
            String shippingOrderId,
            ShippingOrderSegmentScopeCommand command
    ) {
        ShippingOrderRecord shippingOrder = requireShippingOrderAccess(
                access,
                parseLongId(shippingOrderId, "发货单不存在或已删除。")
        );
        Long operatorUserId = access.getSessionUserId();
        List<Long> requestedSegmentIds = parseRequestedSegmentIds(command);
        if (requestedSegmentIds.size() > 1) {
            throw new IllegalArgumentException("一次只能选择一个子发货单生成账单。");
        }
        List<PurchaseOrderLogisticsQuoteLineRecord> quoteLines = requestedSegmentIds.isEmpty()
                ? emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrder(shippingOrder.id))
                : emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrderSegments(shippingOrder.id, requestedSegmentIds));
        if (quoteLines.isEmpty()) {
            throw new IllegalArgumentException("发货单没有可生成账单的报价行。");
        }
        List<PurchaseOrderLogisticsQuoteLineRecord> confirmedLines = new ArrayList<>();
        for (PurchaseOrderLogisticsQuoteLineRecord line : quoteLines) {
            if (!LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(line.quoteStatus))) {
                throw new IllegalArgumentException("发货单还有物流报价未确认，不能生成预估账单。");
            }
            confirmedLines.add(line);
        }

        PurchaseOrderLogisticsQuoteLineRecord sample = confirmedLines.get(0);
        Long billingSegmentId = requestedSegmentIds.isEmpty() ? null : sample.shippingOrderSegmentId;
        String billingSegmentNo = requestedSegmentIds.isEmpty() ? null : sample.shippingOrderSegmentNo;
        mapper.cancelOpenLogisticsExpectedBills(shippingOrder.ownerUserId, shippingOrder.id, billingSegmentId, operatorUserId);
        mapper.cancelOpenLogisticsBillReconciliations(shippingOrder.ownerUserId, shippingOrder.id, billingSegmentId, operatorUserId);

        BigDecimal exchangeRate = BigDecimal.ONE;
        LogisticsExpectedBillRecord bill = new LogisticsExpectedBillRecord();
        bill.id = mapper.nextLogisticsExpectedBillId();
        bill.ownerUserId = shippingOrder.ownerUserId;
        bill.expectedBillNo = "EB-" + bill.id;
        bill.shippingOrderId = shippingOrder.id;
        bill.shippingOrderNo = shippingOrder.shippingOrderNo;
        bill.shippingOrderTitle = shippingOrder.title;
        bill.shippingOrderSegmentId = billingSegmentId;
        bill.shippingOrderSegmentNo = billingSegmentNo;
        bill.forwarderCode = sample.forwarderCode;
        bill.forwarderName = sample.forwarderName;
        bill.routeCode = sample.routeCode;
        bill.routeName = sample.routeName;
        bill.serviceCode = sample.serviceCode;
        bill.serviceName = sample.serviceName;
        bill.transportMode = sample.plannedTransportMode;
        bill.currency = defaultText(sample.currency, "RMB");
        bill.exchangeRateToCny = exchangeRate;
        bill.billStatus = "GENERATED";
        bill.generatedFrom = "SHIPPING_ORDER_QUOTE";
        bill.componentCount = confirmedLines.size();

        List<LogisticsExpectedBillComponentRecord> components = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseOrderLogisticsQuoteLineRecord line : confirmedLines) {
            BigDecimal expectedAmount = logisticsExpectedAmount(line);
            totalAmount = totalAmount.add(expectedAmount);

            LogisticsExpectedBillComponentRecord component = new LogisticsExpectedBillComponentRecord();
            component.id = mapper.nextLogisticsExpectedBillComponentId();
            component.ownerUserId = shippingOrder.ownerUserId;
            component.expectedBillId = bill.id;
            component.shippingOrderId = shippingOrder.id;
            component.shippingOrderSegmentId = line.shippingOrderSegmentId;
            component.shippingOrderLineId = line.shippingOrderLineId;
            component.quoteLineId = line.id;
            component.productMasterId = line.productMasterId;
            component.productVariantId = line.productVariantId;
            component.barcode = line.barcode;
            component.pskuCode = line.pskuCode;
            component.siteCode = line.siteCode;
            component.feeType = "HEADHAUL";
            component.rawFeeName = "头程物流报价";
            component.quantity = line.quantity == null ? null : BigDecimal.valueOf(line.quantity.longValue());
            component.chargeQuantity = component.quantity;
            component.chargeUnit = defaultText(line.billingUnit, "UNKNOWN");
            component.unitPrice = line.unitPrice;
            component.currency = defaultText(line.currency, bill.currency);
            component.exchangeRateToCny = exchangeRate;
            component.expectedAmount = expectedAmount;
            component.expectedAmountCny = expectedAmount.multiply(exchangeRate);
            component.allocationBasis = "QUOTE_LINE";
            component.rawSnapshotJson = productForwarderChannelQuoteSnapshot(line);
            components.add(component);
        }

        bill.expectedTotalAmount = totalAmount;
        bill.expectedTotalCny = totalAmount.multiply(exchangeRate);
        bill.rawSnapshotJson = logisticsExpectedBillSnapshot(shippingOrder, confirmedLines);
        mapper.insertLogisticsExpectedBill(bill, operatorUserId);
        for (LogisticsExpectedBillComponentRecord component : components) {
            mapper.insertLogisticsExpectedBillComponent(component, operatorUserId);
        }

        LogisticsBillReconciliationRecord reconciliation = new LogisticsBillReconciliationRecord();
        reconciliation.id = mapper.nextLogisticsBillReconciliationId();
        reconciliation.ownerUserId = shippingOrder.ownerUserId;
        reconciliation.shippingOrderId = shippingOrder.id;
        reconciliation.shippingOrderSegmentId = billingSegmentId;
        reconciliation.expectedBillId = bill.id;
        reconciliation.reconciliationNo = "REC-" + reconciliation.id;
        reconciliation.reconciliationStatus = "PENDING_ACTUAL_BILL";
        reconciliation.expectedTotalCny = bill.expectedTotalCny;
        reconciliation.matchedComponentCount = 0;
        reconciliation.unmatchedExpectedCount = components.size();
        reconciliation.unmatchedActualCount = 0;
        mapper.insertLogisticsBillReconciliation(reconciliation, operatorUserId);

        LogisticsBillView view = toLogisticsBillView(bill, components);
        view.reconciliationStatus = reconciliation.reconciliationStatus;
        return view;
    }

    private BigDecimal logisticsExpectedAmount(PurchaseOrderLogisticsQuoteLineRecord line) {
        if (line.estimatedAmount != null) {
            return line.estimatedAmount;
        }
        if (line.unitPrice != null && line.quantity != null) {
            return line.unitPrice.multiply(BigDecimal.valueOf(line.quantity.longValue()));
        }
        throw new IllegalArgumentException("报价行缺少确认金额或单价，不能生成预估账单。");
    }

    private String logisticsExpectedBillSnapshot(
            ShippingOrderRecord shippingOrder,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("shippingOrderId", shippingOrder.id);
        snapshot.put("shippingOrderNo", shippingOrder.shippingOrderNo);
        snapshot.put("lineCount", lines.size());
        snapshot.put("quoteLineIds", lines.stream().map(line -> line.id).collect(Collectors.toList()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private LogisticsBillView toLogisticsBillView(
            LogisticsExpectedBillRecord bill,
            List<LogisticsExpectedBillComponentRecord> components
    ) {
        LogisticsBillView view = new LogisticsBillView();
        view.id = bill.id == null ? null : String.valueOf(bill.id);
        view.expectedBillNo = bill.expectedBillNo;
        view.shippingOrderId = bill.shippingOrderId == null ? null : String.valueOf(bill.shippingOrderId);
        view.shippingOrderNo = bill.shippingOrderNo;
        view.shippingOrderTitle = bill.shippingOrderTitle;
        view.shippingOrderSegmentId = bill.shippingOrderSegmentId == null ? null : String.valueOf(bill.shippingOrderSegmentId);
        view.shippingOrderSegmentNo = bill.shippingOrderSegmentNo;
        view.forwarderCode = bill.forwarderCode;
        view.forwarderName = bill.forwarderName;
        view.routeCode = bill.routeCode;
        view.routeName = bill.routeName;
        view.serviceCode = bill.serviceCode;
        view.serviceName = bill.serviceName;
        view.transportMode = bill.transportMode;
        view.currency = bill.currency;
        view.expectedTotalAmount = bill.expectedTotalAmount;
        view.expectedTotalCny = bill.expectedTotalCny;
        view.actualTotalCny = bill.actualTotalCny;
        view.diffAmountCny = bill.diffAmountCny;
        view.componentCount = bill.componentCount == null ? 0 : bill.componentCount;
        view.billStatus = bill.billStatus;
        view.reconciliationStatus = bill.reconciliationStatus;
        view.createdAt = bill.createdAt;
        view.updatedAt = bill.updatedAt;
        for (LogisticsExpectedBillComponentRecord component : emptyIfNull(components)) {
            view.components.add(toLogisticsBillComponentView(component));
        }
        return view;
    }

    private LogisticsBillComponentView toLogisticsBillComponentView(LogisticsExpectedBillComponentRecord component) {
        LogisticsBillComponentView view = new LogisticsBillComponentView();
        view.id = component.id == null ? null : String.valueOf(component.id);
        view.shippingOrderSegmentId = component.shippingOrderSegmentId == null ? null : String.valueOf(component.shippingOrderSegmentId);
        view.shippingOrderLineId = component.shippingOrderLineId == null ? null : String.valueOf(component.shippingOrderLineId);
        view.quoteLineId = component.quoteLineId == null ? null : String.valueOf(component.quoteLineId);
        view.barcode = component.barcode;
        view.pskuCode = component.pskuCode;
        view.siteCode = component.siteCode;
        view.feeType = component.feeType;
        view.quantity = component.quantity;
        view.chargeQuantity = component.chargeQuantity;
        view.chargeUnit = component.chargeUnit;
        view.unitPrice = component.unitPrice;
        view.currency = component.currency;
        view.expectedAmount = component.expectedAmount;
        view.expectedAmountCny = component.expectedAmountCny;
        return view;
    }

    private List<PurchaseOrderLogisticsQuoteLineRecord> refreshShippingOrderLogisticsQuoteLines(
            ShippingOrderRecord shippingOrder,
            Long operatorUserId
    ) {
        List<PurchaseOrderLogisticsQuoteLineRecord> lines =
                emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrder(shippingOrder.id));
        applyYiteMaterialDefaults(lines);
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            line.shippingOrderId = shippingOrder.id;
            line.shippingOrderNo = shippingOrder.shippingOrderNo;
            line.quoteStatus = normalizeLogisticsQuoteStatus(line.quoteStatus);
            line.shippingSubmitStatus = normalizeShippingSubmitStatus(line.shippingSubmitStatus);
            line.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
            line.plannedTransportMode = normalizeTransportMode(line.plannedTransportMode);
            if (line.id == null) {
                line.id = mapper.nextLogisticsQuoteLineId();
                line.quoteStatus = LOGISTICS_QUOTE_PENDING;
                line.shippingSubmitStatus = SHIPPING_NOT_SUBMITTED;
                mapper.insertLogisticsQuoteLine(line, operatorUserId);
            } else {
                mapper.refreshLogisticsQuoteLineSnapshot(line, operatorUserId);
            }
        }
        return lines;
    }

    private ShippingOrderLineRecord toShippingOrderLineRecord(
            Long shippingOrderId,
            ShippingOrderSegmentRecord segment,
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long lineId
    ) {
        ShippingOrderLineRecord row = new ShippingOrderLineRecord();
        row.id = lineId;
        row.shippingOrderId = shippingOrderId;
        row.shippingOrderSegmentId = segment == null ? null : segment.id;
        row.shippingOrderSegmentNo = segment == null ? null : segment.segmentNo;
        row.ownerUserId = line.ownerUserId;
        row.logicalStoreId = line.logicalStoreId;
        PurchaseOrderRecord order = requireOrder(line.purchaseOrderId);
        row.sourceStoreCode = defaultText(line.sourceStoreCode, order.anchorStoreCodeCache);
        row.sourceStoreName = defaultText(line.sourceStoreName, order.projectNameCache);
        row.purchaseOrderId = line.purchaseOrderId;
        row.purchaseOrderNo = line.purchaseOrderNo;
        row.purchaseOrderTitle = line.purchaseOrderTitle;
        row.purchaseOrderItemId = line.purchaseOrderItemId;
        row.purchaseOrderItemSiteId = line.purchaseOrderItemSiteId;
        row.productMasterId = line.productMasterId;
        row.productVariantId = line.productVariantId;
        row.skuParent = line.skuParent;
        row.partnerSku = line.partnerSku;
        row.titleCache = line.titleCache;
        row.imageUrlCache = line.imageUrlCache;
        row.siteCode = line.siteCode;
        row.pskuCode = line.pskuCode;
        row.yiteMaterial = line.yiteMaterial;
        row.plannedTransportMode = normalizeTransportMode(line.plannedTransportMode);
        row.quantity = nonNull(line.quantity);
        row.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
        row.quoteLineId = line.id;
        return row;
    }

    private String defaultShippingOrderTitle(List<PurchaseOrderRecord> orders) {
        if (orders == null || orders.isEmpty()) {
            return "发货单";
        }
        String firstTitle = defaultText(orders.get(0).title, orders.get(0).orderNo);
        if (orders.size() == 1) {
            return firstTitle + " 发货单";
        }
        return firstTitle + " 等 " + orders.size() + " 张采购单";
    }

    private Map<String, Integer> countBy(
            List<ShippingOrderLineRecord> lines,
            Function<ShippingOrderLineRecord, String> classifier
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingOrderLineRecord line : emptyIfNull(lines)) {
            String key = defaultText(classifier.apply(line), "-");
            result.merge(key, nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

    private PurchaseOrderRecord shippingOrderAsPurchaseOrder(ShippingOrderRecord shippingOrder) {
        PurchaseOrderRecord record = new PurchaseOrderRecord();
        record.id = shippingOrder.id;
        record.ownerUserId = shippingOrder.ownerUserId;
        record.orderNo = shippingOrder.shippingOrderNo;
        record.title = shippingOrder.title;
        record.status = shippingOrder.status;
        return record;
    }

    private ShippingOrderView toShippingOrderView(ShippingOrderRecord order, boolean includeLines) {
        ShippingOrderView view = new ShippingOrderView();
        if (order == null) {
            return view;
        }
        view.id = String.valueOf(order.id);
        view.shippingOrderNo = order.shippingOrderNo;
        view.title = order.title;
        view.status = order.status;
        view.purchaseOrderCount = nonNull(order.purchaseOrderCount);
        view.lineCount = nonNull(order.lineCount);
        view.skuCount = nonNull(order.skuCount);
        view.totalQuantity = nonNull(order.totalQuantity);
        view.missingYiteMaterialCount = nonNull(order.missingYiteMaterialCount);
        view.quoteStatus = normalizeLogisticsQuoteStatus(order.quoteStatus);
        view.shippingSubmitStatus = normalizeShippingSubmitStatus(order.shippingSubmitStatus);
        view.forwarderName = order.forwarderName;
        view.routeName = order.routeName;
        view.submittedAt = order.submittedAt;
        view.remark = order.remark;
        view.createdAt = order.createdAt;
        view.updatedAt = order.updatedAt;
        view.segments = emptyIfNull(mapper.listShippingOrderSegments(order.id)).stream()
                .map(this::toShippingOrderSegmentView)
                .collect(Collectors.toList());
        if (includeLines) {
            view.lines = emptyIfNull(mapper.listShippingOrderLines(order.id)).stream()
                    .map(this::toShippingOrderLineView)
                    .collect(Collectors.toList());
        }
        return view;
    }

    private ShippingOrderSegmentView toShippingOrderSegmentView(ShippingOrderSegmentRecord segment) {
        ShippingOrderSegmentView view = new ShippingOrderSegmentView();
        view.id = String.valueOf(segment.id);
        view.segmentNo = segment.segmentNo;
        view.siteCode = segment.siteCode;
        view.transportMode = normalizeTransportMode(segment.transportMode);
        view.forwarderCode = segment.forwarderCode;
        view.forwarderName = segment.forwarderName;
        view.routeCode = segment.routeCode;
        view.routeName = segment.routeName;
        view.serviceCode = segment.serviceCode;
        view.serviceName = segment.serviceName;
        view.quoteStatus = normalizeLogisticsQuoteStatus(segment.quoteStatus);
        view.shippingSubmitStatus = normalizeShippingSubmitStatus(segment.shippingSubmitStatus);
        view.lineCount = nonNull(segment.lineCount);
        view.skuCount = nonNull(segment.skuCount);
        view.totalQuantity = nonNull(segment.totalQuantity);
        view.missingYiteMaterialCount = nonNull(segment.missingYiteMaterialCount);
        view.submittedAt = segment.submittedAt;
        return view;
    }

    private ShippingOrderLineView toShippingOrderLineView(ShippingOrderLineRecord line) {
        ShippingOrderLineView view = new ShippingOrderLineView();
        view.id = String.valueOf(line.id);
        view.shippingOrderSegmentId = line.shippingOrderSegmentId == null ? null : String.valueOf(line.shippingOrderSegmentId);
        view.shippingOrderSegmentNo = line.shippingOrderSegmentNo;
        view.sourceStoreCode = line.sourceStoreCode;
        view.sourceStoreName = line.sourceStoreName;
        view.purchaseOrderId = String.valueOf(line.purchaseOrderId);
        view.purchaseOrderNo = line.purchaseOrderNo;
        view.purchaseOrderTitle = line.purchaseOrderTitle;
        view.purchaseOrderItemId = String.valueOf(line.purchaseOrderItemId);
        view.purchaseOrderItemSiteId = String.valueOf(line.purchaseOrderItemSiteId);
        view.partnerSku = line.partnerSku;
        view.skuParent = line.skuParent;
        view.barcode = line.barcode;
        view.productTitle = defaultText(line.titleCache, line.partnerSku);
        view.productTitleCn = defaultText(line.titleCache, line.partnerSku);
        view.productTitleEn = defaultText(line.titleEn, line.titleCache);
        view.productImageUrl = NoonImageUrlNormalizer.normalize(line.imageUrlCache);
        view.siteCode = line.siteCode;
        view.pskuCode = line.pskuCode;
        view.yiteMaterial = line.yiteMaterial;
        view.plannedTransportMode = normalizeTransportMode(line.plannedTransportMode);
        view.quoteStatus = normalizeLogisticsQuoteStatus(line.quoteStatus);
        view.shippingSubmitStatus = normalizeShippingSubmitStatus(line.shippingSubmitStatus);
        view.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
        view.unitPrice = line.unitPrice;
        view.currency = line.currency;
        view.billingUnit = line.billingUnit;
        view.quantity = nonNull(line.quantity);
        return view;
    }

    private List<PurchaseOrderLogisticsQuoteLineRecord> refreshLogisticsQuoteLines(
            PurchaseOrderRecord order,
            Long operatorUserId
    ) {
        List<PurchaseOrderLogisticsQuoteLineRecord> lines =
                emptyIfNull(mapper.listLogisticsQuoteCandidatesByOrder(order.id));
        applyYiteMaterialDefaults(lines);
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            line.quoteStatus = normalizeLogisticsQuoteStatus(line.quoteStatus);
            line.shippingSubmitStatus = normalizeShippingSubmitStatus(line.shippingSubmitStatus);
            line.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
            line.plannedTransportMode = normalizeTransportMode(line.plannedTransportMode);
            if (line.id == null) {
                line.id = mapper.nextLogisticsQuoteLineId();
                line.quoteStatus = LOGISTICS_QUOTE_PENDING;
                line.shippingSubmitStatus = SHIPPING_NOT_SUBMITTED;
                mapper.insertLogisticsQuoteLine(line, operatorUserId);
            } else {
                mapper.refreshLogisticsQuoteLineSnapshot(line, operatorUserId);
            }
        }
        return lines;
    }

    private void applyYiteMaterialDefaults(List<PurchaseOrderLogisticsQuoteLineRecord> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Long ownerUserId = lines.stream()
                .map(line -> line.ownerUserId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
        if (ownerUserId == null) {
            return;
        }
        List<Long> productVariantIds = lines.stream()
                .map(line -> line.productVariantId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        List<String> partnerSkus = lines.stream()
                .map(line -> trimToNull(line.partnerSku))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (productVariantIds.isEmpty() && partnerSkus.isEmpty()) {
            return;
        }
        Map<String, String> materialByProduct = new HashMap<>();
        for (ProductForwarderDeclarationAttributeRecord attribute : emptyIfNull(
                mapper.listProductForwarderDeclarationAttributes(
                        ownerUserId,
                        YITE_FORWARDER_CODE,
                        YITE_MATERIAL_ATTRIBUTE_CODE,
                        productVariantIds,
                        partnerSkus
                )
        )) {
            if (attribute == null) {
                continue;
            }
            String material = trimToNull(attribute.attributeValue);
            if (YITE_MATERIAL_OPTIONS.contains(material)) {
                materialByProduct.putIfAbsent(
                        stableProductKey(attribute.sourceStoreCode, attribute.partnerSku, attribute.productVariantId),
                        material
                );
                if (!StringUtils.hasText(attribute.sourceStoreCode) && StringUtils.hasText(attribute.partnerSku)) {
                    materialByProduct.putIfAbsent(
                            stableProductKey(null, attribute.partnerSku, attribute.productVariantId),
                            material
                    );
                }
            }
        }
        if (materialByProduct.isEmpty()) {
            return;
        }
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            if ((line.productVariantId == null && !StringUtils.hasText(line.partnerSku))
                    || StringUtils.hasText(line.yiteMaterial)) {
                continue;
            }
            String material = materialByProduct.get(stableProductKey(line.sourceStoreCode, line.partnerSku, line.productVariantId));
            if (!StringUtils.hasText(material) && StringUtils.hasText(line.partnerSku)) {
                material = materialByProduct.get(stableProductKey(null, line.partnerSku, line.productVariantId));
            }
            if (StringUtils.hasText(material)) {
                line.yiteMaterial = material;
            }
        }
    }

    private void persistYiteMaterialAttributeFromShippingLine(
            ShippingOrderRecord order,
            ShippingOrderLineRecord line,
            String material,
            Long operatorUserId
    ) {
        if (order == null || line == null || line.productVariantId == null) {
            return;
        }
        if (!StringUtils.hasText(material)) {
            mapper.softDeleteProductForwarderDeclarationAttribute(
                    order.ownerUserId,
                    line.sourceStoreCode,
                    line.logicalStoreId,
                    line.partnerSku,
                    line.productVariantId,
                    YITE_FORWARDER_CODE,
                    YITE_MATERIAL_ATTRIBUTE_CODE,
                    operatorUserId
            );
            return;
        }
        if (line.productMasterId == null) {
            return;
        }
        ProductForwarderDeclarationAttributeRecord attribute = new ProductForwarderDeclarationAttributeRecord();
        attribute.id = mapper.nextProductForwarderDeclarationAttributeId();
        attribute.ownerUserId = order.ownerUserId;
        attribute.productMasterId = line.productMasterId;
        attribute.productVariantId = line.productVariantId;
        attribute.logicalStoreId = line.logicalStoreId;
        attribute.sourceStoreCode = line.sourceStoreCode;
        attribute.partnerSku = line.partnerSku;
        attribute.barcode = trimToNull(line.barcode);
        attribute.forwarderCode = YITE_FORWARDER_CODE;
        attribute.attributeCode = YITE_MATERIAL_ATTRIBUTE_CODE;
        attribute.attributeValue = material;
        attribute.sourceShippingOrderId = order.id;
        attribute.sourceShippingOrderLineId = line.id;
        mapper.upsertProductForwarderDeclarationAttribute(attribute, operatorUserId);
    }

    private List<LogisticsQuoteExportOption> collectLogisticsQuoteExportOptions(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        List<PurchaseOrderLogisticsQuoteLineRecord> routableLines = routableLogisticsQuoteLines(lines);
        Map<String, List<PurchaseOrderLogisticsQuoteLineRecord>> linesBySiteTransport = routableLines.stream()
                .collect(Collectors.groupingBy(
                        line -> logisticsQuoteRouteKey(line.siteCode, line.plannedTransportMode),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<LogisticsQuoteExportOption> options = new ArrayList<>();
        for (String transportMode : List.of(TRANSPORT_SEA, TRANSPORT_AIR)) {
            List<String> siteCodes = routableLines.stream()
                    .filter(line -> transportMode.equals(normalizeTransportMode(line.plannedTransportMode)))
                    .map(line -> normalizeSiteCode(line.siteCode))
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            if (siteCodes.isEmpty()) {
                continue;
            }
            List<ForwarderRouteRecommendationRecord> candidates =
                    mapper.listRouteRecommendationCandidates(siteCodes, transportMode);
            for (ForwarderRouteRecommendationRecord candidate : emptyIfNull(candidates)) {
                List<PurchaseOrderLogisticsQuoteLineRecord> matchingLines = linesBySiteTransport.getOrDefault(
                        logisticsQuoteRouteKey(candidate.siteCode, candidate.transportMode),
                        Collections.emptyList()
                );
                if (matchingLines.isEmpty()) {
                    continue;
                }
                LogisticsQuoteExportOption option = new LogisticsQuoteExportOption();
                option.candidate = candidate;
                option.templateType = logisticsQuoteTemplateType(candidate);
                option.pendingLineCount = (int) matchingLines.stream()
                        .filter(line -> !LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(line.quoteStatus)))
                        .count();
                option.newProductLineCount = (int) matchingLines.stream()
                        .filter(line -> Boolean.TRUE.equals(line.isNewProduct))
                        .count();
                options.add(option);
            }
        }
        return options.stream()
                .sorted(Comparator
                        .comparing((LogisticsQuoteExportOption option) -> defaultText(option.candidate.forwarderName, option.candidate.forwarderCode))
                        .thenComparing(option -> defaultText(option.candidate.siteCode, ""))
                        .thenComparing(option -> defaultText(option.candidate.transportMode, ""))
                        .thenComparing(option -> defaultText(option.candidate.routeName, option.candidate.routeCode)))
                .collect(Collectors.toList());
    }

    private PurchaseOrderLogisticsQuoteOptionsView toLogisticsQuoteOptionsView(
            PurchaseOrderRecord order,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            List<LogisticsQuoteExportOption> options
    ) {
        PurchaseOrderLogisticsQuoteOptionsView view = new PurchaseOrderLogisticsQuoteOptionsView();
        view.purchaseOrderId = String.valueOf(order.id);
        view.purchaseOrderNo = order.orderNo;
        view.pendingLineCount = exportableLogisticsQuoteLines(lines).size();
        view.unsupportedChannelCount = (int) options.stream()
                .filter(option -> !StringUtils.hasText(option.templateType))
                .count();
        Map<String, PurchaseOrderLogisticsQuoteForwarderOptionView> forwarderViews = new LinkedHashMap<>();
        for (LogisticsQuoteExportOption option : options) {
            if (!StringUtils.hasText(option.templateType)) {
                continue;
            }
            String forwarderKey = defaultText(option.candidate.forwarderCode, option.candidate.forwarderName);
            PurchaseOrderLogisticsQuoteForwarderOptionView forwarder = forwarderViews.computeIfAbsent(
                    forwarderKey,
                    ignored -> toLogisticsQuoteForwarderOptionView(option)
            );
            forwarder.channels.add(toLogisticsQuoteChannelOptionView(option));
        }
        view.forwarders = new ArrayList<>(forwarderViews.values());
        return view;
    }

    private PurchaseOrderLogisticsQuoteForwarderOptionView toLogisticsQuoteForwarderOptionView(
            LogisticsQuoteExportOption option
    ) {
        PurchaseOrderLogisticsQuoteForwarderOptionView view = new PurchaseOrderLogisticsQuoteForwarderOptionView();
        view.forwarderCode = option.candidate.forwarderCode;
        view.forwarderName = defaultText(option.candidate.forwarderName, option.candidate.forwarderCode);
        view.templateType = option.templateType;
        view.templateName = logisticsQuoteTemplateName(option.templateType);
        return view;
    }

    private PurchaseOrderLogisticsQuoteChannelOptionView toLogisticsQuoteChannelOptionView(
            LogisticsQuoteExportOption option
    ) {
        ForwarderRouteRecommendationRecord candidate = option.candidate;
        PurchaseOrderLogisticsQuoteChannelOptionView view = new PurchaseOrderLogisticsQuoteChannelOptionView();
        view.routeCode = candidate.routeCode;
        view.routeName = candidate.routeName;
        view.serviceCode = candidate.serviceCode;
        view.serviceName = candidate.serviceName;
        view.siteCode = candidate.siteCode;
        view.transportMode = normalizeTransportMode(candidate.transportMode);
        view.transportModeLabel = transportModeLabel(candidate.transportMode);
        view.country = candidate.country;
        view.targetPlatform = candidate.targetPlatform;
        view.deliveryCity = candidate.deliveryCity;
        view.destinationNode = candidate.destinationNode;
        view.transitTimeText = candidate.transitTimeText;
        view.priceSummary = seaPriceSummary(candidate);
        view.pendingLineCount = option.pendingLineCount;
        view.newProductLineCount = option.newProductLineCount;
        return view;
    }

    private LogisticsQuoteExportOption requireLogisticsQuoteExportOption(
            List<LogisticsQuoteExportOption> options,
            String forwarderCode,
            String routeCode
    ) {
        return options.stream()
                .filter(option -> StringUtils.hasText(option.templateType))
                .filter(option -> sameCode(option.candidate.forwarderCode, forwarderCode))
                .filter(option -> sameCode(option.candidate.routeCode, routeCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("请选择当前采购单支持且已配置导出模板的货代渠道。"));
    }

    private List<PurchaseOrderLogisticsQuoteLineRecord> exportableLogisticsQuoteLines(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        return routableLogisticsQuoteLines(lines).stream()
                .filter(line -> !LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(line.quoteStatus)))
                .collect(Collectors.toList());
    }

    static List<PurchaseOrderLogisticsQuoteLineRecord> logisticsQuoteReportLines(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            ForwarderRouteRecommendationRecord candidate
    ) {
        return routableLogisticsQuoteLines(lines).stream()
                .filter(line -> matchesLogisticsQuoteOption(line, candidate))
                .collect(Collectors.toList());
    }

    private static List<PurchaseOrderLogisticsQuoteLineRecord> routableLogisticsQuoteLines(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        return (lines == null ? List.<PurchaseOrderLogisticsQuoteLineRecord>of() : lines).stream()
                .peek(line -> {
                    line.quoteStatus = normalizeLogisticsQuoteStatus(line.quoteStatus);
                    line.plannedTransportMode = normalizeTransportMode(line.plannedTransportMode);
                })
                .filter(line -> StringUtils.hasText(normalizeSiteCode(line.siteCode)))
                .filter(line -> TRANSPORT_SEA.equals(normalizeTransportMode(line.plannedTransportMode))
                        || TRANSPORT_AIR.equals(normalizeTransportMode(line.plannedTransportMode)))
                .collect(Collectors.toList());
    }

    private static boolean matchesLogisticsQuoteOption(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        return sameCode(normalizeSiteCode(line.siteCode), normalizeSiteCode(candidate.siteCode))
                && normalizeTransportMode(line.plannedTransportMode).equals(normalizeTransportMode(candidate.transportMode));
    }

    private void applyLogisticsQuoteChannel(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        line.forwarderCode = candidate.forwarderCode;
        line.forwarderName = defaultText(candidate.forwarderName, candidate.forwarderCode);
        line.routeCode = candidate.routeCode;
        line.routeName = candidate.routeName;
        line.serviceCode = candidate.serviceCode;
        line.serviceName = candidate.serviceName;
        line.currency = defaultText(candidate.currency, "RMB");
        line.billingUnit = candidate.billingUnit;
    }

    private String logisticsQuoteRouteKey(String siteCode, String transportMode) {
        return defaultText(normalizeSiteCode(siteCode), "") + ":" + normalizeTransportMode(transportMode);
    }

    private String logisticsQuoteTemplateType(ForwarderRouteRecommendationRecord candidate) {
        if (isYiteForwarder(candidate)) {
            return YITE_TEMPLATE_TYPE;
        }
        if (isEtForwarder(candidate)) {
            return ET_TEMPLATE_TYPE;
        }
        return null;
    }

    private String logisticsQuoteTemplateName(String templateType) {
        if (YITE_TEMPLATE_TYPE.equals(templateType)) {
            return YITE_TEMPLATE_NAME;
        }
        if (ET_TEMPLATE_TYPE.equals(templateType)) {
            return ET_TEMPLATE_NAME;
        }
        return null;
    }

    private String logisticsQuoteTemplateFileExtension(String templateType) {
        return ET_TEMPLATE_TYPE.equals(templateType) ? "xlsx" : "xls";
    }

    private String logisticsQuoteReportFilename(String documentNo, LogisticsQuoteExportOption selectedOption) {
        String templateType = selectedOption == null ? null : selectedOption.templateType;
        String prefix = logisticsQuoteTemplateFilenamePrefix(templateType);
        if (StringUtils.hasText(prefix)) {
            return safeFilePart(prefix)
                    + "-"
                    + safeFilePart(documentNo)
                    + "."
                    + logisticsQuoteTemplateFileExtension(templateType);
        }
        List<String> parts = new ArrayList<>();
        parts.add(safeFilePart(documentNo));
        ForwarderRouteRecommendationRecord candidate = selectedOption == null ? null : selectedOption.candidate;
        parts.add(safeFilePart(defaultText(candidate == null ? null : candidate.forwarderName,
                candidate == null ? null : candidate.forwarderCode)));
        parts.add(safeFilePart(defaultText(candidate == null ? null : candidate.routeName,
                candidate == null ? null : candidate.routeCode)));
        return String.join("-", parts)
                + "-报价表."
                + logisticsQuoteTemplateFileExtension(templateType);
    }

    private String logisticsQuoteTemplateFilenamePrefix(String templateType) {
        if (YITE_TEMPLATE_TYPE.equals(templateType)) {
            return YITE_FILENAME_PREFIX;
        }
        if (ET_TEMPLATE_TYPE.equals(templateType)) {
            return ET_FILENAME_PREFIX;
        }
        return null;
    }

    private String logisticsQuoteTemplateContentType(String templateType) {
        return ET_TEMPLATE_TYPE.equals(templateType)
                ? LOGISTICS_QUOTE_XLSX_CONTENT_TYPE
                : LOGISTICS_QUOTE_XLS_CONTENT_TYPE;
    }

    private boolean isYiteForwarder(ForwarderRouteRecommendationRecord candidate) {
        String code = candidate == null ? null : candidate.forwarderCode;
        String name = candidate == null ? null : candidate.forwarderName;
        return sameCode(code, YITE_FORWARDER_CODE)
                || sameCode(code, "YITE")
                || (StringUtils.hasText(name) && name.contains("义特"));
    }

    private boolean isEtForwarder(ForwarderRouteRecommendationRecord candidate) {
        String code = candidate == null ? null : candidate.forwarderCode;
        String name = candidate == null ? null : candidate.forwarderName;
        return sameCode(code, ET_FORWARDER_CODE)
                || sameCode(code, "YITONG")
                || (StringUtils.hasText(name) && name.contains("易通"));
    }

    private static boolean sameCode(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private byte[] buildLogisticsQuoteWorkbook(
            PurchaseOrderRecord order,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            String templateType
    ) {
        if (ET_TEMPLATE_TYPE.equals(templateType)) {
            return buildEtLogisticsQuoteWorkbook(order, lines);
        }
        return buildYiteLogisticsQuoteWorkbook(order, lines);
    }

    private byte[] buildYiteLogisticsQuoteWorkbook(
            PurchaseOrderRecord order,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        try (HSSFWorkbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Map<String, YiteProductImage> imageCache = new HashMap<>();
            Map<String, List<PurchaseOrderLogisticsQuoteLineRecord>> grouped =
                    lines.stream().collect(Collectors.groupingBy(
                            this::yiteSheetKey,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            if (grouped.isEmpty()) {
                Sheet sheet = workbook.createSheet("模板");
                writeYiteTemplateSheet(workbook, sheet, order, Collections.emptyList(), imageCache);
            } else {
                int sheetNo = 0;
                for (List<PurchaseOrderLogisticsQuoteLineRecord> sheetLines : grouped.values()) {
                    Sheet sheet = workbook.createSheet(sheetNo == 0 && grouped.size() == 1
                            ? "模板"
                            : safeSheetName("义特-" + yiteServiceName(firstLine(sheetLines))));
                    writeYiteTemplateSheet(workbook, sheet, order, sheetLines, imageCache);
                    sheetNo += 1;
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("物流报价确认表生成失败。", exception);
        }
    }

    private byte[] buildEtLogisticsQuoteWorkbook(
            PurchaseOrderRecord order,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Map<String, YiteProductImage> imageCache = new HashMap<>();
            Sheet template = workbook.createSheet("装箱清单");
            writeEtTemplateSheet(workbook, template, lines, imageCache);
            Sheet guide = workbook.createSheet("填表指南");
            writeEtGuideSheet(workbook, guide);
            writeEtWarehouseNoticeSheet(workbook, workbook.createSheet("仓单地址及须知"));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("易通装箱审核模板生成失败。", exception);
        }
    }

    private void writeEtTemplateSheet(
            Workbook workbook,
            Sheet sheet,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            Map<String, YiteProductImage> imageCache
    ) {
        CellStyle instructionStyle = etInstructionStyle(workbook);
        CellStyle headerStyle = etHeaderStyle(workbook);
        CellStyle bodyStyle = etBodyStyle(workbook, false);

        Row instruction = sheet.createRow(0);
        instruction.setHeightInPoints(75);
        writeStringCell(instruction, 0, ET_INSTRUCTION_TEXT, instructionStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 20));

        Row header = sheet.createRow(ET_HEADER_ROW_INDEX);
        header.setHeightInPoints(25);
        for (int index = 0; index < ET_DETAIL_HEADERS.length; index++) {
            writeStringCell(header, index, ET_DETAIL_HEADERS[index], headerStyle);
        }
        for (int index = 0; index < ET_HIDDEN_HEADERS.length; index++) {
            writeStringCell(header, ET_HIDDEN_START_COLUMN + index, ET_HIDDEN_HEADERS[index], headerStyle);
            sheet.setColumnHidden(ET_HIDDEN_START_COLUMN + index, true);
        }
        sheet.setColumnHidden(ET_HIDDEN_START_COLUMN + ET_HIDDEN_HEADERS.length, true);

        Drawing<?> drawing = sheet.createDrawingPatriarch();
        int displayRowCount = Math.max(30, lines == null ? 0 : lines.size());
        for (int index = 0; index < displayRowCount; index++) {
            Row row = sheet.createRow(ET_FIRST_DATA_ROW_INDEX + index);
            row.setHeightInPoints(ET_PRODUCT_IMAGE_ROW_HEIGHT_POINTS);
            for (int column = 0; column < ET_VISIBLE_COLUMN_COUNT; column++) {
                writeStringCell(row, column, "", bodyStyle);
            }
            if (lines != null && index < lines.size()) {
                writeEtTemplateRow(workbook, sheet, drawing, row, lines.get(index), index, bodyStyle, imageCache);
            }
        }

        configureEtTemplateColumns(sheet);
    }

    private void writeEtGuideSheet(Workbook workbook, Sheet sheet) {
        CellStyle instructionStyle = etInstructionStyle(workbook);
        CellStyle headerStyle = etHeaderStyle(workbook);
        CellStyle bodyStyle = etBodyStyle(workbook, false);
        CellStyle redBodyStyle = etBodyStyle(workbook, true);

        Row instruction = sheet.createRow(0);
        instruction.setHeightInPoints(52.5F);
        writeStringCell(instruction, 0, ET_INSTRUCTION_TEXT, instructionStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 20));

        Row header = sheet.createRow(ET_HEADER_ROW_INDEX);
        header.setHeightInPoints(25);
        for (int index = 0; index < ET_DETAIL_HEADERS.length; index++) {
            writeStringCell(header, index, ET_DETAIL_HEADERS[index], headerStyle);
        }
        Object[][] examples = {
                {"1/20-18/20", 40, 50, 50, 20, 30, "sku1", "sku1", "T-shirt", "T恤", "1", "Basaa\nNusha", "Basaa\nNusha", "", "锦纶", "否", "否", "否", "否", "否", ""},
                {"19/20", 30, 50, 50, 20, 15, "sku2", "sku2", "Jeans", "牛仔裤", "1", "BaideM", "BaideM", "", "布", "否", "否", "否", "否", "否", ""},
                {"19/20", 30, 50, 50, 20, 25, "sku3", "sku3", "Shoes", "鞋子", "1", "Dounal", "Dounal", "", "仿麂皮", "否", "否", "否", "否", "否", ""},
                {"20/20", 10, 15, 10, 2, 250, "sku4", "sku4", "Earings", "耳饰", "1", "SLL", "SLL", "", "镀金", "否", "否", "否", "否", "否", ""}
        };
        for (int rowIndex = 0; rowIndex < examples.length; rowIndex++) {
            Row row = sheet.createRow(ET_FIRST_DATA_ROW_INDEX + rowIndex);
            row.setHeightInPoints(48);
            CellStyle rowStyle = rowIndex == 1 || rowIndex == 2 ? redBodyStyle : bodyStyle;
            for (int column = 0; column < examples[rowIndex].length; column++) {
                writeStringCell(row, column, examples[rowIndex][column], rowStyle);
            }
        }
        configureEtTemplateColumns(sheet);
    }

    private void writeEtWarehouseNoticeSheet(Workbook workbook, Sheet sheet) {
        CellStyle style = etInstructionStyle(workbook);
        Row row = sheet.createRow(0);
        row.setHeightInPoints(380);
        writeStringCell(row, 0, "佛山仓收货地址：佛山市南海区里水镇和桂工业园B区顺福东路1号(中联精犇产业园内左边仓库)导航：易通天下佛山仓 \n"
                + "收货人：腰果 13527824266\n"
                + "收货时间：9：00-12:00，13:30-18:00\n"
                + "交货须知：\n"
                + "1：货物外包箱务必贴/写有我司发货申请单号，否则仓库拒收；\n"
                + "2：发往沙特的所有产品均需要在产品上或者独立包装上标记有不可移除的made in China标签；纺织品，箱包，皮革等必须缝上水洗唛，手表要丝印made in china (不可移除）；\n"
                + "3: 木箱/木架/托盘包装的货物，需用免熏蒸木材包装，合页封顶，不接受原木，卡脚高9cm左右，送货最好用带尾板车，否则要另请叉车卸货会产生叉车费；\n"
                + "4：带电池的货物要贴好电池标识；\n"
                + "5：机器和电器类，需印有“60赫兹”，英规三插；输入电压必须包括127V或220V或380V\n"
                + "6：涉及水效的产品需要贴水效标(彩印）如：水龙头，花洒，马桶。", style);
        sheet.addMergedRegion(new CellRangeAddress(0, 29, 0, 11));
        for (int index = 0; index < 12; index++) {
            sheet.setColumnWidth(index, 14 * 256);
        }
    }

    private void configureEtTemplateColumns(Sheet sheet) {
        double[] widths = {
                16.0, 9.1667, 9.0, 8.8334, 9.5, 11.1667, 10.3334, 10.8334, 13.0, 10.1667,
                12.8334, 10.5, 12.3334, 13.0, 8.3334, 11.6667, 10.8334, 23.8334, 13.0, 13.0, 11.6667
        };
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, Math.max(1, (int) Math.round(widths[index] * 256)));
        }
    }

    private void writeEtTemplateRow(
            Workbook workbook,
            Sheet sheet,
            Drawing<?> drawing,
            Row row,
            PurchaseOrderLogisticsQuoteLineRecord line,
            int lineIndex,
            CellStyle bodyStyle,
            Map<String, YiteProductImage> imageCache
    ) {
        writeStringCell(row, 0, "1/" + (lineIndex + 1), bodyStyle);
        writeNumericCell(row, 1, ET_DEFAULT_CARTON_LENGTH_CM, bodyStyle);
        writeNumericCell(row, 2, ET_DEFAULT_CARTON_WIDTH_CM, bodyStyle);
        writeNumericCell(row, 3, ET_DEFAULT_CARTON_HEIGHT_CM, bodyStyle);
        writeNumericCell(row, 4, ET_DEFAULT_CARTON_WEIGHT_KG, bodyStyle);
        writeNumericCell(row, 5, line.quantity, bodyStyle);
        writeStringCell(row, 6, defaultText(line.barcode, defaultText(line.pskuCode, line.partnerSku)), bodyStyle);
        writeStringCell(row, 7, defaultText(line.partnerSku, defaultText(line.barcode, line.pskuCode)), bodyStyle);
        writeStringCell(
                row,
                8,
                truncateText(defaultText(line.titleEn, line.titleCache), ET_ENGLISH_SHORT_NAME_MAX_LENGTH),
                bodyStyle
        );
        writeStringCell(row, 9, defaultText(line.titleCache, line.titleEn), bodyStyle);
        writeDecimalCell(row, 10, ET_DEFAULT_DECLARE_UNIT_PRICE_USD, bodyStyle);
        writeStringCell(row, 11, line.brandName, bodyStyle);
        writeStringCell(row, 12, line.brandName, bodyStyle);
        writeStringCell(row, ET_PRODUCT_IMAGE_COLUMN, "", bodyStyle);
        embedProductImage(workbook, drawing, row, line.imageUrlCache, imageCache, ET_PRODUCT_IMAGE_COLUMN);
        writeStringCell(row, 14, line.yiteMaterial, bodyStyle);
        writeStringCell(row, 15, "否", bodyStyle);
        writeStringCell(row, 16, "否", bodyStyle);
        writeStringCell(row, 17, "否", bodyStyle);
        writeStringCell(row, 18, "否", bodyStyle);
        writeStringCell(row, 19, "否", bodyStyle);
        writeStringCell(row, 20, "", bodyStyle);
        writeStringCell(row, ET_HIDDEN_QUOTE_LINE_ID_COLUMN, line.id);
        writeStringCell(row, ET_HIDDEN_PURCHASE_ORDER_ID_COLUMN, line.purchaseOrderId);
        writeStringCell(row, ET_HIDDEN_PURCHASE_ORDER_ITEM_ID_COLUMN, line.purchaseOrderItemId);
        writeStringCell(row, ET_HIDDEN_PURCHASE_ORDER_ITEM_SITE_ID_COLUMN, line.purchaseOrderItemSiteId);
        writeStringCell(row, ET_HIDDEN_FORWARDER_CODE_COLUMN, defaultText(line.forwarderCode, ET_FORWARDER_CODE));
        writeStringCell(row, ET_HIDDEN_FORWARDER_NAME_COLUMN, defaultText(line.forwarderName, ET_FORWARDER_NAME));
        writeStringCell(row, ET_HIDDEN_ROUTE_CODE_COLUMN, line.routeCode);
        writeStringCell(row, ET_HIDDEN_ROUTE_NAME_COLUMN, line.routeName);
        writeStringCell(row, ET_HIDDEN_SERVICE_CODE_COLUMN, line.serviceCode);
        writeStringCell(row, ET_HIDDEN_SERVICE_NAME_COLUMN, line.serviceName);
        writeDecimalCell(row, ET_HIDDEN_UNIT_PRICE_COLUMN, line.unitPrice);
        writeStringCell(row, ET_HIDDEN_BILLING_UNIT_COLUMN, line.billingUnit);
        writeDecimalCell(row, ET_HIDDEN_ESTIMATED_AMOUNT_COLUMN, line.estimatedAmount);
        writeStringCell(row, ET_HIDDEN_REMARK_COLUMN, line.remark);
    }

    private CellStyle etInstructionStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle etHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorders(style);
        return style;
    }

    private CellStyle etBodyStyle(Workbook workbook, boolean red) {
        Font font = workbook.createFont();
        font.setFontName("宋体");
        if (red) {
            font.setColor(IndexedColors.RED.getIndex());
        }
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorders(style);
        return style;
    }

    private void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void writeYiteTemplateSheet(
            HSSFWorkbook workbook,
            Sheet sheet,
            PurchaseOrderRecord order,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            Map<String, YiteProductImage> imageCache
    ) {
        PurchaseOrderLogisticsQuoteLineRecord sample = firstLine(lines);
        writeLabelValueRow(sheet, 0, "客户订单号", order.orderNo);
        writeStringCell(sheet.getRow(0), 2, "使用说明\n1、带星标*为必填项\n2、一箱货有多个商品，箱号重复即可\n"
                + "3、申报价值为单件价值，快件总价值=申报价值*数量\n4、表格填写不可以使用公式\n"
                + "5、箱号、箱规、申报单价、材质请物流按义特要求确认后回传");
        writeLabelValueRow(sheet, 1, "服务*", yiteServiceName(sample));
        writeLabelValueRow(sheet, 2, "地址库编码", "");
        writeLabelValueRow(sheet, 3, "收件人姓名*", "noon");
        writeLabelValueRow(sheet, 4, "收件人公司", "");
        writeLabelValueRow(sheet, 5, "收件人地址一*", "noon-FBN");
        writeLabelValueRow(sheet, 6, "收件人地址二", "");
        writeLabelValueRow(sheet, 7, "收件人城市*", yiteCity(sample));
        writeLabelValueRow(sheet, 8, "收件人省份/州", yiteCity(sample));
        writeLabelValueRow(sheet, 9, "收件人邮编*", yiteCountryName(sample));
        writeLabelValueRow(sheet, 10, "收件人国家代码(二字代码)*", defaultText(siteCode(sample), "SA"));
        writeLabelValueRow(sheet, 11, "收件人电话", "");
        writeLabelValueRow(sheet, 12, "收件人邮箱", "");
        writeLabelValueRow(sheet, 13, "带电*", "否");
        writeLabelValueRow(sheet, 14, "带磁*", "否");
        writeLabelValueRow(sheet, 15, "液体*", "否");
        writeLabelValueRow(sheet, 16, "粉末*", "否");
        writeLabelValueRow(sheet, 17, "危险品*", "否");
        writeLabelValueRow(sheet, 18, "报关方式*", "买单报关");
        writeLabelValueRow(sheet, 19, "交税方式", "");
        writeLabelValueRow(sheet, 20, "PO Number", order.orderNo);
        writeLabelValueRow(sheet, 21, "箱数", "");

        Row header = sheet.createRow(YITE_DETAIL_HEADER_ROW_INDEX);
        for (int index = 0; index < YITE_DETAIL_HEADERS.length; index++) {
            writeStringCell(header, index, YITE_DETAIL_HEADERS[index]);
        }
        for (int index = 0; index < YITE_HIDDEN_HEADERS.length; index++) {
            writeStringCell(header, YITE_HIDDEN_START_COLUMN + index, YITE_HIDDEN_HEADERS[index]);
            sheet.setColumnHidden(YITE_HIDDEN_START_COLUMN + index, true);
        }
        configureYiteTemplateColumns(sheet);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        int rowIndex = YITE_DETAIL_FIRST_DATA_ROW_INDEX;
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(YITE_PRODUCT_IMAGE_ROW_HEIGHT_POINTS);
            writeYiteTemplateRow(workbook, sheet, drawing, row, line, imageCache);
        }
    }

    private void configureYiteTemplateColumns(Sheet sheet) {
        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 16 * 256);
        sheet.setColumnWidth(4, 16 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        sheet.setColumnWidth(6, 36 * 256);
        sheet.setColumnWidth(7, 36 * 256);
        sheet.setColumnWidth(8, 16 * 256);
        sheet.setColumnWidth(9, 16 * 256);
        sheet.setColumnWidth(10, 16 * 256);
        sheet.setColumnWidth(11, 10 * 256);
        sheet.setColumnWidth(12, 16 * 256);
        sheet.setColumnWidth(13, 16 * 256);
        sheet.setColumnWidth(14, 16 * 256);
        sheet.setColumnWidth(15, 16 * 256);
    }

    private void writeLabelValueRow(Sheet sheet, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        writeStringCell(row, 0, label);
        writeStringCell(row, 1, value);
    }

    private void writeYiteTemplateRow(
            HSSFWorkbook workbook,
            Sheet sheet,
            Drawing<?> drawing,
            Row row,
            PurchaseOrderLogisticsQuoteLineRecord line,
            Map<String, YiteProductImage> imageCache
    ) {
        writeStringCell(row, 0, "");
        writeDecimalCell(row, 1, null);
        writeDecimalCell(row, 2, null);
        writeDecimalCell(row, 3, null);
        writeDecimalCell(row, 4, null);
        writeStringCell(row, 5, defaultText(line.barcode, line.pskuCode));
        writeStringCell(row, 6, defaultText(line.titleEn, line.titleCache));
        writeStringCell(row, 7, defaultText(line.titleCache, line.titleEn));
        writeNumericCell(row, 8, line.quantity);
        writeDecimalCell(row, 9, null);
        writeStringCell(row, 10, line.yiteMaterial);
        writeStringCell(row, YITE_PRODUCT_IMAGE_COLUMN, "");
        embedProductImage(workbook, drawing, row, line.imageUrlCache, imageCache, YITE_PRODUCT_IMAGE_COLUMN);
        writeStringCell(row, 12, line.brandName);
        writeStringCell(row, 13, line.partnerSku);
        writeStringCell(row, 14, "");
        writeStringCell(row, 15, yiteHistoryQuoteText(line));
        writeStringCell(row, YITE_HIDDEN_QUOTE_LINE_ID_COLUMN, line.id);
        writeStringCell(row, YITE_HIDDEN_PURCHASE_ORDER_ID_COLUMN, line.purchaseOrderId);
        writeStringCell(row, YITE_HIDDEN_PURCHASE_ORDER_ITEM_ID_COLUMN, line.purchaseOrderItemId);
        writeStringCell(row, YITE_HIDDEN_PURCHASE_ORDER_ITEM_SITE_ID_COLUMN, line.purchaseOrderItemSiteId);
        writeStringCell(row, YITE_HIDDEN_FORWARDER_CODE_COLUMN, defaultText(line.forwarderCode, YITE_FORWARDER_CODE));
        writeDecimalCell(row, YITE_HIDDEN_UNIT_PRICE_COLUMN, line.unitPrice);
        writeStringCell(row, YITE_HIDDEN_BILLING_UNIT_COLUMN, line.billingUnit);
        writeDecimalCell(row, YITE_HIDDEN_ESTIMATED_AMOUNT_COLUMN, line.estimatedAmount);
        writeStringCell(row, YITE_HIDDEN_REMARK_COLUMN, line.remark);
    }

    private String yiteHistoryQuoteText(PurchaseOrderLogisticsQuoteLineRecord line) {
        if (line == null || line.unitPrice == null) {
            return "";
        }
        String priceText = line.unitPrice.stripTrailingZeros().toPlainString();
        if (!StringUtils.hasText(line.billingUnit)) {
            return priceText;
        }
        return priceText + "/" + line.billingUnit;
    }

    private void embedProductImage(
            Workbook workbook,
            Drawing<?> drawing,
            Row row,
            String imageUrl,
            Map<String, YiteProductImage> imageCache,
            int columnIndex
    ) {
        YiteProductImage image = loadYiteProductImage(imageUrl, imageCache);
        if (image == null) {
            return;
        }
        int pictureIndex = workbook.addPicture(image.bytes, image.pictureType);
        ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
        anchor.setCol1(columnIndex);
        anchor.setCol2(columnIndex + 1);
        anchor.setRow1(row.getRowNum());
        anchor.setRow2(row.getRowNum() + 1);
        anchor.setDx1(0);
        anchor.setDy1(0);
        anchor.setDx2(0);
        anchor.setDy2(0);
        drawing.createPicture(anchor, pictureIndex);
    }

    private int clampAnchor(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private YiteProductImage loadYiteProductImage(String imageUrl, Map<String, YiteProductImage> imageCache) {
        String value = trimToNull(imageUrl);
        if (value == null) {
            return null;
        }
        if (!value.startsWith("data:")) {
            value = NoonImageUrlNormalizer.normalize(value);
        }
        if (imageCache.containsKey(value)) {
            return imageCache.get(value);
        }
        YiteProductImage image = value.startsWith("data:")
                ? loadYiteDataUrlImage(value)
                : loadYiteRemoteImage(value);
        imageCache.put(value, image);
        return image;
    }

    private YiteProductImage loadYiteDataUrlImage(String value) {
        int comma = value.indexOf(',');
        if (comma <= 0 || !value.substring(0, comma).toLowerCase(Locale.ROOT).contains(";base64")) {
            return null;
        }
        String header = value.substring(0, comma).toLowerCase(Locale.ROOT);
        int pictureType = header.contains("image/png")
                ? Workbook.PICTURE_TYPE_PNG
                : (header.contains("image/jpeg") || header.contains("image/jpg") ? Workbook.PICTURE_TYPE_JPEG : -1);
        if (pictureType < 0) {
            return null;
        }
        try {
            return createYiteProductImage(Base64.getDecoder().decode(value.substring(comma + 1)), pictureType);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private YiteProductImage loadYiteRemoteImage(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || (host != null && host.toLowerCase(Locale.ROOT).endsWith(".test"))) {
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(YITE_PRODUCT_IMAGE_TIMEOUT)
                    .header("User-Agent", "Nuono-Logistics-Quote-Exporter/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = YITE_PRODUCT_IMAGE_HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0 || bytes.length > YITE_PRODUCT_IMAGE_MAX_BYTES) {
                return null;
            }
            int pictureType = detectPictureType(response.headers().firstValue("content-type").orElse(null), bytes);
            return pictureType < 0 ? null : createYiteProductImage(bytes, pictureType);
        } catch (Exception exception) {
            return null;
        }
    }

    private int detectPictureType(String contentType, byte[] bytes) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("image/png")) {
            return Workbook.PICTURE_TYPE_PNG;
        }
        if (type.contains("image/jpeg") || type.contains("image/jpg")) {
            return Workbook.PICTURE_TYPE_JPEG;
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return Workbook.PICTURE_TYPE_PNG;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return Workbook.PICTURE_TYPE_JPEG;
        }
        return -1;
    }

    private YiteProductImage createYiteProductImage(byte[] bytes, int pictureType) {
        if (bytes == null || bytes.length == 0 || bytes.length > YITE_PRODUCT_IMAGE_MAX_BYTES) {
            return null;
        }
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bufferedImage == null || bufferedImage.getWidth() <= 0 || bufferedImage.getHeight() <= 0) {
                return null;
            }
            return createYiteProductThumbnail(bufferedImage);
        } catch (IOException exception) {
            return null;
        }
    }

    private YiteProductImage createYiteProductThumbnail(BufferedImage source) {
        int size = LOGISTICS_PRODUCT_IMAGE_EMBED_PIXELS;
        double scale = Math.min(
                size / (double) Math.max(1, source.getWidth()),
                size / (double) Math.max(1, source.getHeight())
        );
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = Math.max(0, (size - width) / 2);
        int y = Math.max(0, (size - height) / 2);
        BufferedImage thumbnail = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(thumbnail, "png", output);
            byte[] bytes = output.toByteArray();
            return bytes.length == 0 ? null : new YiteProductImage(bytes, Workbook.PICTURE_TYPE_PNG, size, size);
        } catch (IOException exception) {
            return null;
        }
    }

    private PurchaseOrderLogisticsQuoteLineRecord firstLine(List<PurchaseOrderLogisticsQuoteLineRecord> lines) {
        return lines == null || lines.isEmpty() ? null : lines.get(0);
    }

    private String yiteSheetKey(PurchaseOrderLogisticsQuoteLineRecord line) {
        return defaultText(siteCode(line), "SA") + "-" + normalizeTransportMode(line.plannedTransportMode);
    }

    private String yiteServiceName(PurchaseOrderLogisticsQuoteLineRecord line) {
        if (line != null && StringUtils.hasText(line.serviceName)) {
            return line.serviceName;
        }
        String siteCode = siteCode(line);
        String transportMode = line == null ? TRANSPORT_SEA : normalizeTransportMode(line.plannedTransportMode);
        if ("AE".equalsIgnoreCase(siteCode)) {
            return TRANSPORT_AIR.equals(transportMode) ? "阿联酋空运双清" : "阿联酋海运双清";
        }
        return TRANSPORT_AIR.equals(transportMode) ? "沙特空运双清" : "沙特海运双清";
    }

    private String yiteCity(PurchaseOrderLogisticsQuoteLineRecord line) {
        return "AE".equalsIgnoreCase(siteCode(line)) ? "迪拜" : "利雅得";
    }

    private String yiteCountryName(PurchaseOrderLogisticsQuoteLineRecord line) {
        return "AE".equalsIgnoreCase(siteCode(line)) ? "阿联酋" : "沙特";
    }

    private String siteCode(PurchaseOrderLogisticsQuoteLineRecord line) {
        return line == null ? null : line.siteCode;
    }

    private String safeSheetName(String value) {
        String text = safeFilePart(defaultText(value, "模板"));
        text = text.replaceAll("[\\[\\]]", "_");
        return text.length() > 31 ? text.substring(0, 31) : text;
    }

    private void writeLogisticsQuoteRow(Row row, PurchaseOrderLogisticsQuoteLineRecord line) {
        writeStringCell(row, 0, line.id);
        writeStringCell(row, 1, line.purchaseOrderId);
        writeStringCell(row, 2, line.purchaseOrderItemId);
        writeStringCell(row, 3, line.purchaseOrderItemSiteId);
        writeStringCell(row, 4, line.purchaseOrderNo);
        writeStringCell(row, 5, line.purchaseOrderTitle);
        writeStringCell(row, 6, line.siteCode);
        writeStringCell(row, 7, normalizeTransportMode(line.plannedTransportMode));
        writeStringCell(row, 8, line.partnerSku);
        writeStringCell(row, 9, line.pskuCode);
        writeStringCell(row, 10, defaultText(line.titleCache, line.partnerSku));
        writeNumericCell(row, 11, line.quantity);
        writeStringCell(row, 12, fulfillmentTypeLabel(normalizeFulfillmentType(line.fulfillmentType)));
        writeStringCell(row, 13, Boolean.TRUE.equals(line.isNewProduct) ? "是" : "否");
        writeStringCell(row, 14, logisticsQuoteStatusLabel(line.quoteStatus));
        writeStringCell(row, 15, shippingSubmitStatusLabel(line.shippingSubmitStatus));
        writeStringCell(row, 16, line.forwarderCode);
        writeStringCell(row, 17, line.forwarderName);
        writeStringCell(row, 18, line.routeCode);
        writeStringCell(row, 19, line.routeName);
        writeStringCell(row, 20, line.serviceCode);
        writeStringCell(row, 21, line.serviceName);
        writeStringCell(row, 22, line.currency);
        writeDecimalCell(row, 23, line.unitPrice);
        writeStringCell(row, 24, line.billingUnit);
        writeDecimalCell(row, 25, line.estimatedAmount);
        writeStringCell(row, 26, line.remark);
    }

    private boolean isEtTemplateSheet(Sheet sheet) {
        Row header = sheet == null ? null : sheet.getRow(ET_HEADER_ROW_INDEX);
        return "箱号".equals(readTextCell(header, 0))
                && "*长(CM)".equals(readTextCell(header, 1))
                && "*每箱数量".equals(readTextCell(header, 5))
                && "*商家条码".equals(readTextCell(header, 6))
                && ET_HIDDEN_HEADERS[3].equals(readTextCell(header, ET_HIDDEN_PURCHASE_ORDER_ITEM_SITE_ID_COLUMN));
    }

    private boolean isYiteTemplateSheet(Sheet sheet) {
        Row header = sheet == null ? null : sheet.getRow(YITE_DETAIL_HEADER_ROW_INDEX);
        return "货箱编号*".equals(readTextCell(header, 0))
                && "产品SKU*".equals(readTextCell(header, 5))
                && "产品中文品名*".equals(readTextCell(header, 7));
    }

    private boolean isGenericLogisticsQuoteSheet(Sheet sheet) {
        Row header = sheet == null ? null : sheet.getRow(0);
        return "报价行ID".equals(readTextCell(header, 0))
                && "采购站点行ID".equals(readTextCell(header, 3))
                && "货代编码".equals(readTextCell(header, 16));
    }

    private void importGenericLogisticsQuoteSheet(
            PurchaseOrderRecord order,
            Sheet sheet,
            Long operatorUserId,
            String sourceFilename,
            PurchaseOrderLogisticsQuoteImportView view,
            Set<Long> allowedSegmentIds
    ) {
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankRow(row)) {
                continue;
            }
            view.totalRows += 1;
            importLogisticsQuoteRow(order, row, rowIndex + 1, operatorUserId, sourceFilename, view, allowedSegmentIds);
        }
    }

    private void importEtLogisticsQuoteSheet(
            PurchaseOrderRecord order,
            Sheet sheet,
            Long operatorUserId,
            String sourceFilename,
            PurchaseOrderLogisticsQuoteImportView view,
            Set<Long> allowedSegmentIds
    ) {
        for (int rowIndex = ET_FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankEtRow(row)) {
                continue;
            }
            view.totalRows += 1;
            importEtLogisticsQuoteRow(order, row, rowIndex + 1, operatorUserId, sourceFilename, view, allowedSegmentIds);
        }
    }

    private void importEtLogisticsQuoteRow(
            PurchaseOrderRecord order,
            Row row,
            int rowNumber,
            Long operatorUserId,
            String sourceFilename,
            PurchaseOrderLogisticsQuoteImportView view,
            Set<Long> allowedSegmentIds
    ) {
        Long itemSiteId = readLongCell(row, ET_HIDDEN_PURCHASE_ORDER_ITEM_SITE_ID_COLUMN);
        if (itemSiteId == null) {
            addImportError(view, rowNumber, "缺少采购站点行ID，请使用采购单导出的易通模板回传。");
            return;
        }
        if (!hasEtConfirmation(row)) {
            addImportError(view, rowNumber, "请补充易通箱号、尺寸、重量，或在隐藏确认列填写单价/金额/备注后回传。");
            return;
        }
        PurchaseOrderLogisticsQuoteLineRecord line =
                selectLogisticsQuoteLineForImport(order, itemSiteId);
        if (line == null) {
            addImportError(view, rowNumber, "报价行不存在或不属于当前导出单据。");
            return;
        }
        if (isOutsideSelectedShippingOrderSegment(order, line, allowedSegmentIds)) {
            addImportError(view, rowNumber, "报价行不属于当前筛选的子发货单。");
            return;
        }
        line.quoteStatus = LOGISTICS_QUOTE_CONFIRMED;
        line.shippingSubmitStatus = normalizeShippingSubmitStatus(line.shippingSubmitStatus);
        line.forwarderCode = defaultText(
                readTextCell(row, ET_HIDDEN_FORWARDER_CODE_COLUMN),
                defaultText(line.forwarderCode, ET_FORWARDER_CODE)
        );
        line.forwarderName = defaultText(
                readTextCell(row, ET_HIDDEN_FORWARDER_NAME_COLUMN),
                defaultText(line.forwarderName, ET_FORWARDER_NAME)
        );
        line.routeCode = defaultText(readTextCell(row, ET_HIDDEN_ROUTE_CODE_COLUMN), line.routeCode);
        line.routeName = defaultText(readTextCell(row, ET_HIDDEN_ROUTE_NAME_COLUMN), line.routeName);
        line.serviceCode = defaultText(readTextCell(row, ET_HIDDEN_SERVICE_CODE_COLUMN), line.serviceCode);
        line.serviceName = defaultText(readTextCell(row, ET_HIDDEN_SERVICE_NAME_COLUMN), line.serviceName);
        line.currency = defaultText(line.currency, "RMB");
        BigDecimal unitPrice = readDecimalCell(row, ET_HIDDEN_UNIT_PRICE_COLUMN);
        BigDecimal estimatedAmount = readDecimalCell(row, ET_HIDDEN_ESTIMATED_AMOUNT_COLUMN);
        line.unitPrice = unitPrice == null ? line.unitPrice : unitPrice;
        line.billingUnit = defaultText(readTextCell(row, ET_HIDDEN_BILLING_UNIT_COLUMN), line.billingUnit);
        line.estimatedAmount = estimatedAmount == null ? line.estimatedAmount : estimatedAmount;
        line.remark = defaultText(readTextCell(row, ET_HIDDEN_REMARK_COLUMN), etPackingRemark(row));
        mapper.confirmLogisticsQuoteLine(line, operatorUserId);
        view.updatedRows += 1;
    }

    private PurchaseOrderLogisticsQuoteLineRecord selectLogisticsQuoteLineForImport(
            PurchaseOrderRecord order,
            Long itemSiteId
    ) {
        if (order != null && StringUtils.hasText(order.orderNo) && order.orderNo.startsWith("SO-")) {
            return mapper.selectLogisticsQuoteLineByShippingOrderItemSiteForUpdate(order.id, itemSiteId);
        }
        return mapper.selectLogisticsQuoteLineByItemSiteForUpdate(order.id, itemSiteId);
    }

    private boolean isOutsideSelectedShippingOrderSegment(
            PurchaseOrderRecord order,
            PurchaseOrderLogisticsQuoteLineRecord line,
            Set<Long> allowedSegmentIds
    ) {
        return order != null
                && StringUtils.hasText(order.orderNo)
                && order.orderNo.startsWith("SO-")
                && allowedSegmentIds != null
                && !allowedSegmentIds.isEmpty()
                && (line == null
                        || line.shippingOrderSegmentId == null
                        || !allowedSegmentIds.contains(line.shippingOrderSegmentId));
    }

    private boolean hasEtConfirmation(Row row) {
        boolean hasBoxInfo = StringUtils.hasText(readTextCell(row, 0))
                && StringUtils.hasText(readTextCell(row, 1))
                && StringUtils.hasText(readTextCell(row, 2))
                && StringUtils.hasText(readTextCell(row, 3))
                && StringUtils.hasText(readTextCell(row, 4));
        return hasBoxInfo
                || readDecimalCell(row, ET_HIDDEN_UNIT_PRICE_COLUMN) != null
                || readDecimalCell(row, ET_HIDDEN_ESTIMATED_AMOUNT_COLUMN) != null
                || StringUtils.hasText(readTextCell(row, ET_HIDDEN_REMARK_COLUMN));
    }

    private String etPackingRemark(Row row) {
        List<String> parts = new ArrayList<>();
        parts.add("易通模板回传确认");
        String boxNo = readTextCell(row, 0);
        String length = readTextCell(row, 1);
        String width = readTextCell(row, 2);
        String height = readTextCell(row, 3);
        String weight = readTextCell(row, 4);
        String quantity = readTextCell(row, 5);
        String barcode = readTextCell(row, 6);
        if (StringUtils.hasText(boxNo)) {
            parts.add("箱号=" + boxNo);
        }
        if (StringUtils.hasText(length) && StringUtils.hasText(width) && StringUtils.hasText(height)) {
            parts.add("箱规=" + length + "x" + width + "x" + height + "cm");
        }
        if (StringUtils.hasText(weight)) {
            parts.add("箱重=" + weight + "kg");
        }
        if (StringUtils.hasText(barcode)) {
            parts.add("商家条码=" + barcode);
        }
        if (StringUtils.hasText(quantity)) {
            parts.add("数量=" + quantity);
        }
        return String.join("；", parts);
    }

    private boolean isBlankEtRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int index = 0; index < ET_VISIBLE_COLUMN_COUNT; index++) {
            if (StringUtils.hasText(readTextCell(row, index))) {
                return false;
            }
        }
        for (int index = 0; index < ET_HIDDEN_HEADERS.length; index++) {
            if (StringUtils.hasText(readTextCell(row, ET_HIDDEN_START_COLUMN + index))) {
                return false;
            }
        }
        return true;
    }

    private void importYiteLogisticsQuoteSheet(
            PurchaseOrderRecord order,
            Sheet sheet,
            Long operatorUserId,
            String sourceFilename,
            PurchaseOrderLogisticsQuoteImportView view,
            Set<Long> allowedSegmentIds
    ) {
        String serviceName = defaultText(readTextCell(sheet.getRow(1), 1), "义特海外无忧B2B");
        for (int rowIndex = YITE_DETAIL_FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankYiteRow(row)) {
                continue;
            }
            view.totalRows += 1;
            importYiteLogisticsQuoteRow(order, row, rowIndex + 1, serviceName, operatorUserId, sourceFilename, view, allowedSegmentIds);
        }
    }

    private void importYiteLogisticsQuoteRow(
            PurchaseOrderRecord order,
            Row row,
            int rowNumber,
            String serviceName,
            Long operatorUserId,
            String sourceFilename,
            PurchaseOrderLogisticsQuoteImportView view,
            Set<Long> allowedSegmentIds
    ) {
        Long itemSiteId = readLongCell(row, YITE_HIDDEN_PURCHASE_ORDER_ITEM_SITE_ID_COLUMN);
        String productCode = readTextCell(row, 5);
        BigDecimal latestQuote = readDecimalCell(row, YITE_VISIBLE_LATEST_QUOTE_COLUMN);
        BigDecimal declaredUnitPrice = readDecimalCell(row, YITE_VISIBLE_DECLARED_UNIT_PRICE_COLUMN);
        BigDecimal hiddenUnitPrice = readDecimalCell(row, YITE_HIDDEN_UNIT_PRICE_COLUMN);
        BigDecimal unitPrice = latestQuote != null
                ? latestQuote
                : (declaredUnitPrice == null ? hiddenUnitPrice : declaredUnitPrice);
        BigDecimal estimatedAmount = readDecimalCell(row, YITE_HIDDEN_ESTIMATED_AMOUNT_COLUMN);
        if (unitPrice == null && estimatedAmount == null) {
            addImportError(view, rowNumber, "缺少报价，请在最新报价或产品申报单价列填写报价后回传。");
            return;
        }
        List<PurchaseOrderLogisticsQuoteLineRecord> lines =
                selectLogisticsQuoteLinesForYiteImport(order, itemSiteId, productCode, allowedSegmentIds);
        if (lines.isEmpty()) {
            addImportError(view, rowNumber, "报价行不存在或不属于当前导出单据，请检查产品SKU。");
            return;
        }
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            if (isOutsideSelectedShippingOrderSegment(order, line, allowedSegmentIds)) {
                addImportError(view, rowNumber, "报价行不属于当前筛选的子发货单。");
                return;
            }
        }
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            line.quoteStatus = LOGISTICS_QUOTE_CONFIRMED;
            line.shippingSubmitStatus = normalizeShippingSubmitStatus(line.shippingSubmitStatus);
            String hiddenForwarderCode = readTextCell(row, YITE_HIDDEN_FORWARDER_CODE_COLUMN);
            line.forwarderCode = defaultText(hiddenForwarderCode, defaultText(line.forwarderCode, YITE_FORWARDER_CODE));
            line.forwarderName = defaultText(line.forwarderName, YITE_FORWARDER_NAME);
            line.routeCode = defaultText(line.routeCode, serviceName);
            line.routeName = defaultText(line.routeName, serviceName);
            line.serviceCode = defaultText(line.serviceCode, serviceName);
            line.serviceName = defaultText(line.serviceName, serviceName);
            line.currency = "RMB";
            line.unitPrice = unitPrice;
            line.billingUnit = defaultText(readTextCell(row, YITE_HIDDEN_BILLING_UNIT_COLUMN), line.billingUnit);
            line.estimatedAmount = estimatedAmount;
            line.remark = defaultText(readTextCell(row, YITE_HIDDEN_REMARK_COLUMN), "义特模板回传确认");
            mapper.confirmLogisticsQuoteLine(line, operatorUserId);
        }
        view.updatedRows += 1;
    }

    private List<PurchaseOrderLogisticsQuoteLineRecord> selectLogisticsQuoteLinesForYiteImport(
            PurchaseOrderRecord order,
            Long itemSiteId,
            String productCode,
            Set<Long> allowedSegmentIds
    ) {
        if (itemSiteId != null) {
            PurchaseOrderLogisticsQuoteLineRecord line = selectLogisticsQuoteLineForImport(order, itemSiteId);
            if (line != null) {
                return List.of(line);
            }
        }
        return selectLogisticsQuoteLinesForYiteImportByProductCode(order, productCode, allowedSegmentIds);
    }

    private List<PurchaseOrderLogisticsQuoteLineRecord> selectLogisticsQuoteLinesForYiteImportByProductCode(
            PurchaseOrderRecord order,
            String productCode,
            Set<Long> allowedSegmentIds
    ) {
        if (!StringUtils.hasText(productCode) || order == null || order.id == null) {
            return Collections.emptyList();
        }
        List<PurchaseOrderLogisticsQuoteLineRecord> lines;
        boolean shippingOrder = StringUtils.hasText(order.orderNo) && order.orderNo.startsWith("SO-");
        if (shippingOrder) {
            lines = emptyIfNull(mapper.listLogisticsQuoteCandidatesByShippingOrder(order.id));
        } else {
            lines = emptyIfNull(mapper.listLogisticsQuoteCandidatesByOrder(order.id));
        }
        String normalizedProductCode = normalizeProductCode(productCode);
        List<PurchaseOrderLogisticsQuoteLineRecord> matches = lines.stream()
                .filter(line -> !isOutsideSelectedShippingOrderSegment(order, line, allowedSegmentIds))
                .filter(line -> matchesYiteImportProductCode(line, normalizedProductCode))
                .collect(Collectors.toList());
        if (!shippingOrder && matches.size() != 1) {
            return Collections.emptyList();
        }
        return matches;
    }

    private boolean matchesYiteImportProductCode(
            PurchaseOrderLogisticsQuoteLineRecord line,
            String normalizedProductCode
    ) {
        if (line == null || !StringUtils.hasText(normalizedProductCode)) {
            return false;
        }
        return normalizedProductCode.equals(normalizeProductCode(line.barcode))
                || normalizedProductCode.equals(normalizeProductCode(line.pskuCode))
                || normalizedProductCode.equals(normalizeProductCode(line.partnerSku));
    }

    private String normalizeProductCode(String value) {
        return defaultText(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlankYiteRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int index = 0; index < YITE_DETAIL_HEADERS.length; index++) {
            if (StringUtils.hasText(readTextCell(row, index))) {
                return false;
            }
        }
        for (int index = 0; index < YITE_HIDDEN_HEADERS.length; index++) {
            if (StringUtils.hasText(readTextCell(row, YITE_HIDDEN_START_COLUMN + index))) {
                return false;
            }
        }
        return true;
    }

    private void importLogisticsQuoteRow(
            PurchaseOrderRecord order,
            Row row,
            int rowNumber,
            Long operatorUserId,
            String sourceFilename,
            PurchaseOrderLogisticsQuoteImportView view,
            Set<Long> allowedSegmentIds
    ) {
        Long itemSiteId = readLongCell(row, 3);
        if (itemSiteId == null) {
            addImportError(view, rowNumber, "缺少采购站点行ID。");
            return;
        }
        PurchaseOrderLogisticsQuoteLineRecord line =
                selectLogisticsQuoteLineForImport(order, itemSiteId);
        if (line == null) {
            addImportError(view, rowNumber, "报价行不存在或不属于当前导出单据。");
            return;
        }
        if (isOutsideSelectedShippingOrderSegment(order, line, allowedSegmentIds)) {
            addImportError(view, rowNumber, "报价行不属于当前筛选的子发货单。");
            return;
        }
        BigDecimal unitPrice = readDecimalCell(row, 23);
        BigDecimal estimatedAmount = readDecimalCell(row, 25);
        if (unitPrice == null && estimatedAmount == null) {
            addImportError(view, rowNumber, "请填写单价或确认金额。");
            return;
        }
        line.quoteStatus = LOGISTICS_QUOTE_CONFIRMED;
        line.shippingSubmitStatus = normalizeShippingSubmitStatus(line.shippingSubmitStatus);
        line.forwarderCode = readTextCell(row, 16);
        line.forwarderName = readTextCell(row, 17);
        line.routeCode = readTextCell(row, 18);
        line.routeName = readTextCell(row, 19);
        line.serviceCode = readTextCell(row, 20);
        line.serviceName = readTextCell(row, 21);
        line.currency = defaultText(readTextCell(row, 22), "RMB");
        line.unitPrice = unitPrice;
        line.billingUnit = readTextCell(row, 24);
        line.estimatedAmount = estimatedAmount;
        line.remark = readTextCell(row, 26);
        mapper.confirmLogisticsQuoteLine(line, operatorUserId);
        view.updatedRows += 1;
    }

    // Legacy product quote projection retained for migration compatibility.
    // Product logistics cost facts are curated by AI/data correction jobs and displayed from productlogisticscost.
    private void persistProductForwarderChannelQuote(
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId,
            String sourceFilename
    ) {
        if (line == null
                || line.ownerUserId == null
                || line.productVariantId == null
                || !StringUtils.hasText(line.forwarderCode)) {
            return;
        }
        ProductForwarderChannelQuoteRecord quote = new ProductForwarderChannelQuoteRecord();
        quote.id = mapper.nextProductForwarderChannelQuoteId();
        quote.ownerUserId = line.ownerUserId;
        quote.productMasterId = line.productMasterId;
        quote.productVariantId = line.productVariantId;
        quote.logicalStoreId = line.logicalStoreId;
        quote.sourceStoreCode = line.sourceStoreCode;
        quote.partnerSku = line.partnerSku;
        quote.barcode = trim(line.barcode);
        quote.forwarderCode = trim(line.forwarderCode);
        quote.forwarderName = trim(line.forwarderName);
        quote.routeCode = trim(line.routeCode);
        quote.routeName = trim(line.routeName);
        quote.serviceCode = trim(line.serviceCode);
        quote.serviceName = trim(line.serviceName);
        quote.siteCode = trim(line.siteCode);
        quote.transportMode = trim(line.plannedTransportMode);
        quote.currency = trim(line.currency);
        quote.unitPrice = line.unitPrice;
        quote.billingUnit = defaultText(line.billingUnit, "UNKNOWN");
        quote.estimatedAmount = line.estimatedAmount;
        quote.sourceType = "SHIPPING_ORDER_QUOTE";
        quote.sourceShippingOrderId = line.shippingOrderId;
        quote.sourceShippingOrderLineId = line.shippingOrderLineId;
        quote.sourceQuoteLineId = line.id;
        quote.sourceFilename = trim(sourceFilename);
        quote.effectiveStatus = "CURRENT";
        quote.rawSnapshotJson = productForwarderChannelQuoteSnapshot(line);
        mapper.markHistoricalProductForwarderChannelQuote(
                quote.ownerUserId,
                quote.sourceStoreCode,
                quote.logicalStoreId,
                quote.partnerSku,
                quote.productVariantId,
                quote.forwarderCode,
                quote.siteCode,
                quote.routeCode,
                quote.serviceCode,
                quote.billingUnit,
                operatorUserId
        );
        mapper.insertProductForwarderChannelQuote(quote, operatorUserId);
    }

    private String productForwarderChannelQuoteSnapshot(PurchaseOrderLogisticsQuoteLineRecord line) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("quoteLineId", line.id);
        snapshot.put("shippingOrderId", line.shippingOrderId);
        snapshot.put("shippingOrderNo", line.shippingOrderNo);
        snapshot.put("purchaseOrderId", line.purchaseOrderId);
        snapshot.put("purchaseOrderNo", line.purchaseOrderNo);
        snapshot.put("purchaseOrderItemSiteId", line.purchaseOrderItemSiteId);
        snapshot.put("barcode", line.barcode);
        snapshot.put("pskuCode", line.pskuCode);
        snapshot.put("siteCode", line.siteCode);
        snapshot.put("quantity", line.quantity);
        snapshot.put("forwarderCode", line.forwarderCode);
        snapshot.put("routeCode", line.routeCode);
        snapshot.put("serviceCode", line.serviceCode);
        snapshot.put("currency", line.currency);
        snapshot.put("unitPrice", line.unitPrice);
        snapshot.put("billingUnit", line.billingUnit);
        snapshot.put("estimatedAmount", line.estimatedAmount);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private void addImportError(PurchaseOrderLogisticsQuoteImportView view, int rowNumber, String message) {
        PurchaseOrderLogisticsQuoteImportErrorView error = new PurchaseOrderLogisticsQuoteImportErrorView();
        error.rowNumber = rowNumber;
        error.message = message;
        view.errors.add(error);
        view.skippedRows += 1;
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int index = 0; index < LOGISTICS_QUOTE_HEADERS.length; index++) {
            if (StringUtils.hasText(readTextCell(row, index))) {
                return false;
            }
        }
        return true;
    }

    private void writeStringCell(Row row, int columnIndex, Object value) {
        Cell cell = row.createCell(columnIndex);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void writeStringCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            cell.setCellValue(String.valueOf(value));
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void writeNumericCell(Row row, int columnIndex, Integer value) {
        Cell cell = row.createCell(columnIndex);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private void writeNumericCell(Row row, int columnIndex, Integer value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value != null) {
            cell.setCellValue(value);
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void writeDecimalCell(Row row, int columnIndex, BigDecimal value) {
        Cell cell = row.createCell(columnIndex);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private void writeDecimalCell(Row row, int columnIndex, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private String readTextCell(Row row, int columnIndex) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (Math.rint(value) == value) {
                return String.valueOf((long) value);
            }
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        String value = cell.getCellType() == CellType.FORMULA
                ? cell.getCellFormula()
                : cell.getStringCellValue();
        return trimToNull(value);
    }

    private Long readLongCell(Row row, int columnIndex) {
        String value = readTextCell(row, columnIndex);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal readDecimalCell(Row row, int columnIndex) {
        String value = readTextCell(row, columnIndex);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private PurchaseOrderLogisticsPlanView buildLogisticsPlanView(
            PurchaseOrderRecord order,
            Long operatorUserId,
            boolean persist
    ) {
        List<PurchaseOrderItemRecord> items = mapper.listItemsByOrder(order.id);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("当前采购单还没有商品，不能生成物流计划。");
        }

        Map<Long, List<PurchaseOrderItemSiteRecord>> sitesByItem = mapper.listItemSitesByOrder(order.id).stream()
                .collect(Collectors.groupingBy(row -> row.purchaseOrderItemId, LinkedHashMap::new, Collectors.toList()));
        PurchaseOrderLogisticsPlanView view = new PurchaseOrderLogisticsPlanView();
        Long planId = persist ? mapper.nextLogisticsPlanId() : null;
        view.id = persist ? String.valueOf(planId) : "preview-" + order.id;
        view.planNo = persist ? "LP-" + planId : "PREVIEW-" + order.orderNo;
        view.purchaseOrderId = String.valueOf(order.id);
        view.purchaseOrderNo = order.orderNo;
        view.purchaseOrderTitle = order.title;
        view.storeName = defaultText(order.projectNameCache, order.projectCodeCache);
        view.storeCode = order.anchorStoreCodeCache;
        view.status = persist ? "draft" : "preview";
        view.transportMode = "pending";
        view.generatedAt = VIEW_DATE_TIME_FORMATTER.format(LocalDateTime.now());

        Map<String, SiteTransportQuantity> siteQuantityTotals = new LinkedHashMap<>();
        int skuCount = 0;
        int totalQuantity = 0;
        int missingItemCount = 0;
        for (PurchaseOrderItemRecord item : items) {
            List<PurchaseOrderItemSiteRecord> sites = sitesByItem.getOrDefault(item.id, Collections.emptyList());
            ProductArchiveRecord product = mapper.selectProductArchiveByVariant(order.logicalStoreId, item.productVariantId);
            PurchaseOrderLogisticsPlanLineView line = toLogisticsPlanLine(item, product, sites);
            view.lines.add(line);
            skuCount += Math.max(sites.size(), 1);
            totalQuantity += nonNull(item.totalQuantity);
            if (!line.missingFields.isEmpty()) {
                missingItemCount++;
            }
            for (PurchaseOrderItemSiteRecord site : sites) {
                String siteCode = normalizeSiteCode(site.siteCode);
                String transportMode = normalizeTransportMode(site.transportMode);
                if (StringUtils.hasText(siteCode)) {
                    addSiteTransportQuantity(siteQuantityTotals, siteCode, transportMode, site.quantity);
                }
            }
        }
        view.itemCount = items.size();
        view.skuCount = skuCount;
        view.totalQuantity = totalQuantity;
        view.missingItemCount = missingItemCount;
        view.estimatedSeaVolumeCbm = totalSeaLooseVolumeCbm(view);
        view.estimatedSeaVolumeCbmText = formatCbm(view.estimatedSeaVolumeCbm);
        view.estimatedAirChargeableWeightKg = hasMissingAirChargeableWeightInputs(view)
                ? null : totalAirChargeableWeightKg(view, DEFAULT_AIR_VOLUME_DIVISOR);
        view.estimatedAirChargeableWeightKgText = formatKg(view.estimatedAirChargeableWeightKg);
        for (SiteTransportQuantity entry : siteQuantityTotals.values()) {
            SiteQuantitySummaryView summary = new SiteQuantitySummaryView();
            summary.site = entry.siteCode;
            summary.siteName = siteName(entry.siteCode);
            summary.transportMode = entry.transportMode;
            summary.transportModeLabel = transportModeLabel(entry.transportMode);
            summary.quantity = entry.quantity;
            view.siteSummaries.add(summary);
        }
        if (missingItemCount > 0) {
            view.messages.add("有 " + missingItemCount + " 个商品缺少物流计划所需的规格或箱规信息，生成后需要人工补齐。");
        } else {
            view.messages.add("采购单商品规格和箱规信息完整，可以进入运输方式和货代推荐。");
        }
        view.messages.add("已按采购单中的空运/海运拆分数量；后续将只推荐对应货代服务线。");
        String seaRecommendationStatus = appendSeaForwarderRecommendations(view, siteQuantityTotals);
        String airRecommendationStatus = appendAirForwarderRecommendations(view, siteQuantityTotals);
        view.recommendationStatus = mergeRecommendationStatus(seaRecommendationStatus, airRecommendationStatus);

        if (persist) {
            mapper.supersedeCurrentLogisticsPlansByOrder(order.id, operatorUserId);
            mapper.softDeleteLogisticsRecommendationsByOrder(order.id, operatorUserId);
            mapper.insertLogisticsPlan(
                    planId,
                    order.id,
                    order.ownerUserId,
                    order.logicalStoreId,
                    view.planNo,
                    "DRAFT",
                    "PENDING",
                    view.itemCount,
                    view.skuCount,
                    view.totalQuantity,
                    view.missingItemCount,
                    writeJson(view.siteSummaries),
                    writeJson(view),
                    operatorUserId
            );
            persistLogisticsRecommendations(planId, order.id, view.recommendations, operatorUserId);
            log(order.id, null, "GENERATE_LOGISTICS_PLAN", operatorUserId, order.status, order.status, view.planNo);
        }
        return view;
    }

    private void persistLogisticsRecommendations(
            Long planId,
            Long purchaseOrderId,
            List<PurchaseOrderLogisticsRecommendationView> recommendations,
            Long operatorUserId
    ) {
        for (PurchaseOrderLogisticsRecommendationView recommendation : recommendations) {
            LogisticsRecommendationInsertRecord row = new LogisticsRecommendationInsertRecord();
            row.id = mapper.nextLogisticsRecommendationId();
            row.logisticsPlanId = planId;
            row.purchaseOrderId = purchaseOrderId;
            row.routeCode = recommendation.routeCode;
            row.forwarderCode = recommendation.forwarderCode;
            row.serviceCode = recommendation.serviceCode;
            row.transportMode = recommendation.transportMode;
            row.rankNo = recommendation.rank;
            row.recommended = recommendation.recommended;
            row.estimateStatus = recommendation.estimateStatus;
            row.currency = firstComponentCurrency(recommendation);
            row.estimatedTotalAmount = recommendation.estimatedTotalAmount;
            row.recurringAmountPerDay = recommendation.recurringAmountPerDay;
            row.snapshotJson = writeJson(recommendation);
            mapper.insertLogisticsRecommendation(row, operatorUserId);

            for (PurchaseOrderLogisticsCostComponentView component : recommendation.costComponents) {
                LogisticsCostComponentInsertRecord componentRow = new LogisticsCostComponentInsertRecord();
                componentRow.id = mapper.nextLogisticsCostComponentId();
                componentRow.recommendationId = row.id;
                componentRow.logisticsPlanId = planId;
                componentRow.componentType = component.componentType;
                componentRow.componentName = component.componentName;
                componentRow.sourceTable = sourceTableForComponent(component.componentType);
                componentRow.sourceId = component.sourceId;
                componentRow.currency = component.currency;
                componentRow.unitPrice = component.unitPrice;
                componentRow.billingUnit = component.billingUnit;
                componentRow.billableQuantity = component.billableQuantity;
                componentRow.amount = component.amount;
                componentRow.amountStatus = component.amountStatus;
                componentRow.includedInTotal = component.includedInTotal;
                componentRow.formulaText = component.formulaText;
                componentRow.remark = component.remark;
                mapper.insertLogisticsCostComponent(componentRow, operatorUserId);
            }
        }
    }

    private String firstComponentCurrency(PurchaseOrderLogisticsRecommendationView recommendation) {
        if (recommendation == null || recommendation.costComponents == null) {
            return null;
        }
        return recommendation.costComponents.stream()
                .map(component -> component.currency)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String sourceTableForComponent(String componentType) {
        if ("HEADHAUL".equals(componentType) || "LAST_MILE".equals(componentType)) {
            return "forwarder_quote_base_price";
        }
        if (componentType != null && componentType.startsWith("WAREHOUSE_")) {
            return "forwarder_warehouse_processing_fee";
        }
        return null;
    }

    private String appendSeaForwarderRecommendations(
            PurchaseOrderLogisticsPlanView view,
            Map<String, SiteTransportQuantity> siteQuantityTotals
    ) {
        List<SiteTransportQuantity> seaEntries = siteQuantityTotals.values().stream()
                .filter(entry -> TRANSPORT_SEA.equals(entry.transportMode))
                .filter(entry -> entry.quantity > 0)
                .collect(Collectors.toList());
        if (seaEntries.isEmpty()) {
            return "no_sea_quantity";
        }

        List<ForwarderRouteRecommendationRecord> candidates = mapper.listRouteRecommendationCandidates(siteCodesFromEntries(seaEntries), TRANSPORT_SEA);
        if (candidates.isEmpty()) {
            view.messages.add("当前海运站点暂无可用货代报价，不能生成真实货代推荐。");
            return "no_sea_quote";
        }

        boolean blockedBySpecs = hasMissingSeaLooseVolumeInputs(view);
        boolean hasCartonWarnings = hasMissingCartonSpecs(view);
        List<ForwarderRouteRecommendationRecord> sorted = candidates.stream()
                .sorted(Comparator
                        .comparing((ForwarderRouteRecommendationRecord record) -> preferredSeaUnitPrice(record) == null ? BigDecimal.valueOf(Long.MAX_VALUE) : preferredSeaUnitPrice(record))
                        .thenComparing(record -> defaultText(record.forwarderName, record.forwarderCode))
                        .thenComparing(record -> defaultText(record.serviceCode, "")))
                .collect(Collectors.toList());
        RouteCostInputs routeCostInputs = routeCostInputs(sorted);
        int rank = 1;
        for (ForwarderRouteRecommendationRecord candidate : sorted) {
            view.recommendations.add(toSeaForwarderRecommendation(
                    candidate,
                    rank,
                    blockedBySpecs,
                    hasCartonWarnings,
                    view.estimatedSeaVolumeCbm,
                    view,
                    routeCostInputs.basePrices(candidate.routeCode),
                    routeCostInputs.warehouseFees(candidate.routeCode),
                    routeCostInputs.transportFees(candidate.routeCode)
            ));
            rank++;
        }
        if (blockedBySpecs) {
            view.messages.add("已找到 " + sorted.size() + " 条海运货代服务线；当前存在商品尺寸缺失，暂不能按散货体积估算海运费用。");
        } else if (hasCartonWarnings) {
            view.messages.add("已找到 " + sorted.size() + " 条海运货代服务线；当前按商品散货体积 "
                    + defaultText(view.estimatedSeaVolumeCbmText, "-") + " 估算，箱规缺失不阻塞海运草案。");
        } else {
            view.messages.add("已找到 " + sorted.size() + " 条海运货代服务线；当前按商品散货体积 "
                    + defaultText(view.estimatedSeaVolumeCbmText, "-") + " 估算，可进入品类和计费规则复核。");
        }
        return blockedBySpecs ? "sea_candidate_blocked_by_specs" : "sea_candidate_ready";
    }

    private String appendAirForwarderRecommendations(
            PurchaseOrderLogisticsPlanView view,
            Map<String, SiteTransportQuantity> siteQuantityTotals
    ) {
        List<SiteTransportQuantity> airEntries = siteQuantityTotals.values().stream()
                .filter(entry -> TRANSPORT_AIR.equals(entry.transportMode))
                .filter(entry -> entry.quantity > 0)
                .collect(Collectors.toList());
        if (airEntries.isEmpty()) {
            return "no_air_quantity";
        }

        List<ForwarderRouteRecommendationRecord> candidates = mapper.listRouteRecommendationCandidates(siteCodesFromEntries(airEntries), TRANSPORT_AIR);
        if (candidates.isEmpty()) {
            view.messages.add("当前空运站点暂无可用货代报价，不能生成真实货代推荐。");
            return "no_air_quote";
        }

        boolean blockedBySpecs = hasMissingAirChargeableWeightInputs(view);
        List<ForwarderRouteRecommendationRecord> sorted = candidates.stream()
                .sorted(Comparator
                        .comparing((ForwarderRouteRecommendationRecord record) -> preferredAirUnitPrice(record) == null ? BigDecimal.valueOf(Long.MAX_VALUE) : preferredAirUnitPrice(record))
                        .thenComparing(record -> defaultText(record.forwarderName, record.forwarderCode))
                        .thenComparing(record -> defaultText(record.serviceCode, "")))
                .collect(Collectors.toList());
        RouteCostInputs routeCostInputs = routeCostInputs(sorted);
        int rank = 1;
        for (ForwarderRouteRecommendationRecord candidate : sorted) {
            view.recommendations.add(toAirForwarderRecommendation(
                    candidate,
                    rank,
                    blockedBySpecs,
                    view,
                    routeCostInputs.basePrices(candidate.routeCode),
                    routeCostInputs.warehouseFees(candidate.routeCode),
                    routeCostInputs.transportFees(candidate.routeCode)
            ));
            rank++;
        }
        if (blockedBySpecs) {
            view.messages.add("已找到 " + sorted.size() + " 条空运货代服务线；当前存在空运商品尺寸或重量缺失，暂不能按计费重估算空运费用。");
        } else {
            view.messages.add("已找到 " + sorted.size() + " 条空运货代服务线；当前按各货代体积重除数和最低计费规则估算，可进入品类和计费规则复核。");
        }
        return blockedBySpecs ? "air_candidate_blocked_by_specs" : "air_candidate_ready";
    }

    private List<String> siteCodesFromEntries(List<SiteTransportQuantity> entries) {
        return entries.stream()
                .map(entry -> normalizeSiteCode(entry.siteCode))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private RouteCostInputs routeCostInputs(List<ForwarderRouteRecommendationRecord> candidates) {
        List<String> routeCodes = candidates.stream()
                .map(candidate -> candidate.routeCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (routeCodes.isEmpty()) {
            return RouteCostInputs.empty();
        }
        List<ForwarderRouteSegmentRecord> segments = mapper.listRouteSegments(routeCodes);
        Map<String, List<ForwarderRouteSegmentRecord>> segmentsByRoute = segments.stream()
                .collect(Collectors.groupingBy(segment -> segment.routeCode, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ForwarderBasePriceRecord>> basePricesByService = basePricesByService(segments);
        Map<String, List<ForwarderWarehouseProcessingFeeRecord>> warehouseFeesByService = warehouseFeesByService(segments);
        Map<String, List<ForwarderTransportFeeRecord>> transportFeesByService = transportFeesByService(segments);
        return new RouteCostInputs(segmentsByRoute, basePricesByService, warehouseFeesByService, transportFeesByService);
    }

    private Map<String, List<ForwarderBasePriceRecord>> basePricesByService(List<ForwarderRouteSegmentRecord> segments) {
        List<String> serviceCodes = segments.stream()
                .filter(segment -> "LAST_MILE".equals(segment.segmentRole))
                .map(segment -> segment.serviceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (serviceCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return mapper.listBasePricesByServiceCodes(serviceCodes).stream()
                .collect(Collectors.groupingBy(price -> price.serviceCode, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, List<ForwarderWarehouseProcessingFeeRecord>> warehouseFeesByService(List<ForwarderRouteSegmentRecord> segments) {
        List<String> serviceCodes = segments.stream()
                .filter(segment -> "WAREHOUSE_PROCESSING".equals(segment.segmentRole))
                .map(segment -> segment.serviceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (serviceCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return mapper.listWarehouseProcessingFeesByServiceCodes(serviceCodes).stream()
                .collect(Collectors.groupingBy(fee -> fee.serviceCode, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, List<ForwarderTransportFeeRecord>> transportFeesByService(List<ForwarderRouteSegmentRecord> segments) {
        List<String> serviceCodes = segments.stream()
                .filter(segment -> "LAST_MILE".equals(segment.segmentRole))
                .map(segment -> segment.serviceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (serviceCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return mapper.listTransportFeesByServiceCodes(serviceCodes).stream()
                .collect(Collectors.groupingBy(fee -> fee.serviceCode, LinkedHashMap::new, Collectors.toList()));
    }

    private String mergeRecommendationStatus(String seaStatus, String airStatus) {
        boolean hasSeaCandidate = isCandidateRecommendationStatus(seaStatus);
        boolean hasAirCandidate = isCandidateRecommendationStatus(airStatus);
        if (hasSeaCandidate && hasAirCandidate) {
            if (isBlockedRecommendationStatus(seaStatus) || isBlockedRecommendationStatus(airStatus)) {
                return "transport_candidate_blocked_by_specs";
            }
            return "transport_candidate_ready";
        }
        if (hasSeaCandidate) {
            return seaStatus;
        }
        if (hasAirCandidate) {
            return airStatus;
        }
        if ("no_sea_quote".equals(seaStatus) || "no_air_quote".equals(airStatus)) {
            return "no_transport_quote";
        }
        return "no_transport_quantity";
    }

    private boolean isCandidateRecommendationStatus(String status) {
        return StringUtils.hasText(status) && status.contains("candidate");
    }

    private boolean isBlockedRecommendationStatus(String status) {
        return StringUtils.hasText(status) && status.contains("blocked");
    }

    private PurchaseOrderLogisticsRecommendationView toSeaForwarderRecommendation(
            ForwarderRouteRecommendationRecord candidate,
            int rank,
            boolean blockedBySpecs,
            boolean hasCartonWarnings,
            BigDecimal estimatedSeaVolumeCbm,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderBasePriceRecord> segmentBasePrices,
            List<ForwarderWarehouseProcessingFeeRecord> warehouseFees,
            List<ForwarderTransportFeeRecord> transportFees
    ) {
        PurchaseOrderLogisticsRecommendationView view = new PurchaseOrderLogisticsRecommendationView();
        view.rank = rank;
        view.recommended = rank == 1;
        view.routeCode = candidate.routeCode;
        view.routeName = candidate.routeName;
        view.forwarderCode = candidate.forwarderCode;
        view.forwarderName = defaultText(candidate.forwarderName, candidate.forwarderCode);
        view.serviceCode = candidate.serviceCode;
        view.serviceName = candidate.serviceName;
        view.transportMode = normalizeTransportMode(candidate.transportMode);
        view.country = candidate.country;
        view.targetPlatform = candidate.targetPlatform;
        view.deliveryCity = candidate.deliveryCity;
        view.destinationNode = candidate.destinationNode;
        view.transitTimeText = candidate.transitTimeText;
        view.priceSummary = seaPriceSummary(candidate);
        view.cargoCategorySummary = summarizeCsv(candidate.cargoCategoryNamesCsv, 3);
        view.estimateStatus = blockedBySpecs ? "blocked_by_missing_specs" : "loose_volume_estimated";
        if (!blockedBySpecs) {
            costCalculator.enrich(view, candidate, plan, segmentBasePrices, warehouseFees, transportFees);
            view.estimatedCostText = defaultText(costSummaryText(view), estimatedSeaCostText(candidate, estimatedSeaVolumeCbm));
        }
        view.reasons.add("匹配采购单海运数量和 " + defaultText(candidate.country, "目标站点") + " 服务线。");
        if (nonNull(candidate.priceRuleCount) > 0) {
            view.reasons.add("已读取 " + candidate.priceRuleCount + " 条基础报价规则。");
        }
        if (blockedBySpecs) {
            view.risks.add("采购单存在商品尺寸缺失，暂不能按散货体积估算海运费用。");
        } else if (hasCartonWarnings) {
            view.risks.add("箱规缺失未阻塞本次海运估算；当前按商品散货体积计算。");
        }
        if (candidate.minUnitPrice == null) {
            view.risks.add("服务线未提供可直接计价的正常基础单价，需要人工询价。");
        }
        return view;
    }

    private PurchaseOrderLogisticsRecommendationView toAirForwarderRecommendation(
            ForwarderRouteRecommendationRecord candidate,
            int rank,
            boolean blockedBySpecs,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderBasePriceRecord> segmentBasePrices,
            List<ForwarderWarehouseProcessingFeeRecord> warehouseFees,
            List<ForwarderTransportFeeRecord> transportFees
    ) {
        PurchaseOrderLogisticsRecommendationView view = new PurchaseOrderLogisticsRecommendationView();
        view.rank = rank;
        view.recommended = rank == 1;
        view.routeCode = candidate.routeCode;
        view.routeName = candidate.routeName;
        view.forwarderCode = candidate.forwarderCode;
        view.forwarderName = defaultText(candidate.forwarderName, candidate.forwarderCode);
        view.serviceCode = candidate.serviceCode;
        view.serviceName = candidate.serviceName;
        view.transportMode = normalizeTransportMode(candidate.transportMode);
        view.country = candidate.country;
        view.targetPlatform = candidate.targetPlatform;
        view.deliveryCity = candidate.deliveryCity;
        view.destinationNode = candidate.destinationNode;
        view.transitTimeText = candidate.transitTimeText;
        view.priceSummary = seaPriceSummary(candidate);
        view.cargoCategorySummary = summarizeCsv(candidate.cargoCategoryNamesCsv, 3);
        view.estimateStatus = blockedBySpecs ? "air_blocked_by_missing_specs" : "air_chargeable_weight_estimated";
        if (!blockedBySpecs) {
            costCalculator.enrich(view, candidate, plan, segmentBasePrices, warehouseFees, transportFees);
            view.estimatedCostText = defaultText(costSummaryText(view), estimatedAirCostText(candidate, plan));
        }
        view.reasons.add("匹配采购单空运数量和 " + defaultText(candidate.country, "目标站点") + " 服务线。");
        if (nonNull(candidate.priceRuleCount) > 0) {
            view.reasons.add("已读取 " + candidate.priceRuleCount + " 条基础报价规则。");
        }
        if (candidate.volumeDivisor != null) {
            view.reasons.add("体积重除数 " + formatDecimal(candidate.volumeDivisor) + "。");
        }
        if (blockedBySpecs) {
            view.risks.add("采购单存在空运商品尺寸或重量缺失，暂不能按计费重估算空运费用。");
        }
        if (preferredAirKgUnitPrice(candidate) == null) {
            view.risks.add("服务线未提供可直接计价的 KG 正常基础单价，需要人工询价。");
        }
        return view;
    }

    private void addItemsInternal(
            Long orderId,
            List<ItemCommand> itemCommands,
            Long operatorUserId
    ) {
        PurchaseOrderRecord order = requireOrder(orderId);
        if (itemCommands == null || itemCommands.isEmpty()) {
            return;
        }
        Map<String, StoreSiteRecord> availableStoreSites = storeSitesByCode(order.logicalStoreId);
        Map<Long, Set<String>> existingSitesByItemId = existingSitesByItemId(order.id);
        Map<String, Set<String>> pendingSitesByPartnerSku = new LinkedHashMap<>();
        LinkedHashSet<String> nextOrderSiteCodes = new LinkedHashSet<>(readStringList(order.siteCodesJson));
        for (ItemCommand itemCommand : itemCommands) {
            String psku = requiredText(itemCommand == null ? null : itemCommand.psku, "请选择 PSKU。");
            String requestedFulfillmentType = normalizeOptionalFulfillmentType(itemCommand == null ? null : itemCommand.fulfillmentType);
            String fulfillmentSourceName = trimToNull(itemCommand == null ? null : itemCommand.fulfillmentSourceName);
            ProductArchiveRecord product = resolveProduct(order.logicalStoreId, psku);
            List<SiteTransportQuantity> allocations = normalizeSiteTransportQuantities(itemCommand);
            if (allocations.isEmpty()) {
                throw new IllegalArgumentException("请填写 " + psku + " 的站点和数量。");
            }
            ensureNoDuplicateSitesInAllocations(psku, allocations);

            PurchaseOrderItemRecord item = mapper.selectItemByPartnerSku(order.id, product.partnerSku);
            boolean itemAlreadyExisted = item != null;
            if (item == null) {
                item = new PurchaseOrderItemRecord();
                item.id = mapper.nextItemId();
                item.purchaseOrderId = order.id;
                item.ownerUserId = order.ownerUserId;
                item.logicalStoreId = order.logicalStoreId;
                item.productMasterId = product.productMasterId;
                item.productVariantId = product.productVariantId;
                item.skuParent = product.skuParent;
                item.partnerSku = product.partnerSku;
                item.childSku = product.childSku;
                item.titleCache = defaultText(product.title, product.partnerSku);
                item.imageUrlCache = NoonImageUrlNormalizer.normalize(product.imageUrl);
                item.sourceType = "STORE_ARCHIVE";
                item.fulfillmentType = requestedFulfillmentType == null
                        ? FULFILLMENT_WAREHOUSE_RECEIPT
                        : requestedFulfillmentType;
                item.fulfillmentSourceName = fulfillmentSourceName;
                item.createdBy = operatorUserId;
                item.updatedBy = operatorUserId;
                mapper.insertItem(item);
            } else if (
                    requestedFulfillmentType != null
                            && !normalizeFulfillmentType(item.fulfillmentType).equals(requestedFulfillmentType)
            ) {
                throw new IllegalArgumentException(psku + " 已在采购单中选择 "
                        + fulfillmentTypeLabel(item.fulfillmentType)
                        + "，同一采购单商品只能选择一种到货方式。");
            }

            Set<String> existingSites = itemAlreadyExisted
                    ? existingSitesByItemId.getOrDefault(item.id, Collections.emptySet())
                    : Collections.emptySet();
            Set<String> pendingSites = pendingSitesByPartnerSku.computeIfAbsent(
                    product.partnerSku,
                    ignored -> new LinkedHashSet<>()
            );
            for (SiteTransportQuantity allocation : allocations) {
                String siteCode = normalizeSiteCode(allocation.siteCode);
                if (!availableStoreSites.containsKey(siteCode)) {
                    throw new IllegalArgumentException("站点 " + siteCode + " 不属于当前店铺。");
                }
                if (existingSites.contains(siteCode) || pendingSites.contains(siteCode)) {
                    throw new IllegalArgumentException(psku + " 已在站点 " + siteCode
                            + " 加入采购单，不能重复添加相同商品相同站点。");
                }
                nextOrderSiteCodes.add(siteCode);
                ProductOfferRecord offer = mapper.selectProductOffer(order.logicalStoreId, product.partnerSku, product.productVariantId, siteCode);
                if (offer == null) {
                    throw new IllegalArgumentException(psku + " 在站点 " + siteCode + " 没有商品 Offer，不能加入采购单。");
                }
                PurchaseOrderItemSiteRecord site = new PurchaseOrderItemSiteRecord();
                site.id = mapper.nextItemSiteId();
                site.purchaseOrderId = order.id;
                site.purchaseOrderItemId = item.id;
                site.ownerUserId = order.ownerUserId;
                site.logicalStoreId = order.logicalStoreId;
                site.siteId = offer.siteId;
                site.siteCode = offer.siteCode;
                site.productSiteOfferId = offer.productSiteOfferId;
                site.pskuCode = offer.pskuCode;
                site.offerCode = offer.offerCode;
                site.transportMode = allocation.transportMode;
                site.quantity = allocation.quantity;
                site.createdBy = operatorUserId;
                site.updatedBy = operatorUserId;
                mapper.upsertItemSite(site);
                pendingSites.add(siteCode);
            }
            mapper.recalculateItemAggregates(item.id, operatorUserId);
            log(order.id, item.id, "UPSERT_ITEM", operatorUserId, null, null, psku);
        }
        persistOrderSiteCodesIfChanged(order, nextOrderSiteCodes, operatorUserId);
    }

    private Map<Long, Set<String>> existingSitesByItemId(Long orderId) {
        List<PurchaseOrderItemSiteRecord> sites = mapper.listItemSitesByOrder(orderId);
        if (sites == null || sites.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        for (PurchaseOrderItemSiteRecord site : sites) {
            if (site == null || site.purchaseOrderItemId == null) {
                continue;
            }
            String siteCode = normalizeSiteCode(site.siteCode);
            if (!StringUtils.hasText(siteCode)) {
                continue;
            }
            result.computeIfAbsent(site.purchaseOrderItemId, ignored -> new LinkedHashSet<>()).add(siteCode);
        }
        return result;
    }

    private void ensureNoDuplicateSitesInAllocations(String psku, List<SiteTransportQuantity> allocations) {
        Set<String> seenSites = new LinkedHashSet<>();
        for (SiteTransportQuantity allocation : allocations) {
            String siteCode = normalizeSiteCode(allocation == null ? null : allocation.siteCode);
            if (!StringUtils.hasText(siteCode)) {
                continue;
            }
            if (!seenSites.add(siteCode)) {
                throw new IllegalArgumentException(psku + " 在站点 " + siteCode
                        + " 重复填写，不能重复添加相同商品相同站点。");
            }
        }
    }

    private void collectItemInternal(
            PurchaseOrderRecord order,
            PurchaseOrderItemRecord item,
            Long operatorUserId
    ) {
        String status = dbStatus(item);
        if (item.sourceCollectionId != null && ("QUEUED".equals(status) || "RUNNING".equals(status))) {
            return;
        }
        if (!StringUtils.hasText(item.imageUrlCache)) {
            mapper.markItemCollectionFailed(
                    item.id,
                    "missing_product_image",
                    "该商品缺少主图，不能发起 1688 图搜采集。",
                    operatorUserId
            );
            log(order.id, item.id, "COLLECT_SKIPPED", operatorUserId, status, "FAILED", "missing_product_image");
            return;
        }

        mapper.supersedeCurrentAli1688TasksByItem(item.id, operatorUserId);
        mapper.supersedeCurrentLinksByItem(item.id, operatorUserId);
        ProductSelectionSourceCollectionRow source = createHiddenSourceCollection(order, item, operatorUserId);
        Ali1688CollectionView aliView = ali1688CollectionService.ensureTaskForSourceCollection(source, operatorUserId);
        Long linkId = mapper.nextCollectionLinkId();
        String dbStatus = toDbCollectionStatus(aliView.status);
        mapper.insertCollectionLink(
                linkId,
                order.id,
                item.id,
                order.ownerUserId,
                order.logicalStoreId,
                source.getId(),
                parseNullableLong(aliView.taskId),
                "po-item:" + item.id,
                dbStatus,
                nonNull(aliView.progressPercent),
                nonNull(aliView.candidateCount),
                nonNull(aliView.recommendedCount),
                aliView.failureCode,
                aliView.failureMessage,
                writeJson(Map.of(
                        "purchaseOrderId", order.id,
                        "purchaseOrderItemId", item.id,
                        "partnerSku", item.partnerSku
                )),
                null,
                operatorUserId
        );
        mapper.updateItemCollection(
                item.id,
                linkId,
                dbStatus,
                nonNull(aliView.progressPercent),
                nonNull(aliView.candidateCount),
                nonNull(aliView.recommendedCount),
                aliView.failureCode,
                aliView.failureMessage,
                isFinished(dbStatus) ? 1 : 0,
                operatorUserId
        );
        log(order.id, item.id, "START_1688_COLLECTION", operatorUserId, status, dbStatus, aliView.taskId);
    }

    private ProductSelectionSourceCollectionRow createHiddenSourceCollection(
            PurchaseOrderRecord order,
            PurchaseOrderItemRecord item,
            Long operatorUserId
    ) {
        Long sourceId = productSelectionMapper.nextSourceCollectionId();
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(sourceId);
        row.setOwnerUserId(order.ownerUserId);
        row.setLogicalStoreId(order.logicalStoreId);
        row.setCollectionNo("POI-" + sourceId);
        row.setSourceType(HIDDEN_SOURCE_TYPE);
        row.setSourcePlatform("StoreArchive");
        row.setSourceTitle(defaultText(item.titleCache, item.partnerSku));
        row.setSourceTitleCn(defaultText(item.titleCache, item.partnerSku));
        String imageUrl = NoonImageUrlNormalizer.normalize(item.imageUrlCache);
        row.setSourceImageUrl(imageUrl);
        row.setImageUrlsJson(writeStringList(Collections.singletonList(imageUrl)));
        ProductArchiveRecord product = mapper.selectProductArchiveByVariant(item.logicalStoreId, item.productVariantId);
        List<String> specHints = buildSpecHints(product, item);
        row.setSpecHintsJson(writeStringList(specHints));
        row.setSpecAttributeCount(specHints.size());
        row.setSelectedText(buildSelectedText(item, specHints));
        row.setNotes("purchaseOrder=" + order.orderNo + ";itemId=" + item.id);
        row.setStatus("success");
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);
        productSelectionMapper.insertSourceCollection(row);
        ProductSelectionSourceCollectionRow inserted = productSelectionMapper.selectSourceCollectionById(sourceId);
        return inserted == null ? row : inserted;
    }

    private PurchaseOrderView toOrderView(PurchaseOrderRecord order) {
        PurchaseOrderView view = new PurchaseOrderView();
        view.id = String.valueOf(order.id);
        view.orderNo = order.orderNo;
        view.title = order.title;
        view.storeName = defaultText(order.projectNameCache, order.projectCodeCache);
        view.storeCode = order.anchorStoreCodeCache;
        view.ownerName = "";
        view.status = toViewOrderStatus(order.status);
        view.createdAt = order.createdAt;
        view.updatedAt = order.updatedAt;
        view.remark = order.remark;
        view.siteCodes = readStringList(order.siteCodesJson);
        view.logisticsQuoteSummary = toLogisticsQuoteSummary(mapper.listLogisticsQuoteCandidatesByOrder(order.id));

        Map<Long, List<PurchaseOrderItemSiteRecord>> sitesByItem = mapper.listItemSitesByOrder(order.id).stream()
                .collect(Collectors.groupingBy(row -> row.purchaseOrderItemId, LinkedHashMap::new, Collectors.toList()));
        for (PurchaseOrderItemRecord item : mapper.listItemsByOrder(order.id)) {
            view.items.add(toItemView(item, sitesByItem.getOrDefault(item.id, Collections.emptyList())));
        }
        return view;
    }

    private PurchaseOrderItemView toItemView(
            PurchaseOrderItemRecord item,
            List<PurchaseOrderItemSiteRecord> sites
    ) {
        PurchaseOrderItemView view = new PurchaseOrderItemView();
        view.id = String.valueOf(item.id);
        view.sourceCollectionId = item.sourceCollectionId == null ? null : String.valueOf(item.sourceCollectionId);
        view.sourceCollectionNo = item.sourceCollectionNo;
        view.sourcePlatform = "店铺";
        view.sourceTitle = defaultText(item.titleCache, item.partnerSku);
        view.sourceTitleCn = defaultText(item.titleCache, item.partnerSku);
        view.sourceImageUrl = NoonImageUrlNormalizer.normalize(item.imageUrlCache);
        view.variantId = String.valueOf(item.productVariantId);
        view.skuParent = item.skuParent;
        view.partnerSku = item.partnerSku;
        view.productFulltype = item.productFulltypeCache;
        view.productTitle = defaultText(item.titleCache, item.partnerSku);
        view.productImageUrl = NoonImageUrlNormalizer.normalize(item.imageUrlCache);
        view.sourcingSpec = item.sourcingSpecText;
        view.sourcingSize = item.sourcingSizeText;
        view.sourcingColor = item.sourcingColorText;
        view.fulfillmentType = normalizeFulfillmentType(item.fulfillmentType);
        view.fulfillmentTypeLabel = fulfillmentTypeLabel(view.fulfillmentType);
        view.fulfillmentSourceName = trimToNull(item.fulfillmentSourceName);
        view.totalQuantity = nonNull(item.totalQuantity);
        view.collectionStatus = toViewItemStatus(dbStatus(item));
        view.progress = nonNull(firstNonNull(item.aliProgressPercent, item.progressPercent));
        view.currentTaskNo = item.aliTaskNo;
        view.candidateCount = nonNull(firstNonNull(item.aliCandidateCount, item.candidateCount));
        view.top5Count = nonNull(firstNonNull(item.aliRecommendedCount, item.recommendedCount));
        view.failureMessage = firstText(item.aliFailureMessage, item.failureMessage);
        view.lastCollectedAt = firstText(item.aliFinishedAt, item.lastCollectedAt);
        for (PurchaseOrderItemSiteRecord site : sites) {
            view.allocations.add(toSiteAllocationView(site));
        }
        return view;
    }

    private SiteAllocationView toSiteAllocationView(PurchaseOrderItemSiteRecord site) {
        SiteAllocationView view = new SiteAllocationView();
        view.site = site.siteCode;
        view.siteName = siteName(site.siteCode);
        view.siteId = site.siteId;
        view.pskuCode = site.pskuCode;
        view.transportMode = normalizeTransportMode(site.transportMode);
        view.transportModeLabel = transportModeLabel(view.transportMode);
        view.quantity = nonNull(site.quantity);
        view.enabled = "ACTIVE".equals(site.status);
        return view;
    }

    private PurchaseOrderLogisticsPlanLineView toLogisticsPlanLine(
            PurchaseOrderItemRecord item,
            ProductArchiveRecord product,
            List<PurchaseOrderItemSiteRecord> sites
    ) {
        PurchaseOrderLogisticsPlanLineView view = new PurchaseOrderLogisticsPlanLineView();
        view.itemId = String.valueOf(item.id);
        view.partnerSku = item.partnerSku;
        view.productTitle = defaultText(firstText(product == null ? null : product.title, item.titleCache), item.partnerSku);
        view.productImageUrl = NoonImageUrlNormalizer.normalize(firstText(product == null ? null : product.imageUrl, item.imageUrlCache));
        int totalQuantity = nonNull(item.totalQuantity);
        if (totalQuantity <= 0) {
            totalQuantity = sites.stream().mapToInt(site -> nonNull(site.quantity)).sum();
        }
        view.totalQuantity = totalQuantity;
        view.productDimensionsText = dimensionsText(
                product == null ? null : product.productLengthCm,
                product == null ? null : product.productWidthCm,
                product == null ? null : product.productHeightCm,
                "cm"
        );
        view.productWeightText = formatMeasure(product == null ? null : product.productWeightG, "g");
        view.cartonDimensionsText = dimensionsText(
                product == null ? null : product.cartonLengthCm,
                product == null ? null : product.cartonWidthCm,
                product == null ? null : product.cartonHeightCm,
                "cm"
        );
        view.cartonWeightText = formatMeasure(product == null ? null : product.cartonWeightKg, "kg");
        view.cartonQuantity = product == null ? null : product.cartonQuantity;
        view.looseVolumeCbm = looseVolumeCbm(product, totalQuantity);
        view.looseVolumeCbmText = formatCbm(view.looseVolumeCbm);
        view.seaQuantity = sites.stream()
                .filter(site -> TRANSPORT_SEA.equals(normalizeTransportMode(site.transportMode)))
                .mapToInt(site -> nonNull(site.quantity))
                .sum();
        view.seaLooseVolumeCbm = looseVolumeCbm(product, view.seaQuantity);
        view.seaLooseVolumeCbmText = formatCbm(view.seaLooseVolumeCbm);
        view.airQuantity = sites.stream()
                .filter(site -> TRANSPORT_AIR.equals(normalizeTransportMode(site.transportMode)))
                .mapToInt(site -> nonNull(site.quantity))
                .sum();
        view.airActualWeightKg = actualWeightKg(product, view.airQuantity);
        view.airActualWeightKgText = formatKg(view.airActualWeightKg);
        view.airLooseVolumeCbm = looseVolumeCbm(product, view.airQuantity);
        view.airLooseVolumeCbmText = formatCbm(view.airLooseVolumeCbm);
        view.specSourceType = product == null ? null : product.specSourceType;
        for (PurchaseOrderItemSiteRecord site : sites) {
            view.allocations.add(toSiteAllocationView(site));
        }
        appendLogisticsMissingFields(view.missingFields, item, product);
        return view;
    }

    private void appendLogisticsMissingFields(
            List<String> target,
            PurchaseOrderItemRecord item,
            ProductArchiveRecord product
    ) {
        if (product == null) {
            target.add("商品规格快照缺失");
            return;
        }
        if (!allPresent(product.productLengthCm, product.productWidthCm, product.productHeightCm)) {
            target.add("商品尺寸缺失");
        }
        if (product.productWeightG == null) {
            target.add("商品重量缺失");
        }
        if (!allPresent(product.cartonLengthCm, product.cartonWidthCm, product.cartonHeightCm)) {
            target.add("箱规尺寸缺失");
        }
        if (product.cartonWeightKg == null) {
            target.add("箱重缺失");
        }
        if (product.cartonQuantity == null || product.cartonQuantity <= 0) {
            target.add("装箱数缺失");
        }
    }

    private boolean hasMissingLogisticsSpecs(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return false;
        }
        return view.lines.stream().anyMatch(line -> line.missingFields != null && !line.missingFields.isEmpty());
    }

    private boolean hasMissingSeaLooseVolumeInputs(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return false;
        }
        return view.lines.stream()
                .filter(line -> nonNull(line.seaQuantity) > 0)
                .anyMatch(line -> line.seaLooseVolumeCbm == null);
    }

    private boolean hasMissingAirChargeableWeightInputs(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return false;
        }
        return view.lines.stream()
                .filter(line -> nonNull(line.airQuantity) > 0)
                .anyMatch(line -> line.airActualWeightKg == null || line.airLooseVolumeCbm == null);
    }

    private boolean hasMissingCartonSpecs(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return false;
        }
        return view.lines.stream()
                .filter(line -> nonNull(line.seaQuantity) > 0)
                .anyMatch(line -> line.missingFields != null && line.missingFields.stream()
                        .anyMatch(field -> field != null && field.contains("箱")));
    }

    private BigDecimal totalSeaLooseVolumeCbm(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasVolume = false;
        for (PurchaseOrderLogisticsPlanLineView line : view.lines) {
            if (line == null || line.seaLooseVolumeCbm == null) {
                continue;
            }
            total = total.add(line.seaLooseVolumeCbm);
            hasVolume = true;
        }
        return hasVolume ? total : null;
    }

    private BigDecimal totalAirChargeableWeightKg(PurchaseOrderLogisticsPlanView view, BigDecimal volumeDivisor) {
        BigDecimal actualWeightKg = totalAirActualWeightKg(view);
        BigDecimal volumeWeightKg = totalAirVolumeWeightKg(view, volumeDivisor);
        if (actualWeightKg == null || volumeWeightKg == null) {
            return null;
        }
        return actualWeightKg.max(volumeWeightKg);
    }

    private BigDecimal totalAirActualWeightKg(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasWeight = false;
        for (PurchaseOrderLogisticsPlanLineView line : view.lines) {
            if (line == null || nonNull(line.airQuantity) <= 0 || line.airActualWeightKg == null) {
                continue;
            }
            total = total.add(line.airActualWeightKg);
            hasWeight = true;
        }
        return hasWeight ? total : null;
    }

    private BigDecimal totalAirVolumeWeightKg(PurchaseOrderLogisticsPlanView view, BigDecimal volumeDivisor) {
        BigDecimal totalVolumeCbm = totalAirLooseVolumeCbm(view);
        BigDecimal divisor = validVolumeDivisor(volumeDivisor);
        if (totalVolumeCbm == null || divisor == null) {
            return null;
        }
        return totalVolumeCbm
                .multiply(CUBIC_CM_PER_CBM)
                .divide(divisor, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal totalAirLooseVolumeCbm(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasVolume = false;
        for (PurchaseOrderLogisticsPlanLineView line : view.lines) {
            if (line == null || nonNull(line.airQuantity) <= 0 || line.airLooseVolumeCbm == null) {
                continue;
            }
            total = total.add(line.airLooseVolumeCbm);
            hasVolume = true;
        }
        return hasVolume ? total : null;
    }

    private BigDecimal looseVolumeCbm(ProductArchiveRecord product, int quantity) {
        if (product == null || quantity <= 0) {
            return null;
        }
        if (!allPresent(product.productLengthCm, product.productWidthCm, product.productHeightCm)) {
            return null;
        }
        return product.productLengthCm
                .multiply(product.productWidthCm)
                .multiply(product.productHeightCm)
                .multiply(BigDecimal.valueOf(quantity))
                .divide(CUBIC_CM_PER_CBM, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal actualWeightKg(ProductArchiveRecord product, int quantity) {
        if (product == null || quantity <= 0 || product.productWeightG == null) {
            return null;
        }
        return product.productWeightG
                .multiply(BigDecimal.valueOf(quantity))
                .divide(GRAMS_PER_KG, 8, RoundingMode.HALF_UP);
    }

    private void appendQuoteCountryNames(Set<String> target, String siteCode) {
        String normalized = normalizeSiteCode(siteCode);
        switch (normalized) {
            case "SA":
                target.add("沙特");
                target.add("SA");
                target.add("Saudi Arabia");
                target.add("Saudi");
                return;
            case "AE":
                target.add("阿联酋");
                target.add("UAE");
                target.add("AE");
                target.add("United Arab Emirates");
                return;
            default:
                if (StringUtils.hasText(normalized)) {
                    target.add(normalized);
                }
        }
    }

    private String seaPriceSummary(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return "需询价";
        }
        List<String> parts = new ArrayList<>();
        String currency = defaultText(candidate.currency, "");
        if (candidate.cbmMinUnitPrice != null) {
            parts.add(formatPricePart(currency, candidate.cbmMinUnitPrice, "CBM"));
        }
        if (candidate.kgMinUnitPrice != null) {
            parts.add(formatPricePart(currency, candidate.kgMinUnitPrice, "KG"));
        }
        if (!parts.isEmpty()) {
            return String.join("；", parts);
        }
        if (candidate.minUnitPrice == null) {
            return "需询价";
        }
        String billingUnit = defaultText(candidate.billingUnit, "单位");
        return formatPricePart(currency, candidate.minUnitPrice, billingUnit);
    }

    private String seaPriceSummary(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return "需询价";
        }
        List<String> parts = new ArrayList<>();
        String currency = defaultText(candidate.currency, "");
        if (candidate.cbmMinUnitPrice != null) {
            parts.add(formatPricePart(currency, candidate.cbmMinUnitPrice, "CBM"));
        }
        if (candidate.kgMinUnitPrice != null) {
            parts.add(formatPricePart(currency, candidate.kgMinUnitPrice, "KG"));
        }
        if (!parts.isEmpty()) {
            return String.join("；", parts);
        }
        if (candidate.minUnitPrice == null) {
            return "需询价";
        }
        String billingUnit = defaultText(candidate.billingUnit, "单位");
        return formatPricePart(currency, candidate.minUnitPrice, billingUnit);
    }

    private BigDecimal preferredSeaUnitPrice(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        return candidate.minUnitPrice;
    }

    private BigDecimal preferredSeaUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        return candidate.minUnitPrice;
    }

    private BigDecimal preferredSeaCbmUnitPrice(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        if ("CBM".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private BigDecimal preferredSeaCbmUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        if ("CBM".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private String estimatedSeaCostText(ForwarderSeaRecommendationRecord candidate, BigDecimal estimatedSeaVolumeCbm) {
        BigDecimal unitPrice = preferredSeaCbmUnitPrice(candidate);
        if (unitPrice == null || estimatedSeaVolumeCbm == null || estimatedSeaVolumeCbm.signum() <= 0) {
            return null;
        }
        BigDecimal amount = unitPrice.multiply(estimatedSeaVolumeCbm).setScale(2, RoundingMode.HALF_UP);
        String currency = defaultText(candidate.currency, "");
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatMoney(amount) + "（按散货 " + formatCbm(estimatedSeaVolumeCbm) + " 估算）";
    }

    private String estimatedSeaCostText(ForwarderRouteRecommendationRecord candidate, BigDecimal estimatedSeaVolumeCbm) {
        BigDecimal unitPrice = preferredSeaCbmUnitPrice(candidate);
        if (unitPrice == null || estimatedSeaVolumeCbm == null || estimatedSeaVolumeCbm.signum() <= 0) {
            return null;
        }
        BigDecimal amount = unitPrice.multiply(estimatedSeaVolumeCbm).setScale(2, RoundingMode.HALF_UP);
        String currency = defaultText(candidate.currency, "");
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatMoney(amount) + "（按散货 " + formatCbm(estimatedSeaVolumeCbm) + " 估算）";
    }

    private BigDecimal preferredAirUnitPrice(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        return candidate.minUnitPrice;
    }

    private BigDecimal preferredAirUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        return candidate.minUnitPrice;
    }

    private BigDecimal preferredAirKgUnitPrice(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        if ("KG".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private BigDecimal preferredAirKgUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        if ("KG".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private String estimatedAirCostText(
            ForwarderSeaRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan
    ) {
        BigDecimal unitPrice = preferredAirKgUnitPrice(candidate);
        BigDecimal chargeableWeightKg = totalAirChargeableWeightKg(plan, candidate == null ? null : candidate.volumeDivisor);
        if (unitPrice == null || chargeableWeightKg == null || chargeableWeightKg.signum() <= 0) {
            return null;
        }
        BigDecimal amount = unitPrice.multiply(chargeableWeightKg).setScale(2, RoundingMode.HALF_UP);
        String currency = defaultText(candidate.currency, "");
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatMoney(amount) + "（按空运计费重 " + formatKg(chargeableWeightKg) + " 估算）";
    }

    private String estimatedAirCostText(
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan
    ) {
        BigDecimal unitPrice = preferredAirKgUnitPrice(candidate);
        BigDecimal chargeableWeightKg = totalAirChargeableWeightKg(plan, candidate == null ? null : candidate.volumeDivisor);
        if (unitPrice == null || chargeableWeightKg == null || chargeableWeightKg.signum() <= 0) {
            return null;
        }
        BigDecimal amount = unitPrice.multiply(chargeableWeightKg).setScale(2, RoundingMode.HALF_UP);
        String currency = defaultText(candidate.currency, "");
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatMoney(amount) + "（按空运计费重 " + formatKg(chargeableWeightKg) + " 估算）";
    }

    private String costSummaryText(PurchaseOrderLogisticsRecommendationView recommendation) {
        if (!StringUtils.hasText(recommendation.estimatedTotalCostText)) {
            return null;
        }
        if (StringUtils.hasText(recommendation.recurringCostText)) {
            return recommendation.estimatedTotalCostText + " + " + recommendation.recurringCostText + "仓储";
        }
        return recommendation.estimatedTotalCostText;
    }

    private BigDecimal validVolumeDivisor(BigDecimal volumeDivisor) {
        if (volumeDivisor != null && volumeDivisor.signum() > 0) {
            return volumeDivisor;
        }
        return DEFAULT_AIR_VOLUME_DIVISOR;
    }

    private String formatPricePart(String currency, BigDecimal value, String billingUnit) {
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatDecimal(value) + "/" + defaultText(billingUnit, "单位") + " 起";
    }

    private String summarizeCsv(String csv, int limit) {
        if (!StringUtils.hasText(csv)) {
            return null;
        }
        List<String> values = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            String value = trim(raw);
            if (StringUtils.hasText(value) && seen.add(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        int safeLimit = Math.max(limit, 1);
        if (values.size() <= safeLimit) {
            return String.join("、", values);
        }
        return String.join("、", values.subList(0, safeLimit)) + " 等 " + values.size() + " 类";
    }

    private List<Ali1688CollectionView.SpecValue> purchaseOrderItemSpecs(PurchaseOrderItemRecord item) {
        List<Ali1688CollectionView.SpecValue> specs = new ArrayList<>();
        addSpec(specs, "规格", item.sourcingSpecText);
        addSpec(specs, "尺寸", item.sourcingSizeText);
        addSpec(specs, "颜色", item.sourcingColorText);
        return specs;
    }

    private List<Ali1688CollectionView.SpecValue> mergeSourceSpecs(
            List<Ali1688CollectionView.SpecValue> preferred,
            List<Ali1688CollectionView.SpecValue> fallback
    ) {
        List<Ali1688CollectionView.SpecValue> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendSpecs(merged, seen, preferred);
        appendSpecs(merged, seen, fallback);
        return merged;
    }

    private void appendSpecs(
            List<Ali1688CollectionView.SpecValue> target,
            Set<String> seen,
            List<Ali1688CollectionView.SpecValue> specs
    ) {
        if (specs == null) {
            return;
        }
        for (Ali1688CollectionView.SpecValue spec : specs) {
            if (spec == null || !StringUtils.hasText(spec.name) || !StringUtils.hasText(spec.value)) {
                continue;
            }
            String key = spec.name.trim() + "\n" + spec.value.trim();
            if (seen.add(key)) {
                target.add(new Ali1688CollectionView.SpecValue(spec.name.trim(), spec.value.trim()));
            }
        }
    }

    private void addSpec(List<Ali1688CollectionView.SpecValue> specs, String name, String value) {
        String trimmed = trim(value);
        if (StringUtils.hasText(trimmed)) {
            specs.add(new Ali1688CollectionView.SpecValue(name, trimmed));
        }
    }

    private ProductOptionView toProductOptionView(ProductArchiveRecord record) {
        ProductOptionView view = new ProductOptionView();
        view.variantId = record.productVariantId == null ? null : String.valueOf(record.productVariantId);
        view.skuParent = record.skuParent;
        view.partnerSku = record.partnerSku;
        view.productTitle = defaultText(record.title, record.partnerSku);
        view.productImageUrl = NoonImageUrlNormalizer.normalize(record.imageUrl);
        view.availableSiteCodes = splitCsv(record.availableSiteCodesCsv);
        return view;
    }

    private List<String> buildSpecHints(ProductArchiveRecord product, PurchaseOrderItemRecord item) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        String partnerSku = firstText(product == null ? null : product.partnerSku, item.partnerSku);
        String childSku = firstText(product == null ? null : product.childSku, item.childSku);
        addHint(hints, "PSKU", partnerSku);
        addHint(hints, "Noon SKU", childSku);
        ProcurementPurchaseOrderSourcingRequirement.of(
                item.sourcingSpecText,
                item.sourcingSizeText,
                item.sourcingColorText
        ).toSpecHints().forEach(hints::add);
        addHint(hints, "Size", product == null ? null : product.sizeEn);
        addHint(hints, "Size AR", product == null ? null : product.sizeAr);
        addDimensionHint(
                hints,
                "Product dimensions",
                product == null ? null : product.productLengthCm,
                product == null ? null : product.productWidthCm,
                product == null ? null : product.productHeightCm,
                "cm"
        );
        addHint(hints, "Product weight", formatMeasure(product == null ? null : product.productWeightG, "g"));
        addDimensionHint(
                hints,
                "Carton dimensions",
                product == null ? null : product.cartonLengthCm,
                product == null ? null : product.cartonWidthCm,
                product == null ? null : product.cartonHeightCm,
                "cm"
        );
        addHint(hints, "Carton weight", formatMeasure(product == null ? null : product.cartonWeightKg, "kg"));
        addHint(hints, "Carton quantity", product == null || product.cartonQuantity == null ? null : String.valueOf(product.cartonQuantity));
        addHint(hints, "Spec source", product == null ? null : product.specSourceType);
        return new ArrayList<>(hints);
    }

    private String buildSelectedText(PurchaseOrderItemRecord item, List<String> specHints) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(item.partnerSku)) {
            parts.add(item.partnerSku.trim());
        }
        specHints.stream()
                .filter(text -> text.startsWith("规格:")
                        || text.startsWith("尺寸:")
                        || text.startsWith("颜色:")
                        || text.startsWith("Size:")
                        || text.startsWith("Product dimensions:")
                        || text.startsWith("Carton dimensions:"))
                .forEach(parts::add);
        return String.join("; ", parts);
    }

    private void addDimensionHint(
            LinkedHashSet<String> hints,
            String label,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            String unit
    ) {
        if (length == null && width == null && height == null) {
            return;
        }
        String value = formatDecimal(length) + " x " + formatDecimal(width) + " x " + formatDecimal(height) + " " + unit;
        addHint(hints, label, value);
    }

    private String dimensionsText(BigDecimal length, BigDecimal width, BigDecimal height, String unit) {
        if (length == null && width == null && height == null) {
            return null;
        }
        return formatDecimal(length) + " x " + formatDecimal(width) + " x " + formatDecimal(height) + " " + unit;
    }

    private boolean allPresent(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return false;
        }
        for (BigDecimal value : values) {
            if (value == null) {
                return false;
            }
        }
        return true;
    }

    private void addHint(LinkedHashSet<String> hints, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = value.trim();
        if (!StringUtils.hasText(normalized) || "null".equalsIgnoreCase(normalized)) {
            return;
        }
        hints.add(label + ": " + normalized);
    }

    private String formatMeasure(BigDecimal value, String unit) {
        return value == null ? null : formatDecimal(value) + " " + unit;
    }

    private String formatCbm(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " CBM";
    }

    private String formatKg(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " KG";
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private ProductArchiveRecord resolveProduct(Long logicalStoreId, String psku) {
        List<ProductArchiveRecord> matches = mapper.listProductArchiveMatches(logicalStoreId, psku);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("PSKU 不属于当前店铺商品档案，不能加入采购单。");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("PSKU 命中多个商品档案，请用更精确的 PSKU。");
        }
        return matches.get(0);
    }

    private PurchaseOrderRecord requireOrder(Long orderId) {
        PurchaseOrderRecord order = mapper.selectOrderById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("采购单不存在或已删除。");
        }
        return order;
    }

    private PurchaseOrderRecord requireOrderAccess(BusinessAccessContext access, Long orderId) {
        return requireOrderAccess(access, requireOrder(orderId));
    }

    private PurchaseOrderRecord requireOrderAccess(BusinessAccessContext access, PurchaseOrderRecord order) {
        if (access == null || !access.canAccessStore(order.anchorStoreCodeCache)) {
            throw new IllegalArgumentException("当前账号不能操作该采购单。");
        }
        return order;
    }

    private void assertOrderEditable(PurchaseOrderRecord order) {
        if (isOrderSubmitted(order)) {
            throw new IllegalArgumentException("采购单已提交，不能再更改。");
        }
    }

    private boolean isOrderSubmitted(PurchaseOrderRecord order) {
        return order != null && ORDER_SUBMITTED.equals(normalizeUpper(order.status));
    }

    private ShippingOrderRecord requireShippingOrderAccess(BusinessAccessContext access, Long shippingOrderId) {
        ShippingOrderRecord order = mapper.selectShippingOrderById(shippingOrderId);
        if (order == null) {
            throw new IllegalArgumentException("发货单不存在或已删除。");
        }
        if (access == null || !ownerUserId(access).equals(order.ownerUserId)) {
            throw new IllegalArgumentException("当前账号不能操作该发货单。");
        }
        return order;
    }

    private Long ownerUserId(BusinessAccessContext access) {
        if (access == null || access.getBusinessOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板范围。");
        }
        return access.getBusinessOwnerUserId();
    }

    private StoreScopeRecord requireStoreScope(BusinessAccessContext access, String requestedStoreCode) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        String storeCode = StringUtils.hasText(requestedStoreCode)
                ? requestedStoreCode.trim()
                : access.getStoreCodes().stream().findFirst().orElse(null);
        if (!StringUtils.hasText(storeCode)) {
            throw new IllegalArgumentException("请选择店铺。");
        }
        if (!access.canAccessStore(storeCode)) {
            throw new IllegalArgumentException("当前账号不能操作该店铺。");
        }
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        StoreScopeRecord scope = mapper.selectStoreScope(ownerUserId, storeCode);
        if (scope == null || scope.logicalStoreId == null) {
            throw new IllegalArgumentException("该店铺尚未初始化商品档案，不能创建采购单。");
        }
        return scope;
    }

    private List<String> normalizeSiteCodes(List<String> requestedSiteCodes, Long logicalStoreId) {
        Map<String, StoreSiteRecord> sites = storeSitesByCode(logicalStoreId);
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("当前店铺没有可用站点。");
        }
        List<String> source = requestedSiteCodes == null || requestedSiteCodes.isEmpty()
                ? new ArrayList<>(sites.keySet())
                : requestedSiteCodes;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String siteCode : source) {
            String normalizedSite = normalizeSiteCode(siteCode);
            if (!sites.containsKey(normalizedSite)) {
                throw new IllegalArgumentException("站点 " + normalizedSite + " 不属于当前店铺。");
            }
            normalized.add(normalizedSite);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个站点。");
        }
        return new ArrayList<>(normalized);
    }

    private Map<String, StoreSiteRecord> storeSitesByCode(Long logicalStoreId) {
        return mapper.listStoreSites(logicalStoreId).stream()
                .collect(Collectors.toMap(
                        site -> normalizeSiteCode(site.siteCode),
                        site -> site,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<String> siteCodesFromItems(List<ItemCommand> itemCommands) {
        LinkedHashSet<String> siteCodes = new LinkedHashSet<>();
        if (itemCommands == null) {
            return new ArrayList<>();
        }
        for (ItemCommand command : itemCommands) {
            if (command == null) {
                continue;
            }
            if (command.siteQuantities != null) {
                for (SiteQuantityCommand siteQuantity : command.siteQuantities) {
                    String siteCode = normalizeSiteCode(siteQuantity == null ? null : siteQuantity.siteCode);
                    if (StringUtils.hasText(siteCode)) {
                        siteCodes.add(siteCode);
                    }
                }
            }
            String siteCode = normalizeSiteCode(command.site);
            if (StringUtils.hasText(siteCode)) {
                siteCodes.add(siteCode);
            }
        }
        return new ArrayList<>(siteCodes);
    }

    private void persistOrderSiteCodesIfChanged(
            PurchaseOrderRecord order,
            LinkedHashSet<String> nextOrderSiteCodes,
            Long operatorUserId
    ) {
        List<String> current = readStringList(order.siteCodesJson);
        List<String> next = new ArrayList<>(nextOrderSiteCodes);
        if (!current.equals(next)) {
            mapper.updateOrderSiteCodes(order.id, writeStringList(next), operatorUserId);
            order.siteCodesJson = writeStringList(next);
        }
    }

    private List<SiteTransportQuantity> normalizeSiteTransportQuantities(ItemCommand command) {
        Map<String, SiteTransportQuantity> result = new LinkedHashMap<>();
        if (command != null && command.siteQuantities != null) {
            for (SiteQuantityCommand siteQuantity : command.siteQuantities) {
                addSiteTransportQuantity(
                        result,
                        siteQuantity == null ? null : siteQuantity.siteCode,
                        siteQuantity == null ? null : siteQuantity.transportMode,
                        siteQuantity == null ? null : siteQuantity.quantity
                );
            }
        }
        if (result.isEmpty() && command != null) {
            addSiteTransportQuantity(result, command.site, command.transportMode, command.quantity);
        }
        return new ArrayList<>(result.values());
    }

    private void addSiteTransportQuantity(
            Map<String, SiteTransportQuantity> target,
            String rawSiteCode,
            String rawTransportMode,
            Integer quantity
    ) {
        String siteCode = normalizeSiteCode(rawSiteCode);
        if (!StringUtils.hasText(siteCode) || quantity == null || quantity <= 0) {
            return;
        }
        String transportMode = normalizeTransportMode(rawTransportMode);
        String key = siteCode + "|" + transportMode;
        SiteTransportQuantity current = target.get(key);
        if (current == null) {
            target.put(key, new SiteTransportQuantity(siteCode, transportMode, quantity));
            return;
        }
        current.quantity += quantity;
    }

    private static String normalizeTransportMode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "AIR":
            case "空":
            case "空运":
                return TRANSPORT_AIR;
            case "EXPRESS":
            case "快递":
                return TRANSPORT_EXPRESS;
            case "SEA":
            case "海":
            case "海运":
                return TRANSPORT_SEA;
            case "UNSPECIFIED":
            case "未分配":
            case "未指定":
                return TRANSPORT_UNSPECIFIED;
            default:
                return TRANSPORT_UNSPECIFIED;
        }
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String transportModeLabel(String transportMode) {
        switch (normalizeTransportMode(transportMode)) {
            case TRANSPORT_AIR:
                return "空";
            case TRANSPORT_EXPRESS:
                return "快递";
            case TRANSPORT_SEA:
                return "海";
            default:
                return "未分配";
        }
    }

    private void log(
            Long orderId,
            Long itemId,
            String operationType,
            Long operatorUserId,
            String beforeStatus,
            String afterStatus,
            String detail
    ) {
        mapper.insertOperationLog(
                mapper.nextOperationLogId(),
                orderId,
                itemId,
                operationType,
                operatorUserId,
                beforeStatus,
                afterStatus,
                detail == null ? null : writeJson(Map.of("detail", detail))
        );
    }

    private String dbStatus(PurchaseOrderItemRecord item) {
        return toDbCollectionStatus(firstText(item.aliStatus, item.collectionStatus));
    }

    private String toDbCollectionStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "WAITING_SOURCE":
            case "NOT_STARTED":
                return "NOT_STARTED";
            case "QUEUED":
                return "QUEUED";
            case "RUNNING":
                return "RUNNING";
            case "SUCCESS":
            case "SUCCEEDED":
                return "SUCCEEDED";
            case "PARTIAL_SUCCESS":
            case "PARTIAL_SUCCEEDED":
                return "PARTIAL_SUCCEEDED";
            case "FAILED":
                return "FAILED";
            default:
                return "NOT_STARTED";
        }
    }

    private String toViewItemStatus(String value) {
        String dbStatus = toDbCollectionStatus(value);
        if ("QUEUED".equals(dbStatus) || "RUNNING".equals(dbStatus)) {
            return "collecting";
        }
        if ("SUCCEEDED".equals(dbStatus) || "PARTIAL_SUCCEEDED".equals(dbStatus)) {
            return "succeeded";
        }
        if ("FAILED".equals(dbStatus)) {
            return "failed";
        }
        return "not_started";
    }

    private String toViewOrderStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case ORDER_SUBMITTED:
                return "submitted";
            case "READY":
                return "pending_collection";
            case "COLLECTING":
                return "collecting";
            case "PARTIAL_DONE":
                return "partial_done";
            case "COMPLETED":
                return "done";
            case "ABNORMAL":
                return "exception";
            default:
                return "draft";
        }
    }

    private boolean isFinished(String dbStatus) {
        return "SUCCEEDED".equals(dbStatus) || "PARTIAL_SUCCEEDED".equals(dbStatus) || "FAILED".equals(dbStatus);
    }

    private Long parseNullableLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private Long parseLongId(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(message);
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception error) {
            return new ArrayList<>();
        }
    }

    private String writeStringList(List<String> values) {
        return writeJson(values == null ? Collections.emptyList() : values);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("采购单 JSON 序列化失败。", error);
        }
    }

    private List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String normalized = normalizeSiteCode(item);
            if (StringUtils.hasText(normalized) && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String normalizeSiteCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeOptionalFulfillmentType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return normalizeFulfillmentType(value);
    }

    private String normalizeFulfillmentType(String value) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            return FULFILLMENT_WAREHOUSE_RECEIPT;
        }
        String upper = text.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (FULFILLMENT_WAREHOUSE_RECEIPT.equals(upper)
                || "WAREHOUSE".equals(upper)
                || "WAREHOUSE_RECEIVING".equals(upper)
                || "货到仓库".equals(text)
                || "到仓".equals(text)
                || "仓库".equals(text)
                || "入仓".equals(text)) {
            return FULFILLMENT_WAREHOUSE_RECEIPT;
        }
        if (FULFILLMENT_FACTORY_DIRECT.equals(upper)
                || "FACTORY".equals(upper)
                || "FORWARDER".equals(upper)
                || "FORWARDER_RECEIPT".equals(upper)
                || "货到货代".equals(text)
                || "货代".equals(text)
                || "厂家".equals(text)
                || "厂家直发".equals(text)
                || "直发货代".equals(text)) {
            return FULFILLMENT_FACTORY_DIRECT;
        }
        throw new IllegalArgumentException("不支持的到货方式：" + text);
    }

    private String fulfillmentTypeLabel(String fulfillmentType) {
        return FULFILLMENT_FACTORY_DIRECT.equals(normalizeFulfillmentType(fulfillmentType))
                ? "货到货代"
                : "货到仓库";
    }

    private String normalizeYiteMaterial(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        if (!YITE_MATERIAL_OPTIONS.contains(text)) {
            throw new IllegalArgumentException("义特材质只能选择：塑料、陶瓷、金属、纸、纺织、木制。");
        }
        return text;
    }

    private static String normalizeLogisticsQuoteStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (LOGISTICS_QUOTE_CONFIRMED.equals(normalized)
                || "已确认".equals(value)
                || "确认".equals(value)) {
            return LOGISTICS_QUOTE_CONFIRMED;
        }
        return LOGISTICS_QUOTE_PENDING;
    }

    private String logisticsQuoteStatusLabel(String value) {
        return LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(value)) ? "已确认" : "待报价";
    }

    private String normalizeShippingSubmitStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (SHIPPING_SUBMITTED.equals(normalized)
                || "已提交".equals(value)
                || "已提交发货".equals(value)) {
            return SHIPPING_SUBMITTED;
        }
        if (SHIPPING_PARTIAL_SUBMITTED.equals(normalized)
                || "部分提交".equals(value)
                || "部分提交发货".equals(value)) {
            return SHIPPING_PARTIAL_SUBMITTED;
        }
        return SHIPPING_NOT_SUBMITTED;
    }

    private String shippingSubmitStatusLabel(String value) {
        String normalized = normalizeShippingSubmitStatus(value);
        if (SHIPPING_SUBMITTED.equals(normalized)) {
            return "已提交发货";
        }
        if (SHIPPING_PARTIAL_SUBMITTED.equals(normalized)) {
            return "部分提交";
        }
        return "未提交";
    }

    private PurchaseOrderLogisticsQuoteSummaryView toLogisticsQuoteSummary(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        PurchaseOrderLogisticsQuoteSummaryView summary = new PurchaseOrderLogisticsQuoteSummaryView();
        for (PurchaseOrderLogisticsQuoteLineRecord line : emptyIfNull(lines)) {
            summary.totalLineCount += 1;
            if (Boolean.TRUE.equals(line.isNewProduct)) {
                summary.newProductLineCount += 1;
            }
            if (LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(line.quoteStatus))) {
                summary.confirmedLineCount += 1;
            } else {
                summary.pendingLineCount += 1;
            }
            if (SHIPPING_SUBMITTED.equals(normalizeShippingSubmitStatus(line.shippingSubmitStatus))) {
                summary.submittedLineCount += 1;
            }
        }
        summary.shippingSubmitStatus =
                summary.totalLineCount > 0 && summary.totalLineCount.equals(summary.submittedLineCount)
                        ? SHIPPING_SUBMITTED
                        : SHIPPING_NOT_SUBMITTED;
        return summary;
    }

    private String safeFilePart(String value) {
        String text = defaultText(value, "采购单");
        return text.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String requiredText(String value, String message) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        String text = trim(value);
        return StringUtils.hasText(text) ? text : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String stableProductKey(String sourceStoreCode, String partnerSku, Long productVariantId) {
        String psku = defaultText(partnerSku, "");
        if (!psku.isEmpty()) {
            String store = defaultText(sourceStoreCode, "");
            return store.isEmpty() ? "psku:" + psku : store + "|psku:" + psku;
        }
        return "variant:" + productVariantId;
    }

    private String truncateText(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first == null ? second : first;
    }

    private Integer nonNull(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void addTrimmed(Set<String> target, String value) {
        String text = trim(value);
        if (StringUtils.hasText(text)) {
            target.add(text);
        }
    }

    private String ali1688HistoryGroupKey(
            String siteCode,
            String partnerSku,
            String pskuCode,
            String skuParent
    ) {
        return normalizeSiteCode(siteCode) + ":"
                + defaultText(firstText(partnerSku, skuParent), "").toUpperCase(Locale.ROOT);
    }

    private void fillPagination(PurchaseOrderAli1688HistoryView view) {
        view.pagination.page = 1;
        view.pagination.pageSize = view.items.size();
        view.pagination.total = view.items.size();
        view.unlinkedAssignedLineCount = 0;
    }

    private String siteName(String siteCode) {
        if ("SA".equalsIgnoreCase(siteCode)) {
            return "沙特 SA";
        }
        if ("AE".equalsIgnoreCase(siteCode)) {
            return "阿联酋 AE";
        }
        return siteCode;
    }

    private static final class LogisticsQuoteExportOption {
        private ForwarderRouteRecommendationRecord candidate;
        private String templateType;
        private Integer pendingLineCount = 0;
        private Integer newProductLineCount = 0;
    }

    private static final class Ali1688HistoryAccumulator {
        private String storeCode;
        private String siteCode;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String productTitle;
        private final LinkedHashMap<Long, PurchaseOrderAli1688PurchaseBatchView> purchaseBatches = new LinkedHashMap<>();
        private final LinkedHashSet<String> batchSourceKeys = new LinkedHashSet<>();
        private final LinkedHashMap<String, PurchaseOrderAli1688HistorySourceView> historyByOrder = new LinkedHashMap<>();

        private Ali1688HistoryAccumulator(PurchaseOrderAli1688PurchaseBatchRow row) {
            fillIdentity(row.storeCode, row.siteCode, row.skuParent, row.partnerSku, row.pskuCode, null);
        }

        private Ali1688HistoryAccumulator(PurchaseOrderAli1688HistoryRow row) {
            fillIdentity(row.storeCode, row.siteCode, row.skuParent, row.partnerSku, row.pskuCode, row.productTitle);
        }

        private void add(PurchaseOrderAli1688PurchaseBatchRow row) {
            fillIdentity(row.storeCode, row.siteCode, row.skuParent, row.partnerSku, row.pskuCode, null);
            PurchaseOrderAli1688PurchaseBatchView batch = purchaseBatches.computeIfAbsent(row.id, ignored -> toBatchView(row));
            PurchaseOrderAli1688HistorySourceView source = toBatchSourceView(row);
            if (source == null) {
                return;
            }
            String key = sourceKey(source);
            if (batchSourceKeys.add(row.id + ":" + key)) {
                batch.sources.add(source);
            }
        }

        private void add(PurchaseOrderAli1688HistoryRow row) {
            fillIdentity(row.storeCode, row.siteCode, row.skuParent, row.partnerSku, row.pskuCode, row.productTitle);
            PurchaseOrderAli1688HistorySourceView source = toHistorySourceView(row);
            String key = historyOrderKey(source);
            PurchaseOrderAli1688HistorySourceView existing = historyByOrder.get(key);
            if (existing == null) {
                historyByOrder.put(key, source);
            } else {
                mergeHistorySource(existing, source);
            }
        }

        private PurchaseOrderAli1688HistoryItemView toView() {
            PurchaseOrderAli1688HistoryItemView view = new PurchaseOrderAli1688HistoryItemView();
            view.storeCode = storeCode;
            view.siteCode = siteCode;
            view.skuParent = skuParent;
            view.partnerSku = partnerSku;
            view.pskuCode = pskuCode;
            view.productTitle = productTitle;
            view.purchaseBatches = new ArrayList<>(purchaseBatches.values());
            view.history = new ArrayList<>(historyByOrder.values());

            if (!view.purchaseBatches.isEmpty()) {
                view.purchaseCount = view.purchaseBatches.size();
                view.totalQuantity = view.purchaseBatches.stream()
                        .map(batch -> batch.countedQuantity)
                        .filter(quantity -> quantity != null)
                        .mapToInt(Integer::intValue)
                        .sum();
                view.totalCost = view.purchaseBatches.stream()
                        .map(batch -> batch.countedCost)
                        .filter(cost -> cost != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                view.averageUnitPrice = unitPrice(view.totalCost, view.totalQuantity);
                PurchaseOrderAli1688PurchaseBatchView latestBatch = latestBatch(view.purchaseBatches);
                view.recentPurchaseTime = latestBatchSourceTime(latestBatch);
                view.recentUnitPrice = latestBatch == null ? null : firstNonNull(latestBatch.unitPrice, view.averageUnitPrice);
                return view;
            }

            view.purchaseCount = view.history.size();
            view.totalQuantity = view.history.stream()
                    .map(source -> source.assignedQuantity)
                    .filter(quantity -> quantity != null)
                    .mapToInt(Integer::intValue)
                    .sum();
            view.totalCost = view.history.stream()
                    .map(source -> source.allocatedCost)
                    .filter(cost -> cost != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            view.averageUnitPrice = unitPrice(view.totalCost, view.totalQuantity);
            PurchaseOrderAli1688HistorySourceView latestHistory = latestHistory(view.history);
            view.recentPurchaseTime = latestHistory == null ? null : latestHistory.orderTime;
            view.recentUnitPrice = latestHistory == null ? view.averageUnitPrice : firstNonNull(latestHistory.unitPrice, view.averageUnitPrice);
            return view;
        }

        private void fillIdentity(
                String storeCode,
                String siteCode,
                String skuParent,
                String partnerSku,
                String pskuCode,
                String productTitle
        ) {
            this.storeCode = firstNonBlank(this.storeCode, storeCode);
            this.siteCode = firstNonBlank(this.siteCode, siteCode);
            this.skuParent = firstNonBlank(this.skuParent, skuParent);
            this.partnerSku = firstNonBlank(this.partnerSku, partnerSku);
            this.pskuCode = firstNonBlank(this.pskuCode, pskuCode);
            this.productTitle = firstNonBlank(this.productTitle, productTitle);
        }

        private static PurchaseOrderAli1688PurchaseBatchView toBatchView(PurchaseOrderAli1688PurchaseBatchRow row) {
            PurchaseOrderAli1688PurchaseBatchView view = new PurchaseOrderAli1688PurchaseBatchView();
            view.id = row.id;
            view.label = row.batchLabel;
            view.batchSequence = row.batchSequence;
            view.countedQuantity = row.countedQuantity;
            view.countedCost = row.countedCost;
            view.unitPrice = unitPrice(row.countedCost, row.countedQuantity);
            view.note = row.note;
            return view;
        }

        private static PurchaseOrderAli1688HistorySourceView toBatchSourceView(
                PurchaseOrderAli1688PurchaseBatchRow row
        ) {
            if (row.sourceOrderId == null
                    && row.sourceItemId == null
                    && row.sourceAssignmentId == null
                    && !StringUtils.hasText(row.orderNo)) {
                return null;
            }
            PurchaseOrderAli1688HistorySourceView view = new PurchaseOrderAli1688HistorySourceView();
            view.orderId = row.sourceOrderId;
            view.itemId = row.sourceItemId;
            view.assignmentId = row.sourceAssignmentId;
            view.orderNo = row.orderNo;
            view.orderTime = row.orderTime;
            view.supplierName = row.supplierName;
            return view;
        }

        private static PurchaseOrderAli1688HistorySourceView toHistorySourceView(PurchaseOrderAli1688HistoryRow row) {
            PurchaseOrderAli1688HistorySourceView view = new PurchaseOrderAli1688HistorySourceView();
            view.allocationId = row.allocationId;
            view.orderId = row.orderId;
            view.itemId = row.itemId;
            view.assignmentId = row.assignmentId;
            view.orderNo = row.orderNo;
            view.orderTime = row.orderTime;
            view.supplierName = row.supplierName;
            view.assignedQuantity = row.assignedQuantity;
            view.allocatedCost = row.allocatedCost;
            view.unitPrice = row.unitPrice;
            view.sourceLineLabel = row.sourceLineLabel;
            view.allocationBasis = row.allocationBasis;
            view.evidenceText = row.evidenceText;
            return view;
        }

        private static String sourceKey(PurchaseOrderAli1688HistorySourceView source) {
            if (source.allocationId != null) {
                return "allocation:" + source.allocationId;
            }
            if (source.assignmentId != null) {
                return "assignment:" + source.assignmentId;
            }
            return defaultString(source.orderNo) + ":" + defaultString(source.itemId) + ":" + defaultString(source.orderTime);
        }

        private static String historyOrderKey(PurchaseOrderAli1688HistorySourceView source) {
            if (source.allocationId != null) {
                return "allocation:" + source.allocationId;
            }
            if (StringUtils.hasText(source.orderNo)) {
                return "orderNo:" + source.orderNo;
            }
            if (source.orderId != null) {
                return "orderId:" + source.orderId;
            }
            return sourceKey(source);
        }

        private static void mergeHistorySource(
                PurchaseOrderAli1688HistorySourceView target,
                PurchaseOrderAli1688HistorySourceView source
        ) {
            target.assignedQuantity = addNullable(target.assignedQuantity, source.assignedQuantity);
            target.allocatedCost = addNullable(target.allocatedCost, source.allocatedCost);
            target.unitPrice = unitPrice(target.allocatedCost, target.assignedQuantity);
            if (!StringUtils.hasText(target.orderTime) || compareNullableText(source.orderTime, target.orderTime) > 0) {
                target.orderTime = source.orderTime;
            }
            target.supplierName = firstNonBlank(target.supplierName, source.supplierName);
            target.sourceLineLabel = firstNonBlank(target.sourceLineLabel, source.sourceLineLabel);
            target.allocationBasis = firstNonBlank(target.allocationBasis, source.allocationBasis);
            target.evidenceText = firstNonBlank(target.evidenceText, source.evidenceText);
        }

        private static PurchaseOrderAli1688PurchaseBatchView latestBatch(
                List<PurchaseOrderAli1688PurchaseBatchView> batches
        ) {
            PurchaseOrderAli1688PurchaseBatchView latest = null;
            String latestTime = null;
            for (PurchaseOrderAli1688PurchaseBatchView batch : batches) {
                String sourceTime = latestBatchSourceTime(batch);
                if (latest == null || compareNullableText(sourceTime, latestTime) > 0) {
                    latest = batch;
                    latestTime = sourceTime;
                }
            }
            return latest;
        }

        private static String latestBatchSourceTime(PurchaseOrderAli1688PurchaseBatchView batch) {
            if (batch == null || batch.sources == null) {
                return null;
            }
            String latest = null;
            for (PurchaseOrderAli1688HistorySourceView source : batch.sources) {
                if (source != null && compareNullableText(source.orderTime, latest) > 0) {
                    latest = source.orderTime;
                }
            }
            return latest;
        }

        private static PurchaseOrderAli1688HistorySourceView latestHistory(
                List<PurchaseOrderAli1688HistorySourceView> sources
        ) {
            PurchaseOrderAli1688HistorySourceView latest = null;
            for (PurchaseOrderAli1688HistorySourceView source : sources) {
                if (source != null && (latest == null || compareNullableText(source.orderTime, latest.orderTime) > 0)) {
                    latest = source;
                }
            }
            return latest;
        }

        private static BigDecimal unitPrice(BigDecimal cost, Integer quantity) {
            if (cost == null || quantity == null || quantity <= 0) {
                return null;
            }
            return cost.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        private static Integer addNullable(Integer left, Integer right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left + right;
        }

        private static BigDecimal addNullable(BigDecimal left, BigDecimal right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left.add(right);
        }

        private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
            return first == null ? second : first;
        }

        private static int compareNullableText(String left, String right) {
            if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
                return 0;
            }
            if (!StringUtils.hasText(left)) {
                return -1;
            }
            if (!StringUtils.hasText(right)) {
                return 1;
            }
            return left.compareTo(right);
        }

        private static String firstNonBlank(String first, String second) {
            return StringUtils.hasText(first) ? first : second;
        }

        private static String defaultString(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }

    private static final class RouteCostInputs {
        private final Map<String, List<ForwarderRouteSegmentRecord>> segmentsByRoute;
        private final Map<String, List<ForwarderBasePriceRecord>> basePricesByService;
        private final Map<String, List<ForwarderWarehouseProcessingFeeRecord>> warehouseFeesByService;
        private final Map<String, List<ForwarderTransportFeeRecord>> transportFeesByService;

        private RouteCostInputs(
                Map<String, List<ForwarderRouteSegmentRecord>> segmentsByRoute,
                Map<String, List<ForwarderBasePriceRecord>> basePricesByService,
                Map<String, List<ForwarderWarehouseProcessingFeeRecord>> warehouseFeesByService,
                Map<String, List<ForwarderTransportFeeRecord>> transportFeesByService
        ) {
            this.segmentsByRoute = segmentsByRoute;
            this.basePricesByService = basePricesByService;
            this.warehouseFeesByService = warehouseFeesByService;
            this.transportFeesByService = transportFeesByService;
        }

        private static RouteCostInputs empty() {
            return new RouteCostInputs(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        private List<ForwarderBasePriceRecord> basePrices(String routeCode) {
            List<ForwarderBasePriceRecord> values = new ArrayList<>();
            for (ForwarderRouteSegmentRecord segment : segmentsByRoute.getOrDefault(routeCode, Collections.emptyList())) {
                if ("LAST_MILE".equals(segment.segmentRole)) {
                    values.addAll(basePricesByService.getOrDefault(segment.serviceCode, Collections.emptyList()));
                }
            }
            return values;
        }

        private List<ForwarderWarehouseProcessingFeeRecord> warehouseFees(String routeCode) {
            List<ForwarderWarehouseProcessingFeeRecord> values = new ArrayList<>();
            for (ForwarderRouteSegmentRecord segment : segmentsByRoute.getOrDefault(routeCode, Collections.emptyList())) {
                if ("WAREHOUSE_PROCESSING".equals(segment.segmentRole)) {
                    values.addAll(warehouseFeesByService.getOrDefault(segment.serviceCode, Collections.emptyList()));
                }
            }
            return values;
        }

        private List<ForwarderTransportFeeRecord> transportFees(String routeCode) {
            List<ForwarderTransportFeeRecord> values = new ArrayList<>();
            for (ForwarderRouteSegmentRecord segment : segmentsByRoute.getOrDefault(routeCode, Collections.emptyList())) {
                if ("LAST_MILE".equals(segment.segmentRole)) {
                    values.addAll(transportFeesByService.getOrDefault(segment.serviceCode, Collections.emptyList()));
                }
            }
            return values;
        }
    }

    private static final class YiteProductImage {
        private final byte[] bytes;
        private final int pictureType;
        private final int width;
        private final int height;

        private YiteProductImage(byte[] bytes, int pictureType, int width, int height) {
            this.bytes = bytes;
            this.pictureType = pictureType;
            this.width = width;
            this.height = height;
        }
    }

    private static final class SiteTransportQuantity {
        private final String siteCode;
        private final String transportMode;
        private int quantity;

        private SiteTransportQuantity(String siteCode, String transportMode, int quantity) {
            this.siteCode = siteCode;
            this.transportMode = transportMode;
            this.quantity = quantity;
        }
    }
}
