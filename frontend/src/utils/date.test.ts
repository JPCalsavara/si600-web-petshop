import { describe, expect, it } from 'vitest';
import { formatDate, isDueSoon, isOverdue } from './date';

describe('Date Utils', () => {
  it('formatDate deve formatar datas no padrão dd/mm/aaaa', () => {
    expect(formatDate('2026-10-25T15:30:00Z')).toBe('25/10/2026');
    expect(formatDate('2026-05-08')).toBe('08/05/2026');
    expect(formatDate(null)).toBe('-');
    expect(formatDate(undefined)).toBe('-');
  });

  it('isOverdue deve detectar datas expiradas', () => {
    const past = new Date(Date.now() - 1000 * 60 * 60).toISOString();
    const future = new Date(Date.now() + 1000 * 60 * 60 * 24).toISOString();
    expect(isOverdue(past)).toBe(true);
    expect(isOverdue(future)).toBe(false);
    expect(isOverdue(null)).toBe(false);
  });

  it('isDueSoon deve identificar prazos em até 3 dias', () => {
    const twoDaysAhead = new Date(Date.now() + 1000 * 60 * 60 * 24 * 2).toISOString();
    const fiveDaysAhead = new Date(Date.now() + 1000 * 60 * 60 * 24 * 5).toISOString();
    const past = new Date(Date.now() - 1000 * 60).toISOString();

    expect(isDueSoon(twoDaysAhead)).toBe(true);
    expect(isDueSoon(fiveDaysAhead)).toBe(false);
    expect(isDueSoon(past)).toBe(false);
    expect(isDueSoon(null)).toBe(false);
  });
});
