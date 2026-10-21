/**
 * Converte data ISO/OffsetDateTime para dd/mm/aaaa sem passar por Date (evita deslocar dia por fuso).
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '-';
  const datePart = value.slice(0, 10);
  const [y, m, d] = datePart.split('-');
  return y && m && d ? `${d}/${m}/${y}` : '-';
}

/**
 * Retorna true se a data limite informada (string ISO) já expirou em relação ao instante atual.
 */
export function isOverdue(deadlineStr: string | null | undefined): boolean {
  if (!deadlineStr) return false;
  const deadline = new Date(deadlineStr);
  return new Date().getTime() > deadline.getTime();
}

/**
 * Retorna true se a data limite vence em até thresholdDays (padrão: 3 dias corridos).
 */
export function isDueSoon(deadlineStr: string | null | undefined, thresholdDays = 3): boolean {
  if (!deadlineStr) return false;
  const now = new Date();
  const deadline = new Date(deadlineStr);
  const diffTime = deadline.getTime() - now.getTime();
  const diffDays = diffTime / (1000 * 60 * 60 * 24);
  return diffDays >= 0 && diffDays <= thresholdDays;
}
