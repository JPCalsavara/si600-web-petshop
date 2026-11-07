import type { ProjectHistoryEventType } from '../types';

export const HISTORY_EVENT_LABELS: Record<ProjectHistoryEventType, string> = {
  PROJETO_CRIADO: 'Projeto criado',
  PDF_ENVIADO: 'Envio de PDF da planta',
  PDF_APROVADO: 'Aprovação de PDF da planta',
  PDF_REPROVADO: 'Reprovação de PDF da planta',
  TAXAS_CONFIGURADAS: 'Configuração de taxas',
  QUANTIDADE_INFORMADA: 'Quantidade de taxa informada',
  TAXA_COTADA: 'Cotação de taxa registrada',
  PAGAMENTO_GERADO: 'Pagamento gerado',
  PAGAMENTO_CONFIRMADO: 'Baixa de pagamento confirmada',
  PAGAMENTO_CANCELADO: 'Pagamento cancelado',
};

export const ACTOR_ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrador',
  CLIENT: 'Cliente',
};
