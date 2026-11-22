package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US-14: troca de senha do usuário autenticado. As regras da nova senha ficam em PasswordPolicy. */
public record ChangePasswordRequest(
        @NotBlank(message = "Informe a senha atual.") @Size(max = 200, message = "Senha muito longa.") String currentPassword,
        @NotBlank(message = "Informe a nova senha.") @Size(max = 200, message = "Senha muito longa.") String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}
