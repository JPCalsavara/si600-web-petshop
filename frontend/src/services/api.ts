import axios, { AxiosRequestConfig } from 'axios';
import type { HealthStatus, Project, ProjectStatus, Fee, FeeRequest, ProjectFee } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const localRole = typeof window !== 'undefined' ? localStorage.getItem('MOCK_ROLE') : null;
const localActorId = typeof window !== 'undefined' ? localStorage.getItem('MOCK_ACTOR_ID') : null;

const actorId = localActorId || import.meta.env.VITE_USER_ID || 'client-1';
const actorRole = (localRole || import.meta.env.VITE_USER_ROLE || 'CLIENT').toUpperCase();

const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

apiClient.interceptors.request.use((config) => {
  config.headers.set('X-Authenticated-User-Id', actorId);
  config.headers.set('X-Authenticated-User-Role', actorRole);
  return config;
});

export class ApiRequestError extends Error {
  constructor(message: string, public readonly status: number, public readonly invalidFields: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await apiClient(config);
    if (response.status === 204) return undefined as T;
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      let detail = `Falha na API: HTTP ${error.response.status}`;
      const invalidFields: Record<string, string> = {};
      const problem = error.response.data;
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
      throw new ApiRequestError(detail, error.response.status, invalidFields);
    }
    throw error;
  }
}

export async function checkBackendHealth(): Promise<HealthStatus> {
  try {
    const response = await apiClient.get('/health', { headers: { Accept: 'application/json' } });
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      throw new Error(`Falha ao conectar ao backend: HTTP ${error.response?.status || 'Desconhecido'} ${error.message}`);
    }
    throw error;
  }
}

export function getCurrentActorRole(): 'CLIENT' | 'ADMIN' {
  return actorRole === 'ADMIN' ? 'ADMIN' : 'CLIENT';
}

export async function getClientProject(projectId: string): Promise<Project> {
  return request<Project>({ method: 'GET', url: `/projects/${projectId}` });
}

export async function getProjectFees(projectId: string): Promise<any[]> {
  return request<any[]>({ method: 'GET', url: `/projects/${projectId}/fees` });
}

export async function submitProjectPdf(
  projectId: string,
  areaM2: number,
  file: File,
): Promise<Project> {
  const form = new FormData();
  form.append('areaM2', String(areaM2));
  form.append('file', file);
  return request<Project>({
    method: 'POST',
    url: `/projects/${projectId}/pdf`,
    data: form,
  });
}

export async function getPdfDownloadUrl(projectId: string): Promise<{ url: string; expiresInSeconds: number }> {
  return request<{ url: string; expiresInSeconds: number }>({ method: 'GET', url: `/projects/${projectId}/pdf/download` });
}

export async function listAdminProjects(status?: ProjectStatus): Promise<Project[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return request<Project[]>({ method: 'GET', url: `/projects/admin${query}` });
}

export async function approveProject(projectId: string): Promise<Project> {
  return request<Project>({ method: 'POST', url: `/projects/admin/${projectId}/approve` });
}

export async function rejectProject(projectId: string, justification: string): Promise<Project> {
  return request<Project>({
    method: 'POST',
    url: `/projects/admin/${projectId}/reject`,
    headers: { 'Content-Type': 'application/json' },
    data: { justification },
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
  const result = await request<unknown>({ method: 'GET', url: '/fees' });
  if (!Array.isArray(result)) throw new Error('Não foi possível carregar o catálogo. Tente novamente.');
  return result.map(parseFee);
}

export async function getFee(id: string): Promise<Fee> {
  return parseFee(await request<unknown>({ method: 'GET', url: `/fees/${encodeURIComponent(id)}` }));
}

export async function createFee(body: FeeRequest): Promise<Fee> {
  return parseFee(await request<unknown>({
    method: 'POST', url: '/fees', headers: { 'Content-Type': 'application/json' }, data: body,
  }));
}

export async function updateFee(id: string, body: FeeRequest): Promise<Fee> {
  return parseFee(await request<unknown>({
    method: 'PUT', url: `/fees/${encodeURIComponent(id)}`, headers: { 'Content-Type': 'application/json' }, data: body,
  }));
}

export async function deactivateFee(id: string): Promise<Fee> {
  return parseFee(await request<unknown>({ method: 'POST', url: `/fees/${encodeURIComponent(id)}/deactivate` }));
}

/** US-06: grava a quantidade e leva a taxa para "Aguardando cotação". */
export async function submitFeeQuantity(projectId: string, projectFeeId: string, quantity: number): Promise<ProjectFee> {
  return request<ProjectFee>({
    method: 'PUT',
    url: `/projects/${encodeURIComponent(projectId)}/fees/${encodeURIComponent(projectFeeId)}/quantity`,
    headers: { 'Content-Type': 'application/json' },
    data: { quantity }
  });
}
