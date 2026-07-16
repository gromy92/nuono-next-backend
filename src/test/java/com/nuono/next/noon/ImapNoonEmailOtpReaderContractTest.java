package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

class ImapNoonEmailOtpReaderContractTest {

    @Test
    void readerShouldNotReuseOldSeenOtpMessages() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/noon/ImapNoonEmailOtpReader.java"
        ));

        assertTrue(source.contains("profile.add(FetchProfile.Item.FLAGS)"));
        assertTrue(source.contains("message.isSet(Flags.Flag.SEEN)"));
        assertTrue(source.contains("requestedAt.minus(DELIVERY_CLOCK_SKEW)"));
        assertTrue(source.contains("!receivedDate.toInstant().isBefore(threshold)"));
        assertTrue(source.contains("message.setFlag(Flags.Flag.SEEN, true)"));
    }

    @Test
    void generationAwarePollingDoesNotAcknowledgeBeforeProviderValidation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/noon/ImapNoonEmailOtpReader.java"
        ));

        int pollStart = source.indexOf("public Optional<OtpCandidate> pollAfter(");
        int acknowledgeStart = source.indexOf("public void acknowledge(");
        String pollingImplementation = source.substring(pollStart, acknowledgeStart);
        String acknowledgeImplementation = source.substring(acknowledgeStart);

        assertTrue(pollStart >= 0);
        assertTrue(acknowledgeStart > pollStart);
        assertTrue(pollingImplementation.contains("cursor.getLastUid() + 1L"));
        assertTrue(pollingImplementation.contains("excluded.contains(messageKeyHash)"));
        assertFalse(pollingImplementation.contains("setFlag(Flags.Flag.SEEN, true)"));
        assertTrue(acknowledgeImplementation.contains("setFlag(Flags.Flag.SEEN, true)"));
    }

    @Test
    void emptyTrustedSenderAllowlistFailsBeforeOpeningMailboxSnapshot() {
        ImapNoonEmailOtpReader reader = reader("");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> reader.snapshot("merchant@163.com", "mailbox-secret")
        );

        assertTrue(failure.getMessage().contains("allowlist is not configured"));
    }

    @Test
    void lookalikeSenderDomainIsRejected() throws Exception {
        ImapNoonEmailOtpReader reader = reader("trusted-noon.com");
        MimeMessage message = verificationMessage(
                "security@trusted-noon.com.evil.test",
                "merchant@163.com",
                "bounce@trusted-noon.com.evil.test"
        );

        assertFalse(reader.isNoonVerificationMail(
                message,
                Instant.now().minusSeconds(1),
                "merchant@163.com"
        ));
    }

    @Test
    void wrongMailboxRecipientIsRejected() throws Exception {
        ImapNoonEmailOtpReader reader = reader("trusted-noon.com");
        MimeMessage message = verificationMessage(
                "security@trusted-noon.com",
                "different@163.com",
                "bounce@trusted-noon.com"
        );

        assertFalse(reader.isNoonVerificationMail(
                message,
                Instant.now().minusSeconds(1),
                "merchant@163.com"
        ));
    }

    @Test
    void exactAndSubdomainTrustedSendersAreAccepted() throws Exception {
        ImapNoonEmailOtpReader reader = reader("trusted-noon.com");

        MimeMessage exact = verificationMessage(
                "security@trusted-noon.com",
                "merchant@163.com",
                "bounce@trusted-noon.com"
        );
        MimeMessage subdomain = verificationMessage(
                "security@mail.trusted-noon.com",
                "merchant@163.com",
                "bounce@return.trusted-noon.com"
        );

        assertTrue(reader.isNoonVerificationMail(
                exact,
                Instant.now().minusSeconds(1),
                "merchant@163.com"
        ));
        assertTrue(reader.isNoonVerificationMail(
                subdomain,
                Instant.now().minusSeconds(1),
                "merchant@163.com"
        ));
    }

    @Test
    void untrustedReturnPathRejectsOtherwiseTrustedFrom() throws Exception {
        ImapNoonEmailOtpReader reader = reader("trusted-noon.com");
        MimeMessage message = verificationMessage(
                "security@trusted-noon.com",
                "merchant@163.com",
                "bounce@evil.test"
        );

        assertFalse(reader.isNoonVerificationMail(
                message,
                Instant.now().minusSeconds(1),
                "merchant@163.com"
        ));
    }

    private ImapNoonEmailOtpReader reader(String trustedSenderDomains) {
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setTrustedSenderDomains(trustedSenderDomains);
        return new ImapNoonEmailOtpReader(properties);
    }

    private MimeMessage verificationMessage(String from, String recipient, String returnPath) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(from));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
        message.setSubject("Verify your email");
        message.setSentDate(Date.from(Instant.now()));
        message.setText("Your verification code is 123456");
        if (returnPath != null) {
            message.setHeader("Return-Path", "<" + returnPath + ">");
        }
        message.saveChanges();
        return message;
    }
}
