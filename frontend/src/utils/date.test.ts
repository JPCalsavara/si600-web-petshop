import { describe, expect, it } from 'vitest';
import { formatDate, formatDateTime } from './date';

describe('Date Utils — Formatação sem deslocamento de fuso', () => {
  describe('1. Casos Bons (Happy Path)', () => {
    it('formatDate formata datas ISO completas e YYYY-MM-DD no padrão dd/mm/aaaa', () => {
      expect(formatDate('2026-10-25T15:30:00Z')).toBe('25/10/2026');
      expect(formatDate('2026-05-08')).toBe('08/05/2026');
    });

    it('formatDateTime formata carimbos de data/hora válidos em pt-BR', () => {
      const formatted = formatDateTime('2026-10-25T15:30:00Z');
      expect(formatted).toContain('2026');
    });
  });

  describe('2. Casos Ruins e Incompletos (Boundary & Malformed)', () => {
    it('retorna traço para valores nulos, indefinidos ou strings malformadas', () => {
      expect(formatDate(null)).toBe('-');
      expect(formatDate(undefined)).toBe('-');
      expect(formatDate('')).toBe('-');
      expect(formatDate('invalido')).toBe('-');
      expect(formatDateTime(null)).toBe('-');
      expect(formatDateTime(undefined)).toBe('-');
      expect(formatDateTime('data-invalida')).toBe('data-invalida');
    });
  });
});
