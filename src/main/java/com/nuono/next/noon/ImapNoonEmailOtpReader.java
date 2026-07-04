package com.nuono.next.noon;

import com.sun.mail.imap.IMAPStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.BodyPart;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ImapNoonEmailOtpReader implements NoonEmailOtpReader {

    private static final Pattern OTP_PATTERN = Pattern.compile(">\\s*(\\d{6})\\s*<|\\b(\\d{6})\\b");
    private static final Duration RECENT_WINDOW = Duration.ofMinutes(3);
    private static final Duration DELIVERY_CLOCK_SKEW = Duration.ofSeconds(30);
    private static final int MAX_MESSAGES_TO_SCAN = 30;
    private static final int MAX_ATTEMPTS = 4;
    private static final long RETRY_DELAY_MILLIS = 5000L;

    @Override
    public String readOtp(String email, String mailAuthCode) {
        String normalizedEmail = normalize(email);
        String normalizedAuthCode = normalize(mailAuthCode);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("缺少邮箱地址，无法读取 Noon 验证码。");
        }
        if (!StringUtils.hasText(normalizedAuthCode)) {
            throw new IllegalArgumentException("缺少邮箱授权码，无法读取 Noon 验证码。");
        }
        MailProvider provider = resolveProvider(normalizedEmail);
        Instant requestedAt = Instant.now();
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String code = readLatestOtp(provider, normalizedEmail, normalizedAuthCode, requestedAt);
                if (StringUtils.hasText(code)) {
                    return code;
                }
            } catch (Exception exception) {
                lastFailure = exception;
            }
            if (attempt < MAX_ATTEMPTS) {
                sleepBeforeRetry();
            }
        }
        if (lastFailure != null) {
            throw new IllegalStateException("读取 Noon 邮箱验证码失败：" + lastFailure.getMessage(), lastFailure);
        }
        throw new IllegalStateException("读取 Noon 邮箱验证码失败：未找到最近的 Verify your email 邮件。");
    }

    private String readLatestOtp(
            MailProvider provider,
            String email,
            String mailAuthCode,
            Instant requestedAt
    ) throws Exception {
        Store store = null;
        Folder inbox = null;
        try {
            store = createStore(provider, email, mailAuthCode);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            int messageCount = inbox.getMessageCount();
            if (messageCount <= 0) {
                return null;
            }
            int start = Math.max(1, messageCount - MAX_MESSAGES_TO_SCAN + 1);
            Message[] messages = inbox.getMessages(start, messageCount);
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(FetchProfile.Item.FLAGS);
            inbox.fetch(messages, profile);
            for (int index = messages.length - 1; index >= 0; index--) {
                Message message = messages[index];
                if (message.isSet(Flags.Flag.SEEN)) {
                    continue;
                }
                if (!isNoonVerificationMail(message, requestedAt)) {
                    continue;
                }
                String code = extractOtpCode(extractText(message));
                if (StringUtils.hasText(code)) {
                    message.setFlag(Flags.Flag.SEEN, true);
                    return code;
                }
            }
            return null;
        } finally {
            closeQuietly(inbox);
            closeQuietly(store);
        }
    }

    private Store createStore(MailProvider provider, String email, String mailAuthCode) throws MessagingException {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        properties.put("mail.imap.host", provider.host);
        properties.put("mail.imap.port", "993");
        properties.put("mail.imap.ssl.enable", "true");
        properties.put("mail.imap.connectiontimeout", "10000");
        properties.put("mail.imap.timeout", "10000");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        store.connect(provider.host, 993, email, mailAuthCode);
        if (provider.requiresImapId && store instanceof IMAPStore) {
            ((IMAPStore) store).id(Map.of(
                    "name", "nuono",
                    "version", "1.0.0",
                    "vendor", "nuono",
                    "support-email", email
            ));
        }
        return store;
    }

    private boolean isNoonVerificationMail(Message message, Instant requestedAt) throws MessagingException {
        String subject = normalize(message.getSubject());
        if (!StringUtils.hasText(subject) || !subject.toLowerCase(Locale.ROOT).contains("verify your email")) {
            return false;
        }
        Date receivedDate = message.getReceivedDate();
        if (receivedDate == null) {
            receivedDate = message.getSentDate();
        }
        if (receivedDate == null) {
            return true;
        }
        Instant recentThreshold = Instant.now().minus(RECENT_WINDOW);
        Instant requestThreshold = requestedAt == null
                ? recentThreshold
                : requestedAt.minus(DELIVERY_CLOCK_SKEW);
        Instant threshold = recentThreshold.isAfter(requestThreshold) ? recentThreshold : requestThreshold;
        return !receivedDate.toInstant().isBefore(threshold);
    }

    private String extractText(Part part) throws Exception {
        if (part == null) {
            return null;
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content == null ? null : content.toString();
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content == null ? null : Jsoup.parse(content.toString()).text() + " " + content;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (!(content instanceof Multipart)) {
                return null;
            }
            Multipart multipart = (Multipart) content;
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart bodyPart = multipart.getBodyPart(index);
                String text = extractText(bodyPart);
                if (StringUtils.hasText(text)) {
                    builder.append(text).append('\n');
                }
            }
            return builder.toString();
        }
        Object content = part.getContent();
        return content == null ? null : content.toString();
    }

    private String extractOtpCode(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Matcher matcher = OTP_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return StringUtils.hasText(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
    }

    private MailProvider resolveProvider(String email) {
        String lowerEmail = email.toLowerCase(Locale.ROOT);
        if (lowerEmail.endsWith("@163.com")) {
            return new MailProvider("imap.163.com", true);
        }
        if (lowerEmail.endsWith("@126.com")) {
            return new MailProvider("imap.126.com", true);
        }
        if (lowerEmail.endsWith("@qq.com") || lowerEmail.endsWith("@foxmail.com")) {
            return new MailProvider("imap.qq.com", false);
        }
        throw new IllegalArgumentException("暂不支持该邮箱服务商，请使用 163、126、QQ 或 foxmail 邮箱。");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Noon 验证码邮件时被中断。", exception);
        }
    }

    private void closeQuietly(Folder folder) {
        if (folder == null || !folder.isOpen()) {
            return;
        }
        try {
            folder.close(false);
        } catch (MessagingException ignored) {
            // Ignore cleanup failures.
        }
    }

    private void closeQuietly(Store store) {
        if (store == null || !store.isConnected()) {
            return;
        }
        try {
            store.close();
        } catch (MessagingException ignored) {
            // Ignore cleanup failures.
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class MailProvider {
        private final String host;

        private final boolean requiresImapId;

        private MailProvider(String host, boolean requiresImapId) {
            this.host = host;
            this.requiresImapId = requiresImapId;
        }
    }
}
