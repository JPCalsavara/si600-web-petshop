import type { ProjectFeeStatus } from '../types';

export const FEE_STATUS_LABELS: Record<ProjectFeeStatus, string> = {
  AGUARDANDO_QUANTIDADE: 'Aguardando quantidade',
  AGUARDANDO_COTACAO: 'Aguardando cotação',
  DISPONIVEL: 'Disponível',
  COTADA: 'Cotada',
  PAGAMENTO_GERADO: 'Pagamento gerado',
};

export const FEE_TYPE_LABELS = { FIXA: 'Fixa', POR_METRAGEM: 'Por metragem', VARIAVEL: 'Variável' } as const;

/** Rótulo em português do status; devolve o valor original se o backend enviar um status desconhecido. */
export function feeStatusLabel(status: string): string {
  return FEE_STATUS_LABELS[status as ProjectFeeStatus] ?? status;
}

/** O botão "Gerar pagamento" só existe com o valor definido: taxa disponível ou já cotada (US-06/US-11). */
export function canGeneratePayment(status: string, value: number | null): boolean {
  return (status === 'DISPONIVEL' || status === 'COTADA') && value !== null;
}
