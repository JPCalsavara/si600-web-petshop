import type { ProjectStatus } from '../types';

export const STATUS_LABELS: Record<ProjectStatus, string> = {
  AGUARDANDO_PDF: 'Aguardando PDF',
  PDF_EM_ANALISE: 'PDF em análise',
  REPROVADO: 'Reprovado',
  APROVADO: 'Aprovado',
  AGUARDANDO_PAGAMENTO: 'Aguardando pagamento',
  CONCLUIDO: 'Concluído',
};

export const STATUS_ORDER = Object.keys(STATUS_LABELS) as ProjectStatus[];

/** US-05: as taxas só aparecem depois da aprovação do PDF (inclui as etapas seguintes do projeto). */
export function feesVisible(status: ProjectStatus): boolean {
  return status === 'APROVADO' || status === 'AGUARDANDO_PAGAMENTO' || status === 'CONCLUIDO';
}
