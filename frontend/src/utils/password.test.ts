import { describe, expect, it } from 'vitest';
import { validateNewPassword } from './password';

describe('validateNewPassword (US-14)', () => {
  it('aceita senha com letra e número e confirmação igual', () => {
    expect(validateNewPassword('nova-senha-456', 'nova-senha-456')).toBeNull();
  });

  it('recusa senha em branco, curta, só letras ou só números', () => {
    expect(validateNewPassword('', '')).toBe('Informe a nova senha.');
    expect(validateNewPassword('abc1', 'abc1')).toBe('A nova senha deve ter pelo menos 8 caracteres.');
    expect(validateNewPassword('somenteletras', 'somenteletras')).toBe(
      'A nova senha deve conter pelo menos uma letra e um número.',
    );
    expect(validateNewPassword('123456789', '123456789')).toBe(
      'A nova senha deve conter pelo menos uma letra e um número.',
    );
  });

  it('recusa senha acima de 200 caracteres', () => {
    expect(validateNewPassword('a1'.repeat(101), 'a1'.repeat(101))).toBe('Senha muito longa.');
  });

  it('recusa confirmação diferente da nova senha', () => {
    expect(validateNewPassword('nova-senha-456', 'nova-senha-457')).toBe(
      'A confirmação não confere com a nova senha.',
    );
  });
});
