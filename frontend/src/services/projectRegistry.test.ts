import { describe, expect, it } from 'vitest';
import { projectFormSchema } from '../utils/masks';

describe('Validação de Criação de Projeto (US-03 — Casos Bons, Ruins e Incompletos)', () => {
  it('1. Caso Bom: aceita payload completo com CPF/CNPJ válido e pagamento após envio do PDF', () => {
    const result = projectFormSchema.safeParse({
      name: 'Empresa Expositora Alfa',
      document: '54.018.395/0001-89',
      email: 'contato@alfa.com.br',
      address: 'Av. Unicamp, 1000',
      area: 'B2C',
      pdfDeadline: '2026-11-10',
      paymentDeadline: '2026-11-20',
    });
    expect(result.success).toBe(true);
  });

  it('2. Caso Ruim: rejeita quando o prazo de pagamento é anterior ao prazo do PDF', () => {
    const result = projectFormSchema.safeParse({
      name: 'Empresa Expositora Alfa',
      document: '54.018.395/0001-89',
      email: 'contato@alfa.com.br',
      address: 'Av. Unicamp, 1000',
      area: 'B2C',
      pdfDeadline: '2026-11-20',
      paymentDeadline: '2026-11-10',
    });
    expect(result.success).toBe(false);
  });

  it('3. Caso Incompleto: rejeita campos vazios, CPF/CNPJ incompleto ou e-mail malformado', () => {
    const result = projectFormSchema.safeParse({
      name: '   ',
      document: '123',
      email: 'email-invalido',
      address: '',
      area: 'B2C',
      pdfDeadline: '',
      paymentDeadline: '',
    });
    expect(result.success).toBe(false);
  });
});
