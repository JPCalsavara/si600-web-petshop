export type QuoteParse = { ok: true; value: number } | { ok: false; error: string };

/** Valida o valor em R$ digitado pelo Admin na cotação (US-07): > 0, até 2 casas e 10 dígitos inteiros. */
export function parseQuoteAmount(raw: string): QuoteParse {
  const text = raw.trim().replace(',', '.');
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(text) || Number(text) <= 0) {
    return { ok: false, error: 'Informe um valor em reais maior que zero, com no máximo 2 casas decimais.' };
  }
  return { ok: true, value: Number(text) };
}
