export interface HealthStatus {
  status: string;
  message: string;
  timestamp: string;
}

export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  timestamp?: string;
  invalidFields?: Record<string, string>;
}

export type ProjectStatus =
  | 'AGUARDANDO_PDF'
  | 'PDF_EM_ANALISE'
  | 'REPROVADO'
  | 'APROVADO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'CONCLUIDO';

export interface Project {
  id: string;
  companyName: string;
  document: string;
  representativeName: string;
  category: string;
  pdfDeadline: string;
  status: ProjectStatus;
  boothAreaM2: number | null;
  pdfOriginalFilename: string | null;
  pdfSizeBytes: number | null;
  pdfUploadedAt: string | null;
  decisionBy: string | null;
  decisionAt: string | null;
  rejectionJustification: string | null;
}

// ---- US-03: criação de projeto do cliente ----
export type AreaEstande = 'B2C' | 'Music Hub' | 'Music Sport' | 'Internacional';

export interface Fee {
  id: string;
  name: string;
  amount: number;
  /** Taxa fixa do catálogo (ex.: aluguel do estande). */
  fixed: boolean;
}

export interface ClientProject {
  id: string;
  name: string;
  /** CPF/CNPJ exatamente como digitado. */
  document: string;
  address: string;
  area: AreaEstande;
  /** E-mail do cliente, usado como login. */
  email: string;
  /** Datas no formato yyyy-mm-dd. */
  pdfDeadline: string;
  paymentDeadline: string;
  feeIds: string[];
  status: ProjectStatus;
}

export type ClientProjectInput = Omit<ClientProject, 'id' | 'status'>;
export type ClientProjectUpdate = Pick<ClientProject, 'pdfDeadline' | 'paymentDeadline' | 'feeIds'>;
