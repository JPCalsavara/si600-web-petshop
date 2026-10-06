package br.unicamp.ft.si600.eventos.config;

import br.unicamp.ft.si600.eventos.notification.AsyncEmailSender;
import br.unicamp.ft.si600.eventos.notification.EmailSender;
import br.unicamp.ft.si600.eventos.notification.GmailEmailSender;
import br.unicamp.ft.si600.eventos.notification.GmailProperties;
import br.unicamp.ft.si600.eventos.notification.LoggingEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GmailProperties.class)
public class EmailConfiguration {

    @Bean("emailSender")
    @ConditionalOnProperty(name = "notification.gmail.enabled", havingValue = "true")
    EmailSender gmailEmailSender(GmailProperties properties, ObjectMapper mapper) {
        // Falha na subida, e não no primeiro e-mail de um cliente, se faltar credencial.
        requireConfigured(properties.getSender(), "GMAIL_SENDER");
        requireConfigured(properties.getClientId(), "GMAIL_CLIENT_ID");
        requireConfigured(properties.getClientSecret(), "GMAIL_CLIENT_SECRET");
        requireConfigured(properties.getRefreshToken(), "GMAIL_REFRESH_TOKEN");
        return new AsyncEmailSender(new GmailEmailSender(properties, mapper));
    }

    @Bean("emailSender")
    @ConditionalOnProperty(name = "notification.gmail.enabled", havingValue = "false", matchIfMissing = true)
    EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }

    private static void requireConfigured(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "notification.gmail.enabled=true exige a variável de ambiente " + variable + ".");
        }
    }
}
