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
  address: string;
  contactEmail: string;
  paymentDeadline: string | null;
  alertTag?: AlertTag;
}

export type AlertTag = 'ATRASADO' | 'PRAZO_PROXIMO' | 'PENDENTE' | 'EM_DIA';

export interface InactiveExhibitor {
  projectId: string;
  companyName: string;
  representativeName: string;
  contactEmail: string;
  createdAt: string;
  lastAccessAt: string | null;
  daysSinceLastAccess: number | null;
  neverAccessed: boolean;
}

export interface PendingApproval {
  projectId: string;
  companyName: string;
  pdfOriginalFilename: string | null;
  pdfUploadedAt: string | null;
  boothAreaM2: number | null;
  status: ProjectStatus;
}

export interface AdminDashboardData {
  inactiveExhibitors: InactiveExhibitor[];
  pendingDeadlines: Project[];
  pendingApprovals: PendingApproval[];
}

// ---- US-03: criação de projeto do cliente ----
export type AreaEstande = 'B2C' | 'Music Hub' | 'Music Sport' | 'Internacional';

export interface ProjectFeeOption {
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

// US-02: catálogo persistente de taxas.
export type FeeType = 'FIXA' | 'POR_METRAGEM' | 'VARIAVEL';
export type AreaPricingMode = 'VALOR_POR_M2' | 'UNIDADES_POR_INTERVALO';

export interface Fee {
  id: string;
  name: string;
  description: string | null;
  type: FeeType;
  active: boolean;
  areaPricingMode: AreaPricingMode | null;
  amount: number | null;
  amountPerM2: number | null;
  areaPerUnitM2: number | null;
  unitAmount: number | null;
  measurementUnit: string | null;
}

export type FeeRequest = { name: string; description: string } & (
  | { type: 'FIXA'; amount: number }
  | { type: 'POR_METRAGEM'; areaPricingMode: 'VALOR_POR_M2'; amountPerM2: number }
  | { type: 'POR_METRAGEM'; areaPricingMode: 'UNIDADES_POR_INTERVALO'; areaPerUnitM2: number; unitAmount: number }
  | { type: 'VARIAVEL'; measurementUnit: string }
);

// US-06: quantidade das taxas variáveis informada pelo Cliente.
export type ProjectFeeStatus = 'AGUARDANDO_QUANTIDADE' | 'AGUARDANDO_COTACAO' | 'DISPONIVEL' | 'COTADA' | 'PAGAMENTO_GERADO';

export interface ProjectFee {
  id: string;
  feeId: string;
  name: string;
  description: string | null;
  type: FeeType;
  measurementUnit: string | null;
  quantity: number | null;
  status: ProjectFeeStatus;
  quantityUpdatedAt: string | null;
  /** Falso quando o pagamento já foi gerado ou a taxa não é variável. */
  quantityEditable: boolean;
}

/** US-05: linha da lista de taxas do Cliente (GET /projects/{id}/fees). */
export interface CalculatedFee {
  /** Id do vínculo taxa-projeto: é o id das rotas de quantidade e cotação. */
  projectFeeId: string;
  /** Id da taxa no catálogo. */
  feeId: string;
  name: string;
  description: string | null;
  type: FeeType;
  /** Fixa/por metragem: valor calculado. Variável: valor cotado (null até a cotação). */
  calculatedValue: number | null;
  /** Variável: quantidade informada. Por metragem: m² ou unidades cobradas. */
  quantity: number | null;
  measurementUnit: string | null;
  status: ProjectFeeStatus;
  quantityEditable: boolean;
  paymentDeadline: string | null;
}
