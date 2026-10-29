import { useEffect, useState, useMemo } from 'react';
import { STATUS_LABELS, STATUS_ORDER } from '../../constants/projectStatus';
import { listAdminProjects } from '../../services/api';
import type { Project, ProjectStatus } from '../../types';
import styles from './ProjectListPage.module.scss';
import { ProjectFeesConfigPage } from '../ProjectFeesConfigPage/ProjectFeesConfigPage';

interface Props {
  onAdd: () => void;
  onOpenPdfReview: () => void;
}

const PAGE_SIZE = 15;

type SortField = 'companyName' | 'pdfDeadline' | 'paymentDeadline' | 'status';
type SortDirection = 'asc' | 'desc';

/** yyyy-mm-dd -> dd/mm/aaaa sem passar por Date (evita deslocar um dia por fuso). */
export function formatDate(value: string | null) {
  if (!value) return '-';
  const datePart = value.slice(0, 10);
  const [y, m, d] = datePart.split('-');
  return y && m && d ? `${d}/${m}/${y}` : '-';
}

export function ProjectListPage({ onAdd, onOpenPdfReview }: Props) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [configProject, setConfigProject] = useState<Project | null>(null);

  // Filtros em texto
  const [projectSearch, setProjectSearch] = useState('');
  const [deadlineSearch, setDeadlineSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<ProjectStatus | ''>('');

  // Ordenação
  const [sortField, setSortField] = useState<SortField>('companyName');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');

  // Paginação (15 projetos por página)
  const [currentPage, setCurrentPage] = useState(1);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const data = await listAdminProjects();
        setProjects(data);
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
  }, []);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
    setCurrentPage(1);
  };

  const clearFilters = () => {
    setProjectSearch('');
    setDeadlineSearch('');
    setStatusFilter('');
    setCurrentPage(1);
  };

  const hasActiveFilters = Boolean(projectSearch || deadlineSearch || statusFilter);

  // Filtragem
  const filteredProjects = useMemo(() => {
    return projects.filter((p) => {
      // 1. Filtro por projeto (empresa, CPF/CNPJ ou representante)
      if (projectSearch.trim()) {
        const q = projectSearch.toLowerCase().trim();
        const matchCompany = (p.companyName || '').toLowerCase().includes(q);
        const matchDoc = (p.document || '').toLowerCase().includes(q);
        const matchRep = (p.representativeName || '').toLowerCase().includes(q);
        if (!matchCompany && !matchDoc && !matchRep) return false;
      }

      // 2. Filtro por prazo (PDF ou Pagamento)
      if (deadlineSearch.trim()) {
        const q = deadlineSearch.toLowerCase().trim();
        const pdfFormatted = formatDate(p.pdfDeadline).toLowerCase();
        const payFormatted = formatDate(p.paymentDeadline).toLowerCase();
        const rawPdf = (p.pdfDeadline || '').toLowerCase();
        const rawPay = (p.paymentDeadline || '').toLowerCase();
        if (
          !pdfFormatted.includes(q) &&
          !payFormatted.includes(q) &&
          !rawPdf.includes(q) &&
          !rawPay.includes(q)
        ) {
          return false;
        }
      }

      // 3. Filtro por status
      if (statusFilter) {
        if (p.status !== statusFilter) return false;
      }

      return true;
    });
  }, [projects, projectSearch, deadlineSearch, statusFilter]);

  // Ordenação
  const sortedProjects = useMemo(() => {
    return [...filteredProjects].sort((a, b) => {
      let valA: string | number = '';
      let valB: string | number = '';

      if (sortField === 'companyName') {
        valA = (a.companyName || '').toLowerCase();
        valB = (b.companyName || '').toLowerCase();
      } else if (sortField === 'pdfDeadline') {
        valA = a.pdfDeadline ? new Date(a.pdfDeadline).getTime() : 0;
        valB = b.pdfDeadline ? new Date(b.pdfDeadline).getTime() : 0;
      } else if (sortField === 'paymentDeadline') {
        valA = a.paymentDeadline ? new Date(a.paymentDeadline).getTime() : 0;
        valB = b.paymentDeadline ? new Date(b.paymentDeadline).getTime() : 0;
      } else if (sortField === 'status') {
        valA = STATUS_LABELS[a.status] || a.status;
        valB = STATUS_LABELS[b.status] || b.status;
      }

      if (valA < valB) return sortDirection === 'asc' ? -1 : 1;
      if (valA > valB) return sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }, [filteredProjects, sortField, sortDirection]);

  // Paginação de 15 por página
  const totalPages = Math.max(1, Math.ceil(sortedProjects.length / PAGE_SIZE));
  const pagedProjects = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return sortedProjects.slice(start, start + PAGE_SIZE);
  }, [sortedProjects, currentPage]);

  const renderSortIndicator = (field: SortField) => {
    if (sortField !== field) {
      return <span className={styles.sortIconNeutral}>↕</span>;
    }
    return <span className={styles.sortIconActive}>{sortDirection === 'asc' ? '↑' : '↓'}</span>;
  };

  const getStatusClass = (status: ProjectStatus) => {
    switch (status) {
      case 'APROVADO':
      case 'CONCLUIDO':
        return styles.statusSuccess;
      case 'AGUARDANDO_PDF':
      case 'AGUARDANDO_PAGAMENTO':
        return styles.statusWarning;
      case 'PDF_EM_ANALISE':
        return styles.statusInfo;
      case 'REPROVADO':
        return styles.statusDanger;
      default:
        return styles.statusDefault;
    }
  };

  if (configProject) {
    return (
      <ProjectFeesConfigPage
        projectId={configProject.id}
        projectName={configProject.companyName || 'Projeto'}
        onBack={() => setConfigProject(null)}
      />
    );
  }

  return (
    <div className={styles.page} data-testid="project-list-page">
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Projetos</h1>
          <p className={styles.subtitle}>
            Gerenciamento completo de estandes, prazos de entrega e parametrização de taxas.
          </p>
        </div>
        <div className={styles.headerActions}>
          <button
            type="button"
            className={styles.pdfReviewButton}
            onClick={onOpenPdfReview}
            data-testid="btn-pdf-review"
          >
            Análise de PDFs →
          </button>
          <button
            type="button"
            className={styles.addButton}
            onClick={onAdd}
            data-testid="btn-add-project"
          >
            + Novo Projeto
          </button>
        </div>
      </header>

      {/* Barra de Filtros Interativa */}
      <section className={styles.filtersSection} aria-label="Filtros de projetos">
        <div className={styles.filtersGrid}>
          {/* Filtro por Projeto */}
          <div className={styles.filterGroup}>
            <label htmlFor="filter-project" className={styles.filterLabel}>
              Projeto / Empresa
            </label>
            <input
              id="filter-project"
              type="text"
              className={styles.filterInput}
              placeholder="Nome, CNPJ ou representante..."
              value={projectSearch}
              onChange={(e) => {
                setProjectSearch(e.target.value);
                setCurrentPage(1);
              }}
              data-testid="input-filter-project"
            />
          </div>

          {/* Filtro por Prazo */}
          <div className={styles.filterGroup}>
            <label htmlFor="filter-deadline" className={styles.filterLabel}>
              Prazo (PDF / Pagamento)
            </label>
            <input
              id="filter-deadline"
              type="text"
              className={styles.filterInput}
              placeholder="Ex: 25/10/2026, 2026-10..."
              value={deadlineSearch}
              onChange={(e) => {
                setDeadlineSearch(e.target.value);
                setCurrentPage(1);
              }}
              data-testid="input-filter-deadline"
            />
          </div>

          {/* Filtro por Status */}
          <div className={styles.filterGroup}>
            <label htmlFor="filter-status" className={styles.filterLabel}>
              Status do Projeto
            </label>
            <select
              id="filter-status"
              className={styles.filterSelect}
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as ProjectStatus | '');
                setCurrentPage(1);
              }}
              data-testid="select-filter-status"
            >
              <option value="">Todos os status</option>
              {STATUS_ORDER.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className={styles.filterMetaRow}>
          <div className={styles.resultsCount}>
            Exibindo <strong>{sortedProjects.length}</strong> de <strong>{projects.length}</strong> projetos cadastrados
            {hasActiveFilters && ' (filtros aplicados)'}
          </div>
          {hasActiveFilters && (
            <button
              type="button"
              className={styles.clearFilterButton}
              onClick={clearFilters}
              data-testid="btn-clear-filters"
            >
              ✕ Limpar filtros
            </button>
          )}
        </div>
      </section>

      {/* Tabela de Projetos */}
      <section className={styles.panel}>
        {error && (
          <div className={styles.alert} role="alert" data-testid="error-message">
            {error}
          </div>
        )}

        {loading ? (
          <div className={styles.loadingContainer}>
            <div className={styles.spinner}></div>
            <p>Carregando projetos...</p>
          </div>
        ) : sortedProjects.length === 0 ? (
          <div className={styles.emptyContainer}>
            <p className={styles.emptyTitle}>Nenhum projeto encontrado</p>
            <p className={styles.emptySubtitle}>
              {hasActiveFilters
                ? 'Tente ajustar ou limpar os filtros de busca para encontrar o que procura.'
                : 'Cadastre um novo projeto para começar a monitorar os estandes.'}
            </p>
            {hasActiveFilters && (
              <button
                type="button"
                className={styles.clearFilterButtonInline}
                onClick={clearFilters}
              >
                Limpar todos os filtros
              </button>
            )}
          </div>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th
                    className={styles.sortableHeader}
                    onClick={() => handleSort('companyName')}
                    data-testid="header-sort-company"
                  >
                    Empresa / Projeto {renderSortIndicator('companyName')}
                  </th>
                  <th>CPF/CNPJ</th>
                  <th>Estande</th>
                  <th
                    className={styles.sortableHeader}
                    onClick={() => handleSort('pdfDeadline')}
                    data-testid="header-sort-pdf-deadline"
                  >
                    Prazo PDF {renderSortIndicator('pdfDeadline')}
                  </th>
                  <th
                    className={styles.sortableHeader}
                    onClick={() => handleSort('paymentDeadline')}
                    data-testid="header-sort-payment-deadline"
                  >
                    Prazo Pagamento {renderSortIndicator('paymentDeadline')}
                  </th>
                  <th>Taxas</th>
                  <th
                    className={styles.sortableHeader}
                    onClick={() => handleSort('status')}
                    data-testid="header-sort-status"
                  >
                    Status {renderSortIndicator('status')}
                  </th>
                  <th className={styles.actionsHeader}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {pagedProjects.map((p) => (
                  <tr key={p.id} data-testid={`row-project-${p.id}`}>
                    <td data-label="Empresa">
                      <div className={styles.companyCell}>
                        <strong className={styles.companyName}>{p.companyName}</strong>
                        {p.representativeName && (
                          <span className={styles.representativeName}>{p.representativeName}</span>
                        )}
                      </div>
                    </td>
                    <td data-label="CPF/CNPJ">
                      <span className={styles.documentText}>{p.document || '-'}</span>
                    </td>
                    <td data-label="Estande">
                      <span className={styles.categoryBadge}>{p.category || 'Geral'}</span>
                    </td>
                    <td data-label="Prazo PDF">
                      <span className={styles.dateCell}>{formatDate(p.pdfDeadline)}</span>
                    </td>
                    <td data-label="Prazo Pagamento">
                      <span className={styles.dateCell}>{formatDate(p.paymentDeadline)}</span>
                    </td>
                    <td data-label="Taxas">
                      {p.status === 'APROVADO' || p.status === 'AGUARDANDO_PAGAMENTO' ? (
                        <button
                          type="button"
                          className={styles.configFeeBtn}
                          onClick={() => setConfigProject(p)}
                          data-testid={`btn-config-fee-${p.id}`}
                        >
                          Configurar taxas
                        </button>
                      ) : (
                        <span className={styles.feeNotice}>Após aprovação do PDF</span>
                      )}
                    </td>
                    <td data-label="Status">
                      <span className={`${styles.statusBadge} ${getStatusClass(p.status)}`}>
                        {STATUS_LABELS[p.status] || p.status}
                      </span>
                    </td>
                    <td data-label="Ações" className={styles.actionsCell}>
                      {p.status === 'APROVADO' || p.status === 'AGUARDANDO_PAGAMENTO' ? (
                        <button
                          type="button"
                          className={styles.actionLinkBtn}
                          onClick={() => setConfigProject(p)}
                        >
                          Ver taxas →
                        </button>
                      ) : (
                        <span className={styles.noAction}>—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Paginação de 15 em 15 */}
            {totalPages > 1 && (
              <div className={styles.pagination} data-testid="projects-pagination">
                <div className={styles.paginationInfo}>
                  Página <strong>{currentPage}</strong> de <strong>{totalPages}</strong> (15 projetos por página)
                </div>
                <div className={styles.paginationControls}>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                    disabled={currentPage <= 1}
                    data-testid="btn-prev-projects"
                  >
                    ← Anterior
                  </button>
                  <span className={styles.pageCounter}>
                    {currentPage} / {totalPages}
                  </span>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                    disabled={currentPage >= totalPages}
                    data-testid="btn-next-projects"
                  >
                    Próximo →
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
