package com.nuono.next.product.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.ProductWriteAuthRecovery;
import com.nuono.next.product.ProductWriteAuthRequiredException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class ProductNoonAdapter {
    private final NoonSessionGateway noonSessionGateway;
    private final NoonProductGateway noonProductGateway;
    private ProductWriteAuthRecovery productWriteAuthRecovery = ProductWriteAuthRecovery.disabled();

    public ProductNoonAdapter(
            NoonSessionGateway noonSessionGateway,
            NoonProductGateway noonProductGateway
    ) {
        this.noonSessionGateway = noonSessionGateway;
        this.noonProductGateway = noonProductGateway;
    }

    @Autowired(required = false)
    public void setProductWriteAuthRecovery(ProductWriteAuthRecovery productWriteAuthRecovery) {
        if (productWriteAuthRecovery != null) {
            this.productWriteAuthRecovery = productWriteAuthRecovery;
        }
    }

    public NoonSession loginWithPersistedCookie(
            Long ownerUserId,
            String noonUser,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        productWriteAuthRecovery.requireAvailable(ownerUserId, projectCode, storeCode);
        try {
            return noonSessionGateway.loginWithPersistedCookie(
                    ownerUserId,
                    noonUser,
                    persistedCookie,
                    projectCode,
                    storeCode
            );
        } catch (RuntimeException exception) {
            throw classifyOrSuspend(ownerUserId, projectCode, storeCode, exception);
        }
    }

    public NoonSession loginWithPersistedCookiePinnedEgress(
            Long ownerUserId,
            String noonUser,
            String persistedCookie,
            String projectCode,
            String storeCode,
            String targetHost,
            int targetPort
    ) {
        productWriteAuthRecovery.requireAvailable(ownerUserId, projectCode, storeCode);
        try {
            return noonSessionGateway.loginWithPersistedCookiePinnedEgress(
                    ownerUserId,
                    noonUser,
                    persistedCookie,
                    projectCode,
                    storeCode,
                    targetHost,
                    targetPort
            );
        } catch (RuntimeException exception) {
            throw classifyOrSuspend(ownerUserId, projectCode, storeCode, exception);
        }
    }

    public NoonSessionGateway.RequestCountScope openRequestCountScope() {
        return noonSessionGateway.openRequestCountScope();
    }

    public JsonNode getJson(NoonSession session, String url, boolean withProject) {
        requireAvailable(session);
        try {
            return requireNoAuthResponse(session, session.getJson(url, withProject));
        } catch (RuntimeException exception) {
            throw classifyOrSuspend(session, exception);
        }
    }

    public JsonNode getJson(NoonSession session, String url, boolean withProject, Map<String, String> headers) {
        requireAvailable(session);
        try {
            return requireNoAuthResponse(session, session.getJson(url, withProject, headers));
        } catch (RuntimeException exception) {
            throw classifyOrSuspend(session, exception);
        }
    }

    public JsonNode postJson(NoonSession session, String url, JsonNode body, boolean withProject) {
        requireAvailable(session);
        try {
            return requireNoAuthResponse(session, session.postJson(url, body, withProject));
        } catch (RuntimeException exception) {
            throw classifyOrSuspend(session, exception);
        }
    }

    public JsonNode postWriteJson(NoonSession session, String url, JsonNode body, boolean withProject) {
        return postWriteJson(session, url, body, withProject, null);
    }

    public JsonNode postWriteJson(
            NoonSession session,
            String url,
            JsonNode body,
            boolean withProject,
            Map<String, String> headers
    ) {
        requireAvailable(session);
        try {
            return requireNoAuthResponse(
                    session,
                    session.postWriteJson(url, body, withProject, headers)
            );
        } catch (RuntimeException exception) {
            throw classifyOrSuspend(session, exception);
        }
    }

    public JsonNode postMultipartFile(
            NoonSession session,
            String url,
            String fieldName,
            String fileName,
            String contentType,
            byte[] content,
            boolean withProject,
            Map<String, String> headers
    ) {
        requireAvailable(session);
        try {
            return requireNoAuthResponse(
                    session,
                    session.postMultipartFile(
                            url,
                            fieldName,
                            fileName,
                            contentType,
                            content,
                            withProject,
                            headers
                    )
            );
        } catch (RuntimeException exception) {
            throw classifyOrSuspend(session, exception);
        }
    }

    public String userMessage(Throwable throwable) {
        return noonProductGateway.classify(throwable).getMessage();
    }

    private void requireAvailable(NoonSession session) {
        if (session != null) {
            try {
                productWriteAuthRecovery.requireAvailable(
                        session.getOwnerUserId(),
                        session.getProjectCode(),
                        session.getStoreCode()
                );
            } catch (RuntimeException exception) {
                throw classifyOrSuspend(session, exception);
            }
        }
    }

    private JsonNode requireNoAuthResponse(NoonSession session, JsonNode response) {
        if (session == null) {
            return response;
        }
        return requireNoAuthResponse(
                session.getOwnerUserId(),
                session.getProjectCode(),
                session.getStoreCode(),
                response
        );
    }

    JsonNode requireNoAuthResponse(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            JsonNode response
    ) {
        if (response == null || response.isNull() || response.isMissingNode()) {
            return response;
        }
        String failureEnvelope = ProductNoonAuthEnvelope.evidence(response);
        if (failureEnvelope == null || failureEnvelope.isEmpty()) {
            return response;
        }
        IllegalStateException responseFailure = new IllegalStateException(
                "auth_required: Noon authorization failure response: " + failureEnvelope
        );
        ProductWriteAuthRequiredException authRequired =
                productWriteAuthRecovery.suspendIfAuthFailure(
                        ownerUserId,
                        projectCode,
                        storeCode,
                        responseFailure,
                        false
                );
        if (authRequired != null) {
            throw authRequired;
        }
        return response;
    }

    private RuntimeException classifyOrSuspend(
            NoonSession session,
            RuntimeException exception
    ) {
        if (session == null) {
            return noonProductGateway.toException(exception);
        }
        return classifyOrSuspend(
                session.getOwnerUserId(),
                session.getProjectCode(),
                session.getStoreCode(),
                exception
        );
    }

    private RuntimeException classifyOrSuspend(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            RuntimeException exception
    ) {
        RuntimeException classified = exception instanceof NoonProductException
                ? exception
                : noonProductGateway.toException(exception);
        ProductWriteAuthRequiredException authRequired =
                productWriteAuthRecovery.suspendIfAuthFailure(
                        ownerUserId,
                        projectCode,
                        storeCode,
                        classified,
                        false
                );
        return authRequired == null ? classified : authRequired;
    }
}
