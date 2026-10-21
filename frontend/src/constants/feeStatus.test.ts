import { describe, expect, it } from 'vitest';
import { canGeneratePayment, feeStatusLabel } from './feeStatus';
import { feesVisible } from './projectStatus';

describe('status das taxas (US-05/US-06/US-07)', () => {
  it('só oferece "Gerar pagamento" com valor definido em taxa disponível ou cotada', () => {
    expect(canGeneratePayment('DISPONIVEL', 500)).toBe(true);
    expect(canGeneratePayment('COTADA', 250)).toBe(true);
    expect(canGeneratePayment('AGUARDANDO_COTACAO', null)).toBe(false);
    expect(canGeneratePayment('AGUARDANDO_QUANTIDADE', null)).toBe(false);
    expect(canGeneratePayment('PAGAMENTO_GERADO', 250)).toBe(false);
    expect(canGeneratePayment('DISPONIVEL', null)).toBe(false);
  });

  it('traduz os status e preserva valores desconhecidos', () => {
    expect(feeStatusLabel('AGUARDANDO_COTACAO')).toBe('Aguardando cotação');
    expect(feeStatusLabel('COTADA')).toBe('Cotada');
    expect(feeStatusLabel('NOVO_STATUS')).toBe('NOVO_STATUS');
  });

  it('libera as taxas após a aprovação do PDF, inclusive nas etapas seguintes', () => {
    expect(feesVisible('APROVADO')).toBe(true);
    expect(feesVisible('AGUARDANDO_PAGAMENTO')).toBe(true);
    expect(feesVisible('CONCLUIDO')).toBe(true);
    expect(feesVisible('AGUARDANDO_PDF')).toBe(false);
    expect(feesVisible('PDF_EM_ANALISE')).toBe(false);
    expect(feesVisible('REPROVADO')).toBe(false);
  });
});
