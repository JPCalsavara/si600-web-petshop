import type { AlertTag } from '../types';

export const ALERT_TAG_LABELS: Record<AlertTag, string> = {
  ATRASADO: 'Atrasado',
  PRAZO_PROXIMO: 'Prazo próximo',
  PENDENTE: 'Pendente',
  EM_DIA: 'Em dia',
};

export const ALERT_TAG_ORDER: AlertTag[] = [
  'ATRASADO',
  'PRAZO_PROXIMO',
  'PENDENTE',
  'EM_DIA',
];
