package br.unicamp.ft.si600.eventos.entity;

/** Ciclo de vida de uma taxa dentro de um projeto. */
public enum ProjectFeeStatus {
    /** Taxa variável ainda sem quantidade informada pelo Cliente. */
    AGUARDANDO_QUANTIDADE,
    /** Quantidade enviada (US-06); aguarda o Admin informar o valor (US-07). */
    AGUARDANDO_COTACAO,
    /** Taxa fixa ou por metragem: o valor é calculado pelo catálogo e o pagamento já pode ser gerado. */
    DISPONIVEL,
    /** Valor informado pelo Admin (US-07). */
    COTADA,
    /** Pagamento já gerado (US-11): a quantidade não pode mais ser alterada. */
    PAGAMENTO_GERADO
}
