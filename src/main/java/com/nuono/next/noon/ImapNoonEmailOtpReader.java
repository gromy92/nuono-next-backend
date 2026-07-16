package com.nuono.next.noon;

import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import com.sun.mail.imap.IMAPStore;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.BodyPart;
import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
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

    private final NoonAuthRecoveryProperties authRecoveryProperties;

    public ImapNoonEmailOtpReader(NoonAuthRecoveryProperties authRecoveryProperties) {
        this.authRecoveryProperties = Objects.requireNonNull(
                authRecoveryProperties,
                "authRecoveryProperties must not be null"
        );
    }

    @Override
    public String readOtp(String email, String mailAuthCode) {
        MailAccess access = requireMailAccess(email, mailAuthCode);
        Instant requestedAt = Instant.now();
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String code = readLatestOtp(
                        access.provider,
                        access.email,
                        access.mailAuthCode,
                        requestedAt
                );
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

    @Override
    public MailboxCursor snapshot(String email, String mailAuthCode) {
        MailAccess access = requireMailAccess(email, mailAuthCode);
        Store store = null;
        Folder inbox = null;
        try {
            store = createStore(access.provider, access.email, access.mailAuthCode);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            UIDFolder uidFolder = requireUidFolder(inbox);
            long lastUid = 0L;
            int messageCount = inbox.getMessageCount();
            if (messageCount > 0) {
                lastUid = Math.max(0L, uidFolder.getUID(inbox.getMessage(messageCount)));
            }
            return new MailboxCursor(uidFolder.getUIDValidity(), lastUid, Instant.now());
        } catch (Exception exception) {
            throw new IllegalStateException("读取 Noon 邮箱代次游标失败：" + exception.getMessage(), exception);
        } finally {
            closeQuietly(inbox);
            closeQuietly(store);
        }
    }

    @Override
    public Optional<OtpCandidate> pollAfter(
            String email,
            String mailAuthCode,
            MailboxCursor cursor,
            Instant notBefore,
            Set<String> excludedMessageKeyHashes
    ) {
        if (cursor == null) {
            throw new IllegalArgumentException("缺少邮箱代次游标，无法读取 Noon 验证码。");
        }
        MailAccess access = requireMailAccess(email, mailAuthCode);
        Set<String> excluded = excludedMessageKeyHashes == null ? Set.of() : excludedMessageKeyHashes;
        Store store = null;
        Folder inbox = null;
        try {
            store = createStore(access.provider, access.email, access.mailAuthCode);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            UIDFolder uidFolder = requireUidFolder(inbox);
            long uidValidity = uidFolder.getUIDValidity();
            if (uidValidity != cursor.getUidValidity()) {
                throw new IllegalStateException("邮箱 UIDVALIDITY 已变化，不能安全复用当前验证码代次。");
            }
            long firstUid = Math.max(1L, cursor.getLastUid() + 1L);
            Message[] messages = uidFolder.getMessagesByUID(firstUid, UIDFolder.LASTUID);
            if (messages == null || messages.length == 0) {
                return Optional.empty();
            }
            int start = Math.max(0, messages.length - MAX_MESSAGES_TO_SCAN);
            Message[] recentMessages = Arrays.copyOfRange(messages, start, messages.length);
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(FetchProfile.Item.FLAGS);
            profile.add("Return-Path");
            inbox.fetch(recentMessages, profile);
            for (int index = recentMessages.length - 1; index >= 0; index--) {
                Message message = recentMessages[index];
                long uid = uidFolder.getUID(message);
                if (uid <= cursor.getLastUid()
                        || !isNoonVerificationMail(message, notBefore, access.email)) {
                    continue;
                }
                String messageKeyHash = messageKeyHash(uidValidity, uid, message);
                if (excluded.contains(messageKeyHash)) {
                    continue;
                }
                String code = extractOtpCode(extractText(message));
                if (StringUtils.hasText(code)) {
                    return Optional.of(new OtpCandidate(
                            code,
                            messageKeyHash,
                            messageInstant(message),
                            uidValidity,
                            uid
                    ));
                }
            }
            return Optional.empty();
        } catch (Exception exception) {
            throw new IllegalStateException("读取 Noon 邮箱代次验证码失败：" + exception.getMessage(), exception);
        } finally {
            closeQuietly(inbox);
            closeQuietly(store);
        }
    }

    @Override
    public void acknowledge(String email, String mailAuthCode, OtpCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("缺少待确认的 Noon 验证码邮件。");
        }
        MailAccess access = requireMailAccess(email, mailAuthCode);
        Store store = null;
        Folder inbox = null;
        try {
            store = createStore(access.provider, access.email, access.mailAuthCode);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            UIDFolder uidFolder = requireUidFolder(inbox);
            if (uidFolder.getUIDValidity() != candidate.getUidValidity()) {
                throw new IllegalStateException("邮箱 UIDVALIDITY 已变化，拒绝确认错误代次的验证码邮件。");
            }
            Message message = uidFolder.getMessageByUID(candidate.getUid());
            if (message == null
                    || !candidate.getMessageKeyHash().equals(
                    messageKeyHash(candidate.getUidValidity(), candidate.getUid(), message)
            )) {
                throw new IllegalStateException("验证码邮件标识不匹配，拒绝标记已读。");
            }
            message.setFlag(Flags.Flag.SEEN, true);
        } catch (Exception exception) {
            throw new IllegalStateException("确认 Noon 邮箱验证码失败：" + exception.getMessage(), exception);
        } finally {
            closeQuietly(inbox);
            closeQuietly(store);
        }
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
            profile.add("Return-Path");
            inbox.fetch(messages, profile);
            for (int index = messages.length - 1; index >= 0; index--) {
                Message message = messages[index];
                if (message.isSet(Flags.Flag.SEEN)) {
                    continue;
                }
                if (!isNoonVerificationMail(message, requestedAt, email)) {
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

    private MailAccess requireMailAccess(String email, String mailAuthCode) {
        String normalizedEmail = normalize(email);
        String normalizedAuthCode = normalize(mailAuthCode);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("缺少邮箱地址，无法读取 Noon 验证码。");
        }
        if (!StringUtils.hasText(normalizedAuthCode)) {
            throw new IllegalArgumentException("缺少邮箱授权码，无法读取 Noon 验证码。");
        }
        if (authRecoveryProperties.normalizedTrustedSenderDomains().isEmpty()) {
            throw new IllegalStateException("Noon OTP trusted sender domain allowlist is not configured.");
        }
        return new MailAccess(resolveProvider(normalizedEmail), normalizedEmail, normalizedAuthCode);
    }

    private UIDFolder requireUidFolder(Folder inbox) {
        if (!(inbox instanceof UIDFolder)) {
            throw new IllegalStateException("邮箱服务不支持 UID，无法安全关联 Noon 验证码代次。");
        }
        return (UIDFolder) inbox;
    }

    private Instant messageInstant(Message message) throws MessagingException {
        Date receivedDate = message.getReceivedDate();
        if (receivedDate == null) {
            receivedDate = message.getSentDate();
        }
        return receivedDate == null ? null : receivedDate.toInstant();
    }

    private String messageKeyHash(long uidValidity, long uid, Message message) throws Exception {
        String[] messageIds = message.getHeader("Message-ID");
        String messageId = messageIds == null || messageIds.length == 0 ? "" : String.join("|", messageIds);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest((uidValidity + ":" + uid + ":" + messageId).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return builder.toString();
    }

    boolean isNoonVerificationMail(
            Message message,
            Instant requestedAt,
            String configuredMailboxRecipient
    ) throws MessagingException {
        if (!hasExactMailboxRecipient(message, configuredMailboxRecipient)
                || !hasOnlyTrustedAddresses(message.getFrom())
                || !hasTrustedReturnPathWhenPresent(message)) {
            return false;
        }
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

    private boolean hasExactMailboxRecipient(Message message, String configuredMailboxRecipient)
            throws MessagingException {
        String expectedRecipient = normalizeMailboxAddress(configuredMailboxRecipient);
        if (!StringUtils.hasText(expectedRecipient)) {
            return false;
        }
        Address[] recipients = message.getAllRecipients();
        if (recipients == null || recipients.length == 0) {
            return false;
        }
        for (Address recipient : recipients) {
            if (expectedRecipient.equals(normalizeMailboxAddress(recipient))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOnlyTrustedAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return false;
        }
        for (Address address : addresses) {
            if (!isTrustedAddress(address)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasTrustedReturnPathWhenPresent(Message message) throws MessagingException {
        String[] returnPathHeaders = message.getHeader("Return-Path");
        if (returnPathHeaders == null || returnPathHeaders.length == 0) {
            return true;
        }
        boolean foundAddress = false;
        for (String returnPathHeader : returnPathHeaders) {
            final InternetAddress[] addresses;
            try {
                addresses = InternetAddress.parseHeader(returnPathHeader, true);
            } catch (AddressException exception) {
                return false;
            }
            if (addresses.length == 0) {
                return false;
            }
            foundAddress = true;
            for (InternetAddress address : addresses) {
                if (!isTrustedAddress(address)) {
                    return false;
                }
            }
        }
        return foundAddress;
    }

    private boolean isTrustedAddress(Address address) {
        if (!(address instanceof InternetAddress)) {
            return false;
        }
        String mailboxAddress = ((InternetAddress) address).getAddress();
        if (!StringUtils.hasText(mailboxAddress)) {
            return false;
        }
        int separator = mailboxAddress.lastIndexOf('@');
        if (separator <= 0 || separator >= mailboxAddress.length() - 1) {
            return false;
        }
        return authRecoveryProperties.allowsTrustedSenderDomain(mailboxAddress.substring(separator + 1));
    }

    private String normalizeMailboxAddress(Address address) {
        if (!(address instanceof InternetAddress)) {
            return null;
        }
        return normalizeMailboxAddress(((InternetAddress) address).getAddress());
    }

    private String normalizeMailboxAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        try {
            InternetAddress parsed = new InternetAddress(address.trim(), true);
            String mailboxAddress = parsed.getAddress();
            return StringUtils.hasText(mailboxAddress)
                    ? mailboxAddress.trim().toLowerCase(Locale.ROOT)
                    : null;
        } catch (AddressException exception) {
            return null;
        }
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

    private static final class MailAccess {
        private final MailProvider provider;
        private final String email;
        private final String mailAuthCode;

        private MailAccess(MailProvider provider, String email, String mailAuthCode) {
            this.provider = provider;
            this.email = email;
            this.mailAuthCode = mailAuthCode;
        }
    }
}
