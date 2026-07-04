package com.nuono.next.auth;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "nuono.auth.email-code.smtp", name = "host")
public class SmtpAuthEmailCodeSender implements AuthEmailCodeSender {

    private static final DateTimeFormatter EXPIRES_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSenderImpl mailSender;
    private final AuthEmailCodeProperties properties;

    public SmtpAuthEmailCodeSender(AuthEmailCodeProperties properties) {
        this.properties = properties;
        this.mailSender = createMailSender(properties.getSmtp());
    }

    @Override
    public void sendLoginCode(String email, String code, LocalDateTime expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        String from = resolveFrom();
        if (StringUtils.hasText(from)) {
            message.setFrom(from);
        }
        message.setTo(email);
        message.setSubject(StringUtils.hasText(properties.getSubject()) ? properties.getSubject() : "Nuono 登录验证码");
        message.setText("您的 Nuono 登录验证码是：" + code
                + "\n\n验证码将在 " + EXPIRES_AT_FORMATTER.format(expiresAt) + " 前失效。"
                + "\n如果不是您本人操作，请忽略本邮件。");
        mailSender.send(message);
    }

    private String resolveFrom() {
        AuthEmailCodeProperties.Smtp smtp = properties.getSmtp();
        if (StringUtils.hasText(smtp.getFrom())) {
            return smtp.getFrom().trim();
        }
        return StringUtils.hasText(smtp.getUsername()) ? smtp.getUsername().trim() : null;
    }

    private JavaMailSenderImpl createMailSender(AuthEmailCodeProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
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
