import { describe, expect, it } from 'vitest';
import {
  canGeneratePayment,
  feeStatusLabel,
  formatFeePricing,
  parseQuantity,
  parseQuoteAmount,
} from './feeStatus';
import { feesVisible } from './projectStatus';
import type { Fee } from '../types';

describe('Módulo de Domínio de Taxas (US-02/US-05/US-06/US-07)', () => {
  describe('1. Casos Bons (Happy Path)', () => {
    it('só oferece "Gerar pagamento" com valor definido em taxa disponível ou cotada', () => {
      expect(canGeneratePayment('DISPONIVEL', 500)).toBe(true);
      expect(canGeneratePayment('COTADA', 250)).toBe(true);
    });

    it('traduz os status conhecidos e libera taxas após aprovação do PDF', () => {
      expect(feeStatusLabel('AGUARDANDO_COTACAO')).toBe('Aguardando cotação');
      expect(feeStatusLabel('COTADA')).toBe('Cotada');
      expect(feesVisible('APROVADO')).toBe(true);
      expect(feesVisible('AGUARDANDO_PAGAMENTO')).toBe(true);
      expect(feesVisible('CONCLUIDO')).toBe(true);
    });

    it('formata o preço de taxas FIXA, VALOR_POR_M2, UNIDADES_POR_INTERVALO e VARIAVEL', () => {
      const fixa: Fee = {
        id: '1',
        name: 'Inscrição',
        description: null,
        type: 'FIXA',
        active: true,
        areaPricingMode: null,
        amount: 350,
        amountPerM2: null,
        areaPerUnitM2: null,
        unitAmount: null,
        measurementUnit: null,
      };
      const porM2: Fee = {
        ...fixa,
        type: 'POR_METRAGEM',
        areaPricingMode: 'VALOR_POR_M2',
        amount: null,
        amountPerM2: 45.5,
      };
      const porIntervalo: Fee = {
        ...fixa,
        type: 'POR_METRAGEM',
        areaPricingMode: 'UNIDADES_POR_INTERVALO',
        amount: null,
        areaPerUnitM2: 15,
        unitAmount: 120,
      };
      const variavel: Fee = {
        ...fixa,
        type: 'VARIAVEL',
        amount: null,
        measurementUnit: 'kVA',
      };

      expect(formatFeePricing(fixa)).toContain('350,00');
      expect(formatFeePricing(porM2)).toContain('por m²');
      expect(formatFeePricing(porIntervalo)).toContain('1 unidade a cada 15 m²');
      expect(formatFeePricing(porIntervalo)).toContain('120,00');
      expect(formatFeePricing(variavel)).toBe('kVA — valor a definir');
    });

    it('valida quantidade e cotação válidas com ponto ou vírgula', () => {
      expect(parseQuantity('12,5', 'kVA')).toEqual({ ok: true, value: 12.5 });
      expect(parseQuoteAmount('1500,75')).toEqual({ ok: true, value: 1500.75 });
    });
  });

  describe('2. Casos Ruins (Sad Path)', () => {
    it('bloqueia geração de pagamento e visualização quando status não permite', () => {
      expect(canGeneratePayment('AGUARDANDO_COTACAO', null)).toBe(false);
      expect(canGeneratePayment('AGUARDANDO_QUANTIDADE', null)).toBe(false);
      expect(canGeneratePayment('PAGAMENTO_GERADO', 250)).toBe(false);
      expect(canGeneratePayment('DISPONIVEL', null)).toBe(false);
      expect(feesVisible('AGUARDANDO_PDF')).toBe(false);
      expect(feesVisible('PDF_EM_ANALISE')).toBe(false);
      expect(feesVisible('REPROVADO')).toBe(false);
    });

    it('rejeita quantidade e cotação iguais a zero ou acima do limite do banco', () => {
      expect(parseQuantity('0', 'kVA')).toEqual({
        ok: false,
        error: 'A quantidade deve ser maior que zero.',
      });
      expect(parseQuantity('10000000000', 'kVA')).toEqual({
        ok: false,
        error: 'A quantidade informada é grande demais.',
      });
      expect(parseQuoteAmount('0')).toEqual({
        ok: false,
        error: 'Informe um valor em reais maior que zero, com no máximo 2 casas decimais.',
      });
    });
  });

  describe('3. Casos Incompletos (Boundary & Malformed)', () => {
    it('preserva status desconhecido e rejeita entradas vazias ou com mais de 2 casas decimais', () => {
      expect(feeStatusLabel('NOVO_STATUS')).toBe('NOVO_STATUS');
      expect(parseQuantity('', 'kVA').ok).toBe(false);
      expect(parseQuantity('10.123', 'kVA').ok).toBe(false);
      expect(parseQuoteAmount('').ok).toBe(false);
      expect(parseQuoteAmount('abc').ok).toBe(false);
    });
  });
});
