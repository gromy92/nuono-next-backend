package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
@ConditionalOnProperty(prefix = "nuono.noon.pull.real-provider", name = "enabled", havingValue = "true")
public class NoonSessionGatewayPullSessionFactory implements NoonPullGatewaySessionFactory {
    private final NoonSessionGateway noonSessionGateway;
    private NoonPullProjectAuthGate projectAuthGate = (ownerUserId, projectCode) -> false;

    public NoonSessionGatewayPullSessionFactory(NoonSessionGateway noonSessionGateway) {
        this.noonSessionGateway = noonSessionGateway;
    }

    @Override
    public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
        if (projectAuthGate.isBlocked(binding.getOwnerUserId(), binding.getProjectCode())) {
            throw new NoonInterfacePullException(
                    "auth_required: Noon Project authorization recovery is pending; project="
                            + binding.getProjectCode()
            );
        }
        NoonSession session = noonSessionGateway.loginWithPersistedCookie(
                binding.getOwnerUserId(),
                binding.getNoonUser(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );
        return new GatewaySessionAdapter(session);
    }

    @Autowired(required = false)
    void setProjectAuthGate(NoonPullProjectAuthGate projectAuthGate) {
        this.projectAuthGate = projectAuthGate == null
                ? (ownerUserId, projectCode) -> false
                : projectAuthGate;
    }

    private static class GatewaySessionAdapter implements NoonPullGatewaySession {
        private final NoonSession session;

        private GatewaySessionAdapter(NoonSession session) {
            this.session = session;
        }

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return session.postJson(url, body, withProject, extraHeaders);
        }

        @Override
        public byte[] postBytes(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return session.postBytes(url, body, withProject, extraHeaders);
        }

        @Override
        public JsonNode postWriteJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return session.postWriteJson(url, body, withProject, extraHeaders);
        }

        @Override
        public JsonNode postMultipartFile(
                String url,
                String fieldName,
                String fileName,
                String contentType,
                byte[] content,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            return session.postMultipartFile(url, fieldName, fileName, contentType, content, withProject, extraHeaders);
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            return session.getText(url, withProject, extraHeaders).getBytes(StandardCharsets.UTF_8);
        }
    }
}
