package com.nuono.next.procurement.aliorder;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Boss-only, owner-scoped immediate execution of the existing 1688 history reader. */
@RestController
@RequestMapping("/api/procurement/ali1688-orders")
public class Ali1688HistoricalOrderManualSyncController {

    private final Ali1688HistoricalOrderMapper mapper;
    private final Ali1688HistoricalOrderManualSync syncService;
    private final BusinessAccessResolver accessResolver;

    public Ali1688HistoricalOrderManualSyncController(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688HistoricalOrderManualSync syncService,
            BusinessAccessResolver accessResolver
    ) {
        this.mapper = mapper;
        this.syncService = syncService;
        this.accessResolver = accessResolver;
    }

    @PostMapping("/sync-now")
    public ResponseEntity<Void> syncNow(HttpServletRequest request) {
        BusinessAccessContext context = accessResolver.requireBusinessContext(
                request, BusinessCapability.ALI1688_HISTORICAL_ORDERS
        );
        if (context == null || context.getAccountType() != BusinessAccountType.BOSS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅老板可以立即补拉 1688 历史订单。");
        }
        Long ownerUserId = context.getBusinessOwnerUserId() == null
                ? context.getSessionUserId() : context.getBusinessOwnerUserId();
        Ali1688HistoricalOrderAuthorizationRow authorization = ownerUserId == null
                ? null : mapper.selectCurrentAuthorization(ownerUserId);
        if (authorization == null || authorization.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前没有可补拉的 1688 授权账号。");
        }
        if (!syncService.request(ownerUserId, authorization.getId(), context.getSessionUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前授权账号不能执行 1688 补拉。");
        }
        return ResponseEntity.accepted().build();
    }
}
