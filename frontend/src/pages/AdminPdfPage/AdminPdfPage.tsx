import { useEffect, useState } from 'react';
import {
  approveProject,
  getPdfDownloadUrl,
  listAdminProjects,
  rejectProject,
} from '../../services/api';
import { STATUS_LABELS, STATUS_ORDER } from '../../constants/projectStatus';
import type { Project, ProjectStatus } from '../../types';
import styles from './AdminPdfPage.module.scss';

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('pt-BR') : '-';
}

export function AdminPdfPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [filter, setFilter] = useState<ProjectStatus | ''>('PDF_EM_ANALISE');
  const [selected, setSelected] = useState<Project | null>(null);
  const [justification, setJustification] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [dropdownOpen, setDropdownOpen] = useState(false);

  async function load() {
    try {
      setLoading(true);
      setError(null);
      setProjects(await listAdminProjects(filter || undefined));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível carregar os projetos.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [filter]);

  async function download(projectId: string) {
    try {
      setError(null);
      const result = await getPdfDownloadUrl(projectId);
      window.open(result.url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível gerar o download.');
    }
  }

  async function approve(project: Project) {
    try {
      setError(null);
      await approveProject(project.id);
      await load();
      setSelected(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível aprovar o PDF.');
    }
  }

  async function reject() {
    if (!selected || !justification.trim()) {
      setError('A justificativa é obrigatória para reprovar o PDF.');
      return;
    }
    try {
      setError(null);
      await rejectProject(selected.id, justification);
      setJustification('');
      setSelected(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível reprovar o PDF.');
    }
  }

  const handleFilterSelect = (val: ProjectStatus | '') => {
    setFilter(val);
    setDropdownOpen(false);
  };

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>Projetos</h1>
          <p>Analise os projetos com PDF em análise.</p>
        </div>
        <div className={styles.dropdownWrap}>
          <div className={styles.dropdownHeader} onClick={() => setDropdownOpen(!dropdownOpen)}>
            {filter === '' ? 'Todos os status' : STATUS_LABELS[filter]}
            <span className={styles.caret}>▼</span>
          </div>
          {dropdownOpen && (
            <ul className={styles.dropdownList}>
              <li onClick={() => handleFilterSelect('')}>Todos os status</li>
              {STATUS_ORDER.map(status => (
                <li key={status} onClick={() => handleFilterSelect(status)}>{STATUS_LABELS[status]}</li>
              ))}
            </ul>
          )}
        </div>
      </header>

      {error && <div className={styles.alertDanger}>{error}</div>}

      <section className={styles.card}>
        {loading ? <p>Carregando...</p> : projects.length === 0 ? (
          <p className={styles.empty}>Nenhum projeto encontrado.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>Empresa</th>
                  <th>CNPJ</th>
                  <th>Expositor</th>
                  <th>Estante</th>
                  <th>Data</th>
                  <th>Status</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {projects.map((project) => (
                  <tr key={project.id}>
                    <td data-label="Empresa"><strong>{project.companyName}</strong></td>
                    <td data-label="CNPJ">{project.document}</td>
                    <td data-label="Expositor">{project.representativeName}</td>
                    <td data-label="Estante">{project.category}</td>
                    <td data-label="Data">{dateTime(project.pdfUploadedAt)}</td>
                    <td data-label="Status"><span className={styles.status}>{STATUS_LABELS[project.status]}</span></td>
                    <td data-label="Ações">
                      <div className={styles.actions}>
                        {project.pdfOriginalFilename && (
                          <button onClick={() => download(project.id)}>Baixar PDF</button>
                        )}
                        {project.status === 'PDF_EM_ANALISE' && (
                          <>
                            <button className={styles.approve} onClick={() => approve(project)}>Aprovar</button>
                            <button className={styles.reject} onClick={() => { setError(null); setSelected(project); }}>Reprovar</button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {selected && (
        <div className={styles.modalBackdrop}>
          <div className={styles.modal}>
            <h2>Reprovar PDF</h2>
            <p><strong>{selected.companyName}</strong></p>
            <label>
              Justificativa dos ajustes necessários *
              <textarea
                value={justification}
                onChange={(e) => setJustification(e.target.value)}
                rows={5}
                placeholder="Descreva os ajustes que o cliente precisa realizar."
              />
            </label>
            <div className={styles.modalActions}>
              <button onClick={() => { setSelected(null); setJustification(''); }}>Cancelar</button>
              <button className={styles.reject} onClick={reject}>Confirmar reprovação</button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
