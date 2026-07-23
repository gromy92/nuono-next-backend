package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

abstract class WarehouseDispatchServiceState {
    protected static final String FULFILLMENT_WAREHOUSE = "WAREHOUSE_RECEIPT";
    protected static final String FULFILLMENT_FACTORY = "FACTORY_DIRECT";
    protected static final String TRANSPORT_AIR = "AIR";
    protected static final String TRANSPORT_SEA = "SEA";
    protected static final String TRANSPORT_UNSPECIFIED = "UNSPECIFIED";
    protected static final String LOGISTICS_QUOTE_CONFIRMED = "CONFIRMED";
    protected static final String SHIPPING_SUBMITTED = "SUBMITTED";
    protected static final String LOGISTICS_QUOTE_BLOCK_MESSAGE = "物流报价未确认或运营未提交发货，仓库暂不能装箱。";
    protected static final BigDecimal CUBIC_CM_PER_CBM = BigDecimal.valueOf(1_000_000L);
    protected static final BigDecimal GRAMS_PER_KG = BigDecimal.valueOf(1_000L);
    protected static final BigDecimal DEFAULT_AIR_VOLUME_DIVISOR = BigDecimal.valueOf(5000L);
    protected static final BigDecimal DEFAULT_YT_SEA_MIN_CBM = BigDecimal.valueOf(0.2);
    protected static final List<ShippingOptionDefinition> DEFAULT_SHIPPING_OPTIONS = List.of(
            new ShippingOptionDefinition("AUTO_RECOMMEND", "自动推荐组合", 95, "AUTO",
                    List.of("ZD", "YT"), List.of("众鸫", "义特"), true, "ZD", "YT"),
            new ShippingOptionDefinition("FORWARDER_ET", "易通单货代", 88, "SINGLE",
                    List.of("ET"), List.of("易通"), false, "ET", "ET"),
            new ShippingOptionDefinition("FORWARDER_ZD", "众鸫单货代", 84, "SINGLE",
                    List.of("ZD"), List.of("众鸫"), false, "ZD", "ZD"),
            new ShippingOptionDefinition("COMBINATION_ZD_YT", "众鸫空运 + 义特海运", 86, "COMBINATION",
                    List.of("ZD", "YT"), List.of("众鸫", "义特"), false, "ZD", "YT"),
            new ShippingOptionDefinition("COMBINATION_ET_ZD", "易通 + 众鸫组合", 82, "COMBINATION",
                    List.of("ET", "ZD"), List.of("易通", "众鸫"), false, "ZD", "ET")
    );

    protected final WarehouseDispatchMapper mapper;
    protected final ObjectMapper objectMapper;

    protected WarehouseDispatchServiceState(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }
}
