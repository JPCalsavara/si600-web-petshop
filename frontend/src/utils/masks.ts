import { z } from 'zod';
import type { AreaEstande, AreaPricingMode, FeeType } from '../types';

/**
 * Aplica máscara dinâmica de CPF (000.000.000-00) ou CNPJ (00.000.000/0000-00).
 */
export function maskCpfCnpj(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 14);
  if (digits.length <= 11) {
    return digits
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
  }
  return digits
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
}

const positiveNumberField = z
  .string()
  .refine((val) => {
    const trimmed = val.trim();
    if (!trimmed) return false;
    const num = Number(trimmed);
    return Number.isFinite(num) && num > 0;
  }, { message: 'Informe um valor maior que zero.' });

export const feeFormSchema = z
  .object({
    name: z
      .string()
      .transform((v) => v.replace(/^[\s\x00-\x20]+|[\s\x00-\x20]+$/gu, ''))
      .refine((v) => v.length > 0, { message: 'Informe o nome da taxa.' }),
    description: z.string(),
    type: z.enum(['FIXA', 'POR_METRAGEM', 'VARIAVEL']),
    mode: z.enum(['VALOR_POR_M2', 'UNIDADES_POR_INTERVALO']),
    unit: z
      .string()
      .transform((v) => v.replace(/^[\s\x00-\x20]+|[\s\x00-\x20]+$/gu, '')),
    amount: z.string(),
    amountPerM2: z.string(),
    areaPerUnitM2: z.string(),
    unitAmount: z.string(),
  })
  .superRefine((data, ctx) => {
    if (data.type === 'FIXA') {
      if (!positiveNumberField.safeParse(data.amount).success) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['amount'],
          message: 'Informe um valor maior que zero.',
        });
      }
    } else if (data.type === 'VARIAVEL') {
      if (!data.unit) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['measurementUnit'],
          message: 'Informe a unidade de medida.',
        });
      }
    } else if (data.mode === 'VALOR_POR_M2') {
      if (!positiveNumberField.safeParse(data.amountPerM2).success) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['amountPerM2'],
          message: 'Informe um valor maior que zero.',
        });
      }
    } else {
      if (!positiveNumberField.safeParse(data.areaPerUnitM2).success) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['areaPerUnitM2'],
          message: 'Informe um valor maior que zero.',
        });
      }
      if (!positiveNumberField.safeParse(data.unitAmount).success) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['unitAmount'],
          message: 'Informe um valor maior que zero.',
        });
      }
    }
  });

export interface FeeFormInput {
  name: string;
  description: string;
  type: FeeType;
  mode: AreaPricingMode;
  unit: string;
  amount: string;
  amountPerM2: string;
  areaPerUnitM2: string;
  unitAmount: string;
}

export function validateFeeFormWithZod(input: FeeFormInput): Record<string, string> {
  const result = feeFormSchema.safeParse(input);
  if (result.success) return {};
  const errors: Record<string, string> = {};
  for (const issue of result.error.issues) {
    const key = String(issue.path[0] ?? '');
    if (key && !errors[key]) {
      errors[key] = issue.message;
    }
  }
  return errors;
}

export const projectFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Informe o nome ou razão social.'),
    document: z
      .string()
      .refine((v) => {
        const digits = v.replace(/\D/g, '');
        return digits.length === 11 || digits.length === 14;
      }, 'Informe um CPF (11 dígitos) ou CNPJ (14 dígitos) válido.'),
    email: z.string().trim().email('Informe um e-mail válido.'),
    address: z.string().trim().min(1, 'Informe o endereço do expositor.'),
    area: z.custom<AreaEstande>(),
    pdfDeadline: z.string().min(1, 'Informe a data limite de envio do PDF.'),
    paymentDeadline: z.string().min(1, 'Informe a data limite de pagamento.'),
  })
  .refine((data) => !data.pdfDeadline || !data.paymentDeadline || data.paymentDeadline >= data.pdfDeadline, {
    message: 'A data limite de pagamento não pode ser anterior à data de envio do PDF.',
    path: ['paymentDeadline'],
  });
