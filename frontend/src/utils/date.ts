/**
 * Converte data ISO/OffsetDateTime para dd/mm/aaaa sem passar por Date (evita deslocar dia por fuso UTC-3).
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '-';
  const datePart = value.slice(0, 10);
  const [y, m, d] = datePart.split('-');
  return y && m && d && y.length === 4 && m.length === 2 && d.length === 2
    ? `${d}/${m}/${y}`
    : '-';
}

/**
 * Converte timestamp ISO/OffsetDateTime para data e hora pt-BR.
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString('pt-BR');
}
