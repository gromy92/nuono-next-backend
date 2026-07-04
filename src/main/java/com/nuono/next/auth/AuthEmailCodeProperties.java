package com.nuono.next.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.auth.email-code")
public class AuthEmailCodeProperties {

    private String allowedEmail;

    private String loginAccountNo;

    private int ttlSeconds = 300;

    private int cooldownSeconds = 60;

    private int maxAttempts = 5;

    private int codeLength = 6;

    private String subject = "Nuono 登录验证码";

    private Smtp smtp = new Smtp();

    public String getAllowedEmail() {
        return allowedEmail;
    }

    public void setAllowedEmail(String allowedEmail) {
        this.allowedEmail = allowedEmail;
    }

    public String getLoginAccountNo() {
        return loginAccountNo;
    }

    public void setLoginAccountNo(String loginAccountNo) {
        this.loginAccountNo = loginAccountNo;
    }

    public int getTtlSeconds() {
        return Math.max(60, ttlSeconds);
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getCooldownSeconds() {
        return Math.max(0, cooldownSeconds);
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getCodeLength() {
        return Math.max(4, Math.min(8, codeLength));
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Smtp getSmtp() {
        return smtp;
    }

    public void setSmtp(Smtp smtp) {
        this.smtp = smtp == null ? new Smtp() : smtp;
    }

    public static class Smtp {

        private String host;

        private int port = 465;

        private String username;

        private String password;

        private String from;

        private boolean sslEnabled = true;

        private boolean starttlsEnabled;

        private int connectionTimeoutMs = 10000;

        private int timeoutMs = 10000;

        private int writeTimeoutMs = 10000;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public boolean isSslEnabled() {
            return sslEnabled;
        }

        public void setSslEnabled(boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
        }

        public boolean isStarttlsEnabled() {
            return starttlsEnabled;
        }

        public void setStarttlsEnabled(boolean starttlsEnabled) {
            this.starttlsEnabled = starttlsEnabled;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getWriteTimeoutMs() {
            return writeTimeoutMs;
        }

        public void setWriteTimeoutMs(int writeTimeoutMs) {
            this.writeTimeoutMs = writeTimeoutMs;
        }
    }
}
