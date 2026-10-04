package br.unicamp.ft.si600.eventos.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Despacha o envio em outra thread para que a lentidão ou a queda do provedor
 * nunca atrase nem derrube a resposta da API. Falhas são apenas registradas.
 */
public class AsyncEmailSender implements EmailSender, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AsyncEmailSender.class);

    private final EmailSender delegate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "email-sender");
        thread.setDaemon(true);
        return thread;
    });

    public AsyncEmailSender(EmailSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public void send(String to, String subject, String body) {
        executor.execute(() -> {
            try {
                delegate.send(to, subject, body);
            } catch (RuntimeException ex) {
                log.error("Falha ao enviar e-mail: {}", ex.getMessage());
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
