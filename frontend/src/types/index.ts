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

export type AlertTag = 'ATRASADO' | 'PRAZO_PROXIMO' | 'PENDENTE' | 'EM_DIA';

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

export interface DashboardFinancialSummary {
  totalProjectFees: number;
  pendingQuotesCount: number;
  awaitingQuantityCount: number;
  quotedFeesCount: number;
  generatedPaymentsCount: number;
  estimatedRevenue: number;
}

export interface AdminDashboardData {
  inactiveExhibitors: InactiveExhibitor[];
  pendingDeadlines: Project[];
  pendingApprovals: PendingApproval[];
  financialSummary?: DashboardFinancialSummary;
}

export type AreaEstande = 'B2C' | 'Music Hub' | 'Music Sport' | 'Internacional';

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

export type ProjectFeeStatus =
  | 'AGUARDANDO_QUANTIDADE'
  | 'AGUARDANDO_COTACAO'
  | 'DISPONIVEL'
  | 'COTADA'
  | 'PAGAMENTO_GERADO';

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
  quantityEditable: boolean;
}

export interface CalculatedFee {
  projectFeeId: string;
  feeId: string;
  name: string;
  description: string | null;
  type: FeeType;
  calculatedValue: number | null;
  quantity: number | null;
  measurementUnit: string | null;
  status: ProjectFeeStatus;
  quantityEditable: boolean;
  paymentDeadline: string | null;
}

export interface QuoteableProjectFee {
  projectId: string;
  projectName: string;
  clientEmail: string | null;
  projectFeeId: string;
  feeName: string;
  feeType: string;
  quantity: number | null;
  measurementUnit: string | null;
  status: string;
  quotedAmount: number | null;
  quotedBy: string | null;
  quotedAt: string | null;
}

export interface ProjectFeeConfiguration {
  projectFeeId: string;
  feeId: string;
  name: string;
  description: string | null;
  type: string;
  measurementUnit: string | null;
  amount: number | null;
  amountPerM2: number | null;
  areaPerUnitM2: number | null;
  unitAmount: number | null;
  areaPricingMode: string | null;
  status: string;
  quantity: number | null;
  quotedAmount: number | null;
  quotedBy: string | null;
  quotedAt: string | null;
}

export type ProjectHistoryEventType =
  | 'PROJETO_CRIADO'
  | 'PDF_ENVIADO'
  | 'PDF_APROVADO'
  | 'PDF_REPROVADO'
  | 'TAXAS_CONFIGURADAS'
  | 'QUANTIDADE_INFORMADA'
  | 'TAXA_COTADA'
  | 'PAGAMENTO_GERADO'
  | 'PAGAMENTO_CONFIRMADO'
  | 'PAGAMENTO_CANCELADO';

export interface ProjectHistoryEvent {
  id: string;
  projectId: string;
  eventType: ProjectHistoryEventType;
  actorId: string;
  actorRole: 'ADMIN' | 'CLIENT' | string;
  description: string;
  createdAt: string;
}
