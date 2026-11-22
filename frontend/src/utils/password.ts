export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 200;

/**
 * US-14: espelha PasswordPolicy do backend para avisar o usuário antes de enviar.
 * O backend continua sendo a validação que vale. Devolve a mensagem do primeiro problema, ou null.
 */
export function validateNewPassword(newPassword: string, confirmation: string): string | null {
  if (!newPassword) return 'Informe a nova senha.';
  if (newPassword.length < PASSWORD_MIN_LENGTH) {
    return `A nova senha deve ter pelo menos ${PASSWORD_MIN_LENGTH} caracteres.`;
  }
  if (newPassword.length > PASSWORD_MAX_LENGTH) return 'Senha muito longa.';
  if (!/\p{L}/u.test(newPassword) || !/\p{Nd}/u.test(newPassword)) {
    return 'A nova senha deve conter pelo menos uma letra e um número.';
  }
  if (newPassword !== confirmation) return 'A confirmação não confere com a nova senha.';
  return null;
}
