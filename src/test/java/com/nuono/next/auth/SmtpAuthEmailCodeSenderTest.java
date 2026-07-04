package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Properties;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpAuthEmailCodeSenderTest {

    @Test
    void shouldMarkProductionConstructorForSpringInjection() throws NoSuchMethodException {
        Constructor<SmtpAuthEmailCodeSender> constructor =
                SmtpAuthEmailCodeSender.class.getConstructor(AuthEmailCodeProperties.class);

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    void shouldSendLoginCodeAsUtf8MimeMessage() throws Exception {
        AuthEmailCodeProperties properties = new AuthEmailCodeProperties();
        properties.setSubject("Nuono 登录验证码");
        properties.getSmtp().setHost("smtp.example.com");
        properties.getSmtp().setUsername("login@example.com");
        CapturingMailSender mailSender = new CapturingMailSender();
        SmtpAuthEmailCodeSender sender = new SmtpAuthEmailCodeSender(properties, mailSender);

        sender.sendLoginCode("nuoonyeah@163.com", "348294", LocalDateTime.of(2026, 7, 4, 13, 11, 28));

        MimeMessage message = mailSender.sentMessage;
        assertNotNull(message);
        assertEquals("Nuono 登录验证码", message.getSubject());
        assertEquals("login@example.com", message.getFrom()[0].toString());
        assertEquals("nuoonyeah@163.com", message.getAllRecipients()[0].toString());
        assertTrue(message.getContent().toString().contains("您的 Nuono 登录验证码是：348294"));
        assertTrue(message.getContent().toString().contains("验证码将在 2026-07-04 13:11:28 前失效。"));
        assertTrue(message.getContent().toString().contains("如果不是您本人操作，请忽略本邮件。"));

        message.saveChanges();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        assertTrue(raw.toString(StandardCharsets.UTF_8).contains("charset=UTF-8"));
    }

    private static class CapturingMailSender extends JavaMailSenderImpl {

        private final Session session = Session.getInstance(new Properties());

        private MimeMessage sentMessage;

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(session);
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            this.sentMessage = mimeMessage;
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            fail("email code sender must send MIME message with explicit UTF-8 charset");
        }
    }
}
