package com.nuono.next.productselection;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-selection")
public class Ali1688PriceProbeController {

    private final ObjectProvider<Ali1688PriceProbeService> priceProbeServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public Ali1688PriceProbeController(
            ObjectProvider<Ali1688PriceProbeService> priceProbeServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.priceProbeServiceProvider = priceProbeServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/ali1688-candidates/{candidateId}/price-probe")
    public Ali1688RealPriceSnapshot requestPriceProbe(
            @PathVariable String candidateId,
            @RequestBody(required = false) Ali1688PriceProbeCommand command,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        try {
            return service().requestProbe(candidateId, command == null ? new Ali1688PriceProbeCommand() : command, session.getUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private Ali1688PriceProbeService service() {
        Ali1688PriceProbeService service = priceProbeServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "1688 真实价格探针服务未启用。");
        }
        return service;
    }
}
