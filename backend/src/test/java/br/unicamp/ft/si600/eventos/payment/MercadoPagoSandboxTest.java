package br.unicamp.ft.si600.eventos.payment;

import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.BoletoRequest;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.OrderResult;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoProperties;
import br.unicamp.ft.si600.eventos.gateway.RestMercadoPagoGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;

/** Opt-in explícito; não inicializa Spring, banco ou servidor de aplicação. */
class MercadoPagoSandboxTest {
    private final MercadoPagoSandboxSettings settings = MercadoPagoSandboxSettings.load();

    @Test
    @EnabledIf("createEnabled")
    void deveCriarBoletoNaContaDeTeste() throws Exception {
        requireTestAccount();
        ObjectMapper mapper = new ObjectMapper();
        BoletoRequest request;
        try {
            request = mapper.readValue(Path.of(settings.required("MP_SANDBOX_REQUEST_FILE")).toFile(), BoletoRequest.class);
        } catch (Exception ex) {
            // Não propaga erros de parsing que poderiam reproduzir dados cadastrais do arquivo.
            throw new IllegalArgumentException("Arquivo de requisição sandbox ausente ou inválido.");
        }
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            MercadoPagoGateway gateway = new RestMercadoPagoGateway(properties(), mapper, validators.getValidator());
            report(gateway.createBoleto(request));
        }
    }

    @Test
    @EnabledIf("getEnabled")
    void deveConsultarOrderNaContaDeTeste() {
        requireTestAccount();
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            MercadoPagoGateway gateway = new RestMercadoPagoGateway(properties(), new ObjectMapper(), validators.getValidator());
            report(gateway.getOrder(settings.required("MP_SANDBOX_ORDER_ID")));
        }
    }

    private MercadoPagoProperties properties() {
        settings.required("MP_ACCESS_TOKEN");
        MercadoPagoProperties properties = settings.gatewayProperties();
        // O experimento real não aceita redirecionar credenciais para outra URL.
        if (!properties.enabled() || !"https://api.mercadopago.com".equals(properties.baseUrl())) {
            throw new IllegalStateException("Sandbox exige MP_ENABLED=true e a URL oficial Mercado Pago.");
        }
        return properties;
    }

    private void requireTestAccount() {
        if (!"TEST_ACCOUNT".equals(settings.get("MP_SANDBOX_ACCOUNT_CONFIRMED"))) {
            throw new IllegalStateException("Confirme o uso de conta e credenciais de teste conforme o guia sandbox.");
        }
    }

    static boolean createEnabled() { return MercadoPagoSandboxSettings.load().enabledFor("create"); }
    static boolean getEnabled() { return MercadoPagoSandboxSettings.load().enabledFor("get"); }

    private void report(OrderResult result) {
        // Somente IDs e estados; sem ticket URL, linha digitável, documento ou credencial.
        System.out.printf("HTTP do provedor: %d%n", result.providerHttpStatus());
        System.out.printf("Mercado Pago sandbox: order=%s payment=%s status=%s detail=%s paymentStatus=%s%n",
                result.orderId(), result.paymentId(), result.status(), result.statusDetail(), result.paymentStatus());
        System.out.printf("Campos presentes: ticketUrl=%s digitableLine=%s barcodeContent=%s%n",
                result.ticketUrl() != null, result.digitableLine() != null, result.barcodeContent() != null);
        System.out.printf("Vencimento informado pelo provedor: %s%n", result.paymentExpiresAt());
    }
}
