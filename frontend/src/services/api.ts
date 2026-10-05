import type { HealthStatus, Project, ProjectStatus, Fee, FeeRequest } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const actorId = import.meta.env.VITE_USER_ID || 'client-1';
const actorRole = (import.meta.env.VITE_USER_ROLE || 'CLIENT').toUpperCase();

function actorHeaders(): HeadersInit {
  return {
    'X-Authenticated-User-Id': actorId,
    'X-Authenticated-User-Role': actorRole,
  };
}

export class ApiRequestError extends Error {
  constructor(message: string, public readonly status: number, public readonly invalidFields: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  Object.entries(actorHeaders()).forEach(([key, value]) => headers.set(key, value));

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });

  if (!response.ok) {
    let detail = `Falha na API: HTTP ${response.status}`;
    const invalidFields: Record<string, string> = {};
    try {
      const problem: unknown = await response.json();
      if (isRecord(problem)) {
        if (typeof problem.detail === 'string') detail = problem.detail;
        else if (typeof problem.title === 'string') detail = problem.title;
        if (isRecord(problem.invalidFields)) {
          for (const [field, message] of Object.entries(problem.invalidFields)) {
            if (typeof message === 'string') invalidFields[field] = message;
          }
          detail += ` ${Object.values(invalidFields).join(' ')}`;
        }
      }
    } catch {
      // Mantém a mensagem HTTP quando o backend não retorna JSON.
    }
    throw new ApiRequestError(detail, response.status, invalidFields);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function checkBackendHealth(): Promise<HealthStatus> {
  const response = await fetch(`${API_BASE_URL}/health`, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`Falha ao conectar ao backend: HTTP ${response.status} ${response.statusText}`);
  }
  return response.json();
}

export function getCurrentActorRole(): 'CLIENT' | 'ADMIN' {
  return actorRole === 'ADMIN' ? 'ADMIN' : 'CLIENT';
}

export async function getClientProject(projectId: string): Promise<Project> {
  return request<Project>(`/projects/${projectId}`);
}

export async function submitProjectPdf(
  projectId: string,
  areaM2: number,
  file: File,
): Promise<Project> {
  const form = new FormData();
  form.append('areaM2', String(areaM2));
  form.append('file', file);
  return request<Project>(`/projects/${projectId}/pdf`, {
    method: 'POST',
    body: form,
  });
}

export async function getPdfDownloadUrl(projectId: string): Promise<{ url: string; expiresInSeconds: number }> {
  return request(`/projects/${projectId}/pdf/download`);
}

export async function listAdminProjects(status?: ProjectStatus): Promise<Project[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return request<Project[]>(`/projects/admin${query}`);
}

export async function approveProject(projectId: string): Promise<Project> {
  return request<Project>(`/projects/admin/${projectId}/approve`, { method: 'POST' });
}

export async function rejectProject(projectId: string, justification: string): Promise<Project> {
  return request<Project>(`/projects/admin/${projectId}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ justification }),
  });
}

function isFee(value: unknown): value is Fee {
  if (!isRecord(value)) return false;
  const nullableNumber = (item: unknown) => item === null || (typeof item === 'number' && Number.isFinite(item));
  return typeof value.id === 'string' && typeof value.name === 'string' && typeof value.active === 'boolean'
    && (value.description === null || typeof value.description === 'string')
    && typeof value.type === 'string' && ['FIXA', 'POR_METRAGEM', 'VARIAVEL'].includes(value.type)
    && (value.areaPricingMode === null || (typeof value.areaPricingMode === 'string' && ['VALOR_POR_M2', 'UNIDADES_POR_INTERVALO'].includes(value.areaPricingMode)))
    && nullableNumber(value.amount) && nullableNumber(value.amountPerM2)
    && nullableNumber(value.areaPerUnitM2) && nullableNumber(value.unitAmount)
    && (value.measurementUnit === null || typeof value.measurementUnit === 'string');
}

function parseFee(value: unknown): Fee {
  if (!isFee(value)) throw new Error('Não foi possível carregar os dados da taxa. Tente novamente.');
  return value;
}

export async function listFees(): Promise<Fee[]> {
  const result = await request<unknown>('/fees');
  if (!Array.isArray(result)) throw new Error('Não foi possível carregar o catálogo. Tente novamente.');
  return result.map(parseFee);
}

export async function getFee(id: string): Promise<Fee> {
  return parseFee(await request<unknown>(`/fees/${encodeURIComponent(id)}`));
}

export async function createFee(body: FeeRequest): Promise<Fee> {
  return parseFee(await request<unknown>('/fees', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  }));
}

export async function updateFee(id: string, body: FeeRequest): Promise<Fee> {
  return parseFee(await request<unknown>(`/fees/${encodeURIComponent(id)}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  }));
}

export async function deactivateFee(id: string): Promise<Fee> {
  return parseFee(await request<unknown>(`/fees/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }));
}
