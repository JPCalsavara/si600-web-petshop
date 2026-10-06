package br.unicamp.ft.si600.eventos.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Usado quando o Gmail está desligado (desenvolvimento local): não envia nada e não registra conteúdo. */
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Envio de e-mail desativado (notification.gmail.enabled=false); mensagem não enviada.");
    }
}
