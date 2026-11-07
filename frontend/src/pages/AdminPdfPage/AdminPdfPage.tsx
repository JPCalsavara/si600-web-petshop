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

interface Props {
  onBack?: () => void;
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('pt-BR') : '-';
}

export function AdminPdfPage({ onBack }: Props) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [filter, setFilter] = useState<ProjectStatus | ''>('PDF_EM_ANALISE');
  const [selected, setSelected] = useState<Project | null>(null);
  const [justification, setJustification] = useState('');
  const [approvedAreas, setApprovedAreas] = useState<Record<string, string>>({});
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

  useEffect(() => {
    void load();
  }, [filter]);

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
    const rawArea = approvedAreas[project.id] ?? '';
    const area = Number(rawArea);
    if (!Number.isFinite(area) || area <= 0) {
      setError('Informe a metragem aprovada em m².');
      return;
    }
    try {
      setError(null);
      await approveProject(project.id, area);
      setApprovedAreas((prev) => {
        const next = { ...prev };
        delete next[project.id];
        return next;
      });
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
      {onBack && (
        <div className={styles.topNav}>
          <button type="button" className={styles.backBtn} onClick={onBack}>
            ← Voltar aos projetos
          </button>
        </div>
      )}

      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Análise de Plantas (PDF)</h1>
          <p className={styles.subtitle}>
            Analise os projetos com PDF em análise. Aprove a metragem oficial ou solicite ajustes.
          </p>
        </div>

        <div className={styles.dropdownWrap}>
          <div className={styles.dropdownHeader} onClick={() => setDropdownOpen(!dropdownOpen)}>
            <span>{filter === '' ? 'Todos os status' : STATUS_LABELS[filter]}</span>
            <span className={styles.caret}>▼</span>
          </div>
          {dropdownOpen && (
            <ul className={styles.dropdownList}>
              <li onClick={() => handleFilterSelect('')}>Todos os status</li>
              {STATUS_ORDER.map((status) => (
                <li key={status} onClick={() => handleFilterSelect(status)}>
                  {STATUS_LABELS[status]}
                </li>
              ))}
            </ul>
          )}
        </div>
      </header>

      {error && (
        <div className={styles.alertDanger} role="alert">
          {error}
        </div>
      )}

      <section className={styles.card}>
        {loading ? (
          <div className={styles.loadingState}>Carregando projetos para análise...</div>
        ) : projects.length === 0 ? (
          <p className={styles.empty}>Nenhum projeto encontrado para o filtro selecionado.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Empresa</th>
                  <th>CNPJ</th>
                  <th>Expositor</th>
                  <th>Estande</th>
                  <th>Enviado em</th>
                  <th>Status</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {projects.map((project) => (
                  <tr key={project.id}>
                    <td data-label="Empresa">
                      <strong className={styles.companyName}>{project.companyName}</strong>
                    </td>
                    <td data-label="CNPJ">{project.document}</td>
                    <td data-label="Expositor">{project.representativeName}</td>
                    <td data-label="Estande">
                      <span className={styles.categoryBadge}>{project.category}</span>
                    </td>
                    <td data-label="Data">{dateTime(project.pdfUploadedAt)}</td>
                    <td data-label="Status">
                      <span className={`${styles.status} ${styles[project.status] ?? ''}`}>
                        {STATUS_LABELS[project.status]}
                      </span>
                    </td>
                    <td data-label="Ações">
                      <div className={styles.actions}>
                        {project.pdfOriginalFilename && (
                          <button
                            type="button"
                            className={styles.downloadBtn}
                            onClick={() => void download(project.id)}
                          >
                            Baixar PDF
                          </button>
                        )}
                        {project.status === 'PDF_EM_ANALISE' && (
                          <>
                            <input
                              type="number"
                              min="0.01"
                              step="0.01"
                              className={styles.areaInput}
                              placeholder="m² aprovado"
                              aria-label={`Metragem aprovada para ${project.companyName}`}
                              value={approvedAreas[project.id] ?? ''}
                              onChange={(event) =>
                                setApprovedAreas((prev) => ({
                                  ...prev,
                                  [project.id]: event.target.value,
                                }))
                              }
                            />
                            <button
                              type="button"
                              className={styles.approve}
                              onClick={() => void approve(project)}
                            >
                              Aprovar
                            </button>
                            <button
                              type="button"
                              className={styles.reject}
                              onClick={() => {
                                setError(null);
                                setSelected(project);
                              }}
                            >
                              Reprovar
                            </button>
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
            <div className={styles.modalHeader}>
              <h2>Reprovar PDF</h2>
              <p className={styles.modalCompany}>{selected.companyName}</p>
            </div>
            <label className={styles.modalField}>
              <span>Justificativa dos ajustes necessários *</span>
              <textarea
                value={justification}
                onChange={(e) => setJustification(e.target.value)}
                rows={5}
                placeholder="Descreva os ajustes que o cliente precisa realizar."
              />
            </label>
            <div className={styles.modalActions}>
              <button
                type="button"
                className={styles.cancelBtn}
                onClick={() => {
                  setSelected(null);
                  setJustification('');
                }}
              >
                Cancelar
              </button>
              <button type="button" className={styles.rejectConfirm} onClick={() => void reject()}>
                Confirmar reprovação
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
