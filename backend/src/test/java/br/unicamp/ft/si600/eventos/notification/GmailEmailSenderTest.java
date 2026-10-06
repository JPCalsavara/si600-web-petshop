package br.unicamp.ft.si600.eventos.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O Gmail e o endpoint OAuth do Google são dependências externas: aqui são simulados por um
 * servidor HTTP local, de modo que o cliente real (HTTP, OAuth, MIME) é exercitado de ponta a ponta.
 */
class GmailEmailSenderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final AtomicInteger sendCalls = new AtomicInteger();
    private final AtomicInteger sendStatus = new AtomicInteger(200);
    private final AtomicBoolean failNextWith401 = new AtomicBoolean();
    private final List<String> tokenRequests = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> rawMessages = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private GmailEmailSender sender;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenCalls.incrementAndGet();
            tokenRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"access_token\":\"tok" + tokenCalls.get() + "\",\"expires_in\":3600}").getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/gmail/v1/users/me/messages/send", exchange -> {
            sendCalls.incrementAndGet();
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            rawMessages.add(mapper.readTree(exchange.getRequestBody().readAllBytes()).get("raw").asText());
            exchange.sendResponseHeaders(failNextWith401.getAndSet(false) ? 401 : sendStatus.get(), -1);
            exchange.close();
        });
        server.start();

        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        GmailProperties properties = new GmailProperties();
        properties.setSender("organizacao@gmail.com");
        properties.setClientId("client-id");
        properties.setClientSecret("sec&ret");
        properties.setRefreshToken("refresh-token");
        properties.setApiBaseUrl(base);
        properties.setTokenUrl(base + "/token");
        sender = new GmailEmailSender(properties, mapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void deveEnviarMensagemMimeUtf8ComTokenObtidoPorRefreshToken() {
        String subject = "PDF do estande reprovado - Empresa Ação & Cia Ltda. com nome longo para forçar a quebra";
        sender.send("cliente@exemplo.com", subject, "Olá, João.\nAjustar altura máxima — 3 m.");

        assertThat(tokenRequests.get(0))
                .contains("grant_type=refresh_token", "refresh_token=refresh-token", "client_secret=sec%26ret");
        assertThat(authorizations).containsExactly("Bearer tok1");

        String mime = decode(rawMessages.get(0));
        assertThat(mime).contains("From: organizacao@gmail.com\r\n", "To: cliente@exemplo.com\r\n",
                "Content-Type: text/plain; charset=UTF-8");
        assertThat(decodeSubject(mime)).isEqualTo(subject);
        String body = mime.substring(mime.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        assertThat(new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8))
                .contains("Olá, João.", "altura máxima — 3 m.");
    }

    @Test
    void deveReaproveitarTokenEntreEnvios() {
        sender.send("cliente@exemplo.com", "Um", "x");
        sender.send("cliente@exemplo.com", "Dois", "x");

        assertThat(tokenCalls.get()).isEqualTo(1);
        assertThat(sendCalls.get()).isEqualTo(2);
    }

    @Test
    void deveRenovarTokenUmaVezQuandoGmailResponde401() {
        failNextWith401.set(true);

        sender.send("cliente@exemplo.com", "Assunto", "x");

        assertThat(tokenCalls.get()).isEqualTo(2);
        assertThat(sendCalls.get()).isEqualTo(2);
        assertThat(authorizations.get(1)).isEqualTo("Bearer tok2");
    }

    @Test
    void deveFalharQuandoGmailRespondeErro() {
        sendStatus.set(500);

        assertThatThrownBy(() -> sender.send("cliente@exemplo.com", "Assunto", "x"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("500");
    }

    @Test
    void deveFalharQuandoServidorEstaForaDoAr() {
        server.stop(0);

        assertThatThrownBy(() -> sender.send("cliente@exemplo.com", "Assunto", "x"))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    void deveRejeitarDestinatarioInvalidoSemChamarAApi() {
        for (String invalid : new String[]{"a@b.com\r\nBcc: x@y.com", "a@b.com\nBcc: x@y.com", "", null}) {
            assertThatThrownBy(() -> sender.send(invalid, "Assunto", "x"))
                    .isInstanceOf(EmailDeliveryException.class);
        }
        assertThat(sendCalls.get()).isZero();
    }

    private String decode(String raw) {
        return new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
    }

    private String decodeSubject(String mime) {
        String header = mime.substring(mime.indexOf("Subject: ") + 9, mime.indexOf("\r\nMIME-Version"));
        StringBuilder out = new StringBuilder();
        for (String word : header.split("\r\n ")) {
            assertThat(word.length()).isLessThanOrEqualTo(75);
            out.append(new String(Base64.getDecoder().decode(word.substring(10, word.length() - 2)),
                    StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}
