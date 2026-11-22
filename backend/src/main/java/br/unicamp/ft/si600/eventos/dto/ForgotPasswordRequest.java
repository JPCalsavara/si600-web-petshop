package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US-14: pedido de recuperação de acesso feito na tela de login (sem autenticação). */
public record ForgotPasswordRequest(
        @NotBlank(message = "Informe o e-mail.") @Size(max = 254, message = "E-mail muito longo.") String email) {}
