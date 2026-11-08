import type { Fee, ProjectFeeStatus } from '../types';

export const FEE_STATUS_LABELS: Record<ProjectFeeStatus, string> = {
  AGUARDANDO_QUANTIDADE: 'Aguardando quantidade',
  AGUARDANDO_COTACAO: 'Aguardando cotação',
  DISPONIVEL: 'Disponível',
  COTADA: 'Cotada',
  PAGAMENTO_GERADO: 'Pagamento gerado',
};

export const FEE_TYPE_LABELS = {
  FIXA: 'Fixa',
  POR_METRAGEM: 'Por metragem',
  VARIAVEL: 'Variável',
} as const;

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** Rótulo em português do status; devolve o valor original se o backend enviar um status desconhecido. */
export function feeStatusLabel(status: string): string {
  return FEE_STATUS_LABELS[status as ProjectFeeStatus] ?? status;
}

/** O botão "Gerar pagamento" só existe com o valor definido: taxa disponível ou já cotada (US-06/US-11). */
export function canGeneratePayment(status: string, value: number | null): boolean {
  return (status === 'DISPONIVEL' || status === 'COTADA') && value !== null;
}

/** Formatação canônica de preço/configuração de uma Taxa do catálogo para todas as modalidades. */
export function formatFeePricing(fee: Fee): string {
  if (fee.type === 'VARIAVEL') {
    return `${fee.measurementUnit ?? 'unidade'} — valor a definir`;
  }
  if (fee.type === 'FIXA') {
    return fee.amount === null ? 'Valor indisponível' : currency.format(fee.amount);
  }
  if (fee.areaPricingMode === 'VALOR_POR_M2') {
    return fee.amountPerM2 === null
      ? 'Valor indisponível'
      : `${currency.format(fee.amountPerM2)} por m²`;
  }
  return `1 unidade a cada ${fee.areaPerUnitM2?.toLocaleString('pt-BR') ?? '—'} m²; ${
    fee.unitAmount === null ? 'valor indisponível' : currency.format(fee.unitAmount)
  } por unidade`;
}

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

export type QuoteParse = { ok: true; value: number } | { ok: false; error: string };

/** Valida o valor em R$ digitado pelo Admin na cotação (US-07): > 0, até 2 casas e 10 dígitos inteiros. */
export function parseQuoteAmount(raw: string): QuoteParse {
  const text = raw.trim().replace(',', '.');
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(text) || Number(text) <= 0) {
    return { ok: false, error: 'Informe um valor em reais maior que zero, com no máximo 2 casas decimais.' };
  }
  return { ok: true, value: Number(text) };
}
