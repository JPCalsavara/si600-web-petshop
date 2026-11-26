package br.unicamp.ft.si600.eventos.entity;

/** US-14: situação de um pedido de recuperação de acesso ("Esqueci a senha"). */
public enum PasswordResetStatus {
    /** Aguardando o Admin gerar a senha temporária. */
    PENDENTE,
    /** O Admin já gerou a senha temporária e a repassou ao usuário. */
    ATENDIDO
}
