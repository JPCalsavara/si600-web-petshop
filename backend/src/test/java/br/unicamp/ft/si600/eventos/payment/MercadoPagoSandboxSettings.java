package br.unicamp.ft.si600.eventos.payment;

import br.unicamp.ft.si600.eventos.gateway.MercadoPagoProperties;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

/** Mesmo Config Data do backend, sem subir Spring, banco ou servidor de aplicação. */
final class MercadoPagoSandboxSettings {
    private final StandardEnvironment environment;

    private MercadoPagoSandboxSettings(StandardEnvironment environment) {
        this.environment = environment;
    }

    static MercadoPagoSandboxSettings load() {
        return load("classpath:mercadopago.yml");
    }

    static MercadoPagoSandboxSettings load(String location) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("sandboxConfigLocation",
                Map.of("spring.config.location", location)));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return new MercadoPagoSandboxSettings(environment);
    }

    String get(String name) { return environment.getProperty(name); }

    String required(String name) {
        String value = get(name);
        if (value == null || value.isBlank() || value.startsWith("COLOCAR_")) {
            throw new IllegalArgumentException("Variável obrigatória não configurada: " + name);
        }
        return value;
    }

    boolean enabledFor(String action) {
        return "true".equalsIgnoreCase(get("MP_SANDBOX_ENABLED")) && action.equals(get("MP_SANDBOX_ACTION"));
    }

    MercadoPagoProperties gatewayProperties() {
        return Binder.get(environment).bind("mercadopago", Bindable.of(MercadoPagoProperties.class))
                .orElseThrow(() -> new IllegalStateException("Configuração Mercado Pago ausente."));
    }
}
