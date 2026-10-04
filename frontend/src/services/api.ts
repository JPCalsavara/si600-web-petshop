import type { ApiError, HealthStatus, Project, ProjectStatus } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const actorId = import.meta.env.VITE_USER_ID || 'client-1';
const actorRole = (import.meta.env.VITE_USER_ROLE || 'CLIENT').toUpperCase();

function actorHeaders(): HeadersInit {
  return {
    'X-Authenticated-User-Id': actorId,
    'X-Authenticated-User-Role': actorRole,
  };
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  Object.entries(actorHeaders()).forEach(([key, value]) => headers.set(key, value));

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });

  if (!response.ok) {
    let detail = `Falha na API: HTTP ${response.status}`;
    try {
      const problem = (await response.json()) as ApiError;
      detail = problem.detail || problem.title || detail;
      if (problem.invalidFields) {
        detail += ` ${Object.values(problem.invalidFields).join(' ')}`;
      }
    } catch {
      // Mantém a mensagem HTTP quando o backend não retorna JSON.
    }
    throw new Error(detail);
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
