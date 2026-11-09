import axios, { AxiosRequestConfig } from 'axios';
import type {
  HealthStatus,
  Project,
  ProjectStatus,
  Fee,
  FeeRequest,
  ProjectFee,
  CalculatedFee,
  AdminDashboardData,
  ProjectHistoryEvent,
  QuoteableProjectFee,
  ProjectFeeConfiguration,
} from '../types';

export type { QuoteableProjectFee, ProjectFeeConfiguration };

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

function getStorageItem(key: string): string | null {
  try {
    const storage = (globalThis as { localStorage?: Storage }).localStorage;
    return storage ? storage.getItem(key) : null;
  } catch {
    return null;
  }
}

export function getCurrentActorId(): string {
  const localActorId = getStorageItem('MOCK_ACTOR_ID');
  return localActorId || import.meta.env.VITE_USER_ID || 'client-1';
}

export function getCurrentActorRole(): 'CLIENT' | 'ADMIN' {
  const localRole = getStorageItem('MOCK_ROLE');
  const resolved = (localRole || import.meta.env.VITE_USER_ROLE || 'CLIENT').toUpperCase();
  return resolved === 'ADMIN' ? 'ADMIN' : 'CLIENT';
}

export function getCurrentClientProjectId(): string | undefined {
  const localProjectId = getStorageItem('MOCK_PROJECT_ID');
  return localProjectId || import.meta.env.VITE_PROJECT_ID || undefined;
}

const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

apiClient.interceptors.request.use((config) => {
  config.headers.set('X-Authenticated-User-Id', getCurrentActorId());
  config.headers.set('X-Authenticated-User-Role', getCurrentActorRole());
  return config;
});

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly invalidFields: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function normalizeProblemDetail(
  status: number,
  problem: unknown,
): { detail: string; invalidFields: Record<string, string> } {
  let detail = `Falha na API: HTTP ${status}`;
  const invalidFields: Record<string, string> = {};
  if (isRecord(problem)) {
    if (typeof problem.detail === 'string') detail = problem.detail;
    else if (typeof problem.title === 'string') detail = problem.title;
    const rawErrors = isRecord(problem.invalidFields)
      ? problem.invalidFields
      : isRecord(problem.errors)
        ? problem.errors
        : null;
    if (rawErrors) {
      for (const [field, message] of Object.entries(rawErrors)) {
        if (typeof message === 'string') invalidFields[field] = message;
      }
      if (Object.keys(invalidFields).length > 0) {
        detail += ` ${Object.values(invalidFields).join(' ')}`;
      }
    }
  }
  return { detail, invalidFields };
}

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await apiClient(config);
    if (response.status === 204) return undefined as T;
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      const { detail, invalidFields } = normalizeProblemDetail(error.response.status, error.response.data);
      throw new ApiRequestError(detail, error.response.status, invalidFields);
    }
    if (axios.isAxiosError(error)) {
      throw new ApiRequestError(
        'Não foi possível comunicar com a API. Verifique se o backend está disponível e tente novamente.',
        0,
      );
    }
    throw error;
  }
}

export async function checkBackendHealth(): Promise<HealthStatus> {
  return request<HealthStatus>({
    method: 'GET',
    url: '/health',
    headers: { Accept: 'application/json' },
  });
}

export async function createProject(body: {
  name: string;
  document: string;
  address: string;
  area: string;
  email: string;
  pdfDeadline: string;
  paymentDeadline: string;
  representativeName?: string;
  feeIds?: string[];
}): Promise<Project> {
  return request<Project>({
    method: 'POST',
    url: '/projects',
    headers: { 'Content-Type': 'application/json' },
    data: body,
  });
}

export async function getClientProject(projectId: string): Promise<Project> {
  return request<Project>({ method: 'GET', url: `/projects/${projectId}` });
}

export async function getProjectFees(projectId: string): Promise<CalculatedFee[]> {
  return request<CalculatedFee[]>({ method: 'GET', url: `/projects/${encodeURIComponent(projectId)}/fees` });
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

export async function approveProject(projectId: string, approvedAreaM2: number): Promise<Project> {
  return request<Project>({
    method: 'POST',
    url: `/projects/admin/${projectId}/approve`,
    headers: { 'Content-Type': 'application/json' },
    data: { approvedAreaM2 },
  });
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

export function parseFee(value: unknown): Fee {
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

export async function submitFeeQuantity(projectId: string, projectFeeId: string, quantity: number): Promise<ProjectFee> {
  return request<ProjectFee>({
    method: 'PUT',
    url: `/projects/${encodeURIComponent(projectId)}/fees/${encodeURIComponent(projectFeeId)}/quantity`,
    headers: { 'Content-Type': 'application/json' },
    data: { quantity },
  });
}

export async function getQuoteableFees(): Promise<QuoteableProjectFee[]> {
  return request<QuoteableProjectFee[]>({ method: 'GET', url: '/admin/quotes' });
}

export async function quoteProjectFee(projectId: string, projectFeeId: string, amount: number): Promise<QuoteableProjectFee> {
  return request<QuoteableProjectFee>({
    method: 'PUT',
    url: `/projects/${encodeURIComponent(projectId)}/fees/${encodeURIComponent(projectFeeId)}/quote`,
    headers: { 'Content-Type': 'application/json' },
    data: { amount },
  });
}

export async function getProjectFeeConfiguration(projectId: string): Promise<ProjectFeeConfiguration[]> {
  return request<ProjectFeeConfiguration[]>({ method: 'GET', url: `/projects/${encodeURIComponent(projectId)}/fees/configuration` });
}

export async function configureProjectFees(projectId: string, feeIds: string[]): Promise<ProjectFeeConfiguration[]> {
  return request<ProjectFeeConfiguration[]>({
    method: 'PUT',
    url: `/projects/${encodeURIComponent(projectId)}/fees/configuration`,
    headers: { 'Content-Type': 'application/json' },
    data: { feeIds },
  });
}

export async function getProjectHistory(projectId: string): Promise<ProjectHistoryEvent[]> {
  return request<ProjectHistoryEvent[]>({
    method: 'GET',
    url: `/projects/${encodeURIComponent(projectId)}/history`,
  });
}

export async function exportProjectCsv(projectId: string): Promise<Blob> {
  return request<Blob>({
    method: 'GET',
    url: `/projects/${encodeURIComponent(projectId)}/export/csv`,
    responseType: 'blob',
  });
}

export async function downloadProjectCsv(projectId: string): Promise<void> {
  const blobData = await exportProjectCsv(projectId);
  const blob = blobData instanceof Blob
    ? blobData
    : new Blob([blobData], { type: 'text/csv;charset=utf-8;' });
  const objectUrl = window.URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = `projeto-${projectId}.csv`;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  window.URL.revokeObjectURL(objectUrl);
}

export async function getAdminDashboard(): Promise<AdminDashboardData> {
  return request<AdminDashboardData>({ method: 'GET', url: '/projects/admin/dashboard' });
}
