package br.unicamp.ft.si600.eventos.notification;

/** Porta de saída para envio de e-mails transacionais (texto puro, UTF-8). */
public interface EmailSender {
    /**
     * @throws EmailDeliveryException quando o provedor não aceita a mensagem
     */
    void send(String to, String subject, String body);
}
