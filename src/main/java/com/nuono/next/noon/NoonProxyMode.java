package com.nuono.next.noon;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.util.StringUtils;

enum NoonProxyMode {
    DIRECT,
    FIXED,
    PROVIDER;

    static Proxy select(
            String configuredMode,
            boolean legacyEnabled,
            String configuredType,
            String host,
            int port,
            String providerUrl,
            Function<Proxy.Type, Proxy> providerLoader
    ) {
        NoonProxyMode mode = resolve(
                configuredMode,
                legacyEnabled,
                StringUtils.hasText(host) && port > 0,
                StringUtils.hasText(providerUrl)
        );
        if (mode == DIRECT) {
            return null;
        }
        Proxy.Type type = "SOCKS".equalsIgnoreCase(configuredType)
                ? Proxy.Type.SOCKS
                : Proxy.Type.HTTP;
        return mode == FIXED
                ? new Proxy(type, new InetSocketAddress(host, port))
                : providerLoader.apply(type);
    }

    static NoonProxyMode resolve(
            String configuredMode,
            boolean legacyEnabled,
            boolean fixedEndpointConfigured,
            boolean providerConfigured
    ) {
        String normalized = StringUtils.hasText(configuredMode)
                ? configuredMode.trim().toUpperCase(Locale.ROOT)
                : "AUTO";
        if ("AUTO".equals(normalized)) {
            if (!legacyEnabled) {
                return DIRECT;
            }
            if (providerConfigured) {
                return PROVIDER;
            }
            if (fixedEndpointConfigured) {
                return FIXED;
            }
            throw new IllegalStateException(
                    "Noon 代理已启用但未配置 provider-url 或 host/port；请检查生产 .env 是否被正确加载。"
            );
        }
        if ("DIRECT".equals(normalized)) {
            return DIRECT;
        }
        if ("FIXED".equals(normalized)) {
            if (!fixedEndpointConfigured) {
                throw new IllegalStateException(
                        "Noon 代理模式 FIXED 缺少有效 host/port；请求已在外部 I/O 前停止。"
                );
            }
            return FIXED;
        }
        if ("PROVIDER".equals(normalized)) {
            if (!providerConfigured) {
                throw new IllegalStateException(
                        "Noon 代理模式 PROVIDER 缺少 provider-url；请求已在外部 I/O 前停止。"
                );
            }
            return PROVIDER;
        }
        throw new IllegalStateException(
                "不支持的 Noon 代理模式；只允许 AUTO、DIRECT、FIXED 或 PROVIDER。"
        );
    }
}
