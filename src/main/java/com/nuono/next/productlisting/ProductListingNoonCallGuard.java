package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.product.ProductWriteAuthRequiredException;
import com.nuono.next.product.noon.ProductNoonAuthEnvelope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.springframework.util.StringUtils;

final class ProductListingNoonCallGuard {
    private ProductListingNoonCallGuard() {
    }

    static NoonPullGatewaySession loginGuarded(
            ProductListingNoonWriteRequest request,
            NoonPullStoreBinding binding,
            ProductListingWriteAuthRecovery authorization,
            NoonPullGatewaySessionFactory sessionFactory
    ) {
        authorization.requireAvailable(request, binding);
        request.heartbeatOrThrow();
        return leaseGuarded(
                request,
                () -> authorization.requireAvailable(request, binding),
                sessionFactory.login(binding)
        );
    }

    static boolean isAuthorizationRecoveryPending(
            ProductListingNoonWriteRequest request,
            NoonPullStoreBinding binding,
            ProductListingWriteAuthRecovery authorization
    ) {
        try {
            authorization.requireAvailable(request, binding);
            return false;
        } catch (ProductWriteAuthRequiredException ignored) {
            return true;
        }
    }

    static NoonPullGatewaySession leaseGuarded(
            ProductListingNoonWriteRequest request,
            Runnable authorizationCheck,
            NoonPullGatewaySession delegate
    ) {
        if (request == null || delegate == null) {
            return delegate;
        }
        return (NoonPullGatewaySession) Proxy.newProxyInstance(
                NoonPullGatewaySession.class.getClassLoader(),
                new Class<?>[] {NoonPullGatewaySession.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() != Object.class) {
                        request.heartbeatOrThrow();
                        if (authorizationCheck != null) {
                            authorizationCheck.run();
                        }
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
    }

    static JsonNode requireAuthorized(JsonNode response) {
        String evidence = ProductNoonAuthEnvelope.evidence(response);
        if (StringUtils.hasText(evidence)) {
            throw new AuthEnvelopeException(
                    "auth_required: Noon 2xx response contains authorization rejection (" + evidence + ").");
        }
        return response;
    }

    static boolean isAuthEnvelopeFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AuthEnvelopeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean writeMayHaveOccurred(
            boolean priorSuccess,
            boolean providerCallStarted,
            Throwable failure
    ) {
        return priorSuccess || (providerCallStarted
                && !isAuthEnvelopeFailure(failure)
                && !ProductListingNoonWriteRequest.isExecutionLeaseLost(failure));
    }

    private static final class AuthEnvelopeException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private AuthEnvelopeException(String message) {
            super(message);
        }
    }
}
