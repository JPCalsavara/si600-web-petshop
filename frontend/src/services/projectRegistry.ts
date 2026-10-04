import type {
  ClientProject,
  ClientProjectInput,
  ClientProjectUpdate,
  Fee,
  ProjectStatus,
} from '../types';

/**
 * Registro de projetos da US-03.
 *
 * Ainda não existe endpoint de criação no backend, então o estado fica em memória
 * (some ao recarregar a página). Toda a regra de negócio da US-03 está aqui, atrás
 * de funções com contrato estável, para trocar por chamadas HTTP depois sem mexer nas telas.
 */

export const FEE_CATALOG: Fee[] = [
  { id: 't0', name: 'Aluguel do Estande', amount: 0, fixed: true },
  { id: 't1', name: 'TFE', amount: 0, fixed: false },
  { id: 't2', name: 'Energia Elétrica Medida por KVA', amount: 0, fixed: false },
  { id: 't3', name: 'Taxa de Limpeza', amount: 0, fixed: false },
  { id: 't4', name: 'Extintor de Incêndio', amount: 0, fixed: false },
  { id: 't5', name: 'Taxa de Emissão', amount: 0, fixed: false },
  { id: 't6', name: 'Credencial Extra', amount: 0, fixed: false },
];

export class ProjectValidationError extends Error {}

let projects: ClientProject[] = [];

const digits = (value: string) => value.replace(/\D/g, '');
const normalizeEmail = (value: string) => value.trim().toLowerCase();

function assertDeadlines(pdfDeadline: string, paymentDeadline: string) {
  if (!pdfDeadline || !paymentDeadline) {
    throw new ProjectValidationError('Informe a data limite de envio do PDF e a data limite de pagamento.');
  }
  // yyyy-mm-dd compara corretamente como texto e evita problemas de fuso horário.
  if (paymentDeadline < pdfDeadline) {
    throw new ProjectValidationError('A data limite de pagamento não pode ser anterior à data de envio do PDF.');
  }
}

export function generateTemporaryPassword(length = 12): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%&';
  const bytes = crypto.getRandomValues(new Uint32Array(length));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join('');
}

export function listClientProjects(status?: ProjectStatus): ClientProject[] {
  return projects.filter((p) => !status || p.status === status);
}

export function createClientProject(input: ClientProjectInput): { project: ClientProject; temporaryPassword: string } {
  const required = [input.name, input.document, input.address, input.email];
  if (required.some((v) => !v.trim())) {
    throw new ProjectValidationError('Preencha todos os campos obrigatórios.');
  }
  assertDeadlines(input.pdfDeadline, input.paymentDeadline);

  const documentTaken = projects.some((p) => digits(p.document) === digits(input.document));
  const emailTaken = projects.some((p) => normalizeEmail(p.email) === normalizeEmail(input.email));
  if (documentTaken || emailTaken) {
    throw new ProjectValidationError('Já existe um projeto cadastrado com este e-mail ou CPF/CNPJ.');
  }

  const project: ClientProject = {
    ...input,
    id: crypto.randomUUID(),
    email: input.email.trim(),
    status: 'AGUARDANDO_PDF',
  };
  projects = [...projects, project];
  return { project, temporaryPassword: generateTemporaryPassword() };
}

export function updateClientProject(id: string, update: ClientProjectUpdate): ClientProject {
  assertDeadlines(update.pdfDeadline, update.paymentDeadline);
  const current = projects.find((p) => p.id === id);
  if (!current) throw new ProjectValidationError('Projeto não encontrado.');
  const updated = { ...current, ...update };
  projects = projects.map((p) => (p.id === id ? updated : p));
  return updated;
}

/** Uso em testes. */
export function resetClientProjects() {
  projects = [];
}
