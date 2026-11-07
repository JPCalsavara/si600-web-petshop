package br.unicamp.ft.si600.eventos.entity;

/** US-10: qual pendência/prazo originou a notificação (e, por consequência, quem a recebe). */
public enum DeadlineType {
    /** Cliente ainda precisa enviar (ou reenviar) o PDF antes do prazo de envio. */
    PDF_CLIENTE,
    /** PDF enviado e aguardando análise do Admin; usa o mesmo prazo de envio do PDF. */
    APROVACAO_ADMIN,
    /** Cliente ainda tem taxas sem pagamento quitado antes do prazo de pagamento. */
    PAGAMENTO_CLIENTE
}
