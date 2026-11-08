import { beforeEach, describe, expect, it } from 'vitest';
import {
  ApiRequestError,
  getCurrentActorId,
  getCurrentActorRole,
  getCurrentClientProjectId,
  normalizeProblemDetail,
  parseFee,
} from './api';

const memoryStore = new Map<string, string>();
const localStorageAdapter = {
  getItem: (key: string) => memoryStore.get(key) ?? null,
  setItem: (key: string, value: string) => {
    memoryStore.set(key, value);
  },
  removeItem: (key: string) => {
    memoryStore.delete(key);
  },
  clear: () => {
    memoryStore.clear();
  },
} as Storage;

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageAdapter,
  configurable: true,
  writable: true,
});

describe('Seam HTTP e Sessão do Ator — Contratos de Borda (ADR 0001 / ADR 0002)', () => {
  beforeEach(() => {
    localStorageAdapter.clear();
  });

  describe('1. Casos Bons (Happy Path)', () => {
    it('resolve dinamicamente o papel, o identificador do ator e o projeto a partir do localStorage', () => {
      localStorageAdapter.setItem('MOCK_ROLE', 'admin');
      localStorageAdapter.setItem('MOCK_ACTOR_ID', 'admin-42');
      localStorageAdapter.setItem('MOCK_PROJECT_ID', 'proj-99');

      expect(getCurrentActorRole()).toBe('ADMIN');
      expect(getCurrentActorId()).toBe('admin-42');
      expect(getCurrentClientProjectId()).toBe('proj-99');

      localStorageAdapter.setItem('MOCK_ROLE', 'client');
      expect(getCurrentActorRole()).toBe('CLIENT');
    });

    it('valida uma taxa íntegra retornada pela API via parseFee', () => {
      const validFee = {
        id: 'f-1',
        name: 'Taxa de Limpeza',
        description: 'Cobrança fixa',
        type: 'FIXA',
        active: true,
        areaPricingMode: null,
        amount: 200,
        amountPerM2: null,
        areaPerUnitM2: null,
        unitAmount: null,
        measurementUnit: null,
      };
      expect(parseFee(validFee)).toEqual(validFee);
    });
  });

  describe('2. Casos Ruins (Sad Path — Erros de Domínio RFC 7807)', () => {
    it('normaliza ProblemDetail com detail e invalidFields em ApiRequestError estruturado', () => {
      const normalized = normalizeProblemDetail(409, {
        title: 'Conflito de negócio',
        detail: 'Já existe uma taxa ativa com este nome.',
      });

      const error = new ApiRequestError(normalized.detail, 409, normalized.invalidFields);
      expect(error.status).toBe(409);
      expect(error.message).toBe('Já existe uma taxa ativa com este nome.');
      expect(error.invalidFields).toEqual({});
    });
  });

  describe('3. Casos Incompletos (Boundary & Malformed Payloads)', () => {
    it('extrai matriz de erros por campo (invalidFields e errors) de respostas HTTP 400', () => {
      const normalized = normalizeProblemDetail(400, {
        title: 'Dados inválidos',
        detail: 'Revise os campos informados.',
        invalidFields: {
          paymentDeadline: 'A data é obrigatória.',
        },
      });

      expect(normalized.invalidFields).toEqual({
        paymentDeadline: 'A data é obrigatória.',
      });
      expect(normalized.detail).toContain('A data é obrigatória.');
    });

    it('rejeita payloads de taxa malformados em parseFee sem propagar dados corrompidos', () => {
      expect(() => parseFee(null)).toThrow('Não foi possível carregar os dados da taxa');
      expect(() => parseFee({ id: 123, name: 'Invalida' })).toThrow(
        'Não foi possível carregar os dados da taxa',
      );
    });
  });
});
