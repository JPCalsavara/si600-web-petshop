import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import { checkBackendHealth } from './api';

vi.mock('axios', () => {
  const mAxiosInstance = {
    get: vi.fn(),
    interceptors: {
      request: { use: vi.fn() }
    }
  };
  return {
    default: {
      create: vi.fn(() => mAxiosInstance),
      isAxiosError: vi.fn(),
    }
  };
});

// Helper to access the mocked instance
const mockedAxiosCreate = vi.mocked(axios.create);
// Get the mocked instance returned by create()
const mAxiosInstance = mockedAxiosCreate() as any;

describe('API Service - checkBackendHealth', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('deve retornar status e mensagem quando backend responder com sucesso', async () => {
    const mockResponse = {
      status: 'UP',
      message: 'Backend SI600 Sistema de Eventos operacional',
      timestamp: '2026-09-30T19:00:00Z',
    };

    mAxiosInstance.get.mockResolvedValueOnce({ data: mockResponse });

    const data = await checkBackendHealth();

    expect(data.status).toBe('UP');
    expect(data.message).toContain('operacional');
  });

  it('deve lancar erro quando backend responder com status diferente de 2xx', async () => {
    const mockAxiosError = {
      response: {
        status: 500,
        statusText: 'Internal Server Error'
      },
      message: 'Network Error'
    };
    
    mAxiosInstance.get.mockRejectedValueOnce(mockAxiosError);
    vi.mocked(axios.isAxiosError).mockReturnValueOnce(true);

    await expect(checkBackendHealth()).rejects.toThrow('Falha ao conectar ao backend: HTTP 500 Network Error');
  });
});
