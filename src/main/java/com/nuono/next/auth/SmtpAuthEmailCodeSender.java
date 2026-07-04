package com.nuono.next.auth;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "nuono.auth.email-code.smtp", name = "host")
public class SmtpAuthEmailCodeSender implements AuthEmailCodeSender {

    private static final DateTimeFormatter EXPIRES_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSenderImpl mailSender;
    private final AuthEmailCodeProperties properties;

    public SmtpAuthEmailCodeSender(AuthEmailCodeProperties properties) {
        this(properties, createMailSender(properties.getSmtp()));
    }

    SmtpAuthEmailCodeSender(AuthEmailCodeProperties properties, JavaMailSenderImpl mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    @Override
    public void sendLoginCode(String email, String code, LocalDateTime expiresAt) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String from = resolveFrom();
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(email);
            helper.setSubject(StringUtils.hasText(properties.getSubject()) ? properties.getSubject() : "Nuono 登录验证码");
            helper.setText("您的 Nuono 登录验证码是：" + code
                    + "\n\n验证码将在 " + EXPIRES_AT_FORMATTER.format(expiresAt) + " 前失效。"
                    + "\n如果不是您本人操作，请忽略本邮件。", false);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("无法创建邮箱验证码邮件。", exception);
        }
    }

    private String resolveFrom() {
        AuthEmailCodeProperties.Smtp smtp = properties.getSmtp();
        if (StringUtils.hasText(smtp.getFrom())) {
            return smtp.getFrom().trim();
        }
        return StringUtils.hasText(smtp.getUsername()) ? smtp.getUsername().trim() : null;
    }

    private static JavaMailSenderImpl createMailSender(AuthEmailCodeProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPort());
        if (StringUtils.hasText(smtp.getUsername())) {
            sender.setUsername(smtp.getUsername().trim());
        }
        if (StringUtils.hasText(smtp.getPassword())) {
            sender.setPassword(smtp.getPassword());
        }
        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.transport.protocol", "smtp");
        javaMailProperties.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(smtp.getUsername())));
        javaMailProperties.put("mail.smtp.ssl.enable", String.valueOf(smtp.isSslEnabled()));
        javaMailProperties.put("mail.smtp.starttls.enable", String.valueOf(smtp.isStarttlsEnabled()));
        javaMailProperties.put("mail.smtp.connectiontimeout", String.valueOf(smtp.getConnectionTimeoutMs()));
        javaMailProperties.put("mail.smtp.timeout", String.valueOf(smtp.getTimeoutMs()));
        javaMailProperties.put("mail.smtp.writetimeout", String.valueOf(smtp.getWriteTimeoutMs()));
        return sender;
    }
}
