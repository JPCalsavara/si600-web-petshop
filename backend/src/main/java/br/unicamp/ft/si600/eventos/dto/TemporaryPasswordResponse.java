package br.unicamp.ft.si600.eventos.dto;

/** US-14: a senha temporária só existe nesta resposta; o Admin a repassa ao usuário. */
public record TemporaryPasswordResponse(String email, String temporaryPassword) {
    @Override
    public String toString() {
        return "TemporaryPasswordResponse[email=" + email + ", temporaryPassword=redacted]";
    }
}
