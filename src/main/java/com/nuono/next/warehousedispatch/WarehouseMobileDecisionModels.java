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

final class MobileShippingDecisionRequest {
    final String siteCode;
    final String transportMode;
    final boolean sensitiveConfirmed;
    final String generationMode;
    final List<String> targetForwarderCodes;
    final List<String> targetOptionKeys;
    final LinkedHashMap<Long, Integer> requested;

    MobileShippingDecisionRequest(
            String siteCode,
            String transportMode,
            boolean sensitiveConfirmed,
            String generationMode,
            List<String> targetForwarderCodes,
            List<String> targetOptionKeys,
            LinkedHashMap<Long, Integer> requested
    ) {
        this.siteCode = siteCode;
        this.transportMode = transportMode;
        this.sensitiveConfirmed = sensitiveConfirmed;
        this.generationMode = generationMode;
        this.targetForwarderCodes = targetForwarderCodes;
        this.targetOptionKeys = targetOptionKeys;
        this.requested = requested;
    }
}

final class ShippingDecisionEvaluation {
    String decisionStatus = "BLOCKED";
    String generationMode = "AUTO";
    List<ShippingBatchSourceRecord> sources = new ArrayList<>();
    final List<ShippingDecisionOption> candidates = new ArrayList<>();
    final List<ShippingDecisionOption> rankedOptions = new ArrayList<>();
    ShippingDecisionOption recommended;
    ShippingDecisionOption alternative;
    final List<String> blockers = new ArrayList<>();
    final List<String> reviewReasons = new ArrayList<>();
}

final class ShippingDecisionOption {
    ShippingOptionDefinition definition;
    ShippingSuggestionOptionView view;
    String optionKey;
    String decisionStatus = "BLOCKED";
    final List<String> blockers = new ArrayList<>();
    final List<String> reviewReasons = new ArrayList<>();
}

final class ForwarderAllocationSummary {
    final String forwarderCode;
    final String forwarderName;
    int quantity;
    final Set<String> pskus = new LinkedHashSet<>();

    ForwarderAllocationSummary(String forwarderCode, String forwarderName) {
        this.forwarderCode = forwarderCode;
        this.forwarderName = forwarderName;
    }

    MobileShippingDecisionForwarderAllocationView toView(int totalQuantity) {
        MobileShippingDecisionForwarderAllocationView view = new MobileShippingDecisionForwarderAllocationView();
        view.forwarderCode = forwarderCode;
        view.forwarderName = forwarderName;
        view.quantity = quantity;
        view.pskuCount = pskus.size();
        view.quantityShare = totalQuantity <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(quantity)
                        .divide(BigDecimal.valueOf(totalQuantity), 4, RoundingMode.HALF_UP);
        return view;
    }
}
