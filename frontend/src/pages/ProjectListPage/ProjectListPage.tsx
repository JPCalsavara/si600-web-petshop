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
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const projects = listClientProjects(filter || undefined);

  const handleFilterSelect = (val: ProjectStatus | '') => {
    setFilter(val);
    setDropdownOpen(false);
  };

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Projetos</h1>
        <div className={styles.tools}>
          <button type="button" className={styles.link} onClick={onOpenPdfReview}>Análise de PDFs</button>
          <div className={styles.dropdownWrap}>
            <div className={styles.dropdownHeader} onClick={() => setDropdownOpen(!dropdownOpen)}>
              {filter === '' ? 'Todos os status' : STATUS_LABELS[filter]}
              <span className={styles.caret}>▼</span>
            </div>
            {dropdownOpen && (
              <ul className={styles.dropdownList}>
                <li onClick={() => handleFilterSelect('')}>Todos os status</li>
                {STATUS_ORDER.map((s) => (
                  <li key={s} onClick={() => handleFilterSelect(s)}>{STATUS_LABELS[s]}</li>
                ))}
              </ul>
            )}
          </div>
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
                    <td data-label="Empresa"><strong>{p.name}</strong></td>
                    <td data-label="CPF/CNPJ">{p.document}</td>
                    <td data-label="Estande">{p.area}</td>
                    <td data-label="Prazo PDF">{formatDate(p.pdfDeadline)}</td>
                    <td data-label="Prazo pagamento">{formatDate(p.paymentDeadline)}</td>
                    <td data-label="Taxas" title={FEE_CATALOG.filter((f) => p.feeIds.includes(f.id)).map((f) => f.name).join(', ')}>
                      {p.feeIds.length}
                    </td>
                    <td data-label="Status"><span className={styles.status}>{STATUS_LABELS[p.status]}</span></td>
                    <td data-label="Ações"><button type="button" className={styles.edit} onClick={() => onEdit(p)}>Editar</button></td>
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
