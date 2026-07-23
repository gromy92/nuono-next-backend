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

abstract class WarehouseMobileShippingOperations extends WarehouseDispatchPlanOperations {

    protected WarehouseMobileShippingOperations(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

@Transactional
    public MobileShippingDecisionPreviewView previewMobileShippingDecision(
            BusinessAccessContext access,
            MobileShippingDecisionPreviewCommand command
    ) {
        MobileShippingDecisionRequest request = mobileShippingDecisionRequest(command);
        List<FulfillmentBalanceRecord> balances = mapper.selectBalancesForUpdate(new ArrayList<>(request.requested.keySet()));
        ShippingDecisionEvaluation evaluation = evaluateMobileShippingDecision(access, request, balances);
        return toMobileShippingDecisionPreviewView(evaluation);
    }

@Transactional
    public MobileShippingDecisionConfirmView confirmMobileShippingDecision(
            BusinessAccessContext access,
            MobileShippingDecisionConfirmCommand command
    ) {
        MobileShippingDecisionRequest request = mobileShippingDecisionRequest(command);
        List<FulfillmentBalanceRecord> balances = mapper.selectBalancesForUpdate(new ArrayList<>(request.requested.keySet()));
        ShippingDecisionEvaluation evaluation = evaluateMobileShippingDecision(access, request, balances);
        ShippingDecisionOption accepted = acceptedMobileShippingDecisionOption(evaluation, command.acceptedOptionKey);
        if (accepted == null || "BLOCKED".equals(accepted.decisionStatus)) {
            throw new IllegalArgumentException(evaluation.blockers.isEmpty()
                    ? "没有可生成的物流计划方案。"
                    : evaluation.blockers.get(0));
        }

        CreateShippingBatchCommand batchCommand = new CreateShippingBatchCommand();
        batchCommand.remark = trimToNull(command.remark);
        for (Map.Entry<Long, Integer> entry : request.requested.entrySet()) {
            ShippingBatchSourceCommand source = new ShippingBatchSourceCommand();
            source.fulfillmentBalanceId = entry.getKey();
            source.quantity = entry.getValue();
            batchCommand.sources.add(source);
        }
        ShippingBatchView batch = createShippingBatch(access, batchCommand);

        ShippingSuggestionOptionView persistedOption = persistedMobileDecisionOption(access, batch, accepted);
        ShippingBatchView selectedBatch = selectShippingOption(access, batch.id, persistedOption.id);
        MobileShippingDecisionConfirmView view = new MobileShippingDecisionConfirmView();
        view.shippingBatchId = selectedBatch.id;
        view.batchNo = selectedBatch.batchNo;
        view.status = selectedBatch.status;
        view.decisionStatus = accepted.decisionStatus;
        view.recommendedOptionId = persistedOption.id;
        view.recommendedSummary = toMobileShippingDecisionOptionView(accepted, persistedOption.id);
        view.nextAction = "CREATE_OUTBOUND_ORDER";
        return view;
    }
}
