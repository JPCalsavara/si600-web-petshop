package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.notification.EmailDeliveryException;
import br.unicamp.ft.si600.eventos.notification.EmailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Substitui o provedor externo nos testes de integração: guarda o que seria enviado. */
public class CapturingEmailSender implements EmailSender {
    public record Sent(String to, String subject, String body) {}

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    public void send(String to, String subject, String body) {
        if (failing) throw new EmailDeliveryException("provedor indisponível");
        sent.add(new Sent(to, subject, body));
    }

    public List<Sent> sent() { return sent; }
    public void failing(boolean failing) { this.failing = failing; }
    public void clear() { sent.clear(); failing = false; }
}
