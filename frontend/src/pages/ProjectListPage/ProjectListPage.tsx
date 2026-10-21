import { useEffect, useState } from 'react';
import { STATUS_LABELS, STATUS_ORDER } from '../../constants/projectStatus';
import { listAdminProjects } from '../../services/api';
import type { Project, ProjectStatus } from '../../types';
import styles from './ProjectListPage.module.scss';
import { ProjectFeesConfigPage } from '../ProjectFeesConfigPage/ProjectFeesConfigPage';

interface Props {
  onAdd: () => void;
  onOpenPdfReview: () => void;
}

/** yyyy-mm-dd -> dd/mm/aaaa sem passar por Date (evita deslocar um dia por fuso). */
export function formatDate(value: string | null) {
  if (!value) return '-';
  const datePart = value.slice(0, 10);
  const [y, m, d] = datePart.split('-');
  return y && m && d ? `${d}/${m}/${y}` : '-';
}

export function ProjectListPage({ onAdd, onOpenPdfReview }: Props) {
  const [filter, setFilter] = useState<ProjectStatus | ''>('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [configProject, setConfigProject] = useState<Project | null>(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError(null);
        setProjects(await listAdminProjects(filter || undefined));
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : 'Não foi possível carregar os projetos.'
        );
      } finally {
        setLoading(false);
      }
    }

    void load();
  }, [filter]);

  if (configProject) {
    return (
      <ProjectFeesConfigPage
        projectId={configProject.id}
        projectName={configProject.companyName}
        onBack={() => setConfigProject(null)}
      />
    );
  }

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
        {error && <div className={styles.alert} role="alert">{error}</div>}
        {loading ? (
          <p className={styles.empty}>Carregando projetos...</p>
        ) : projects.length === 0 ? (
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
                    <td data-label="Empresa"><strong>{p.companyName}</strong></td>
                    <td data-label="CPF/CNPJ">{p.document}</td>
                    <td data-label="Estande">{p.category}</td>
                    <td data-label="Prazo PDF">{formatDate(p.pdfDeadline)}</td>
                    <td data-label="Prazo pagamento">{formatDate(p.paymentDeadline)}</td>
                    <td data-label="Taxas">
                      {p.status === 'APROVADO' || p.status === 'AGUARDANDO_PAGAMENTO'
                        ? <button type="button" onClick={() => setConfigProject(p)}>Configurar taxas</button>
                        : 'Disponível após aprovação do PDF'}
                    </td>
                    <td data-label="Status"><span className={styles.status}>{STATUS_LABELS[p.status]}</span></td>
                    <td data-label="Ações">{p.status === 'APROVADO' || p.status === 'AGUARDANDO_PAGAMENTO' ? <button type="button" onClick={() => setConfigProject(p)}>Ver taxas</button> : '—'}</td>
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
