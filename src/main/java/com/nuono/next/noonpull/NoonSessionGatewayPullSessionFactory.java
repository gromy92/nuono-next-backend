package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonBinaryDownloadSink;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
@Conditional(NoonPullRealProviderCondition.class)
public class NoonSessionGatewayPullSessionFactory implements NoonPullGatewaySessionFactory {
    private final NoonSessionGateway noonSessionGateway;
    private NoonAccountSessionAttentionPort accountSessionAttention;

    public NoonSessionGatewayPullSessionFactory(NoonSessionGateway noonSessionGateway) {
        this.noonSessionGateway = noonSessionGateway;
    }

    @Override
    public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
        requireProjectAvailable(binding);
        NoonSession session = noonSessionGateway.loginWithPersistedCookie(
                binding.getOwnerUserId(),
                binding.getSessionProjectUser(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );
        return new GatewaySessionAdapter(session);
    }

    @Override
    public NoonPullGatewaySession openOneShot(NoonPullStoreBinding binding) {
        requireProjectAvailable(binding);
        NoonSession session = noonSessionGateway.openWithPersistedCookieWithoutProbe(
                binding.getOwnerUserId(),
                binding.getSessionProjectUser(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );
        return new GatewaySessionAdapter(session);
    }

    @Autowired(required = false)
    void setAccountSessionAttention(NoonAccountSessionAttentionPort accountSessionAttention) {
        this.accountSessionAttention = accountSessionAttention;
    }

    private void requireProjectAvailable(NoonPullStoreBinding binding) {
        if (accountSessionAttention != null && accountSessionAttention.blocksProviderCalls()) {
            throw new NoonAuthenticationRequiredException(
                    "Noon shared account authorization is not currently available."
            );
        }
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
        public byte[] postBytesOnce(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return session.postBytesOnce(url, body, withProject, extraHeaders);
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
            return session.getBytes(url, withProject, extraHeaders);
        }

        @Override
        public byte[] getBytesOnce(String url, boolean withProject, Map<String, String> extraHeaders) {
            return session.getBytesOnce(url, withProject, extraHeaders);
        }

        @Override
        public void getBytesOnce(
                String url,
                boolean withProject,
                Map<String, String> extraHeaders,
                NoonBinaryDownloadSink sink
        ) {
            session.getBytesOnce(url, withProject, extraHeaders, sink);
        }
    }
}
