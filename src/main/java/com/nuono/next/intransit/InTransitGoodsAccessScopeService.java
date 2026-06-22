package com.nuono.next.intransit;

import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.operationsconfig.OperationConfigScopeRepository;
import com.nuono.next.operationsconfig.OperationConfigStoreScope;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InTransitGoodsAccessScopeService {

    private final OperationConfigScopeRepository repository;

    public InTransitGoodsAccessScopeService(OperationConfigScopeRepository repository) {
        this.repository = repository;
    }

    public void applyReadableBatchScope(BusinessAccessContext context, InTransitBatchQuery query) {
        InTransitBatchQuery resolved = query == null ? new InTransitBatchQuery() : query;
        if (StringUtils.hasText(resolved.getTargetStoreCode())) {
            resolved.setTargetStoreCode(InTransitDestination.require(resolved.getTargetStoreCode()).code());
        }
        resolved.setTargetSiteCode(cleanUpper(resolved.getTargetSiteCode()));
        resolved.setAllowedStoreSites(List.of());
        resolved.setAccessScopeRestricted(false);
    }

    public void requireWritableBatchScope(BusinessAccessContext context, SaveBatchCommand command) {
        if (command == null) {
            return;
        }
        if (StringUtils.hasText(command.getTargetStoreCode())) {
            command.setTargetStoreCode(InTransitDestination.require(command.getTargetStoreCode()).code());
        }
        command.setTargetSiteCode(cleanUpper(command.getTargetSiteCode()));
    }

    public void requireWritableLineScope(BusinessAccessContext context, SaveLineCommand command) {
        if (command == null) {
            return;
        }
        requireWritableStoreSite(context, command.getStoreCode(), command.getSiteCode());
        command.setStoreCode(cleanUpper(command.getStoreCode()));
        command.setSiteCode(cleanUpper(command.getSiteCode()));
    }

    public void requireBatchAccess(BusinessAccessContext context, BatchView batch) {
        if (batch == null) {
            throw new BusinessAccessDeniedException("在途批次不存在。");
        }
    }

    public void requireReadableStoreSite(BusinessAccessContext context, String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode) && !StringUtils.hasText(siteCode)) {
            return;
        }
        requireAuthorizedStoreSite(authorizedStoreSites(context), cleanUpper(storeCode), cleanUpper(siteCode));
    }

    public void requireWritableStoreSite(BusinessAccessContext context, String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode) && !StringUtils.hasText(siteCode)) {
            return;
        }
        requireAuthorizedStoreSite(authorizedStoreSites(context), cleanUpper(storeCode), cleanUpper(siteCode));
    }

    private List<InTransitStoreSiteScope> authorizedStoreSites(BusinessAccessContext context) {
        if (context == null || !context.isBusinessAccount()) {
            throw new BusinessAccessDeniedException("当前账号不能操作店铺业务。");
        }
        return repository.listStoreSitesByStoreCodes(context.getStoreCodes()).stream()
                .filter(store -> context.canAccessStore(store.getStoreCode()))
                .filter(store -> matchesOwner(context, store))
                .map(store -> new InTransitStoreSiteScope(cleanUpper(store.getStoreCode()), cleanUpper(store.getSiteCode())))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean matchesOwner(BusinessAccessContext context, OperationConfigStoreScope store) {
        if (context.isBossAccount() && context.getBusinessOwnerUserId() != null) {
            return context.getBusinessOwnerUserId().equals(store.getOwnerUserId());
        }
        Long mappedOwnerUserId = context.resolveOwnerUserIdForStore(store.getStoreCode());
        return mappedOwnerUserId != null && mappedOwnerUserId.equals(store.getOwnerUserId());
    }

    private InTransitStoreSiteScope requireAuthorizedStoreSite(
            List<InTransitStoreSiteScope> authorized,
            String storeCode,
            String siteCode
    ) {
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
        return authorized.stream()
                .filter(scope -> storeCode.equals(scope.getStoreCode()))
                .filter(scope -> siteCode.equals(scope.getSiteCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessAccessDeniedException("当前账号不能操作该店铺。"));
    }

    private static String cleanUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
