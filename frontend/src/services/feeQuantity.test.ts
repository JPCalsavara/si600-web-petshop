import { describe, expect, it } from 'vitest';
import { parseQuantity } from './feeQuantity';

describe('US-06 — parseQuantity', () => {
  it.each([['10', 10], ['12.5', 12.5], ['12,5', 12.5], [' 3 ', 3], ['0.01', 0.01], ['9999999999.99', 9_999_999_999.99]])(
    'aceita %s', (raw, expected) => {
      expect(parseQuantity(raw, 'kVA')).toEqual({ ok: true, value: expected });
    });

  it.each(['', '   ', '0', '0.00', '-1', 'abc', '1e3', '1.234', '1,2,3', '10000000000', '--5'])(
    'rejeita %j', (raw) => {
      expect(parseQuantity(raw, 'kVA').ok).toBe(false);
    });

  it('inclui a unidade na mensagem de erro', () => {
    const result = parseQuantity('x', 'kVA');
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toContain('kVA');
  });
});
