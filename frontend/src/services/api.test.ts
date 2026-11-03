import {
  describe,
  it,
  expect,
  vi,
  beforeEach,
  beforeAll,
  afterAll,
} from 'vitest';
import axios from 'axios';
import type { ApiRequestError } from './api';

let api: typeof import('./api');

const { requestUse, apiClient, mockedAxiosCreate, mockedIsAxiosError } =
  vi.hoisted(() => {
    const requestUse = vi.fn();
    const mockedAxiosCreate = vi.fn();
    const mockedIsAxiosError = vi.fn();

    const apiClient = Object.assign(vi.fn(), {
      get: vi.fn(),
      interceptors: { request: { use: requestUse } },
    });

    mockedAxiosCreate.mockReturnValue(apiClient);

    return {
      requestUse,
      apiClient,
      mockedAxiosCreate,
      mockedIsAxiosError,
    };
  });

vi.mock('axios', () => ({
  default: {
    create: mockedAxiosCreate,
    isAxiosError: mockedIsAxiosError,
  },
}));

function mockAxiosError(status: number, data?: unknown) {
  const error = {
    response: { status, data },
    message: `HTTP ${status}`,
  };

  vi.mocked(axios.isAxiosError).mockReturnValue(true);
  return error;
}

beforeAll(async () => {
  vi.stubEnv('VITE_USER_ROLE', 'CLIENT');
  vi.stubEnv('VITE_USER_ID', 'client-1');

  vi.resetModules();
  api = await import('./api');
});

afterAll(() => {
  vi.unstubAllEnvs();
});

describe('API Service - contratos Frontend/Backend', () => {
	beforeEach(() => {
		apiClient.mockReset();
		apiClient.get.mockReset();
		mockedIsAxiosError.mockReset();
		mockedIsAxiosError.mockReturnValue(false);
	});

  it('cria o cliente HTTP com a base da API e registra o interceptor de identidade', () => {
    expect(mockedAxiosCreate).toHaveBeenCalledWith({ baseURL: '/api' });
    expect(requestUse).toHaveBeenCalledTimes(1);

    const interceptor = requestUse.mock.calls[0][0] as (
      config: { headers: Map<string, string> },
    ) => { headers: Map<string, string> };

    const config = interceptor({
      headers: new Map<string, string>(),
    });

    expect(config.headers.get('X-Authenticated-User-Id')).toBe('client-1');
    expect(config.headers.get('X-Authenticated-User-Role')).toBe('CLIENT');
  });

  it('envia feeIds como parte do contrato de criação do projeto', async () => {
    const project = { id: 'p1', contactEmail: 'cliente@teste.com' };
    apiClient.mockResolvedValueOnce({ status: 201, data: project });

    await api.createProject({
      name: 'Empresa A',
      document: '54.018.395/0001-89',
      address: 'Rua X, 100',
      area: 'B2C',
      email: 'cliente@teste.com',
      pdfDeadline: '2026-11-10',
      paymentDeadline: '2026-11-20',
      representativeName: 'João',
      feeIds: ['t0'],
    });

    expect(apiClient).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'POST',
        url: '/projects',
        data: {
          name: 'Empresa A',
          document: '54.018.395/0001-89',
          address: 'Rua X, 100',
          area: 'B2C',
          email: 'cliente@teste.com',
          pdfDeadline: '2026-11-10',
          paymentDeadline: '2026-11-20',
          representativeName: 'João',
          feeIds: ['t0'],
        },
      }),
    );
  });

  it('envia o arquivo no multipart esperado pelo backend', async () => {
    apiClient.mockResolvedValueOnce({
      status: 200,
      data: { id: 'p1' },
    });

    const file = new File(['%PDF-1.7'], 'planta.pdf', {
      type: 'application/pdf',
    });

    await api.submitProjectPdf('p1', 42.5, file);

    const config = apiClient.mock.calls[0][0];

    expect(config.method).toBe('POST');
    expect(config.url).toBe('/projects/p1/pdf');
    expect(config.data).toBeInstanceOf(FormData);
    expect(config.data.get('file')).toBe(file);
    expect(config.data.get('areaM2')).toBe('42.5');
  });

  it('envia exatamente os campos obrigatórios das decisões administrativas', async () => {
    apiClient
      .mockResolvedValueOnce({ status: 200, data: { id: 'p1' } })
      .mockResolvedValueOnce({ status: 200, data: { id: 'p1' } });

    await api.approveProject('p1', 42.5);
    await api.rejectProject('p1', 'Ajustar cotas.');

    expect(apiClient.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        method: 'POST',
        url: '/projects/admin/p1/approve',
        data: { approvedAreaM2: 42.5 },
      }),
    );

    expect(apiClient.mock.calls[1][0]).toEqual(
      expect.objectContaining({
        method: 'POST',
        url: '/projects/admin/p1/reject',
        data: { justification: 'Ajustar cotas.' },
      }),
    );
  });

  it('monta corretamente a listagem administrativa e a consulta do cliente', async () => {
    apiClient
      .mockResolvedValueOnce({ status: 200, data: [] })
      .mockResolvedValueOnce({ status: 200, data: { id: 'p1' } });

    await api.listAdminProjects('PDF_EM_ANALISE');
    await api.getClientProject('p1');

    expect(apiClient.mock.calls[0][0]).toMatchObject({
      method: 'GET',
      url: '/projects/admin?status=PDF_EM_ANALISE',
    });

    expect(apiClient.mock.calls[1][0]).toMatchObject({
      method: 'GET',
      url: '/projects/p1',
    });
  });

  it('transforma erro de validação da API em erro utilizável pela interface', async () => {
    apiClient.mockRejectedValueOnce(
      mockAxiosError(400, {
        title: 'Dados inválidos',
        detail: 'Revise os campos informados.',
        invalidFields: {
          paymentDeadline: 'A data é obrigatória.',
        },
      }),
    );

    await expect(api.getClientProject('p1')).rejects.toMatchObject({
      name: 'ApiRequestError',
      status: 400,
      message: expect.stringContaining('A data é obrigatória.'),
      invalidFields: {
        paymentDeadline: 'A data é obrigatória.',
      },
    } satisfies Partial<ApiRequestError>);
  });

  it('trata indisponibilidade da API sem deixar exceção Axios exposta ao componente', async () => {
    const error = { message: 'Network Error' };

    mockedIsAxiosError.mockReturnValue(true);
    apiClient.mockRejectedValueOnce(error);

    await expect(api.getClientProject('p1')).rejects.toMatchObject({
      name: 'ApiRequestError',
      status: 0,
      message: expect.stringContaining(
        'Não foi possível comunicar com a API',
      ),
    });
  });

  it('mantém o contrato existente de health check', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: {
        status: 'UP',
        message: 'Backend SI600 Sistema de Eventos operacional',
        timestamp: '2026-09-30T19:00:00Z',
      },
    });

    await expect(api.checkBackendHealth()).resolves.toMatchObject({
      status: 'UP',
    });
  });

  it('usa as rotas de taxas, quantidade e cotação esperadas pelo backend', async () => {
    apiClient.mockResolvedValue({ status: 200, data: [] });

    await api.getProjectFees('p1');
    await api.submitFeeQuantity('p1', 'pf1', 12.5);
    await api.getQuoteableFees();
    await api.quoteProjectFee('p1', 'pf1', 250.5);

    const calls = apiClient.mock.calls.map(([config]) => config);
    expect(calls[0]).toMatchObject({ method: 'GET', url: '/projects/p1/fees' });
    expect(calls[1]).toMatchObject({ method: 'PUT', url: '/projects/p1/fees/pf1/quantity', data: { quantity: 12.5 } });
    expect(calls[2]).toMatchObject({ method: 'GET', url: '/admin/quotes' });
    expect(calls[3]).toMatchObject({ method: 'PUT', url: '/projects/p1/fees/pf1/quote', data: { amount: 250.5 } });
  });

  it('propaga a mensagem e os campos inválidos devolvidos pela API de cotação', async () => {
    mockedIsAxiosError.mockReturnValue(true);
    apiClient.mockRejectedValueOnce({
      response: {
        status: 400,
        data: { title: 'Dados de requisição inválidos', invalidFields: { amount: 'O valor deve ser maior que zero.' } },
      },
      message: 'HTTP 400',
    });

    await expect(api.quoteProjectFee('p1', 'pf1', 0)).rejects.toMatchObject({
      status: 400,
      invalidFields: { amount: 'O valor deve ser maior que zero.' },
      message: expect.stringContaining('O valor deve ser maior que zero.'),
    });
  });

  it('chama a rota de dashboard do admin esperada pelo backend', async () => {
    const mockDashboardData = {
      inactiveExhibitors: [],
      pendingDeadlines: [],
      pendingApprovals: [],
    };
    apiClient.mockResolvedValueOnce({ status: 200, data: mockDashboardData });

    const result = await api.getAdminDashboard();
    expect(result).toEqual(mockDashboardData);
    expect(apiClient).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/projects/admin/dashboard' })
    );
  });
});
