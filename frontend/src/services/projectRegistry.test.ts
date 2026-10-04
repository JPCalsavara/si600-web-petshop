import { beforeEach, describe, expect, it } from 'vitest';
import {
  ProjectValidationError,
  createClientProject,
  listClientProjects,
  resetClientProjects,
  updateClientProject,
} from './projectRegistry';
import type { ClientProjectInput } from '../types';

const valid: ClientProjectInput = {
  name: 'Audio Technia',
  document: '12.345.678/0001-90',
  address: 'Rua A, 10',
  area: 'Music Hub',
  email: 'contato@audio.com',
  pdfDeadline: '2026-10-10',
  paymentDeadline: '2026-11-10',
  feeIds: ['t0', 't1'],
};

describe('US-03 - registro de projetos', () => {
  beforeEach(resetClientProjects);

  it('cria projeto "Aguardando PDF" e devolve senha temporária', () => {
    const { project, temporaryPassword } = createClientProject(valid);
    expect(project.status).toBe('AGUARDANDO_PDF');
    expect(temporaryPassword).toHaveLength(12);
    expect(listClientProjects()).toHaveLength(1);
  });

  it('aceita pagamento no mesmo dia do PDF, rejeita anterior', () => {
    expect(() => createClientProject({ ...valid, paymentDeadline: valid.pdfDeadline })).not.toThrow();
    resetClientProjects();
    expect(() => createClientProject({ ...valid, paymentDeadline: '2026-10-09' })).toThrow(ProjectValidationError);
  });

  it('rejeita datas ausentes e campos obrigatórios vazios', () => {
    expect(() => createClientProject({ ...valid, pdfDeadline: '' })).toThrow(ProjectValidationError);
    expect(() => createClientProject({ ...valid, name: '  ' })).toThrow(ProjectValidationError);
  });

  it('garante e-mail e CPF/CNPJ únicos (ignorando máscara e caixa)', () => {
    createClientProject(valid);
    expect(() => createClientProject({ ...valid, email: 'outro@x.com', document: '12345678000190' })).toThrow(/já existe/i);
    expect(() => createClientProject({ ...valid, document: '999', email: 'CONTATO@audio.com' })).toThrow(/já existe/i);
  });

  it('filtra por status', () => {
    createClientProject(valid);
    expect(listClientProjects('AGUARDANDO_PDF')).toHaveLength(1);
    expect(listClientProjects('CONCLUIDO')).toHaveLength(0);
  });

  it('edita prazos e taxas, revalidando as datas', () => {
    const { project } = createClientProject(valid);
    const updated = updateClientProject(project.id, { pdfDeadline: '2026-10-20', paymentDeadline: '2026-10-25', feeIds: ['t3'] });
    expect(updated.feeIds).toEqual(['t3']);
    expect(() => updateClientProject(project.id, { pdfDeadline: '2026-10-20', paymentDeadline: '2026-10-01', feeIds: [] })).toThrow(ProjectValidationError);
  });
});
