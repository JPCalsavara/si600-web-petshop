import { useEffect, useMemo, useState } from 'react';
import {
  approveProject,
  getPdfDownloadUrl,
  listAdminProjects,
  rejectProject,
} from '../../services/api';
import { STATUS_LABELS, STATUS_ORDER } from '../../constants/projectStatus';
import { usePaginatedList } from '../../components/Pagination/Pagination';
import { formatDateTime } from '../../utils/date';
import type { Project, ProjectStatus } from '../../types';
import styles from './AdminPdfPage.module.scss';

interface Props {
  onBack?: () => void;
}

const PAGE_SIZE = 15;

export function AdminPdfPage({ onBack }: Props) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [filter, setFilter] = useState<ProjectStatus | ''>('PDF_EM_ANALISE');
  const [projectSearch, setProjectSearch] = useState('');
  const [selected, setSelected] = useState<Project | null>(null);
  const [justification, setJustification] = useState('');
  const [approvedAreas, setApprovedAreas] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

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

  const filteredProjects = useMemo(() => {
    const matches = projects.filter((p) => {
      if (projectSearch.trim()) {
        const q = projectSearch.toLowerCase().trim();
        const matchCompany = (p.companyName || '').toLowerCase().includes(q);
        const matchDoc = (p.document || '').toLowerCase().includes(q);
        const matchRep = (p.representativeName || '').toLowerCase().includes(q);
        if (!matchCompany && !matchDoc && !matchRep) return false;
      }
      return true;
    });
    return [...matches].sort((a, b) => {
      const timeA = a.pdfUploadedAt ? new Date(a.pdfUploadedAt).getTime() : 0;
      const timeB = b.pdfUploadedAt ? new Date(b.pdfUploadedAt).getTime() : 0;
      return timeB - timeA;
    });
  }, [projects, projectSearch]);

  const hasActiveFilters = Boolean(projectSearch.trim() || filter !== 'PDF_EM_ANALISE');

  const {
    currentPage: safeCurrentPage,
    totalPages,
    pagedItems: pagedProjects,
    setCurrentPage,
    prevPage,
    nextPage,
    resetPage,
  } = usePaginatedList(filteredProjects, PAGE_SIZE);

  const clearFilters = () => {
    setProjectSearch('');
    setFilter('PDF_EM_ANALISE');
    resetPage();
  };

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
      </header>

      {/* Barra de Filtros */}
      <section className={styles.filtersSection} aria-label="Filtros de análise de plantas">
        <div className={styles.filtersGrid}>
          <div className={styles.filterGroup}>
            <label htmlFor="filter-pdf-project" className={styles.filterLabel}>
              Empresa / CNPJ / Expositor
            </label>
            <input
              id="filter-pdf-project"
              type="text"
              className={styles.filterInput}
              placeholder="Buscar por nome da empresa, CNPJ ou expositor..."
              value={projectSearch}
              onChange={(e) => {
                setProjectSearch(e.target.value);
                setCurrentPage(1);
              }}
              data-testid="input-filter-pdf-project"
            />
          </div>

          <div className={styles.filterGroup}>
            <label htmlFor="filter-pdf-status" className={styles.filterLabel}>
              Status do Projeto
            </label>
            <select
              id="filter-pdf-status"
              className={styles.filterSelect}
              value={filter}
              onChange={(e) => {
                setFilter(e.target.value as ProjectStatus | '');
                setCurrentPage(1);
              }}
              data-testid="select-filter-pdf-status"
            >
              <option value="">Todos os status</option>
              {STATUS_ORDER.map((status) => (
                <option key={status} value={status}>
                  {STATUS_LABELS[status]}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className={styles.filterMetaRow}>
          <div className={styles.resultsCount}>
            Exibindo <strong>{filteredProjects.length}</strong> de <strong>{projects.length}</strong> projetos
            {projectSearch.trim() && ' (filtrados por busca)'}
          </div>
          {hasActiveFilters && (
            <button
              type="button"
              className={styles.clearFilterButton}
              onClick={clearFilters}
              data-testid="btn-clear-pdf-filters"
            >
              ✕ Restaurar filtros
            </button>
          )}
        </div>
      </section>

      {error && (
        <div className={styles.alertDanger} role="alert">
          {error}
        </div>
      )}

      <section className={styles.panel}>
        {loading ? (
          <div className={styles.loadingState}>Carregando projetos para análise...</div>
        ) : filteredProjects.length === 0 ? (
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
                  <th className={styles.actionsHeader}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {pagedProjects.map((project) => (
                  <tr key={project.id}>
                    <td data-label="Empresa">
                      <strong className={styles.companyName}>{project.companyName}</strong>
                    </td>
                    <td data-label="CNPJ">{project.document}</td>
                    <td data-label="Expositor">{project.representativeName}</td>
                    <td data-label="Estande">
                      <span className={styles.categoryBadge}>{project.category}</span>
                    </td>
                    <td data-label="Data">{formatDateTime(project.pdfUploadedAt)}</td>
                    <td data-label="Status">
                      <span className={`${styles.status} ${styles[project.status] ?? ''}`}>
                        {STATUS_LABELS[project.status]}
                      </span>
                    </td>
                    <td data-label="Ações" className={styles.actionsCell}>
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

            {totalPages > 1 && (
              <div className={styles.pagination} data-testid="pdf-pagination">
                <div className={styles.paginationInfo}>
                  Página <strong>{safeCurrentPage}</strong> de <strong>{totalPages}</strong>
                </div>
                <div className={styles.paginationControls}>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={prevPage}
                    disabled={safeCurrentPage <= 1}
                  >
                    ← Anterior
                  </button>
                  <span className={styles.pageCounter}>
                    {safeCurrentPage} / {totalPages}
                  </span>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={nextPage}
                    disabled={safeCurrentPage >= totalPages}
                  >
                    Próximo →
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </section>

      {selected && (
        <div className={styles.modalBackdrop}>
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <div>
                <h2>Reprovar PDF</h2>
                <p className={styles.modalCompany}>{selected.companyName}</p>
              </div>
              <button
                type="button"
                className={styles.closeModalBtn}
                onClick={() => {
                  setSelected(null);
                  setJustification('');
                }}
                aria-label="Cancelar"
                title="Cancelar e fechar"
              >
                <span aria-hidden="true">✕</span>
                <span className={styles.srOnly}>Cancelar</span>
              </button>
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
