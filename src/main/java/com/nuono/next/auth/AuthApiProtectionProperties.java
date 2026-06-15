package com.nuono.next.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.auth.api-protection")
public class AuthApiProtectionProperties {

    private boolean enabled = true;

    private List<String> publicPaths = new ArrayList<>(List.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/plugin/auth/login",
            "/api/plugin/auth/logout",
            "/api/auth/sample-accounts",
            "/api/system/bootstrap"
    ));

    private List<String> publicPrefixes = new ArrayList<>(List.of(
            "/api/mobile/"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths == null ? new ArrayList<>() : new ArrayList<>(publicPaths);
    }

    public List<String> getPublicPrefixes() {
        return publicPrefixes;
    }

    public void setPublicPrefixes(List<String> publicPrefixes) {
        this.publicPrefixes = publicPrefixes == null ? new ArrayList<>() : new ArrayList<>(publicPrefixes);
    }
}
