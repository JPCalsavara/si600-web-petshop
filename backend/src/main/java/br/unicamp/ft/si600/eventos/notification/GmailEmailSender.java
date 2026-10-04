package br.unicamp.ft.si600.eventos.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Envia e-mails pela API REST do Gmail (users.messages.send), autenticando com OAuth2 via
 * refresh token. Usa apenas o HttpClient do JDK, sem SDK do Google.
 */
public class GmailEmailSender implements EmailSender {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ENCODED_WORD_BYTES = 42; // mantém cada encoded-word abaixo de 75 caracteres
    private static final String CRLF = "\r\n";

    private final GmailProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Clock clock;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public GmailEmailSender(GmailProperties properties, ObjectMapper mapper) {
        this(properties, mapper, Clock.systemUTC());
    }

    GmailEmailSender(GmailProperties properties, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public void send(String to, String subject, String body) {
        requireSingleLine(to, "destinatário");
        String raw = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(buildMessage(to, subject, body).getBytes(StandardCharsets.UTF_8));

        int status = post(raw, accessToken(false));
        if (status == 401) {
            // Token revogado ou expirado antes do previsto: renova uma única vez.
            status = post(raw, accessToken(true));
        }
        if (status / 100 != 2) {
            throw new EmailDeliveryException("A API do Gmail respondeu HTTP " + status + ".");
        }
    }

    String buildMessage(String to, String subject, String body) {
        requireSingleLine(properties.getSender(), "remetente");
        return "From: " + properties.getSender() + CRLF
                + "To: " + to + CRLF
                + "Subject: " + encodeHeader(subject) + CRLF
                + "MIME-Version: 1.0" + CRLF
                + "Content-Type: text/plain; charset=UTF-8" + CRLF
                + "Content-Transfer-Encoding: base64" + CRLF
                + CRLF
                + Base64.getMimeEncoder(76, CRLF.getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(body.getBytes(StandardCharsets.UTF_8))
                + CRLF;
    }

    /** RFC 2047: quebra o assunto em encoded-words sem partir caracteres multibyte. */
    private String encodeHeader(String subject) {
        String clean = subject.replaceAll("[\\r\\n]+", " ").trim();
        StringBuilder out = new StringBuilder();
        StringBuilder chunk = new StringBuilder();
        int chunkBytes = 0;
        for (int i = 0; i < clean.length(); ) {
            int cp = clean.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int bytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (chunkBytes + bytes > MAX_ENCODED_WORD_BYTES) {
                appendWord(out, chunk);
                chunk.setLength(0);
                chunkBytes = 0;
            }
            chunk.append(ch);
            chunkBytes += bytes;
            i += Character.charCount(cp);
        }
        appendWord(out, chunk);
        return out.toString();
    }

    private void appendWord(StringBuilder out, CharSequence chunk) {
        if (chunk.length() == 0) return;
        if (out.length() > 0) out.append(CRLF).append(' ');
        out.append("=?UTF-8?B?")
                .append(Base64.getEncoder().encodeToString(chunk.toString().getBytes(StandardCharsets.UTF_8)))
                .append("?=");
    }

    private int post(String raw, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(properties.getApiBaseUrl() + "/gmail/v1/users/me/messages/send"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("raw", raw))))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException ex) {
            throw new EmailDeliveryException("Não foi possível contatar a API do Gmail.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Envio de e-mail interrompido.", ex);
        }
    }

    private synchronized String accessToken(boolean forceRefresh) {
        if (!forceRefresh && accessToken != null && clock.instant().isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }
        String form = "client_id=" + enc(properties.getClientId())
                + "&client_secret=" + enc(properties.getClientSecret())
                + "&refresh_token=" + enc(properties.getRefreshToken())
                + "&grant_type=refresh_token";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getTokenUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new EmailDeliveryException(
                        "Falha ao obter token OAuth do Google (HTTP " + response.statusCode() + ").");
            }
            JsonNode json = mapper.readTree(response.body());
            String token = json.path("access_token").asText("");
            if (token.isBlank()) {
                throw new EmailDeliveryException("O Google não retornou access_token.");
            }
            long expiresIn = json.path("expires_in").asLong(3600);
            accessToken = token;
            accessTokenExpiresAt = clock.instant().plusSeconds(Math.max(expiresIn - 60, 0));
            return token;
        } catch (IOException ex) {
            throw new EmailDeliveryException("Não foi possível obter o token OAuth do Google.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Envio de e-mail interrompido.", ex);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static void requireSingleLine(String value, String field) {
        if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n")) {
            throw new EmailDeliveryException("Valor inválido para " + field + ".");
        }
    }
}
