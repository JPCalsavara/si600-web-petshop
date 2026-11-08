import { useEffect, useMemo, useState } from 'react';
import { getAdminDashboard } from '../../services/api';
import type {
  AdminDashboardData,
  DashboardFinancialSummary,
  InactiveExhibitor,
  PendingApproval,
  Project,
  ProjectStatus,
} from '../../types';
import { STATUS_LABELS, STATUS_ORDER } from '../../constants/projectStatus';
import { ALERT_TAG_LABELS } from '../../constants/alertTag';
import { ProjectTimeline } from '../../components/ProjectTimeline/ProjectTimeline';
import { formatDate } from '../../utils/date';
import styles from './AdminDashboardPage.module.scss';

interface Props {
  onOpenPdfReview?: (projectId?: string) => void;
  onNavigateToProjects?: () => void;
  onNavigateToQuotes?: () => void;
}

const PAGE_SIZE = 4;

type PendingKanbanItem =
  | { kind: 'approval'; item: PendingApproval }
  | { kind: 'deadline'; item: Project };

type KpiFilterMode = 'ALL' | 'NEVER_ACCESSED' | 'PENDING_APPROVALS' | 'CRITICAL_DEADLINES';

interface ModalTarget {
  projectId: string;
  companyName: string;
  project?: Project;
  inactive?: InactiveExhibitor;
  approval?: PendingApproval;
}

const DEFAULT_FINANCIAL_SUMMARY: DashboardFinancialSummary = {
  totalProjectFees: 0,
  pendingQuotesCount: 0,
  awaitingQuantityCount: 0,
  quotedFeesCount: 0,
  generatedPaymentsCount: 0,
  estimatedRevenue: 0,
};

function formatCurrencyBRL(value: number | null | undefined): string {
  const numericValue = typeof value === 'number' && !Number.isNaN(value) ? value : 0;
  return numericValue.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  });
}

function getAlertBadgeLabel(alertTag?: string): string {
  if (alertTag && alertTag in ALERT_TAG_LABELS) {
    return ALERT_TAG_LABELS[alertTag as keyof typeof ALERT_TAG_LABELS];
  }
  return ALERT_TAG_LABELS.EM_DIA;
}

export function AdminDashboardPage({
  onOpenPdfReview,
  onNavigateToProjects,
  onNavigateToQuotes,
}: Props) {
  const [data, setData] = useState<AdminDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filtros interativos do Dashboard
  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<ProjectStatus | ''>('');
  const [kpiFilter, setKpiFilter] = useState<KpiFilterMode>('ALL');

  // Paginação individual por grupo (4 itens por página)
  const [pageNeverAccessed, setPageNeverAccessed] = useState(1);
  const [pagePending, setPagePending] = useState(1);
  const [pageOnTrack, setPageOnTrack] = useState(1);

  // Modal de detalhes + linha do tempo do projeto
  const [selectedModal, setSelectedModal] = useState<ModalTarget | null>(null);

  async function loadDashboard() {
    try {
      setLoading(true);
      setError(null);
      const result = await getAdminDashboard();
      setData(result);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Não foi possível carregar as informações do dashboard.'
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadDashboard();
  }, []);

  function resetPagination() {
    setPageNeverAccessed(1);
    setPagePending(1);
    setPageOnTrack(1);
  }

  function clearAllFilters() {
    setSearchTerm('');
    setCategoryFilter('');
    setStatusFilter('');
    setKpiFilter('ALL');
    resetPagination();
  }

  // Mapa rápido por projectId para enriquecer cards entre painéis
  const projectById = useMemo(() => {
    const map = new Map<string, Project>();
    for (const p of data?.pendingDeadlines || []) {
      map.set(p.id, p);
    }
    return map;
  }, [data]);

  const inactiveById = useMemo(() => {
    const map = new Map<string, InactiveExhibitor>();
    for (const item of data?.inactiveExhibitors || []) {
      map.set(item.projectId, item);
    }
    return map;
  }, [data]);

  const approvalById = useMemo(() => {
    const map = new Map<string, PendingApproval>();
    for (const app of data?.pendingApprovals || []) {
      map.set(app.projectId, app);
    }
    return map;
  }, [data]);

  // Lista de categorias únicas disponíveis
  const availableCategories = useMemo(() => {
    const set = new Set<string>();
    for (const p of data?.pendingDeadlines || []) {
      if (p.category && p.category.trim()) {
        set.add(p.category.trim());
      }
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b, 'pt-BR'));
  }, [data]);

  // Cálculo consolidado dos 6 KPIs Operacionais e Financeiros
  const kpiMetrics = useMemo(() => {
    const allProjects = data?.pendingDeadlines || [];
    const allInactives = data?.inactiveExhibitors || [];
    const allApprovals = data?.pendingApprovals || [];
    const financial = data?.financialSummary || DEFAULT_FINANCIAL_SUMMARY;

    const uniqueProjectIds = new Set<string>([
      ...allProjects.map((p) => p.id),
      ...allInactives.map((i) => i.projectId),
      ...allApprovals.map((a) => a.projectId),
    ]);
    const totalProjects = uniqueProjectIds.size;

    const totalAreaM2 = allProjects.reduce(
      (acc, p) => acc + (typeof p.boothAreaM2 === 'number' ? p.boothAreaM2 : 0),
      0
    );

    const strictlyNeverAccessed = allInactives.filter((i) => i.neverAccessed).length;
    const accessedCount = Math.max(0, allInactives.length - strictlyNeverAccessed);
    const neverAccessedPercent =
      allInactives.length > 0
        ? Math.round((strictlyNeverAccessed / allInactives.length) * 100)
        : 0;

    const approvedCount = allProjects.filter(
      (p) =>
        p.status === 'APROVADO' ||
        p.status === 'AGUARDANDO_PAGAMENTO' ||
        p.status === 'CONCLUIDO'
    ).length;
    const rejectedCount = allProjects.filter((p) => p.status === 'REPROVADO').length;

    const overdueCount = allProjects.filter((p) => p.alertTag === 'ATRASADO').length;
    const dueSoonCount = allProjects.filter((p) => p.alertTag === 'PRAZO_PROXIMO').length;
    const criticalDeadlinesCount = overdueCount + dueSoonCount;
    const regularDeadlinesCount = Math.max(0, allProjects.length - criticalDeadlinesCount);

    return {
      totalProjects,
      totalAreaM2,
      strictlyNeverAccessed,
      accessedCount,
      neverAccessedPercent,
      pendingApprovalsCount: allApprovals.length,
      approvedCount,
      rejectedCount,
      overdueCount,
      dueSoonCount,
      criticalDeadlinesCount,
      regularDeadlinesCount,
      financial,
    };
  }, [data]);

  // Função auxiliar de filtro por busca, categoria, status e KPI ativo
  const normalizedSearch = searchTerm.trim().toLowerCase();
  const hasActiveFilters =
    normalizedSearch.length > 0 ||
    categoryFilter !== '' ||
    statusFilter !== '' ||
    kpiFilter !== 'ALL';

  function matchesCommonFilters(
    projectId: string,
    companyName: string,
    representativeName?: string | null,
    contactEmail?: string | null
  ): boolean {
    const linked = projectById.get(projectId);

    if (categoryFilter && linked?.category !== categoryFilter) {
      return false;
    }

    if (statusFilter && linked?.status !== statusFilter) {
      return false;
    }

    if (normalizedSearch) {
      const haystack = [
        companyName,
        representativeName || linked?.representativeName || '',
        contactEmail || linked?.contactEmail || '',
        linked?.document || '',
        linked?.category || '',
      ]
        .join(' ')
        .toLowerCase();

      if (!haystack.includes(normalizedSearch)) {
        return false;
      }
    }

    if (kpiFilter === 'NEVER_ACCESSED') {
      const inact = inactiveById.get(projectId);
      if (!inact?.neverAccessed) return false;
    } else if (kpiFilter === 'PENDING_APPROVALS') {
      if (!approvalById.has(projectId) && linked?.status !== 'PDF_EM_ANALISE') {
        return false;
      }
    } else if (kpiFilter === 'CRITICAL_DEADLINES') {
      if (linked?.alertTag !== 'ATRASADO' && linked?.alertTag !== 'PRAZO_PROXIMO') {
        return false;
      }
    }

    return true;
  }

  // 1. Grupo: Nunca acessou / Inativos
  const neverAccessedItems: InactiveExhibitor[] = (data?.inactiveExhibitors || []).filter((item) =>
    matchesCommonFilters(
      item.projectId,
      item.companyName,
      item.representativeName,
      item.contactEmail
    )
  );
  const totalNeverAccessedPages = Math.max(1, Math.ceil(neverAccessedItems.length / PAGE_SIZE));
  const effectivePageNeverAccessed = Math.min(pageNeverAccessed, totalNeverAccessedPages);
  const pagedNeverAccessed = neverAccessedItems.slice(
    (effectivePageNeverAccessed - 1) * PAGE_SIZE,
    effectivePageNeverAccessed * PAGE_SIZE
  );

  // 2. Grupo: Pendentes (aprovações de plantas + pendências de prazos)
  const pendingApprovalsList: PendingApproval[] = (data?.pendingApprovals || []).filter((app) =>
    matchesCommonFilters(app.projectId, app.companyName)
  );
  const allApprovalIds = new Set((data?.pendingApprovals || []).map((app) => app.projectId));

  const pendingDeadlineProjects: Project[] = (data?.pendingDeadlines || [])
    .filter(
      (p) =>
        (p.alertTag !== 'EM_DIA' && Boolean(p.alertTag)) ||
        p.status === 'PDF_EM_ANALISE' ||
        allApprovalIds.has(p.id)
    )
    .filter((p) =>
      matchesCommonFilters(p.id, p.companyName, p.representativeName, p.contactEmail)
    );

  const allPendingItems: PendingKanbanItem[] = [
    ...pendingApprovalsList.map((app): PendingKanbanItem => ({ kind: 'approval', item: app })),
    ...pendingDeadlineProjects
      .filter((p) => !allApprovalIds.has(p.id))
      .map((p): PendingKanbanItem => ({ kind: 'deadline', item: p })),
  ];

  const totalPendingPages = Math.max(1, Math.ceil(allPendingItems.length / PAGE_SIZE));
  const effectivePagePending = Math.min(pagePending, totalPendingPages);
  const pagedPendingItems = allPendingItems.slice(
    (effectivePagePending - 1) * PAGE_SIZE,
    effectivePagePending * PAGE_SIZE
  );

  // 3. Grupo: Em dia
  const onTrackProjects: Project[] = (data?.pendingDeadlines || [])
    .filter(
      (p) =>
        (p.alertTag === 'EM_DIA' || !p.alertTag) &&
        p.status !== 'PDF_EM_ANALISE' &&
        !allApprovalIds.has(p.id)
    )
    .filter((p) =>
      matchesCommonFilters(p.id, p.companyName, p.representativeName, p.contactEmail)
    );
  const totalOnTrackPages = Math.max(1, Math.ceil(onTrackProjects.length / PAGE_SIZE));
  const effectivePageOnTrack = Math.min(pageOnTrack, totalOnTrackPages);
  const pagedOnTrack = onTrackProjects.slice(
    (effectivePageOnTrack - 1) * PAGE_SIZE,
    effectivePageOnTrack * PAGE_SIZE
  );

  function openProjectModal(projectId: string, fallbackCompanyName: string) {
    setSelectedModal({
      projectId,
      companyName: fallbackCompanyName,
      project: projectById.get(projectId),
      inactive: inactiveById.get(projectId),
      approval: approvalById.get(projectId),
    });
  }

  function toggleKpiFilter(mode: KpiFilterMode) {
    setKpiFilter((prev) => (prev === mode ? 'ALL' : mode));
    resetPagination();
  }

  return (
    <div className={styles.dashboard} data-testid="admin-dashboard-page">
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Dashboard Operacional</h1>
          <p className={styles.subtitle}>
            Visão consolidada de expositores, KPIs executivos, pendências operacionais e aprovações de plantas.
          </p>
        </div>

        <div className={styles.headerControls}>
          {onNavigateToProjects && (
            <button
              type="button"
              className={styles.secondaryNavButton}
              onClick={() => onNavigateToProjects()}
              data-testid="btn-navigate-projects"
            >
              Ver Todos os Projetos
            </button>
          )}
          {onOpenPdfReview && (
            <button
              type="button"
              className={styles.pdfReviewButton}
              onClick={() => onOpenPdfReview()}
              data-testid="btn-open-pdf-review"
            >
              Análise de PDFs
            </button>
          )}
          <button
            type="button"
            className={styles.refreshButton}
            onClick={() => void loadDashboard()}
            disabled={loading}
            data-testid="btn-refresh-dashboard"
          >
            {loading ? 'Atualizando...' : 'Atualizar'}
          </button>
        </div>
      </header>

      {error && (
        <div className={styles.alertError} role="alert" data-testid="dashboard-error">
          <span>{error}</span>
        </div>
      )}

      {loading && !data && (
        <div className={styles.loadingContainer} data-testid="dashboard-loading">
          <div className={styles.spinner}></div>
          <p>Consolidando métricas operacionais...</p>
        </div>
      )}

      {data && (
        <>
          {/* Grade Simétrica de 6 KPIs Operacionais e Financeiros */}
          <section
            className={styles.kpiGrid}
            aria-label="Indicadores chave de desempenho"
            data-testid="dashboard-kpi-grid"
          >
            {/* KPI 1: Total de Expositores */}
            <article
              className={`${styles.kpiCard} ${styles.kpiAccentBlue} ${
                kpiFilter === 'ALL' && !hasActiveFilters ? styles.kpiCardSelected : ''
              }`}
              onClick={() => clearAllFilters()}
              data-testid="kpi-total-projects"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && clearAllFilters()}
            >
              <div className={styles.kpiTopRow}>
                <span className={styles.kpiLabel}>Total de Expositores</span>
                <span className={`${styles.kpiTag} ${styles.kpiTagBlue}`}>
                  {availableCategories.length > 0
                    ? `${availableCategories.length} setor(es)`
                    : 'Base Ativa'}
                </span>
              </div>
              <strong className={styles.kpiValue} data-testid="kpi-value-total-projects">
                {kpiMetrics.totalProjects}
              </strong>
              <p className={styles.kpiSubtitle}>
                Área homologada/informada:{' '}
                <strong>{kpiMetrics.totalAreaM2.toLocaleString('pt-BR')} m²</strong>
              </p>
            </article>

            {/* KPI 2: Sem Acesso ao Portal */}
            <article
              className={`${styles.kpiCard} ${styles.kpiAccentRed} ${
                kpiFilter === 'NEVER_ACCESSED' ? styles.kpiCardSelected : ''
              }`}
              onClick={() => toggleKpiFilter('NEVER_ACCESSED')}
              data-testid="kpi-never-accessed"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && toggleKpiFilter('NEVER_ACCESSED')}
            >
              <div className={styles.kpiTopRow}>
                <span className={styles.kpiLabel}>Sem Acesso ao Portal</span>
                <span className={`${styles.kpiTag} ${styles.kpiTagRed}`}>
                  {kpiMetrics.neverAccessedPercent}% da base
                </span>
              </div>
              <strong className={styles.kpiValue} data-testid="kpi-value-never-accessed">
                {kpiMetrics.strictlyNeverAccessed}
              </strong>
              <p className={styles.kpiSubtitle}>
                <strong>{kpiMetrics.accessedCount}</strong> expositor(es) já acessaram o sistema
              </p>
            </article>

            {/* KPI 3: Plantas em Análise */}
            <article
              className={`${styles.kpiCard} ${styles.kpiAccentIndigo} ${
                kpiFilter === 'PENDING_APPROVALS' ? styles.kpiCardSelected : ''
              }`}
              onClick={() => toggleKpiFilter('PENDING_APPROVALS')}
              data-testid="kpi-pending-approvals"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && toggleKpiFilter('PENDING_APPROVALS')}
            >
              <div className={styles.kpiTopRow}>
                <span className={styles.kpiLabel}>Plantas em Análise</span>
                <span className={`${styles.kpiTag} ${styles.kpiTagIndigo}`}>Fila Técnica</span>
              </div>
              <strong className={styles.kpiValue} data-testid="kpi-value-pending-approvals">
                {kpiMetrics.pendingApprovalsCount}
              </strong>
              <p className={styles.kpiSubtitle}>
                <strong>{kpiMetrics.approvedCount}</strong> aprovadas ·{' '}
                <strong>{kpiMetrics.rejectedCount}</strong> reprovadas
              </p>
            </article>

            {/* KPI 4: Alertas de Prazo */}
            <article
              className={`${styles.kpiCard} ${styles.kpiAccentAmber} ${
                kpiFilter === 'CRITICAL_DEADLINES' ? styles.kpiCardSelected : ''
              }`}
              onClick={() => toggleKpiFilter('CRITICAL_DEADLINES')}
              data-testid="kpi-critical-deadlines"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && toggleKpiFilter('CRITICAL_DEADLINES')}
            >
              <div className={styles.kpiTopRow}>
                <span className={styles.kpiLabel}>Alertas de Prazo</span>
                <span className={`${styles.kpiTag} ${styles.kpiTagAmber}`}>
                  {kpiMetrics.overdueCount} atrasados
                </span>
              </div>
              <strong className={styles.kpiValue} data-testid="kpi-value-critical-deadlines">
                {kpiMetrics.criticalDeadlinesCount}
              </strong>
              <p className={styles.kpiSubtitle}>
                <strong>{kpiMetrics.dueSoonCount}</strong> prazo próximo ·{' '}
                <strong>{kpiMetrics.regularDeadlinesCount}</strong> em dia
              </p>
            </article>

            {/* KPI 5: Fila de Cotações (Taxas) */}
            <article
              className={`${styles.kpiCard} ${styles.kpiAccentPurple}`}
              onClick={() => onNavigateToQuotes?.()}
              data-testid="kpi-pending-quotes"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && onNavigateToQuotes?.()}
            >
              <div className={styles.kpiTopRow}>
                <span className={styles.kpiLabel}>Fila de Cotações (Taxas)</span>
                <span className={`${styles.kpiTag} ${styles.kpiTagPurple}`}>
                  {kpiMetrics.financial.quotedFeesCount} cotadas
                </span>
              </div>
              <strong className={styles.kpiValue} data-testid="kpi-value-pending-quotes">
                {kpiMetrics.financial.pendingQuotesCount}
              </strong>
              <p className={styles.kpiSubtitle}>
                <strong>{kpiMetrics.financial.awaitingQuantityCount}</strong> aguardando quantidade do cliente
              </p>
            </article>

            {/* KPI 6: Receita Estimada (Taxas) */}
            <article
              className={`${styles.kpiCard} ${styles.kpiAccentEmerald}`}
              onClick={() => onNavigateToQuotes?.()}
              data-testid="kpi-estimated-revenue"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && onNavigateToQuotes?.()}
            >
              <div className={styles.kpiTopRow}>
                <span className={styles.kpiLabel}>Receita Estimada (Taxas)</span>
                <span className={`${styles.kpiTag} ${styles.kpiTagEmerald}`}>
                  {kpiMetrics.financial.totalProjectFees} vínculos
                </span>
              </div>
              <strong className={styles.kpiValue} data-testid="kpi-value-estimated-revenue">
                {formatCurrencyBRL(kpiMetrics.financial.estimatedRevenue)}
              </strong>
              <p className={styles.kpiSubtitle}>
                <strong>{kpiMetrics.financial.generatedPaymentsCount}</strong> pagamento(s) gerado(s)
              </p>
            </article>
          </section>

          {/* Barra de Organização e Filtros Rápidos */}
          <section
            className={styles.filtersBar}
            aria-label="Filtros do dashboard"
            data-testid="dashboard-filters-bar"
          >
            <div className={styles.filterControlsGrid}>
              <div className={styles.filterField}>
                <label htmlFor="dashboard-search" className={styles.filterLabel}>
                  Buscar Expositor
                </label>
                <input
                  id="dashboard-search"
                  type="text"
                  className={styles.filterInput}
                  placeholder="Empresa, responsável, e-mail ou CNPJ..."
                  value={searchTerm}
                  onChange={(e) => {
                    setSearchTerm(e.target.value);
                    resetPagination();
                  }}
                  data-testid="dashboard-search-input"
                />
              </div>

              <div className={styles.filterField}>
                <label htmlFor="dashboard-category" className={styles.filterLabel}>
                  Setor / Categoria
                </label>
                <select
                  id="dashboard-category"
                  className={styles.filterSelect}
                  value={categoryFilter}
                  onChange={(e) => {
                    setCategoryFilter(e.target.value);
                    resetPagination();
                  }}
                  data-testid="dashboard-category-filter"
                >
                  <option value="">Todos os setores</option>
                  {availableCategories.map((cat) => (
                    <option key={cat} value={cat}>
                      {cat}
                    </option>
                  ))}
                </select>
              </div>

              <div className={styles.filterField}>
                <label htmlFor="dashboard-status" className={styles.filterLabel}>
                  Status do Projeto
                </label>
                <select
                  id="dashboard-status"
                  className={styles.filterSelect}
                  value={statusFilter}
                  onChange={(e) => {
                    setStatusFilter(e.target.value as ProjectStatus | '');
                    resetPagination();
                  }}
                  data-testid="dashboard-status-filter"
                >
                  <option value="">Todos os status</option>
                  {STATUS_ORDER.map((st) => (
                    <option key={st} value={st}>
                      {STATUS_LABELS[st]}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {hasActiveFilters && (
              <div className={styles.activeFilterFooter}>
                <span className={styles.activeFilterHint}>
                  Exibindo resultados filtrados nas 3 colunas operacionais.
                </span>
                <button
                  type="button"
                  className={styles.clearFiltersBtn}
                  onClick={clearAllFilters}
                  data-testid="btn-clear-dashboard-filters"
                >
                  Limpar filtros
                </button>
              </div>
            )}
          </section>

          {/* Quadro Kanban de 3 Colunas com Cards de Altura Fixa */}
          <div className={styles.kanbanBoard} data-testid="kanban-board">
            {/* Coluna 1: Nunca Acessou */}
            <div
              className={`${styles.kanbanColumn} ${styles.colNeverAccessed}`}
              data-testid="inactive-exhibitors-panel"
            >
              <div className={styles.kanbanColumnHeader}>
                <div className={styles.columnTitleArea}>
                  <span className={`${styles.columnIndicator} ${styles.indicatorRed}`}></span>
                  <h2>Nunca Acessou</h2>
                </div>
                <span className={styles.columnCountBadge} data-testid="count-never-accessed">
                  {neverAccessedItems.length}
                </span>
              </div>
              <p className={styles.columnDescription}>
                Expositores cadastrados sem acesso recente ou que nunca entraram no portal.
              </p>

              <div className={styles.kanbanCardsList}>
                {neverAccessedItems.length === 0 ? (
                  <div className={styles.emptyCard}>Nenhum expositor inativo registrado.</div>
                ) : (
                  pagedNeverAccessed.map((item) => {
                    const linked = projectById.get(item.projectId);
                    const statusText = linked
                      ? STATUS_LABELS[linked.status] ?? linked.status
                      : 'Cadastrado';

                    return (
                      <article
                        key={item.projectId}
                        className={styles.kanbanCard}
                        data-testid={`row-inactive-${item.projectId}`}
                      >
                        <div className={styles.cardHeader}>
                          <div className={styles.cardTitleGroup}>
                            <strong className={styles.cardCompanyName} title={item.companyName}>
                              {item.companyName}
                            </strong>
                            <div className={styles.cardSubtags}>
                              <span className={styles.categoryChip}>
                                {linked?.category || 'Expositor'}
                              </span>
                              {linked?.document && (
                                <span className={styles.documentChip}>{linked.document}</span>
                              )}
                            </div>
                          </div>
                          {item.neverAccessed ? (
                            <span
                              className={styles.badgeNeverAccessed}
                              data-testid="badge-never-accessed"
                            >
                              Nunca acessou
                            </span>
                          ) : (
                            <span className={styles.badgeDaysAgo} data-testid="badge-days-ago">
                              {item.daysSinceLastAccess === 0
                                ? 'Hoje'
                                : `${item.daysSinceLastAccess} dia(s) atrás`}
                            </span>
                          )}
                        </div>

                        <div className={styles.cardBody}>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Contato:</span>
                            <span className={styles.metaValue} title={item.contactEmail}>
                              {item.contactEmail}
                            </span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Responsável:</span>
                            <span className={styles.metaValue} title={item.representativeName}>
                              {item.representativeName || '-'}
                            </span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Status / Área:</span>
                            <span className={styles.metaValue}>
                              {statusText}
                              {linked?.boothAreaM2 ? ` · ${linked.boothAreaM2} m²` : ''}
                            </span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Criado em:</span>
                            <span className={styles.metaValue}>
                              {formatDate(item.createdAt)}
                              {linked?.pdfDeadline
                                ? ` · PDF ${formatDate(linked.pdfDeadline)}`
                                : ''}
                            </span>
                          </div>
                        </div>

                        <div className={styles.cardFooter}>
                          <button
                            type="button"
                            className={styles.secondaryCardAction}
                            onClick={() => openProjectModal(item.projectId, item.companyName)}
                            data-testid={`btn-details-inactive-${item.projectId}`}
                          >
                            Ver Detalhes do Projeto →
                          </button>
                        </div>
                      </article>
                    );
                  })
                )}
              </div>

              {totalNeverAccessedPages > 1 && (
                <div className={styles.pagination} data-testid="pagination-never-accessed">
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() => setPageNeverAccessed((p) => Math.max(1, p - 1))}
                    disabled={pageNeverAccessed <= 1}
                    data-testid="btn-prev-never-accessed"
                  >
                    ← Anterior
                  </button>
                  <span className={styles.pageInfo} data-testid="page-info-never-accessed">
                    {pageNeverAccessed} / {totalNeverAccessedPages}
                  </span>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() =>
                      setPageNeverAccessed((p) => Math.min(totalNeverAccessedPages, p + 1))
                    }
                    disabled={pageNeverAccessed >= totalNeverAccessedPages}
                    data-testid="btn-next-never-accessed"
                  >
                    Próximo →
                  </button>
                </div>
              )}
            </div>

            {/* Coluna 2: Pendentes */}
            <div
              className={`${styles.kanbanColumn} ${styles.colPending}`}
              data-testid="pending-deadlines-panel"
            >
              <div className={styles.kanbanColumnHeader}>
                <div className={styles.columnTitleArea}>
                  <span className={`${styles.columnIndicator} ${styles.indicatorAmber}`}></span>
                  <h2>Pendentes</h2>
                </div>
                <span
                  className={`${styles.columnCountBadge} ${
                    allPendingItems.length > 0 ? styles.countWarning : ''
                  }`}
                  data-testid="count-pending"
                >
                  {allPendingItems.length}
                </span>
              </div>
              <p className={styles.columnDescription}>
                Plantas aguardando aprovação técnica e projetos com prazos de PDF ou pagamento pendentes.
              </p>

              <div className={styles.kanbanCardsList}>
                {allPendingItems.length === 0 ? (
                  <div className={styles.emptyCard}>Nenhum projeto com pendências no momento.</div>
                ) : (
                  <div data-testid="pending-approvals-panel" className={styles.approvalsGroup}>
                    {pagedPendingItems.map((entry) => {
                      if (entry.kind === 'approval') {
                        const app = entry.item;
                        const linked = projectById.get(app.projectId);

                        return (
                          <article
                            key={`approval-${app.projectId}`}
                            className={`${styles.kanbanCard} ${styles.cardApprovalHighlight}`}
                            data-testid={`row-approval-${app.projectId}`}
                          >
                            <div className={styles.cardHeader}>
                              <div className={styles.cardTitleGroup}>
                                <strong className={styles.cardCompanyName} title={app.companyName}>
                                  {app.companyName}
                                </strong>
                                <div className={styles.cardSubtags}>
                                  <span className={styles.categoryChip}>
                                    {linked?.category || 'Análise Técnica'}
                                  </span>
                                  {linked?.document && (
                                    <span className={styles.documentChip}>{linked.document}</span>
                                  )}
                                </div>
                              </div>
                              <span
                                className={`${styles.alertBadge} ${styles.alertPendente}`}
                                data-testid="badge-approval-status"
                              >
                                Planta em Análise
                              </span>
                            </div>

                            <div className={styles.cardBody}>
                              <div className={styles.metaRow}>
                                <span className={styles.metaLabel}>Arquivo:</span>
                                <span
                                  className={styles.metaValue}
                                  title={app.pdfOriginalFilename || 'planta.pdf'}
                                >
                                  {app.pdfOriginalFilename || 'planta.pdf'}
                                </span>
                              </div>
                              <div className={styles.metaRow}>
                                <span className={styles.metaLabel}>Área:</span>
                                <span className={styles.metaValue}>
                                  {app.boothAreaM2 ? `${app.boothAreaM2} m²` : '-'}
                                </span>
                              </div>
                              <div className={styles.metaRow}>
                                <span className={styles.metaLabel}>Responsável:</span>
                                <span className={styles.metaValue}>
                                  {linked?.representativeName || linked?.contactEmail || '-'}
                                </span>
                              </div>
                              <div className={styles.metaRow}>
                                <span className={styles.metaLabel}>Enviado em:</span>
                                <span className={styles.metaValue}>
                                  {formatDate(app.pdfUploadedAt)}
                                </span>
                              </div>
                            </div>

                            <div className={styles.cardFooter}>
                              <button
                                type="button"
                                className={styles.actionShortcut}
                                onClick={() => onOpenPdfReview?.(app.projectId)}
                                data-testid={`shortcut-review-pdf-${app.projectId}`}
                                id={`shortcut-review-pdf-${app.projectId}`}
                              >
                                Analisar Planta →
                              </button>
                              <button
                                type="button"
                                className={styles.iconDetailsButton}
                                onClick={() => openProjectModal(app.projectId, app.companyName)}
                                title="Ver detalhes e histórico"
                                data-testid={`btn-details-approval-${app.projectId}`}
                              >
                                Detalhes
                              </button>
                            </div>
                          </article>
                        );
                      }

                      const p = entry.item;
                      const formattedStatus = STATUS_LABELS[p.status] ?? p.status;

                      return (
                        <article
                          key={`deadline-${p.id}`}
                          className={styles.kanbanCard}
                          data-testid={`row-deadline-${p.id}`}
                        >
                          <div className={styles.cardHeader}>
                            <div className={styles.cardTitleGroup}>
                              <strong className={styles.cardCompanyName} title={p.companyName}>
                                {p.companyName}
                              </strong>
                              <div className={styles.cardSubtags}>
                                <span className={styles.categoryChip}>
                                  {p.category || 'Expositor'}
                                </span>
                                {p.document && (
                                  <span className={styles.documentChip}>{p.document}</span>
                                )}
                              </div>
                            </div>
                            <span
                              className={`${styles.alertBadge} ${
                                p.alertTag === 'ATRASADO'
                                  ? styles.alertAtrasado
                                  : p.alertTag === 'PRAZO_PROXIMO'
                                  ? styles.alertPrazoProximo
                                  : p.alertTag === 'PENDENTE'
                                  ? styles.alertPendente
                                  : styles.alertEmDia
                              }`}
                              data-testid={`badge-alert-${p.alertTag || 'PENDENTE'}`}
                            >
                              {getAlertBadgeLabel(p.alertTag)}
                            </span>
                          </div>

                          <div className={styles.cardBody}>
                            <div className={styles.metaRow}>
                              <span className={styles.metaLabel}>Responsável:</span>
                              <span className={styles.metaValue} title={p.representativeName}>
                                {p.representativeName || '-'}
                              </span>
                            </div>
                            <div className={styles.metaRow}>
                              <span className={styles.metaLabel}>Status:</span>
                              <span className={styles.metaValue} title={p.status}>
                                {formattedStatus}
                              </span>
                            </div>
                            <div className={styles.metaRow}>
                              <span className={styles.metaLabel}>Prazo PDF:</span>
                              <span className={styles.metaValue}>{formatDate(p.pdfDeadline)}</span>
                            </div>
                            <div className={styles.metaRow}>
                              <span className={styles.metaLabel}>Prazo Pagamento:</span>
                              <span className={styles.metaValue}>
                                {formatDate(p.paymentDeadline)}
                              </span>
                            </div>
                          </div>

                          <div className={styles.cardFooter}>
                            <button
                              type="button"
                              className={styles.secondaryCardAction}
                              onClick={() => openProjectModal(p.id, p.companyName)}
                              data-testid={`btn-details-deadline-${p.id}`}
                            >
                              Ver Detalhes do Projeto →
                            </button>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                )}
              </div>

              {totalPendingPages > 1 && (
                <div className={styles.pagination} data-testid="pagination-pending">
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() => setPagePending((p) => Math.max(1, p - 1))}
                    disabled={pagePending <= 1}
                    data-testid="btn-prev-pending"
                  >
                    ← Anterior
                  </button>
                  <span className={styles.pageInfo} data-testid="page-info-pending">
                    {pagePending} / {totalPendingPages}
                  </span>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() => setPagePending((p) => Math.min(totalPendingPages, p + 1))}
                    disabled={pagePending >= totalPendingPages}
                    data-testid="btn-next-pending"
                  >
                    Próximo →
                  </button>
                </div>
              )}
            </div>

            {/* Coluna 3: Em Dia */}
            <div
              className={`${styles.kanbanColumn} ${styles.colOnTrack}`}
              data-testid="on-track-panel"
            >
              <div className={styles.kanbanColumnHeader}>
                <div className={styles.columnTitleArea}>
                  <span className={`${styles.columnIndicator} ${styles.indicatorGreen}`}></span>
                  <h2>Em Dia</h2>
                </div>
                <span className={styles.columnCountBadge} data-testid="count-on-track">
                  {onTrackProjects.length}
                </span>
              </div>
              <p className={styles.columnDescription}>
                Projetos com obrigações regulares, plantas aprovadas ou sem pendências imediatas.
              </p>

              <div className={styles.kanbanCardsList}>
                {onTrackProjects.length === 0 ? (
                  <div className={styles.emptyCard}>Nenhum projeto regularizado registrado.</div>
                ) : (
                  pagedOnTrack.map((p) => {
                    const formattedStatus = STATUS_LABELS[p.status] ?? p.status;

                    return (
                      <article
                        key={`ontrack-${p.id}`}
                        className={styles.kanbanCard}
                        data-testid={`row-ontrack-${p.id}`}
                      >
                        <div className={styles.cardHeader}>
                          <div className={styles.cardTitleGroup}>
                            <strong className={styles.cardCompanyName} title={p.companyName}>
                              {p.companyName}
                            </strong>
                            <div className={styles.cardSubtags}>
                              <span className={styles.categoryChip}>
                                {p.category || 'Expositor'}
                              </span>
                              {p.document && (
                                <span className={styles.documentChip}>{p.document}</span>
                              )}
                            </div>
                          </div>
                          <span
                            className={`${styles.alertBadge} ${styles.alertEmDia}`}
                            data-testid="badge-alert-EM_DIA"
                          >
                            Em Dia
                          </span>
                        </div>

                        <div className={styles.cardBody}>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Responsável:</span>
                            <span className={styles.metaValue} title={p.representativeName}>
                              {p.representativeName || '-'}
                            </span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Status:</span>
                            <span className={styles.metaValue} title={p.status}>
                              {formattedStatus}
                            </span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Área Aprovada:</span>
                            <span className={styles.metaValue}>
                              {p.boothAreaM2 ? `${p.boothAreaM2} m²` : '-'}
                            </span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Prazos (PDF / Pag.):</span>
                            <span className={styles.metaValue}>
                              {formatDate(p.pdfDeadline)} · {formatDate(p.paymentDeadline)}
                            </span>
                          </div>
                        </div>

                        <div className={styles.cardFooter}>
                          <button
                            type="button"
                            className={styles.secondaryCardAction}
                            onClick={() => openProjectModal(p.id, p.companyName)}
                            data-testid={`btn-details-ontrack-${p.id}`}
                          >
                            Ver Detalhes do Projeto →
                          </button>
                        </div>
                      </article>
                    );
                  })
                )}
              </div>

              {totalOnTrackPages > 1 && (
                <div className={styles.pagination} data-testid="pagination-on-track">
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() => setPageOnTrack((p) => Math.max(1, p - 1))}
                    disabled={pageOnTrack <= 1}
                    data-testid="btn-prev-on-track"
                  >
                    ← Anterior
                  </button>
                  <span className={styles.pageInfo} data-testid="page-info-on-track">
                    {pageOnTrack} / {totalOnTrackPages}
                  </span>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() => setPageOnTrack((p) => Math.min(totalOnTrackPages, p + 1))}
                    disabled={pageOnTrack >= totalOnTrackPages}
                    data-testid="btn-next-on-track"
                  >
                    Próximo →
                  </button>
                </div>
              )}
            </div>
          </div>
        </>
      )}

      {/* Modal Rápido de Detalhes e Histórico do Projeto */}
      {selectedModal && (
        <div
          className={styles.modalBackdrop}
          onClick={() => setSelectedModal(null)}
          role="dialog"
          aria-modal="true"
          aria-labelledby="dashboard-modal-title"
          data-testid="dashboard-project-modal"
        >
          <div className={styles.modalCardWide} onClick={(e) => e.stopPropagation()}>
            <header className={styles.modalHeader}>
              <div>
                <h2 id="dashboard-modal-title" className={styles.modalCompany}>
                  {selectedModal.companyName}
                </h2>
                <p className={styles.modalSubtitle}>
                  Ficha operacional do expositor e histórico cronológico de eventos
                </p>
              </div>
              <button
                type="button"
                className={styles.closeModalBtn}
                onClick={() => setSelectedModal(null)}
                data-testid="btn-close-project-modal"
              >
                Fechar
              </button>
            </header>

            <div className={styles.modalDetailsGrid}>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>Setor / Categoria</span>
                <strong className={styles.modalFieldValue}>
                  {selectedModal.project?.category || '-'}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>CPF / CNPJ</span>
                <strong className={styles.modalFieldValue}>
                  {selectedModal.project?.document || '-'}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>Responsável</span>
                <strong className={styles.modalFieldValue}>
                  {selectedModal.project?.representativeName ||
                    selectedModal.inactive?.representativeName ||
                    '-'}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>E-mail de Contato</span>
                <strong className={styles.modalFieldValue}>
                  {selectedModal.project?.contactEmail ||
                    selectedModal.inactive?.contactEmail ||
                    '-'}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>Status Operacional</span>
                <strong className={styles.modalFieldValue}>
                  {selectedModal.project
                    ? STATUS_LABELS[selectedModal.project.status] ?? selectedModal.project.status
                    : selectedModal.approval
                    ? STATUS_LABELS[selectedModal.approval.status]
                    : 'Cadastrado'}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>Área Homologada / Informada</span>
                <strong className={styles.modalFieldValue}>
                  {selectedModal.project?.boothAreaM2 || selectedModal.approval?.boothAreaM2
                    ? `${selectedModal.project?.boothAreaM2 ?? selectedModal.approval?.boothAreaM2} m²`
                    : '-'}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>Prazo de Envio de PDF</span>
                <strong className={styles.modalFieldValue}>
                  {formatDate(selectedModal.project?.pdfDeadline ?? null)}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>Prazo de Pagamento</span>
                <strong className={styles.modalFieldValue}>
                  {formatDate(selectedModal.project?.paymentDeadline ?? null)}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>Último Acesso ao Portal</span>
                <strong className={styles.modalFieldValue}>
                  {selectedModal.inactive?.neverAccessed
                    ? 'Nunca acessou'
                    : selectedModal.inactive?.lastAccessAt
                    ? formatDate(selectedModal.inactive.lastAccessAt)
                    : 'Sem registro'}
                </strong>
              </div>
              <div className={styles.modalField}>
                <span className={styles.modalFieldLabel}>Endereço</span>
                <strong className={styles.modalFieldValue}>
                  {selectedModal.project?.address || '-'}
                </strong>
              </div>
            </div>

            {selectedModal.project?.rejectionJustification && (
              <div className={styles.rejectionBox}>
                <strong>Motivo da última reprovação:</strong>{' '}
                {selectedModal.project.rejectionJustification}
              </div>
            )}

            <div className={styles.modalTimelineWrapper}>
              <ProjectTimeline
                projectId={selectedModal.projectId}
                projectName={selectedModal.companyName}
              />
            </div>

            <footer className={styles.modalFooter}>
              {(selectedModal.project?.status === 'PDF_EM_ANALISE' || selectedModal.approval) &&
                onOpenPdfReview && (
                  <button
                    type="button"
                    className={styles.pdfReviewButton}
                    onClick={() => {
                      const pid = selectedModal.projectId;
                      setSelectedModal(null);
                      onOpenPdfReview(pid);
                    }}
                  >
                    Analisar Planta deste Projeto →
                  </button>
                )}
              {onNavigateToProjects && (
                <button
                  type="button"
                  className={styles.secondaryNavButton}
                  onClick={() => {
                    setSelectedModal(null);
                    onNavigateToProjects();
                  }}
                >
                  Ir para Lista de Projetos
                </button>
              )}
              <button
                type="button"
                className={styles.closeModalBtn}
                onClick={() => setSelectedModal(null)}
              >
                Concluir Consulta
              </button>
            </footer>
          </div>
        </div>
      )}
    </div>
  );
}
