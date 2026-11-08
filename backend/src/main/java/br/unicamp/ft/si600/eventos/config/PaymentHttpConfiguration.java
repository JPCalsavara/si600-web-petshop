package br.unicamp.ft.si600.eventos.config;

import br.unicamp.ft.si600.eventos.payment.http.PaymentAccessPolicy;
import br.unicamp.ft.si600.eventos.payment.http.PaymentBoundaryFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@org.springframework.context.annotation.Import({PaymentAccessPolicy.class, br.unicamp.ft.si600.eventos.payment.http.MercadoPagoWebhookSignature.class})
@Configuration
@EnableConfigurationProperties(PaymentInternalProperties.class)
public class PaymentHttpConfiguration {
    @Bean PaymentBoundaryFilter paymentBoundaryFilter(PaymentInternalProperties internal, ObjectMapper mapper, org.springframework.core.env.Environment environment) {
        return new PaymentBoundaryFilter(internal, mapper, environment);
    }
}
