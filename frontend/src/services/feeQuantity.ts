/** Limite da coluna NUMERIC(12,2) do backend: 10 dígitos inteiros e 2 decimais. */
const MAX_QUANTITY = 9_999_999_999.99;

export type QuantityParse = { ok: true; value: number } | { ok: false; error: string };

/** Valida o texto digitado na quantidade de uma taxa variável (US-06). */
export function parseQuantity(raw: string, unit: string | null): QuantityParse {
  const text = raw.trim().replace(',', '.');
  const label = unit ? ` (${unit})` : '';
  if (text === '' || !/^\d+(\.\d{1,2})?$/.test(text)) {
    return { ok: false, error: `Informe uma quantidade válida${label}, com no máximo 2 casas decimais.` };
  }
  const value = Number(text);
  if (value <= 0) return { ok: false, error: 'A quantidade deve ser maior que zero.' };
  if (value > MAX_QUANTITY) return { ok: false, error: 'A quantidade informada é grande demais.' };
  return { ok: true, value };
}
