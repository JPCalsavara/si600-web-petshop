export interface HealthStatus {
  status: string;
  message: string;
  timestamp: string;
}

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

export async function checkBackendHealth(): Promise<HealthStatus> {
  const response = await fetch(`${API_BASE_URL}/health`, {
    headers: {
      'Accept': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Falha ao conectar ao backend: HTTP ${response.status} ${response.statusText}`);
  }

  return response.json();
}
