package com.nuono.next.product.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class ProductNoonAdapter {
    private final NoonSessionGateway noonSessionGateway;
    private final NoonProductGateway noonProductGateway;

    public ProductNoonAdapter(
            NoonSessionGateway noonSessionGateway,
            NoonProductGateway noonProductGateway
    ) {
        this.noonSessionGateway = noonSessionGateway;
        this.noonProductGateway = noonProductGateway;
    }

    public NoonSession loginWithPersistedCookie(
            Long ownerUserId,
            String noonUser,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        try {
            return noonSessionGateway.loginWithPersistedCookie(
                    ownerUserId,
                    noonUser,
                    persistedCookie,
                    projectCode,
                    storeCode
            );
        } catch (RuntimeException exception) {
            throw noonProductGateway.toException(exception);
        }
    }

    public NoonSessionGateway.RequestCountScope openRequestCountScope() {
        return noonSessionGateway.openRequestCountScope();
    }

    public JsonNode getJson(NoonSession session, String url, boolean withProject) {
        try {
            return session.getJson(url, withProject);
        } catch (RuntimeException exception) {
            throw noonProductGateway.toException(exception);
        }
    }

    public JsonNode getJson(NoonSession session, String url, boolean withProject, Map<String, String> headers) {
        try {
            return session.getJson(url, withProject, headers);
        } catch (RuntimeException exception) {
            throw noonProductGateway.toException(exception);
        }
    }

    public JsonNode postJson(NoonSession session, String url, JsonNode body, boolean withProject) {
        try {
            return session.postJson(url, body, withProject);
        } catch (RuntimeException exception) {
            throw noonProductGateway.toException(exception);
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
        try {
            return session.postWriteJson(url, body, withProject, headers);
        } catch (RuntimeException exception) {
            throw noonProductGateway.toException(exception);
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
        try {
            return session.postMultipartFile(url, fieldName, fileName, contentType, content, withProject, headers);
        } catch (RuntimeException exception) {
            throw noonProductGateway.toException(exception);
        }
    }

    public String userMessage(Throwable throwable) {
        return noonProductGateway.classify(throwable).getMessage();
    }
}
