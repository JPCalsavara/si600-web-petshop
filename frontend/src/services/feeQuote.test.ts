import { describe, expect, it } from 'vitest';
import { parseQuoteAmount } from './feeQuote';

describe('US-07 — valor da cotação', () => {
  it('aceita valores positivos com até 2 casas (vírgula ou ponto)', () => {
    expect(parseQuoteAmount('250.5')).toEqual({ ok: true, value: 250.5 });
    expect(parseQuoteAmount(' 1234,56 ')).toEqual({ ok: true, value: 1234.56 });
  });

  it('rejeita vazio, zero, negativo, 3 casas, texto e valores grandes demais', () => {
    for (const raw of ['', '0', '0.00', '-3', '10.999', 'abc', '12345678901']) {
      expect(parseQuoteAmount(raw).ok).toBe(false);
    }
  });
});
