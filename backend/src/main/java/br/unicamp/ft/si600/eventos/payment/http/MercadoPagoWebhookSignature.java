package br.unicamp.ft.si600.eventos.payment.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;

/** Manifesto oficial: ID da query, request-id opcional e ts literal; nunca assina o JSON. */
@Component
public final class MercadoPagoWebhookSignature {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MercadoPagoWebhookSignature.class);
    private final boolean enabled;
    private final String secret;
    private final Clock clock;

    public MercadoPagoWebhookSignature(@Value("${payment.webhook.enabled:false}") boolean enabled,
                                       @Value("${payment.webhook.secret:}") String secret, Clock clock) {
        this.enabled = enabled;
        this.secret = secret;
        this.clock = clock;
    }

    public boolean enabled() { return enabled; }
    public boolean configured() { return enabled && !secret.isBlank() && !secret.startsWith("COLOCAR_"); }

    public String verify(HttpServletRequest request) {
        if (!enabled) throw rejected(HttpStatus.NOT_FOUND, "DISABLED", "Webhook desabilitado.");
        if (!configured()) throw rejected(HttpStatus.SERVICE_UNAVAILABLE, secret.isBlank() ? "SECRET_MISSING" : "SECRET_PLACEHOLDER", "Webhook sem configuração de assinatura.");
        String[] ids = request.getParameterValues("data.id");
        if (ids == null || ids.length != 1 || ids[0].isBlank()) {
            throw rejected(HttpStatus.BAD_REQUEST, "PARAMETERS_INVALID", "data.id único é obrigatório na URL.");
        }
        String id = ids[0];
        if (!id.matches("(?i)ORD[A-Z0-9]{1,100}")) {
            throw rejected(HttpStatus.BAD_REQUEST, "PARAMETERS_INVALID",
                    "data.id inválido: informe um ID de ordem Mercado Pago com prefixo ORD seguido de caracteres alfanuméricos.");
        }
        String signature = singleHeader(request, "x-signature");
        String requestId = singleHeader(request, "x-request-id");
        if (signature == null) throw unauthorized("SIGNATURE_MISSING");
        if (signature.length() > 512) throw unauthorized("SIGNATURE_INVALID");
        if (requestId != null && !requestId.matches("[A-Za-z0-9._:-]{1,200}")) throw unauthorized("PARAMETERS_INVALID");
        var parts = new HashMap<String, String>();
        for (String part : signature.split(",", -1)) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || parts.putIfAbsent(pair[0].trim(), pair[1].trim()) != null) throw unauthorized("SIGNATURE_INVALID");
        }
        String ts = parts.get("ts");
        String hash = parts.get("v1");
        if (ts == null || !ts.matches("[0-9]{10}|[0-9]{13}")) throw unauthorized("TIMESTAMP_INVALID");
        if (hash == null || !hash.matches("[a-fA-F0-9]{64}")) throw unauthorized("SIGNATURE_INVALID");
        byte[] received = HexFormat.of().parseHex(hash);
        // Orders/SDK Java preserva o ID; a documentação legada também define ID em minúsculas.
        // Ambos exigem HMAC completo sobre o mesmo ID autenticado. Não há fallback sem assinatura.
        boolean exact = MessageDigest.isEqual(hmac(manifest(id, requestId, ts)), received);
        boolean lowercase = MessageDigest.isEqual(hmac(manifest(id.toLowerCase(Locale.ROOT), requestId, ts)), received);
        if (!(exact | lowercase)) throw unauthorized("HMAC_MISMATCH");
        // Exemplos Orders usam milissegundos; exemplos legados e SDK usam segundos.
        Instant signedAt = ts.length() == 13 ? Instant.ofEpochMilli(Long.parseLong(ts)) : Instant.ofEpochSecond(Long.parseLong(ts));
        Duration age = Duration.between(signedAt, clock.instant());
        // Política local de replay: permite reentregas com a mesma assinatura por 24h.
        if (age.compareTo(Duration.ofHours(24)) > 0 || age.compareTo(Duration.ofMinutes(-5)) < 0) {
            log.info("MP_WEBHOOK timestamp_age_seconds={} within_window=false", age.getSeconds());
            throw unauthorized("TIMESTAMP_OUT_OF_WINDOW");
        }
        return id.toUpperCase(Locale.ROOT);
    }

    private String singleHeader(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() > 1) throw unauthorized("PARAMETERS_INVALID");
        return values.isEmpty() || values.getFirst().isBlank() ? null : values.getFirst().trim();
    }

    private static String manifest(String id, String requestId, String ts) {
        return "id:" + id + ";" + (requestId == null ? "" : "request-id:" + requestId + ";") + "ts:" + ts + ";";
    }

    private byte[] hmac(String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 indisponível.");
        }
    }

    private ResponseStatusException unauthorized(String reason) {
        return rejected(HttpStatus.UNAUTHORIZED, reason, "Assinatura de webhook inválida.");
    }

    private ResponseStatusException rejected(HttpStatus status, String reason, String detail) {
        log.info("MP_WEBHOOK validation=rejected reason={} http={}", reason, status.value());
        return new ResponseStatusException(status, detail);
    }
}
