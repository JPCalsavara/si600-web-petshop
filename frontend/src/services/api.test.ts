import { describe, it, expect, vi, beforeEach } from 'vitest';
import { checkBackendHealth } from './api';

describe('API Service - checkBackendHealth', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('deve retornar status e mensagem quando backend responder com sucesso', async () => {
    const mockResponse = {
      status: 'UP',
      message: 'Backend SI600 Sistema de Eventos operacional',
      timestamp: '2026-09-30T19:00:00Z',
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });

    const data = await checkBackendHealth();

    expect(data.status).toBe('UP');
    expect(data.message).toContain('operacional');
  });

  it('deve lancar erro quando backend responder com status diferente de 2xx', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
    });

    await expect(checkBackendHealth()).rejects.toThrow('Falha ao conectar ao backend: HTTP 500 Internal Server Error');
  });
});
