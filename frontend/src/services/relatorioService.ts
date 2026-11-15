import axios from 'axios';
import { getSession, ApiRequestError, normalizeProblemDetail, listAdminProjects } from './api';
import type { Project } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

// Statuses reais do backend (ProjectFeeStatus.java)
export type StatusPagamento =
  | 'DISPONIVEL'
  | 'AGUARDANDO_QUANTIDADE'
  | 'AGUARDANDO_COTACAO'
  | 'COTADA'
  | 'PAGAMENTO_GERADO';

export const STATUS_LABELS: Record<StatusPagamento, string> = {
  DISPONIVEL: 'Disponível',
  AGUARDANDO_QUANTIDADE: 'Aguardando quantidade',
  AGUARDANDO_COTACAO: 'Aguardando cotação',
  COTADA: 'Cotada',
  PAGAMENTO_GERADO: 'Pagamento gerado',
};

export const TODOS_OS_STATUSES: StatusPagamento[] = [
  'DISPONIVEL',
  'AGUARDANDO_QUANTIDADE',
  'AGUARDANDO_COTACAO',
  'COTADA',
  'PAGAMENTO_GERADO',
];

// Status que entram nos totais reais (regra da issue #27: só pagamento gerado ou pago).
// Deve acompanhar STATUS_CONTABILIZADOS em RelatorioService.java.
// TODO(US-12, issue #26): incluir 'PAGO' aqui quando a US-12 criar esse status na taxa.
export const STATUS_CONTABILIZADOS: StatusPagamento[] = ['PAGAMENTO_GERADO'];

export function contaNosTotais(status: StatusPagamento): boolean {
  return STATUS_CONTABILIZADOS.includes(status);
}

export interface RelatorioLinha {
  nomeTaxa: string;
  statusPagamento: StatusPagamento;
  quantidadeTaxas: number;
  totalValorCalculado: number;
}

export interface RelatorioResumo {
  totalGeral: number;
  totalPagamentoGerado: number;
  totalDisponivel: number;
  totalCotada: number;
}

export interface RelatorioTaxasReceita {
  linhas: RelatorioLinha[];
  resumo: RelatorioResumo;
}

export interface FiltroRelatorio {
  projetoId?: string;
  dataInicio?: string;
  dataFim?: string;
  status?: StatusPagamento[];
}

const apiClient = axios.create({ baseURL: API_BASE_URL });

apiClient.interceptors.request.use((config) => {
  const session = getSession();
  if (session) config.headers.set('Authorization', `Bearer ${session.token}`);
  return config;
});

export async function buscarRelatorioTaxas(
  filtro: FiltroRelatorio,
): Promise<RelatorioTaxasReceita> {
  const params = new URLSearchParams();
  if (filtro.projetoId) params.set('projetoId', filtro.projetoId);
  if (filtro.dataInicio) params.set('dataInicio', filtro.dataInicio);
  if (filtro.dataFim) params.set('dataFim', filtro.dataFim);
  if (filtro.status && filtro.status.length > 0) {
    filtro.status.forEach((s) => params.append('status', s));
  }

  const query = params.toString() ? `?${params.toString()}` : '';

  try {
    const response = await apiClient.get<RelatorioTaxasReceita>(
      `/admin/relatorios/taxas-receita${query}`,
    );
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      const { detail, invalidFields } = normalizeProblemDetail(
        error.response.status,
        error.response.data,
      );
      throw new ApiRequestError(detail, error.response.status, invalidFields);
    }
    throw new ApiRequestError(
      'Não foi possível comunicar com a API. Verifique se o backend está disponível e tente novamente.',
      0,
    );
  }
}

export async function buscarProjetosParaFiltro(): Promise<
  Pick<Project, 'id' | 'companyName'>[]
> {
  return listAdminProjects();
}

export function formatarBRL(valor: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(valor);
}
