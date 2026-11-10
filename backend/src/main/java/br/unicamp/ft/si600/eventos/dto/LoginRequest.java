package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Informe o e-mail.") @Size(max = 254, message = "E-mail muito longo.") String email,
        @NotBlank(message = "Informe a senha.") @Size(max = 200, message = "Senha muito longa.") String password) {}
