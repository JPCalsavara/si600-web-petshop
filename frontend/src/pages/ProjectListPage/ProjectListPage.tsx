import { useState } from 'react';
import { STATUS_LABELS, STATUS_ORDER } from '../../constants/projectStatus';
import { FEE_CATALOG, listClientProjects } from '../../services/projectRegistry';
import type { ClientProject, ProjectStatus } from '../../types';
import styles from './ProjectListPage.module.scss';

interface Props {
  onAdd: () => void;
  onEdit: (project: ClientProject) => void;
  onOpenPdfReview: () => void;
}

/** yyyy-mm-dd -> dd/mm/aaaa sem passar por Date (evita deslocar um dia por fuso). */
export function formatDate(value: string) {
  const [y, m, d] = value.split('-');
  return y && m && d ? `${d}/${m}/${y}` : '-';
}

export function ProjectListPage({ onAdd, onEdit, onOpenPdfReview }: Props) {
  const [filter, setFilter] = useState<ProjectStatus | ''>('');
  const projects = listClientProjects(filter || undefined);

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Projetos</h1>
        <div className={styles.tools}>
          <button type="button" className={styles.link} onClick={onOpenPdfReview}>Análise de PDFs</button>
          <select
            aria-label="Filtrar por status"
            value={filter}
            onChange={(e) => setFilter(e.target.value as ProjectStatus | '')}
          >
            <option value="">Todos os status</option>
            {STATUS_ORDER.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
          </select>
        </div>
      </header>

      <section className={styles.panel}>
        {projects.length === 0 ? (
          <p className={styles.empty}>Nenhum projeto encontrado.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>Empresa</th>
                  <th>CPF/CNPJ</th>
                  <th>Estande</th>
                  <th>Prazo PDF</th>
                  <th>Prazo pagamento</th>
                  <th>Taxas</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {projects.map((p) => (
                  <tr key={p.id}>
                    <td><strong>{p.name}</strong></td>
                    <td>{p.document}</td>
                    <td>{p.area}</td>
                    <td>{formatDate(p.pdfDeadline)}</td>
                    <td>{formatDate(p.paymentDeadline)}</td>
                    <td title={FEE_CATALOG.filter((f) => p.feeIds.includes(f.id)).map((f) => f.name).join(', ')}>
                      {p.feeIds.length}
                    </td>
                    <td><span className={styles.status}>{STATUS_LABELS[p.status]}</span></td>
                    <td><button type="button" className={styles.edit} onClick={() => onEdit(p)}>Editar</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <div className={styles.footer}>
        <button type="button" className={styles.add} onClick={onAdd}>Adicionar</button>
      </div>
    </div>
  );
}
