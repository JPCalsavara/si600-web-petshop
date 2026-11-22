package br.unicamp.ft.si600.eventos.security;

/** US-14: requisitos de segurança da senha escolhida pelo usuário. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 200;

    private PasswordPolicy() {}

    /** @return a mensagem do primeiro requisito violado, ou {@code null} se a senha é aceita. */
    public static String violation(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "A nova senha deve ter pelo menos " + MIN_LENGTH + " caracteres.";
        }
        if (password.length() > MAX_LENGTH) {
            return "Senha muito longa.";
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return "A nova senha deve conter pelo menos uma letra e um número.";
        }
        return null;
    }
}
