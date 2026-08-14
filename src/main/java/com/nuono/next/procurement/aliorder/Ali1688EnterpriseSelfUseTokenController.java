package com.nuono.next.procurement.aliorder;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/procurement/ali1688-orders/authorizations/open-api")
public class Ali1688EnterpriseSelfUseTokenController {

    private final Ali1688EnterpriseSelfUseTokenService service;
    private final BusinessAccessResolver accessResolver;

    public Ali1688EnterpriseSelfUseTokenController(
            Ali1688EnterpriseSelfUseTokenService service,
            BusinessAccessResolver accessResolver
    ) {
        this.service = service;
        this.accessResolver = accessResolver;
    }

    @PostMapping("/enterprise-self-use-token")
    public Ali1688HistoricalOrderAuthorizationView.CompleteView save(
            @RequestBody Ali1688HistoricalOrderAuthorizationView.EnterpriseSelfUseTokenRequest body,
            HttpServletRequest request
    ) {
        return service.save(requireBoss(request), body);
    }

    private BusinessAccessContext requireBoss(HttpServletRequest request) {
        BusinessAccessContext context = accessResolver.requireBusinessContext(
                request, BusinessCapability.ALI1688_HISTORICAL_ORDERS
        );
        if (!context.isBossAccount()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有老板可以变更 1688 历史订单授权。");
        }
        return context;
    }
}
