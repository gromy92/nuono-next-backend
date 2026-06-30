package com.nuono.next.outboundfee;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/official-outbound-fee")
public class OfficialOutboundFeeController {

    private final OfficialOutboundFeeCalculationService calculationService;
    private final BusinessAccessResolver businessAccessResolver;

    public OfficialOutboundFeeController(
            OfficialOutboundFeeCalculationService calculationService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.calculationService = calculationService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @PostMapping("/calculate-by-effective-spec")
    public OfficialOutboundFeeCalculationView calculateByEffectiveSpec(
            @RequestBody(required = false) OfficialOutboundFeeCalculationCommand command,
            HttpServletRequest request
    ) {
        try {
            OfficialOutboundFeeCalculationCommand effectiveCommand =
                    command == null ? new OfficialOutboundFeeCalculationCommand() : command;
            BusinessAccessContext context = requireProfitStoreAccess(request, effectiveCommand.getStoreCode());
            effectiveCommand.setOwnerUserId(ownerUserId(context, effectiveCommand.getStoreCode()));
            effectiveCommand.setOperatorUserId(context.getSessionUserId());
            return calculationService.calculateByEffectiveSpec(effectiveCommand);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/calculate-by-noon-official-spec")
    public OfficialOutboundFeeCalculationView calculateByNoonOfficialSpec(
            @RequestBody(required = false) OfficialOutboundFeeCalculationCommand command,
            HttpServletRequest request
    ) {
        try {
            OfficialOutboundFeeCalculationCommand effectiveCommand =
                    command == null ? new OfficialOutboundFeeCalculationCommand() : command;
            BusinessAccessContext context = requireProfitStoreAccess(request, effectiveCommand.getStoreCode());
            effectiveCommand.setOwnerUserId(ownerUserId(context, effectiveCommand.getStoreCode()));
            effectiveCommand.setOperatorUserId(context.getSessionUserId());
            return calculationService.calculateByNoonOfficialSpec(effectiveCommand);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/batch-calculate-by-effective-spec")
    public List<OfficialOutboundFeeCalculationView> batchCalculateByEffectiveSpec(
            @RequestBody(required = false) OfficialOutboundFeeBatchCalculationCommand command,
            HttpServletRequest request
    ) {
        return batchCalculate(command, request, false);
    }

    @PostMapping("/batch-calculate-by-noon-official-spec")
    public List<OfficialOutboundFeeCalculationView> batchCalculateByNoonOfficialSpec(
            @RequestBody(required = false) OfficialOutboundFeeBatchCalculationCommand command,
            HttpServletRequest request
    ) {
        return batchCalculate(command, request, true);
    }

    @PostMapping("/latest-calculations")
    public List<OfficialOutboundFeeCalculationView> latestCalculations(
            @RequestBody(required = false) OfficialOutboundFeeLatestCalculationQuery query,
            HttpServletRequest request
    ) {
        try {
            OfficialOutboundFeeLatestCalculationQuery effectiveQuery =
                    query == null ? new OfficialOutboundFeeLatestCalculationQuery() : query;
            BusinessAccessContext context = requireProfitStoreAccess(request, effectiveQuery.getStoreCode());
            effectiveQuery.setOwnerUserId(ownerUserId(context, effectiveQuery.getStoreCode()));
            return calculationService.latestCalculations(effectiveQuery);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private List<OfficialOutboundFeeCalculationView> batchCalculate(
            OfficialOutboundFeeBatchCalculationCommand command,
            HttpServletRequest request,
            boolean noonOfficialSpec
    ) {
        try {
            OfficialOutboundFeeBatchCalculationCommand effectiveCommand =
                    command == null ? new OfficialOutboundFeeBatchCalculationCommand() : command;
            if (!effectiveCommand.hasItems()) {
                throw new IllegalArgumentException("待计算 SKU 不能为空。");
            }
            BusinessAccessContext context = requireProfitStoreAccess(request, effectiveCommand.getStoreCode());
            Long ownerUserId = ownerUserId(context, effectiveCommand.getStoreCode());
            List<OfficialOutboundFeeCalculationView> results = new ArrayList<>();
            for (OfficialOutboundFeeCalculationCommand item : effectiveCommand.getItems()) {
                item.setOwnerUserId(ownerUserId);
                item.setStoreCode(effectiveCommand.getStoreCode());
                item.setSite(StringUtils.hasText(item.getSite()) ? item.getSite() : effectiveCommand.getSite());
                item.setCalculationDate(item.getCalculationDate() == null ? effectiveCommand.getCalculationDate() : item.getCalculationDate());
                item.setOperatorUserId(context.getSessionUserId());
                try {
                    results.add(noonOfficialSpec
                            ? calculationService.calculateByNoonOfficialSpec(item)
                            : calculationService.calculateByEffectiveSpec(item));
                } catch (RuntimeException exception) {
                    results.add(batchItemFailure(item, ownerUserId, effectiveCommand.getStoreCode(), noonOfficialSpec, exception));
                }
            }
            return results;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private BusinessAccessContext requireProfitStoreAccess(HttpServletRequest request, String storeCode) {
        if (!StringUtils.hasText(storeCode)) {
            throw new IllegalArgumentException("店铺不能为空。");
        }
        return businessAccessResolver.requireStoreAccess(request, BusinessCapability.PROFIT, storeCode);
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }

    private OfficialOutboundFeeCalculationView batchItemFailure(
            OfficialOutboundFeeCalculationCommand command,
            Long ownerUserId,
            String storeCode,
            boolean noonOfficialSpec,
            RuntimeException exception
    ) {
        String site = normalizeSite(command.getSite());
        OfficialOutboundFeeCalculationView view = new OfficialOutboundFeeCalculationView();
        view.setReady(false);
        view.setStatus("FAILED");
        view.setFailureCode("BATCH_ITEM_FAILED");
        view.setMessage(exception.getMessage() == null ? "当前 SKU 出舱费计算失败。" : exception.getMessage());
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(storeCode);
        view.setSkuId(command.getSkuId());
        view.setSite(site);
        view.setCountry("AE".equals(site) ? "UAE" : "KSA");
        view.setPlatform("NOON");
        view.setFulfillmentType("FBN");
        view.setSalePrice(command.getSalePrice());
        view.setMarketCurrency("AE".equals(site) ? "AED" : "SAR");
        view.setSpecSourceType(noonOfficialSpec ? "noon_official" : null);
        return view;
    }

    private String normalizeSite(String site) {
        String normalized = site == null ? "" : site.trim().toUpperCase(Locale.ROOT);
        if ("AE".equals(normalized) || "UAE".equals(normalized)) {
            return "AE";
        }
        return "SA";
    }
}
