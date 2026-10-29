import { useEffect, useState } from 'react';
import { getAdminDashboard } from '../../services/api';
import type { AdminDashboardData, InactiveExhibitor, Project, PendingApproval } from '../../types';
import { formatDate } from '../../utils/date';
import styles from './AdminDashboardPage.module.scss';

interface Props {
  onOpenPdfReview?: (projectId?: string) => void;
  onNavigateToProjects?: () => void;
}

const PAGE_SIZE = 4;

type PendingKanbanItem =
  | { kind: 'approval'; item: PendingApproval }
  | { kind: 'deadline'; item: Project };

export function AdminDashboardPage({ onOpenPdfReview }: Props) {
  const [data, setData] = useState<AdminDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Paginação individual por grupo (4 itens por página)
  const [pageNeverAccessed, setPageNeverAccessed] = useState(1);
  const [pagePending, setPagePending] = useState(1);
  const [pageOnTrack, setPageOnTrack] = useState(1);

  async function loadDashboard() {
    try {
      setLoading(true);
      setError(null);
      const result = await getAdminDashboard();
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível carregar as informações do dashboard.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadDashboard();
  }, []);

  // 1. Grupo: Nunca acessou / Inativos
  const neverAccessedItems: InactiveExhibitor[] = data?.inactiveExhibitors || [];
  const totalNeverAccessedPages = Math.max(1, Math.ceil(neverAccessedItems.length / PAGE_SIZE));
  const pagedNeverAccessed = neverAccessedItems.slice(
    (pageNeverAccessed - 1) * PAGE_SIZE,
    pageNeverAccessed * PAGE_SIZE
  );

  // 2. Grupo: Pendentes (aprovações de plantas + pendências de prazos)
  const pendingApprovalsList: PendingApproval[] = data?.pendingApprovals || [];
  const approvalIds = new Set(pendingApprovalsList.map((app) => app.projectId));

  const pendingDeadlineProjects: Project[] = (data?.pendingDeadlines || []).filter(
    (p) => (p.alertTag !== 'EM_DIA' && Boolean(p.alertTag)) || p.status === 'PDF_EM_ANALISE' || approvalIds.has(p.id)
  );

  const allPendingItems: PendingKanbanItem[] = [
    ...pendingApprovalsList.map((app): PendingKanbanItem => ({ kind: 'approval', item: app })),
    ...pendingDeadlineProjects
      .filter((p) => !approvalIds.has(p.id))
      .map((p): PendingKanbanItem => ({ kind: 'deadline', item: p })),
  ];

  const totalPendingPages = Math.max(1, Math.ceil(allPendingItems.length / PAGE_SIZE));
  const pagedPendingItems = allPendingItems.slice(
    (pagePending - 1) * PAGE_SIZE,
    pagePending * PAGE_SIZE
  );

  // 3. Grupo: Em dia
  const onTrackProjects: Project[] = (data?.pendingDeadlines || []).filter(
    (p) => (p.alertTag === 'EM_DIA' || !p.alertTag) && p.status !== 'PDF_EM_ANALISE' && !approvalIds.has(p.id)
  );
  const totalOnTrackPages = Math.max(1, Math.ceil(onTrackProjects.length / PAGE_SIZE));
  const pagedOnTrack = onTrackProjects.slice(
    (pageOnTrack - 1) * PAGE_SIZE,
    pageOnTrack * PAGE_SIZE
  );

  return (
    <div className={styles.dashboard} data-testid="admin-dashboard-page">
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Dashboard Operacional</h1>
          <p className={styles.subtitle}>
            Visão consolidada de expositores, pendências operacionais e aprovações de plantas.
          </p>
        </div>

        <div className={styles.headerControls}>
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
                pagedNeverAccessed.map((item) => (
                  <article
                    key={item.projectId}
                    className={styles.kanbanCard}
                    data-testid={`row-inactive-${item.projectId}`}
                  >
                    <div className={styles.cardHeader}>
                      <strong className={styles.cardCompanyName}>{item.companyName}</strong>
                      {item.neverAccessed ? (
                        <span className={styles.badgeNeverAccessed} data-testid="badge-never-accessed">
                          Nunca acessou
                        </span>
                      ) : (
                        <span className={styles.badgeDaysAgo} data-testid="badge-days-ago">
                          {item.daysSinceLastAccess === 0 ? 'Hoje' : `${item.daysSinceLastAccess} dia(s) atrás`}
                        </span>
                      )}
                    </div>
                    <div className={styles.cardBody}>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Contato:</span>
                        <span className={styles.metaValue}>{item.contactEmail}</span>
                      </div>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Responsável:</span>
                        <span className={styles.metaValue}>{item.representativeName}</span>
                      </div>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Criado em:</span>
                        <span className={styles.metaValue}>{formatDate(item.createdAt)}</span>
                      </div>
                    </div>
                  </article>
                ))
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
                  onClick={() => setPageNeverAccessed((p) => Math.min(totalNeverAccessedPages, p + 1))}
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
                className={`${styles.columnCountBadge} ${allPendingItems.length > 0 ? styles.countWarning : ''}`}
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
                      return (
                        <article
                          key={`approval-${app.projectId}`}
                          className={`${styles.kanbanCard} ${styles.cardApprovalHighlight}`}
                          data-testid={`row-approval-${app.projectId}`}
                        >
                          <div className={styles.cardHeader}>
                            <strong className={styles.cardCompanyName}>{app.companyName}</strong>
                            <span className={`${styles.alertBadge} ${styles.alertPendente}`} data-testid="badge-approval-status">
                              Planta em Análise
                            </span>
                          </div>
                          <div className={styles.cardBody}>
                            <div className={styles.metaRow}>
                              <span className={styles.metaLabel}>Arquivo:</span>
                              <span className={styles.metaValue}>{app.pdfOriginalFilename || 'planta.pdf'}</span>
                            </div>
                            <div className={styles.metaRow}>
                              <span className={styles.metaLabel}>Área:</span>
                              <span className={styles.metaValue}>{app.boothAreaM2 ? `${app.boothAreaM2} m²` : '-'}</span>
                            </div>
                            <div className={styles.metaRow}>
                              <span className={styles.metaLabel}>Enviado em:</span>
                              <span className={styles.metaValue}>{formatDate(app.pdfUploadedAt)}</span>
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
                          </div>
                        </article>
                      );
                    }

                    const p = entry.item;
                    return (
                      <article
                        key={`deadline-${p.id}`}
                        className={styles.kanbanCard}
                        data-testid={`row-deadline-${p.id}`}
                      >
                        <div className={styles.cardHeader}>
                          <strong className={styles.cardCompanyName}>{p.companyName}</strong>
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
                            {p.alertTag === 'ATRASADO'
                              ? 'Atrasado'
                              : p.alertTag === 'PRAZO_PROXIMO'
                              ? 'Prazo Próximo'
                              : p.alertTag === 'PENDENTE'
                              ? 'Pendente'
                              : 'Em Dia'}
                          </span>
                        </div>
                        <div className={styles.cardBody}>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Responsável:</span>
                            <span className={styles.metaValue}>{p.representativeName || '-'}</span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Prazo PDF:</span>
                            <span className={styles.metaValue}>{formatDate(p.pdfDeadline)}</span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Prazo Pagamento:</span>
                            <span className={styles.metaValue}>{formatDate(p.paymentDeadline)}</span>
                          </div>
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
                pagedOnTrack.map((p) => (
                  <article
                    key={`ontrack-${p.id}`}
                    className={styles.kanbanCard}
                    data-testid={`row-ontrack-${p.id}`}
                  >
                    <div className={styles.cardHeader}>
                      <strong className={styles.cardCompanyName}>{p.companyName}</strong>
                      <span className={`${styles.alertBadge} ${styles.alertEmDia}`} data-testid="badge-alert-EM_DIA">
                        Em Dia
                      </span>
                    </div>
                    <div className={styles.cardBody}>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Responsável:</span>
                        <span className={styles.metaValue}>{p.representativeName || '-'}</span>
                      </div>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Status:</span>
                        <span className={styles.metaValue}>{p.status}</span>
                      </div>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Área Aprovada:</span>
                        <span className={styles.metaValue}>{p.boothAreaM2 ? `${p.boothAreaM2} m²` : '-'}</span>
                      </div>
                    </div>
                  </article>
                ))
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
      )}
    </div>
  );
}
