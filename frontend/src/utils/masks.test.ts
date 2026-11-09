import { describe, expect, it } from 'vitest';
import { maskCpfCnpj, projectFormSchema, validateFeeFormWithZod } from './masks';

describe('masks e schemas Zod', () => {
  it('aplica máscara de CPF para até 11 dígitos e CNPJ para 12-14 dígitos', () => {
    expect(maskCpfCnpj('12345678901')).toBe('123.456.789-01');
    expect(maskCpfCnpj('12345678000199')).toBe('12.345.678/0001-99');
    expect(maskCpfCnpj('12.345.678/0001-99')).toBe('12.345.678/0001-99');
  });

  it('valida formulário de taxa com Zod mantendo mensagens esperadas', () => {
    const errors = validateFeeFormWithZod({
      name: '   ',
      description: '',
      type: 'FIXA',
      mode: 'VALOR_POR_M2',
      unit: '',
      amount: '-5',
      amountPerM2: '',
      areaPerUnitM2: '',
      unitAmount: '',
    });
    expect(errors.name).toBe('Informe o nome da taxa.');
    expect(errors.amount).toBe('Informe um valor maior que zero.');
  });

  it('valida formulário de projeto com Zod', () => {
    const valid = projectFormSchema.safeParse({
      name: 'Empresa Teste',
      document: '12.345.678/0001-99',
      email: 'contato@empresa.com',
      address: 'Av. Brasil, 100',
      area: 'B2C',
      pdfDeadline: '2050-12-01',
      paymentDeadline: '2050-12-31',
    });
    expect(valid.success).toBe(true);
  });
});
